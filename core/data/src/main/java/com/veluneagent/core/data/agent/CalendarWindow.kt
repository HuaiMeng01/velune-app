package com.veluneagent.core.data.agent

import java.util.Calendar
import java.util.TimeZone

/**
 * 日历时间窗口计算。
 *
 * 单独抽出来是因为这里的规则最容易在「用户随口一句话」上出错：
 * 「半小时后」「明天下午三点」「三点到四点」「晚上十一点到凌晨一点」，
 * 每条都要能算对，而且必须可单测。
 */
object CalendarWindow {

    data class Window(val start: Long, val end: Long)

    /**
     * @param startInMinutes 相对当前时间的分钟数；>=0 时优先于日期+时刻
     * @param dayOffset 0=今天，1=明天
     * @param hour 开始小时；null 表示没给
     * @param endHour 结束小时；给了就以它为准（不再用 duration）
     */
    fun compute(
        now: Long,
        startInMinutes: Int,
        dayOffset: Int,
        hour: Int?,
        minute: Int,
        endHour: Int?,
        endMinute: Int,
        durationMinutes: Int,
        allDay: Boolean,
        zone: TimeZone = TimeZone.getDefault(),
    ): Window {
        val calendar = Calendar.getInstance(zone).apply { timeInMillis = now }

        val start = when {
            allDay -> {
                // CalendarContract 规定全天事件的 DTSTART 按 **UTC 午夜** 解释。
                // 这里必须先在用户时区里定出「哪一天」，再取那一天的 UTC 00:00：
                // 直接拿本地 0 点当毫秒值，在 UTC+8 下会被系统读成前一天 16:00，
                // 表现为「明天全天开会」落到前一天。
                val localDay = Calendar.getInstance(zone).apply {
                    timeInMillis = now
                    add(Calendar.DAY_OF_YEAR, dayOffset)
                }
                Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    clear()
                    set(
                        localDay.get(Calendar.YEAR),
                        localDay.get(Calendar.MONTH),
                        localDay.get(Calendar.DAY_OF_MONTH),
                        0,
                        0,
                        0,
                    )
                }.timeInMillis
            }

            startInMinutes >= 0 -> now + startInMinutes * 60_000L

            else -> calendar.apply {
                add(Calendar.DAY_OF_YEAR, dayOffset)
                set(Calendar.HOUR_OF_DAY, hour ?: 9)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        val end = when {
            // 全天事件：DTEND 必须是次日 00:00
            allDay -> start + 86_400_000L

            endHour != null -> {
                val explicit = Calendar.getInstance(zone).apply {
                    timeInMillis = start
                    set(Calendar.HOUR_OF_DAY, endHour)
                    set(Calendar.MINUTE, endMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                // 结束早于开始 = 跨夜（23:00 → 01:00）
                if (explicit <= start) explicit + 86_400_000L else explicit
            }

            else -> start + durationMinutes.coerceIn(1, 1440) * 60_000L
        }

        return Window(start = start, end = end)
    }
}
