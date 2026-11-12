package com.veluneagent.core.data.system

import android.content.ContentValues
import android.content.Context
import com.veluneagent.core.data.VeluneDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TodoItem(
    val id: Long,
    val title: String,
    val done: Boolean,
    val dueAt: Long?,
    val createdAt: Long,
)

/**
 * 本地待办清单。
 *
 * 为什么不用系统「待办」：Android 没有公开的待办 Provider（Google Tasks 是私有实现，
 * 各家 ROM 也不统一）。要做跨设备同步的待办，必须有服务端；当前版本只做本机清单。
 * 需要「到点提醒」的场景，交给系统日历（见 CalendarRepository）。
 */
class TodoStore(private val context: Context) {

    private val helper = VeluneDb.of(context)

    suspend fun add(title: String, dueAt: Long? = null): Long = withContext(Dispatchers.IO) {
        val normalized = title.trim().take(200)
        val values = ContentValues().apply {
            put("title", normalized)
            put("done", 0)
            if (dueAt != null) put("due_at", dueAt)
            put("created_at", System.currentTimeMillis())
        }
        val id = helper.writableDatabase.insertOrThrow("todos", null, values)
        // 有截止时间就排闹钟。不排的话 due_at 只是躺在库里的一列：
        // 用户以为"它记住了"，实际到点什么都不会发生
        dueAt?.let { TodoReminder.schedule(context, id, normalized, it) }
        id
    }

    /**
     * 启动时重建闹钟。
     *
     * 设备重启后 AlarmManager 里的预约全部丢失，不重建就永远不响。
     * 只取未完成的：已完成的不该再提醒。
     */
    suspend fun rescheduleReminders() = withContext(Dispatchers.IO) {
        runCatching {
            TodoReminder.rescheduleAll(context, list(includeDone = false, limit = 200))
        }
    }

    suspend fun list(includeDone: Boolean = false, limit: Int = 50): List<TodoItem> =
        withContext(Dispatchers.IO) {
            val where = if (includeDone) null else "done = 0"
            val items = mutableListOf<TodoItem>()
            helper.readableDatabase.query(
                "todos",
                arrayOf("id", "title", "done", "due_at", "created_at"),
                where,
                null,
                null,
                null,
                "done ASC, (due_at IS NULL) ASC, due_at ASC, id DESC",
                limit.toString(),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    items += TodoItem(
                        id = cursor.getLong(0),
                        title = cursor.getString(1).orEmpty(),
                        done = cursor.getInt(2) != 0,
                        dueAt = if (cursor.isNull(3)) null else cursor.getLong(3),
                        createdAt = cursor.getLong(4),
                    )
                }
            }
            items
        }

    suspend fun setDone(id: Long, done: Boolean): Boolean = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put("done", if (done) 1 else 0) }
        helper.writableDatabase.update("todos", values, "id = ?", arrayOf(id.toString())) > 0
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM todos WHERE done = 0", null)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }
}
