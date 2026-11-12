package com.veluneagent.core.data.agent

import com.veluneagent.core.data.ChatMessage
import com.veluneagent.core.data.StreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 引擎的单测基线。
 *
 * 覆盖的不是「happy path 能跑」，而是四种最容易出生产事故的路径：
 * 预算触顶、重复调用打转、用户拒绝、连续失败。
 */
class AgentEngineTest {

    // ---------- 测试替身 ----------

    private class FakeGateway(private val script: List<List<StreamEvent>>) : ModelGateway {
        var calls = 0
            private set

        override fun stream(messages: List<ChatMessage>): Flow<StreamEvent> = flow {
            val events = script.getOrNull(calls) ?: listOf(StreamEvent.Done)
            calls++
            events.forEach { emit(it) }
        }
    }

    private class FakeCatalog(private val tools: List<ToolSpec>) : ToolProvider {
        override fun specs(): List<ToolSpec> = tools
        override fun find(name: String): ToolSpec? = tools.firstOrNull { it.name == name }
        override fun requiresApproval(spec: ToolSpec): Boolean = spec.source == ToolSource.Plugin
        override fun scopesOf(spec: ToolSpec): List<String> = emptyList()
    }

    private class FakeRunner(
        private val result: (ToolCall) -> ToolResult = { call ->
            ToolResult(call.id, call.name, true, """{"ok":true}""", "ok", 1)
        },
    ) : ToolRunner {
        var executed = 0
            private set
        val executedNames = mutableListOf<String>()

        override suspend fun execute(spec: ToolSpec, call: ToolCall, runId: String?): ToolResult {
            executed++
            executedNames += call.name
            return result(call)
        }
    }

    private class FakeApprover(
        private val decision: ApprovalDecision,
        /** 模拟用户在面板前停留的时间（推进假时钟）。 */
        private val duringApprove: () -> Unit = {},
    ) : AgentApprover {
        var asked = 0
            private set

        override suspend fun approve(request: ApprovalRequest): ApprovalDecision {
            asked++
            duringApprove()
            return decision
        }
    }

    private val calcSpec = ToolSpec(
        name = "calc",
        displayName = "四则运算",
        description = "算数",
        parametersJson = """{"type":"object","properties":{"expression":{"type":"string"}},"required":["expression"]}""",
        sideEffect = false,
    )

    private val pluginSpec = ToolSpec(
        name = "com_example__do",
        displayName = "插件工具",
        description = "外部调用",
        parametersJson = """{"type":"object","properties":{}}""",
        sideEffect = true,
        source = ToolSource.Plugin,
        pluginId = "com.example",
        origin = "示例插件",
    )

    private fun engine(
        gateway: ModelGateway,
        catalog: ToolProvider,
        runner: ToolRunner,
        approver: AgentApprover = FakeApprover(ApprovalDecision.AllowOnce),
        budget: AgentBudget = AgentBudget(),
        now: () -> Long = System::currentTimeMillis,
    ) = AgentEngine(
        gateway = gateway,
        catalog = catalog,
        executor = runner,
        approver = approver,
        recorder = null,
        budget = budget,
        now = now,
    )

    private fun toolCall(id: String, name: String, args: String) =
        StreamEvent.ToolCalls(listOf(ToolCall(id, name, args)))

    private fun finished(events: List<AgentEvent>): AgentOutcome =
        events.filterIsInstance<AgentEvent.Finished>().single().outcome

    // ---------- 用例 ----------

    @Test
    fun `一次工具调用后给出最终答案`() = runBlocking {
        val gateway = FakeGateway(
            listOf(
                listOf(StreamEvent.Delta("我算一下"), toolCall("c1", "calc", """{"expression":"1+1"}""")),
                listOf(StreamEvent.Delta("答案是 2")),
            ),
        )
        val runner = FakeRunner()
        val events = engine(gateway, FakeCatalog(listOf(calcSpec)), runner)
            .run(1L, listOf(ChatMessage("user", "1+1 等于几")))
            .toList()

        val outcome = finished(events)
        assertEquals(StopReason.Completed, outcome.reason)
        assertEquals(2, outcome.steps)
        assertEquals(1, outcome.toolCalls)
        assertEquals("答案是 2", outcome.answer)
        assertEquals(listOf("calc"), runner.executedNames)
    }

    @Test
    fun `用户拒绝后工具不执行，模型换路子回答`() = runBlocking {
        val gateway = FakeGateway(
            listOf(
                listOf(toolCall("c1", "com_example__do", "{}")),
                listOf(StreamEvent.Delta("那我不查了，先按经验回答")),
            ),
        )
        val runner = FakeRunner()
        val events = engine(
            gateway = gateway,
            catalog = FakeCatalog(listOf(pluginSpec)),
            runner = runner,
            approver = FakeApprover(ApprovalDecision.Deny),
        ).run(1L, listOf(ChatMessage("user", "帮我查一下"))).toList()

        val outcome = finished(events)
        assertEquals(0, runner.executed)
        assertEquals(StopReason.Completed, outcome.reason)
        assertTrue(outcome.answer.contains("按经验回答"))
        assertTrue(events.filterIsInstance<AgentEvent.ToolFinished>().single().result.errorCode == "user_denied")
    }

    @Test
    fun `步数预算触顶即停并保留已完成内容`() = runBlocking {
        val gateway = FakeGateway(
            listOf(
                listOf(StreamEvent.Delta("先算一下"), toolCall("c1", "calc", """{"expression":"1+1"}""")),
                listOf(toolCall("c2", "calc", """{"expression":"2+2"}""")),
                listOf(StreamEvent.Delta("不该走到这里")),
            ),
        )
        val events = engine(
            gateway = gateway,
            catalog = FakeCatalog(listOf(calcSpec)),
            runner = FakeRunner(),
            budget = AgentBudget(maxSteps = 2),
        ).run(1L, listOf(ChatMessage("user", "算两次"))).toList()

        val outcome = finished(events)
        assertEquals(StopReason.BudgetExceeded, outcome.reason)
        assertEquals(2, outcome.steps)
        assertTrue(outcome.message!!.contains("最大步骤数"))
    }

    @Test
    fun `同名同参重复调用判定为循环`() = runBlocking {
        val sameCall = toolCall("c1", "calc", """{"expression":"1+1"}""")
        val gateway = FakeGateway(
            listOf(
                listOf(sameCall),
                listOf(toolCall("c2", "calc", """{"expression":"1+1"}""")),
                listOf(StreamEvent.Delta("不该走到这里")),
            ),
        )
        val runner = FakeRunner()
        val events = engine(gateway, FakeCatalog(listOf(calcSpec)), runner)
            .run(1L, listOf(ChatMessage("user", "循环测试")))
            .toList()

        assertEquals(StopReason.LoopDetected, finished(events).reason)
        assertEquals(2, runner.executed)
    }

    @Test
    fun `连续工具失败三次后终止`() = runBlocking {
        val failing = FakeRunner { call ->
            ToolResult.failure(call.id, call.name, "接口挂了", "http_500", 1)
        }
        val gateway = FakeGateway(
            listOf(
                listOf(toolCall("c1", "calc", """{"expression":"1"}""")),
                listOf(toolCall("c2", "calc", """{"expression":"2"}""")),
                listOf(toolCall("c3", "calc", """{"expression":"3"}""")),
                listOf(StreamEvent.Delta("不该走到这里")),
            ),
        )
        val events = engine(
            gateway = gateway,
            catalog = FakeCatalog(listOf(calcSpec)),
            runner = failing,
            budget = AgentBudget(maxConsecutiveFailures = 3),
        ).run(1L, listOf(ChatMessage("user", "一直失败"))).toList()

        assertEquals(StopReason.TooManyFailures, finished(events).reason)
        assertEquals(3, failing.executed)
    }

    @Test
    fun `本会话允许后不再重复弹审批`() = runBlocking {
        val approver = FakeApprover(ApprovalDecision.AllowForSession)
        val gateway = FakeGateway(
            listOf(
                listOf(toolCall("c1", "com_example__do", """{"q":"a"}""")),
                listOf(toolCall("c2", "com_example__do", """{"q":"b"}""")),
                listOf(StreamEvent.Delta("两次都查完了")),
            ),
        )
        val events = engine(
            gateway = gateway,
            catalog = FakeCatalog(listOf(pluginSpec)),
            runner = FakeRunner(),
            approver = approver,
        ).run(1L, listOf(ChatMessage("user", "查两次"))).toList()

        assertEquals(1, approver.asked)
        assertEquals(StopReason.Completed, finished(events).reason)
        assertEquals(2, events.filterIsInstance<AgentEvent.ToolFinished>().size)
    }

    @Test
    fun `未注册的工具被拒绝且不崩溃`() = runBlocking {
        val gateway = FakeGateway(
            listOf(
                listOf(toolCall("c1", "rm_rf", "{}")),
                listOf(StreamEvent.Delta("这个工具不存在")),
            ),
        )
        val runner = FakeRunner()
        val events = engine(gateway, FakeCatalog(listOf(calcSpec)), runner)
            .run(1L, listOf(ChatMessage("user", "删库")))
            .toList()

        assertEquals(0, runner.executed)
        assertEquals("unknown_tool", events.filterIsInstance<AgentEvent.ToolFinished>().single().result.errorCode)
        assertEquals(StopReason.Completed, finished(events).reason)
    }

    @Test
    fun `模型报错时连续失败计数生效`() = runBlocking {
        val gateway = FakeGateway(
            listOf(
                listOf(StreamEvent.Failed("HTTP 500")),
                listOf(StreamEvent.Failed("HTTP 500")),
                listOf(StreamEvent.Failed("HTTP 500")),
            ),
        )
        val events = engine(
            gateway = gateway,
            catalog = FakeCatalog(emptyList()),
            runner = FakeRunner(),
            budget = AgentBudget(maxConsecutiveFailures = 3, maxSteps = 10),
        ).run(1L, listOf(ChatMessage("user", "hi"))).toList()

        assertEquals(StopReason.TooManyFailures, finished(events).reason)
    }

    @Test
    fun `默认预算不限制时长，跑得再久也不被掐断`() = runBlocking {
        // 每次读表推进 10 分钟：旧实现（墙钟 120s）会在第二步开头就判超时
        var clock = 0L
        val gateway = FakeGateway(
            listOf(
                listOf(toolCall("c1", "calc", """{"expression":"1"}""")),
                listOf(toolCall("c2", "calc", """{"expression":"2"}""")),
                listOf(StreamEvent.Delta("两次都算完了")),
            ),
        )
        val events = engine(
            gateway = gateway,
            catalog = FakeCatalog(listOf(calcSpec)),
            runner = FakeRunner(),
            budget = AgentBudget(), // 默认预算：maxWallClockMs = 0
            now = { clock.also { clock += 600_000 } },
        ).run(1L, listOf(ChatMessage("user", "慢慢算"))).toList()

        val outcome = finished(events)
        assertEquals(StopReason.Completed, outcome.reason)
        assertEquals(3, outcome.steps)
        assertEquals("两次都算完了", outcome.answer)
    }

    @Test
    fun `审批停留的时间不计入墙钟预算`() = runBlocking {
        var clock = 0L
        val gateway = FakeGateway(
            listOf(
                listOf(toolCall("c1", "com_example__do", "{}")),
                listOf(StreamEvent.Delta("查到了")),
            ),
        )
        val events = engine(
            gateway = gateway,
            catalog = FakeCatalog(listOf(pluginSpec)),
            runner = FakeRunner(),
            // 只给 60s 额度，但用户在审批面板上停留了 120s
            approver = FakeApprover(ApprovalDecision.AllowOnce) { clock += 120_000 },
            budget = AgentBudget(maxWallClockMs = 60_000),
            now = { clock },
        ).run(1L, listOf(ChatMessage("user", "查一下"))).toList()

        val outcome = finished(events)
        assertEquals(StopReason.Completed, outcome.reason)
        assertEquals(1, outcome.toolCalls)
    }

    @Test
    fun `maxSteps 为 0 表示不限步数`() = runBlocking {
        val script = (1..6).map { i ->
            listOf(toolCall("c$i", "calc", """{"expression":"$i+1"}"""))
        } + listOf(listOf(StreamEvent.Delta("六步走完")))
        val events = engine(
            gateway = FakeGateway(script),
            catalog = FakeCatalog(listOf(calcSpec)),
            runner = FakeRunner(),
            budget = AgentBudget(maxSteps = 0),
        ).run(1L, listOf(ChatMessage("user", "多步任务"))).toList()

        val outcome = finished(events)
        assertEquals(StopReason.Completed, outcome.reason)
        assertEquals(7, outcome.steps)
        assertEquals(6, outcome.toolCalls)
    }
}
