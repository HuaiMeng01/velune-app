package com.veluneagent.core.data.context

import android.content.ContentValues
import android.content.Context
import com.veluneagent.core.data.ChatMessage
import com.veluneagent.core.data.TokenEstimator
import com.veluneagent.core.data.VeluneDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ConversationSummary(
    val conversationId: Long,
    val summary: String,
    val coveredCount: Int,
    val updatedAt: Long,
)

/** 摘要仓库。抽接口是为了让压缩逻辑能在纯 JVM 单测里跑，不必起 Android Context。 */
interface SummaryRepository {
    suspend fun get(conversationId: Long): ConversationSummary?
    suspend fun save(conversationId: Long, summary: String, coveredCount: Int)
    suspend fun clear(conversationId: Long)
}

/** 摘要落库。一个会话只留最新一份：旧摘要已经被新摘要吸收，留着只会造成两套事实。 */
class SummaryStore(context: Context) : SummaryRepository {

    private val helper = VeluneDb.of(context)

    override suspend fun get(conversationId: Long): ConversationSummary? = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT summary, covered_count, updated_at FROM context_summaries WHERE conversation_id = ?",
            arrayOf(conversationId.toString()),
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                ConversationSummary(
                    conversationId = conversationId,
                    summary = cursor.getString(0).orEmpty(),
                    coveredCount = cursor.getInt(1),
                    updatedAt = cursor.getLong(2),
                )
            } else {
                null
            }
        }
    }

    override suspend fun save(conversationId: Long, summary: String, coveredCount: Int) =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("conversation_id", conversationId)
                put("summary", summary)
                put("covered_count", coveredCount)
                put("updated_at", System.currentTimeMillis())
            }
            helper.writableDatabase.insertWithOnConflict(
                "context_summaries",
                null,
                values,
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
            )
            Unit
        }

    override suspend fun clear(conversationId: Long) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete(
            "context_summaries",
            "conversation_id = ?",
            arrayOf(conversationId.toString()),
        )
        Unit
    }
}

/**
 * 上下文压缩。
 *
 * 分两层，对应 08 章的设计：
 * - L1 滑动窗口：只保留最近 N 条原文（无论如何都会执行，是兜底）；
 * - L2 摘要压缩：把窗口外的旧消息交给模型压成结构化摘要，替换原文，并落库复用。
 *
 * 三条不可协商的约束：
 * 1. **压缩失败不能阻断发送**：摘要调用失败 → 回退到纯窗口裁剪，并标记 degraded；
 * 2. **摘要只增不减地覆盖旧摘要**：新摘要必须吸收旧摘要内容，否则早期事实会丢；
 * 3. **触发看 token 而不是条数**：一条 5KB 的工具结果抵得上几十条短消息。
 */
class ContextCompressor(private val store: SummaryRepository) {

    data class Outcome(
        val messages: List<ChatMessage>,
        /** 本次折叠进摘要的消息条数。0 表示没有压缩。 */
        val foldedCount: Int,
        val summaryUsed: Boolean,
        /** 摘要生成失败、退化为窗口裁剪。 */
        val degraded: Boolean,
        val estimatedTokensBefore: Int,
        val estimatedTokensAfter: Int,
    )

    /**
     * @param raw 完整历史（已剔除占位消息），不做任何裁剪
     * @param triggerTokens 待折叠部分的 token 超过该值才值得压缩
     * @param keepRecent 保留原文的最近条数
     * @param summarize 摘要生成器；返回 null 表示失败
     */
    suspend fun prepare(
        conversationId: Long,
        raw: List<ChatMessage>,
        triggerTokens: Int,
        keepRecent: Int,
        summarize: suspend (prompt: List<ChatMessage>) -> String?,
    ): Outcome {
        val before = TokenEstimator.estimateMessages(raw)
        if (raw.isEmpty()) {
            return Outcome(raw, 0, false, false, 0, 0)
        }

        val keep = keepRecent.coerceAtLeast(2)
        val head = raw.dropLast(keep)
        val tail = raw.takeLast(keep)
        if (head.isEmpty()) {
            return Outcome(raw, 0, false, false, before, before)
        }

        val existing = store.get(conversationId)
        // 已折叠过的消息不再重复折叠：coveredCount 记录的是「从最早一条起已并入摘要的条数」
        val covered = (existing?.coveredCount ?: 0).coerceIn(0, head.size)
        val fresh = head.drop(covered)

        if (existing != null && TokenEstimator.estimateMessages(fresh) < triggerTokens) {
            // 没有新的量需要压：直接复用旧摘要，新增部分按原文带上，绝不为了省事丢消息
            val folded = listOf(summaryMessage(existing.summary)) + fresh + tail
            return Outcome(
                messages = folded,
                foldedCount = 0,
                summaryUsed = true,
                degraded = false,
                estimatedTokensBefore = before,
                estimatedTokensAfter = TokenEstimator.estimateMessages(folded),
            )
        }

        val headTokens = TokenEstimator.estimateMessages(head)
        if (existing == null && headTokens < triggerTokens) {
            // 首次压缩且还没到量级：原样发送，避免每轮都多花一次摘要调用
            return Outcome(raw, 0, false, false, before, before)
        }

        val toFold = if (existing != null) fresh else head
        val summary = summarize(buildPrompt(existing?.summary, toFold))

        if (summary.isNullOrBlank()) {
            // 降级：只保留最近 N 条原文。宁可丢旧上下文，也不能让消息发不出去。
            return Outcome(
                messages = tail,
                foldedCount = head.size,
                summaryUsed = false,
                degraded = true,
                estimatedTokensBefore = before,
                estimatedTokensAfter = TokenEstimator.estimateMessages(tail),
            )
        }

        store.save(
            conversationId = conversationId,
            summary = summary,
            coveredCount = covered + toFold.size,
        )

        val folded = listOf(summaryMessage(summary)) + tail

        return Outcome(
            messages = folded,
            foldedCount = toFold.size,
            summaryUsed = true,
            degraded = false,
            estimatedTokensBefore = before,
            estimatedTokensAfter = TokenEstimator.estimateMessages(folded),
        )
    }

    private fun summaryMessage(summary: String) = ChatMessage(
        role = "system",
        text = "以下是本次对话更早内容的压缩摘要（原文已省略）：\n$summary",
    )

    /** 提供会话级摘要供召回/展示；没有压缩过则返回 null。 */
    suspend fun summaryOf(conversationId: Long): ConversationSummary? = store.get(conversationId)

    suspend fun reset(conversationId: Long) = store.clear(conversationId)

    private fun buildPrompt(existingSummary: String?, head: List<ChatMessage>): List<ChatMessage> {
        val transcript = buildString {
            head.forEach { message ->
                val role = when (message.role) {
                    "user" -> "用户"
                    "assistant" -> "助手"
                    "tool" -> "工具"
                    else -> message.role
                }
                // 单条截断：工具结果可能很长，摘要只需要结论
                append('[').append(role).append("] ")
                append(message.text.take(MaxCharsPerMessage))
                append('\n')
            }
        }.take(MaxTranscriptChars)

        val user = buildString {
            if (!existingSummary.isNullOrBlank()) {
                append("已有摘要（必须与新材料合并，不得丢失其中事实）：\n")
                append(existingSummary.take(MaxCharsPerMessage * 2))
                append("\n\n")
            }
            append("待压缩对话：\n")
            append(transcript)
        }

        return listOf(
            ChatMessage(role = "system", text = SummarizerPrompt),
            ChatMessage(role = "user", text = user),
        )
    }

    private companion object {
        const val MaxCharsPerMessage = 1_200
        const val MaxTranscriptChars = 14_000

        val SummarizerPrompt = """
            你是对话上下文压缩器。把给定对话压缩成一份可供后续继续对话使用的结构化摘要。

            必须保留：
            - 用户的目标与约束（尤其是明确说过的偏好、禁忌、格式要求）
            - 已确认的事实、数字、名称、路径
            - 已经执行过的动作及其结果（工具调用成功/失败都要写）
            - 尚未解决的问题

            禁止：编造未出现的信息；加入建议、评价或客套话；使用 Markdown 标题。

            输出严格按以下四行（没有内容写「无」），总长不超过 400 字：
            【目标】…
            【已确认】…
            【已做过】…
            【未决】…
        """.trimIndent()
    }
}
