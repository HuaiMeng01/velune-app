package com.veluneagent.feature.chat.ui

import com.veluneagent.core.data.AttachmentKind
import com.veluneagent.core.data.ChatAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTest {

    private fun bubble(
        id: Long,
        fromUser: Boolean,
        text: String,
        attachment: ChatAttachment? = null,
    ) = Bubble(id = id, fromUser = fromUser, text = text, createdAt = id, attachment = attachment)

    @Test
    fun `剔除占位与中断消息`() {
        val history = buildHistory(
            listOf(
                bubble(1, true, "你好"),
                bubble(2, false, "…"),
                bubble(3, false, "（已中断）"),
                bubble(4, false, "在的"),
            ),
            limit = 40,
        )
        assertEquals(listOf("user", "assistant"), history.map { it.role })
        assertEquals(listOf("你好", "在的"), history.map { it.text })
    }

    @Test
    fun `QQ 来源进上下文时带说话人前缀`() {
        // App 内自己发的消息：sender 为空，正文必须逐字不变
        val plain = bubble(1, true, "机主自己说的")
        // 从 QQ 写进同一条会话的消息：进上下文时要能看出「是谁说的」
        val remote = Bubble(
            id = 2,
            fromUser = true,
            text = "别人从 QQ 发来的",
            createdAt = 2,
            sender = "QQ 私聊 · 张三",
        )
        val history = buildHistory(listOf(plain, remote), limit = 40)
        assertEquals("机主自己说的", history[0].text)
        assertEquals("[QQ 私聊 · 张三] 别人从 QQ 发来的", history[1].text)
    }

    @Test
    fun `来源前缀与文本附件同时存在时格式不被破坏`() {
        // 既有附件用例都直调 chatMessageOf，绕过了前缀层；
        // 「sender 非空 + 附件」这个组合只有走 buildHistory 才覆盖得到
        val attachment = ChatAttachment(
            kind = AttachmentKind.Text,
            name = "notes.md",
            mime = "text/markdown",
            text = "# 标题",
        )
        val remote = Bubble(
            id = 1,
            fromUser = true,
            text = "看看这个",
            createdAt = 1,
            attachment = attachment,
            sender = "QQ 私聊 · 张三",
        )
        val history = buildHistory(listOf(remote), limit = 40)
        assertEquals(1, history.size)
        val text = history[0].text
        assertTrue(text.startsWith("[QQ 私聊 · 张三] 看看这个"))
        // 围栏结构没被前缀破坏：三反引号开头 + 三反引号结尾
        assertEquals(6, text.count { it == '`' })
        // 前缀只出现一次（不会因为 attach 拼装或多次 map 被重复加）
        assertEquals(1, text.split("[QQ 私聊 · 张三]").size - 1)
    }

    @Test
    fun `超出上限时保留最近的若干条`() {
        val all = (1..10).map { bubble(it.toLong(), it % 2 == 1, "m$it") }
        val history = buildHistory(all, limit = 3)
        assertEquals(listOf("m8", "m9", "m10"), history.map { it.text })
    }

    @Test
    fun `文本附件拼进正文`() {
        val attachment = ChatAttachment(
            kind = AttachmentKind.Text,
            name = "notes.md",
            mime = "text/markdown",
            text = "# 标题",
        )
        val message = chatMessageOf("看看这个", attachment)
        assertTrue(message.text.startsWith("看看这个"))
        assertTrue(message.text.contains("[附件：notes.md]"))
        assertTrue(message.text.contains("# 标题"))
        assertNull(message.imageDataUrl)
    }

    @Test
    fun `图片附件走多模态字段且正文保持原样`() {
        val attachment = ChatAttachment(
            kind = AttachmentKind.Image,
            name = "a.png",
            mime = "image/png",
            dataUrl = "data:image/png;base64,AAA",
        )
        val message = chatMessageOf("图里是什么", attachment)
        assertEquals("图里是什么", message.text)
        assertEquals("data:image/png;base64,AAA", message.imageDataUrl)
    }

    @Test
    fun `编辑重发使用新文本而不是旧文本`() {
        val old = bubble(3, true, "旧问题")
        val message = outgoingMessage(
            snapshot = listOf(bubble(1, true, "开场"), old, bubble(4, false, "旧回答")),
            text = "新问题",
            regenerate = false,
            edited = old,
            pendingAttachment = null,
        )
        assertEquals("新问题", message.text)
    }

    @Test
    fun `编辑重发沿到原消息的附件`() {
        val attachment = ChatAttachment(
            kind = AttachmentKind.Text,
            name = "a.md",
            mime = "text/markdown",
            text = "正文",
        )
        val old = bubble(3, true, "旧问题", attachment)
        val message = outgoingMessage(
            snapshot = listOf(old),
            text = "新问题",
            regenerate = false,
            edited = old,
            pendingAttachment = null,
        )
        assertTrue(message.text.contains("[附件：a.md]"))
        assertTrue(message.text.startsWith("新问题"))
    }

    @Test
    fun `重新生成复用最后一条用户消息并忽略新文本`() {
        val snapshot = listOf(bubble(1, true, "第一问"), bubble(2, false, "回答"), bubble(3, true, "第二问"))
        val message = outgoingMessage(
            snapshot = snapshot,
            text = "",
            regenerate = true,
            edited = null,
            pendingAttachment = null,
        )
        assertEquals("第二问", message.text)
    }

    @Test
    fun `重新生成时来源前缀不丢`() {
        // regenerate 走 toChatMessage()：QQ 来源的前缀必须一起带上，
        // 否则重答一次就"忘了这句话是谁说的"
        val remote = Bubble(
            id = 3,
            fromUser = true,
            text = "第二问",
            createdAt = 3,
            sender = "QQ 私聊 · 张三",
        )
        val message = outgoingMessage(
            snapshot = listOf(bubble(1, true, "第一问"), bubble(2, false, "回答"), remote),
            text = "",
            regenerate = true,
            edited = null,
            pendingAttachment = null,
        )
        assertEquals("[QQ 私聊 · 张三] 第二问", message.text)
    }

    @Test
    fun `普通发送带上待发附件`() {
        val attachment = ChatAttachment(
            kind = AttachmentKind.Image,
            name = "b.png",
            mime = "image/png",
            dataUrl = "data:image/png;base64,BBB",
        )
        val message = outgoingMessage(
            snapshot = emptyList(),
            text = "看图",
            regenerate = false,
            edited = null,
            pendingAttachment = attachment,
        )
        assertEquals("data:image/png;base64,BBB", message.imageDataUrl)
    }

    @Test
    fun `没有附件时正文不变`() {
        val message = chatMessageOf("纯文本", null)
        assertEquals("纯文本", message.text)
        assertNull(message.imageDataUrl)
    }
}
