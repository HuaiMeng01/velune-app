package com.veluneagent.core.data.export

import com.veluneagent.core.data.MessageRole
import com.veluneagent.core.data.StoredMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatExporterTest {

    private fun message(
        id: Long,
        role: MessageRole,
        text: String,
        at: Long = 1_700_000_000_000,
        reasoning: String = "",
    ) = StoredMessage(
        id = id,
        conversationId = 1L,
        role = role,
        text = text,
        createdAt = at,
        reasoning = reasoning,
        promptTokens = 10,
        completionTokens = 20,
    )

    private val sessions = listOf(
        ChatExporter.Session(
            title = "小鹿",
            createdAt = 1_700_000_000_000,
            messages = listOf(
                message(1, MessageRole.User, "在吗"),
                message(2, MessageRole.Assistant, "在的，怎么了？"),
            ),
        ),
        ChatExporter.Session(
            title = "新对话",
            createdAt = 1_700_000_100_000,
            messages = listOf(message(3, MessageRole.User, "只有一条")),
        ),
    )

    @Test
    fun `头部包含会话数与消息数`() {
        val markdown = ChatExporter.toMarkdown(sessions, exportedAt = 1_700_000_200_000)
        assertTrue(markdown.startsWith("# Velune 聊天记录"))
        assertTrue(markdown.contains("会话：2 个 · 消息：3 条"))
    }

    @Test
    fun `每个会话都有标题与角色名`() {
        val markdown = ChatExporter.toMarkdown(sessions)
        assertTrue(markdown.contains("## 小鹿"))
        assertTrue(markdown.contains("## 新对话"))
        assertTrue(markdown.contains("**你 · "))
        assertTrue(markdown.contains("**Velune · "))
    }

    @Test
    fun `思考过程不导出`() {
        val withThinking = listOf(
            ChatExporter.Session(
                title = "t",
                createdAt = 1L,
                messages = listOf(message(9, MessageRole.Assistant, "答案", reasoning = "这是一段很长的推理")),
            ),
        )
        val markdown = ChatExporter.toMarkdown(withThinking)
        assertTrue(markdown.contains("答案"))
        assertFalse("思考不该出现在导出文件里", markdown.contains("这是一段很长的推理"))
    }

    @Test
    fun `文件名带时间戳且落在 export 目录`() {
        val name = ChatExporter.fileNameFor(1_700_000_200_000)
        assertTrue(name.startsWith("export/velune-chat-"))
        assertTrue(name.endsWith(".md"))
        assertEquals(name, ChatExporter.fileNameFor(1_700_000_200_000))
    }
}
