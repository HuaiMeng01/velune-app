package com.veluneagent.core.data.agent

import com.veluneagent.core.data.ThinkingPolicy
import com.veluneagent.core.data.ThinkingSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * 时间窗口与思考参数的单测。
 *
 * 这两块都属于「用户随口一句话」触发的逻辑：算错一天、算成 23:00 跨天，
 * 用户不会看到异常，只会看到日历里多了一个错误的日程。
 */
class CalendarWindowTest {

    private val zone = TimeZone.getTimeZone("Asia/Shanghai")

    private fun base(hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(zone).apply {
            set(2026, Calendar.OCTOBER, 21, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun field(millis: Long, field: Int): Int =
        Calendar.getInstance(zone).apply { timeInMillis = millis }.get(field)

    @Test
    fun `半小时后用相对分钟`() {
        val now = base(10)
        val window = CalendarWindow.compute(
            now = now,
            startInMinutes = 30,
            dayOffset = 0,
            hour = null,
            minute = 0,
            endHour = null,
            endMinute = 0,
            durationMinutes = 60,
            allDay = false,
            zone = zone,
        )
        assertEquals(now + 30 * 60_000L, window.start)
        assertEquals(60 * 60_000L, window.end - window.start)
        assertEquals(10, field(window.start, Calendar.HOUR_OF_DAY))
        assertEquals(30, field(window.start, Calendar.MINUTE))
    }

    @Test
    fun `明天下午三点加六十分钟`() {
        val window = CalendarWindow.compute(
            now = base(9),
            startInMinutes = -1,
            dayOffset = 1,
            hour = 15,
            minute = 0,
            endHour = null,
            endMinute = 0,
            durationMinutes = 60,
            allDay = false,
            zone = zone,
        )
        assertEquals(22, field(window.start, Calendar.DAY_OF_MONTH))
        assertEquals(15, field(window.start, Calendar.HOUR_OF_DAY))
        assertEquals(16, field(window.end, Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `三点到四点用显式结束时间`() {
        val window = CalendarWindow.compute(
            now = base(9),
            startInMinutes = -1,
            dayOffset = 0,
            hour = 15,
            minute = 0,
            endHour = 16,
            endMinute = 30,
            durationMinutes = 60,
            allDay = false,
            zone = zone,
        )
        assertEquals(90 * 60_000L, window.end - window.start)
        assertEquals(16, field(window.end, Calendar.HOUR_OF_DAY))
        assertEquals(30, field(window.end, Calendar.MINUTE))
    }

    @Test
    fun `结束早于开始视为跨夜`() {
        val window = CalendarWindow.compute(
            now = base(9),
            startInMinutes = -1,
            dayOffset = 0,
            hour = 23,
            minute = 0,
            endHour = 1,
            endMinute = 0,
            durationMinutes = 60,
            allDay = false,
            zone = zone,
        )
        assertEquals(2 * 60 * 60_000L, window.end - window.start)
        assertEquals(22, field(window.end, Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `全天事件按 UTC 午夜写入`() {
        val window = CalendarWindow.compute(
            now = base(9),
            startInMinutes = -1,
            dayOffset = 1,
            hour = null,
            minute = 0,
            endHour = null,
            endMinute = 0,
            durationMinutes = 60,
            allDay = true,
            zone = zone,
        )
        // CalendarContract 规定全天事件的 DTSTART 是 **UTC 00:00**。
        // 旧实现拿本地 0 点当毫秒值，系统按 UTC 解释后整体前移一天
        // （上海 22 号 00:00 → UTC 21 号 16:00）。
        val utcStart = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = window.start
        }
        val utcEnd = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = window.end
        }
        assertEquals("起点应为 UTC 零点", 0, utcStart.get(Calendar.HOUR_OF_DAY))
        assertEquals("起点应是 22 号", 22, utcStart.get(Calendar.DAY_OF_MONTH))
        assertEquals("终点应为次日 UTC 零点", 0, utcEnd.get(Calendar.HOUR_OF_DAY))
        assertEquals("终点应是 23 号", 23, utcEnd.get(Calendar.DAY_OF_MONTH))
        assertEquals(86_400_000L, window.end - window.start)
        // 用户视角仍然是 22 号，只是时刻变成本地 08:00
        assertEquals(22, field(window.start, Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `没给小时时回落到九点`() {
        val window = CalendarWindow.compute(
            now = base(9),
            startInMinutes = -1,
            dayOffset = 0,
            hour = null,
            minute = 0,
            endHour = null,
            endMinute = 0,
            durationMinutes = 30,
            allDay = false,
            zone = zone,
        )
        assertEquals(9, field(window.start, Calendar.HOUR_OF_DAY))
        assertEquals(30 * 60_000L, window.end - window.start)
    }
}

class ThinkingSupportTest {

    @Test
    fun `GLM-5_3 系列不允许关闭思考`() {
        assertFalse(ThinkingSupport.canDisable("glm-5.3"))
        assertFalse(ThinkingSupport.canDisable("glm-5.3-flash"))
        assertTrue(ThinkingSupport.canDisable("glm-4.6"))
        assertTrue(ThinkingSupport.canDisable("deepseek-flash"))
        assertTrue(ThinkingSupport.canDisable("mimo-v2.6-pro"))
    }

    @Test
    fun `用户自填的未知模型不猜测能否关闭`() {
        // 模型名由用户输入，必然会出现「应用没听过」的名字。
        // 这时既不能说能关（可能 400），也不能说不能关（可能本来就能关），只能是 Unknown。
        assertEquals(ThinkingPolicy.Unknown, ThinkingSupport.policyOf("my-gateway-model"))
        assertEquals(ThinkingPolicy.Unknown, ThinkingSupport.policyOf("gpt-4o"))
        assertEquals(ThinkingPolicy.Unknown, ThinkingSupport.policyOf(""))
        assertFalse(ThinkingSupport.canDisable("gpt-4o"))

        assertEquals(ThinkingPolicy.CanDisable, ThinkingSupport.policyOf("DeepSeek-V4-Pro"))
        assertEquals(ThinkingPolicy.ForcedOn, ThinkingSupport.policyOf("GLM-5.3-Flash"))
    }

    @Test
    fun `推理强度只对支持的模型下发`() {
        assertTrue(ThinkingSupport.supportsEffort("deepseek-v4-pro"))
        assertTrue(ThinkingSupport.supportsEffort("glm-5.3"))
        // MiMo 文档明确不支持 reasoning_effort
        assertFalse(ThinkingSupport.supportsEffort("mimo-v2.6-pro"))
        assertFalse(ThinkingSupport.supportsEffort("gpt-4o"))
    }
}
