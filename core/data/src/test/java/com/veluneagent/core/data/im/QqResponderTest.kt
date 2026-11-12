package com.veluneagent.core.data.im

import android.content.Context
import com.veluneagent.core.data.ChatMessage
import com.veluneagent.core.data.ChatStore
import com.veluneagent.core.data.MessageRole
import com.veluneagent.core.data.Provider
import com.veluneagent.core.data.ProviderStore
import com.veluneagent.core.data.StreamEvent
import com.veluneagent.core.data.agent.ModelGateway
import com.veluneagent.core.data.context.ConversationSummary
import com.veluneagent.core.data.context.SummaryRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/**
 * `QqResponder.handle()` 的回归测试。
 *
 * ## 这条链路之前为什么没有测试
 *
 * `handle()` 是 QQ 侧唯一的主链路，但它依赖 `Context`（SharedPreferences / PromptStore）、
 * `ChatStore`（SQLite）、`ProviderStore`（静态单例）、以及发起真实 HTTP 的 `ChatClient`。
 * 既有的 28 个 QQ 用例全部停留在「纯函数解析」层（事件解析、白名单），**一行都没碰过 handle**，
 * 所以 19:09 那批修复（计量、排队、失败话术）落地时没有任何护栏。
 *
 * ## 怎么绕开
 *
 * - `gatewayFactory`：构造缝，注入 [ModelGateway]，完全不发网络请求；
 * - `summaries`：[SummaryRepository] 是接口，注入内存实现，避免第二个 SQLite 依赖；
 * - `ChatStore`：**不造假实现**，用 Robolectric 的真 SQLite —— 只有真库才能断言
 *   `prompt_tokens` / `completion_tokens` 列真的被写了（这正是 Q18 的防线）；
 * - `ProviderStore` 走 `SecretBox` → `AndroidKeyStore`，Robolectric 没有该 Provider，
 *   由 [FakeAndroidKeyStore] 补一个（测试专用，不改源码）。
 *
 * ## 三个必须知道的 Robolectric 坑（不写下来，下一个人还会再撞一遍）
 *
 * **坑 1 · 类上没有 `@SQLiteMode(NATIVE)` 会炸在 Dispatchers.IO 上。**
 * Robolectric 的 legacy SQLite 把数据库连接**绑在开库的那个线程**上，而 `ChatStore`
 * 的每个方法都是 `withContext(Dispatchers.IO)`。于是跨线程立刻抛：
 * ```
 * IllegalStateException: Illegal connection pointer 1.
 *   Current pointers for thread Thread[DefaultDispatcher-worker-2] []
 * ```
 * 这条症状看起来像产品 bug，其实是影子实现限制。解法就是类头那行
 * `@SQLiteMode(SQLiteMode.Mode.NATIVE)`。
 *
 * **坑 2 · 静态单例会让用例互相串库（这是测试隔离问题，不是产品 bug）。**
 * Robolectric 同一个类的多个用例共享 JVM，而有两处状态活得比 `Application` 还长：
 * - `VeluneDb.helper`（`ChatStore.kt:984` 的 `@Volatile private var`）——数据库句柄复用；
 * - `QqBot._autoConversationId`（`ensureConversation` 读的是它，**不读 prefs**）——
 *   上一条用例的会话 id 会漏到下一条。
 *
 * 两者叠加的典型症状：`single { it.role == Assistant }` 抛
 * `Collection contains more than one matching element`，或者"失败用例断言库里不该有
 * assistant 行，却查到了"。**判定依据**：单次 `handle()` 不可能产出两条 assistant，
 * 所以一定是上一条漏下来的 —— 看到这种红，先怀疑隔离，不要改断言。
 * 解法：`@Before` 里 `clearAllConversations()` + `QqBot.setAutoConversationId(0L)`。
 *
 * **坑 3 · 别用「文件名 ↔ 结果类名」核对测试数量。**
 * Kotlin 允许一个文件里声明多个顶层类（本项目里 `CalculatorTest` 在 `ToolArgsTest.kt`、
 * `ThinkingSupportTest` 在 `CalendarWindowTest.kt`）。按文件名核对会把它们误判成
 * "构建残留的幽灵用例"而漏算。正确口径：`grep "class XxxTest"` 数**类**。
 *
 * ## 每个用例都对应一个真实缺陷
 *
 * | 用例 | 守的是 | 删掉对应实现后会怎样 |
 * |---|---|---|
 * | 服务商回报 usage | Q1 计量 | 用量列恒 0，账单对不上 |
 * | **服务商不报 usage** | **Q18 估算兜底** | 非 DeepSeek 用户用量恒 0 |
 * | 上游失败 | Q3 话术 | 对面收到 `HTTP 401` 原文 |
 * | 连发排队 | Q2 排队语义 | 第二条被直接丢弃 |
 * | 摘要注入 | Q6 上下文接续 | App 压缩后 QQ 端失忆 |
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class QqResponderTest {

    private lateinit var ctx: Context
    private lateinit var store: ChatStore

    /** 内存摘要仓库：`summary` 非空即模拟「App 侧已经压缩过这段会话」。 */
    private class FakeSummaries(private val summary: String? = null) : SummaryRepository {
        override suspend fun get(conversationId: Long): ConversationSummary? =
            summary?.let { ConversationSummary(conversationId, it, 10, 0L) }

        override suspend fun save(conversationId: Long, summary: String, coveredCount: Int) = Unit

        override suspend fun clear(conversationId: Long) = Unit
    }

    /** 固定返回一串事件的网关；`onStream` 可挂起，用来制造「上一轮还没答完」。 */
    private class FakeGateway(
        private val events: List<StreamEvent>,
        private val onStream: (suspend () -> Unit)? = null,
    ) : ModelGateway {
        val started = CompletableDeferred<Unit>()

        override fun stream(messages: List<ChatMessage>): Flow<StreamEvent> = flow {
            started.complete(Unit)
            onStream?.invoke()
            events.forEach { emit(it) }
        }
    }

    @Before
    fun setUp() {
        FakeAndroidKeyStore.install()
        FakeAndroidKeyStore.reset()
        ctx = RuntimeEnvironment.getApplication()
        store = ChatStore(ctx)

        // `VeluneDb` 是进程级单例（ChatStore.kt:984 `@Volatile private var helper`），
        // Robolectric 同一个测试类的多个用例共享同一个 JVM，所以上一条用例写进去的
        // 消息会漏到下一条 —— 表现为「单条会话里出现两条 assistant」。
        // 这里显式清库，保证每条用例都从空库开始。
        runBlocking { store.clearAllConversations() }
        // 「自动会话 id」已经提到 QqBot 的 JVM 静态 StateFlow（ensureConversation 读的是
        // `QqBot.autoConversationId.value`，不再读 prefs），所以清 SharedPreferences
        // 清不掉它 —— 静态值比 Application 活得久，会把上一条用例的会话漏到这一条。
        // 结果就是「复用同一条会话 → 出现两条 assistant」这类与顺序相关的假红。
        QqBot.setAutoConversationId(0L)
        ctx.getSharedPreferences("velune_qqbot_session", Context.MODE_PRIVATE)
            .edit().clear().apply()

        ProviderStore.init(ctx)
        val provider = ProviderStore.save(
            id = "test-provider",
            name = "测试服务商",
            baseUrl = "http://localhost",
            model = "test-model",
            apiKey = "sk-test-secret-123",
        )
        ProviderStore.setActive(provider.id)
    }

    @After
    fun tearDown() {
        FakeAndroidKeyStore.reset()
    }

    private fun responder(
        events: List<StreamEvent>,
        summaries: String? = null,
        onStream: (suspend () -> Unit)? = null,
    ): Pair<QqResponder, FakeGateway> {
        val gateway = FakeGateway(events, onStream)
        val r = QqResponder(
            context = ctx,
            store = store,
            gatewayFactory = { _: Provider, _: String, _: Boolean -> gateway },
            summaries = FakeSummaries(summaries),
        )
        return r to gateway
    }

    // ---------------------------------------------------------------- Q1

    @Test
    fun `服务商回报 usage 时 用量列写入服务端的真实值`() = runBlocking {
        val (responder, _) = responder(
            listOf(
                StreamEvent.Delta("你好"),
                StreamEvent.Usage(promptTokens = 120, completionTokens = 18),
                StreamEvent.Done,
            ),
        )

        val reply = responder.handle("你好", "张三", isGroup = false).getOrThrow()
        val assistant = store.messages(reply.conversationId, 50)
            .single { it.role == MessageRole.Assistant }

        assertEquals("服务端回报的 prompt 应原样落库", 120, assistant.promptTokens)
        assertEquals("服务端回报的 completion 应原样落库", 18, assistant.completionTokens)
    }

    // ---------------------------------------------------------------- Q18

    /**
     * **这是 Q18 唯一的防线**。
     *
     * OpenAI 兼容端点在 `stream = true` 时默认**不在流式块里返回 usage**
     * （`ChatClient` 也从未发送 `stream_options.include_usage`），所以绝大多数
     * 第三方/自建服务商都不会有 [StreamEvent.Usage]。没有估算兜底时这一格恒为 0，
     * 用户看到的是「明明聊了一整天，用量页今天 0 token」。
     */
    @Test
    fun `服务商不报 usage 时 用量列仍须大于 0 走本地估算兜底`() = runBlocking {
        val (responder, _) = responder(
            listOf(StreamEvent.Delta("你好呀，今天过得怎么样"), StreamEvent.Done),
        )

        val reply = responder.handle("你好", "张三", isGroup = false).getOrThrow()
        val assistant = store.messages(reply.conversationId, 50)
            .single { it.role == MessageRole.Assistant }

        assertTrue(
            "没有 usage 事件时必须走 TokenEstimator 兜底，否则非 DeepSeek 用户的用量恒为 0",
            assistant.promptTokens > 0,
        )
        assertTrue(
            "completion 同理，不能因为服务商不报就留 0",
            assistant.completionTokens > 0,
        )
    }

    // ---------------------------------------------------------------- Q3

    @Test
    fun `上游失败时不落 assistant 行 且不把内部错误原文发给对面`() = runBlocking {
        val (responder, _) = responder(
            listOf(StreamEvent.Failed("HTTP 401 Unauthorized: invalid api key sk-test-secret-123")),
        )

        val reply = responder.handle("你好", "张三", isGroup = false).getOrThrow()
        val messages = store.messages(reply.conversationId, 50)

        assertTrue(
            "失败不是模型说的话，不能落库，否则下一轮会被当成 AI 自己的发言污染上下文",
            messages.none { it.role == MessageRole.Assistant },
        )
        assertTrue("对面必须收到一句人话，不能空回复", reply.text.isNotBlank())
        assertFalse("HTTP 状态码是内部信息：${reply.text}", reply.text.contains("HTTP"))
        assertFalse("状态码是内部信息：${reply.text}", reply.text.contains("401"))
        assertFalse("API Key 绝不能外泄：${reply.text}", reply.text.contains("sk-"))
    }

    // ---------------------------------------------------------------- Q2

    /**
     * 连发的第二条必须**排队等待**，而不是立刻回一句「我还在看上一条」。
     *
     * 注意这里不断言 45 秒超时后那条忙提示的文案：`QUEUE_TIMEOUT_MS` 是私有常量，
     * 从外部驱动需要真等 45 秒。若要把「超时后的诚实话术」也纳入回归，
     * 需要把它提成 internal 构造参数（纯结构改动），否则该分支仍无测试。
     */
    @Test
    fun `上一轮未答完时 第二条排队等待而不是被直接丢弃`() = runBlocking {
        val hold = CompletableDeferred<Unit>()
        val (responder, gateway) = responder(
            events = listOf(StreamEvent.Delta("第一条的答复"), StreamEvent.Done),
            onStream = { hold.await() },
        )

        val first = async(Dispatchers.Default) {
            responder.handle("第一条", "张三", isGroup = false)
        }
        gateway.started.await()

        // 第二条此刻进入：若实现是 tryLock 直接丢弃，这里会立刻拿到一个 busy 回复。
        val secondImmediately = withTimeoutOrNull(1_500) {
            responder.handle("第二条", "张三", isGroup = false)
        }
        assertNull(
            "第二条应当阻塞在锁上排队，而不是立刻返回；立刻返回说明又退回了「丢弃」语义",
            secondImmediately,
        )

        hold.complete(Unit)
        val firstReply = first.await().getOrThrow()
        assertEquals("第一条的答复", firstReply.text)

        // 第一条让出锁之后，第三条必须能被正常处理（锁没有被泄漏）。
        val third = withTimeoutOrNull(10_000) {
            responder.handle("第三条", "张三", isGroup = false)
        }
        assertNotNull("排队释放后必须还能继续处理后续消息", third)
        assertTrue("第三条不应再拿到忙提示", third!!.getOrThrow().text.contains("第一条的答复"))
    }

    // ---------------------------------------------------------------- Q6

    @Test
    fun `会话有压缩摘要时 摘要要进入发给模型的历史`() = runBlocking {
        val captured = mutableListOf<List<ChatMessage>>()
        val gateway = object : ModelGateway {
            override fun stream(messages: List<ChatMessage>): Flow<StreamEvent> {
                captured += messages
                return flowOf(StreamEvent.Delta("好的"), StreamEvent.Done)
            }
        }
        val responder = QqResponder(
            context = ctx,
            store = store,
            gatewayFactory = { _: Provider, _: String, _: Boolean -> gateway },
            summaries = FakeSummaries("用户之前在 App 里聊过：他在做 iOS 客户端迁移"),
        )

        val reply = responder.handle("刚才聊到哪了", "张三", isGroup = false).getOrThrow()

        val sent = captured.single()
        assertTrue(
            "App 侧压缩过的摘要必须注入 QQ 的 history，否则「回 QQ 接不上」",
            sent.any { it.role == "system" && it.text.contains("iOS 客户端迁移") },
        )
        assertEquals("好的", reply.text)
    }
}
