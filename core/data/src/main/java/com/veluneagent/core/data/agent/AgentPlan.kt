package com.veluneagent.core.data.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class PlanStatus { Pending, Running, Done }

data class PlanStep(val title: String, val status: PlanStatus)

/**
 * 当前这一轮的执行计划（多步任务的可见清单）。
 *
 * 为什么用全局单例而不是塞进消息：计划是**过程状态**，不是对话内容 ——
 * 它随本轮开始清空、随轮次结束失效，不该被持久化，也不该出现在下一轮的历史里。
 * 用 StateFlow 让界面直接观察，避免为了一个 3 行的清单去改数据库。
 *
 * 只保留最近一次 `update_plan` 的结果：模型每步都会重写整份清单，
 * 我们不需要历史版本 —— 用户要看的是"现在到哪一步了"。
 */
object AgentPlan {

    const val MaxSteps = 10

    private val _steps = MutableStateFlow<List<PlanStep>>(emptyList())
    val steps: StateFlow<List<PlanStep>> = _steps

    fun update(steps: List<PlanStep>) {
        _steps.value = steps.take(MaxSteps)
    }

    fun clear() {
        _steps.value = emptyList()
    }

    /** 已完成/总数，用于「2/5」这类摘要。 */
    fun progress(steps: List<PlanStep> = _steps.value): Pair<Int, Int> =
        steps.count { it.status == PlanStatus.Done } to steps.size
}
