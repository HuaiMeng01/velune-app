package com.veluneagent.core.data.im

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * QQ 官方机器人（开放平台）直连。
 *
 * **这是唯一「只用手机、不依赖任何外部设备」的合规方案**：
 * 手机作为客户端主动连官方网关（纯出站），既收消息也发消息，
 * 不需要公网回调地址、不需要协议端、不需要另一台机器。
 *
 * 接口全部按官方文档实现（核对日期 2026-10-21）：
 * - 取凭证：`POST https://bots.qq.com/app/getAppAccessToken`，body `{appId, clientSecret}`
 *   （该域名不分环境；实测 api.sgroup.qq.com 上此路径返回 404「不支持的调用」）
 * - 鉴权头：`Authorization: QQBot {access_token}`（有效期 7200s）
 * - 网关：`GET /gateway/bot`（正式 https://api.sgroup.qq.com，沙箱 https://sandbox.api.sgroup.qq.com）
 * - 单聊：`POST /v2/users/{user_openid}/messages`
 * - 群聊：`POST /v2/groups/{group_openid}/messages`
 * - intents：`GROUP_AND_C2C_EVENT = 1 << 25`
 *
 * 网关握手顺序（写错任何一步都会「连上但收不到消息」）：
 * `gateway()` 拿 wss → 服务端 `op=10 Hello`（`d.heartbeat_interval`）→ 客户端 `op=2 Identify`
 * → `READY`（`d.session_id`）→ 按周期发 `op=1` 心跳（`d` 为最新 `s`，首帧为 `null`）。
 * 断线重连不再 Identify，而是 `op=6 Resume`，网关会把断线期间的事件补发回来。
 *
 * **环境选择不是可选项**：新增机器人默认强制 IP 白名单（只有白名单 IP 才能连 WS、调 OpenAPI），
 * 手机公网 IP 每次都变，所以纯手机方案只能走沙箱；官方规则明确"IP 白名单只作用于正式环境"。
 *
 * 限制（官方规则，非本实现取舍）：
 * - 群聊只会在「@机器人」时收到事件（GROUP_AT_MESSAGE_CREATE）；
 * - 被动回复有效期：单聊 60 分钟 / 4 次，群聊 5 分钟 / 5 次；
 * - OpenID 不是 QQ 号，无法从 OpenID 反查真实账号；**同一用户在不同环境下的 OpenID 也不同**。
 */
object QqOfficial {

    /** 正式环境：需要服务器 IP 白名单 */
    const val ApiBaseProduction = "https://api.sgroup.qq.com"

    /** 沙箱环境：豁免 IP 白名单，只能与「沙箱配置」名单内的人/群对话 */
    const val ApiBaseSandbox = "https://sandbox.api.sgroup.qq.com"

    /** 取凭证的域名不分环境，不能跟着 OpenAPI 换 */
    private const val TokenUrl = "https://bots.qq.com/app/getAppAccessToken"

    fun apiBase(sandbox: Boolean): String = if (sandbox) ApiBaseSandbox else ApiBaseProduction

    /** GROUP_AND_C2C_EVENT：单聊消息 + 群 @ 消息 */
    const val Intents = 1 shl 25

    /**
     * 一条收到的消息。
     *
     * [timestamp] 与 [hasNonText] 都带默认值：这两项是后加的，
     * 给默认值能保证所有既有构造点（含单测）不被破坏。
     */
    data class Incoming(
        val text: String,
        val openId: String,
        val groupOpenId: String?,
        val messageId: String,
        val eventType: String,
        /** 事件发生时间（毫秒）。0 = 事件里没有可用时间，此时不做被动窗口判断。 */
        val timestamp: Long = 0L,
        /** 消息里有非文本内容（图片/表情/语音）：[text] 会为空串，但仍然要回一句话。 */
        val hasNonText: Boolean = false,
    ) {
        val isGroup: Boolean get() = groupOpenId != null
    }

    /** REST 用：需要 callTimeout，短请求不能挂死在半路上。 */
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * WebSocket 专用 client。
     *
     * **不能和 REST 共用一个 client**：REST 那份带 `callTimeout=30s`，
     * 长连接跑满 30 秒就会被自己掐断（表现是「每半分钟断一次、反复重连」）；
     * 另外它没有 `pingInterval`，中间设备会把空闲连接当垃圾清掉。
     */
    private val wsClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    // ---- access_token 缓存：官方说 7200s，提前 60s 换新，避免边界期请求失败 ----
    // 缓存必须带凭证指纹：用户改了 AppID/AppSecret 后若继续用旧 token，
    // 表现就是"改了没用，一直鉴权失败"，而这恰好是最常见的操作（自建机器人第一次填错）
    @Volatile private var cachedToken: String = ""
    @Volatile private var cachedKey: String = ""
    @Volatile private var tokenExpireAt: Long = 0L
    private val tokenLock = Any()

    private fun tokenKeyOf(appId: String, appSecret: String): String =
        appId.trim() + "|" + appSecret.trim().hashCode()

    private fun cachedTokenFor(key: String): String? =
        cachedToken.takeIf {
            it.isNotBlank() && key == cachedKey && System.currentTimeMillis() < tokenExpireAt - 60_000
        }

    suspend fun accessToken(appId: String, appSecret: String, force: Boolean = false): Result<String> {
        val key = tokenKeyOf(appId, appSecret)
        if (!force) cachedTokenFor(key)?.let { return Result.success(it) }
        synchronized(tokenLock) {
            if (!force) cachedTokenFor(key)?.let { return Result.success(it) }
        }
        // token 不分环境，所以这里不接 sandbox：正式/沙箱共用同一个 token
        val body = JSONObject()
            .put("appId", appId.trim())
            .put("clientSecret", appSecret.trim())
            .toString()
        val request = Request.Builder()
            .url(TokenUrl)
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody(jsonMedia))
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("获取凭证失败：HTTP ${response.code} ${text.take(120)}")
                val json = JSONObject(text)
                val token = json.optString("access_token")
                if (token.isBlank()) {
                    val code = json.optString("code").ifBlank { json.optString("err_code") }
                    error("获取凭证失败：${json.optString("message").ifBlank { "code=$code" }}")
                }
                val expires = json.optString("expires_in").toLongOrNull() ?: 7_200L
                cachedToken = token
                cachedKey = key
                tokenExpireAt = System.currentTimeMillis() + expires * 1_000
                token
            }
        }
    }

    suspend fun gateway(token: String, sandbox: Boolean): Result<String> = runCatching {
        val request = Request.Builder()
            .url(apiBase(sandbox) + "/gateway/bot")
            .addHeader("Authorization", "QQBot $token")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("获取网关失败：HTTP ${response.code} ${text.take(120)}")
            JSONObject(text).optString("url").ifBlank { error("网关地址为空") }
        }
    }

    /**
     * 连通性自检：能换到凭证、能拿到网关地址，就说明 AppID/AppSecret 与网络都通。
     * 官方没有「查机器人信息」的轻量接口，这两步是最小可验证集合。
     */
    suspend fun probe(appId: String, appSecret: String, sandbox: Boolean): Result<String> {
        val token = accessToken(appId, appSecret, force = true).getOrElse { return Result.failure(it) }
        val gw = gateway(token, sandbox).getOrElse { return Result.failure(it) }
        val env = if (sandbox) "沙箱" else "正式"
        return Result.success("$env 凭证有效 · 网关 ${gw.substringAfter("://").take(24)}")
    }

    /** 单聊（C2C）发消息。msgId 为被动回复时传入，5/60 分钟内有效。 */
    suspend fun sendC2C(
        token: String,
        userOpenId: String,
        content: String,
        msgId: String? = null,
        sandbox: Boolean = true,
    ): Result<Unit> = send(
        token = token,
        path = "/v2/users/${userOpenId}/messages",
        content = content,
        msgId = msgId,
        sandbox = sandbox,
    )

    /** 群聊发消息。 */
    suspend fun sendGroup(
        token: String,
        groupOpenId: String,
        content: String,
        msgId: String? = null,
        sandbox: Boolean = true,
    ): Result<Unit> = send(
        token = token,
        path = "/v2/groups/${groupOpenId}/messages",
        content = content,
        msgId = msgId,
        sandbox = sandbox,
    )

    /**
     * `msg_seq` 必须**按 msg_id 各自计数**，不能全局递增。
     *
     * 官方语义：`msg_id` 是被动回复的凭证，`msg_seq` 是这条凭证下的第几次回复；
     * 上限是「群 5 次 / 单聊 4 次」。用全局计数器时，两条不同消息的 seq 会交叉增长，
     * 结果就是「同一 msg_id 的第 2 次回复被判成重复」。
     *
     * 容量控制：超过 500 条消息的计数就整体清空 —— 旧 msg_id 早就过了被动窗口，
     * 留着只会让内存慢慢涨。
     */
    private val seqByMsg = ConcurrentHashMap<String, AtomicInteger>()

    private suspend fun send(
        token: String,
        path: String,
        content: String,
        msgId: String?,
        sandbox: Boolean,
    ): Result<Unit> = runCatching {
        val body = JSONObject().apply {
            put("content", content)
            put("msg_type", 0)
            if (!msgId.isNullOrBlank()) {
                if (seqByMsg.size > 500) seqByMsg.clear()
                val n = seqByMsg.getOrPut(msgId) { AtomicInteger(0) }.incrementAndGet()
                // 超上限还在打接口只会换来一次失败：本地就判死，省一次网络往返
                val cap = if (path.contains("/groups/")) 5 else 4
                if (n > cap) error("被动回复次数已达上限（$cap 次），本条不再发送")
                put("msg_id", msgId)
                put("msg_seq", n)
            }
        }.toString()

        val request = Request.Builder()
            .url(apiBase(sandbox) + path)
            .addHeader("Authorization", "QQBot $token")
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .post(body.toRequestBody(jsonMedia))
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("发送失败：HTTP ${response.code} ${text.take(160)}")
            val json = runCatching { JSONObject(text) }.getOrNull()
            val errCode = json?.optInt("err_code", 0) ?: 0
            if (errCode != 0) {
                // 文档明确要求按 err_code 判断，message 可能随时调整
                error("发送失败：err_code=$errCode ${json?.optString("message").orEmpty().take(80)}")
            }
        }
    }

    /**
     * 网关长连接。
     *
     * 生命周期：Hello → Identify / Resume → 心跳 → 断开 → 外层按关闭码决定「立刻重连还是彻底停」。
     *
     * 为什么必须有 Resume：断线期间网关仍在产生事件，不 Resume 就等于每次网络抖动
     * 都丢掉那段时间里用户发的消息 —— 手机在 Wi-Fi / 蜂窝之间切换时这非常常见。
     */
    class Link(
        private val scope: CoroutineScope,
        private val appId: String,
        private val appSecret: String,
        private val sandbox: Boolean = true,
        private val onIncoming: suspend (Incoming) -> Unit,
        private val onState: (QqLink.State, String?) -> Unit,
    ) {
        /** 这次断开之后怎么重连。 */
        private enum class ClosePolicy { RESUMABLE, REIDENTIFY, FATAL }

        private var job: Job? = null
        private val running = AtomicBoolean(false)
        private var socket: WebSocket? = null

        /** `READY` 里的 session_id。非空才允许 Resume；4006/4007/Invalid Session 会清掉它。 */
        private var sessionId: String? = null

        /** 最新收到的事件序号 `s`：心跳回传它，Resume 也要带上。 */
        private var lastSeq: Int? = null

        /** 本次运行是否曾经连上过：连上过才把退避清零，避免「连不上还每秒重试」。 */
        private var everConnected = false

        fun start() {
            if (running.getAndSet(true)) return
            job = scope.launch {
                var attempt = 0
                while (isActive && running.get()) {
                    onState(QqLink.State.Connecting, null)
                    val policy = runOnce()
                    if (!running.get()) break
                    // 致命错误（4914 机器人未上线 / 4915 已封禁）重连一万次也不会成功：
                    // 必须真的停下来，否则就是无限退避重连 + 前台通知一直闪
                    if (policy == ClosePolicy.FATAL) {
                        running.set(false)
                        break
                    }
                    attempt = if (policy == ClosePolicy.RESUMABLE && everConnected) {
                        0
                    } else {
                        (attempt + 1).coerceAtMost(5)
                    }
                    val backoff = (1_000L shl attempt).coerceAtMost(30_000L)
                    onState(QqLink.State.Error, "连接断开，${backoff / 1000}s 后重试")
                    delay(backoff)
                }
            }
        }

        fun stop() {
            running.set(false)
            job?.cancel()
            job = null
            runCatching { socket?.close(1000, "stop") }
            socket = null
        }

        /** 跑一次连接（含握手、心跳、收消息），返回这轮结束时应当采用的续连策略。 */
        private suspend fun runOnce(): ClosePolicy {
            val token = accessToken(appId, appSecret).getOrElse { error ->
                onState(QqLink.State.Error, error.message ?: "获取凭证失败")
                return ClosePolicy.RESUMABLE
            }
            val url = gateway(token, sandbox).getOrElse { error ->
                onState(QqLink.State.Error, error.message ?: "获取网关失败")
                return ClosePolicy.RESUMABLE
            }

            val done = CompletableDeferred<ClosePolicy>()
            // 最近一次收到任何帧的时间：心跳协程用它判断「连接还在不在」
            val lastAckAt = AtomicLong(System.currentTimeMillis())
            var heartbeat: Job? = null

            fun finish(policy: ClosePolicy) {
                if (!done.isCompleted) done.complete(policy)
            }

            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    // 真正的握手要等 op=10 Hello 才开始：这里只记一次活跃时间
                    lastAckAt.set(System.currentTimeMillis())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                    when (json.optInt("op", -1)) {
                        // Hello：决定 Identify 还是 Resume，然后起心跳
                        10 -> {
                            lastAckAt.set(System.currentTimeMillis())
                            val interval = json.optJSONObject("d")
                                ?.optLong("heartbeat_interval", 30_000) ?: 30_000
                            // 只有「有 session 且知道 seq」才能 Resume；否则必须先 Identify。
                            // 4006/4007/Invalid Session 已把 sessionId 清空，自然落到 Identify。
                            val session = sessionId
                            val seqNow = lastSeq
                            val payload = if (session != null && seqNow != null) {
                                resumePayload(token, session, seqNow)
                            } else {
                                identifyPayload(token)
                            }
                            if (!webSocket.send(payload.toString())) {
                                // send 返回 false = 发送队列已满或连接已坏，不能假装成功
                                runCatching { webSocket.cancel() }
                                finish(ClosePolicy.RESUMABLE)
                                return
                            }
                            heartbeat?.cancel()
                            heartbeat = scope.launch {
                                while (isActive) {
                                    delay(interval)
                                    val silent = System.currentTimeMillis() - lastAckAt.get()
                                    if (silent > (interval * 2.5).toLong()) {
                                        // 网关既不回 ACK 也不主动断：这种「假活」连接只能自己收尾
                                        runCatching { webSocket.close(1000, "heartbeat timeout") }
                                        return@launch
                                    }
                                    // org.json 陷阱：put(key, null) 会删掉这个 key，
                                    // 网关收到的就成了「没有 d 字段」的心跳，所以首帧必须用 JSONObject.NULL
                                    val seqField: Any = lastSeq ?: JSONObject.NULL
                                    val sent = webSocket.send(
                                        JSONObject().put("op", 1).put("d", seqField).toString(),
                                    )
                                    if (!sent) {
                                        runCatching { webSocket.cancel() }
                                        return@launch
                                    }
                                }
                            }
                        }

                        // Heartbeat ACK
                        11 -> lastAckAt.set(System.currentTimeMillis())

                        // Dispatch：READY / RESUMED / 业务事件
                        0 -> {
                            lastSeq = json.optInt("s", lastSeq ?: 0)
                            when (json.optString("t")) {
                                "READY" -> {
                                    sessionId = json.optJSONObject("d")
                                        ?.optString("session_id")
                                        ?.takeIf { it.isNotBlank() }
                                    everConnected = true
                                    onState(QqLink.State.Connected, null)
                                }
                                "RESUMED" -> {
                                    everConnected = true
                                    onState(QqLink.State.Connected, null)
                                }
                                else -> {
                                    val incoming = QqOfficialEventParser.parse(text)
                                    // 网关会重投未被及时处理的事件（Resume 补发尤其如此）：
                                    // 同一个 messageId 只处理一次，否则一句话答两遍、额度花两份
                                    if (incoming != null &&
                                        !IncomingDedup.seenBefore(incoming.messageId)
                                    ) {
                                        scope.launch { runCatching { onIncoming(incoming) } }
                                    }
                                }
                            }
                        }

                        // 7 = Reconnect：服务端要求重连（保留 session，下次 Resume）
                        7 -> runCatching { webSocket.close(1000, "reconnect") }

                        // 9 = Invalid Session：session 作废，必须清掉并重新 Identify
                        9 -> {
                            sessionId = null
                            lastSeq = null
                            runCatching { webSocket.close(1000, "invalid session") }
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    onState(QqLink.State.Error, t.message ?: "连接失败")
                    finish(ClosePolicy.RESUMABLE)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    finish(policyOf(code))
                    // okhttp 的默认实现会回一个 close 帧；覆写后必须自己补上，否则连接不会真正结束
                    runCatching { webSocket.close(1000, null) }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    finish(policyOf(code))
                }
            }

            socket = wsClient.newWebSocket(Request.Builder().url(url).build(), listener)
            return try {
                done.await()
            } finally {
                heartbeat?.cancel()
                runCatching { socket?.cancel() }
                socket = null
            }
        }

        /**
         * 关闭码 → 续连策略。
         *
         * 4006/4007 说明 session 已经不可用，**必须清掉**才会走 Identify；
         * 4914/4915 是「这台机器人根本没资格连」，只能停，不能重连。
         */
        private fun policyOf(code: Int): ClosePolicy = when {
            // 4006 无效 session / 4007 seq 错误：清 session，下次重新 Identify
            code == 4006 || code == 4007 -> {
                sessionId = null
                lastSeq = null
                ClosePolicy.REIDENTIFY
            }
            // 4009 连接过期：保留 session，重连后 Resume
            code == 4009 -> ClosePolicy.RESUMABLE
            // 4900-4913 内部错误：延迟重连
            code in 4900..4913 -> ClosePolicy.RESUMABLE
            // 4914 机器人已下架，只允许沙箱环境
            code == 4914 -> {
                onState(
                    QqLink.State.Error,
                    "机器人未上线或当前不允许正式环境：请把「运行环境」切回沙箱",
                )
                ClosePolicy.FATAL
            }
            // 4915 机器人已封禁
            code == 4915 -> {
                onState(QqLink.State.Error, "机器人已被封禁，请联系 QQ 开放平台")
                ClosePolicy.FATAL
            }
            else -> ClosePolicy.RESUMABLE
        }

        /** Identify：首次鉴权。`shard` 即使不分片也必须显式传 `[0, 1]`。 */
        private fun identifyPayload(token: String): JSONObject = JSONObject().apply {
            put("op", 2)
            put(
                "d",
                JSONObject().apply {
                    put("token", "QQBot $token")
                    put("intents", Intents)
                    put("shard", JSONArray().put(0).put(1))
                    put(
                        "properties",
                        JSONObject()
                            .put("\$os", "android")
                            .put("\$browser", "velune")
                            .put("\$device", "velune"),
                    )
                },
            )
        }

        /** Resume：断线重连后的续连，网关会补发 `seq` 之后遗漏的事件。 */
        private fun resumePayload(token: String, session: String, seqNow: Int): JSONObject =
            JSONObject().apply {
                put("op", 6)
                put(
                    "d",
                    JSONObject()
                        .put("token", "QQBot $token")
                        .put("session_id", session)
                        .put("seq", seqNow),
                )
            }
    }
}

/**
 * 官方事件解析。
 *
 * 单聊是 `C2C_MESSAGE_CREATE`（author.user_openid），
 * 群聊是 `GROUP_AT_MESSAGE_CREATE`（author.member_openid + group_openid）。
 * content 在群事件里已由平台去掉 @ 前缀，不需要自己再剥。
 *
 * 纯图片 / 表情消息的 `content` 是空串：这里**不能**返回 null，
 * 否则就是「消息到了、机器人永远不回」，用户侧等于机器人坏了。
 * 空文本一律标记 [QqOfficial.Incoming.hasNonText]，由上层回一句固定话术。
 */
object QqOfficialEventParser {

    private const val Tag = "Velune-QQ"

    fun parse(raw: String): QqOfficial.Incoming? {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (json.optInt("op", -1) != 0) return null
        val type = json.optString("t")
        val data = json.optJSONObject("d") ?: return null
        val content = data.optString("content").trim()
        val messageId = data.optString("id")
        val author = data.optJSONObject("author")
        val timestamp = readTimestamp(data)

        return when (type) {
            "C2C_MESSAGE_CREATE" -> {
                val openId = author?.optString("user_openid").orEmpty()
                // 连 openid 都没有的事件无法回复，只能丢
                if (openId.isEmpty()) return null
                QqOfficial.Incoming(
                    text = content,
                    openId = openId,
                    groupOpenId = null,
                    messageId = messageId,
                    eventType = type,
                    timestamp = timestamp,
                    hasNonText = content.isEmpty(),
                )
            }

            "GROUP_AT_MESSAGE_CREATE" -> {
                val groupOpenId = data.optString("group_openid")
                val member = author?.optString("member_openid").orEmpty()
                if (groupOpenId.isEmpty()) return null
                QqOfficial.Incoming(
                    text = content,
                    openId = member,
                    groupOpenId = groupOpenId,
                    messageId = messageId,
                    eventType = type,
                    timestamp = timestamp,
                    hasNonText = content.isEmpty(),
                )
            }

            else -> {
                // 不静默：GROUP_ADD_ROBOT、FRIEND_ADD 之类的事件被忽略时，
                // 日志是排查「有事件但没有反应」的唯一线索
                logIgnored(type)
                null
            }
        }
    }

    /**
     * 事件时间：官方可能给毫秒数，也可能给 ISO8601 字符串（如 `2026-10-21T23:00:00+08:00`）。
     * 解析不出来返回 0，上层据此跳过被动窗口判断 —— 宁可不判，也不要误杀。
     */
    private fun readTimestamp(data: JSONObject): Long {
        val numeric = data.optLong("timestamp", 0L)
        if (numeric > 0L) return numeric
        val text = data.optString("timestamp")
        if (text.isBlank()) return 0L
        return runCatching { java.time.Instant.parse(text).toEpochMilli() }.getOrDefault(0L)
    }

    /** 单测跑在 JVM 上，`android.util.Log` 未 mock 会抛；日志绝不能影响解析结果。 */
    private fun logIgnored(type: String) {
        runCatching { Log.d(Tag, "忽略事件类型：$type") }
    }
}
