package com.veluneagent.core.data.im

import com.veluneagent.core.data.ChatMessage
import com.veluneagent.core.data.Provider
import com.veluneagent.core.data.agent.AgentApprover
import com.veluneagent.core.data.agent.AgentBudget
import com.veluneagent.core.data.agent.AgentEngine
import com.veluneagent.core.data.agent.AgentEvent
import com.veluneagent.core.data.agent.AgentRunStore
import com.veluneagent.core.data.agent.ApprovalDecision
import com.veluneagent.core.data.agent.ApprovalRequest
import com.veluneagent.core.data.agent.BuiltinTools
import com.veluneagent.core.data.agent.OpenAiGateway
import com.veluneagent.core.data.agent.ToolProvider
import com.veluneagent.core.data.agent.ToolRunner
import com.veluneagent.core.data.agent.ToolSource
import com.veluneagent.core.data.agent.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

/**
 * QQ 侧「只读 Agent」。
 *
 * 为什么不是直接把 AgentEngine 全量搬过来：QQ 消息是**外部输入**，
 * 完全没有"用户点确认"这一步。所以这里做了两道减法：
 *
 * 1. 工具目录先裁一遍（[ReadOnlyCatalog]）：副作用工具、隐私工具、插件工具
 *    连声明都不给模型看 —— 模型看不到 ssh_exec，就不会去调、也不会写"我试试"的废话；
 * 2. 审批一律拒绝（[DenyAll]）：万一目录被绕过，还有这一层兜底。
 *
 * 预算也压得比 App 内小：QQ 有被动回复窗口（群 5 分钟 / 单聊 60 分钟），
 * 跑到第 12 步再回消息，人早走了。
 */
class QqAgentRunner(
    catalog: ToolProvider,
    private val executor: ToolRunner,
    private val runStore: AgentRunStore? = null,
) {

    /**
     * 工具路径的产出：正文 + 本轮真实用量。
     *
     * 为什么不只返回 `String`：调用方（[QqResponder]）落库时必须同时写
     * `prompt_tokens` / `completion_tokens`，否则 QQ 消耗的 token 在「用量」页上是隐形的。
     * 用量由引擎的 [com.veluneagent.core.data.agent.AgentOutcome] 提供，不是估算值。
     */
    data class AgentReply(
        val text: String,
        val promptTokens: Int = 0,
        val completionTokens: Int = 0,
    )

    private val safeCatalog: ToolProvider = ReadOnlyCatalog(catalog)

    /** 当前 QQ 侧实际可用的工具名。UI 上用来说明"能做什么"，测试里用来兜住白名单。 */
    val allowedTools: List<String>
        get() = safeCatalog.specs().map { it.name }

    suspend fun run(
        conversationId: Long,
        history: List<ChatMessage>,
        provider: Provider,
        apiKey: String,
        thinking: Boolean,
        reasoningEffort: String? = null,
    ): Result<AgentReply> = withContext(Dispatchers.IO) {
        val gateway = OpenAiGateway(
            provider = provider,
            apiKey = apiKey,
            tools = { safeCatalog.specs() },
            thinking = thinking,
            reasoningEffort = reasoningEffort,
            // QQ 侧比 App 内更急：40 秒没有新内容基本可以判死，用户已经等太久了
            idleTimeoutSeconds = 40L,
        )
        val engine = AgentEngine(
            gateway = gateway,
            catalog = safeCatalog,
            executor = executor,
            approver = DenyAll,
            recorder = runStore,
            budget = AgentBudget(
                maxSteps = 4,
                maxToolCalls = 3,
                // 90s 是 QQ 被动回复窗口给的硬约束，属于外部限制而非引擎限制，保留
                maxWallClockMs = 90_000,
                maxTotalTokens = 24_000,
                // 单步不再掐表：挂死由上面的读取空闲超时（40s 无新内容）兜住
                stepTimeoutMs = 0,
            ),
        )

        val buffer = StringBuilder()
        var outcomeAnswer = ""
        var failure: String? = null
        // AgentOutcome 里的 token 是本轮**累计值**（引擎自己把事件里的 usage 加起来），
        // 且 Finished 只会发一次，所以直接取即可，不需要在这里再累加。
        var promptTokens = 0
        var completionTokens = 0

        runCatching {
            engine.run(conversationId, history).collect { event ->
                when (event) {
                    is AgentEvent.Delta -> buffer.append(event.text)
                    is AgentEvent.Finished -> {
                        outcomeAnswer = event.outcome.answer
                        failure = event.outcome.message
                        promptTokens = event.outcome.promptTokens
                        completionTokens = event.outcome.completionTokens
                    }

                    else -> Unit
                }
            }
        }.onFailure {
            // 预算耗尽 / 超时都会以异常形式冒出来，已产出的正文仍可用
            if (it is kotlinx.coroutines.CancellationException) throw it
            failure = it.message ?: it.javaClass.simpleName
        }

        val text = buffer.toString().trim().ifBlank { outcomeAnswer.trim() }
        if (text.isEmpty()) {
            Result.failure(IllegalStateException(failure ?: "模型没有返回内容"))
        } else {
            Result.success(
                AgentReply(
                    text = text,
                    promptTokens = promptTokens,
                    completionTokens = completionTokens,
                ),
            )
        }
    }
}

/**
 * 只读目录：把"能做"收敛到可解释的一小撮。
 *
 * 过滤条件是四联：无副作用 + 不涉隐私 + 不花钱 + 内置工具，再排掉一小撮
 * 对远程入口没有意义、却会额外占用上下文的工具（见 [quarantined]）。
 * 插件工具一律排除——插件的能力边界由清单声明，但远程触发时没有审批环节，
 * 不能把它的可执行面直接暴露给 QQ。
 *
 * 白名单最终由测试锁死（`ReadOnlyCatalogTest`）：任何放宽都会让"只读"变成越权后门。
 */
internal class ReadOnlyCatalog(private val delegate: ToolProvider) : ToolProvider {

    /**
     * 明确隔离的工具。
     *
     * `update_plan` 不改世界也不泄隐私，但它是**本地 UI 的计划卡片**写入器：
     * 从 QQ 触发只会在用户手机桌面上凭空出现一张进度卡，没有任何人能看见它，
     * 却要占掉 L2 规则里的一大段说明。放进白名单的收益是负的。
     */
    private val quarantined = setOf(BuiltinTools.NameUpdatePlan)

    private fun allowed(spec: ToolSpec): Boolean =
        spec.source == ToolSource.Builtin &&
            !spec.sideEffect &&
            !spec.sensitive &&
            !spec.costly &&
            spec.name !in quarantined

    override fun specs(): List<ToolSpec> = delegate.specs().filter(::allowed)

    override fun find(name: String): ToolSpec? = delegate.find(name)?.takeIf(::allowed)

    /**
     * QQ 侧不存在审批人，审批兜底对该路径无效；白名单由测试锁死。
     *
     * 所以这里直接返回 false：真正的边界是 [specs] 少给模型看什么，
     * 而不是"给了但等一个永远不会有人点的确认框"。
     */
    override fun requiresApproval(spec: ToolSpec): Boolean = false

    override fun scopesOf(spec: ToolSpec): List<String> = delegate.scopesOf(spec)
}

/** 纵深防御：正常路径下工具目录已经裁过，走到这里说明有代码绕过，直接拒。 */
private object DenyAll : AgentApprover {
    override suspend fun approve(request: ApprovalRequest): ApprovalDecision = ApprovalDecision.Deny
}
