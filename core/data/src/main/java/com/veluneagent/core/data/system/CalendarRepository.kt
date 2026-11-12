package com.veluneagent.core.data.system

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 系统日历读写。
 *
 * 走 CalendarContract，不需要任何第三方 SDK；但必须拿到 READ/WRITE_CALENDAR 运行时权限。
 * 写入时先找一个「可写日历」：用户可能只有只读订阅日历，直接插 Events 会失败。
 */
class CalendarRepository(
    private val context: Context,
    private val permissions: PermissionRequester,
) {

    data class Event(
        val id: Long,
        val title: String,
        val begin: Long,
        val end: Long,
        val location: String = "",
        val allDay: Boolean = false,
    )

    suspend fun createEvent(
        title: String,
        beginMillis: Long,
        endMillis: Long,
        description: String = "",
        location: String = "",
        allDay: Boolean = false,
        /** 提前多少分钟提醒；null 表示不设提醒，0 表示准时。 */
        reminderMinutes: Int? = 10,
        /** RFC5545 重复规则（如 FREQ=WEEKLY）；null / 空 = 不重复。 */
        repeatRule: String? = null,
    ): Result<Long> = runCatching {
        require(permissions.isGranted(Manifest.permission.WRITE_CALENDAR) ||
            permissions.request(Manifest.permission.WRITE_CALENDAR)) {
            "未授予日历写入权限，请在系统设置中允许 Velune 修改日历"
        }
        require(endMillis > beginMillis) { "结束时间必须晚于开始时间" }
        val calendarId = writableCalendarId() ?: error("设备上没有可写日历，请先在系统日历里创建一个本地日历")
        val timeZone = TimeZone.getDefault().id
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title.take(200))
            put(CalendarContract.Events.DESCRIPTION, description.take(2_000))
            if (location.isNotBlank()) put(CalendarContract.Events.EVENT_LOCATION, location.take(200))
            put(CalendarContract.Events.DTSTART, beginMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            // 全天事件按文档要求用 UTC，且 DTEND 必须是次日 00:00（由调用方保证）
            put(CalendarContract.Events.EVENT_TIMEZONE, if (allDay) "UTC" else timeZone)
            put(CalendarContract.Events.HAS_ALARM, if (reminderMinutes != null) 1 else 0)
            put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
            // 重复事件：RRULE 直接落在 Events 表上，系统日历会按规则展开实例
            repeatRule?.takeIf { it.isNotBlank() }?.let {
                put(CalendarContract.Events.RRULE, it)
            }
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: error("写入日历失败（系统拒绝）")
        val eventId = ContentUris.parseId(uri)

        // 提醒是独立表：必须带上 EVENT_ID / MINUTES / METHOD 三个字段，缺一不可
        if (reminderMinutes != null) {
            val reminder = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, reminderMinutes)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            runCatching {
                context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminder)
            }
        }
        eventId
    }

    /**
     * 删除一个日程。
     *
     * 重复事件传 event_id 会删掉整个系列（CalendarContract 的默认行为）。
     * 之前只有「创建 + 查询」，建错了只能让用户自己去系统日历里翻着删。
     */
    suspend fun deleteEvent(eventId: Long): Result<Unit> = runCatching {
        require(
            permissions.isGranted(Manifest.permission.WRITE_CALENDAR) ||
                permissions.request(Manifest.permission.WRITE_CALENDAR),
        ) { "未授予日历写入权限，请在系统设置中允许 Velune 修改日历" }
        require(eventId > 0L) { "event_id 不合法" }
        val deleted = context.contentResolver.delete(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
            null,
            null,
        )
        require(deleted > 0) { "没有找到这个日程，可能已经被删除" }
    }

    /**
     * 修改一条日程。null 表示该字段保持不变。
     *
     * 为什么不"删了重建"：重建会换掉 event_id，用户在其他日历应用里的链接、
     * 共享邀请、外部同步都会断掉。这里原地改。
     */
    suspend fun updateEvent(
        eventId: Long,
        title: String? = null,
        beginMillis: Long? = null,
        endMillis: Long? = null,
        allDay: Boolean? = null,
        reminderMinutes: Int? = null,
    ): Result<Unit> = runCatching {
        require(
            permissions.isGranted(Manifest.permission.WRITE_CALENDAR) ||
                permissions.request(Manifest.permission.WRITE_CALENDAR),
        ) { "未授予日历写入权限，请在系统设置中允许 Velune 修改日历" }
        require(eventId > 0L) { "event_id 不合法" }
        require(title != null || beginMillis != null || allDay != null || reminderMinutes != null) {
            "没有要修改的内容"
        }
        if (beginMillis != null && endMillis != null) {
            require(endMillis > beginMillis) { "结束时间必须晚于开始时间" }
        }

        val values = ContentValues().apply {
            title?.let { put(CalendarContract.Events.TITLE, it.take(200)) }
            if (beginMillis != null) put(CalendarContract.Events.DTSTART, beginMillis)
            if (endMillis != null) put(CalendarContract.Events.DTEND, endMillis)
            if (allDay != null) {
                put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
                // 时区必须跟着全天标记一起改：否则系统会按本地时区解释一个本该是 UTC 的时刻
                put(
                    CalendarContract.Events.EVENT_TIMEZONE,
                    if (allDay) "UTC" else TimeZone.getDefault().id,
                )
            }
        }
        val updated = context.contentResolver.update(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
            values,
            null,
            null,
        )
        require(updated > 0) { "没有找到这个日程，可能已经被删除" }

        if (reminderMinutes != null) {
            // 提醒在独立表里，改提醒 = 删旧的再插新的
            context.contentResolver.delete(
                CalendarContract.Reminders.CONTENT_URI,
                "${CalendarContract.Reminders.EVENT_ID} = ?",
                arrayOf(eventId.toString()),
            )
            if (reminderMinutes >= 0) {
                runCatching {
                    context.contentResolver.insert(
                        CalendarContract.Reminders.CONTENT_URI,
                        ContentValues().apply {
                            put(CalendarContract.Reminders.EVENT_ID, eventId)
                            put(CalendarContract.Reminders.MINUTES, reminderMinutes)
                            put(
                                CalendarContract.Reminders.METHOD,
                                CalendarContract.Reminders.METHOD_ALERT,
                            )
                        },
                    )
                }
            }
        }
    }

    suspend fun listEvents(fromMillis: Long, toMillis: Long, limit: Int = 20): Result<List<Event>> =        runCatching {
            require(permissions.isGranted(Manifest.permission.READ_CALENDAR) ||
                permissions.request(Manifest.permission.READ_CALENDAR)) {
                "未授予日历读取权限"
            }
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(fromMillis.toString())
                .appendPath(toMillis.toString())
                .build()
            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.ALL_DAY,
            )
            val events = mutableListOf<Event>()
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                while (cursor.moveToNext() && events.size < limit) {
                    events += Event(
                        id = cursor.getLong(0),
                        title = cursor.getString(1).orEmpty(),
                        begin = cursor.getLong(2),
                        end = cursor.getLong(3),
                        location = cursor.getString(4).orEmpty(),
                        allDay = cursor.getInt(5) != 0,
                    )
                }
            }
            events
        }

    private fun writableCalendarId(): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.IS_PRIMARY,
        )
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            "${CalendarContract.Calendars.IS_PRIMARY} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val level = cursor.getInt(1)
                // CAL_ACCESS_CONTRIBUTOR = 500：可以新增事件
                if (level >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) {
                    return cursor.getLong(0)
                }
            }
        }
        return null
    }

    companion object {
        private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

        fun format(millis: Long): String = fmt.format(Date(millis))

        /** 把「今天 15:00」这类入口参数转成毫秒；失败返回 null 由工具层报错。 */
        fun resolveMillis(
            year: Int?,
            month: Int?,
            day: Int?,
            hour: Int,
            minute: Int,
        ): Long {
            val cal = Calendar.getInstance()
            year?.let { cal.set(Calendar.YEAR, it) }
            month?.let { cal.set(Calendar.MONTH, it - 1) }
            day?.let { cal.set(Calendar.DAY_OF_MONTH, it) }
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        /** 相对今天的天数 + 时分，得到毫秒。模型只需要给「后天 15:30」这样的相对表达。 */
        fun resolveRelative(dayOffset: Int, hour: Int, minute: Int): Long =
            Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, dayOffset)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

        /** 把 base 那天的时分替换掉，用于「三点到四点」这类显式结束时间。 */
        fun withTimeOfDay(baseMillis: Long, hour: Int, minute: Int): Long =
            Calendar.getInstance().apply {
                timeInMillis = baseMillis
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

        fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        fun startOfToday(): Long = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        fun endOfDay(daysAhead: Int = 0): Long = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, daysAhead)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }.timeInMillis
    }
}
