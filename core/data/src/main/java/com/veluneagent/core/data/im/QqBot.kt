package com.veluneagent.core.data.im

import android.content.Context
import com.veluneagent.core.data.security.SecretBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * QQ 机器人连接器（OneBot 11 / NapCat 等实现）。
 *
 * **为什么不是「接入 QQ 官方 API」**：官方机器人需要公网回调地址接收事件，
 * 手机没有固定公网 IP，收不到消息。业界可行路径是用户在服务器（或电脑）上跑
 * NapCat / go-cqhttp 这类协议端，App 作为客户端调它的 HTTP API。
 * 这与 Operit 的做法一致，代价是用户需要自己部署协议端。
 *
 * 本版只做**单向发送**：把 Agent 的结果推到 QQ。
 * 接收消息需要 WebSocket 长连接与事件循环，属于独立子系统，未实现。
 *
 * 接口按 OneBot 11 标准（`/send_private_msg`、`/send_group_msg`），
 * `Authorization: Bearer <token>` 为 NapCat 等实现的通用鉴权方式。
 * [待核实: 用目标协议端实际抓一次包确认路径与鉴权头]
 */
object QqBot {

    /** 目标类型：私聊或群。 */
    enum class Target { Private, Group }

    /**
     * 接入方式。
     *
     * - [Official]：QQ 开放平台官方 Bot，手机直接连官方网关。**不需要任何外部设备**，
     *   但需要去 q.qq.com 注册机器人拿 AppID/AppSecret，且群聊只在被 @ 时收到消息。
     * - [OneBot]：连自建协议端（NapCat）。功能更全（能收群内全部消息、能用真实 QQ 号），
     *   但需要另一台设备常开。
     */
    enum class Mode { Official, OneBot }

    data class Config(
        val mode: Mode = Mode.Official,
        val appId: String = "",
        val appSecret: String = "",
        val baseUrl: String,
        val token: String,
        val target: Target,
        val targetId: String,
        val displayName: String,
        /** 正向 WebSocket 地址，用于**接收**消息；不填则只能发不能收 */
        val wsUrl: String = "",
        /** 是否开启自动回复（收到私聊 / 群里 @ 我的消息就让模型回答并回发） */
        val autoReply: Boolean = false,
        /**
         * 官方模式是否连沙箱环境。
         *
         * 默认 true 不是"偷懒"，是唯一能在纯手机上跑通的选择：
         * 新增机器人默认强制 IP 白名单（"只有白名单 IP 才能连接 websocket 和调用 OPENAPI"），
         * 而手机的公网 IP 每切一次网就会变、白名单又不支持网段；
         * 官方规则里明确写着"IP 白名单只作用于正式环境，不影响沙箱环境"。
         * 代价：只能和「沙箱配置」名单里的人对话——自用场景正好。
         */
        val sandbox: Boolean = true,
        /**
         * 允许 QQ 里触发**只读**工具（时间/搜索/计算/应用列表…）。
         *
         * 默认关：QQ 消息是外部输入，没有"用户点确认"这一步。
         * 开启后也只放行无副作用、不涉隐私的内置工具（见 [QqAgentRunner]）。
         */
        val agentTools: Boolean = false,
        /**
         * 回复写进哪个会话。
         *
         * `0` = 自动（第一次收到消息时建一个「QQ 消息」会话并记住）。
         * 大于 0 = 用户在设置里挑的一条已有会话。
         *
         * 为什么允许挑已有会话：QQ 与 App 是同一个人的两个入口，写进同一条会话
         * 才能接着上文聊；固定新建「QQ 消息」会让人设与上下文在两端分裂。
         */
        val conversationId: Long = 0L,
        /**
         * 是否开深度思考（推理模型的长思考）。
         *
         * **默认 false，与 App 里的默认相反**（`ChatPrefs` 那边是 true）。
         * 原因很实在：QQ 里等 20 秒等于没回。用户发一句「现在几点」，
         * 开思考要 20-40 秒才吐第一个字，体感就是"机器人不理我"，
         * 而 QQ 场景本来要的就是三句话以内的短答。
         */
        val thinking: Boolean = false,
    ) {
        val configured: Boolean
            get() = when (mode) {
                Mode.Official -> appId.isNotBlank() && appSecret.isNotBlank()
                Mode.OneBot -> baseUrl.isNotBlank() && targetId.isNotBlank()
            }

        /** 能否接收消息（官方模式只要凭证齐全；OneBot 模式还要 WS 地址） */
        val canReceive: Boolean
            get() = when (mode) {
                Mode.Official -> appId.isNotBlank() && appSecret.isNotBlank()
                Mode.OneBot -> wsUrl.isNotBlank()
            }
    }

    private const val PREFS = "velune_qqbot"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_TOKEN = "token"
    private const val KEY_TARGET = "target"
    private const val KEY_TARGET_ID = "target_id"
    private const val KEY_NAME = "display_name"
    private const val KEY_WS_URL = "ws_url"
    private const val KEY_MODE = "mode"
    private const val KEY_APP_ID = "app_id"
    private const val KEY_APP_SECRET = "app_secret"
    private const val KEY_AUTO_REPLY = "auto_reply"
    private const val KEY_SANDBOX = "sandbox"
    private const val KEY_AGENT_TOOLS = "agent_tools"
    private const val KEY_CONVERSATION_ID = "conversation_id"
    private const val KEY_THINKING = "thinking"

    /** 自动会话 id 存在这条 prefs 里（与配置分开：它是运行时产物，不是用户配置）。 */
    private const val SESSION_PREFS = "velune_qqbot_session"
    private const val KEY_SESSION_CONVERSATION = "conversation_id"

    /**
     * 扫码绑定者 openid（与配置分开放：它是"谁有权用这个机器人"，不是模型/连接参数）。
     *
     * 为什么不放进 [Config]：`Config` 每次 `save` 都是整份覆盖，而 owner 是**扫码那一刻**
     * 才产生的外部身份；混进 Config 会让"改个目标号码"这种操作不小心把它带走。
     */
    private const val KEY_OWNER_OPENID = "owner_openid"

    private var appContext: Context? = null

    private val _config = MutableStateFlow(
        Config(
            mode = Mode.Official,
            baseUrl = "",
            token = "",
            target = Target.Private,
            targetId = "",
            displayName = "",
        ),
    )
    val config: StateFlow<Config> = _config

    private val _autoConversationId = MutableStateFlow(0L)

    /**
     * 「自动」当前沿用的会话 id；`0` = 还没有。
     *
     * 以前这个 id 只存在 [SESSION_PREFS] 里，UI 完全看不见，于是设置页只能写
     * 「第一次收到消息时新建」——用户看到的是"说会自动，但从没告诉我落到哪"。
     * 提成 StateFlow 之后，隐式状态变成可显示的（设置页能写出「沿用上次的 QQ 会话：《…》」）。
     */
    val autoConversationId: StateFlow<Long> = _autoConversationId.asStateFlow()

    /** 更新自动会话 id 并落盘。`0` 表示忘掉（例如那个会话被删了）。 */
    internal fun setAutoConversationId(id: Long) {
        _autoConversationId.value = id
        val ctx = appContext ?: return
        ctx.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_SESSION_CONVERSATION, id)
            .apply()
    }

    private val _ownerOpenId = MutableStateFlow("")

    /**
     * 扫码绑定者。非空时**只回应这个 openid**；为空 = 不启用 ACL（兼容老配置与手工填写）。
     *
     * 为什么需要它：官方机器人的 openid 是公开可得的入口（群里有 @ 权限的人都能触发），
     * 没有白名单时任何人都能把它当免费的模型入口刷额度。扫码绑定天然知道"是谁绑的"，
     * 所以把那个人记下来当白名单，是零额外操作就能拿到的安全收益。
     */
    val ownerOpenId: StateFlow<String> = _ownerOpenId.asStateFlow()

    /**
     * 更新绑定者并落盘。空串表示解除白名单限制。
     *
     * 为什么是 `public` 而不是像 [setAutoConversationId] 那样 `internal`：
     * 唯一调用方是 `feature:settings` 的扫码绑定回调，跨模块调用看不到 `internal`。
     * 为了"保持 internal"而多包一层公开转发函数，只会让调用链更难读。
     *
     * **调用顺序要求**：必须在 `save()` **之后**调用 —— `save()` 里"切环境会清 owner"，
     * 顺序反了刚扫到的绑定者会被立刻抹掉。
     */
    fun setOwnerOpenId(openId: String) {
        _ownerOpenId.value = openId.trim()
        val ctx = appContext ?: return
        ctx.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_OWNER_OPENID, _ownerOpenId.value)
            .apply()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun init(context: Context) {
        if (appContext != null) return
        val ctx = context.applicationContext
        appContext = ctx
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _config.value = Config(
            baseUrl = prefs.getString(KEY_BASE_URL, "").orEmpty(),
            token = prefs.getString(KEY_TOKEN, null)?.let {
                runCatching { SecretBox.decrypt(it) }.getOrNull()
            }.orEmpty(),
            target = runCatching {
                Target.valueOf(prefs.getString(KEY_TARGET, null) ?: Target.Private.name)
            }.getOrDefault(Target.Private),
            targetId = prefs.getString(KEY_TARGET_ID, "").orEmpty(),
            displayName = prefs.getString(KEY_NAME, "").orEmpty(),
            wsUrl = normalizeWsUrl(prefs.getString(KEY_WS_URL, "").orEmpty()),
            mode = runCatching {
                Mode.valueOf(prefs.getString(KEY_MODE, null) ?: Mode.Official.name)
            }.getOrDefault(Mode.Official),
            appId = prefs.getString(KEY_APP_ID, "").orEmpty(),
            appSecret = prefs.getString(KEY_APP_SECRET, null)?.let {
                runCatching { SecretBox.decrypt(it) }.getOrNull()
            }.orEmpty(),
            autoReply = prefs.getBoolean(KEY_AUTO_REPLY, false),
            // 老版本没有这个键：按"沙箱"读，才符合纯手机场景
            sandbox = prefs.getBoolean(KEY_SANDBOX, true),
            agentTools = prefs.getBoolean(KEY_AGENT_TOOLS, false),
            conversationId = prefs.getLong(KEY_CONVERSATION_ID, 0L),
            // 老版本没有这个键：默认关 —— QQ 里等 20 秒等于没回
            thinking = prefs.getBoolean(KEY_THINKING, false),
        )
        // 自动会话 id 在另一条 prefs 里（运行时产物，不跟配置一起存）
        _autoConversationId.value = ctx
            .getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_SESSION_CONVERSATION, 0L)
        // 绑定者同一条 prefs；老版本没有这个键 → 空串 = 不启用 ACL
        _ownerOpenId.value = ctx
            .getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_OWNER_OPENID, "").orEmpty()
    }

    fun save(config: Config) {
        val normalized = config.copy(
            baseUrl = normalizeBaseUrl(config.baseUrl),
            // WS 地址同样要归一化：用户把 HTTP 地址粘进「WS 地址」框是很常见的操作，
            // 直接拿 http:// 去 newWebSocket 会抛 IllegalArgumentException
            wsUrl = normalizeWsUrl(config.wsUrl),
            targetId = config.targetId.trim(),
        )
        // sandbox 是否变化必须在覆盖 _config **之前**比较，所以先算再赋值
        val sandboxChanged = _config.value.sandbox != normalized.sandbox
        // 模式切换同理：官方模式的绑定者是 openid，OneBot 是 QQ 号，两者不能互相顶替，
        // 留着旧值只会让新模式下「谁都不能用」（包括绑定者自己）
        val modeChanged = _config.value.mode != normalized.mode
        _config.value = normalized
        // 切环境必须清绑定者：同一个人的 openid 在沙箱与正式是**两个不同的值**，
        // 留着旧值的结果是新环境下"谁都不能用"——包括绑定者自己
        if (sandboxChanged || modeChanged) setOwnerOpenId("")
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_BASE_URL, normalized.baseUrl)
            putString(
                KEY_TOKEN,
                if (normalized.token.isBlank()) "" else SecretBox.encrypt(normalized.token),
            )
            putString(KEY_TARGET, normalized.target.name)
            putString(KEY_TARGET_ID, normalized.targetId)
            putString(KEY_NAME, normalized.displayName)
            putString(KEY_WS_URL, normalized.wsUrl)
            putString(KEY_MODE, normalized.mode.name)
            putString(KEY_APP_ID, normalized.appId.trim())
            putString(
                KEY_APP_SECRET,
                if (normalized.appSecret.isBlank()) "" else SecretBox.encrypt(normalized.appSecret),
            )
            putBoolean(KEY_AUTO_REPLY, normalized.autoReply)
            putBoolean(KEY_SANDBOX, normalized.sandbox)
            putBoolean(KEY_AGENT_TOOLS, normalized.agentTools)
            putLong(KEY_CONVERSATION_ID, normalized.conversationId.coerceAtLeast(0L))
            putBoolean(KEY_THINKING, normalized.thinking)
        }.apply()
    }

    fun clear() {
        _config.value = Config(
            mode = Mode.Official,
            baseUrl = "",
            token = "",
            target = Target.Private,
            targetId = "",
            displayName = "",
            // 显式写出来而不是靠默认值：清除配置后 QQ 侧回到「不思考」的最快默认，
            // 与首次安装的体验一致（下面的 prefs.clear() 也会抹掉 KEY_THINKING）
            thinking = false,
        )
        // 自动会话 id 同样是运行时产物：清配置后 UI 要立刻回到「下次新建」的说法
        _autoConversationId.value = 0L
        // 绑定者一并解绑：凭证都清了，留着一个 openid 只会让下一个人以为"这机器人认了别人"
        _ownerOpenId.value = ""
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        // 自动会话 id 是运行时产物：清配置时一并丢掉，否则下次「自动」会跳到上一个
        // 已经没人认领的旧会话里
        ctx.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_SESSION_CONVERSATION)
            .remove(KEY_OWNER_OPENID)
            .apply()
    }

    /** 允许用户只填 `192.168.1.10:3000`：补协议是必须的，否则 URL 解析直接失败。 */
    internal fun normalizeBaseUrl(raw: String): String {
        val value = raw.trim().trimEnd('/')
        if (value.isEmpty()) return ""
        return if (value.startsWith("http://") || value.startsWith("https://")) value
        else "http://$value"
    }

    /**
     * WS 地址归一化，与 [normalizeBaseUrl] 对称。
     *
     * 为什么必须做：OkHttp 的 `newWebSocket` 只接受 `ws://` / `wss://`，
     * 用户填 `192.168.1.10:3001`（最常见）或把 REST 地址 `http://…` 粘进来时，
     * `Request.Builder().url()` 会直接抛异常 —— 表现就是「点开始接收后永远停在连接中」。
     * 末尾斜杠不去：WS 地址的路径本身有意义（如 `/onebot/v11/ws`）。
     */
    internal fun normalizeWsUrl(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) return ""
        val lower = value.lowercase()
        return when {
            lower.startsWith("ws://") || lower.startsWith("wss://") -> value
            lower.startsWith("https://") -> "wss://" + value.substring("https://".length)
            lower.startsWith("http://") -> "ws://" + value.substring("http://".length)
            else -> "ws://$value"
        }
    }

    /**
     * 发送文本消息。
     *
     * OneBot 11 的响应体是 `{"status":"ok","retcode":0,...}`，`retcode != 0` 视为失败——
     * 只看 HTTP 200 不够：很多实现业务失败也回 200。
     */
    suspend fun send(text: String, target: Config = _config.value): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (!target.configured) {
                return@withContext Result.failure(
                    IllegalStateException("QQ 机器人未配置：请到 设置 → 插件 → QQ 机器人 填写地址与目标号码"),
                )
            }
            val content = text.trim()
            if (content.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("消息内容不能为空"))
            }
            if (content.length > 4_000) {
                return@withContext Result.failure(IllegalArgumentException("消息过长（上限 4000 字）"))
            }

            val path = when (target.target) {
                Target.Private -> "/send_private_msg"
                Target.Group -> "/send_group_msg"
            }
            val idKey = when (target.target) {
                Target.Private -> "user_id"
                Target.Group -> "group_id"
            }
            // user_id / group_id 在多数实现里接受字符串，统一按字符串传，避免大整数精度问题
            val body = JSONObject()
                .put(idKey, target.targetId.trim())
                .put("message", content)
                .toString()

            val request = Request.Builder()
                .url(target.baseUrl + path)
                .apply { if (target.token.isNotBlank()) addHeader("Authorization", "Bearer ${target.token}") }
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(jsonMedia))
                .build()

            runCatching {
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        error("HTTP ${response.code}：${text.take(160)}")
                    }
                    val retcode = runCatching { JSONObject(text).optInt("retcode", 0) }.getOrDefault(0)
                    if (retcode != 0) {
                        val message = runCatching {
                            JSONObject(text).optString("message").orEmpty()
                        }.getOrDefault("")
                        error("协议端返回 retcode=$retcode ${message.take(120)}")
                    }
                }
            }.map { }
        }

    /** 连通性自检：调 `/get_login_info`，能拿到昵称说明地址与鉴权都对。 */
    suspend fun probe(target: Config = _config.value): Result<String> = withContext(Dispatchers.IO) {
        if (target.baseUrl.isBlank()) {
            return@withContext Result.failure(IllegalStateException("请先填写协议端地址"))
        }
        runCatching {
            val request = Request.Builder()
                .url(target.baseUrl + "/get_login_info")
                .apply { if (target.token.isNotBlank()) addHeader("Authorization", "Bearer ${target.token}") }
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val json = JSONObject(body)
                if (json.optInt("retcode", 0) != 0) {
                    error("retcode=${json.optInt("retcode")}")
                }
                val data = json.optJSONObject("data")
                data?.optString("nickname").orEmpty().ifBlank { "已连接" }
            }
        }
    }

    /** 供 UI 展示：不暴露 token 原文，只告诉用户「有没有填」。 */
    fun maskedToken(token: String): String =
        if (token.isBlank()) "未填写" else "已保存（${token.length} 位）"
}
