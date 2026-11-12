package com.veluneagent.core.data.export

import com.veluneagent.core.data.MessageRole
import com.veluneagent.core.data.StoredMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 聊天记录导出为 Markdown。
 *
 * 为什么选 Markdown 而不是 JSON：这份文件的第一用途是"人看/存档"，
 * 不是"再导回来"。JSON 需要一套 schema 版本管理，而 Markdown 十年后还能直接读。
 * 需要重新导入时再单独做带版本号的格式，不把两件事混在一起。
 *
 * 设计取舍：
 * - 工具调用只留一行摘要（名字 + 成败 + 耗时），完整参数与返回不进导出——
 *   里面可能有路径、短信内容这类隐私，导出文件通常会被随手转发；
 * - 思考过程默认不导出：它有几千字，会把文件撑得没法读。
 */
object ChatExporter {

    data class Session(
        val title: String,
        val createdAt: Long,
        val messages: List<StoredMessage>,
    )

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun toMarkdown(sessions: List<Session>, exportedAt: Long = System.currentTimeMillis()): String {
        val totalMessages = sessions.sumOf { it.messages.size }
        return buildString {
            appendLine("# Velune 聊天记录")
            appendLine()
            appendLine("- 导出时间：${timeFormat.format(Date(exportedAt))}")
            appendLine("- 会话：${sessions.size} 个 · 消息：$totalMessages 条")
            appendLine()

            sessions.forEach { session ->
                appendLine("---")
                appendLine()
                appendLine("## ${session.title.ifBlank { "未命名会话" }}")
                appendLine()
                appendLine("> 创建于 ${timeFormat.format(Date(session.createdAt))}")
                appendLine()

                session.messages.forEach { message ->
                    val user = message.role == MessageRole.User
                    // 导出件里把「谁说的」写清楚：QQ 转发进来的消息不是「你」打的字，
                    // 混进同一个标签会让三个月后回看的自己以为是本人在说话
                    val who = if (user) message.sender.ifBlank { "你" } else "Velune"
                    val stamp = timeFormat.format(Date(message.createdAt))
                    appendLine("**$who · $stamp**")
                    appendLine()
                    appendLine(message.text.trim().ifBlank { "（空）" })
                    appendLine()

                    message.attachment?.let { attachment ->
                        appendLine("_[附件：${attachment.kind.name.lowercase()}]_")
                        appendLine()
                    }

                    if (message.promptTokens > 0 || message.completionTokens > 0) {
                        appendLine("_tokens：${message.promptTokens} 入 / ${message.completionTokens} 出_")
                        appendLine()
                    }
                }
            }
        }
    }

    /** 文件名带时间戳，重复导出不会互相覆盖。 */
    fun fileNameFor(exportedAt: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(exportedAt))
        return "export/velune-chat-$stamp.md"
    }
}
