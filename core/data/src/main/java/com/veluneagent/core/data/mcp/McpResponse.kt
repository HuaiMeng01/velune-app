package com.veluneagent.core.data.mcp

import org.json.JSONObject

/**
 * MCP 响应体的解析。
 *
 * 单独抽出来是因为这一段**纯逻辑、无 Android 依赖**，也是整条链路里最容易出错的一段：
 * Streamable HTTP 的响应可能是普通 JSON，也可能是 SSE 流，而 SSE 流里往往先来一条
 * `notifications/...` 再来结果。这段现在有单测覆盖（`McpResponseTest`）。
 */
internal object McpResponse {

    /**
     * 从响应体里取出 JSON-RPC 响应对象。
     *
     * 三条规则：
     * 1. 以 `{` 开头 → 整体就是 JSON；
     * 2. 否则按 SSE 处理：按空行切事件，事件内的多个 `data:` 行用换行拼接（SSE 规范）；
     * 3. 多个候选中**优先取带 `result` / `error` 的那个**，而不是"最后一条"。
     *
     * 第 3 条是相对早期实现的修正：服务器推 `notifications/...` 与结果的顺序并不固定，
     * 「取最后一条」在通知排在结果之后时会拿到通知，表现为"工具调用返回空"。
     */
    fun extractJson(body: String): JSONObject? {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("{")) return parse(trimmed)
        if (!trimmed.contains("data:")) return null

        val candidates = sseEvents(trimmed).mapNotNull(::parse)
        if (candidates.isEmpty()) return null
        return candidates.lastOrNull { it.has("result") || it.has("error") }
            ?: candidates.last()
    }

    /**
     * 按 SSE 规范切事件：空行分隔，事件内多个 `data:` 行以 `\n` 连接。
     *
     * `event:` / `id:` / `retry:` / 注释行对 JSON-RPC 没有意义，直接忽略。
     */
    private fun sseEvents(body: String): List<String> {
        val events = mutableListOf<String>()
        val buffer = StringBuilder()
        var hasData = false

        fun flush() {
            if (hasData) {
                events += buffer.toString()
                buffer.setLength(0)
                hasData = false
            }
        }

        body.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd('\r')
            when {
                line.isEmpty() -> flush()
                line.startsWith("data:") -> {
                    if (hasData) buffer.append('\n')
                    buffer.append(line.removePrefix("data:").trim())
                    hasData = true
                }
            }
        }
        flush()
        return events
    }

    private fun parse(payload: String): JSONObject? {
        val text = payload.trim()
        if (!text.startsWith("{")) return null
        return runCatching { JSONObject(text) }.getOrNull()
    }
}
