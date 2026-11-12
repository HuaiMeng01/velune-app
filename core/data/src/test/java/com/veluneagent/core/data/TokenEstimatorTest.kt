package com.veluneagent.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenEstimatorTest {

    @Test
    fun `空文本不计 token`() {
        assertEquals(0, TokenEstimator.estimate(""))
    }

    @Test
    fun `中文按字计`() {
        // 10 个汉字 + 每条消息 4 的固定开销
        assertEquals(14, TokenEstimator.estimate("你好世界这是一个测试"))
    }

    @Test
    fun `英文按四字符计`() {
        // 8 个字符 = 2 token，加固定开销
        assertEquals(6, TokenEstimator.estimate("abcdefgh"))
    }

    @Test
    fun `消息列表为逐条累加`() {
        val messages = listOf(
            ChatMessage("system", "你是谁"),
            ChatMessage("user", "介绍一下自己"),
        )
        val expected = TokenEstimator.estimate("你是谁") + TokenEstimator.estimate("介绍一下自己")
        assertEquals(expected, TokenEstimator.estimateMessages(messages))
    }

    @Test
    fun `长文本估算不会溢出`() {
        val long = "a".repeat(100_000)
        assertTrue(TokenEstimator.estimate(long) in 24_000..26_000)
    }
}
