package com.veluneagent.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SSE 行解析是整条链路里最容易出错、又最难在真机复现的地方：
 * 各家兼容端点的字段差异全靠这里兜住，所以必须有测试。
 */
class SseParseTest {

    @Test
    fun `标准增量帧`() {
        val chunk = parseSseLine("""data: {"choices":[{"delta":{"content":"你好"}}]}""")
        assertNotNull(chunk)
        assertEquals("你好", chunk!!.delta)
        assertTrue(!chunk.done && chunk.error == null && !chunk.hasUsage)
    }

    @Test
    fun `结束标记`() {
        val chunk = parseSseLine("data: [DONE]")
        assertNotNull(chunk)
        assertTrue(chunk!!.done)
    }

    @Test
    fun `带用量帧`() {
        val chunk = parseSseLine(
            """data: {"choices":[],"usage":{"prompt_tokens":12,"completion_tokens":34}}""",
        )
        assertNotNull(chunk)
        assertTrue(chunk!!.hasUsage)
        assertEquals(12, chunk.promptTokens)
        assertEquals(34, chunk.completionTokens)
    }

    @Test
    fun `中途错误帧`() {
        val chunk = parseSseLine("""data: {"error":{"message":"insufficient balance"}}""")
        assertNotNull(chunk)
        assertEquals("insufficient balance", chunk!!.error)
    }

    @Test
    fun `非 data 行被忽略`() {
        assertNull(parseSseLine("event: message"))
        assertNull(parseSseLine(": keep-alive"))
        assertNull(parseSseLine(""))
        assertNull(parseSseLine("data:"))
    }

    @Test
    fun `非法 JSON 不抛异常`() {
        assertNull(parseSseLine("data: {not json"))
    }

    @Test
    fun `只有 finish_reason 的帧不产生事件`() {
        val chunk = parseSseLine("""data: {"choices":[{"delta":{},"finish_reason":"stop"}]}""")
        assertNotNull(chunk)
        assertTrue(chunk!!.delta.isEmpty() && !chunk.hasUsage && !chunk.done)
    }

    @Test
    fun `content 为显式 null 时不产出文字`() {
        // 工具调用帧的典型形状：content 是 JSON null，只有 tool_calls 有值。
        // org.json 的 optString 会把 null 转成字符串 "null"，早期版本因此整段回答都是 nullnullnull。
        val chunk = parseSseLine(
            """data: {"choices":[{"delta":{"content":null,"tool_calls":[{"index":0,"id":"c1","type":"function","function":{"name":"now","arguments":"{}"}}]}}]}""",
        )
        assertNotNull(chunk)
        assertTrue(chunk!!.delta.isEmpty())
        assertEquals("now", chunk.toolDeltas.single().name)
    }

    @Test
    fun `reasoning 为 null 时不产出思考文本`() {
        val chunk = parseSseLine(
            """data: {"choices":[{"delta":{"reasoning_content":null,"content":"答案是 2"}}]}""",
        )
        assertNotNull(chunk)
        assertTrue(chunk!!.reasoning.isEmpty())
        assertEquals("答案是 2", chunk.delta)
    }

    @Test
    fun `finish_reason 为 null 时不当作结束原因`() {
        val chunk = parseSseLine("""data: {"choices":[{"delta":{"content":"a"},"finish_reason":null}]}""")
        assertNotNull(chunk)
        assertNull(chunk!!.finishReason)
    }

    @Test
    fun `解析 tool_calls 增量分片`() {
        val first = parseSseLine(
            """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"calc","arguments":"{\"expr"}}]}}]}""",
        )
        assertNotNull(first)
        val delta = first!!.toolDeltas.single()
        assertEquals(0, delta.index)
        assertEquals("call_1", delta.id)
        assertEquals("calc", delta.name)
        assertEquals("{\"expr", delta.argumentsFragment)
    }

    @Test
    fun `解析续片只有 arguments`() {
        val chunk = parseSseLine(
            """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"ession\":\"1+1\"}"}}]}}]}""",
        )
        assertNotNull(chunk)
        val delta = chunk!!.toolDeltas.single()
        assertEquals(0, delta.index)
        assertNull(delta.name)
        assertTrue(delta.argumentsFragment!!.startsWith("ession"))
    }

    @Test
    fun `finish_reason 与用量同时出现时都保留`() {
        val chunk = parseSseLine(
            """data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":9,"completion_tokens":4}}""",
        )
        assertNotNull(chunk)
        assertEquals("tool_calls", chunk!!.finishReason)
        assertEquals(9, chunk.promptTokens)
        assertEquals(4, chunk.completionTokens)
    }

    @Test
    fun `解析思考内容 reasoning_content`() {
        val chunk = parseSseLine(
            """data: {"choices":[{"delta":{"reasoning_content":"先看需求"}}]}""",
        )
        assertNotNull(chunk)
        assertEquals("先看需求", chunk!!.reasoning)
        assertTrue(chunk.delta.isEmpty())
    }

    @Test
    fun `兼容 reasoning 字段命名`() {
        val chunk = parseSseLine("""data: {"choices":[{"delta":{"reasoning":"思考中"}}]}""")
        assertNotNull(chunk)
        assertEquals("思考中", chunk!!.reasoning)
    }

    @Test
    fun `思考与正文可以同时下发`() {
        val chunk = parseSseLine(
            """data: {"choices":[{"delta":{"reasoning_content":"嗯","content":"答案是 2"}}]}""",
        )
        assertNotNull(chunk)
        assertEquals("嗯", chunk!!.reasoning)
        assertEquals("答案是 2", chunk.delta)
    }

    @Test
    fun `兼容 content 为空的推理帧`() {
        val chunk = parseSseLine("""data: {"choices":[{"delta":{"reasoning_content":"思考中"}}]}""")
        assertNotNull(chunk)
        assertTrue(chunk!!.delta.isEmpty())
    }
}
