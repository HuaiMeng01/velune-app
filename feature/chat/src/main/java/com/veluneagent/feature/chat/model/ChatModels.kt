package com.veluneagent.feature.chat.model

import androidx.compose.ui.graphics.Color

data class ModelInfo(
    val id: String,
    val displayName: String,
    val provider: String,
    val accent: Color,
)

/**
 * 上下文预算。messageCount 为当前会话消息条数，compressAt 为「每次请求携带的最近条数」。
 * 环形进度 = messageCount / compressAt，剩余条数用于 tooltip 与无障碍描述。
 */
data class ContextBudget(
    val messageCount: Int,
    val compressAt: Int,
) {
    val remaining: Int get() = (compressAt - messageCount).coerceAtLeast(0)
    val progress: Float get() = (messageCount.toFloat() / compressAt.toFloat()).coerceIn(0f, 1f)
    val nearLimit: Boolean get() = progress >= 0.8f
}

/**
 * 模型展示信息。**不预置任何模型清单**：模型名由用户在服务商配置里自己填，
 * 这里只负责把「用户填了什么」显示到输入栏上。
 */
