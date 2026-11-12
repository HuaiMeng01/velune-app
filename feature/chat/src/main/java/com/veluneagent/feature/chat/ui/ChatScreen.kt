package com.veluneagent.feature.chat.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.veluneagent.core.data.AssistantPlaceholder
import com.veluneagent.core.data.AttachmentKind
import com.veluneagent.core.data.ChatAttachment
import com.veluneagent.core.data.ChatClient
import com.veluneagent.core.data.ChatMessage
import com.veluneagent.core.data.ChatPrefs
import com.veluneagent.core.data.ChatStore
import com.veluneagent.core.data.ChatStreamGuard
import com.veluneagent.core.data.InterruptedMarker
import com.veluneagent.core.data.MessageRole
import com.veluneagent.core.data.Provider
import com.veluneagent.core.data.ProviderStore
import com.veluneagent.core.data.StreamEvent
import com.veluneagent.core.data.StoredMessage
import com.veluneagent.core.data.TokenEstimator
import com.veluneagent.core.data.senderPrefixed
import com.veluneagent.core.data.context.ContextCompressor
import com.veluneagent.core.data.context.SummaryStore
import com.veluneagent.core.data.agent.AgentEngine
import com.veluneagent.core.data.agent.AgentEvent
import com.veluneagent.core.data.agent.AgentOutcome
import com.veluneagent.core.data.agent.ApprovalDecision
import com.veluneagent.core.data.agent.ApprovalRequest
import com.veluneagent.core.data.agent.OpenAiGateway
import com.veluneagent.core.data.agent.StopReason
import com.veluneagent.core.design.component.VxActionMenu
import com.veluneagent.core.design.component.VxActionSheet
import com.veluneagent.core.design.component.VxIcon
import com.veluneagent.core.design.component.VxIconButton
import com.veluneagent.core.design.component.VxMenuItem
import com.veluneagent.core.design.component.VxSearchBar
import com.veluneagent.core.design.component.VxSheetAction
import com.veluneagent.core.design.component.VxSnackbar
import com.veluneagent.core.design.component.VxText
import com.veluneagent.core.design.component.vxRipple
import androidx.compose.foundation.shape.CircleShape
import com.veluneagent.core.design.shape.SquircleCapsule
import com.veluneagent.core.design.shape.SquircleCorners
import com.veluneagent.core.design.shape.SquircleShape
import com.veluneagent.core.design.l10n.tr
import com.veluneagent.core.design.l10n.trf
import com.veluneagent.core.design.theme.VeluneTheme
import com.veluneagent.core.design.theme.VxMotion
import com.veluneagent.feature.chat.model.ContextBudget
import com.veluneagent.feature.chat.model.ModelInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 对话页。消息来自本地库，发送即落库 —— 关掉应用再进来，对话还在。
 *
 * 流式落库策略：每 200ms 刷一次盘，停止/失败/成功都会在 finally 里补最后一次写。
 * 用量（prompt/completion token）优先取服务端回报，缺省时用本地估算兜底。
 */
@Composable
fun ChatScreen(
    conversationId: Long,
    title: String,
    store: ChatStore,
    onBack: (() -> Unit)? = null,
    onOpenModelSetup: () -> Unit = {},
    modifier: Modifier = Modifier,
    agentDeps: AgentDeps? = null,
    /** 打开本会话的执行轨迹（agent_runs 一直是只写不读，这里给了入口） */
    onOpenRuns: () -> Unit = {},
) {
    val messages by store
        .observeMessages(conversationId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val scope = rememberCoroutineScope()
    var streaming by remember { mutableStateOf(false) }
    // 进入会话、以及本轮输出结束时，把「读到哪」推进到最新。
    // 只在非流式状态下标记：流式过程中新消息还在追加，此刻标记会让会话永远没有未读，
    // 用户切走之后就不知道这里已经更新过了。
    LaunchedEffect(streaming) {
        if (!streaming) runCatching { store.markRead(conversationId) }
    }
    var errorText by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var pendingAttachment by remember { mutableStateOf<ChatAttachment?>(null) }
    val agentEnabled by ChatPrefs.agentEnabled.collectAsStateWithLifecycle()
    val approvalGate = remember { ApprovalGate() }
    val pendingApproval by approvalGate.pending.collectAsStateWithLifecycle()
    // 流式过程：正文与工具调用按发生顺序交错存放，渲染就是「边写边调」
    val streamParts = remember { mutableStateListOf<StreamPart>() }
    var agentStatus by remember { mutableStateOf<String?>(null) }
    var compressNote by remember { mutableStateOf<String?>(null) }
    var liveReasoning by remember { mutableStateOf("") }
    // 思考耗时：累加相邻 reasoning 增量的间隔（间隔上限 5s，防止挂起被算成思考）
    var reasoningMs by remember { mutableLongStateOf(0L) }
    var lastReasoningAt by remember { mutableLongStateOf(0L) }
    val appContext = LocalContext.current
    val compressor = remember(appContext) {
        ContextCompressor(SummaryStore(appContext))
    }
    // 提示词：全局只有一条生效版本，聊天 / Agent / QQ 三处共用同一份人设
    val promptStore = remember(appContext) {
        com.veluneagent.core.data.prompt.PromptStore(appContext)
    }
    val globalPersona by promptStore
        .observeAny()
        .collectAsStateWithLifecycle(initialValue = null)
    // 提示词里的 {{user_name}} 从这里取；没设昵称时替换成「你」
    val userName by com.veluneagent.core.data.profile.UserProfile.nickname
        .collectAsStateWithLifecycle()
    // 会话级角色：从「消息页 + → 创建角色」建出来的会话会绑一个提示词，
    // 它优先于场景的全局人设——否则同一个角色换个会话就不认了
    var conversationPersona by remember(conversationId) { mutableStateOf<String?>(null) }
    LaunchedEffect(conversationId) {
        val promptId = store.promptIdOf(conversationId)
        conversationPersona = promptId?.let { promptStore.byId(it)?.body }
    }
    val compressTrigger by ChatPrefs.compressTrigger.collectAsStateWithLifecycle()
    val thinkingEnabled by ChatPrefs.thinkingEnabled.collectAsStateWithLifecycle()
    val reasoningEffort by ChatPrefs.reasoningEffort.collectAsStateWithLifecycle()

    val providers by ProviderStore.providers.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeId by ProviderStore.activeId.collectAsStateWithLifecycle(initialValue = null)
    val compressAt by ChatPrefs.compressAt.collectAsStateWithLifecycle()
    val activeProvider = providers.firstOrNull { it.id == activeId } ?: providers.firstOrNull()
    val activeModel = ModelInfo(
        id = activeProvider?.model.orEmpty(),
        displayName = activeProvider?.model?.ifBlank { activeProvider.name } ?: "未配置模型",
        provider = activeProvider?.name.orEmpty(),
        accent = VeluneTheme.colors.accent,
    )

    /** 记录一段思考耗时。正文到达时断开计时，避免把「输出答案」的时间算进思考。 */
    fun trackReasoning() {
        val now = System.currentTimeMillis()
        if (lastReasoningAt > 0) {
            reasoningMs += (now - lastReasoningAt).coerceIn(0L, 5_000L)
        }
        lastReasoningAt = now
    }

    fun pauseReasoning() {
        lastReasoningAt = 0L
    }

    /** 把增量接到最后一段正文上；如果上一段是工具卡，就新开一段。 */
    fun appendLiveText(delta: String) {
        if (delta.isEmpty()) return
        val last = streamParts.lastOrNull()
        if (last is StreamPart.Text) {
            streamParts[streamParts.lastIndex] = last.copy(text = last.text + delta)
        } else {
            streamParts += StreamPart.Text(key = "text-${streamParts.size}", delta)
        }
    }

    /**
     * 一次完整的请求。
     * @param regenerate 重新生成：截掉最后一条助手回复，用同一条用户消息再发一次
     * @param editFrom  编辑重发：从这条消息起（含）截断，用新文本替换
     */
    fun startTurn(text: String, regenerate: Boolean, editFrom: Long?, quote: Bubble? = null) {
        // job.isActive 兜住「连点两下发送」：streaming 要等写库完成才置 true，中间有窗口期
        if (streaming || job?.isActive == true) return
        val provider = providers.firstOrNull { it.id == activeId } ?: providers.firstOrNull()
        if (provider == null) {
            errorText = "尚未配置模型服务商：设置 → 模型与密钥"
            return
        }

        val snapshot = messages.map { it.toBubble() }
        val lastUserIndex = snapshot.indexOfLast { it.fromUser }
        if (regenerate && lastUserIndex < 0) return
        val edited = editFrom?.let { id -> snapshot.firstOrNull { it.id == id } }
        if (editFrom != null && edited == null) return

        val base: List<Bubble> = when {
            regenerate -> snapshot.take(lastUserIndex)
            edited != null -> snapshot.takeWhile { it.id < edited.id }
            else -> snapshot
        }
        // 不做条数截断：交给压缩器决定「保留多少原文 + 折叠多少成摘要」
        // Agent 模式带 tools，历史里的 assistant 必须回传 reasoning_content，否则上游 400
        val rawHistory = buildHistory(base, Int.MAX_VALUE, includeReasoning = agentEnabled)
        val outgoing = outgoingMessage(snapshot, text, regenerate, edited, pendingAttachment)

        val attachmentForNewMessage =
            if (edited != null) edited.attachment else pendingAttachment

        job = scope.launch {
            // 计划是"本轮"的状态：不清空会把上一轮的清单带到新任务里
            com.veluneagent.core.data.agent.AgentPlan.clear()
            errorText = null
            streaming = true
            streamParts.clear()
            liveReasoning = ""
            reasoningMs = 0L
            lastReasoningAt = 0L
            agentStatus = if (agentEnabled && agentDeps != null) "Agent 模式已开启" else null

            var assistantId: Long? = null
            val buffer = StringBuilder()
            var lastFlush = 0L
            var failure: String? = null
            var serverPrompt = 0
            var serverCompletion = 0

            try {
                // 先把用户消息落库，再做任何可能失败或被取消的事。
                // 输入框在 onSend 里已经清空：一旦下面提前 return（没配模型、没 Key、
                // 或摘要调用被取消），用户写下的这段话就再也找不回来了。
                if (!regenerate) {
                    if (edited != null) {
                        // 编辑：删旧 + 插新必须原子，否则中途崩溃两边的消息都会消失
                        store.replaceFrom(
                            conversationId = conversationId,
                            fromMessageId = edited.id,
                            text = text,
                            attachment = attachmentForNewMessage,
                            replyToId = quote?.id,
                            replyPreview = quote?.text?.trim()?.take(160).orEmpty(),
                        )
                    } else {
                        store.appendMessage(
                            conversationId = conversationId,
                            role = MessageRole.User,
                            text = text,
                            attachment = attachmentForNewMessage,
                            replyToId = quote?.id,
                            // 预览截断到 160 字：引用条只显示两三行，没必要把整段长回答抄进库里
                            replyPreview = quote?.text?.trim()?.take(160).orEmpty(),
                        )
                    }
                }

                // 模型名是用户自填的，为空时先拦下来，别等上游返回看不懂的错误
                if (provider.model.isBlank()) {
                    failure = "当前服务商未填写模型名：设置 → 模型与密钥 → 编辑"
                    return@launch
                }

                // Keystore 解密是 IO，放在主线程会在弱机上掉帧
                val apiKey = withContext(Dispatchers.IO) { ProviderStore.apiKeyOf(provider.id) }
                if (apiKey.isNullOrBlank()) {
                    failure = "当前服务商缺少 API Key：设置 → 模型与密钥"
                    return@launch
                }

                // 上下文压缩：窗口外的旧消息折成摘要，摘要调用失败则回退到窗口裁剪
                compressNote = null
                // 摘要走「模型分工 → 摘要」指定的服务商：压缩是纯机械的归纳活儿，
                // 用最便宜的模型即可，没必要消耗主模型额度。没绑定就跟随主模型。
                val summaryProvider = com.veluneagent.core.data.ModelRouting.providerFor(
                    role = com.veluneagent.core.data.ModelRouting.Role.Summary,
                    providers = providers,
                    main = provider,
                ) ?: provider
                val summaryKey = if (summaryProvider.id == provider.id) {
                    apiKey
                } else {
                    withContext(Dispatchers.IO) { ProviderStore.apiKeyOf(summaryProvider.id) }
                        ?: apiKey
                }
                val prepared = compressor.prepare(
                    conversationId = conversationId,
                    raw = rawHistory,
                    triggerTokens = compressTrigger,
                    keepRecent = compressAt,
                    summarize = { prompt ->
                        val text = StringBuilder()
                        val ok = runCatching {
                            ChatClient.stream(summaryProvider, summaryKey, prompt).collect { event ->
                                if (event is StreamEvent.Delta) text.append(event.text)
                            }
                        }.isSuccess
                        if (ok && text.isNotBlank()) text.toString() else null
                    },
                )
                if (prepared.foldedCount > 0) {
                    compressNote = when {
                        prepared.degraded ->
                            trf("摘要生成失败，已回退为最近 %d 条", compressAt)

                        else ->
                            trf(
                                "已把 %d 条旧消息压缩为摘要（约 %d→%d token）",
                                prepared.foldedCount,
                                prepared.estimatedTokensBefore,
                                prepared.estimatedTokensAfter,
                            )
                    }
                }

                val history = prepared.messages + outgoing
                // 场景决定平台规则：Agent 带工具契约，聊天只有人设。
                // 顺序由 PromptComposer 固定：平台规则 → 用户 persona → 运行时事实
                val scene = if (agentEnabled && agentDeps != null) {
                    com.veluneagent.core.data.prompt.PromptScene.Agent
                } else {
                    com.veluneagent.core.data.prompt.PromptScene.Chat
                }
                // 会话绑定的角色优先于全局人设：同一个角色换个会话也得认出来
                val persona = conversationPersona ?: globalPersona?.body
                val agentHistory = com.veluneagent.core.data.prompt.PromptComposer
                    .build(scene = scene, persona = persona, userName = userName) + history

                // 重新生成：截断必须留在模型检查之后 —— 提前删的话，
                // 没配模型时旧回答会白白消失，新回答又生不出来。
                if (regenerate && lastUserIndex >= 0) {
                    withContext(Dispatchers.IO) {
                        store.deleteMessagesAfter(conversationId, snapshot[lastUserIndex].id)
                    }
                }

                // 必须写在占位行之前：QQ 侧读历史前会先看这个信号，
                // 晚一步就意味着那 200ms 里 QQ 可能读到一行「…」占位
                ChatStreamGuard.begin(conversationId)
                val aid = store.appendMessage(
                    conversationId = conversationId,
                    role = MessageRole.Assistant,
                    text = AssistantPlaceholder,
                    model = provider.model,
                )
                assistantId = aid

                val deps = agentDeps
                if (agentEnabled && deps != null) {
                    // 工具列表用 lambda 提供给网关：装完插件后，下一次模型调用立刻能看到新工具
                    val gateway = OpenAiGateway(
                        provider = provider,
                        apiKey = apiKey,
                        tools = { deps.catalog.specs() },
                        thinking = thinkingEnabled,
                        reasoningEffort = reasoningEffort,
                    )
                    val engine = AgentEngine(
                        gateway = gateway,
                        catalog = deps.catalog,
                        executor = deps.executor,
                        approver = approvalGate,
                        recorder = deps.runStore,
                    )

                    var outcome: AgentOutcome? = null
                    engine.run(conversationId, agentHistory).collect { event ->
                        when (event) {
                            is AgentEvent.Delta -> {
                                pauseReasoning()
                                buffer.append(event.text)
                                appendLiveText(event.text)
                                val now = System.currentTimeMillis()
                                if (now - lastFlush >= 200) {
                                    lastFlush = now
                                    store.updateMessageText(aid, buffer.toString())
                                }
                            }

                            is AgentEvent.Reasoning -> {
                                liveReasoning += event.text
                                trackReasoning()
                            }

                            is AgentEvent.Started -> {
                                // 0 表示不限：不能渲染成「预算 0 步」
                                agentStatus = buildString {
                                    append(if (event.maxSteps > 0) trf("最多 %d 步", event.maxSteps) else tr("步数不限"))
                                    append(" · ")
                                    append(if (event.maxToolCalls > 0) trf("最多 %d 次工具", event.maxToolCalls) else tr("工具不限"))
                                }
                            }

                            is AgentEvent.StepStarted -> {
                                // 不再清空正文：中间过程本身就是用户要看的东西，清掉就只剩最后一句话
                                agentStatus = event.title
                            }

                            is AgentEvent.ToolStarted -> {
                                streamParts += StreamPart.Tool(
                                    key = "tool-${event.call.id}",
                                    step = ToolStepUi(
                                        callId = event.call.id,
                                        name = event.call.name,
                                        displayName = event.spec?.displayName
                                            ?.ifBlank { event.call.name }
                                            ?: event.call.name,
                                        origin = event.spec?.origin?.ifBlank { "未知来源" }
                                            ?: "未注册工具",
                                        argsPreview = event.call.argumentsJson.take(120),
                                        status = ToolStepStatus.Running,
                                        argsFull = event.call.argumentsJson,
                                    ),
                                )
                            }

                            is AgentEvent.ToolFinished -> {
                                val result = event.result
                                val index = streamParts.indexOfLast {
                                    it is StreamPart.Tool && it.step.callId == result.callId
                                }
                                if (index >= 0) {
                                    val part = streamParts[index] as StreamPart.Tool
                                    val old = part.step
                                    streamParts[index] = part.copy(
                                        step = old.copy(
                                            status = when {
                                                result.ok -> ToolStepStatus.Ok
                                                result.errorCode == "user_denied" ->
                                                    ToolStepStatus.Denied

                                                else -> ToolStepStatus.Failed
                                            },
                                            detail = result.displayText,
                                            durationMs = result.durationMs,
                                            resultFull = result.contentForModel,
                                            // 路径来源分两种：fs_write 把它写在**参数**里，
                                            // tts_speak 的参数只有 text/voice/style，路径在**返回结果**里。
                                            // 只读参数会让语音卡片永远拿不到文件，播放器也就永远不出现。
                                            filePath = when (old.name) {
                                                "fs_write" -> runCatching {
                                                    org.json.JSONObject(old.argsFull)
                                                        .optString("path")
                                                        .takeIf { it.isNotBlank() }
                                                }.getOrNull()

                                                "tts_speak" -> runCatching {
                                                    org.json.JSONObject(result.contentForModel)
                                                        .optString("path")
                                                        .takeIf { it.isNotBlank() }
                                                }.getOrNull()

                                                else -> null
                                            },
                                        ),
                                    )
                                }
                            }

                            // 审批面板由 ApprovalGate 的 StateFlow 驱动，这里不需要额外处理
                            is AgentEvent.ApprovalRequired -> Unit

                            is AgentEvent.Finished -> outcome = event.outcome
                        }
                    }

                    val result = outcome
                    if (result != null) {
                        // 只有「一句话都没流出来」时才用 outcome 兜底；
                        // 否则覆盖会把前面的过程文字（含最终回答）全抹掉
                        if (buffer.isEmpty()) {
                            val answer = result.answer.ifBlank {
                                result.message?.let { trf("（已停止：%s）", it) } ?: tr("（模型没有返回内容）")
                            }
                            buffer.append(answer)
                            appendLiveText(answer)
                            store.updateMessageText(aid, answer)
                        }
                        store.updateMessageUsage(
                            aid,
                            result.promptTokens,
                            result.completionTokens,
                            provider.model,
                        )
                        if (result.reason != StopReason.Completed) {
                            errorText = result.message ?: trf("已停止（%s）", result.reason.toString())
                        }
                        agentStatus = trf("本次 %d 步 · %d 次工具", result.steps, result.toolCalls)
                    }
                } else {
                    ChatClient.stream(
                        provider = provider,
                        apiKey = apiKey,
                        history = history,
                        thinking = thinkingEnabled,
                        reasoningEffort = reasoningEffort,
                    ).collect { event ->
                        when (event) {
                            is StreamEvent.Delta -> {
                                pauseReasoning()
                                buffer.append(event.text)
                                appendLiveText(event.text)
                                val now = System.currentTimeMillis()
                                if (now - lastFlush >= 200) {
                                    lastFlush = now
                                    store.updateMessageText(aid, buffer.toString())
                                }
                            }

                            is StreamEvent.Reasoning -> {
                                liveReasoning += event.text
                                trackReasoning()
                            }

                            is StreamEvent.Usage -> {
                                serverPrompt = event.promptTokens
                                serverCompletion = event.completionTokens
                            }

                            is StreamEvent.ToolCalls -> Unit

                            StreamEvent.Done -> Unit

                            is StreamEvent.Failed -> failure = event.message
                        }
                    }

                    // 用量：优先服务端回报，否则本地估算（兼容端点大多不返回 usage）
                    val promptTokens = if (serverPrompt > 0) {
                        serverPrompt
                    } else {
                        TokenEstimator.estimateMessages(history)
                    }
                    val completionTokens = if (serverCompletion > 0) {
                        serverCompletion
                    } else {
                        TokenEstimator.estimate(buffer.toString())
                    }
                    withContext(Dispatchers.IO) {
                        store.updateMessageUsage(aid, promptTokens, completionTokens, provider.model)
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                failure = t.message ?: t.javaClass.simpleName
            } finally {
                // 取消/异常也走这里：把最后一段没刷盘的内容补上，占位符必须被替换掉
                withContext(NonCancellable) {
                    val target = assistantId
                    if (target != null) {
                        val produced = buffer.toString()
                        val finalText = when {
                            produced.isNotEmpty() -> produced
                            failure != null -> trf("请求失败：%s", failure)
                            else -> "（已停止）"
                        }
                        store.updateMessageText(target, finalText)
                        if (liveReasoning.isNotBlank()) {
                            store.updateMessageReasoning(target, liveReasoning, reasoningMs)
                        }
                        // 先落步骤再让 streaming=false：否则界面会先切到"历史渲染"（还没有步骤）
                        // 再等数据库回流，中间闪一下空白
                        val stepsJson = ToolStepCodec.encode(streamParts)
                        if (stepsJson.isNotEmpty()) {
                            store.updateMessageSteps(target, stepsJson)
                        }
                    }
                    failure?.let { errorText = it }
                    approvalGate.cancelPending()
                    streaming = false
                    // 结束即还原成模式说明：调用明细已经落在消息卡片上，
                    // 再在输入框上方挂一条"本次 N 步"会一直占着位置
                    agentStatus = null
                    // 放在最后：上面 updateMessageText 已经把最终正文刷盘，
                    // 此时放开信号，QQ 读到的才是完整句子而不是半截
                    ChatStreamGuard.end(conversationId)
                }
            }
        }

        pendingAttachment = null
    }

    ChatContent(
        title = title,
        model = activeModel,
        bubbles = messages.map { it.toBubble() },
        streaming = streaming,
        errorText = errorText,
        compressAt = compressAt,
        pendingAttachment = pendingAttachment,
        agentEnabled = agentEnabled,
        agentStatus = agentStatus,
        compressNote = compressNote,
        liveReasoning = liveReasoning,
        sandbox = agentDeps?.sandbox,
        compressTrigger = compressTrigger,
        onCompressTriggerChange = { ChatPrefs.setCompressTrigger(it) },
        streamParts = streamParts,
        reasoningMs = reasoningMs,
        approval = pendingApproval,
        onApproval = { decision -> approvalGate.decide(decision) },
        onToggleAgent = { ChatPrefs.setAgentEnabled(!agentEnabled) },
        onPickAttachment = { pendingAttachment = it },
        onClearAttachment = { pendingAttachment = null },
        onCompressAtChange = { ChatPrefs.setCompressAt(it) },
        onBack = onBack,
        onStop = {
            job?.cancel()
            job = null
        },
        onSend = { text, editFrom, quote ->
            startTurn(text, regenerate = false, editFrom = editFrom, quote = quote)
        },
        onRegenerate = { startTurn("", regenerate = true, editFrom = null) },
        onOpenModelSetup = onOpenModelSetup,
        onOpenRuns = onOpenRuns,
        providers = providers,
        activeProviderId = activeProvider?.id,
        onSelectProvider = { provider -> ProviderStore.setActive(provider.id) },
        modifier = modifier,
    )
}

/**
 * 决定这一轮「要发出去的用户消息」。
 *
 * 三种模式容易写串：重新生成要复用原消息，编辑重发必须换成新文本（附件沿用），
 * 普通发送用新文本 + 待发附件。抽成纯函数并被单测覆盖，避免再出现「编辑了却发旧内容」。
 */
internal fun outgoingMessage(
    snapshot: List<Bubble>,
    text: String,
    regenerate: Boolean,
    edited: Bubble?,
    pendingAttachment: ChatAttachment?,
): ChatMessage = when {
    regenerate -> snapshot.lastOrNull { it.fromUser }?.toChatMessage()
        ?: ChatMessage(role = "user", text = text)

    edited != null -> chatMessageOf(text, edited.attachment)
    else -> chatMessageOf(text, pendingAttachment)
}

/** 只保留最近 limit 条，并剔除占位/中断这类不该进上下文的正文。 */
internal fun buildHistory(
    bubbles: List<Bubble>,
    limit: Int,
    includeReasoning: Boolean = false,
): List<ChatMessage> {
    // 占位符与中断标记都不是模型产出，发给上游只会污染上下文
    val usable = bubbles.filter {
        it.text.isNotBlank() && it.text != AssistantPlaceholder && it.text != InterruptedMarker
    }
    val kept = if (usable.size > limit) usable.takeLast(limit) else usable
    return kept.map { bubble ->
        // 来源前缀由 toChatMessage() 统一负责，这里不再单独加（加了会变成双前缀）
        var message = bubble.toChatMessage()
        // 引用要进上下文：否则模型看到"那这个呢"完全不知道"这个"指什么
        if (bubble.replyPreview.isNotBlank()) {
            message = message.copy(text = "【引用】${bubble.replyPreview}\n${message.text}")
        }
        if (includeReasoning && !bubble.fromUser && bubble.reasoning.isNotBlank()) {
            message.copy(reasoningContent = bubble.reasoning)
        } else {
            message
        }
    }
}

/**
 * 组装一条要发出去的消息。
 *
 * 文本附件拼进正文（用围栏包住，避免内容里的 Markdown 打乱结构）；
 * 图片走多模态字段，正文保持用户原话。
 */
internal fun chatMessageOf(text: String, attachment: ChatAttachment?): ChatMessage {
    val content = when {
        attachment == null || attachment.kind != AttachmentKind.Text -> text
        else -> buildString {
            append(text)
            if (text.isNotBlank()) append("\n\n")
            append("[附件：").append(attachment.name).append("]\n")
            append("```\n").append(attachment.text).append("\n```")
        }
    }
    val image = if (attachment?.kind == AttachmentKind.Image) attachment.dataUrl else null
    return ChatMessage(role = "user", text = content, imageDataUrl = image)
}

@Composable
private fun ChatContent(
    title: String,
    bubbles: List<Bubble>,
    onBack: (() -> Unit)?,
    onSend: (String, Long?, Bubble?) -> Unit,
    onOpenModelSetup: () -> Unit,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
    errorText: String? = null,
    compressAt: Int = ChatPrefs.DefaultCompressAt,
    pendingAttachment: ChatAttachment? = null,
    agentEnabled: Boolean = false,
    agentStatus: String? = null,
    compressNote: String? = null,
    liveReasoning: String = "",
    sandbox: com.veluneagent.core.data.sandbox.Sandbox? = null,
    compressTrigger: Int = ChatPrefs.DefaultTriggerTokens,
    onCompressTriggerChange: (Int) -> Unit = {},
    streamParts: List<StreamPart> = emptyList(),
    /** 实时思考耗时，传给最后一条气泡 */
    reasoningMs: Long = 0,
    approval: ApprovalRequest? = null,
    onApproval: (ApprovalDecision) -> Unit = {},
    onToggleAgent: () -> Unit = {},
    onPickAttachment: (ChatAttachment) -> Unit = {},
    onClearAttachment: () -> Unit = {},
    onCompressAtChange: (Int) -> Unit = {},
    onStop: () -> Unit = {},
    onRegenerate: () -> Unit = {},
    onOpenRuns: () -> Unit = {},
    model: ModelInfo = ModelInfo("", "未配置模型", "", Color(0xFF4D6BFE)),
    providers: List<Provider> = emptyList(),
    activeProviderId: String? = null,
    onSelectProvider: (Provider) -> Unit = {},
) {
    val colors = VeluneTheme.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    // 会话内搜索：在已加载的消息里找，不做全文检索（数据本来就都在这屏）
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchCursor by remember { mutableIntStateOf(0) }
    val searchHits = remember(bubbles, searchQuery) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            bubbles.mapIndexedNotNull { index, bubble ->
                if (bubble.text.contains(searchQuery, ignoreCase = true)) index else null
            }
        }
    }
    var quoting by remember { mutableStateOf<Bubble?>(null) }
    var editing by remember { mutableStateOf<Bubble?>(null) }
    var sheetVisible by remember { mutableStateOf(false) }
    var menuVisible by remember { mutableStateOf(false) }
    var modelMenuVisible by remember { mutableStateOf(false) }
    var snackbar by remember { mutableStateOf<String?>(null) }
    var lastSnackbarText by remember { mutableStateOf("") }
    var barHeightPx by remember { mutableIntStateOf(0) }
    var actionTarget by remember { mutableStateOf<Bubble?>(null) }
    var expandedToolId by remember { mutableStateOf<String?>(null) }
    var fileTarget by remember { mutableStateOf<ToolStepUi?>(null) }
    var previewTarget by remember { mutableStateOf<ToolStepUi?>(null) }

    val budget = ContextBudget(messageCount = bubbles.size, compressAt = compressAt)
    val listState = rememberLazyListState()

    // 命中的位置直接滚过去：「搜到了但不知道在第几条」等于没搜。
    // 放在 listState 之后：Compose 里状态声明必须在第一次使用之前。
    LaunchedEffect(searchHits, searchCursor, searchOpen) {
        if (!searchOpen || searchHits.isEmpty()) return@LaunchedEffect
        val hit = searchHits[searchCursor.coerceIn(0, searchHits.lastIndex)]
        // +1：LazyColumn 的第一条是时间分隔线
        listState.animateScrollToItem((hit + 1).coerceAtLeast(0))
    }
    val barHeight: Dp = with(density) { barHeightPx.toDp() }

    // 只有「还没配置服务商」时才提示去设置
    val showModelHint = providers.isEmpty() && bubbles.isNotEmpty()
    val lastTextLength = bubbles.lastOrNull()?.text?.length ?: 0

    // 跟随到底部：用户主动上滑后就不再抢他的滚动位置
    var stickToBottom by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isAtBottom() }.collect { stickToBottom = it }
    }

    // 右缘滚动指示条：滚动时浮现，停手 600ms 后淡出（HyperOS 同款）。
    // collectLatest：停手后的 600ms 等待里又开始滚动的话，直接取消淡出、保持显示。
    var indicatorVisible by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { scrolling ->
                if (scrolling) {
                    indicatorVisible = true
                } else {
                    delay(600)
                    indicatorVisible = false
                }
            }
    }
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (indicatorVisible) 1f else 0f,
        animationSpec = tween(durationMillis = if (indicatorVisible) 120 else 300),
        label = "scrollIndicatorAlpha",
    )
    // 流式过程中气泡高度由片段决定（工具卡也在气泡内），必须把它们算进滚动触发条件
    val liveChars = streamParts.sumOf { part ->
        if (part is StreamPart.Text) part.text.length else 0
    }
    LaunchedEffect(bubbles.size, lastTextLength, liveChars, streamParts.size, streaming) {
        if (bubbles.isEmpty()) return@LaunchedEffect
        if (!stickToBottom) return@LaunchedEffect
        // 流式期间每来一批 token 都直接跳，用 animate 会排队造成卡顿
        listState.scrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
    }

    LaunchedEffect(snackbar) {
        val current = snackbar ?: return@LaunchedEffect
        lastSnackbarText = current
        delay(1800)
        snackbar = null
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                AttachmentReader.read(context, uri)
                    .onSuccess { attachment ->
                        onPickAttachment(attachment)
                        snackbar = trf("已添加 %s", attachment.summary())
                    }
                    .onFailure { snackbar = it.message ?: tr("读取失败") }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvasAlt)
            .safeDrawingPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ChatTopBar(
                title = title,
                onBack = onBack,
                onSearch = { searchOpen = !searchOpen },
            )

            if (searchOpen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VxSearchBar(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            searchCursor = 0
                        },
                        placeholder = "在本次对话中搜索",
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    VxText(
                        text = when {
                            searchQuery.isBlank() -> ""
                            searchHits.isEmpty() -> "无匹配"
                            else -> "${(searchCursor.coerceIn(0, searchHits.lastIndex)) + 1}/${searchHits.size}"
                        },
                        style = VeluneTheme.type.footnote.copy(fontSize = 12.sp),
                        color = colors.textTertiary,
                        maxLines = 1,
                    )
                    if (searchHits.isNotEmpty()) {
                        VxIconButton(
                            icon = VxIcon.ChevronUp,
                            contentDescription = tr("上一条"),
                            onClick = {
                                searchCursor = (searchCursor - 1 + searchHits.size) % searchHits.size
                            },
                            size = 40.dp,
                            iconSize = 15.dp,
                        )
                        VxIconButton(
                            icon = VxIcon.ChevronDown,
                            contentDescription = tr("下一条"),
                            onClick = { searchCursor = (searchCursor + 1) % searchHits.size },
                            size = 40.dp,
                            iconSize = 15.dp,
                        )
                    }
                    VxIconButton(
                        icon = VxIcon.Close,
                        contentDescription = tr("关闭搜索"),
                        onClick = {
                            searchOpen = false
                            searchQuery = ""
                            searchCursor = 0
                        },
                        size = 40.dp,
                        iconSize = 14.dp,
                    )
                }
            }

            if (bubbles.isEmpty()) {
                EmptyConversation(modifier = Modifier.weight(1f), hasProvider = providers.isNotEmpty())
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    item(key = "time-header") {
                        TimeDivider(label = formatChatTime(bubbles.first().createdAt))
                    }

                    itemsIndexed(items = bubbles, key = { _, bubble -> bubble.id }) { index, bubble ->
                        val previous = bubbles.getOrNull(index - 1)
                        val gap = when {
                            previous == null -> 14.dp
                            previous.fromUser == bubble.fromUser -> 6.dp
                            else -> 16.dp
                        }

                        val isLive = streaming && index == bubbles.lastIndex
                        MessageRow(
                            bubble = bubble,
                            topGap = gap,
                            reasoning = if (isLive) liveReasoning else bubble.reasoning,
                            // 生成中用内存片段；结束后用落库的步骤——
                            // 只依赖内存的话，流一结束卡片就整段消失，看起来像被吞了
                            parts = if (isLive && streamParts.isNotEmpty()) {
                                streamParts
                            } else {
                                // 历史消息只落盘了工具卡片，正文在 bubble.text 里；
                                // 片段非空时 MessageRow 会忽略 bubble.text，所以必须补上正文，
                                // 否则"有工具调用的那条回答"会变成只剩卡片、没有答案
                                ToolStepCodec.decode(bubble.steps).let { tools ->
                                    when {
                                        tools.isEmpty() -> null
                                        bubble.text.isBlank() -> tools
                                        else -> tools + StreamPart.Text(
                                            key = "stored-text",
                                            text = bubble.text,
                                        )
                                    }
                                }
                            },
                            reasoningMs = if (isLive) reasoningMs else bubble.reasoningMs,
                            expandedToolId = expandedToolId,
                            onToggleTool = { callId ->
                                expandedToolId = if (expandedToolId == callId) null else callId
                            },
                            onFileAction = { step -> fileTarget = step },
                            onLongPress = { actionTarget = bubble },
                            sandbox = sandbox,
                        )
                    }

                    if (showModelHint) {
                        item(key = "model-hint") {
                            ModelNotConnectedHint(onClick = onOpenModelSetup)
                        }
                    }

                    item(key = "bottom-anchor") {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // 长回答翻上去看历史后，回到底部不该靠“手指划半天”。
                // 参考图样式：输入框上方居中的悬浮箭头，缩放+淡入出现。
                androidx.compose.animation.AnimatedVisibility(
                    visible = !stickToBottom,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = scaleIn(
                        initialScale = 0.6f,
                        animationSpec = tween(VxMotion.EnterAlphaMs),
                    ) + fadeIn(tween(VxMotion.EnterAlphaMs)),
                    exit = scaleOut(targetScale = 0.6f) + fadeOut(tween(VxMotion.DialogExitMs)),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 10.dp)
                            .size(34.dp)
                            .shadow(
                                elevation = 3.dp,
                                shape = CircleShape,
                                clip = false,
                                // 阴影必须用黑色：深色模式下 textPrimary 是近白色，
                                // 拿它当阴影等于给黑底加了一圈白色外发光
                                ambientColor = Color.Black.copy(alpha = 0.10f),
                                spotColor = Color.Black.copy(alpha = 0.14f),
                            )
                            .clip(CircleShape)
                            .background(colors.surface)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = vxRipple(colors.textPrimary.copy(alpha = 0.06f)),
                                onClick = {
                                    scope.launch {
                                        listState.animateScrollToItem(
                                            listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1,
                                        )
                                    }
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        VxIcon(
                            icon = VxIcon.ChevronDown,
                            tint = colors.textSecondary,
                            size = 15.dp,
                            strokeWidth = 2.dp,
                        )
                    }
                }

                // 右缘滚动指示条：贴屏 2dp、宽 3dp 的胶囊滑块，
                // 位置和长度按可见条目索引占比估算；内容不足一屏时不画。
                if (indicatorAlpha > 0.01f) {
                    Canvas(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(vertical = 12.dp)
                            .width(3.dp)
                            .graphicsLayer { alpha = indicatorAlpha },
                    ) {
                        val layout = listState.layoutInfo
                        val visibleItems = layout.visibleItemsInfo
                        if (visibleItems.isEmpty() || layout.totalItemsCount == 0) return@Canvas
                        val lastIdx = layout.totalItemsCount - 1
                        // 首尾都在屏内 = 内容不足一屏，无需指示
                        if (visibleItems.first().index == 0 && visibleItems.last().index >= lastIdx) return@Canvas
                        val startFrac = visibleItems.first().index.toFloat() / layout.totalItemsCount
                        val endFrac = (visibleItems.last().index + 1).toFloat() / layout.totalItemsCount
                        drawRoundRect(
                            color = colors.textPrimary.copy(alpha = 0.18f),
                            topLeft = androidx.compose.ui.geometry.Offset(0f, size.height * startFrac),
                            size = androidx.compose.ui.geometry.Size(
                                size.width,
                                ((size.height * endFrac) - (size.height * startFrac)).coerceAtLeast(size.width),
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width / 2f),
                        )
                    }
                }
                }
            }

            if (errorText != null) {
                // 规范 3.8.8：错误不允许只有文字没有图标——danger 8% 底 + 警告图标的错误条
                Row(
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .fillMaxWidth()
                        .clip(SquircleShape(10.dp))
                        .background(colors.danger.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VxIcon(icon = VxIcon.Warning, tint = colors.danger, size = 14.dp, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    VxText(
                        text = errorText,
                        style = VeluneTheme.type.footnote,
                        color = colors.danger,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (compressNote != null) {
                VxText(
                    text = compressNote,
                    style = VeluneTheme.type.footnote.copy(fontSize = 11.sp),
                    color = VeluneTheme.colors.textTertiary,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp),
                )
            }

            val planSteps by com.veluneagent.core.data.agent.AgentPlan.steps
                .collectAsStateWithLifecycle(initialValue = emptyList())
            if (planSteps.isNotEmpty()) {
                PlanCard(steps = planSteps)
            }

            // 只在执行中显示状态；空闲时不常驻——常驻条会永久占一行，
            // 把聊天列表压短一截，视觉上像挡住了正在看的内容。
            // 开关走「+ → 开启/关闭 Agent 模式」。
            if (agentEnabled && agentStatus != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AttachmentChip(
                        icon = VxIcon.Sparkles,
                        label = agentStatus,
                        onClear = onToggleAgent,
                    )
                }
            }

            // 引用必须也算「有挂起的上下文」：只引用、无编辑无附件时，
            // 旧条件会让整块不渲染，用户点完「引用」看不到任何反馈，也没法取消。
            // 出现/消失走高度过渡：chip 是从输入框里"长出来"的，不再凭空蹦出
            AnimatedVisibility(
                visible = editing != null || quoting != null || pendingAttachment != null,
                enter = expandVertically(
                    expandFrom = Alignment.Top,
                    animationSpec = tween(VxMotion.CollapseMs, easing = VxMotion.EaseOutCubic),
                ) + fadeIn(tween(VxMotion.EnterAlphaMs, easing = VxMotion.EaseOutQuad)),
                exit = shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    animationSpec = tween(VxMotion.CollapseMs, easing = VxMotion.EaseOutSine),
                ) + fadeOut(tween(VxMotion.CollapseMs, easing = VxMotion.EaseOutSine)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (editing != null) {
                        AttachmentChip(
                            icon = VxIcon.Pencil,
                            label = "编辑中 · 发送后替换后续内容",
                            onClear = {
                                editing = null
                                input = ""
                            },
                        )
                    } else if (quoting != null) {
                        AttachmentChip(
                            icon = VxIcon.Message,
                            label = trf("引用：%s", quoting!!.text.replace("\n", " ").take(24)) + "…",
                            onClear = { quoting = null },
                        )
                    } else if (pendingAttachment != null) {
                        AttachmentChip(
                            icon = if (pendingAttachment.kind == AttachmentKind.Image) {
                                VxIcon.Model
                            } else {
                                VxIcon.Doc
                            },
                            label = pendingAttachment.summary(),
                            onClear = onClearAttachment,
                        )
                    }
                }
            }

            ChatInputBar(
                input = input,
                onInputChange = { input = it },
                budget = budget,
                model = model,
                // 点第二次要能关上：之前只写了 =true，菜单开着再点加号毫无反应
                onAdd = {
                    menuVisible = !menuVisible
                    if (menuVisible) modelMenuVisible = false
                },
                onRingClick = { sheetVisible = true },
                onModelClick = {
                    modelMenuVisible = !modelMenuVisible
                    if (modelMenuVisible) menuVisible = false
                },
                onSend = {
                    val text = input.trim()
                    if (text.isNotEmpty() || pendingAttachment != null) {
                        val editFrom = editing?.id
                        val quote = quoting
                        input = ""
                        editing = null
                        quoting = null
                        onSend(text, editFrom, quote)
                    }
                },
                streaming = streaming,
                onStop = onStop,
                // 只有附件没文字时也要能发
                sendEnabled = input.isNotBlank() || pendingAttachment != null,
                modifier = Modifier.onSizeChanged { barHeightPx = it.height },
            )
        }

        if (modelMenuVisible) {
            VxActionMenu(
                items = if (providers.isEmpty()) {
                    listOf(
                        VxMenuItem(
                            icon = VxIcon.Key,
                            label = "去配置服务商",
                            onClick = {
                                modelMenuVisible = false
                                onOpenModelSetup()
                            },
                        ),
                    )
                } else {
                    providers.map { provider ->
                        VxMenuItem(
                            icon = if (provider.id == activeProviderId) VxIcon.Check else VxIcon.Model,
                            label = if (provider.model.isBlank()) {
                                provider.name
                            } else {
                                "${provider.name} · ${provider.model}"
                            },
                            onClick = {
                                modelMenuVisible = false
                                onSelectProvider(provider)
                            },
                        )
                    } + VxMenuItem(
                        icon = VxIcon.Sliders,
                        label = "管理服务商与模型",
                        showChevron = true,
                        onClick = {
                            modelMenuVisible = false
                            onOpenModelSetup()
                        },
                    )
                },
                visible = true,
                onDismiss = { modelMenuVisible = false },
                modifier = Modifier
                    // 贴着输入栏右下角：模型胶囊本身就在右半边，菜单在左边会显得没关系
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = barHeight + 8.dp),
                width = 252.dp,
            )
        }

        VxActionMenu(
            items = listOf(
                VxMenuItem(
                    icon = VxIcon.Paperclip,
                    label = "上传图片 / 文本",
                    onClick = {
                        menuVisible = false
                        filePicker.launch(arrayOf("*/*"))
                    },
                ),
                VxMenuItem(
                    icon = VxIcon.Sparkles,
                    label = if (agentEnabled) "关闭 Agent 模式" else "开启 Agent 模式",
                    onClick = {
                        menuVisible = false
                        onToggleAgent()
                    },
                ),
            ),
            visible = menuVisible,
            onDismiss = { menuVisible = false },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = barHeight + 6.dp),
            width = 220.dp,
        )

        VxSnackbar(
            message = lastSnackbarText,
            visible = snackbar != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = barHeight + 16.dp),
        )

        CompressSheet(
            visible = sheetVisible,
            currentThreshold = compressAt,
            messageCount = bubbles.size,
            triggerTokens = compressTrigger,
            onSelect = {
                onCompressAtChange(it)
                sheetVisible = false
            },
            onSelectTrigger = { onCompressTriggerChange(it) },
            onDismiss = { sheetVisible = false },
        )

        if (approval != null) {
            VxActionSheet(
                visible = true,
                title = trf("需要确认：%s", approval.displayName),
                caption = buildString {
                    append(approval.origin)
                    if (approval.sideEffect) append(tr(" · 有副作用"))
                    append(tr("\n参数：")).append(approval.argumentsPreview)
                    if (approval.scopes.isNotEmpty()) {
                        append(tr("\n权限：")).append(approval.scopes.joinToString(tr("、")))
                    }
                },
                actions = listOf(
                    VxSheetAction(
                        label = "允许这一次",
                        icon = VxIcon.Check,
                        onClick = { onApproval(ApprovalDecision.AllowOnce) },
                    ),
                    VxSheetAction(
                        label = "本轮内都允许",
                        icon = VxIcon.Shield,
                        onClick = { onApproval(ApprovalDecision.AllowForSession) },
                    ),
                    VxSheetAction(
                        label = "拒绝",
                        icon = VxIcon.Close,
                        destructive = true,
                        onClick = { onApproval(ApprovalDecision.Deny) },
                    ),
                ),
                onDismiss = { onApproval(ApprovalDecision.Deny) },
            )
        }

        val file = fileTarget
        if (file != null) {
            VxActionSheet(
                visible = true,
                title = file.displayName,
                caption = file.filePath?.let { trf("沙箱路径：%s", it) } ?: tr("文件已写入沙箱"),
                actions = listOf(
                    VxSheetAction(
                        label = "用其他应用打开",
                        icon = VxIcon.SendUp,
                        onClick = {
                            val path = file.filePath.orEmpty()
                            val box = sandbox
                            val opened = box != null && openSandboxFile(context, box, path)
                            snackbar = if (opened) {
                                "已交给系统打开"
                            } else {
                                trf("没有能打开 %s 的应用，可试用「分享」", path.substringAfterLast('/'))
                            }
                            fileTarget = null
                        },
                    ),
                    VxSheetAction(
                        label = "在应用内预览",
                        icon = VxIcon.Doc,
                        onClick = {
                            previewTarget = file
                            fileTarget = null
                        },
                    ),
                    VxSheetAction(
                        label = "分享文件",
                        icon = VxIcon.SendUp,
                        onClick = {
                            val box = sandbox
                            val shared = box != null && shareSandboxFile(context, box, file.filePath)
                            snackbar = if (shared) "已选择分享目标" else "分享失败"
                            fileTarget = null
                        },
                    ),
                    VxSheetAction(
                        label = "复制文件路径",
                        icon = VxIcon.Copy,
                        onClick = {
                            val absolute = runCatching {
                                sandbox?.resolve(file.filePath.orEmpty())?.absolutePath
                            }.getOrNull().orEmpty()
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            cm.setPrimaryClip(
                                android.content.ClipData.newPlainText("path", absolute),
                            )
                            snackbar = "已复制路径"
                            fileTarget = null
                        },
                    ),
                ),
                onDismiss = { fileTarget = null },
            )
        }

        previewTarget?.let { preview ->
            FilePreviewSheet(
                visible = true,
                path = preview.filePath.orEmpty(),
                sandbox = sandbox,
                onDismiss = { previewTarget = null },
            )
        }

        val target = actionTarget
        if (target != null) {
            val isLastAssistant = !target.fromUser && target.id == bubbles.lastOrNull()?.id
            VxActionSheet(
                visible = true,
                title = if (target.fromUser) "你的消息" else "助手消息",
                actions = buildList {
                    add(
                        VxSheetAction(
                            label = "执行轨迹",
                            icon = VxIcon.Sliders,
                            onClick = {
                                actionTarget = null
                                onOpenRuns()
                            },
                        ),
                    )
                    add(
                        VxSheetAction(
                            label = "复制",
                            icon = VxIcon.Copy,
                            onClick = {
                                val cm = context.getSystemService(
                                    android.content.Context.CLIPBOARD_SERVICE,
                                ) as android.content.ClipboardManager
                                cm.setPrimaryClip(
                                    android.content.ClipData.newPlainText("message", target.text),
                                )
                                actionTarget = null
                                snackbar = "已复制"
                            },
                        ),
                    )
                    if (target.text.isNotBlank()) {
                        add(
                            VxSheetAction(
                                label = "引用",
                                icon = VxIcon.Message,
                                onClick = {
                                    quoting = target
                                    actionTarget = null
                                    snackbar = "已引用，发送时会把这段原文一起带上"
                                },
                            ),
                        )
                    }
                    if (target.fromUser) {
                        add(
                            VxSheetAction(
                                label = "编辑并重发",
                                icon = VxIcon.Pencil,
                                onClick = {
                                    input = target.text
                                    editing = target
                                    actionTarget = null
                                    snackbar = "修改后发送，将替换这条消息之后的内容"
                                },
                            ),
                        )
                    }
                    if (isLastAssistant) {
                        add(
                            VxSheetAction(
                                label = "重新生成",
                                icon = VxIcon.Refresh,
                                onClick = {
                                    actionTarget = null
                                    onRegenerate()
                                },
                            ),
                        )
                    }
                },
                onDismiss = { actionTarget = null },
            )
        }
    }
}

/** 编辑态 / 附件态共用的输入区上方小胶囊。 */
@Composable
private fun AttachmentChip(
    icon: VxIcon,
    label: String,
    onClear: () -> Unit,
) {
    val colors = VeluneTheme.colors
    Row(
        modifier = Modifier
            .clip(SquircleCapsule())
            .background(colors.fill)
            .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VxIcon(icon = icon, tint = colors.textSecondary, size = 13.dp, strokeWidth = 1.8.dp)
        Spacer(modifier = Modifier.width(6.dp))
        VxText(
            text = label,
            style = VeluneTheme.type.footnote.copy(fontSize = 11.sp),
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 230.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(SquircleCapsule())
                .clickable(onClick = onClear),
            contentAlignment = Alignment.Center,
        ) {
            VxIcon(icon = VxIcon.Close, tint = colors.textTertiary, size = 11.dp, strokeWidth = 2.dp)
        }
    }
}

/** 对话页上的一条消息。与存储层解耦，预览也能直接用。 */
internal data class Bubble(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val createdAt: Long,
    val attachment: ChatAttachment? = null,
    /** 思考过程，落库后用于历史渲染 */
    val reasoning: String = "",
    /** 思考耗时（毫秒） */
    val reasoningMs: Long = 0,
    /** 本轮工具调用过程（JSON，见 ToolStepCodec） */
    val steps: String = "",
    /** 引用的那条消息（预览文本冗余存，原消息删除后仍可显示） */
    val replyToId: Long? = null,
    val replyPreview: String = "",
    /**
     * 消息来源标签，例如「QQ 私聊 · 张三」。
     *
     * 只有从 QQ 远程入口写进来的消息才有值。为空时界面与历史版本完全一致——
     * App 里自己发的消息本来就知道是自己说的，不该多挂一行小字。
     */
    val sender: String = "",
)

private fun StoredMessage.toBubble() = Bubble(
    id = id,
    fromUser = role == MessageRole.User,
    text = text,
    createdAt = createdAt,
    attachment = attachment,
    reasoning = reasoning,
    reasoningMs = reasoningMs,
    steps = steps,
    replyToId = replyToId,
    replyPreview = replyPreview,
    sender = sender,
)

/**
 * 气泡 → 发给模型的一条消息。
 *
 * 来源前缀收敛在**这一处**：`buildHistory()`（历史）与 `outgoingMessage()`
 * 的 `regenerate` 分支都经过它，一处改两处对，不会再出现「某个分支少前缀」。
 * `edited` 分支走 `chatMessageOf(text, …)`，不经过这里，所以编辑重发不会被套上旧来源。
 *
 * sender 为空（App 内自己发的消息）时正文逐字不变。
 */
private fun Bubble.toChatMessage(): ChatMessage =
    if (fromUser) {
        // 来源标签只进模型上下文：QQ 写进同一条会话的消息要能看出是谁说的。
        // 格式由 core 层的 senderPrefixed() 统一维护；sender 为空时正文逐字不变
        chatMessageOf(senderPrefixed(sender, text), attachment)
    } else {
        ChatMessage(role = "assistant", text = text)
    }

private fun LazyListState.isAtBottom(): Boolean {
    val total = layoutInfo.totalItemsCount
    if (total == 0) return true
    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return true
    // 留一格冗余：底部锚点那条 Spacer 会让最后一条消息不在可见项末尾
    return lastVisible >= total - 2
}

@Composable
private fun ChatTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            VxIconButton(
                icon = VxIcon.Back,
                contentDescription = tr("返回"),
                onClick = onBack,
                tint = colors.textPrimary,
                size = 40.dp,
                iconSize = 19.dp,
            )
        } else {
            Spacer(modifier = Modifier.width(40.dp))
        }

        // 标题居中：与聊天类 App 的顶栏一致，右侧不再挂状态文本
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            VxText(
                text = title,
                style = type.centerTitle,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (onSearch != null) {
            VxIconButton(
                icon = VxIcon.Search,
                contentDescription = tr("搜索对话内容"),
                onClick = onSearch,
                tint = colors.textPrimary,
                size = 40.dp,
                iconSize = 18.dp,
            )
        } else {
            // 占位保持标题居中：左侧返回锄 40dp，右侧没有按钮时补一个等宽空位
            Spacer(modifier = Modifier.width(40.dp))
        }
    }
}

@Composable
private fun EmptyConversation(modifier: Modifier = Modifier, hasProvider: Boolean = false) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                // 芯片跟着字号长高，固定 38dp 在大字号下会把「Velune」裁掉
                .heightIn(min = 38.dp)
                .clip(SquircleCapsule())
                .background(colors.fill)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            VxText(text = "Velune", style = type.footnote, color = colors.textSecondary)
        }

        Spacer(modifier = Modifier.height(14.dp))

        VxText(
            text = "你好，我是 Velune。",
            style = type.title,
            color = colors.textPrimary,
        )

        Spacer(modifier = Modifier.height(8.dp))

        VxText(
            // 文案不能写死：已经配好服务商的用户被告知「先去配置」，
            // 只会让人以为没保存成功，再跑去设置里翻一遍。
            text = if (hasProvider) {
                "直接发消息就能开始对话。"
            } else {
                "先在设置里配置服务商与 API Key，\n然后就能直接对话。"
            },
            style = type.footnote,
            color = colors.textSecondary,
        )
    }
}

/** 没有配置任何服务商时的提示：点一下去设置。 */
@Composable
private fun ModelNotConnectedHint(onClick: () -> Unit) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type

    Row(
        modifier = Modifier
            .padding(top = 16.dp)
            .clip(SquircleCapsule())
            .background(colors.fill)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = vxRipple(colors.textPrimary.copy(alpha = 0.06f)),
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VxIcon(icon = VxIcon.Warning, tint = colors.warning, size = 15.dp)
        Spacer(modifier = Modifier.width(7.dp))
        VxText(
            text = "尚未配置服务商 · 去设置",
            style = type.footnote,
            color = colors.textSecondary,
        )
    }
}

/** 会话内的时间分隔：今天的只显示时间，跨天显示「昨天 HH:mm」/「M月d日 HH:mm」。 */
@Composable
private fun TimeDivider(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        VxText(
            text = label,
            style = VeluneTheme.type.footnote,
            color = VeluneTheme.colors.textTertiary,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    bubble: Bubble,
    topGap: Dp,
    onLongPress: () -> Unit,
    reasoning: String = "",
    parts: List<StreamPart>? = null,
    /** 思考耗时（毫秒）：流式中传实时值，历史消息传落库值 */
    reasoningMs: Long = 0,
    expandedToolId: String? = null,
    onToggleTool: (String) -> Unit = {},
    /** 音频产物要就地播放，需要把沙箱路径解析成真实文件 */
    sandbox: com.veluneagent.core.data.sandbox.Sandbox? = null,
    onFileAction: (ToolStepUi) -> Unit = {},
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type
    val interaction = remember { MutableInteractionSource() }

    Column(modifier = Modifier.padding(top = topGap)) {
        if (bubble.fromUser) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val maxBubbleWidth = maxWidth * 0.78f
                val shape = SquircleCorners(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomEnd = 6.dp,
                    bottomStart = 20.dp,
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 来源标签：只有 QQ 转发进来的消息才有。为空时这一行完全不存在，
                    // 保证 App 内对话的排版与历史版本逐像素一致
                    if (bubble.sender.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 6.dp, bottom = 3.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            VxText(
                                text = bubble.sender,
                                style = type.footnote.copy(fontSize = 11.sp),
                                color = colors.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = maxBubbleWidth)
                                // 极轻投影：白底上让浅蓝色块与画布分离，不靠加边框
                                .shadow(
                                    elevation = 2.dp,
                                    shape = shape,
                                    clip = false,
                                    // 同上：文字色在深色主题是白色，不能拿来当阴影
                                    ambientColor = Color.Black.copy(alpha = 0.08f),
                                    spotColor = Color.Black.copy(alpha = 0.12f),
                                )
                                .clip(shape)
                                .background(colors.bubbleUser)
                                .combinedClickable(
                                    interactionSource = interaction,
                                    indication = null,
                                    onClick = {},
                                    onLongClick = onLongPress,
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            if (bubble.replyPreview.isNotBlank()) {
                                QuoteStrip(text = bubble.replyPreview, onBubble = true)
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            bubble.attachment?.let { AttachmentBubble(it) }
                            if (bubble.text.isNotBlank()) {
                                VxText(
                                    text = bubble.text,
                                    style = type.body.copy(lineHeight = 25.sp),
                                    color = colors.textPrimary,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = {},
                        onLongClick = onLongPress,
                    ),
            ) {
                if (bubble.replyPreview.isNotBlank()) {
                    QuoteStrip(text = bubble.replyPreview, onBubble = false)
                    Spacer(modifier = Modifier.height(6.dp))
                }
                if (reasoning.isNotBlank()) {
                    ReasoningBlock(
                        text = reasoning,
                        streaming = bubble.text.isBlank() || bubble.text == AssistantPlaceholder &&
                            parts.isNullOrEmpty(),
                        durationMs = reasoningMs,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                if (parts.isNullOrEmpty()) {
                    if (bubble.text.isBlank() || bubble.text == AssistantPlaceholder) {
                        TypingDots()
                    } else {
                        MarkdownText(text = bubble.text, color = colors.textPrimary)
                    }
                } else {
                    // 按发生顺序渲染：文字段 → 工具卡 → 文字段 → …
                    parts.forEach { part ->
                        when (part) {
                            is StreamPart.Text -> {
                                MarkdownText(text = part.text, color = colors.textPrimary)
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            is StreamPart.Tool -> {
                                ToolStepCard(
                                    step = part.step,
                                    expanded = expandedToolId == part.key,
                                    onToggle = { onToggleTool(part.key) },
                                    onFileAction = { onFileAction(part.step) },
                                    audioFile = part.step.filePath
                                        ?.takeIf { isAudioPath(it) }
                                        ?.let { path -> runCatching { sandbox?.resolve(path) }.getOrNull() },
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 用户气泡里的附件：图片直接显示缩略图，文本显示一行文件标签。 */
@Composable
private fun AttachmentBubble(attachment: ChatAttachment) {
    val colors = VeluneTheme.colors
    if (attachment.kind != AttachmentKind.Image) {
        AttachmentTag(attachment)
        return
    }
    val bitmap = remember(attachment.dataUrl) {
        runCatching {
            val base64 = attachment.dataUrl.substringAfter("base64,", "")
            val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = attachment.name,
            modifier = Modifier
                .padding(bottom = 6.dp)
                .widthIn(max = 200.dp)
                .height(140.dp)
                .clip(SquircleShape(12.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        AttachmentTag(attachment)
    }
}

@Composable
private fun AttachmentTag(attachment: ChatAttachment) {
    val colors = VeluneTheme.colors
    Row(
        modifier = Modifier
            .padding(bottom = 6.dp)
            .clip(SquircleShape(10.dp))
            .background(colors.surface.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VxIcon(
            icon = if (attachment.kind == AttachmentKind.Image) VxIcon.Model else VxIcon.Doc,
            tint = colors.textSecondary,
            size = 12.dp,
            strokeWidth = 1.8.dp,
        )
        Spacer(modifier = Modifier.width(5.dp))
        VxText(
            text = attachment.name,
            style = VeluneTheme.type.footnote.copy(fontSize = 11.sp),
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp),
        )
    }
}

/**
 * 思考过程折叠块。
 *
 * 三种形态，各有理由：
 * - **流式中**：给一个 ~4 行高的窗口，内部滚动、自动跟随最新一行。思考内容动辄几千字，
 *   全量铺开会把正文顶出屏幕，用户还得一路往上翻才能看到答案；
 * - **历史消息收起**：只留一行「思考 N 秒 · M 字」。过程材料不该长期占版面；
 * - **展开**：320dp 上限，仍然内部滚动，避免一条消息把整个会话撑爆。
 *
 * 跟随策略：只有当滚动位置已经贴着底部时才自动滚。用户往上翻，就不再抢他的位置；
 * 他自己滑回底部，下一段增量会自动恢复跟随。不用额外的"暂停跟随"按钮。
 */
@Composable
private fun ReasoningBlock(
    text: String,
    streaming: Boolean = false,
    durationMs: Long = 0,
) {
    val colors = VeluneTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    // 箭头旋转代替图标互换：与更新日志折叠同一套（因果感 + 无停顿）
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(
            durationMillis = if (expanded) VxMotion.ExpandMs else VxMotion.CollapseMs,
            easing = if (expanded) VxMotion.EaseOutCubic else VxMotion.EaseOutSine,
        ),
        label = "reasoningChevron",
    )
    val chars = formatCharCount(text.length)
    // 有耗时才说「思考 N 秒」；老消息没有这个数据，不要编一个 0 秒出来
    val label = when {
        streaming -> trf("思考中… · %s", chars)
        durationMs > 0 -> trf("思考 %s · %s", formatSeconds(durationMs), chars)
        else -> trf("思考过程 · %s", chars)
    }
    // 只在这个距离内认为"用户还在底部"；24px 约半行，避免抖动
    val followThreshold = 24
    LaunchedEffect(text.length, expanded) {
        if (scrollState.maxValue - scrollState.value <= followThreshold) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(10.dp))
            // 思考是过程材料，视觉权重必须低于正文：只用一层极浅底，不要实心灰块
            .background(colors.fill.copy(alpha = 0.26f))
            .padding(horizontal = 9.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SquircleShape(6.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = vxRipple(colors.textPrimary.copy(alpha = 0.04f)),
                    onClick = { expanded = !expanded },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VxIcon(
                icon = VxIcon.ChevronDown,
                tint = colors.textTertiary,
                size = 11.dp,
                strokeWidth = 2.dp,
                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
            )
            Spacer(modifier = Modifier.width(5.dp))
            VxText(
                text = label,
                style = VeluneTheme.type.footnote.copy(fontSize = 11.sp),
                color = colors.textTertiary,
            )
        }

        // 流式时给一个小窗口；收起的历史消息不占版面，点一下才铺开。
        // 高度过渡 + 淡入淡出，与更新日志折叠同一套（之前是 if 硬切，卡片像被抽掉一截）
        val showBody = text.isNotBlank() && (streaming || expanded)
        AnimatedVisibility(
            visible = showBody,
            enter = expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = tween(VxMotion.ExpandMs, easing = VxMotion.EaseOutCubic),
            ) + fadeIn(tween(VxMotion.EnterAlphaMs, easing = VxMotion.EaseOutQuad)),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(VxMotion.CollapseMs, easing = VxMotion.EaseOutSine),
            ) + fadeOut(tween(VxMotion.CollapseMs, easing = VxMotion.EaseOutSine)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (expanded) 320.dp else 78.dp)
                    .verticalScroll(scrollState),
            ) {
                VxText(
                    text = text.trim(),
                    style = VeluneTheme.type.footnote.copy(fontSize = 12.sp, lineHeight = 19.sp),
                    color = colors.textTertiary,
                )
            }
        }
    }
}

/**
 * 等待首字时的三点呼吸。
 *
 * 原本这里是一行「正在输入」——它占了和正文一样的位置，却什么信息也没有。
 * 三个点更短、更像"正在发生"，也不会让气泡在首字到达时突然从一个词变成一个段落。
 */
@Composable
private fun TypingDots() {
    val colors = VeluneTheme.colors
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "typing")
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(
                        durationMillis = 520,
                        delayMillis = index * 140,
                    ),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                modifier = Modifier
                    .padding(end = if (index == 2) 0.dp else 5.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(colors.textTertiary.copy(alpha = alpha)),
            )
        }
    }
}

/**
 * 工具调用内联芯片：把「Agent 做了什么」摊开在文字流里。
 *
 * 形态参考对话式产品的动作行：圆角方状态图标 + 动作名 + 结果摘要（截断）+「›」，
 * 宽度包裹内容、嵌在两段文字之间；点按展开来源、参数与返回。
 * 这是可解释性的最小载体 —— 只显示最终答案、不显示中间动作的产品，
 * 用户无法判断结果是查到的还是编的。
 */
@Composable
private fun ToolStepCard(
    step: ToolStepUi,
    expanded: Boolean,
    onToggle: () -> Unit,
    onFileAction: () -> Unit,
    audioFile: java.io.File? = null,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type
    val (icon, tint) = when (step.status) {
        ToolStepStatus.Running -> VxIcon.Refresh to colors.accent
        ToolStepStatus.Ok -> VxIcon.Check to colors.success
        ToolStepStatus.Failed -> VxIcon.Warning to colors.danger
        ToolStepStatus.Denied -> VxIcon.Close to colors.warning
    }

    // 执行中图标慢速旋转：比静态刷新图标更能传达「正在发生」。
    // 只有 Running 时才创建动画，完成的卡片不挂无限动画
    val spin: Float = if (step.status == ToolStepStatus.Running) {
        val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "toolSpin")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(
                    durationMillis = 900,
                    easing = androidx.compose.animation.core.LinearEasing,
                ),
            ),
            label = "toolSpinAngle",
        )
        angle
    } else {
        0f
    }

    // 「›」展开时转 90° 朝下：收起朝右、展开朝下，与行内动作芯片的方向感一致
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(
            durationMillis = if (expanded) VxMotion.ExpandMs else VxMotion.CollapseMs,
            easing = if (expanded) VxMotion.EaseOutCubic else VxMotion.EaseOutSine,
        ),
        label = "toolChevron",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        // 内联芯片：宽度包裹内容，不再通栏——视觉上是文字流里的一个动作，
        // 而不是一整块面板。点按展开参数与返回。
        Row(
            modifier = Modifier
                .clip(SquircleShape(12.dp))
                .background(colors.fill.copy(alpha = 0.7f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = vxRipple(colors.textPrimary.copy(alpha = 0.05f)),
                    onClick = onToggle,
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 状态图标装进圆角方底：结果一眼可辨（✓ 成功 / ⚠ 失败 / 旋转=执行中）
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(SquircleShape(8.dp))
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                VxIcon(
                    icon = icon,
                    tint = tint,
                    size = 13.dp,
                    strokeWidth = 2.dp,
                    modifier = if (step.status == ToolStepStatus.Running) {
                        Modifier.graphicsLayer { rotationZ = spin }
                    } else {
                        Modifier
                    },
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            VxText(
                text = step.displayName,
                style = type.footnote.copy(fontSize = 12.sp),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 结果摘要内联在动作名后面（收起时）：「运行命令 查看 xxx 函数体」一眼可读
            if (!expanded && step.detail != null) {
                Spacer(modifier = Modifier.width(6.dp))
                VxText(
                    text = step.detail,
                    style = type.footnote.copy(fontSize = 11.sp),
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 200.dp),
                )
            }
            Spacer(modifier = Modifier.width(7.dp))
            VxIcon(
                icon = VxIcon.ChevronRight,
                tint = colors.textTertiary,
                size = 12.dp,
                strokeWidth = 2.dp,
                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
            )
        }

        // 语音卡片不需要展开就能播：播放器自己吞掉点击，
        // 否则点一下播放会顺带把卡片收起/展开
        if (audioFile != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            ) {
                AudioPlayerBar(file = audioFile)
            }
        }

        // 展开/收起走高度过渡，与思考块一致；之前是 if 硬切
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = tween(VxMotion.ExpandMs, easing = VxMotion.EaseOutCubic),
            ) + fadeIn(tween(VxMotion.EnterAlphaMs, easing = VxMotion.EaseOutQuad)),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(VxMotion.CollapseMs, easing = VxMotion.EaseOutSine),
            ) + fadeOut(tween(VxMotion.CollapseMs, easing = VxMotion.EaseOutSine)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(6.dp))
                // 来源与耗时收进详情：芯片行只保留「做了什么」，噪音少一半
                VxText(
                    text = buildString {
                        append(step.origin)
                        if (step.durationMs > 0) append(" · ${step.durationMs}ms")
                    },
                    style = type.footnote.copy(fontSize = 10.sp),
                    color = colors.textTertiary,
                )
                if (step.detail != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    VxText(
                        text = step.detail,
                        style = type.footnote.copy(fontSize = 11.sp),
                        color = when (step.status) {
                            ToolStepStatus.Failed -> colors.danger
                            ToolStepStatus.Denied -> colors.warning
                            else -> colors.textSecondary
                        },
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                VxText(
                    text = "参数",
                    style = type.footnote.copy(fontSize = 10.sp),
                    color = colors.textTertiary,
                )
                VxText(
                    text = step.argsFull.ifBlank { "{}" },
                    style = type.footnote.copy(
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp,
                    ),
                    color = colors.textSecondary,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
                if (step.resultFull != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    VxText(
                        text = "返回",
                        style = type.footnote.copy(fontSize = 10.sp),
                        color = colors.textTertiary,
                    )
                    VxText(
                        text = step.resultFull.take(1_500),
                        style = type.footnote.copy(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp,
                        ),
                        color = colors.textSecondary,
                        maxLines = 12,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (step.filePath != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .clip(SquircleCapsule())
                            .background(colors.surface)
                            .clickable(onClick = onFileAction)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VxIcon(icon = VxIcon.Doc, tint = colors.accent, size = 12.dp)
                        Spacer(modifier = Modifier.width(5.dp))
                        VxText(
                            text = trf("打开 %s", step.filePath),
                            style = type.footnote.copy(fontSize = 11.sp),
                            color = colors.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 文件预览。文本文件直接在面板里看，二进制/超大文件给出下载路径提示。
 * 读取放在 IO 线程：沙箱允许到 256KB，主线程读会掉帧。
 */
@Composable
private fun FilePreviewSheet(
    visible: Boolean,
    path: String,
    sandbox: com.veluneagent.core.data.sandbox.Sandbox?,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val colors = VeluneTheme.colors
    val content by produceState(initialValue = "读取中…", path) {
        value = withContext(Dispatchers.IO) {
            if (sandbox == null) {
                "沙箱不可用"
            } else {
                runCatching { sandbox.read(path, 8_000) }
                    .getOrElse { it.message ?: "读取失败" }
            }
        }
    }
    BackHandler(onBack = onDismiss)
    val scroll = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .padding(10.dp)
                .clip(SquircleShape(18.dp))
                .background(colors.surface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VxText(
                    text = path,
                    style = VeluneTheme.type.rowTitle,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                VxIconButton(
                    icon = VxIcon.Close,
                    contentDescription = tr("关闭预览"),
                    onClick = onDismiss,
                    tint = colors.textTertiary,
                    size = 40.dp,
                    iconSize = 14.dp,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            VxText(
                text = content,
                style = VeluneTheme.type.footnote.copy(
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp,
                ),
                color = colors.textSecondary,
                modifier = Modifier.verticalScroll(scroll),
            )
        }
    }
}

/**
 * 字数展示：一万以内按字，超过改用 k。
 * 「思考过程 · 12480 字」在手机上会挤掉右侧时间，而 12.5k 一眼能读。
 */
internal fun formatCharCount(count: Int): String =
    if (count < 10_000) trf("%d 字", count) else trf("%.1fk 字", count / 1000f)

/** 秒级展示：不足 1 秒按 1 秒算，别显示「思考 0 秒」。 */
internal fun formatSeconds(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(1)
    return if (seconds < 60) "${seconds}s" else "${seconds / 60}m${seconds % 60}s"
}

private fun formatChatTime(millis: Long): String {
    if (millis <= 0L) return ""
    val zone = ZoneId.systemDefault()
    val moment = Instant.ofEpochMilli(millis).atZone(zone)
    val today = Instant.now().atZone(zone).toLocalDate()
    val date = moment.toLocalDate()
    return when {
        date == today -> moment.format(DateTimeFormatter.ofPattern("HH:mm"))
        date == today.minusDays(1) -> tr("昨天 ") + moment.format(DateTimeFormatter.ofPattern("HH:mm"))
        date.year == today.year -> trf("%d月%d日 ", date.monthValue, date.dayOfMonth) +
            moment.format(DateTimeFormatter.ofPattern("HH:mm"))
        else -> "${date.year}/${date.monthValue}/${date.dayOfMonth}"
    }
}

private val previewBubbles = listOf(
    Bubble(1, false, "你好，我是 Velune。可以直接发消息，也可以让我读文件、查资料。", System.currentTimeMillis() - 60_000),
    Bubble(2, true, "帮我把这张截图里的功能点整理成清单。", System.currentTimeMillis() - 30_000),
)

@Preview(name = "对话页 · 浅色", widthDp = 400, heightDp = 780, showBackground = true)
@Composable
private fun ChatScreenPreview() {
    VeluneTheme(darkTheme = false) {
        ChatContent(
            title = "新对话",
            bubbles = previewBubbles.map { it.copy(fromUser = false) },
            onBack = {},
            onSend = { _, _, _ -> },
            onOpenModelSetup = {},
        )
    }
}

@Preview(name = "对话页 · 深色", widthDp = 400, heightDp = 780, showBackground = true)
@Composable
private fun ChatScreenDarkPreview() {
    VeluneTheme(darkTheme = true) {
        ChatContent(
            title = "新对话",
            bubbles = previewBubbles,
            onBack = {},
            onSend = { _, _, _ -> },
            onOpenModelSetup = {},
        )
    }
}

/** 只有容器格式能交给 MediaPlayer；pcm 裸流没有头信息，播不了。 */
private fun isAudioPath(path: String): Boolean {
    val lower = path.lowercase()
    return lower.endsWith(".wav") || lower.endsWith(".mp3") ||
        lower.endsWith(".m4a") || lower.endsWith(".ogg") || lower.endsWith(".aac")
}

/**
 * 引用条：显示被引用的原文片段。
 *
 * 只显示两行并省略：引用条的作用是"提醒在回哪句话"，把整段原文铺开会喧宾夺主。
 * 预览文本在写入时已截断到 160 字，这里再按行数兜一层。
 */
@Composable
private fun QuoteStrip(text: String, onBubble: Boolean) {
    val colors = VeluneTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(6.dp))
            .background(
                if (onBubble) {
                    colors.textPrimary.copy(alpha = 0.05f)
                } else {
                    colors.fill.copy(alpha = 0.6f)
                },
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(22.dp)
                .clip(CircleShape)
                .background(colors.accent.copy(alpha = 0.55f)),
        )
        Spacer(modifier = Modifier.width(7.dp))
        VxText(
            text = text.replace("\n", " "),
            style = VeluneTheme.type.footnote.copy(fontSize = 11.sp, lineHeight = 16.sp),
            color = colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 本轮计划清单。
 *
 * 放在输入框上方而不是气泡里：它是"现在到哪一步"的实时状态，跟着消息走会越滚越远。
 * 完成项降透明度、当前项高亮、未开始项灰点 —— 只有三种状态，不做进度条，
 * 步数少（≤10）时进度条反而看不出差别。
 */
@Composable
private fun PlanCard(steps: List<com.veluneagent.core.data.agent.PlanStep>) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type
    val done = steps.count { it.status == com.veluneagent.core.data.agent.PlanStatus.Done }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(SquircleShape(12.dp))
            .background(colors.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        VxText(
            text = trf("计划 %d/%d", done, steps.size),
            style = type.footnote.copy(fontSize = 11.sp),
            color = colors.textTertiary,
        )
        steps.take(6).forEach { step ->
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                VxIcon(
                    icon = when (step.status) {
                        com.veluneagent.core.data.agent.PlanStatus.Done -> VxIcon.Check
                        com.veluneagent.core.data.agent.PlanStatus.Running -> VxIcon.Refresh
                        else -> VxIcon.ChevronDown
                    },
                    tint = when (step.status) {
                        com.veluneagent.core.data.agent.PlanStatus.Done -> colors.success
                        com.veluneagent.core.data.agent.PlanStatus.Running -> colors.accent
                        else -> colors.textTertiary
                    },
                    size = 11.dp,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(6.dp))
                VxText(
                    text = step.title,
                    style = type.footnote.copy(fontSize = 12.sp),
                    color = if (step.status == com.veluneagent.core.data.agent.PlanStatus.Done) {
                        colors.textTertiary
                    } else {
                        colors.textPrimary
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (steps.size > 6) {
            Spacer(modifier = Modifier.height(4.dp))
            VxText(
                text = trf("还有 %d 步", steps.size - 6),
                style = type.footnote.copy(fontSize = 11.sp),
                color = colors.textTertiary,
            )
        }
    }
}
