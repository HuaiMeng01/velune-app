package com.veluneagent.core.data.agent

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.veluneagent.core.data.VeluneDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 一次运行的收尾状态，落库用字符串，方便排查时直接读。 */
enum class RunStatus(val wire: String) {
    Completed("completed"),
    BudgetExceeded("budget_exceeded"),
    LoopDetected("loop_detected"),
    TooManyFailures("too_many_failures"),
    Cancelled("cancelled"),
    Error("error"),
    ;

    companion object {
        fun fromStopReason(reason: StopReason): RunStatus = when (reason) {
            StopReason.Completed -> Completed
            StopReason.BudgetExceeded -> BudgetExceeded
            StopReason.LoopDetected -> LoopDetected
            StopReason.TooManyFailures -> TooManyFailures
            StopReason.UserCancelled -> Cancelled
            StopReason.Error -> Error
        }
    }
}

/**
 * Agent 运行记录与 scope 审计。
 *
 * 存在的意义只有一个：出问题时能回答「它到底做了什么」。
 * 没有它，「Agent 把某接口刷爆了」这种事故就只能靠猜。
 */
class AgentRunStore(context: Context) : ScopeAuditor {

    private val helper = VeluneDb.of(context)

    suspend fun startRun(runId: String, conversationId: Long) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("id", runId)
            put("conversation_id", conversationId)
            put("started_at", System.currentTimeMillis())
            put("status", "running")
        }
        runCatching { helper.writableDatabase.insertWithOnConflict(
                "agent_runs",
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE,
            ) }
        Unit
    }

    suspend fun recordStep(
        runId: String,
        index: Int,
        type: String,
        title: String,
        inputJson: String? = null,
        outputJson: String? = null,
        status: String = "ok",
        durationMs: Long = 0,
        errorCode: String? = null,
    ) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("id", "$runId-$index-$type")
            put("run_id", runId)
            put("idx", index)
            put("type", type)
            put("title", title.take(200))
            put("input_json", inputJson?.take(4_000))
            put("output_json", outputJson?.take(4_000))
            put("status", status)
            put("started_at", System.currentTimeMillis())
            put("duration_ms", durationMs)
            put("error_code", errorCode)
        }
        runCatching { helper.writableDatabase.insertWithOnConflict(
                "agent_steps",
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE,
            ) }
        Unit
    }

    suspend fun finishRun(
        runId: String,
        status: RunStatus,
        steps: Int,
        toolCalls: Int,
        promptTokens: Int,
        completionTokens: Int,
        error: String? = null,
    ) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("finished_at", System.currentTimeMillis())
            put("status", status.wire)
            put("steps", steps)
            put("tool_calls", toolCalls)
            put("prompt_tokens", promptTokens)
            put("completion_tokens", completionTokens)
            put("error", error?.take(500))
        }
        runCatching {
            helper.writableDatabase.update("agent_runs", values, "id = ?", arrayOf(runId))
        }
        Unit
    }

    override fun record(
        pluginId: String,
        scope: String,
        target: String,
        allowed: Boolean,
        runId: String?,
    ) {
        // 审计是旁路：同步写一行，失败直接吞掉，绝不因为写日志失败让工具调用失败
        runCatching {
            val values = ContentValues().apply {
                put("ts", System.currentTimeMillis())
                put("plugin_id", pluginId)
                put("scope", scope)
                put("target", target.take(200))
                put("result", if (allowed) "ALLOWED" else "DENIED")
                put("run_id", runId)
            }
            helper.writableDatabase.insert("scope_audit", null, values)
        }
    }

    /**
     * 某个会话的全部运行记录，新的在前。
     *
     * 之前 `agent_runs` / `agent_steps` 只写不读，用户看不到「这轮 Agent 到底做了什么」。
     * 这个查询是轨迹回放页的唯一数据源。
     */
    suspend fun listRuns(conversationId: Long, limit: Int = 50): List<RunSummary> =
        withContext(Dispatchers.IO) {
            val list = mutableListOf<RunSummary>()
            helper.readableDatabase.rawQuery(
                "SELECT id, started_at, finished_at, status, steps, tool_calls, " +
                    "prompt_tokens, completion_tokens, error FROM agent_runs " +
                    "WHERE conversation_id = ? ORDER BY started_at DESC LIMIT ?",
                arrayOf(conversationId.toString(), limit.toString()),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    list += RunSummary(
                        id = cursor.getString(0),
                        startedAt = cursor.getLong(1),
                        finishedAt = if (cursor.isNull(2)) null else cursor.getLong(2),
                        status = cursor.getString(3),
                        steps = cursor.getInt(4),
                        toolCalls = cursor.getInt(5),
                        promptTokens = cursor.getInt(6),
                        completionTokens = cursor.getInt(7),
                        error = cursor.getString(8),
                    )
                }
            }
            list
        }

    /** 最近一次运行的工具轨迹，供调试面板展示。 */
    suspend fun recentSteps(runId: String): List<RunStep> = withContext(Dispatchers.IO) {
        val list = mutableListOf<RunStep>()
        helper.readableDatabase.rawQuery(
            "SELECT idx, type, title, status, duration_ms, error_code FROM agent_steps " +
                "WHERE run_id = ? ORDER BY idx ASC",
            arrayOf(runId),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list += RunStep(
                    index = cursor.getInt(0),
                    type = cursor.getString(1),
                    title = cursor.getString(2),
                    status = cursor.getString(3),
                    durationMs = cursor.getLong(4),
                    errorCode = cursor.getString(5),
                )
            }
        }
        list
    }

    data class RunStep(
        val index: Int,
        val type: String,
        val title: String,
        val status: String,
        val durationMs: Long,
        val errorCode: String?,
    )

    data class RunSummary(
        val id: String,
        val startedAt: Long,
        val finishedAt: Long?,
        val status: String,
        val steps: Int,
        val toolCalls: Int,
        val promptTokens: Int,
        val completionTokens: Int,
        val error: String?,
    ) {
        val totalTokens: Int get() = promptTokens + completionTokens
        val durationMs: Long get() = finishedAt?.let { (it - startedAt).coerceAtLeast(0) } ?: 0
    }
}
