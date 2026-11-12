package com.veluneagent.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 「哪个会话正在流式输出」的进程内登记表。
 *
 * 为什么需要：App 与 QQ 共用会话时，App 正在流式的半成品会以「每 200ms 覆盖占位行」的形式落库，
 * 于是 QQ 读 history 时会把半句话当成一句完整的 assistant 发言。
 * `AssistantPlaceholder` 哨兵不够用 —— 占位行在流式开始约 200ms 后就被真实正文覆盖。
 *
 * 为什么用内存不落库：流式是进程活着才有的状态；QQ 服务与 Activity 同进程，内存信号两侧可见。
 * ⚠️ 若将来给 `QqBridgeService` 加 `android:process`，本方案失效，必须改为落库标记。
 *
 * 它不是锁：只回答「现在能不能读这个会话的历史」。
 * **绝不可**替代 `QqResponder` 的 `Mutex` —— 那会让 QQ 阻塞到 App 流式结束（最长 120 秒），
 * 把群聊 5 分钟的被动回复窗口耗光。
 */
object ChatStreamGuard {

    private val _activeIds = MutableStateFlow<Set<Long>>(emptySet())

    /** 供 UI / 调试观察；业务判断请用 [isStreaming]，避免读到中间态快照。 */
    val activeIds: StateFlow<Set<Long>> = _activeIds.asStateFlow()

    /** 开始流式。必须与 [end] 成对，且 [end] 要放在 `finally` 里，取消/异常都要覆盖。 */
    fun begin(conversationId: Long) {
        if (conversationId <= 0L) return
        _activeIds.update { it + conversationId }
    }

    /** 结束流式。重复调用无副作用。 */
    fun end(conversationId: Long) {
        if (conversationId <= 0L) return
        _activeIds.update { it - conversationId }
    }

    fun isStreaming(conversationId: Long): Boolean = conversationId in _activeIds.value
}
