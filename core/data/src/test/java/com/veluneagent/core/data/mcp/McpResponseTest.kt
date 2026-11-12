package com.veluneagent.core.data.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MCP 响应解析。
 *
 * 这段是整个 MCP 链路里最容易出错、又最难在真机上排查的一环：
 * 出错的表现不是报错，而是"工具调用返回空"或者"调用一失败就报 JSON 不合法"。
 * 所以三种响应形态必须有测试锁死。
 */
class McpResponseTest {

    @Test
    fun `纯 JSON 响应直接解析`() {
        val body = """{"jsonrpc":"2.0","id":1,"result":{"tools":[]}}"""
        val json = McpResponse.extractJson(body)
        assertEquals(1, json?.optInt("id"))
        assertTrue(json?.has("result") == true)
    }

    @Test
    fun `SSE 里通知在前结果在后，取结果`() {
        val body = """
            event: message
            data: {"jsonrpc":"2.0","method":"notifications/message","params":{}}

            event: message
            data: {"jsonrpc":"2.0","id":2,"result":{"content":[{"type":"text","text":"ok"}]}}

        """.trimIndent()
        val json = McpResponse.extractJson(body)
        assertEquals(2, json?.optInt("id"))
        assertEquals("ok", json?.optJSONObject("result")
            ?.optJSONArray("content")?.optJSONObject(0)?.optString("text"))
    }

    @Test
    fun `SSE 里结果在前通知在后，仍然取结果`() {
        // 早期实现取"最后一条 data 行"，这种顺序会拿到通知，表现为工具返回空
        val body = """
            data: {"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"done"}]}}

            data: {"jsonrpc":"2.0","method":"notifications/progress","params":{}}

        """.trimIndent()
        val json = McpResponse.extractJson(body)
        assertEquals(3, json?.optInt("id"))
        assertTrue(json?.has("result") == true)
    }

    @Test
    fun `SSE 里错误响应也能取到`() {
        val body = """
            data: {"jsonrpc":"2.0","method":"notifications/message"}

            data: {"jsonrpc":"2.0","id":4,"error":{"code":-32601,"message":"Method not found"}}

        """.trimIndent()
        val json = McpResponse.extractJson(body)
        assertEquals(-32601, json?.optJSONObject("error")?.optInt("code"))
    }

    @Test
    fun `单条消息拆成多个 data 行时按 SSE 规则拼接`() {
        val body = """
            data: {
            data:   "jsonrpc": "2.0",
            data:   "id": 5,
            data:   "result": {"ok": true}
            data: }

        """.trimIndent()
        val json = McpResponse.extractJson(body)
        assertEquals(5, json?.optInt("id"))
        assertTrue(json?.optJSONObject("result")?.optBoolean("ok") == true)
    }

    @Test
    fun `HTML 错误页与空响应返回 null`() {
        assertNull(McpResponse.extractJson(""))
        assertNull(McpResponse.extractJson("   "))
        assertNull(McpResponse.extractJson("<html><body>502 Bad Gateway</body></html>"))
    }

    @Test
    fun `只有通知没有响应时退回最后一条候选`() {
        val body = """
            data: {"jsonrpc":"2.0","method":"notifications/initialized"}

        """.trimIndent()
        val json = McpResponse.extractJson(body)
        assertEquals("notifications/initialized", json?.optString("method"))
    }
}
