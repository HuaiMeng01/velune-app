package com.veluneagent.core.data.im

import android.content.Context
import android.util.Log
import com.veluneagent.core.data.AssistantPlaceholder
import com.veluneagent.core.data.ChatMessage
import com.veluneagent.core.data.ChatStore
import com.veluneagent.core.data.ChatStreamGuard
import com.veluneagent.core.data.InterruptedMarker
import com.veluneagent.core.data.MessageRole
import com.veluneagent.core.data.Provider
import com.veluneagent.core.data.ProviderStore
import com.veluneagent.core.data.StreamEvent
import com.veluneagent.core.data.TokenEstimator
import com.veluneagent.core.data.agent.ModelGateway
import com.veluneagent.core.data.agent.OpenAiGateway
import com.veluneagent.core.data.context.SummaryRepository
import com.veluneagent.core.data.context.SummaryStore
import com.veluneagent.core.data.forModelText
import com.veluneagent.core.data.prompt.PromptComposer
import com.veluneagent.core.data.prompt.PromptScene
import com.veluneagent.core.data.prompt.PromptStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * QQ 消息的自动回复。
 *
 * 两条路径：
 * - **闲聊**（默认）：走 [ModelGateway]（默认实现是 `OpenAiGateway`）直接答，无工具、最便宜；
 * - **只读 Agent**（用户在设置里显式打开）：交给 [QqAgentRunner]，可以查时间/搜索/算数，
 *   但看不到也调不了任何有副作用或涉隐私的工具。
 *
 * 为什么用 Mutex：同一个会话的两次请求并发跑会互相踩上下文，
 * 模型可能同时对同一条消息各答一次。串行化是这个功能最低成本的正确性保证。
 *
 * 落库约定（v11 起）：
 * - 用户消息落**裸文本** + 来源标签（`sender`），前缀只加给模型那一轮；
 * - **只有模型真正产出的正文**才作为 assistant 落库 —— 失败提示、忙碌提示都不是模型说的话，
 *   存进去下一轮会被当成"我自己刚说过"，把上下文污染成自说自话。
 */
class QqResponder(
    context: Context,
    private val store: ChatStore,
    /** 为空表示当前进程没有装配 Agent 依赖，此时无论开关怎么开都只能闲聊。 */
    private val agentRunner: QqAgentRunner? = null,
    /**
     * 模型调用的构造缝：默认值就是生产路径，测试换一个 fake [ModelGateway] 即可断言事件序列。
     *
     * 用 [ModelGateway] 而不是裸 `ChatClient.stream`：仓库里「发起一次流式模型调用」只能有一种形态
     * （agent 路径已经走 `ModelGateway`），这里不再引入第三种。
     * 测试侧也因此不需要构造 Usage 事件就能覆盖「端点不报 usage」这条计量兜底的 DoD。
     */
    private val gatewayFactory: (Provider, String, Boolean) -> ModelGateway =
        { provider, apiKey, thinking ->
            OpenAiGateway(
                provider = provider,
                apiKey = apiKey,
                // 闲聊路径不带工具：QQ 的工具开关走 runWithTools，那里有独立的只读白名单
                tools = { emptyList() },
                thinking = thinking,
            )
        },
    /**
     * 早前对话的压缩摘要，**只读**。
     *
     * QQ 侧绝不触发压缩（那是 App 的职责：压缩要花一次模型调用，而且会改写 messages 之外的
     * 存储）；这里只负责把已经存在的摘要拼进上下文，让 QQ 与 App 看到同一段历史。
     */
    private val summaries: SummaryRepository = SummaryStore(context.applicationContext),
    /**
     * 排队等待上限。默认值就是生产行为（[QUEUE_TIMEOUT_MS]）。
     *
     * 抽成参数只为一件事：超时分支（诚实告知 + 不建空会话）需要 45 秒才能触发，
     * 测试不可能真等，只能把它调小。行为零变化。
     */
    private val queueTimeoutMs: Long = QUEUE_TIMEOUT_MS,
) {

    /** 全局唯一的人设（用户在 设置 → 提示词 里配的那条）。 */
    private val promptStore = PromptStore(context)

    private val mutex = Mutex()

    data class Reply(val text: String, val conversationId: Long)

    /** 只读 Agent 当前可用的工具；UI 用来在开关下面写清"能做什么"。 */
    fun availableTools(): List<String> = agentRunner?.allowedTools.orEmpty()

    /**
     * 处理一条 QQ 消息。
     *
     * @param conversationId 用户在设置里选定的会话；`0` = 自动（沿用/新建「QQ 消息」）
     * @param thinking 是否开深度思考。**默认 false**：QQ 里等 20-40 秒等于没回。
     *   刻意与 App 内聊天页的深度思考开关（默认开）解耦，改 QQ 的开关不影响 App。
     */
    suspend fun handle(
        text: String,
        senderName: String,
        isGroup: Boolean,
        useTools: Boolean = false,
        conversationId: Long = 0L,
        thinking: Boolean = false,
    ): Result<Reply> {
        val raw = text.take(2_000)
        val sender = senderLabel(senderName, isGroup)

        // 排队而不是立刻丢弃：用户连发几条是很常见的用法，直接扔掉等于"机器人漏答"。
        // 但有上限 —— 上一轮最长 90 秒，无限等会把 QQ 的被动回复窗口（单聊 60 分钟 /
        // 群聊 5 分钟）耗光，所以最多等 [QUEUE_TIMEOUT_MS]，超时才回忙提示。
        //
        // 忙提示**不落库**（既不是用户发言，也不是模型说的话）：
        // 之前的实现把忙的时候那条用户消息也写进了库，结果是「我问了 3 句、它只答 1 句」
        // 的假证据 —— 用户回 App 一看就判定机器人漏答。
        val locked = withTimeoutOrNull(queueTimeoutMs) { mutex.lock() } != null
        if (!locked) {
            Log.w(Tag, "排队超时，本条未处理")
            // 不建会话、不落库：这条消息根本没被处理，说「稍等一下」是空头支票 ——
            // 用户会真的等，然后永远等不到。如实告知让他重发。
            // 也不能走 ensureConversation：那会给一条被丢弃的消息新建一个空会话留在列表里。
            return Result.success(Reply("（这条我没处理，请再发一次）", conversationId.coerceAtLeast(0L)))
        }
        return try {
            withContext(Dispatchers.IO) {
            val provider = ProviderStore.activeProvider()
                ?: return@withContext Result.failure(
                    IllegalStateException("没有可用服务商：先在「模型与密钥」里配置一个"),
                )
            if (provider.model.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("服务商未填写模型名：设置 → 模型与密钥"),
                )
            }
            val apiKey = ProviderStore.apiKeyOf(provider.id)
                ?: return@withContext Result.failure(IllegalStateException("服务商缺少 API Key"))

            val target = ensureConversation(conversationId)

            // App 正在这条会话里流式输出时先等它说完：否则读到的 history 里有半句话，
            // 模型会把别人的半成品当成"我自己刚说过的一句完整发言"接着编。
            // 最多等 5 秒，超时就不处理（不调模型、不落库），别把被动回复窗口耗在等待上。
            if (!awaitNotStreaming(target)) {
                return@withContext Result.success(
                    Reply("（App 里正在回你上一条，等它说完再问我）", target),
                )
            }

            // 历史 = 摘要（覆盖更早的 coveredCount 条）+ 摘要之后的原文。
            // 只带最近 20 条会让 QQ 看不到 App 里聊过的上文；全带又白花钱，
            // 所以按「摘要覆盖了多少」动态算窗口，上限 60 条兜成本。
            val summary = summaries.get(target)
            val covered = summary?.coveredCount ?: 0
            val total = store.messageCount(target)
            val take = (total - covered).coerceIn(MIN_HISTORY, MAX_HISTORY)

            // 先剔除占位/中断行，规则与 ChatScreen.buildHistory 一致：
            // App 与 QQ 共用会话后，进程被杀会留「（已中断）」占位，
            // 这些既不是模型产出也不是用户发言，发给上游只会污染上下文。
            // 带来源标签进上下文，模型才分得清「谁在说」（群聊里尤其重要）。
            val history = store.messages(target, take)
                .filter {
                    it.text.isNotBlank() &&
                        it.text != AssistantPlaceholder &&
                        it.text != InterruptedMarker
                }
                .map { ChatMessage(role = it.role.wire, text = it.forModelText()) }
            // 当轮交给模型的仍是带前缀的版本：本轮消息此时还没写进库
            val prompt = buildPrompt(text, senderName, isGroup)
            // 落库用裸文本：界面上气泡自己会显示来源小字，正文里再带前缀就是脏数据
            store.appendMessage(
                conversationId = target,
                role = MessageRole.User,
                text = raw,
                sender = sender,
            )

            // 人设全局一份；场景仍传 Qq —— L2 平台规则（你没有写权限 + 短回复）不能丢
            val persona = promptStore.activeAny()?.body
            val toolsOn = useTools && agentRunner != null
            // 搜索/抓取只认 DeepSeek Key：没配就明确告诉模型"你没有联网能力"，
            // 否则它会去调一个目录里根本不存在的工具，或者凭记忆编一条新闻
            val webAvailable = ProviderStore.hasDeepSeekKey()
            val systemMessages = PromptComposer.build(
                scene = PromptScene.Qq,
                persona = persona,
                qqToolsEnabled = toolsOn,
                userName = com.veluneagent.core.data.profile.UserProfile.nickname.value,
                qqWebAvailable = webAvailable,
            ) + summaryBlock(summary?.summary)

            if (toolsOn) {
                return@withContext runWithTools(
                    conversationId = target,
                    history = systemMessages + history,
                    prompt = prompt,
                    provider = provider,
                    apiKey = apiKey,
                    thinking = thinking,
                )
            }

            val buffer = StringBuilder()
            var failure: String? = null
            // 上游在流末尾发 Usage；不接住的话「用量」页永远是 0，而账单照样在涨。
            var promptTokens = 0
            var completionTokens = 0
            // 单独存一份：估算 prompt 时必须用「真正发给上游的那个列表」，
            // 少算 system 消息或本轮 prompt 会让两端数字对不上。
            val outbound = systemMessages + history + ChatMessage(role = "user", text = prompt)
            gatewayFactory(provider, apiKey, thinking).stream(outbound).collect { event ->
                when (event) {
                    is StreamEvent.Delta -> buffer.append(event.text)
                    is StreamEvent.Usage -> {
                        promptTokens = event.promptTokens
                        completionTokens = event.completionTokens
                    }
                    is StreamEvent.Failed -> failure = event.message
                    else -> Unit
                }
            }

            val produced = buffer.toString().trim()
            if (produced.isEmpty()) {
                // 模型一个字都没产出：QQ 那头要给句人话，但库里不能留"我说过这句话"的假记录
                val detail = failure ?: "模型没有返回内容"
                Log.w(Tag, "QQ 闲聊失败（未落库）：$detail")
                // 上游报错原文（HTTP 状态码、Invalid API key…）只留在 logcat：
                // 发给 QQ 对面的第三方既没用，又等于把内部信息送出去。
                return@withContext Result.success(Reply("（这条没答上来，回 App 里试试？）", target))
            }
            // 有正文但上游报过错：多半是被限流/超时掐断。正文照发，但必须留痕，
            // 否则线上分不清「模型就说到这」和「被 429 掐断」。
            // 不给正文追加「（回答中断）」后缀——QQ 里多一句括号更乱，还可能顶到长度上限。
            if (failure != null) {
                Log.w(Tag, "QQ 回复被中断，正文可能不完整：$failure")
            }

            // 用量：优先服务端回报，否则本地估算（与 ChatScreen 同一套规则）。
            // 兼容端点大多不返回 usage —— 不兜底的话「用量」页对绝大多数服务商永远是 0。
            val promptTotal = if (promptTokens > 0) {
                promptTokens
            } else {
                TokenEstimator.estimateMessages(outbound)
            }
            val completionTotal = if (completionTokens > 0) {
                completionTokens
            } else {
                TokenEstimator.estimate(produced)
            }
            val aid = store.appendMessage(target, MessageRole.Assistant, produced, model = provider.model)
            store.updateMessageUsage(aid, promptTotal, completionTotal, provider.model)
            Result.success(Reply(produced, target))
            }
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun runWithTools(
        conversationId: Long,
        history: List<ChatMessage>,
        prompt: String,
        provider: Provider,
        apiKey: String,
        thinking: Boolean = false,
    ): Result<Reply> {
        val runner = agentRunner ?: return Result.failure(IllegalStateException("Agent 未装配"))

        val messages = history + ChatMessage(role = "user", text = prompt)

        val result = runner.run(
            conversationId = conversationId,
            history = messages,
            provider = provider,
            apiKey = apiKey,
            thinking = thinking,
            // QQ 侧一律不下发推理强度：多一步「想得更久」在 QQ 场景只会更慢，
            // 而且不同服务商对 effort 的支持程度不一致，传了反而容易 400。
            reasoningEffort = null,
        )

        return result.fold(
            onSuccess = { reply ->
                // 工具路径同样计量：AgentOutcome 自带本轮累计 prompt/completion token，
                // 不写进 messages 表的话「用量」页会漏掉全部 QQ 工具会话。
                val aid = store.appendMessage(
                    conversationId = conversationId,
                    role = MessageRole.Assistant,
                    text = reply.text,
                    model = provider.model,
                )
                // 引擎的 usage 也可能为 0（部分端点在流式块里不回 usage），同样走估算兜底，
                // 与闲聊路径保持一致 —— 否则同一台设备上两条路径的统计口径会打架。
                val promptTotal = if (reply.promptTokens > 0) {
                    reply.promptTokens
                } else {
                    TokenEstimator.estimateMessages(messages)
                }
                val completionTotal = if (reply.completionTokens > 0) {
                    reply.completionTokens
                } else {
                    TokenEstimator.estimate(reply.text)
                }
                store.updateMessageUsage(aid, promptTotal, completionTotal, provider.model)
                Result.success(Reply(reply.text, conversationId))
            },
            onFailure = { error ->
                // 细节留在应用内可查（logcat），QQ 对面只看到一句人话：
                // 把 HTTP 状态码、上游报错原文发给第三方没有任何好处。
                // 不落库：这不是模型说的话，存进去下一轮会被自己当成上下文。
                Log.w(Tag, "QQ 工具调用失败（未落库）：${error.message}", error)
                Result.success(Reply("（这个我暂时查不了，回 App 里试试？）", conversationId))
            },
        )
    }

    /**
     * 摘要注入：把 App 已经压缩好的更早对话拼成一条 system。
     *
     * 只读不写 —— QQ 侧绝不触发压缩（那要花一次模型调用）。
     * 摘要为空时返回空列表，不塞空 system（有些上游对空 system 直接 400）。
     */
    private fun summaryBlock(summary: String?): List<ChatMessage> {
        val body = summary?.trim().orEmpty()
        if (body.isEmpty()) return emptyList()
        return listOf(
            ChatMessage(
                role = "system",
                text = "以下是本次对话更早内容的压缩摘要（原文已省略）：\n$body",
            ),
        )
    }

    /**
     * App 正在这条会话里流式输出时，等它说完（最多 [maxMs]）。
     *
     * **不参与 [mutex]**：共用锁会让 QQ 阻塞到流式结束（最长 120 秒），
     * 把群聊 5 分钟的被动回复窗口耗光。这里只是「读历史前要不要等一下」的礼貌询问。
     *
     * @return true = 可以读了；false = 等超时，调用方应放弃这条消息（不调模型、不落库）
     */
    private suspend fun awaitNotStreaming(conversationId: Long, maxMs: Long = STREAM_WAIT_MS): Boolean {
        if (!ChatStreamGuard.isStreaming(conversationId)) return true
        val deadline = System.currentTimeMillis() + maxMs
        while (System.currentTimeMillis() < deadline) {
            delay(STREAM_POLL_MS)
            if (!ChatStreamGuard.isStreaming(conversationId)) return true
        }
        Log.w(Tag, "App 侧流式未在 ${maxMs}ms 内结束，本条未处理")
        return false
    }

    /** 来源标签：`QQ 私聊 · 张三` / `QQ 群聊 · 张三`。名字为空时用「对方」。 */    private fun senderLabel(senderName: String, isGroup: Boolean): String {
        val where = if (isGroup) "群聊" else "私聊"
        val who = senderName.ifBlank { "对方" }
        return "QQ $where · $who"
    }

    /**
     * 把 QQ 消息包装成一条提示，带上发言人。
     *
     * 不直接裸传正文：群聊里多人对话时，模型需要知道「谁在说」，
     * 否则会把别人的问题当成自己的上下文继续回答。
     */
    private fun buildPrompt(text: String, senderName: String, isGroup: Boolean): String =
        "[${senderLabel(senderName, isGroup)}] ${text.take(2_000)}"

    /**
     * 决定这一轮写进哪个会话。
     *
     * 顺序：用户选定的会话 → 之前记住的自动会话 → 新建「QQ 消息」。
     * 存在性用 [ChatStore.conversationExists] 判断，**不能**看"有没有消息"：
     * 绑定一个刚建的空会话会被误判成已删除，绑定就静默失效了。
     */
    /**
     * 决定这一轮写进哪个会话。
     *
     * 顺序：用户选定的会话 → 之前记住的自动会话 → 新建「QQ 消息」。
     * 存在性用 [ChatStore.conversationExists] 判断，**不能**看"有没有消息"：
     * 绑定一个刚建的空会话会被误判成已删除，绑定就静默失效了。
     *
     * 自动会话 id **以 [QqBot.autoConversationId] 为唯一事实源**：以前这里自己读一份独立的
     * 偏好存储，UI 看不到，于是设置页只能写死"第一次收到消息时新建"。
     * 两处各存一份也迟早会不一致，所以统一收到 QqBot 上。
     */
    private suspend fun ensureConversation(preferred: Long): Long {
        if (preferred > 0L && store.conversationExists(preferred)) return preferred
        val auto = QqBot.autoConversationId.value
        if (auto > 0L && store.conversationExists(auto)) return auto
        val created = store.createConversation("QQ 消息")
        QqBot.setAutoConversationId(created)
        return created
    }

    private companion object {
        const val Tag = "Velune-QQ"

        /**
         * 排队等待上限。
         *
         * 45 秒的账：连发 3 条、每条约 10 秒，第 3 条最多等 20 秒；即使工具模式下每条 30 秒，
         * 45 秒等待 + 30 秒处理仍远小于群聊 5 分钟的被动回复窗口。
         */
        const val QUEUE_TIMEOUT_MS = 45_000L

        /** 历史窗口下限：摘要覆盖率很低时也至少带 20 条原文，别让上下文太薄。 */
        const val MIN_HISTORY = 20

        /** 历史窗口上限：摘要之后原则上全带，但 60 条以外只是白花钱。 */
        const val MAX_HISTORY = 60

        /** 等 App 流式结束的上限。QQ 的用户在等，超过这个时长不如直接告知。 */
        const val STREAM_WAIT_MS = 5_000L

        /** 轮询间隔：250ms 足够及时，又不至于把线程占满。 */
        const val STREAM_POLL_MS = 250L
    }
}
