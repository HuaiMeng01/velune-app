package com.veluneagent.core.data.prompt

import android.content.ContentValues
import android.content.Context
import com.veluneagent.core.data.VeluneDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 提示词场景。
 *
 * v11 起**提示词不再按场景划分**：用户只维护一条人设，聊天、Agent、QQ 三处共用。
 * 这个枚举仍然保留 —— 它决定的是**平台规则**（L2）用哪一套，不是人设用哪一条：
 * - [Chat]：纯聊天，没有工具；
 * - [Agent]：有工具，平台规则必须压在人格之上（否则一句"不要用工具"就能让 Agent 失能）；
 * - [Qq]：远程入口，回复要短，且能不能用工具取决于设置里的开关。
 *
 * 场景规则写在代码里、由 [PromptComposer] 拼装，用户改不到，也就不存在"改 QQ 的
 * 短句要求顺手把 App 里的说话风格也改掉"的问题了。
 */
enum class PromptScene(val key: String, val label: String) {
    Chat("chat", "聊天"),
    Agent("agent", "Agent 模式"),
    Qq("qq", "QQ 自动回复"),
    ;

    companion object {
        fun of(key: String): PromptScene = entries.firstOrNull { it.key == key } ?: Chat
    }
}

data class Prompt(
    val id: Long,
    val scene: PromptScene,
    val name: String,
    val body: String,
    val isActive: Boolean,
    val updatedAt: Long,
) {
    /** 粗略 token 估算，用于界面上提示"每轮都要发一遍"。 */
    val estimatedTokens: Int
        get() {
            if (body.isEmpty()) return 0
            val cjk = body.count { it in '\u4e00'..'\u9fff' }
            return cjk + maxOf(1, (body.length - cjk) / 4)
        }
}

/**
 * 提示词仓库。
 *
 * 写入路径全部走事务 + "先把其他条全部置为未启用，再启用目标条"——
 * 顺序反了会撞 `idx_prompts_single_active` 的唯一约束（两条同时 active）。
 * 全局唯一由数据库层兜底：应用层一旦漏了某个分支，用户就会看到"两条都说是启用的"。
 */
class PromptStore(context: Context) {

    private val db = VeluneDb.of(context.applicationContext)

    /** 任何写操作都自增一次，观察者据此重查；数据量小，没必要做增量同步。 */
    private val revision = MutableStateFlow(0L)

    /** 全部提示词。设置页用；顺序：启用中的在最前，其后按更新时间倒序。 */
    fun observeAll(): Flow<List<Prompt>> = revision.map { queryAll() }.flowOn(Dispatchers.IO)

    /** 全局唯一生效的那一条。聊天、Agent、QQ 都取它。 */
    fun observeAny(): Flow<Prompt?> = revision.map { activeOnce() }.flowOn(Dispatchers.IO)

    suspend fun listAll(): List<Prompt> = withContext(Dispatchers.IO) { queryAll() }

    suspend fun activeAny(): Prompt? = withContext(Dispatchers.IO) { activeOnce() }

    suspend fun byId(id: Long): Prompt? = withContext(Dispatchers.IO) {
        db.readableDatabase.rawQuery(
            "SELECT id, scene, name, body, is_active, updated_at FROM prompts WHERE id = ?",
            arrayOf(id.toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@withContext null
            cursor.toPrompt(forceActive = null)
        }
    }

    /**
     * 新建一条提示词。
     *
     * @param scene 仅作为归档信息保留（老库里这一列是 NOT NULL）。人设本身不再按场景生效。
     * @param activate 是否立刻设为全局启用；true 时同一事务里先清掉其他条。
     */
    suspend fun create(
        scene: PromptScene = PromptScene.Chat,
        name: String,
        body: String,
        activate: Boolean = false,
    ): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        db.writableDatabase.beginTransaction()
        try {
            if (activate) deactivateAll()
            val values = ContentValues().apply {
                put("scene", scene.key)
                put("name", name.trim())
                put("body", body.trim())
                put("is_active", if (activate) 1 else 0)
                put("created_at", now)
                put("updated_at", now)
            }
            val id = db.writableDatabase.insertOrThrow("prompts", null, values)
            db.writableDatabase.setTransactionSuccessful()
            id
        } finally {
            db.writableDatabase.endTransaction()
        }.also { bump() }
    }

    suspend fun update(id: Long, name: String, body: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("name", name.trim())
            put("body", body.trim())
            put("updated_at", System.currentTimeMillis())
        }
        db.writableDatabase.update("prompts", values, "id = ?", arrayOf(id.toString()))
        bump()
    }

    /** 设为全局唯一启用：先把所有条清零，再置回目标条，单事务完成。 */
    suspend fun activate(id: Long) = withContext(Dispatchers.IO) {
        db.writableDatabase.beginTransaction()
        try {
            deactivateAll()
            db.writableDatabase.update(
                "prompts",
                ContentValues().apply {
                    put("is_active", 1)
                    put("updated_at", System.currentTimeMillis())
                },
                "id = ?",
                arrayOf(id.toString()),
            )
            db.writableDatabase.setTransactionSuccessful()
        } finally {
            db.writableDatabase.endTransaction()
        }
        bump()
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("prompts", "id = ?", arrayOf(id.toString()))
        bump()
    }

    /** 把「启用中」清零。全局只有一列标志位，所以不带任何 where 条件以外的过滤。 */
    private fun deactivateAll() {
        db.writableDatabase.execSQL("UPDATE prompts SET is_active = 0 WHERE is_active = 1")
    }

    private fun queryAll(): List<Prompt> =
        db.readableDatabase.rawQuery(
            """
            SELECT id, scene, name, body, is_active, updated_at
            FROM prompts
            ORDER BY is_active DESC, updated_at DESC, id DESC
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toPrompt(forceActive = null))
            }
        }

    private fun activeOnce(): Prompt? =
        db.readableDatabase.rawQuery(
            """
            SELECT id, scene, name, body, is_active, updated_at
            FROM prompts
            WHERE is_active = 1
            ORDER BY updated_at DESC, id DESC
            LIMIT 1
            """.trimIndent(),
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            cursor.toPrompt(forceActive = true)
        }

    /** 列顺序统一在这里映射，避免三处 SELECT 各写一份下标。 */
    private fun android.database.Cursor.toPrompt(forceActive: Boolean?): Prompt = Prompt(
        id = getLong(0),
        scene = PromptScene.of(getString(1)),
        name = getString(2),
        body = getString(3),
        isActive = forceActive ?: (getInt(4) == 1),
        updatedAt = getLong(5),
    )

    private fun bump() {
        revision.value = revision.value + 1
    }
}
