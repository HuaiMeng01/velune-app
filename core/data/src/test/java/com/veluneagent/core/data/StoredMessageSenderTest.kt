package com.veluneagent.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 交给模型的正文。
 *
 * 空 sender 必须**逐字**返回原文：App 内消息占绝大多数，多一个空格、
 * 多一对括号都会改写上游看到的上下文（也让既有断言失效）。
 */
class StoredMessageSenderTest {

    private fun message(
        role: MessageRole,
        text: String,
        sender: String = "",
    ) = StoredMessage(
        id = 1L,
        conversationId = 1L,
        role = role,
        text = text,
        createdAt = 0L,
        sender = sender,
    )

    @Test
    fun `空来源时逐字返回原文`() {
        assertEquals("你好", message(MessageRole.User, "你好").forModelText())
        assertEquals("  前后有空格  ", message(MessageRole.User, "  前后有空格  ").forModelText())
        assertEquals("", message(MessageRole.User, "").forModelText())
    }

    @Test
    fun `有来源时加方括号前缀`() {
        assertEquals(
            "[QQ 私聊 · 张三] 在吗",
            message(MessageRole.User, "在吗", "[QQ 私聊 · 张三]".trim('[', ']')).forModelText(),
        )
        assertEquals(
            "[QQ 群聊 · 对方] 早上好",
            message(MessageRole.User, "早上好", "QQ 群聊 · 对方").forModelText(),
        )
    }

    @Test
    fun `助手消息不加前缀`() {
        // 目前只有用户消息会带来源；万一将来助理侧也写了标签，也别把标签回灌给模型
        assertEquals("好的", message(MessageRole.Assistant, "好的", "QQ 私聊 · 张三").forModelText())
    }

    @Test
    fun `来源默认是空串`() {
        assertEquals("", message(MessageRole.User, "x").sender)
    }
}
