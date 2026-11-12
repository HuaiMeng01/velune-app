package com.veluneagent.core.data.im

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OneBot 11 消息事件解析。
 *
 * 这里最容易错的两处：`message` 是数组还是 CQ 字符串、群消息怎么判断「在跟我说话」。
 * 判错的后果很直接——要么收不到任何消息，要么被一个热闹的群把模型调用打爆。
 */
class QqEventParserTest {

    private fun privateEvent(text: String) = """
        {"post_type":"message","message_type":"private","sub_type":"friend",
         "self_id":10001,"user_id":20002,"message_id":33,
         "message":[{"type":"text","data":{"text":"$text"}}],
         "sender":{"nickname":"小明"}}
    """.trimIndent()

    @Test
    fun `解析私聊文本`() {
        val incoming = QqEventParser.parseMessageEvent(privateEvent("你好"))
        assertNotNull(incoming)
        assertEquals("你好", incoming!!.text)
        assertEquals("20002", incoming.userId)
        assertEquals(null, incoming.groupId)
        assertEquals("小明", incoming.senderName)
    }

    @Test
    fun `群消息被 at 才算找我`() {
        val atMe = """
            {"post_type":"message","message_type":"group","self_id":10001,
             "user_id":20002,"group_id":30003,"message_id":44,
             "message":[{"type":"at","data":{"qq":"10001"}},{"type":"text","data":{"text":" 今天天气"}}],
             "sender":{"card":"群友A"}}
        """.trimIndent()
        val incoming = QqEventParser.parseMessageEvent(atMe)
        assertNotNull(incoming)
        assertEquals("今天天气", incoming!!.text.trim())
        assertEquals("30003", incoming.groupId)
        assertTrue(incoming.isGroup)

        val notAtMe = """
            {"post_type":"message","message_type":"group","self_id":10001,
             "user_id":20002,"group_id":30003,"message_id":45,
             "message":[{"type":"text","data":{"text":"别人的闲聊"}}]}
        """.trimIndent()
        assertNull(QqEventParser.parseMessageEvent(notAtMe))
    }

    @Test
    fun `兼容 CQ 码字符串模式`() {
        val raw = """
            {"post_type":"message","message_type":"group","self_id":10001,
             "user_id":20002,"group_id":30003,"message_id":46,
             "raw_message":"[CQ:at,qq=10001] 帮我算下 2+2"}
        """.trimIndent()
        val incoming = QqEventParser.parseMessageEvent(raw)
        assertNotNull(incoming)
        assertEquals("帮我算下 2+2", incoming!!.text.trim())
    }

    @Test
    fun `心跳与自身消息一律忽略`() {
        assertNull(
            QqEventParser.parseMessageEvent(
                """{"post_type":"meta_event","meta_event_type":"heartbeat","self_id":10001}""",
            ),
        )
        assertNull(
            QqEventParser.parseMessageEvent(
                """{"post_type":"message","message_type":"private","self_id":10001,"user_id":10001,"message":[{"type":"text","data":{"text":"自己"}}]}""",
            ),
        )
    }

    @Test
    fun `纯图片消息标记为非文本 不静默丢弃`() {
        // 图片段没有 text：以前这里直接 return null，等于把图片消息静默丢掉，
        // 对面看到的是彻底的"已读不回"。现在要解析出来并标记 hasNonText
        val image = QqEventParser.parseMessageEvent(
            """{"post_type":"message","message_type":"private","self_id":10001,"user_id":20002,"message":[{"type":"image","data":{"file":"a.jpg"}}]}""",
        )
        assertNotNull(image)
        assertEquals("", image!!.text)
        assertTrue(image.hasNonText)

        // CQ 码字符串模式同样要识别出图片段
        val cqImage = QqEventParser.parseMessageEvent(
            """{"post_type":"message","message_type":"private","self_id":10001,"user_id":20002,"raw_message":"[CQ:image,file=a.jpg]"}""",
        )
        assertNotNull(cqImage)
        assertTrue(cqImage!!.hasNonText)
    }

    @Test
    fun `完全没有消息段时不触发`() {
        assertNull(
            QqEventParser.parseMessageEvent(
                """{"post_type":"message","message_type":"private","self_id":10001,"user_id":20002}""",
            ),
        )
    }

    @Test
    fun `坏数据不抛异常`() {
        assertNull(QqEventParser.parseMessageEvent("not json"))
        assertNull(QqEventParser.parseMessageEvent(""))
    }
}
