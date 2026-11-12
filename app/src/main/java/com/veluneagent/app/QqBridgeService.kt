package com.veluneagent.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.veluneagent.core.data.im.QqAgentRunner
import com.veluneagent.core.data.im.QqAliasStore
import com.veluneagent.core.data.im.QqBot
import com.veluneagent.core.data.im.QqLink
import com.veluneagent.core.data.im.QqOfficial
import com.veluneagent.core.data.im.QqResponder
import com.veluneagent.core.data.im.QqText
import com.veluneagent.core.data.system.LiveUpdateNotifier
import com.veluneagent.core.design.l10n.tr
import com.veluneagent.core.design.l10n.trf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * QQ 机器人的常驻连接。
 *
 * 必须用前台服务：Android 会把后台进程的 WebSocket 掐掉，收不到消息的「机器人」等于没有。
 * 代价是一条常驻通知——这是系统规则，绕不过去。
 *
 * 两种接入方式共用同一个回复器（[QqResponder]），只有「连哪里、往哪发」不同：
 * - 官方：直连 QQ 网关，手机单独可用；
 * - OneBot：连 NapCat，需要另一台设备。
 */
class QqBridgeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var oneBotLink: QqLink? = null
    private var officialLink: QqOfficial.Link? = null
    private var responder: QqResponder? = null

    /** Android 16 实时动态：常驻通知走这里构建，老系统自动退化为普通常驻通知。 */
    private val liveUpdate = LiveUpdateNotifier(this)

    /**
     * 上一次真正建连时用的配置指纹。
     *
     * link 在构造时就把地址/凭证/环境捕获了，改配置后不重建 = 一直用旧值重连，
     * 用户侧表现就是"改完地址点重试，什么也没发生"。所以每次 start 都比对这个指纹。
     */
    private var runningFingerprint: String? = null

    /**
     * 非空表示「主动停止」并带原因（如 onTimeout 达到系统运行上限）。
     *
     * 为什么需要它：`onTimeout` 里写完原因就 `stopSelf()`，紧接着走 `onDestroy()`；
     * 如果 `onDestroy` 照旧 `setMessage(null)`，刚写进设置页的停止原因立刻被抹掉，
     * 用户看到的就是「机器人静默消失，什么提示也没有」。
     */
    private var stopReason: String? = null

    /**
     * 常驻通知计时器的起点（毫秒），两者都必须是显式记录的时间戳。
     *
     * 通知的 `when` 默认取构建时刻，而状态每刷新一次就重新 build 一次；
     * 不固定起点的话，用户看到的「已运行时长」会在每次状态变化时归零甚至往回跳。
     * - [connectedAt] 非空 = 已连上，头部显示连接已持续时长；
     * - 为空 = 仍在建连，头部显示本次尝试已耗时。
     */
    private var connectedAt: Long? = null
    private var attemptStartedAt = System.currentTimeMillis()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        val app = application as VeluneApplication
        responder = QqResponder(
            context = this,
            store = app.chatStore,
            // 只读 Agent：能力边界在 QqAgentRunner 里裁，这里只负责把依赖接上
            agentRunner = QqAgentRunner(
                catalog = app.toolCatalog,
                executor = app.toolExecutor,
                runStore = app.agentRunStore,
            ),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val config = QqBot.config.value
        if (!config.canReceive) {
            stopLinks()
            runningFingerprint = null
            QqBridge.setState(QqLink.State.Error)
            val reason =
                if (config.mode == QqBot.Mode.Official) tr("缺少 AppID / AppSecret") else tr("未填写 WS 地址")
            // 同样交给 stopReason：stopSelf() 之后 onDestroy 会用 stopReason 覆盖，不能让它变空
            stopReason = reason
            QqBridge.setMessage(reason)
            stopSelf()
            return START_NOT_STICKY
        }

        // 真的要开始连了：上一次主动停止的原因到此作废
        stopReason = null

        // 配置没变且连接还活着时不要重置状态：否则"再点一次保存"会把界面从
        // 「已连接」打回「连接中…」，而网关不会再推一次 READY，界面就永远停在连接中
        if (fingerprintOf(config) != runningFingerprint) {
            stopLinks()
            runningFingerprint = fingerprintOf(config)
            // 新一次连接尝试：计时器从此刻重新起算
            connectedAt = null
            attemptStartedAt = System.currentTimeMillis()
            QqBridge.setState(QqLink.State.Connecting)
        }

        val official = config.mode == QqBot.Mode.Official
        // 重复点「开始接收」时连接可能还活着（fingerprint 未变，状态不会重置）：
        // 不能一律按「正在连接…」重建，否则卡片会被打回建连态，
        // 计时器与进度轴也跟着一起回退。
        val connected = connectedAt != null
        val text = when {
            connected && official -> TextOfficialConnected
            connected -> TextOneBotConnected
            official -> TextOfficialConnecting
            else -> TextOneBotConnecting
        }
        startForeground(NotificationId, statusNotification(text, connected = connected))

        when (config.mode) {
            QqBot.Mode.Official -> startOfficial(config)
            QqBot.Mode.OneBot -> startOneBot(config)
        }
        return START_STICKY
    }

    /** 配置指纹只用于"变了没有"的判断，不含明文比对逻辑，密钥取 hash。 */
    private fun fingerprintOf(config: QqBot.Config): String = buildString {
        append(config.mode.name).append('|')
        append(config.sandbox).append('|')
        append(config.appId.trim()).append('|')
        append(config.appSecret.hashCode()).append('|')
        append(config.baseUrl.trim()).append('|')
        append(config.token.hashCode()).append('|')
        append(config.wsUrl.trim())
    }

    private fun stopLinks() {
        oneBotLink?.stop()
        oneBotLink = null
        officialLink?.stop()
        officialLink = null
    }

    // ---------- 官方 Bot ----------

    private fun startOfficial(config: QqBot.Config) {
        if (officialLink != null) return
        officialLink = QqOfficial.Link(
            scope = scope,
            appId = config.appId,
            appSecret = config.appSecret,
            sandbox = config.sandbox,
            onIncoming = { incoming -> handleOfficial(incoming) },
            onState = { state, message ->
                QqBridge.setState(state)
                QqBridge.setMessage(message)
                publishState(state, message, TextOfficialConnecting, TextOfficialConnected)
            },
        ).also { it.start() }
    }

    private suspend fun handleOfficial(incoming: QqOfficial.Incoming) {
        val config = QqBot.config.value

        // 绑定者限定：必须在**任何模型调用之前**，否则第三方只要给机器人发一条消息就能刷额度。
        // 非绑定者的消息**静默丢弃**（只写设置页一行小字，不回 QQ）：
        // 一旦回一句"你没权限"，对方就知道这台机器人活着，可以继续当探针试。
        //
        // 旧行为是「owner 为空 = 不设限」，而手工填 AppID/AppSecret 的路径从不写 owner，
        // 等于默认对所有人开放。改成首次信任（TOFU）：第一个跟机器人说话的人成为绑定者，
        // 写盘后立刻生效；想换人就在设置里清除配置重新绑定。
        var owner = QqBot.ownerOpenId.value
        if (owner.isBlank()) {
            QqBot.setOwnerOpenId(incoming.openId)
            owner = incoming.openId
            Log.d(Tag, "首次绑定者已写入：…${incoming.openId.takeLast(6)}")
            QqBridge.setMessage("已把第一位发消息的用户设为绑定者，其他来源将被忽略")
        } else if (incoming.openId != owner) {
            Log.d(Tag, "未授权来源已忽略：…${incoming.openId.takeLast(6)}")
            QqBridge.setMessage("收到未授权来源的消息，已忽略")
            return
        }

        if (!config.autoReply) {
            // 静默 return 会让用户以为"连上了但机器人不理人"；前台服务本来就有常驻通知，
            // 顺手把「收到消息但你没开自动回复」说清楚
            QqBridge.setMessage("已收到消息，但自动回复未开启")
            notify(statusNotification("自动回复未开启：收到消息未处理", connected = true))
            return
        }

        // 图片/表情/语音：不消耗模型额度，但**必须**回一句人话。
        // 以前这些消息在解析阶段就被丢掉，对面看到的是彻底的"已读不回"。
        if (incoming.hasNonText) {
            if (!withinPassiveWindow(incoming)) {
                QqBridge.setMessage("有一条消息超过被动回复窗口，未发送")
                return
            }
            sendOfficial(config, incoming, tr(NonTextNotice))
                .onFailure { QqBridge.setMessage(trf("发送失败：%s", it.message ?: tr("未知错误"))) }
            return
        }

        val reply = responder?.handle(
            text = incoming.text,
            // 官方只给 openid 不给昵称；末 6 位切片不可读、会撞号，改用本地别名表
            // （群聊键带 group_openid：member_openid 是按群授权的，同人在两个群可能是不同值）
            senderName = QqAliasStore.nameOf(
                QqAliasStore.keyOf(incoming.isGroup, incoming.groupOpenId, incoming.openId),
            ),
            isGroup = incoming.isGroup,
            useTools = config.agentTools,
            conversationId = config.conversationId,
            thinking = config.thinking,
        ) ?: return
        reply.onSuccess { r ->
            // QQ 的上限是字节：交给 QqText 统一裁（含后缀预算、不劈代理对）
            val body = QqText.truncate(r.text)
            // 被动回复窗口：群 5 分钟 / 单聊 60 分钟。超窗发送必然失败，
            // 与其打一次注定失败的接口，不如本地就说清楚（模型可能已经想了 20 秒）
            if (!withinPassiveWindow(incoming)) {
                QqBridge.setMessage("有一条消息超过被动回复窗口，未发送")
                return@onSuccess
            }
            // 被动回复：带上 msg_id，超出有效期会发送失败
            sendOfficial(config, incoming, body)
                .onFailure { QqBridge.setMessage(trf("发送失败：%s", it.message ?: tr("未知错误"))) }
        }.onFailure {
            QqBridge.setMessage(trf("回复失败：%s", it.message ?: tr("未知错误")))
            sendOfficialNotice(config, incoming)
        }
    }

    /**
     * 被动回复窗口判断。
     *
     * [QqOfficial.Incoming.timestamp] 为 0 表示事件里没有可解析的时间，
     * 这时**不做判断**：宁可不判，也不能因为解析失败把正常消息误杀。
     */
    private fun withinPassiveWindow(incoming: QqOfficial.Incoming): Boolean {
        if (incoming.timestamp <= 0L) return true
        val elapsed = System.currentTimeMillis() - incoming.timestamp
        val limitMs = if (incoming.isGroup) 5 * 60_000L else 60 * 60_000L
        return elapsed <= limitMs
    }

    /** 往这条消息的来源发一段文本（群或单聊），被动回复带上 msg_id。 */
    private suspend fun sendOfficial(
        config: QqBot.Config,
        incoming: QqOfficial.Incoming,
        text: String,
    ): Result<Unit> {
        val token = QqOfficial.accessToken(config.appId, config.appSecret).getOrNull()
            ?: return Result.failure(IllegalStateException(tr("获取凭证失败，消息未发出")))
        return if (incoming.isGroup) {
            val group = incoming.groupOpenId
            if (group == null) {
                Result.failure(IllegalStateException(tr("群消息缺少 group_openid")))
            } else {
                QqOfficial.sendGroup(token, group, text, incoming.messageId, config.sandbox)
            }
        } else {
            QqOfficial.sendC2C(token, incoming.openId, text, incoming.messageId, config.sandbox)
        }
    }

    /**
     * 失败时也必须让**对面**收到一句话。
     *
     * 以前这里什么都不发，只有设置页一行小字：用户那边是"消息发出去了、机器人永远不回"，
     * 比收到一句固定话术糟得多。技术原因（没配服务商、缺 Key…）只进 logcat 与设置页，
     * 不发给 QQ 对面的第三方。
     */
    private suspend fun sendOfficialNotice(config: QqBot.Config, incoming: QqOfficial.Incoming) {
        sendOfficial(config, incoming, tr(FailureNotice))
            .onFailure { Log.w(Tag, "失败话术也没发出去：${it.message}") }
    }

    // ---------- OneBot ----------

    private fun startOneBot(config: QqBot.Config) {
        if (oneBotLink != null) return
        oneBotLink = QqLink(
            scope = scope,
            onIncoming = { incoming -> handleOneBot(incoming) },
            onState = { state, message ->
                QqBridge.setState(state)
                QqBridge.setMessage(message)
                publishState(state, message, TextOneBotConnecting, TextOneBotConnected)
            },
        ).also { it.start(config.wsUrl, config.token) }
    }

    private suspend fun handleOneBot(incoming: QqLink.Incoming) {
        val config = QqBot.config.value

        // 与官方路径同一条规则，只是 OneBot 没有 openId，用 QQ 号充当绑定者。
        // 旧实现这里**完全没有校验**：任何好友、或同群成员 @ 一句，都能用用户的 Key
        // 驱动模型、读 config.conversationId 指向的私人会话，并把结果发到群里。
        var owner = QqBot.ownerOpenId.value
        if (owner.isBlank()) {
            QqBot.setOwnerOpenId(incoming.userId)
            owner = incoming.userId
            Log.d(Tag, "首次绑定者已写入（OneBot）：…${incoming.userId.takeLast(4)}")
            QqBridge.setMessage("已把第一位发消息的用户设为绑定者，其他来源将被忽略")
        } else if (incoming.userId != owner) {
            Log.d(Tag, "未授权来源已忽略（OneBot）：…${incoming.userId.takeLast(4)}")
            QqBridge.setMessage("收到未授权来源的消息，已忽略")
            return
        }

        if (!config.autoReply) {
            QqBridge.setMessage("已收到消息，但自动回复未开启")
            notify(statusNotification("自动回复未开启：收到消息未处理", connected = true))
            return
        }

        // 纯图片/表情/语音：不花模型额度，但要让对面知道「收到了，只是看不懂图」
        if (incoming.hasNonText) {
            sendOneBot(config, incoming, tr(NonTextNotice))
                .onFailure { QqBridge.setMessage(trf("发送失败：%s", it.message ?: tr("未知错误"))) }
            return
        }

        val reply = responder?.handle(
            text = incoming.text,
            // 有真实昵称就用它；只有昵称为空才回落别名表（OneBot 侧昵称是可信的）
            senderName = incoming.senderName.ifBlank {
                QqAliasStore.nameOf(
                    QqAliasStore.keyOf(incoming.isGroup, incoming.groupId, incoming.userId),
                )
            },
            isGroup = incoming.isGroup,
            useTools = config.agentTools,
            conversationId = config.conversationId,
            thinking = config.thinking,
        ) ?: return
        reply.onSuccess { r ->
            val body = QqText.truncate(r.text)
            sendOneBot(config, incoming, body)
                .onFailure { QqBridge.setMessage(trf("发送失败：%s", it.message ?: tr("未知错误"))) }
        }.onFailure {
            QqBridge.setMessage(trf("回复失败：%s", it.message ?: tr("未知错误")))
            sendOneBotNotice(config, incoming)
        }
    }

    /** 往这条消息的来源发一段文本（群或私聊）。 */
    private suspend fun sendOneBot(
        config: QqBot.Config,
        incoming: QqLink.Incoming,
        text: String,
    ): Result<Unit> {
        val target = config.copy(
            target = if (incoming.isGroup) QqBot.Target.Group else QqBot.Target.Private,
            targetId = incoming.groupId ?: incoming.userId,
        )
        return QqBot.send(text, target)
    }

    /** OneBot 侧的失败话术；发送本身也可能失败，所以整体 runCatching，不能再往外抛。 */
    private suspend fun sendOneBotNotice(config: QqBot.Config, incoming: QqLink.Incoming) {
        runCatching { sendOneBot(config, incoming, tr(FailureNotice)) }
            .onFailure { Log.w(Tag, "失败话术也没发出去", it) }
    }

    // 截断逻辑已搬进 core:data 的 QqText（纯函数，可单测回归）：
    // 它有三个安静的错法（按字符裁 / 后缀不算预算 / 劈开代理对），留在 Service 私有方法里
    // 就等于永远只能靠真机发超长消息去试。

    override fun onDestroy() {
        stopLinks()
        runningFingerprint = null
        scope.cancel()
        QqBridge.setState(QqLink.State.Stopped)
        // onTimeout 这类「主动停止」已经把原因写进设置页了。这里若照旧 setMessage(null)，
        // 用户看到的就是「机器人静默消失、没有任何原因」。
        QqBridge.setMessage(stopReason)
        stopReason = null
        super.onDestroy()
    }

    /**
     * Android 15（API 35）对 `dataSync` 型前台服务限制 24 小时内累计 6 小时。
     *
     * 到点后系统会停掉服务；如果应用不实现这个回调，进程会被判定为异常退出，
     * 用户之后在 App 里点「开始接收」也会反复失败 —— 表现就是"机器人彻底坏了，
     * 重启手机才能好"。这里主动收尾：把状态置为 Stopped、写清原因、下发一条通知，
     * 然后自己 stopSelf（用户重新开启时会重新计时）。
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        if (Build.VERSION.SDK_INT < 35) return
        stopLinks()
        runningFingerprint = null
        QqBridge.setState(QqLink.State.Stopped)
        val reason = tr("系统限制后台运行时长已达上限，请在 App 内重新开启 QQ 机器人")
        // 交给 stopReason：stopSelf() 之后立刻走 onDestroy，那里要用它保住这条原因，
        // 否则设置页会在销毁瞬间被清空（通知还在，但 App 里看不到为什么停）
        stopReason = reason
        QqBridge.setMessage(reason)
        // 用独立 id + ongoing=false：下面 stopForeground(REMOVE) 会把常驻通知（id 4211）摘掉，
        // 告知通知若共用同一个 id 就会被一起删掉 —— 用户什么都看不到，机器人静默消失。
        // ongoing=false 让它可手动划掉，不该留一条永远擦不掉的告警。
        runCatching {
            // ongoing=false：已停止的活动不再请求提升，也不会被系统当实时动态卡片
            notify(
                AlertNotificationId,
                liveUpdate.build(
                    channelId = ChannelId,
                    title = tr("QQ 机器人已停止"),
                    text = tr("已停止：后台时长达到系统上限，请重新开启"),
                    ongoing = false,
                ),
            )
        }
        scope.cancel()
        stopForegroundCompat()
        // 故意不调 super.onTimeout：默认实现会把服务判定为异常终止（进程被杀），
        // 那正是我们要避免的"用户点开始接收也救不回来"。
        stopSelf(startId)
    }

    /** API 24 起 `stopForeground(Int)` 才有 REMOVE 语义；旧签名已废弃，故分版本调用。 */
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(ChannelId) != null) return
        manager.createNotificationChannel(
            NotificationChannel(ChannelId, tr("QQ 机器人"), NotificationManager.IMPORTANCE_LOW).apply {
                description = tr("保持与 QQ 的连接")
                setShowBadge(false)
            },
        )
    }

    /**
     * 记录「已连上」的时刻，作为通知计时器的起点。
     *
     * 只在第一次进入 Connected 时记：同一轮连接里后续的状态刷新
     * （自动回复未开启、收到未授权消息等）必须沿用同一个起点，
     * 否则秒表会一次次归零。
     */
    private fun markConnected() {
        if (connectedAt == null) connectedAt = System.currentTimeMillis()
    }

    /**
     * 把连接状态映射到常驻通知。
     *
     * 以前只在 Connected 时刷新通知，导致两种「卡片谎报状态」：
     * 断线重连期间卡片仍显示「已连接」，而用户点开设置页看到的是「连接断开」；
     * 连接失败（Error）时卡片也一直停在建连进度上。
     *
     * 这里把 Connected / Connecting / Error 三种状态都映射过去。Stopped 不处理：
     * 停止流程由 onDestroy 与 onTimeout 负责收掉前台通知。
     */
    private fun publishState(
        state: QqLink.State,
        message: String?,
        connectingText: String,
        connectedText: String,
    ) {
        when (state) {
            QqLink.State.Connected -> {
                markConnected()
                notify(statusNotification(connectedText, connected = true))
            }
            QqLink.State.Connecting -> {
                // 进入建连：计时器回到「本次尝试」，进度轴回到建连段
                connectedAt = null
                notify(statusNotification(message ?: connectingText, connected = false))
            }
            QqLink.State.Error -> {
                connectedAt = null
                notify(statusNotification(message ?: connectingText, connected = false))
            }
            QqLink.State.Stopped -> Unit
        }
    }

    /**
     * 常驻状态通知（Android 16 实时动态）。
     *
     * 连接被建模成一条用户发起的进行中旅程：
     * - 连接中：进度轴停在 0-40 的建连段（确定进度，几十秒内会走完）；
     * - 连接后：改为**不确定进度轴**——保活没有可量化的终点，再挂一条停在 40% 的
     *   进度条，等于告诉用户「有件事永远做不完」。
     *
     * 头部计时器的起点必须显式传入（[connectedAt] / [attemptStartedAt]），
     * 否则每次刷新都会归零。
     *
     * API < 36 由 [LiveUpdateNotifier] 退化为普通常驻通知：连接中带系统进度条，
     * 连接后不画任何进度。
     */
    private fun statusNotification(
        text: String,
        connected: Boolean,
        title: String = "QQ 机器人运行中",
    ): Notification = liveUpdate.build(
        channelId = ChannelId,
        // 常量文案保持中文原文，在这里统一过 tr；调用方传入的串若已译过则幂等
        title = tr(title),
        text = tr(text),
        usesChronometer = true,
        whenMillis = connectedAt ?: attemptStartedAt,
        smallIcon = android.R.drawable.stat_notify_chat,
        progress = if (connected) null else SegmentConnecting,
        indeterminate = connected,
        segments = if (connected) {
            null
        } else {
            listOf(
                LiveUpdateNotifier.Segment(SegmentConnected, ColorConnecting),
                LiveUpdateNotifier.Segment(100 - SegmentConnected, ColorIdle),
            )
        },
        points = if (connected) {
            null
        } else {
            listOf(LiveUpdateNotifier.Point(SegmentConnected, ColorIdle))
        },
    )

    private fun notify(notification: Notification) = notify(NotificationId, notification)

    private fun notify(id: Int, notification: Notification) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(id, notification)
    }

    private companion object {
        const val ChannelId = "qq_bridge"
        /** 前台常驻通知：stopForeground(REMOVE) 摘的就是这个 id。 */
        const val NotificationId = 4211
        /** 一次性告知（如超时停止）：必须与常驻通知分 id，否则会被一并摘掉。 */
        const val AlertNotificationId = 4212
        const val Tag = "Velune-QQ"

        /** 常驻通知文案：官方与 OneBot 两种接入各一套，避免同一句话在多处漂移。 */
        const val TextOfficialConnecting = "正在连接 QQ 网关…"
        const val TextOfficialConnected = "已连接 QQ 网关，等待消息"
        const val TextOneBotConnecting = "正在连接协议端…"
        const val TextOneBotConnected = "已连接协议端，等待消息"

        /** 实时动态进度轴上的里程碑位置（0..100）：40 = 建连完成。 */
        const val SegmentConnected = 40
        /** 连接中时进度条走到的位置。 */
        const val SegmentConnecting = 15

        /** ARGB 颜色：建连段（连接中黄色）、未完成段与里程碑点灰。 */
        const val ColorConnecting = 0xFFF5A623.toInt()
        const val ColorIdle = 0xFF9AA0A6.toInt()

        /**
         * 回复彻底失败时对面看到的唯一一句话。
         *
         * 固定话术，不带任何上游细节：HTTP 状态码、`Invalid API key` 这类原文
         * 发给 QQ 对面的第三方既帮不上忙，又等于把内部信息送出去。
         */
        const val FailureNotice = "我这会儿答不上来，稍后再试"

        /**
         * 收到非文本消息（图片/表情/语音/文件）时的统一话术。
         *
         * 为什么不花钱让模型去"看图"：QQ 侧给到的只是一个附件 id，
         * 模型拿不到实际内容，硬问只会得到一段编出来的描述。
         * 但也不能不回：一句"看不懂图"比彻底的沉默体验好得多。
         */
        const val NonTextNotice = "我暂时只看得懂文字，图片/表情回 App 里发给我吧"
    }
}

/** 供 UI 观察连接状态，并负责启停服务。 */
object QqBridge {

    private val _state = MutableStateFlow(QqLink.State.Stopped)
    val state: StateFlow<QqLink.State> = _state

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    /** 只允许同模块的服务更新状态：UI 侧仍然是只读的 StateFlow。 */
    internal fun setState(value: QqLink.State) {
        _state.value = value
    }

    internal fun setMessage(value: String?) {
        _message.value = value
    }

    fun start(context: Context) {
        val intent = Intent(context, QqBridgeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, QqBridgeService::class.java))
        _state.value = QqLink.State.Stopped
        _message.value = null
    }

    val running: Boolean
        get() = _state.value == QqLink.State.Connected || _state.value == QqLink.State.Connecting
}
