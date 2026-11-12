package com.veluneagent.core.data.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * 待办到点提醒。
 *
 * 之前 `todos.due_at` 只是存进库里，从来没有人调度通知：
 * 用户说「明天提醒我交表」，Agent 回复"好的，已记录"，然后什么都不会发生。
 * 这比没有这个功能更糟 —— 用户会以为事情被记住了。
 *
 * 实现取舍：
 * - 用 `setAndAllowWhileIdle` 而不是 `setExactAndAllowWhileIdle`。
 *   精确闹钟要申请 `SCHEDULE_EXACT_ALARM`（敏感权限，商店审核要单独说明），
 *   而待办提醒差几分钟没有实质影响。
 * - 闹钟在设备重启后会丢失，所以启动时由 [rescheduleAll] 按库重建。
 *   没加 BOOT_COMPLETED 广播：后台广播限制在各家 ROM 上行为不一致，
 *   "打开应用就恢复"更可预期。
 */
object TodoReminder {

    const val ExtraId = "todo_id"
    const val ExtraTitle = "todo_title"

    fun schedule(context: Context, id: Long, title: String, dueAt: Long) {
        // 已经过期的不用设闹钟：设了会立刻触发一条"提醒你昨天的事"
        if (dueAt <= System.currentTimeMillis()) return
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching {
            manager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                dueAt,
                pendingIntent(context, id, title),
            )
        }
    }

    fun cancel(context: Context, id: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { manager.cancel(pendingIntent(context, id, "")) }
    }

    /** 启动时按库重建：重启后闹钟全丢了，不重建就永远不响。 */
    fun rescheduleAll(context: Context, todos: List<TodoItem>) {
        todos.forEach { todo ->
            val dueAt = todo.dueAt ?: return@forEach
            if (todo.done) {
                cancel(context, todo.id)
            } else {
                schedule(context, todo.id, todo.title, dueAt)
            }
        }
    }

    private fun pendingIntent(context: Context, id: Long, title: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            // requestCode 用 id：不同待办互不覆盖；同一个待办重设时 UPDATE_CURRENT 换掉旧的
            id.toInt(),
            Intent(context, TodoAlarmReceiver::class.java).apply {
                putExtra(ExtraId, id)
                putExtra(ExtraTitle, title)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
