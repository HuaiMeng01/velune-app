package com.veluneagent.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 一条发往模型的消息。
 *
 * 支持三种形态：
 * - 普通消息：role + text（+ imageDataUrl 多模态）
 * - 助手工具调用：role=assistant，toolCalls 非空，text 可为空
 * - 工具结果：role=tool，toolCallId 指向对应的调用
 */
data class ChatMessage(
    val role: String,
    val text: String,
    val imageDataUrl: String? = null,
    val toolCalls: List<com.veluneagent.core.data.agent.ToolCall> = emptyList(),
    val toolCallId: String? = null,
    /**
     * 思考过程。**带 tools 的请求必须原样回传**（DeepSeek / MiMo 文档明确：缺失返回 400），
     * 不带 tools 时上游会忽略。所以只在 assistant + toolCalls 的场景序列化。
     */
    val reasoningContent: String? = null,
)

/** 流式事件。 */
sealed interface StreamEvent {
    data class Delta(val text: String) : StreamEvent

    /**
     * 思考过程增量。DeepSeek / GLM / MiMo 的深度思考模式下，
     * reasoning 走 `delta.reasoning_content`（部分端点叫 `delta.reasoning`），与正文分开下发。
     */
    data class Reasoning(val text: String) : StreamEvent

    /** 服务端回报的用量；多数开源兼容端点不返回，此时由 TokenEstimator 兜底。 */
    data class Usage(val promptTokens: Int, val completionTokens: Int) : StreamEvent

    /** 本轮模型要求调用工具。聚合完成后一次性发出，避免半截 JSON 被误执行。 */
    data class ToolCalls(val calls: List<com.veluneagent.core.data.agent.ToolCall>) : StreamEvent

    data object Done : StreamEvent
    data class Failed(val message: String) : StreamEvent
}

/** 单条 SSE 数据解析结果。抽成纯函数是为了能脱离网络做单测。 */
internal data class SseChunk(
    val delta: String = "",
    val reasoning: String = "",
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val done: Boolean = false,
    val error: String? = null,
    val toolDeltas: List<ToolCallDelta> = emptyList(),
    val finishReason: String? = null,
) {
    val hasUsage: Boolean get() = promptTokens > 0 || completionTokens > 0
}

/** tool_calls 的流式分片：index 用于把同名多调用的片段拼回一条。 */
internal data class ToolCallDelta(
    val index: Int,
    val id: String?,
    val name: String?,
    val argumentsFragment: String?,
)

/**
 * OpenAI 兼容的流式对话客户端（SSE）。
 *
 * 只处理 `data: {...}` 与 `data: [DONE]` 两种负载；非标准片段（如 `event:`、注释行）直接跳过。
 * 取消由协程取消驱动：`callbackFlow` 的 awaitClose 里关闭 response，
 * 底层连接随之断开，不会出现「点了停止但请求还在跑」。
 */
object ChatClient {

    /**
     * 两个 SSE 数据包之间允许的最长静默时间。超过即判定为断流。
     *
     * 注意语义：这是「多久没有新内容」，**不是「整轮能跑多久」**。
     * 每读到一行就重置计时，所以长思考、长输出都不会被误杀；
     * 只有服务端彻底不吐字才会命中。各入口可按自己的窗口覆盖（QQ 比 App 内更短）。
     */
    const val DefaultIdleTimeoutSeconds = 180L

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            // 流式响应两包之间可能间隔很久，读超时必须放开（空闲由 source.timeout 兜底）
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun stream(
        provider: Provider,
        apiKey: String,
        history: List<ChatMessage>,
        temperature: Double = 0.7,
        tools: List<com.veluneagent.core.data.agent.ToolSpec> = emptyList(),
        thinking: Boolean = true,
        reasoningEffort: String? = null,
        /** 「无新内容」的上限，不是总时长上限。0 或负数表示不检测。 */
        idleTimeoutSeconds: Long = DefaultIdleTimeoutSeconds,
    ): Flow<StreamEvent> = callbackFlow {
        // 模型名由用户自填。留空时不要打到上游：换回来的只会是一句模糊的 400
        if (provider.model.isBlank()) {
            trySend(StreamEvent.Failed("当前服务商未填写模型名：设置 → 模型与密钥 → 编辑"))
            close()
            return@callbackFlow
        }

        val body = JSONObject().apply {
            put("model", provider.model)
            put("messages", JSONArray().apply { history.forEach { put(it.toWireJson()) } })
            put("stream", true)
            // 思考模式下 temperature 不生效（三家文档一致），但保留不会报错
            put("temperature", temperature)
            // 三家都默认开启思考，所以「开」时什么都不传。
            // 只有确认支持关闭的模型才下发 disabled：用户自填的未知模型一律保持服务端默认
            if (!thinking && ThinkingSupport.canDisable(provider.model)) {
                put("thinking", JSONObject().put("type", "disabled"))
            }
            if (thinking && reasoningEffort != null && ThinkingSupport.supportsEffort(provider.model)) {
                put("reasoning_effort", reasoningEffort)
            }
            if (tools.isNotEmpty()) {
                // 工具声明直接从 ToolSpec 生成，避免 schema 与注册表两处维护
                put("tools", JSONArray().apply { tools.forEach { put(JSONObject(it.toWireJson())) } })
            }
        }.toString()

        val request = Request.Builder()
            .url(provider.baseUrl.trimEnd('/') + "/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Accept", "text/event-stream")
            .post(body.toRequestBody(jsonMedia))
            .build()

        val call = client.newCall(request)

        // 用独立线程读流：OkHttp 的 body.source() 是阻塞 API
        val thread = Thread {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val detail = response.body?.string()?.take(300).orEmpty()
                        trySend(StreamEvent.Failed(httpErrorText(response.code, detail)))
                        close()
                        return@use
                    }
                    val source = response.body?.source()
                    if (source == null) {
                        trySend(StreamEvent.Failed("响应为空"))
                        close()
                        return@use
                    }
                    // 流式响应把 OkHttp 的 readTimeout 放开了，但「服务端不吐字」必须能被发现，
                    // 否则界面会一直转圈，用户只能手动点停止。这里给单次读取加一个空闲上限
                    // （每次成功读到内容都会重置，所以它不会掐断长的正常输出）。
                    if (idleTimeoutSeconds > 0) {
                        source.timeout().timeout(idleTimeoutSeconds, TimeUnit.SECONDS)
                    } else {
                        source.timeout().clearTimeout()
                    }
                    // tool_calls 是分片下发的：name 只在首片出现，arguments 逐片拼接
                    val toolAcc = sortedMapOf<Int, ToolCallAccumulator>()
                    var finished = false
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        val chunk = parseSseLine(line) ?: continue
                        when {
                            chunk.error != null -> {
                                trySend(StreamEvent.Failed(chunk.error))
                                finished = true
                                break
                            }

                            chunk.hasUsage -> trySend(
                                StreamEvent.Usage(chunk.promptTokens, chunk.completionTokens),
                            )

                            chunk.done -> finished = true
                        }
                        if (chunk.reasoning.isNotEmpty()) {
                            trySend(StreamEvent.Reasoning(chunk.reasoning))
                        }
                        if (chunk.delta.isNotEmpty()) trySend(StreamEvent.Delta(chunk.delta))
                        chunk.toolDeltas.forEach { d ->
                            val acc = toolAcc.getOrPut(d.index) { ToolCallAccumulator() }
                            if (!d.id.isNullOrEmpty()) acc.id = d.id
                            if (!d.name.isNullOrEmpty()) acc.name = d.name
                            if (!d.argumentsFragment.isNullOrEmpty()) {
                                acc.arguments.append(d.argumentsFragment)
                            }
                        }
                        if (chunk.done) break
                    }
                    // 工具调用聚合完成后一次性交给上层，避免执行到半截 JSON
                    if (toolAcc.isNotEmpty()) {
                        val calls = toolAcc.entries.mapNotNull { (index, acc) ->
                            if (acc.name.isBlank()) return@mapNotNull null
                            com.veluneagent.core.data.agent.ToolCall(
                                id = acc.id.ifEmpty { "call_$index" },
                                name = acc.name,
                                argumentsJson = acc.arguments.toString().ifBlank { "{}" },
                            )
                        }
                        if (calls.isNotEmpty()) trySend(StreamEvent.ToolCalls(calls))
                    }
                    if (!finished) runCatching { trySend(StreamEvent.Done) }
                }
                close()
            } catch (t: Throwable) {
                if (t is InterruptedException) {
                    close()
                } else if (t is java.io.InterruptedIOException || t is java.net.SocketTimeoutException) {
                    // 用户点停止时也会走到这里（call.cancel），但那时 flow 已关闭，send 会静默失败
                    trySend(StreamEvent.Failed("响应中断：$idleTimeoutSeconds 秒内没有收到新内容"))
                    close()
                } else {
                    trySend(StreamEvent.Failed(t.message ?: t.javaClass.simpleName))
                    close()
                }
            }
        }.apply {
            name = "velune-sse"
            isDaemon = true
            start()
        }

        awaitClose {
            call.cancel()
            thread.interrupt()
        }
    }.flowOn(Dispatchers.IO)

    private fun ChatMessage.toWireJson(): JSONObject = JSONObject().apply {
        put("role", role)
        if (role == "tool") {
            put("tool_call_id", toolCallId.orEmpty())
            put("content", text)
            return@apply
        }
        if (toolCalls.isNotEmpty()) {
            // 助手发起的调用必须原样回传，否则多轮工具调用会被上游判为上下文不合法
            put("content", if (text.isBlank()) JSONObject.NULL else text)
            // 思考模式 + tools：reasoning_content 缺失会被 DeepSeek / MiMo 直接判 400
            if (!reasoningContent.isNullOrBlank()) put("reasoning_content", reasoningContent)
            put(
                "tool_calls",
                JSONArray().apply {
                    toolCalls.forEach { call ->
                        put(
                            JSONObject().apply {
                                put("id", call.id)
                                put("type", "function")
                                put(
                                    "function",
                                    JSONObject().apply {
                                        put("name", call.name)
                                        put("arguments", call.argumentsJson)
                                    },
                                )
                            },
                        )
                    }
                },
            )
            return@apply
        }
        if (imageDataUrl.isNullOrEmpty()) {
            put("content", text)
        } else {
            // 多模态端点要的是 content 数组，且文本部分不能为空
            put(
                "content",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("type", "text")
                            put("text", text.ifBlank { "请描述这张图片" })
                        },
                    )
                    put(
                        JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().put("url", imageDataUrl))
                        },
                    )
                },
            )
        }
    }

    private fun httpErrorText(code: Int, detail: String): String = when (code) {
        400 -> "请求不合法（HTTP 400）：模型名可能不被支持，或参数与端点不兼容"
        401, 403 -> "认证失败（HTTP $code）：请检查 API Key 是否正确、是否有该模型权限"
        // DeepSeek 等厂商余额不足返回 402，而不是 429。原先落到 else 分支，
        // 用户看到的是整段英文 JSON（Insufficient Balance），也不知道该去充值。
        402 -> "账户余额不足（HTTP 402）：请充值或更换密钥"
        404 -> "接口不存在（HTTP 404）：请检查 Base URL 是否需要带 /v1"
        413 -> "请求体过大（HTTP 413）：请减少附件或缩短上下文"
        429 -> "触发限流（HTTP 429）：请稍后重试"
        in 500..599 -> "服务商异常（HTTP $code）：请稍后重试"
        else -> "HTTP $code ${detail.take(200)}"
    }
}

/**
 * 思考模式的能力差异。全部按官方文档核对（2026-10-21）：
 * - DeepSeek：thinking 默认 enabled，可 disabled；reasoning_effort 支持 low/high/max。
 * - 智谱：thinking 默认 enabled；GLM-5.3 / 5.3-FLASH / 5.3-FLASHX **传 disabled 会报错**；
 *   reasoning_effort 仅 GLM-5.2 及以上支持。
 * - MiMo：thinking 默认 enabled，可 disabled；不支持 reasoning_effort。
 */
/** 深度思考的可控性。三态而非布尔：未知模型既不能当「可关」也不能当「不可关」。 */
enum class ThinkingPolicy {
    /** 明确支持关闭。 */
    CanDisable,

    /** 明确不支持关闭，下发 disabled 会 400。 */
    ForcedOn,

    /** 不认识这个模型名：不猜，什么都不传，用服务端默认。 */
    Unknown,
}

object ThinkingSupport {

    /** GLM-5.3 系列不允许关闭思考，传 disabled 会直接报错。 */
    private val forbidDisable = listOf("glm-5.3")

    /** 明确可以安全关闭思考的模型前缀。 */
    private val allowDisable = listOf("deepseek", "mimo", "glm-4")

    private val effortModels = listOf("deepseek", "glm-5.2", "glm-5.3", "glm-5", "glm-4")

    /**
     * 模型名由用户自填，因此这里必须容忍「没见过的名字」。
     * 猜错的代价是请求直接 400，比「多思考一会儿」严重得多。
     */
    fun policyOf(model: String): ThinkingPolicy {
        val m = model.trim().lowercase()
        if (m.isEmpty()) return ThinkingPolicy.Unknown
        return when {
            forbidDisable.any { m.startsWith(it) } -> ThinkingPolicy.ForcedOn
            allowDisable.any { m.startsWith(it) } -> ThinkingPolicy.CanDisable
            else -> ThinkingPolicy.Unknown
        }
    }

    /** 能否安全下发 `thinking.type=disabled`。Unknown 一律不下发。 */
    fun canDisable(model: String): Boolean = policyOf(model) == ThinkingPolicy.CanDisable

    fun supportsEffort(model: String): Boolean {
        val m = model.trim().lowercase()
        return effortModels.any { m.startsWith(it) } && !m.contains("mimo")
    }
}

/** tool_calls 分片累加器。一个 index 对应一次调用。 */
private class ToolCallAccumulator {
    var id: String = ""
    var name: String = ""
    val arguments = StringBuilder()
}

/**
 * 安全取字符串。
 *
 * **必须用它替代 `optString`**：org.json 的 `optString` 遇到显式 `null` 值时返回字符串
 * `"null"`（内部走 `JSON.toString(JSONObject.NULL)`），而这在流式接口里极其常见 ——
 * 工具调用帧通常是 `{"delta":{"content":null,"tool_calls":[...]}}`。
 * 直接 `optString` 的结果是把 4 个字符的 "null" 当成正文增量拼进回复，整段回答变成 nullnullnull。
 */
private fun JSONObject?.str(key: String): String =
    if (this == null || isNull(key)) "" else optString(key)

/**
 * 解析一行 SSE。
 *
 * 兼容：
 * - `data: {"choices":[{"delta":{"content":"…"}}]}` —— 标准增量
 * - `data: {"choices":[{"finish_reason":"stop"}],"usage":{…}}` —— 收尾或用量帧
 * - `data: {"error":{"message":"…"}}` —— 多数兼容端点在中途出错时的返回
 * - `data: [DONE]`
 *
 * 返回 null 表示这一行不需要处理（空行、注释、`event:` 等）。
 */
internal fun parseSseLine(line: String): SseChunk? {
    val trimmed = line.trim()
    if (!trimmed.startsWith("data:")) return null
    val payload = trimmed.removePrefix("data:").trim()
    if (payload.isEmpty()) return null
    if (payload == "[DONE]") return SseChunk(done = true)

    val json = runCatching { JSONObject(payload) }.getOrNull() ?: return null

    json.optJSONObject("error")?.let { err ->
        val message = err.optString("message").ifBlank { err.toString() }
        return SseChunk(error = message)
    }

    val usage = json.optJSONObject("usage")
    val prompt = usage?.optInt("prompt_tokens", 0) ?: 0
    val completion = usage?.optInt("completion_tokens", 0) ?: 0

    val choice = json.optJSONArray("choices")?.optJSONObject(0)
    val deltaObj = choice?.optJSONObject("delta")
    val delta = deltaObj.str("content")
    // 思考内容：兼容 reasoning_content 与 reasoning 两种命名
    val reasoning = deltaObj.str("reasoning_content").ifEmpty { deltaObj.str("reasoning") }
    val finishReason = choice.str("finish_reason").takeIf { it.isNotEmpty() }

    val toolDeltas = buildList {
        val array = deltaObj?.optJSONArray("tool_calls") ?: return@buildList
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val function = item.optJSONObject("function")
            add(
                ToolCallDelta(
                    index = item.optInt("index", i),
                    id = item.str("id").takeIf { it.isNotEmpty() },
                    name = function.str("name").takeIf { it.isNotEmpty() },
                    argumentsFragment = function.str("arguments").takeIf { it.isNotEmpty() },
                ),
            )
        }
    }

    if (delta.isEmpty() && reasoning.isEmpty() && prompt == 0 && completion == 0 &&
        toolDeltas.isEmpty() && finishReason == null
    ) {
        // 心跳/空帧：UI 无需处理
        return SseChunk()
    }
    return SseChunk(
        delta = delta,
        reasoning = reasoning,
        promptTokens = prompt,
        completionTokens = completion,
        toolDeltas = toolDeltas,
        finishReason = finishReason,
    )
}
