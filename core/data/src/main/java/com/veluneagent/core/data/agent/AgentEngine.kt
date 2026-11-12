package com.veluneagent.core.data.agent

import com.veluneagent.core.data.ChatMessage
import com.veluneagent.core.data.StreamEvent
import com.veluneagent.core.data.TokenEstimator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Agent 执行引擎。
 *
 * 一个受预算约束的循环：模型推理 → 需要工具就执行 → 结果回灌 → 再推理。
 * 与「聊天」的区别只在于是否把工具结果喂回模型；与「事故」的区别则在于下面这些闸门：
 *
 * - 步数 / 工具次数 / token 三重成本预算，任一触顶立即停并保留已产出内容；
 * - **不设时间上限**：整轮与单步的墙钟默认关闭（0 = 不限），任务该跑多久由任务决定。
 *   防「服务端挂死」靠协议层的读取空闲超时（多久没有新内容），不靠给整轮掐表；
 * - 审批挂起时长不计入任何预算 —— 用户在面板前思考的时间不是 Agent 的运行时间；
 * - 连续失败 3 次停；
 * - 同一批「工具名 + 参数」重复出现时判定为循环，直接打断；
 * - 有副作用的工具或插件工具一律走审批门，用户拒绝后把拒绝原因作为工具结果回灌，
 *   让模型自己换路子 —— 而不是让整轮崩掉。
 *
 * 引擎不做任何 IO 之外的展示逻辑：UI 通过 [AgentEvent] 渲染。
 */
class AgentEngine(
    private val gateway: ModelGateway,
    private val catalog: ToolProvider,
    private val executor: ToolRunner,
    private val approver: AgentApprover,
    private val recorder: AgentRunStore? = null,
    private val budget: AgentBudget = AgentBudget(),
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { java.util.UUID.randomUUID().toString() },
) {

    fun run(conversationId: Long, history: List<ChatMessage>): Flow<AgentEvent> = flow {
        val runId = newId()
        val messages = history.toMutableList()
        val startedAt = now()
        val sessionAllowed = mutableSetOf<String>()

        var steps = 0
        var toolCalls = 0
        var consecutiveFailures = 0
        var promptTokens = 0
        var completionTokens = 0
        var lastSignature: String? = null
        var lastAnswer = ""
        var outcome: AgentOutcome? = null
        // 审批挂起累计时长：从任何墙钟预算里扣除，避免「用户多看一眼就超时」
        var approvalWaitMs = 0L

        recorder?.startRun(runId, conversationId)
        emit(AgentEvent.Started(budget.maxSteps, budget.maxToolCalls))

        try {
            while (outcome == null) {
                // ---- 预算闸门（循环顶部统一判定，避免在每个分支里重复写）----
                // 0 / 负数 = 不限，只有显式配置了正数才拦
                if (budget.maxSteps > 0 && steps >= budget.maxSteps) {
                    outcome = buildOutcome(StopReason.BudgetExceeded, lastAnswer, steps, toolCalls, promptTokens, completionTokens, "已达到最大步骤数 ${budget.maxSteps}，以下是已完成部分")
                    break
                }
                val elapsed = now() - startedAt - approvalWaitMs
                if (budget.maxWallClockMs > 0 && elapsed > budget.maxWallClockMs) {
                    outcome = buildOutcome(StopReason.BudgetExceeded, lastAnswer, steps, toolCalls, promptTokens, completionTokens, "已运行 ${elapsed / 1000} 秒，超过 ${budget.maxWallClockMs / 1000} 秒上限")
                    break
                }
                if (budget.maxTotalTokens > 0 && promptTokens + completionTokens > budget.maxTotalTokens) {
                    outcome = buildOutcome(StopReason.BudgetExceeded, lastAnswer, steps, toolCalls, promptTokens, completionTokens, "已消耗 ${promptTokens + completionTokens} token，超过 ${budget.maxTotalTokens} 上限")
                    break
                }

                steps++
                emit(AgentEvent.StepStarted(steps, "第 $steps 步 · 模型推理"))
                val stepStartedAt = now()
                val text = StringBuilder()
                val reasoning = StringBuilder()
                var calls: List<ToolCall> = emptyList()
                var failure: String? = null

                try {
                    val consume: suspend () -> Unit = {
                        gateway.stream(messages).collect { event ->
                            when (event) {
                                is StreamEvent.Delta -> {
                                    text.append(event.text)
                                    emit(AgentEvent.Delta(event.text))
                                }

                                is StreamEvent.Reasoning -> {
                                    reasoning.append(event.text)
                                    emit(AgentEvent.Reasoning(event.text))
                                }

                                is StreamEvent.ToolCalls -> calls = event.calls

                                is StreamEvent.Usage -> {
                                    promptTokens += event.promptTokens
                                    completionTokens += event.completionTokens
                                }

                                StreamEvent.Done -> Unit

                                is StreamEvent.Failed -> failure = event.message
                            }
                        }
                    }
                    // 默认不掐表：只要模型还在吐字就该继续。
                    // 挂死由协议层的读取空闲超时兜住，这里只在显式配置了正数时才做兜底。
                    if (budget.stepTimeoutMs > 0) {
                        withTimeout(budget.stepTimeoutMs) { consume() }
                    } else {
                        consume()
                    }
                } catch (t: TimeoutCancellationException) {
                    failure = "单步超时（${budget.stepTimeoutMs / 1000} 秒）"
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    failure = t.message ?: t.javaClass.simpleName
                }

                // 有些端点不返回 usage，用估算兜底，否则预算闸门形同虚设
                if (promptTokens == 0) promptTokens = TokenEstimator.estimateMessages(messages)
                if (completionTokens == 0 && text.isNotEmpty()) {
                    completionTokens = TokenEstimator.estimate(text.toString())
                }

                if (failure != null) {
                    consecutiveFailures++
                    recorder?.recordStep(
                        runId = runId,
                        index = steps,
                        type = "MODEL_CALL",
                        title = "模型调用失败",
                        outputJson = failure,
                        status = "failed",
                        durationMs = now() - stepStartedAt,
                        errorCode = "model_error",
                    )
                    if (consecutiveFailures >= budget.maxConsecutiveFailures) {
                        outcome = buildOutcome(StopReason.TooManyFailures, lastAnswer, steps, toolCalls, promptTokens, completionTokens, "连续 $consecutiveFailures 次调用失败：$failure")
                        break
                    }
                    // 5xx / 429 / 网络类失败值得等一会儿再试；4xx（除 429）是确定性失败，
                    // 立刻重试只会把同一个错误再打两遍，白烧配额也白等。
                    if (shouldBackoff(failure)) {
                        delay(500L * (1L shl (consecutiveFailures - 1).coerceIn(0, 3)))
                    }
                    continue
                }

                // 注意：这里不能清零 —— 工具失败也应该计入「连续失败」，
                // 否则「每步模型成功、工具每步都挂」会永远触发不了熔断
                if (text.isNotEmpty()) lastAnswer = text.toString()
                recorder?.recordStep(
                    runId = runId,
                    index = steps,
                    type = "MODEL_CALL",
                    title = if (calls.isEmpty()) "模型给出回答" else "模型请求 ${calls.size} 个工具",
                    outputJson = text.toString(),
                    durationMs = now() - stepStartedAt,
                )

                if (calls.isEmpty()) {
                    outcome = buildOutcome(StopReason.Completed, text.toString(), steps, toolCalls, promptTokens, completionTokens, null)
                    break
                }

                // 助手发起的调用必须原样进入上下文，并跟着各自的 tool 结果
                messages += ChatMessage(
                    role = "assistant",
                    text = text.toString(),
                    toolCalls = calls,
                    // 带 tools 的请求必须回传思考内容，否则上游 400
                    reasoningContent = reasoning.toString().ifBlank { null },
                )

                var stepHadFailure = false
                var budgetHit = false
                for (call in calls) {
                    if (budget.maxToolCalls > 0 && toolCalls >= budget.maxToolCalls) {
                        budgetHit = true
                        break
                    }
                    toolCalls++
                    val spec = catalog.find(call.name)
                    emit(AgentEvent.ToolStarted(call, spec))

                    val result = executeOne(
                        call = call,
                        spec = spec,
                        runId = runId,
                        sessionAllowed = sessionAllowed,
                        stepIndex = steps * 100 + toolCalls,
                        emitEvent = { event -> emit(event) },
                        onApprovalWait = { waited -> approvalWaitMs += waited },
                    )
                    if (!result.ok) stepHadFailure = true
                    // 子模型（delegate_task）的用量并入本轮预算：
                    // 不计的话，子任务烧的是另一份额度，主账上却看不见
                    promptTokens += result.promptTokens
                    completionTokens += result.completionTokens
                    emit(AgentEvent.ToolFinished(result))
                    messages += ChatMessage(
                        role = "tool",
                        text = result.contentForModel,
                        toolCallId = call.id,
                    )
                }

                if (budgetHit) {
                    outcome = buildOutcome(StopReason.BudgetExceeded, lastAnswer, steps, toolCalls, promptTokens, completionTokens, "工具调用次数达到上限 ${budget.maxToolCalls}")
                    break
                }

                if (stepHadFailure) {
                    consecutiveFailures++
                } else {
                    consecutiveFailures = 0
                }
                if (consecutiveFailures >= budget.maxConsecutiveFailures) {
                    outcome = buildOutcome(StopReason.TooManyFailures, lastAnswer, steps, toolCalls, promptTokens, completionTokens, "连续 $consecutiveFailures 次工具调用失败")
                    break
                }

                // 循环检测：同名同参连续出现两次，模型大概率在原地打转
                val signature = calls.joinToString("|") { "${it.name}:${it.argumentsJson}" }
                if (signature == lastSignature) {
                    outcome = buildOutcome(StopReason.LoopDetected, lastAnswer, steps, toolCalls, promptTokens, completionTokens, "检测到重复调用相同的工具与参数，已中断")
                    break
                }
                lastSignature = signature
            }
        } catch (c: CancellationException) {
            withContext(NonCancellable) {
                recorder?.finishRun(runId, RunStatus.Cancelled, steps, toolCalls, promptTokens, completionTokens, "用户取消")
            }
            throw c
        } finally {
            val finalOutcome = outcome
            withContext(NonCancellable) {
                if (finalOutcome != null) {
                    recorder?.finishRun(
                        runId = runId,
                        status = RunStatus.fromStopReason(finalOutcome.reason),
                        steps = finalOutcome.steps,
                        toolCalls = finalOutcome.toolCalls,
                        promptTokens = finalOutcome.promptTokens,
                        completionTokens = finalOutcome.completionTokens,
                        error = finalOutcome.message,
                    )
                }
            }
        }

        outcome?.let { emit(AgentEvent.Finished(it)) }
    }.flowOn(Dispatchers.IO)

    private suspend fun executeOne(
        call: ToolCall,
        spec: ToolSpec?,
        runId: String,
        sessionAllowed: MutableSet<String>,
        stepIndex: Int,
        emitEvent: suspend (AgentEvent) -> Unit,
        /** 回传审批挂起时长，用于从墙钟预算里扣除。 */
        onApprovalWait: (Long) -> Unit = {},
    ): ToolResult {
        if (spec == null) {
            // 未注册的工具名一律拒绝：模型可能被注入文本诱导，调一个根本不存在的工具
            return ToolResult.failure(call.id, call.name, "未注册的工具：${call.name}", "unknown_tool")
        }
        val startedAt = now()
        val needsApproval = catalog.requiresApproval(spec) && spec.name !in sessionAllowed
        if (needsApproval) {
            val request = ApprovalRequest(
                callId = call.id,
                toolName = spec.name,
                displayName = spec.displayName.ifBlank { spec.name },
                origin = spec.origin.ifBlank {
                    when (spec.source) {
                        ToolSource.Plugin -> "插件"
                        // 不能并到"内置"里：MCP 工具的执行方是用户自己填的第三方服务器，
                        // 审批面板写"内置"等于把外部能力伪装成本地能力
                        ToolSource.Mcp -> "MCP 服务器"
                        ToolSource.Builtin -> "内置"
                    }
                },
                argumentsPreview = preview(call.argumentsJson),
                scopes = catalog.scopesOf(spec),
                sideEffect = spec.sideEffect,
            )
            emitEvent(AgentEvent.ApprovalRequired(request))
            // 用户思考的时间不是 Agent 的运行时间：先计时，等完再从墙钟里扣掉
            val approvalStartedAt = now()
            val decision = approver.approve(request)
            onApprovalWait(now() - approvalStartedAt)
            when (decision) {
                ApprovalDecision.Deny -> {
                    return ToolResult.failure(
                        callId = call.id,
                        name = call.name,
                        message = "用户拒绝了这次工具调用，请改用其他方式或直接说明需要什么",
                        code = "user_denied",
                        durationMs = now() - startedAt,
                    )
                }

                ApprovalDecision.AllowForSession -> sessionAllowed += spec.name
                ApprovalDecision.AllowOnce -> Unit
            }
        }

        // 工具级超时：`ToolSpec.timeoutMs` 之前是个没人读的字段，SSH / 代码执行 / MCP
        // 声明的时间预算全部落空。这里统一兜住，超时后再给模型一条可读的失败原因，
        // 而不是让它一直等到整轮结束。
        val result = runCatching {
            if (spec.timeoutMs > 0) {
                withTimeoutOrNull(spec.timeoutMs.toLong()) { executor.execute(spec, call, runId) }
                    ?: ToolResult.failure(
                        call.id,
                        call.name,
                        "工具执行超过 ${spec.timeoutMs / 1000} 秒，已中止",
                        "tool_timeout",
                        now() - startedAt,
                    )
            } else {
                executor.execute(spec, call, runId)
            }
        }.getOrElse { t ->
            ToolResult.failure(
                call.id,
                call.name,
                t.message ?: t.javaClass.simpleName,
                "executor_crashed",
                now() - startedAt,
            )
        }
        recorder?.recordStep(
            runId = runId,
            index = stepIndex,
            type = "TOOL_CALL",
            title = "${spec.displayName.ifBlank { spec.name }} · ${if (result.ok) "成功" else "失败"}",
            inputJson = call.argumentsJson,
            outputJson = result.displayText,
            status = if (result.ok) "ok" else "failed",
            durationMs = result.durationMs,
            errorCode = result.errorCode,
        )
        return result
    }

    private fun preview(argumentsJson: String): String =
        if (argumentsJson.length <= 160) argumentsJson else argumentsJson.take(160) + "…"

    /**
     * 失败后是否需要退避。
     *
     * 401/402/403/404/413 这类 4xx 是确定性失败，重试结果一定一样；
     * 只有 429 与 5xx、以及超时/网络断开值得等一会儿再试。
     */
    private fun shouldBackoff(message: String): Boolean {
        val isDeterministic4xx = message.contains("HTTP 4") && !message.contains("HTTP 429")
        return !isDeterministic4xx
    }

    private fun buildOutcome(
        reason: StopReason,
        answer: String,
        steps: Int,
        toolCalls: Int,
        promptTokens: Int,
        completionTokens: Int,
        message: String?,
    ) = AgentOutcome(
        reason = reason,
        answer = answer,
        steps = steps,
        toolCalls = toolCalls,
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        message = message,
    )
}
