package com.veluneagent.feature.chat.ui

import com.veluneagent.core.data.agent.AgentApprover
import com.veluneagent.core.data.agent.AgentRunStore
import com.veluneagent.core.data.agent.ApprovalDecision
import com.veluneagent.core.data.agent.ApprovalRequest
import com.veluneagent.core.data.agent.ToolCatalog
import com.veluneagent.core.data.agent.ToolExecutor
import com.veluneagent.core.data.plugin.PluginStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicReference

/** 对话页需要的 Agent 依赖，由 Application 装配后传下来。 */
data class AgentDeps(
    val catalog: ToolCatalog,
    val executor: ToolExecutor,
    val runStore: AgentRunStore,
    val pluginStore: PluginStore,
    /** 沙箱：文件预览/分享需要读真实文件 */
    val sandbox: com.veluneagent.core.data.sandbox.Sandbox? = null,
)

/**
 * 流式过程中的一段。
 *
 * 为什么需要它：Agent 一轮里可能是「说一句 → 调工具 → 再说一句 → 再调工具 → 总结」。
 * 如果只把正文和工具分开存两个列表，UI 就只能在气泡里显示文字、在所有文字之后
 * 把工具卡堆成一坨，读起来像「先说完再翻旧账」。这里按发生顺序落成有序片段，
 * 渲染时就是「边写边调」的真实过程。
 */
internal sealed interface StreamPart {
    /** 稳定 key：工具卡用它记住展开状态。 */
    val key: String

    data class Text(override val key: String, val text: String) : StreamPart

    data class Tool(override val key: String, val step: ToolStepUi) : StreamPart
}

/**
 * 审批门：把「引擎要等用户点一下」变成可挂起的状态。
 *
 * 用 CompletableDeferred 而不是轮询标志位：引擎在 await 上挂起，不占线程、不烧电；
 * 用户点完立即恢复。取消（用户点停止）时 await 抛 CancellationException，
 * finally 负责清空弹窗状态，避免下次进来看到上一轮的残留面板。
 */
class ApprovalGate : AgentApprover {

    private val _pending = MutableStateFlow<ApprovalRequest?>(null)
    val pending: StateFlow<ApprovalRequest?> = _pending

    private val waiting = AtomicReference<CompletableDeferred<ApprovalDecision>?>(null)

    override suspend fun approve(request: ApprovalRequest): ApprovalDecision {
        val deferred = CompletableDeferred<ApprovalDecision>()
        waiting.set(deferred)
        _pending.value = request
        return try {
            deferred.await()
        } finally {
            waiting.set(null)
            _pending.value = null
        }
    }

    fun decide(decision: ApprovalDecision) {
        waiting.get()?.complete(decision)
    }

    /** 取消运行或离开页面时调用，避免引擎永远挂在 await 上。 */
    fun cancelPending() {
        waiting.get()?.cancel()
        waiting.set(null)
        _pending.value = null
    }
}

enum class ToolStepStatus { Running, Ok, Failed, Denied }

/** 工具卡片的 UI 状态。与 ToolResult 解耦：UI 只需要展示所需的字段。 */
data class ToolStepUi(
    val callId: String,
    val name: String,
    val displayName: String,
    val origin: String,
    val argsPreview: String,
    val status: ToolStepStatus,
    val detail: String? = null,
    val durationMs: Long = 0,
    /** 展开详情用：完整参数与工具实际返回 */
    val argsFull: String = "",
    val resultFull: String? = null,
    /** fs_write 产出的沙箱相对路径，用于预览/分享 */
    val filePath: String? = null,
)
