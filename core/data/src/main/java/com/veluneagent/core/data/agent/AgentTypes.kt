package com.veluneagent.core.data.agent

/**
 * 工具来源。内置工具随应用发布，插件工具由 Lite manifest 声明。
 * 这个区分决定了审批文案与信任级别：内置的只读工具可以设 Auto，插件一律 Ask。
 */
enum class ToolSource { Builtin, Plugin, Mcp }

/**
 * 工具声明。
 *
 * `parametersJson` 直接就是给模型看的 JSON Schema 字符串 —— 不做二次拼装，
 * 避免「schema 是 A、发给模型的是 B」这类只在真机上暴露的错位。
 */
data class ToolSpec(
    val name: String,
    val displayName: String,
    val description: String,
    val parametersJson: String,
    val sideEffect: Boolean = true,
    /**
     * 隐私敏感（只读但会读出个人信息，如短信、私有目录文件）。
     *
     * 与 [sideEffect] 是两个维度：侧效应描述"会不会改变世界"，敏感描述"会不会泄露世界"。
     * 自动批准的场景（例如 QQ 远程只读）必须两个都为 false 才放行，
     * 否则"只读"就成了绕过审批读短信的后门。
     */
    val sensitive: Boolean = false,
    /**
     * 会产生**真金白银**的调用（TTS、付费检索…）。
     *
     * 与 sideEffect/sensitive 是第三个维度：不改变世界、不泄露数据，但会烧钱。
     * 无人值守场景（QQ 远程）必须排除 —— 那类入口没有"用户点确认"这一步。
     */
    val costly: Boolean = false,
    val timeoutMs: Int = 5_000,
    val source: ToolSource = ToolSource.Builtin,
    /** 插件工具才有：所属插件 id，用于 scope 校验与来源展示。 */
    val pluginId: String? = null,
    /** 展示用来源，例如「内置」或插件名。 */
    val origin: String = "",
) {
    /** OpenAI tools 数组里的一项。 */
    fun toWireJson(): String = buildString {
        append("""{"type":"function","function":{""")
        append("\"name\":").append(quote(name)).append(',')
        append("\"description\":").append(quote(description)).append(',')
        append("\"parameters\":").append(parametersJson.ifBlank { "{\"type\":\"object\",\"properties\":{}}" })
        append("}}")
    }

    private fun quote(value: String): String = "\"" + value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n") + "\""
}

/** 模型发起的一次工具调用。arguments 保留模型原样输出，执行前再校验。 */
data class ToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

/**
 * 工具执行结果。
 * 约定与 05 章一致：内容必须是可 JSON 序列化的对象且包含 ok，失败不抛异常。
 */
data class ToolResult(
    val callId: String,
    val name: String,
    val ok: Boolean,
    /** 回灌给模型的文本（已按上下文预算裁剪）。 */
    val contentForModel: String,
    /** 给用户看的摘要，展示在工具卡上。 */
    val displayText: String,
    val durationMs: Long,
    val errorCode: String? = null,
    /**
     * 嵌套模型调用（`delegate_task` 的子模型）产生的用量。
     *
     * 必须回传给引擎计入本轮预算：子任务用的是另一份 Key、另一份额度，
     * 如果不计，用户在主账上完全看不到这部分花费，预算闸门也会漏算。
     */
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
) {
    companion object {
        fun failure(
            callId: String,
            name: String,
            message: String,
            code: String,
            durationMs: Long = 0,
        ) = ToolResult(
            callId = callId,
            name = name,
            ok = false,
            contentForModel = """{"ok":false,"error":${message.toJsonString()}}""",
            displayText = message,
            durationMs = durationMs,
            errorCode = code,
        )
    }
}

/**
 * 子模型一次调用的产出。
 *
 * 必须带上 token：子模型可能用的是另一家的 Key、另一份额度，
 * 用户对「这次任务到底花了多少」的唯一感知来自主 run 的统计。
 */
data class SubAgentOutcome(
    val text: String,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
)

/** 有序键值对形式的 JSON 字符串转义，避免为一行代码引入序列化器。 */internal fun String.toJsonString(): String = buildString {
    append('"')
    for (c in this@toJsonString) {
        when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
        }
    }
    append('"')
}

/**
 * 预算。
 *
 * **默认不设任何时间上限**：一轮 Agent 该跑多久由任务决定，不该由一个拍脑袋的秒数决定。
 * 旧的 120s 墙钟 + 60s 单步超时会在正常长任务上误杀——模型还在稳定吐字，却被判「超时」，
 * 用户只能看着一个跑到一半的答案。更糟的是审批挂起的时间也被算进墙钟：
 * 弹窗挂两分钟去倒杯水，回来点「同意」，下一步开头就直接触顶。
 *
 * 时间维度只保留一个守卫，语义是「多久没有任何新内容」，实现在协议层
 * （[com.veluneagent.core.data.ChatClient] 的读取空闲超时 + [OpenAiGateway] 透传）。
 * 只要模型还在吐正文 / 思考 / 工具增量，计时就重置；真的挂死了才判失败。
 *
 * 其余字段负责「不烧钱」：步数、工具次数、token、连续失败。
 * 数值 <= 0 表示不限（[maxConsecutiveFailures] 除外，熔断必须为正，否则无限重试）。
 */
data class AgentBudget(
    val maxSteps: Int = 30,
    val maxToolCalls: Int = 60,
    val maxTotalTokens: Int = 400_000,
    val maxConsecutiveFailures: Int = 3,
    /**
     * 整轮墙钟上限；**0 = 不限（默认）**。
     * 仅给有外部硬约束的入口留口子（QQ 被动回复窗口），App 内对话不该用。
     * 即使启用，审批挂起时长也会被扣除，不占用户额度。
     */
    val maxWallClockMs: Long = 0L,
    /**
     * 单步墙钟上限；**0 = 不限（默认）**。
     * 想防「服务端不吐字」请用协议层的空闲超时，而不是给整步掐表。
     */
    val stepTimeoutMs: Long = 0L,
)

enum class StopReason {
    /** 模型给出了最终回答。 */
    Completed,

    /** 达到步数 / 工具次数 / token / 墙钟预算。 */
    BudgetExceeded,

    /** 检测到同一工具 + 相同参数重复调用。 */
    LoopDetected,

    /** 连续失败超过阈值。 */
    TooManyFailures,

    /** 用户点击停止。 */
    UserCancelled,

    /** 无法恢复的错误（无可用模型、缺少密钥等）。 */
    Error,
}

data class AgentOutcome(
    val reason: StopReason,
    val answer: String,
    val steps: Int,
    val toolCalls: Int,
    val promptTokens: Int,
    val completionTokens: Int,
    val message: String? = null,
)

/** 引擎向 UI 吐出的事件流。UI 只依赖它，不依赖引擎内部状态。 */
sealed interface AgentEvent {
    /** 助手正文增量（含最终回答）。 */
    data class Delta(val text: String) : AgentEvent

    /** 思考过程增量。 */
    data class Reasoning(val text: String) : AgentEvent

    /** 开始时告知本轮预算，便于 UI 展示「第 N/M 步」；字段为 0 时 UI 应显示「不限」。 */
    data class Started(val maxSteps: Int, val maxToolCalls: Int) : AgentEvent

    data class StepStarted(val index: Int, val title: String) : AgentEvent

    data class ToolStarted(val call: ToolCall, val spec: ToolSpec?) : AgentEvent

    data class ToolFinished(val result: ToolResult) : AgentEvent

    /** 需要用户确认。gate 会在拿到决策后继续，UI 收到后弹面板。 */
    data class ApprovalRequired(val request: ApprovalRequest) : AgentEvent

    data class Finished(val outcome: AgentOutcome) : AgentEvent
}

/**
 * 审批请求。字段都是「给用户看的」：做什么、从哪来、要访问什么、参数长什么样。
 */
data class ApprovalRequest(
    val callId: String,
    val toolName: String,
    val displayName: String,
    val origin: String,
    val argumentsPreview: String,
    val scopes: List<String>,
    val sideEffect: Boolean,
)

enum class ApprovalDecision {
    AllowOnce,
    AllowForSession,
    Deny,
}

/** 审批门。UI 实现它（弹面板等用户点），测试实现它（直接返回固定决策）。 */
interface AgentApprover {
    suspend fun approve(request: ApprovalRequest): ApprovalDecision
}

/**
 * 工具来源抽象。
 *
 * 引擎只依赖这两个接口：测试里可以塞纯内存实现，不必为了造一个 PluginStore 去起 Android Context。
 */
interface ToolProvider {
    fun specs(): List<ToolSpec>
    fun find(name: String): ToolSpec?
    fun requiresApproval(spec: ToolSpec): Boolean
    fun scopesOf(spec: ToolSpec): List<String>
}

interface ToolRunner {
    suspend fun execute(spec: ToolSpec, call: ToolCall, runId: String? = null): ToolResult
}

/** scope 审计旁路。审计失败不能影响主流程，所以实现方自己吞异常。 */
interface ScopeAuditor {
    fun record(pluginId: String, scope: String, target: String, allowed: Boolean, runId: String?)
}

/** 模型网关。抽出来是为了让引擎能在没有网络的情况下被单测覆盖。 */
interface ModelGateway {
    /**
     * 发起一次流式调用。
     * @param messages 完整上下文（含此前轮次的 tool 消息）
     */
    fun stream(messages: List<com.veluneagent.core.data.ChatMessage>): kotlinx.coroutines.flow.Flow<com.veluneagent.core.data.StreamEvent>
}
