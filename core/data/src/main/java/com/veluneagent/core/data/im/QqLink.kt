package com.veluneagent.core.data.im

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * OneBot 11 正向 WebSocket 客户端：接收 QQ 消息事件。
 *
 * 为什么用正向 WS 而不是反向 WS：手机是发起方，NapCat 只要开了「正向 WebSocket 服务端」，
 * 填个地址就能连，不需要在协议端配我们的 IP——手机 IP 随时会变，反向 WS 会天天断。
 *
 * 只处理 `post_type == "message"` 的事件；心跳/生命周期事件直接丢弃。
 * 群消息默认只在被 @ 时才算「对我说话」，否则一个热闹的群会把模型调用打爆。
 */
class QqLink(
    private val scope: CoroutineScope,
    private val onIncoming: suspend (Incoming) -> Unit,
    private val onState: (State, String?) -> Unit = { _, _ -> },
) {

    enum class State { Stopped, Connecting, Connected, Error }

    /**
     * 一条收到的消息。
     *
     * [hasNonText] 带默认值：后加的字段，默认值保证既有构造点与单测不受影响。
     */
    data class Incoming(
        val text: String,
        val userId: String,
        val groupId: String?,
        val messageId: Long,
        val selfId: String,
        val senderName: String,
        /** 消息里有非文本内容（图片/表情/语音）：[text] 为空串，但仍要回一句话。 */
        val hasNonText: Boolean = false,
    ) {
        val isGroup: Boolean get() = groupId != null
    }

    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var socket: WebSocket? = null
    private var loop: Job? = null
    private val running = AtomicBoolean(false)
    private var attempt = 0

    fun start(wsUrl: String, token: String) {
        if (running.getAndSet(true)) return
        loop = scope.launch {
            while (isActive && running.get()) {
                onState(State.Connecting, null)
                val outcome = connectOnce(wsUrl, token)
                if (!running.get()) break
                // 连上过就重置退避；**没连上才逐步拉长**。
                // 反过来写（连上才递增）会让「协议端没开」时永远 1 秒重试，把协议端打满。
                attempt = if (outcome.connected) 0 else (attempt + 1).coerceAtMost(5)
                val backoff = (1_000L shl attempt).coerceAtMost(30_000L)
                // 地址格式之类的错误自己带说明，不要被这句通用文案盖掉
                onState(State.Error, outcome.message ?: "连接断开，${backoff / 1000}s 后重试")
                delay(backoff)
            }
        }
    }

    fun stop() {
        running.set(false)
        loop?.cancel()
        loop = null
        runCatching { socket?.close(1000, "client stop") }
        socket = null
        onState(State.Stopped, null)
    }

    private enum class ClosedReason { NORMAL, FAILED }

    /** 一次连接的结局：[connected] 用于决定退避是否清零，[message] 是给界面的可读原因。 */
    private data class Outcome(
        val connected: Boolean,
        val reason: ClosedReason,
        val message: String? = null,
    )

    /** 挂起直到连接结束；返回结局，供外层决定退避策略。**任何情况下都不向外抛。** */
    private suspend fun connectOnce(wsUrl: String, token: String): Outcome {
        // url() 对非法地址直接抛 IllegalArgumentException。以前它冲出 while 循环，
        // 协程就那么死了：状态永远停在「连接中…」，按钮也点不动。现在本地判成一次失败。
        val request = runCatching {
            Request.Builder()
                .url(wsUrl)
                .apply { if (token.isNotBlank()) addHeader("Authorization", "Bearer $token") }
                .build()
        }.getOrElse {
            onState(State.Error, "地址格式不对：$wsUrl")
            return Outcome(false, ClosedReason.FAILED, "地址格式不对：$wsUrl")
        }

        val result = CompletableDeferred<ClosedReason>()
        val opened = AtomicBoolean(false)

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.set(true)
                onState(State.Connected, null)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val incoming = QqEventParser.parseMessageEvent(text) ?: return
                // 协议端在客户端没有及时消费时会重推同一个 message_id：
                // 同一条只处理一次，避免一句话答两遍。拿不到 id（0）时不做去重。
                if (incoming.messageId != 0L &&
                    IncomingDedup.seenBefore(incoming.messageId.toString())
                ) {
                    return
                }
                scope.launch {
                    runCatching { onIncoming(incoming) }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onState(State.Error, t.message ?: "连接失败")
                if (!result.isCompleted) result.complete(ClosedReason.FAILED)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!result.isCompleted) {
                    result.complete(if (code == 1000) ClosedReason.NORMAL else ClosedReason.FAILED)
                }
            }
        }

        val ws = runCatching { client.newWebSocket(request, listener) }.getOrElse {
            onState(State.Error, "地址格式不对：$wsUrl")
            return Outcome(false, ClosedReason.FAILED, "地址格式不对：$wsUrl")
        }
        socket = ws
        val reason = try {
            result.await()
        } finally {
            runCatching { socket?.cancel() }
            socket = null
        }
        return Outcome(opened.get(), reason)
    }
}

/**
 * OneBot 11 消息事件解析。
 *
 * 抽成 object + 纯函数，原因很实际：这类协议最容易在
 * 「message 是数组还是字符串」「群消息怎么判断 @」两个地方出错，
 * 而它们完全不需要真机就能测。
 *
 * 纯图片 / 表情消息没有 text 段：这里**不能**返回 null，
 * 否则就是「消息发出去了、机器人永远不回」。空文本一律标记
 * [QqLink.Incoming.hasNonText]，由上层回一句固定话术。
 */
object QqEventParser {

    fun parseMessageEvent(raw: String): QqLink.Incoming? {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (json.optString("post_type") != "message") return null

        val type = json.optString("message_type")
        val selfId = json.optString("self_id")
        val userId = json.optString("user_id")
        if (userId.isEmpty() || userId == selfId) return null

        val groupId = if (type == "group") json.optString("group_id").takeIf { it.isNotEmpty() } else null
        val segments = readSegments(json)
        val text = segments.filter { it.first == "text" }
            .joinToString("") { it.second }
            .trim()

        val mentionedBot = segments.any { it.first == "at" && it.second == selfId }
        // 群聊只响应被 @ 的消息；私聊全部响应。放在文本判空之前：
        // 群里的图片消息没 @ 我时也不该回应
        if (groupId != null && !mentionedBot) return null
        // 连一个段都没有：这是空事件，没有可回的东西
        if (segments.isEmpty()) return null

        val sender = json.optJSONObject("sender")
        return QqLink.Incoming(
            text = text,
            userId = userId,
            groupId = groupId,
            messageId = json.optLong("message_id", 0),
            selfId = selfId,
            senderName = sender?.optString("card").orEmpty()
                .ifBlank { sender?.optString("nickname").orEmpty() },
            // 有段但没有文字 = 纯图片/表情/语音，交给上层回一句人话
            hasNonText = text.isEmpty(),
        )
    }

    /** 返回 (类型, 内容) 列表；兼容 message 为数组与 raw_message 为 CQ 字符串两种形式。 */
    private fun readSegments(json: JSONObject): List<Pair<String, String>> {
        val array: JSONArray? = json.optJSONArray("message")
        if (array != null) {
            return buildList {
                for (i in 0 until array.length()) {
                    val seg = array.optJSONObject(i) ?: continue
                    val segType = seg.optString("type")
                    val data = seg.optJSONObject("data")
                    val value = when (segType) {
                        "text" -> data?.optString("text").orEmpty()
                        "at" -> data?.optString("qq").orEmpty()
                        else -> ""
                    }
                    add(segType to value)
                }
            }
        }
        // 字符串模式：从 CQ 码里剥出文本与 at
        val raw = json.optString("raw_message")
        if (raw.isEmpty()) return emptyList()
        val out = mutableListOf<Pair<String, String>>()
        var rest = raw
        val regex = Regex("""\[CQ:([a-z_]+)((?:,[^\]]*)?)\]""")
        while (true) {
            val match = regex.find(rest) ?: break
            if (match.range.first > 0) out += "text" to rest.substring(0, match.range.first)
            val name = match.groupValues[1]
            if (name == "at") {
                val qq = Regex("""qq=([^,\]]+)""").find(match.value)?.groupValues?.get(1).orEmpty()
                out += "at" to qq
            } else {
                // 非 at 的 CQ 码（image / face / record…）也要保留成段，
                // 否则「纯图片消息」会被当成空事件丢掉
                out += name to ""
            }
            rest = rest.substring(match.range.last + 1)
        }
        if (rest.isNotEmpty()) out += "text" to rest
        return out
    }
}
