package com.veluneagent.core.data.mcp

/**
 * 一个 MCP 服务器配置。
 *
 * 只支持 **远程 HTTP（Streamable HTTP）** 传输。stdio 传输需要子进程运行时，
 * 在 Android 上等于自带一个 Node/Python 解释器 —— 那是另一个项目的体量，
 * 也会让"装一个 MCP 服务器"变成"先装一套运行环境"。
 *
 * [headers] 用于 `Authorization: Bearer …` 这类鉴权。MCP 的请求头由用户显式填写，
 * 与插件不同：插件永远不允许带认证头（防止插件窃取凭据），MCP 是用户自己配的服务器。
 */
data class McpServer(
    val id: String,
    val name: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
)

/** 从服务器 `tools/list` 拿到的一个工具。 */
data class McpTool(
    val serverId: String,
    val serverName: String,
    /** 服务器侧的原始工具名，调用时要原样回传 */
    val name: String,
    val description: String,
    /** 服务器返回的 inputSchema，原样转成字符串交给模型 */
    val inputSchema: String,
) {
    /**
     * 注册到 Agent 的工具名。
     *
     * 加前缀的原因：不同服务器可能都有 `search`，直接撞名会让后注册的覆盖前一个。
     * 双下划线是 OpenAI 工具名的常见分隔符，模型也更容易照着原样拼。
     */
    val qualifiedName: String get() = "mcp__${serverId}__$name"
}

/** `tools/call` 的结果，已把 content 数组拼成纯文本。 */
data class McpToolResult(
    val text: String,
    val isError: Boolean,
)
