package com.veluneagent.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class TokenCountFormatTest {

    @Test
    fun `四位数以下原样显示`() {
        assertEquals("0", formatTokenCount(0))
        assertEquals("999", formatTokenCount(999))
    }

    @Test
    fun `千级与百万级压缩显示`() {
        assertEquals("1.0k", formatTokenCount(1_000))
        assertEquals("12.3k", formatTokenCount(12_345))
        assertEquals("1.5m", formatTokenCount(1_500_000))
    }
}
