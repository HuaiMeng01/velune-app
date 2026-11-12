package com.veluneagent.core.data.mcp

import com.veluneagent.core.data.agent.SimpleHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * 一个 MCP 服务器的 JSON-RPC 客户端（Streamable HTTP）。
 *
 * 只实现协议里 Agent 真正需要的三段：`initialize` → `tools/list` → `tools/call`。
 * 资源（resources）、提示模板（prompts）、采样（sampling）都没做 —— 它们不影响
 * "让模型多会用几个工具"这个目标，做全反而是负担。
 *
 * 响应可能是普通 JSON，也可能是 SSE（`text/event-stream`），解析见 [McpResponse]。
 */
class McpClient(private val server: McpServer) {

    @Volatile
    private var sessionId: String? = null

    /**
     * 服务端在 `initialize` 里协商出的协议版本。
     *
     * 不能一直发自己写死的版本：服务端可能只支持更新的版本，反向回传它接受的版本，
     * 后续请求的 `MCP-Protocol-Version` 头必须跟着协商结果走，否则严格实现会 400。
     */
    @Volatile
    private var protocolVersion: String = DefaultProtocolVersion

    /** 并发调用时 id 必须唯一：重号会让服务端把两条响应搞混。 */
    private val nextId = AtomicInteger(1)

    /** 配置是否与当前客户端一致。仅比较影响连接的部分（名称、启用状态不影响）。 */
    fun matches(other: McpServer): Boolean =
        server.url == other.url && server.headers == other.headers

    /** 握手 + 拉工具列表。连接失败时错误信息要能直接展示给用户。 */
    fun connect(): Result<List<McpTool>> {
        initialize().getOrElse { return Result.failure(it) }
        // 握手后必须补发这条通知：部分服务器在收到它之前会拒绝 tools/list
        runCatching { notifyInitialized() }
        return listTools()
    }

    fun initialize(): Result<Unit> {
        val params = JSONObject().apply {
            put("protocolVersion", DefaultProtocolVersion)
            put("capabilities", JSONObject())
            put("clientInfo", JSONObject().apply {
                put("name", "Velune")
                put("version", ClientVersion)
            })
        }
        return call("initialize", params).map { result ->
            val negotiated = result.optString("protocolVersion")
            if (negotiated.isNotBlank()) protocolVersion = negotiated
        }
    }

    /**
     * 拉取工具清单，**跟随 `nextCursor` 翻页**。
     *
     * 不翻页会在工具数量超过服务端单页上限（常见 100）时静默少一截 ——
     * 用户看到的是"这个服务器只有 100 个工具"，而不是"有一页没拿到"。
     */
    fun listTools(): Result<List<McpTool>> {
        val collected = mutableListOf<McpTool>()
        var cursor: String? = null
        var pages = 0
        do {
            val params = JSONObject().apply { cursor?.let { put("cursor", it) } }
            val page = call("tools/list", params).getOrElse { return Result.failure(it) }
            val array = page.optJSONArray("tools") ?: JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val name = obj.optString("name").takeIf { it.isNotBlank() } ?: continue
                collected += McpTool(
                    serverId = server.id,
                    serverName = server.name,
                    name = name,
                    description = obj.optString("description").take(400),
                    // schema 缺失时给一个空对象结构，而不是空串：空串会让模型看到非法 JSON
                    inputSchema = obj.optJSONObject("inputSchema")?.toString()
                        ?: """{"type":"object","properties":{}}""",
                )
            }
            val next = page.optString("nextCursor").takeIf { it.isNotBlank() }
            // 服务端把同一个 cursor 反复吐回来时立刻收手，否则会拉 20 页重复工具
            if (next != null && next == cursor) break
            cursor = next
            pages++
        } while (cursor != null && pages < MaxToolPages)
        return Result.success(collected)
    }

    fun callTool(toolName: String, argumentsJson: String): Result<McpToolResult> {
        val args = runCatching { JSONObject(argumentsJson) }.getOrElse { JSONObject() }
        val params = JSONObject().apply {
            put("name", toolName)
            put("arguments", args)
        }
        return call("tools/call", params).map { result ->
            val content = result.optJSONArray("content")
            val text = buildString {
                if (content != null) {
                    for (i in 0 until content.length()) {
                        val block = content.optJSONObject(i) ?: continue
                        when (block.optString("type")) {
                            "text" -> append(block.optString("text"))
                            // 图片/音频等非文本块：给出类型占位，而不是静默丢掉整个结果
                            else -> append("[${block.optString("type").ifBlank { "block" }}]")
                        }
                    }
                } else {
                    // 少数服务器直接把结果放在 structuredContent
                    append(result.optJSONObject("structuredContent")?.toString().orEmpty())
                }
            }
            McpToolResult(text = text.trim(), isError = result.optBoolean("isError", false))
        }
    }

    /**
     * 终止会话（协议里的 `DELETE`）。
     *
     * 不终止的话，每次"改配置 / 刷新 / 删服务器"都会在服务端留下一个不会被回收的会话。
     * 用后台线程发：调用方可能在主线程（删除服务器），不能阻塞 UI；失败也无所谓 ——
     * 服务端本来就有会话过期机制。
     */
    fun close() {
        val sid = sessionId ?: return
        sessionId = null
        val headers = buildMap {
            put("Mcp-Session-Id", sid)
            put("MCP-Protocol-Version", protocolVersion)
            server.headers.forEach { (key, value) -> put(key, value) }
        }
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                SimpleHttp.request(
                    method = "DELETE",
                    url = server.url,
                    headers = headers,
                    timeoutMs = 5_000,
                    allowAuthHeaders = true,
                    maxBytes = 4_096,
                )
            }
        }
    }

    // ---- 传输 ----

    private fun call(method: String, params: JSONObject): Result<JSONObject> {
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", nextId.getAndIncrement())
            put("method", method)
            put("params", params)
        }
        val response = post(payload.toString())
            .getOrElse { return Result.failure(it) }
        response.optJSONObject("error")?.let { error ->
            return Result.failure(
                IllegalStateException(
                    error.optString("message").ifBlank { "服务器返回错误（${error.optInt("code")}）" },
                ),
            )
        }
        return Result.success(response.optJSONObject("result") ?: JSONObject())
    }

    private fun notifyInitialized() {
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("method", "notifications/initialized")
        }
        // 通知没有响应体，服务器可能回 202 或直接断开，失败也无所谓
        post(payload.toString())
    }

    private fun post(body: String): Result<JSONObject> {
        val headers = buildMap {
            put("Accept", "application/json, text/event-stream")
            put("MCP-Protocol-Version", protocolVersion)
            sessionId?.let { put("Mcp-Session-Id", it) }
            server.headers.forEach { (key, value) -> put(key, value) }
        }
        val outcome = SimpleHttp.request(
            method = "POST",
            url = server.url,
            headers = headers,
            jsonBody = body,
            timeoutMs = RequestTimeoutMs,
            // MCP 服务器由用户自己配置，允许带 Authorization
            allowAuthHeaders = true,
            // 工具结果可能是整页网页，32KB 明显不够（进上下文前还会再裁一次）
            maxBytes = 512 * 1024,
        )
        if (!outcome.ok) {
            val detail = outcome.error ?: "HTTP ${outcome.code}"
            return Result.failure(IllegalStateException("连接 ${server.name} 失败：$detail"))
        }
        outcome.headers["mcp-session-id"]?.let { sessionId = it }
        val json = McpResponse.extractJson(outcome.body)
            ?: return Result.failure(IllegalStateException("${server.name} 的响应不是合法 JSON-RPC"))
        return Result.success(json)
    }

    private companion object {
        const val DefaultProtocolVersion = "2025-03-26"
        const val ClientVersion = "0.5.4"

        /**
         * 单次请求上限。要大于 Agent 侧给 MCP 工具留的执行预算（见 `McpRegistry.specs`），
         * 否则用户看到的是"工具超时"，而不是网络层给出的具体原因。
         */
        const val RequestTimeoutMs = 30_000

        /** 翻页上限：防止服务端返回一个永远不为空的 cursor 把客户端拖死。 */
        const val MaxToolPages = 20
    }
}
