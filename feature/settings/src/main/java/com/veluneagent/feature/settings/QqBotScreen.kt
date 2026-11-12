package com.veluneagent.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.veluneagent.core.data.Conversation
import com.veluneagent.core.data.im.QqAliasStore
import com.veluneagent.core.data.im.QqBindController
import com.veluneagent.core.data.im.QqBot
import com.veluneagent.core.data.im.QqLink
import com.veluneagent.core.data.im.QqOfficial
import com.veluneagent.core.design.component.VxCard
import com.veluneagent.core.design.component.VxFormCard
import com.veluneagent.core.design.component.VxRow
import com.veluneagent.core.design.component.VxSectionLabel
import com.veluneagent.core.design.component.VxSubPageScaffold
import com.veluneagent.core.design.component.VxSwitch
import com.veluneagent.core.design.l10n.tr
import com.veluneagent.core.design.l10n.trf
import com.veluneagent.core.design.theme.VxSpace
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * QQ 机器人配置。
 *
 * 与设置主页、应用偏好等页面共用同一套组件：顶栏、列表行（56dp / 17sp Medium）、
 * 无分割线卡片，表单块走 [VxFormCard]，保证全 App 的文字左缘与行高一致。
 *
 * 输入框与按钮保留 MIUIX 控件——它们是"控件"不是"列表行"。
 *
 * 两种接入方式的约束差别很大，所以第一屏就把差别写出来：
 * - **官方 Bot**：只用手机就能跑，但要去 q.qq.com 注册，群里只有被 @ 时才收得到消息；
 * - **OneBot**：功能更全（能收群内全部消息、能用真实 QQ 号），但需要一台常开的设备跑协议端。
 */
@Composable
fun QqBotScreen(
    config: QqBot.Config,
    onSave: (QqBot.Config) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** 可选的会话列表（最近 N 条），用于「回复到哪个会话」 */
    conversations: List<Conversation> = emptyList(),
    /**
     * 会话列表是否已读完首帧。
     *
     * 冷启动时 [conversations] 先给空列表，此时不能断言"绑定的会话被删了"——
     * 否则已绑定会话的用户一进页面就闪一下红字误报。
     */
    conversationsLoaded: Boolean = true,
    /**
     * 「自动」当前沿用的会话 id；`0` = 还没有。
     *
     * 不传也能跑（默认 0），但那样设置页只能写「第一次收到消息时新建」——
     * 而实际可能一直在沿用某条旧会话，用户永远看不到自己说的话落到哪去了。
     */
    autoConversationId: Long = 0L,
    bridgeState: QqLink.State = QqLink.State.Stopped,
    bridgeMessage: String? = null,
    onStartBridge: () -> Unit = {},
    onStopBridge: () -> Unit = {},
) {
    val miuix = MiuixTheme.colorScheme
    val type = MiuixTheme.textStyles
    val scope = rememberCoroutineScope()

    var mode by remember(config.mode) { mutableStateOf(config.mode) }
    var appId by remember(config.appId) { mutableStateOf(config.appId) }
    var appSecret by remember(config.appSecret) { mutableStateOf(config.appSecret) }
    var baseUrl by remember(config.baseUrl) { mutableStateOf(config.baseUrl) }
    var token by remember(config.token) { mutableStateOf(config.token) }
    var wsUrl by remember(config.wsUrl) { mutableStateOf(config.wsUrl) }
    var target by remember(config.target) { mutableStateOf(config.target) }
    var targetId by remember(config.targetId) { mutableStateOf(config.targetId) }
    var displayName by remember(config.displayName) { mutableStateOf(config.displayName) }
    var autoReply by remember(config.autoReply) { mutableStateOf(config.autoReply) }
    var sandbox by remember(config.sandbox) { mutableStateOf(config.sandbox) }
    var agentTools by remember(config.agentTools) { mutableStateOf(config.agentTools) }
    var thinking by remember(config.thinking) { mutableStateOf(config.thinking) }
    var conversationId by remember(config.conversationId) { mutableStateOf(config.conversationId) }
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeIsError by remember { mutableStateOf(false) }
    var probing by remember { mutableStateOf(false) }

    // 扫码绑定：控制器挂在页面作用域上（离开页面 = 停轮询）。
    // 复用页面已有的 scope 而不是再造一个：多一个 scope 就多一处"谁在取消它"的问题。
    val bindController = remember { QqBindController(scope) }
    var bindOpen by remember { mutableStateOf(false) }

    LaunchedEffect(notice) {
        if (notice != null) {
            delay(5000)
            notice = null
        }
    }

    fun snapshot(): QqBot.Config = QqBot.Config(
        mode = mode,
        appId = appId,
        appSecret = appSecret,
        baseUrl = baseUrl,
        token = token,
        target = target,
        targetId = targetId,
        displayName = displayName,
        wsUrl = wsUrl,
        autoReply = autoReply,
        sandbox = sandbox,
        agentTools = agentTools,
        conversationId = conversationId.coerceAtLeast(0L),
        thinking = thinking,
    )

    /**
     * 选中的会话还在不在：被删了要明说回落到「QQ 消息」，否则用户只会觉得"绑定没生效"。
     *
     * 必须等首帧读完再判：冷启动时列表为空，直接判会误报一次「已被删除」。
     */
    val selectedMissing =
        conversationsLoaded && conversationId != 0L && conversations.none { it.id == conversationId }

    /** 自动会话对应的会话对象：拿不到就说明它被删了（或者在首帧之外）。 */
    val autoConv = conversations.firstOrNull { it.id == autoConversationId }

    // 别名表：openid 不可读，官方又不给昵称，只能靠本地别名
    val aliases by QqAliasStore.all.collectAsState()
    var editingAliasKey by remember { mutableStateOf<String?>(null) }
    var editingAliasName by remember { mutableStateOf("") }

    val canSave = snapshot().configured
    val running = bridgeState == QqLink.State.Connected || bridgeState == QqLink.State.Connecting

    VxSubPageScaffold(
        title = "QQ 机器人",
        onBack = onBack,
        // 底色由脚手架统一铺（colors.background），不再这里单独 background(miuix.background)
        modifier = modifier.imePadding(),
        actions = {
            Button(
                onClick = {
                    if (canSave) {
                        onSave(snapshot())
                        // 服务在跑时保存：让服务按新配置重建连接，
                        // 否则改完地址/token 再点重试也不会生效
                        if (running) onStartBridge()
                        notice = if (running) tr("已保存，按新配置重连中") else tr("已保存")
                        noticeIsError = false
                    }
                },
                enabled = canSave,
                colors = ButtonDefaults.buttonColorsPrimary(),
                minHeight = 34.dp,
                cornerRadius = 17.dp,
                insideMargin = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.padding(end = 4.dp),
            ) {
                Text(tr("保存"), style = type.button)
            }
        },
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                // 左右各留 12dp：卡片外距与主设置页一致，页内所有左缘都由这一层决定
                .padding(horizontal = VxSpace.cardInset)
                .padding(bottom = 32.dp),
        ) {
            VxSectionLabel("接入方式")

            VxFormCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiuixChip(
                        label = "官方 Bot",
                        selected = mode == QqBot.Mode.Official,
                        onClick = { mode = QqBot.Mode.Official },
                        modifier = Modifier.weight(1f),
                    )
                    MiuixChip(
                        label = "OneBot 协议端",
                        selected = mode == QqBot.Mode.OneBot,
                        onClick = { mode = QqBot.Mode.OneBot },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (mode == QqBot.Mode.Official) {
                        tr("只靠手机就能用：手机主动连 QQ 官方网关，收发都不需要公网地址、不需要协议端、不需要另一台设备。")
                    } else {
                        tr("需要另一台设备常开：在电脑或 NAS 上跑 NapCat，登录一个 QQ 小号，手机连它的接口。")
                    },
                    style = type.body2,
                    color = miuix.onSurfaceVariantSummary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (mode == QqBot.Mode.Official) {
                        tr("代价：要去 q.qq.com 注册机器人拿 AppID/AppSecret；群里只有 @ 它时才收得到消息。")
                    } else {
                        tr("好处：能收群内全部消息、能用真实 QQ 号；代价：那台设备必须一直在线。")
                    },
                    style = type.footnote2,
                    color = miuix.onSurfaceVariantSummary,
                )
            }

            if (mode == QqBot.Mode.Official) {
                VxSectionLabel("运行环境")

                VxFormCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiuixChip(
                            label = "沙箱（推荐）",
                            selected = sandbox,
                            onClick = { sandbox = true },
                            modifier = Modifier.weight(1f),
                        )
                        MiuixChip(
                            label = "正式",
                            selected = !sandbox,
                            onClick = { sandbox = false },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (sandbox) {
                            tr("沙箱环境免 IP 白名单，手机换网也不会失效 —— 只用一部手机时选这个。")
                        } else {
                            tr("正式环境要求把公网 IP 填进白名单（只支持单个 IPv4）。手机 IP 每次切网都会变，不建议。")
                        },
                        style = type.body2,
                        color = miuix.onSurfaceVariantSummary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (sandbox) {
                            tr("代价：只能和「沙箱配置 → 沙箱单聊」名单里的 QQ 号对话（自用场景把自己加进去即可）。")
                        } else {
                            tr("机器人提审上线后才可用；接口报「源IP不在白名单」时先来这里核对。")
                        },
                        style = type.footnote2,
                        color = miuix.onSurfaceVariantSummary,
                    )
                }

                // 扫码绑定放在「机器人凭证」**上方**：扫码是省事的那条路，
                // 手抄 AppID/AppSecret 是它失败后的退路 —— 顺序反过来会让用户先看到一堆要抄的东西
                if (!bindOpen) {
                    VxCard {
                        VxRow(
                            title = "扫码绑定",
                            subtitle = "用手机 QQ 扫一下，自动填好 AppID 与密钥（不用手抄）",
                            onClick = {
                                bindOpen = true
                                bindController.start()
                            },
                        )
                    }
                } else {
                    VxCard {
                        QqBindSheet(
                            controller = bindController,
                            onCancel = {
                                bindOpen = false
                                bindController.cancel()
                            },
                            onBound = { boundAppId, boundSecret, ownerId ->
                                // 顺序固定，四步都不能换：
                                // 1) 先落盘（内部 SecretBox 加密，明文只活这一次调用）
                                onSave(
                                    snapshot().copy(
                                        mode = QqBot.Mode.Official,
                                        appId = boundAppId,
                                        appSecret = boundSecret,
                                    ),
                                )
                                // 2) 再写绑定者 —— save() 里"切环境会清 owner"，
                                //    顺序反了刚扫到的 openid 会被立刻抹掉
                                QqBot.setOwnerOpenId(ownerId)
                                // 3) 同步本页的输入框状态，否则界面上还显示旧的空 AppID
                                appId = boundAppId
                                appSecret = boundSecret
                                bindOpen = false
                                bindController.cancel()
                                // 4) 连接**已经在跑**才重启：没在跑就只提示，别替用户做决定
                                if (running) {
                                    onStartBridge()
                                    notice = tr("绑定成功，已用新凭证重连")
                                } else {
                                    notice = tr("绑定成功，点「开始接收」生效")
                                }
                                noticeIsError = false
                            },
                        )
                    }
                }

                VxSectionLabel("机器人凭证")

                VxFormCard {
                    TextField(
                        value = appId,
                        onValueChange = { appId = it },
                        label = "AppID",
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = appSecret,
                        onValueChange = { appSecret = it },
                        label = tr("AppSecret（只显示一次，只存本机）"),
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Text(
                    text = tr("步骤：q.qq.com 创建机器人 → 抄下 AppID/AppSecret（密钥只显示一次）→「开发管理 → 事件订阅与回调地址」必须选 WebSocket（选 Webhook 时会连上也收不到消息）→「沙箱配置」里把自己的 QQ 号加进沙箱单聊 → 点「开始接收」，然后在 QQ 里给机器人发第一句话。"),
                    style = type.footnote2,
                    color = miuix.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                VxSectionLabel("协议端")

                VxFormCard {
                    TextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = tr("HTTP 地址，如 192.168.1.10:3000"),
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = token,
                        onValueChange = { token = it },
                        label = tr("访问令牌（没有就留空）"),
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = wsUrl,
                        onValueChange = { wsUrl = it },
                        label = tr("WS 地址，如 ws://192.168.1.10:3001（接收消息用）"),
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                VxSectionLabel("默认目标")

                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MiuixChip(
                        label = "私聊",
                        selected = target == QqBot.Target.Private,
                        onClick = { target = QqBot.Target.Private },
                        modifier = Modifier.weight(1f),
                    )
                    MiuixChip(
                        label = "群聊",
                        selected = target == QqBot.Target.Group,
                        onClick = { target = QqBot.Target.Group },
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                VxFormCard {
                    TextField(
                        value = targetId,
                        onValueChange = { targetId = it },
                        label = if (target == QqBot.Target.Group) tr("群号，如 123456") else tr("QQ 号，如 10001"),
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = tr("备注名（可选）"),
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            VxSectionLabel("自动回复")

            VxCard {
                VxRow(
                    title = "收到消息后让模型回复",
                    subtitle = "私聊直接回；群里只回 @ 机器人的消息。回复会写进你选择的会话（默认「QQ 消息」）。",
                    trailing = {
                        VxSwitch(
                            checked = autoReply,
                            onCheckedChange = {
                                autoReply = it
                                // 开关即时生效：与外观页保持一致，不再等顶栏「保存」。
                                // 只落盘这一个字段（用已保存的 config），不带上尚未保存的
                                // 输入框内容——半截密钥不该因为拨了一下开关就被写进存储。
                                onSave(config.copy(autoReply = it))
                            },
                        )
                    },
                )
            }

            // 单独一张 Card：这是「回复质量 vs 延迟」的取舍，与上面的开关不是同一件事。
            // 默认关 —— QQ 里等 20 秒等于没回，与 App 内聊天页的默认值刻意相反。
            VxCard {
                VxRow(
                    title = "深度思考",
                    subtitle = "默认关闭：QQ 里等 20 秒等于没回。开启后更聪明，但明显更慢",
                    trailing = {
                        VxSwitch(
                            checked = thinking,
                            onCheckedChange = {
                                thinking = it
                                onSave(config.copy(thinking = it))
                            },
                        )
                    },
                )
            }

            VxSectionLabel("回复到哪个会话")

            VxCard {
                VxRow(
                    title = "自动（QQ 消息）",
                    subtitle = when {
                        conversationId != 0L -> tr("第一次收到消息时新建「QQ 消息」会话")
                        autoConversationId == 0L -> tr("启用中 · 第一次收到消息时新建「QQ 消息」会话")
                        autoConv != null ->
                            trf("启用中 · 沿用上次的 QQ 会话：《%s》", autoConv.title.ifBlank { tr("未命名会话") })
                        // autoConversationId 还在但查不到会话：多半被删了，下次会新建
                        else -> tr("启用中 · 上次的 QQ 会话已删除，下次会新建「QQ 消息」")
                    },
                    trailing = {
                        if (conversationId == 0L) {
                            Text("✓", style = type.footnote1, color = miuix.primary)
                        }
                    },
                    onClick = { conversationId = 0L },
                )
                conversations.take(15).forEach { conversation ->
                    val selected = conversation.id == conversationId
                    VxRow(
                        title = conversation.title.ifBlank { tr("未命名会话") },
                        subtitle = if (selected) {
                            "启用中 · 回复写进这条会话"
                        } else {
                            "回复写进这条会话"
                        },
                        trailing = {
                            if (selected) {
                                Text("✓", style = type.footnote1, color = miuix.primary)
                            }
                        },
                        onClick = { conversationId = conversation.id },
                    )
                }
            }

            if (selectedMissing) {
                Text(
                    text = if (autoConv != null) {
                        trf("所选会话已被删除。会自动沿用上次的 QQ 会话：《%s》，可在上面改选。", autoConv.title.ifBlank { tr("未命名会话") })
                    } else {
                        tr("所选会话已被删除。会自动新建「QQ 消息」，可在上面改选。")
                    },
                    style = type.footnote2,
                    color = miuix.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            Text(
                text = tr("写进同一条会话的好处：App 与 QQ 接着同一段上文，人设也只有一份，不会「换个入口就变了个人」。"),
                style = type.footnote2,
                color = miuix.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // 空列表时整段不渲染：一个空标题 + 空 Card 比什么都不显示更让人困惑
            // （用户会以为是加载失败，而不是"还没有人给机器人发过消息"）
            if (aliases.isNotEmpty()) {
                VxSectionLabel("群里的人")

                VxCard {
                    aliases.take(15).forEach { alias ->
                    if (editingAliasKey == alias.key) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            TextField(
                                value = editingAliasName,
                                onValueChange = { editingAliasName = it },
                                label = tr("给这个人的备注"),
                                useLabelAsPlaceholder = true,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                Button(
                                    onClick = {
                                        editingAliasKey = null
                                        editingAliasName = ""
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        color = miuix.secondaryContainer,
                                        contentColor = miuix.onSurfaceContainer,
                                    ),
                                    modifier = Modifier.padding(end = 8.dp),
                                ) {
                                    Text(tr("取消"), style = type.button)
                                }
                                Button(
                                    onClick = {
                                        val key = alias.key
                                        val name = editingAliasName
                                        editingAliasKey = null
                                        editingAliasName = ""
                                        scope.launch {
                                            QqAliasStore.rename(key, name).onFailure {
                                                notice = it.message ?: tr("改名失败")
                                                noticeIsError = true
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        color = miuix.primary,
                                        contentColor = miuix.onPrimary,
                                    ),
                                ) {
                                    Text(tr("保存"), style = type.button)
                                }
                            }
                        }
                    } else {
                        VxRow(
                            title = alias.name,
                            subtitle = trf("收到 %d 条 · 点一下改名", alias.msgCount),
                            onClick = {
                                editingAliasKey = alias.key
                                editingAliasName = alias.name
                            },
                        )
                    }
                }
            }

                Text(
                    text = tr("官方只给 openid 不给昵称，所以这里用本地别名代替。改名只影响这一台设备，也只会用在提示词里。"),
                    style = type.footnote2,
                    color = miuix.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            VxSectionLabel("能力")

            VxCard {
                VxRow(
                    title = "允许在 QQ 里查东西",
                    subtitle = "只能用只读工具：当前时间、计算、联网搜索、已装应用。改文件、执行命令、读短信一律不可用。",
                    trailing = {
                        VxSwitch(
                            checked = agentTools,
                            onCheckedChange = {
                                agentTools = it
                                onSave(config.copy(agentTools = it))
                            },
                        )
                    },
                )
            }

            Text(
                text = tr("默认关闭的原因：QQ 里没有「你点确认」这一步，任何能给你发消息的人都能触发它。开启后最坏后果是多花点 token，不会动你的设备。"),
                style = type.footnote2,
                color = miuix.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        // 先落盘，否则连的还是旧凭证
                        if (canSave) onSave(snapshot())
                        if (running) onStopBridge() else onStartBridge()
                    },
                    enabled = canSave,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = when (bridgeState) {
                            QqLink.State.Connected -> tr("断开连接")
                            QqLink.State.Connecting -> tr("连接中…")
                            QqLink.State.Error -> tr("重试连接")
                            QqLink.State.Stopped -> tr("开始接收")
                        },
                        style = type.button,
                    )
                }
                Button(
                    onClick = {
                        if (probing) return@Button
                        probing = true
                        scope.launch {
                            val result = if (mode == QqBot.Mode.Official) {
                                QqOfficial.probe(appId, appSecret, sandbox)
                            } else {
                                QqBot.probe(snapshot())
                            }
                            probing = false
                            notice = result.fold(
                                onSuccess = { trf("连接成功：%s", it) },
                                onFailure = { trf("连接失败：%s", it.message.orEmpty()) },
                            )
                            noticeIsError = result.isFailure
                        }
                    },
                    enabled = canSave && !probing,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (probing) tr("检测中…") else tr("测试连接"), style = type.button)
                }
            }

            if (bridgeMessage != null) {
                Text(
                    text = bridgeMessage,
                    style = type.footnote2,
                    color = if (bridgeState == QqLink.State.Error) miuix.error else miuix.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            notice?.let {
                Text(
                    text = it,
                    style = type.footnote2,
                    color = if (noticeIsError) miuix.error else miuix.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    onClear()
                    appId = ""
                    appSecret = ""
                    baseUrl = ""
                    token = ""
                    wsUrl = ""
                    targetId = ""
                    displayName = ""
                    sandbox = true
                    // autoReply / agentTools / thinking 必须一起复位：
                    // QqBot.clear() 只把持久化配置置 false，界面 state 不复位会让开关
                    // 停在「开」，用户以为自动回复还在工作
                    autoReply = false
                    agentTools = false
                    thinking = false
                    conversationId = 0L
                    // 别名表跟着一起清：openid 是"机器人视角的用户 id"，换一个机器人后
                    // 同一个人可能拿到不同 openid，旧别名会挂到陌生人身上 ——
                    // 用户看到「用户A」却完全不是原来那个人，比丢掉别名更糟。
                    // 放在 UI 侧显式调用（而不是塞进 QqBot.clear()）：清别名是"用户点了清除"
                    // 这个动作的后果，不是配置对象自己的副作用，而且 clear() 不是 suspend。
                    scope.launch { QqAliasStore.clear() }
                    notice = tr("已清除")
                    noticeIsError = false
                },
                colors = ButtonDefaults.buttonColors(
                    color = miuix.errorContainer,
                    contentColor = miuix.error,
                ),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
            ) {
                Text(tr("清除配置"), style = type.button)
            }

            Text(
                text = if (mode == QqBot.Mode.Official) {
                    tr("安全：AppSecret 用 Android Keystore 加密后只存本机。只做被动回复（单聊 60 分钟内、群聊 5 分钟内有效），不做主动推送。")
                } else {
                    tr("安全：令牌用 Android Keystore 加密后只存本机；消息发送前会展示目标与内容。")
                },
                style = type.footnote2,
                color = miuix.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

/** MIUIX 选中胶囊：用 Button 的两套色板表达选中态，避免再造一个私有组件。 */
@Composable
private fun MiuixChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = if (selected) {
            ButtonDefaults.buttonColorsPrimary()
        } else {
            ButtonDefaults.buttonColors()
        },
        minHeight = 34.dp,
        cornerRadius = 17.dp,
        insideMargin = PaddingValues(horizontal = 10.dp),
    ) {
        Text(tr(label), style = MiuixTheme.textStyles.footnote1)
    }
}
