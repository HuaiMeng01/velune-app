package com.veluneagent.core.data.im

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QQ 官方网关事件解析。
 *
 * 两类事件的 openid 字段不同（单聊 author.user_openid，群聊 author.member_openid + d.group_openid），
 * 写错一个字段名就会「连上了但永远不回复」，而且没有任何报错。
 */
class QqOfficialEventParserTest {

    @Test
    fun `解析单聊消息`() {
        val raw = """
            {"op":0,"s":12,"t":"C2C_MESSAGE_CREATE","d":{
              "id":"ROBOT1.0_abc","content":"你好","timestamp":"2026-10-21T23:00:00+08:00",
              "author":{"user_openid":"A1B2C3","union_openid":""}
            }}
        """.trimIndent()
        val incoming = QqOfficialEventParser.parse(raw)
        assertNotNull(incoming)
        assertEquals("你好", incoming!!.text)
        assertEquals("A1B2C3", incoming.openId)
        assertNull(incoming.groupOpenId)
        assertEquals("ROBOT1.0_abc", incoming.messageId)
        assertEquals("C2C_MESSAGE_CREATE", incoming.eventType)
        assertTrue(!incoming.isGroup)
    }

    @Test
    fun `解析群 at 消息`() {
        val raw = """
            {"op":0,"s":13,"t":"GROUP_AT_MESSAGE_CREATE","d":{
              "id":"ROBOT1.0_def","content":"今天几号","group_openid":"GROUP_XYZ",
              "author":{"member_openid":"MEMBER_1","member_role":"member"}
            }}
        """.trimIndent()
        val incoming = QqOfficialEventParser.parse(raw)
        assertNotNull(incoming)
        assertEquals("GROUP_XYZ", incoming!!.groupOpenId)
        assertEquals("MEMBER_1", incoming.openId)
        assertTrue(incoming.isGroup)
    }

    @Test
    fun `只处理 op 为 0 的事件`() {
        // op=10 Hello / op=11 心跳 ACK / op=7 重连 都不是消息
        assertNull(QqOfficialEventParser.parse("""{"op":10,"d":{"heartbeat_interval":45000}}"""))
        assertNull(QqOfficialEventParser.parse("""{"op":11}"""))
        assertNull(QqOfficialEventParser.parse("""{"op":7}"""))
    }

    @Test
    fun `忽略 READY 与其它事件类型`() {
        assertNull(
            QqOfficialEventParser.parse(
                """{"op":0,"t":"READY","d":{"session_id":"x","user":{"id":"1","username":"bot"}}}""",
            ),
        )
        assertNull(
            QqOfficialEventParser.parse(
                """{"op":0,"t":"GROUP_ADD_ROBOT","d":{"group_openid":"G"}}""",
            ),
        )
    }

    @Test
    fun `空内容不丢弃也不触发模型 但缺 openid 时丢弃`() {
        // 纯图片/表情消息的 content 是空串：必须解析出来并标成 hasNonText，
        // 否则上层拿不到这条消息，对面就是"已读不回"
        val emptyContent = QqOfficialEventParser.parse(
            """{"op":0,"t":"C2C_MESSAGE_CREATE","d":{"id":"1","content":"  ","author":{"user_openid":"U"}}}""",
        )
        assertNotNull(emptyContent)
        assertEquals("", emptyContent!!.text)
        assertTrue(emptyContent.hasNonText)

        // 连 openid 都没有，无法回复：只能丢
        assertNull(
            QqOfficialEventParser.parse(
                """{"op":0,"t":"C2C_MESSAGE_CREATE","d":{"id":"1","content":"hi","author":{}}}""",
            ),
        )
        assertNull(
            QqOfficialEventParser.parse(
                """{"op":0,"t":"GROUP_AT_MESSAGE_CREATE","d":{"id":"1","content":"hi","author":{"member_openid":"M"}}}""",
            ),
        )
    }

    @Test
    fun `文本消息不标记非文本 且带事件时间`() {
        val incoming = QqOfficialEventParser.parse(
            """
            {"op":0,"s":1,"t":"GROUP_AT_MESSAGE_CREATE","d":{
              "id":"9","content":"在吗","group_openid":"G",
              "timestamp":"2026-10-21T23:00:00+08:00",
              "author":{"member_openid":"M"}
            }}
            """.trimIndent(),
        )
        assertNotNull(incoming)
        assertTrue(!incoming!!.hasNonText)
        // ISO8601 字符串必须能解析成毫秒，否则被动回复窗口判断永远不生效
        assertEquals(1792594800000L, incoming.timestamp)
    }

    @Test
    fun `时间戳为毫秒数时直接用`() {
        val incoming = QqOfficialEventParser.parse(
            """{"op":0,"t":"C2C_MESSAGE_CREATE","d":{"id":"1","content":"hi","timestamp":1761068400123,"author":{"user_openid":"U"}}}""",
        )
        assertNotNull(incoming)
        assertEquals(1761068400123L, incoming!!.timestamp)
    }

    @Test
    fun `坏数据不抛异常`() {
        assertNull(QqOfficialEventParser.parse("not json"))
        assertNull(QqOfficialEventParser.parse("{}"))
    }
}
