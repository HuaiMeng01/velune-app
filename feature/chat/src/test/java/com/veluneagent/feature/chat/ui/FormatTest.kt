package com.veluneagent.feature.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 思考块的数字展示。
 *
 * 这两条都是「看起来无所谓、真机上很碍眼」的问题：
 * 五位数会把右侧时间挤出屏幕；「思考 0s」会让用户以为计时坏了。
 */
class FormatTest {

    @Test
    fun `一万以内按字显示`() {
        assertEquals("0 字", formatCharCount(0))
        assertEquals("999 字", formatCharCount(999))
        assertEquals("9999 字", formatCharCount(9999))
    }

    @Test
    fun `超过一万改用 k`() {
        assertEquals("10.0k 字", formatCharCount(10_000))
        assertEquals("10.2k 字", formatCharCount(10_234))
        assertEquals("123.5k 字", formatCharCount(123_456))
    }

    @Test
    fun `不足一秒按一秒算`() {
        assertEquals("1s", formatSeconds(0))
        assertEquals("1s", formatSeconds(400))
        assertEquals("8s", formatSeconds(8_400))
    }

    @Test
    fun `超过一分钟换算成分秒`() {
        assertEquals("1m15s", formatSeconds(75_000))
        assertEquals("2m0s", formatSeconds(120_000))
    }
}
