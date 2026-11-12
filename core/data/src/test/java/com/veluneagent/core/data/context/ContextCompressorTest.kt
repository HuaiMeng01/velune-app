package com.veluneagent.core.data.context

import com.veluneagent.core.data.ChatMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 压缩逻辑的单测。
 *
 * 这类代码的 bug 不会抛异常，只会「悄悄少了一段上下文」——所以必须用测试把
 * 「什么时候压、压完剩什么、压失败退化成什么」钉死。
 */
class ContextCompressorTest {

    private class FakeStore : SummaryRepository {
        val saved = mutableMapOf<Long, ConversationSummary>()
        var saveCalls = 0

        override suspend fun get(conversationId: Long): ConversationSummary? = saved[conversationId]

        override suspend fun save(conversationId: Long, summary: String, coveredCount: Int) {
            saveCalls++
            saved[conversationId] = ConversationSummary(
                conversationId = conversationId,
                summary = summary,
                coveredCount = coveredCount,
                updatedAt = 0,
            )
        }

        override suspend fun clear(conversationId: Long) {
            saved.remove(conversationId)
        }
    }

    private fun messages(count: Int, chars: Int = 40): List<ChatMessage> =
        (0 until count).map { index ->
            ChatMessage(
                role = if (index % 2 == 0) "user" else "assistant",
                text = "m$index-" + "x".repeat(chars),
            )
        }

    @Test
    fun `未达阈值时原样返回且不调用摘要`() = runBlocking {
        val store = FakeStore()
        var summarized = 0
        val outcome = ContextCompressor(store).prepare(
            conversationId = 1,
            raw = messages(6),
            triggerTokens = 10_000,
            keepRecent = 2,
            summarize = { summarized++; "摘要" },
        )

        assertEquals(0, summarized)
        assertEquals(6, outcome.messages.size)
        assertEquals(0, outcome.foldedCount)
        assertFalse(outcome.summaryUsed)
        assertEquals(0, store.saveCalls)
    }

    @Test
    fun `消息数不超过保留条数时不压缩`() = runBlocking {
        val store = FakeStore()
        val outcome = ContextCompressor(store).prepare(
            conversationId = 1,
            raw = messages(3),
            triggerTokens = 1,
            keepRecent = 5,
            summarize = { "不该被调用" },
        )
        assertEquals(3, outcome.messages.size)
        assertEquals(0, outcome.foldedCount)
    }

    @Test
    fun `超过阈值时折叠旧消息并注入摘要`() = runBlocking {
        val store = FakeStore()
        val raw = messages(12, chars = 200)
        val outcome = ContextCompressor(store).prepare(
            conversationId = 7,
            raw = raw,
            triggerTokens = 50,
            keepRecent = 3,
            summarize = { "【目标】压缩测试" },
        )

        assertTrue(outcome.summaryUsed)
        assertFalse(outcome.degraded)
        assertEquals(9, outcome.foldedCount)
        assertEquals(4, outcome.messages.size) // 1 条摘要 + 最近 3 条原文
        assertEquals("system", outcome.messages.first().role)
        assertTrue(outcome.messages.first().text.contains("【目标】压缩测试"))
        assertEquals(raw.takeLast(3), outcome.messages.drop(1))
        assertTrue(outcome.estimatedTokensAfter < outcome.estimatedTokensBefore)
        assertEquals(9, store.saved.getValue(7L).coveredCount)
    }

    @Test
    fun `摘要失败时退化到窗口裁剪而不是报错`() = runBlocking {
        val store = FakeStore()
        val raw = messages(10, chars = 200)
        val outcome = ContextCompressor(store).prepare(
            conversationId = 2,
            raw = raw,
            triggerTokens = 10,
            keepRecent = 4,
            summarize = { null },
        )

        assertTrue(outcome.degraded)
        assertFalse(outcome.summaryUsed)
        assertEquals(4, outcome.messages.size)
        assertEquals(raw.takeLast(4), outcome.messages)
        assertEquals(0, store.saveCalls)
    }

    @Test
    fun `已有摘要会被带入摘要请求并要求合并`() = runBlocking {
        val store = FakeStore()
        store.saved[3L] = ConversationSummary(3L, "【目标】上一轮的旧摘要", 5, 0)
        var promptText = ""

        ContextCompressor(store).prepare(
            conversationId = 3,
            raw = messages(8, chars = 300),
            triggerTokens = 10,
            keepRecent = 2,
            summarize = { prompt ->
                promptText = prompt.joinToString("\n") { it.text }
                "新摘要"
            },
        )

        assertTrue(promptText.contains("上一轮的旧摘要"))
        assertTrue(promptText.contains("必须与新材料合并"))
        assertEquals(1, store.saveCalls)
    }

    @Test
    fun `累计覆盖条数等于旧覆盖量加本次新折叠`() = runBlocking {
        val store = FakeStore()
        store.saved[4L] = ConversationSummary(4L, "旧摘要", coveredCount = 4, updatedAt = 0)

        ContextCompressor(store).prepare(
            conversationId = 4,
            raw = messages(10, chars = 300),
            triggerTokens = 10,
            keepRecent = 2,
            summarize = { "新摘要" },
        )

        // head = 8 条，其中前 4 条已折叠；本次折叠 4 条 → 累计 8
        assertEquals(8, store.saved.getValue(4L).coveredCount)
    }

    @Test
    fun `已折叠过的消息不会被重复摘要`() = runBlocking {
        val store = FakeStore()
        store.saved[9L] = ConversationSummary(9L, "旧摘要", coveredCount = 8, updatedAt = 0)
        var summarizeCalls = 0

        val outcome = ContextCompressor(store).prepare(
            conversationId = 9,
            raw = messages(10, chars = 300),
            triggerTokens = 10,
            keepRecent = 2,
            summarize = { summarizeCalls++; "不该被调用" },
        )

        // head = 8 条，已全部折叠过 → 不再调模型，直接复用摘要
        assertEquals(0, summarizeCalls)
        assertTrue(outcome.summaryUsed)
        assertEquals(3, outcome.messages.size) // 摘要 + 最近 2 条
        assertTrue(outcome.messages.first().text.contains("旧摘要"))
    }

    @Test
    fun `新增量不足阈值时复用摘要且不丢消息`() = runBlocking {
        val store = FakeStore()
        store.saved[11L] = ConversationSummary(11L, "旧摘要", coveredCount = 2, updatedAt = 0)
        var summarizeCalls = 0

        val raw = messages(8, chars = 10)
        val outcome = ContextCompressor(store).prepare(
            conversationId = 11,
            raw = raw,
            triggerTokens = 5_000,
            keepRecent = 2,
            summarize = { summarizeCalls++; "不该被调用" },
        )

        assertEquals(0, summarizeCalls)
        assertTrue(outcome.summaryUsed)
        // 摘要 + 未折叠的 4 条 + 最近 2 条 = 7
        assertEquals(7, outcome.messages.size)
        assertEquals(raw.takeLast(2), outcome.messages.takeLast(2))
    }

    @Test
    fun `空历史直接返回空`() = runBlocking {
        val outcome = ContextCompressor(FakeStore()).prepare(
            conversationId = 1,
            raw = emptyList(),
            triggerTokens = 1,
            keepRecent = 2,
            summarize = { null },
        )
        assertTrue(outcome.messages.isEmpty())
        assertFalse(outcome.summaryUsed)
    }

    @Test
    fun `保留条数小于下限时按两条处理`() = runBlocking {
        val store = FakeStore()
        val raw = messages(6, chars = 300)
        val outcome = ContextCompressor(store).prepare(
            conversationId = 5,
            raw = raw,
            triggerTokens = 10,
            keepRecent = 0,
            summarize = { "摘要" },
        )
        // keepRecent 被抬到 2，因此保留 2 条原文
        assertNotNull(outcome.messages.lastOrNull())
        assertEquals(3, outcome.messages.size)
    }
}
