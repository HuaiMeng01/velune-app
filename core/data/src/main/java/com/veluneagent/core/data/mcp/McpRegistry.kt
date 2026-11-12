package com.veluneagent.core.data.mcp

import com.veluneagent.core.data.agent.ToolSource
import com.veluneagent.core.data.agent.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 所有 MCP 服务器的工具汇总。
 *
 * 工具列表是**运行时拉取**的，不是安装时固定下来的 —— 服务器升级后工具可能变多或变少，
 * 所以每次刷新都整体替换，不做增量合并。缓存只在内存里：进程重启后重新拉一次，
 * 避免用一份过期的工具列表去调用已经改了接口的服务器。
 */
class McpRegistry(private val store: McpStore) {

    /** 刷新结果：拿到多少工具、哪些服务器失败。UI 要把失败原因显示出来。 */
    data class RefreshReport(
        val toolCount: Int,
        val failures: List<String>,
    )

    @Volatile
    private var bindings: List<McpTool> = emptyList()

    /**
     * serverId → 已握手的客户端。
     *
     * 必须缓存：`Mcp-Session-Id` 是**按连接**下发的，refresh 时握手拿到的会话
     * 如果不在调用时复用，服务器会认为这是一次没有初始化的裸请求而拒绝。
     * 这一点在只看代码时很容易漏掉，真机上表现为"能列出工具、但一调用就 400"。
     */
    private val clients = java.util.concurrent.ConcurrentHashMap<String, McpClient>()

    fun tools(): List<McpTool> = bindings

    fun toolCountOf(serverId: String): Int = bindings.count { it.serverId == serverId }

    /**
     * 转成 Agent 能用的工具声明。
     *
     * `sideEffect` 一律为 true：MCP 协议里没有声明"这个工具是否只读"的字段，
     * 我们无从判断。当成有副作用处理，代价是多一次确认，收益是不会静默改掉用户的东西。
     *
     * [ToolSpec.origin] 必须写明来源：审批面板直接展示它，写"内置"会让用户以为
     * 这是 App 自带的能力，而实际执行方是他自己填的第三方服务器。
     */
    fun specs(): List<ToolSpec> = bindings.map { tool ->
        ToolSpec(
            name = tool.qualifiedName,
            displayName = "${tool.serverName} · ${tool.name}",
            description = tool.description.ifBlank { "来自 MCP 服务器「${tool.serverName}」的工具" },
            parametersJson = tool.inputSchema,
            sideEffect = true,
            // 远程工具可能很慢（抓网页、跑查询），给足时间但别无限等。
            // 引擎按这个值套工具级超时，必须略大于 McpClient 的单次请求上限，
            // 否则用户看到的是"工具超时"而不是网络层给出的具体原因。
            timeoutMs = 35_000,
            source = ToolSource.Mcp,
            origin = "MCP · ${tool.serverName}",
        )
    }

    fun find(qualifiedName: String): McpTool? =
        bindings.firstOrNull { it.qualifiedName == qualifiedName }

    suspend fun call(tool: McpTool, argumentsJson: String): Result<McpToolResult> =
        withContext(Dispatchers.IO) {
            val server = store.find(tool.serverId)
                ?: return@withContext Result.failure(
                    IllegalStateException("MCP 服务器「${tool.serverName}」已被删除"),
                )

            // 先用已有会话。失败（会话过期、服务器重启）再重建一次
            clients[tool.serverId]?.let { cached ->
                val attempt = cached.callTool(tool.name, argumentsJson)
                if (attempt.isSuccess) return@withContext attempt
                // 会话已经不可用：摘掉，别让下一次调用再撞一次同样的墙
                clients.remove(tool.serverId)
                cached.close()
            }

            val fresh = McpClient(server)
            fresh.connect().getOrElse { return@withContext Result.failure(it) }
            clients[server.id] = fresh
            fresh.callTool(tool.name, argumentsJson)
        }

    /**
     * 逐个服务器重新拉取工具列表。
     *
     * 三条约束：
     * 1. **并行**：串行握手时 N 台服务器最坏要等 N 次超时；
     * 2. **单个失败不影响其他**：一台挂了不该让所有 MCP 工具一起消失；
     * 3. **失败时保留上一次的工具**：一次网络抖动不该让整个进程丢掉这台服务器的工具。
     *    失败原因仍然回传 —— 静默失败会让用户以为"这个服务器没有工具"。
     */
    suspend fun refresh(): RefreshReport = withContext(Dispatchers.IO) {
        val servers = store.list().filter { it.enabled }
        val results = coroutineScope {
            servers.map { server -> async { server to bind(server) } }.awaitAll()
        }

        val collected = mutableListOf<McpTool>()
        val failures = mutableListOf<String>()
        results.forEach { (server, outcome) ->
            outcome.fold(
                onSuccess = { tools -> collected += tools },
                onFailure = { error ->
                    failures += "${server.name}：${error.message}"
                    collected += bindings.filter { it.serverId == server.id }
                },
            )
        }
        bindings = collected
        RefreshReport(toolCount = collected.size, failures = failures)
    }

    /**
     * 冷启动用：失败后重试几轮。
     *
     * 进程刚起来时网络栈常常还没就绪，一次性刷新失败会让本次进程的 MCP 工具永久缺失，
     * 用户只能自己点刷新。这里给几轮机会，代价只是后台线程多待几秒。
     */
    suspend fun refreshWithRetry(attempts: Int = 3, delayMs: Long = 2_000): RefreshReport {
        var report = refresh()
        var round = 1
        while (report.failures.isNotEmpty() && round < attempts) {
            delay(delayMs)
            report = refresh()
            round++
        }
        return report
    }

    /** 停用或删除服务器后立刻把它从可调用列表里摘掉，不等下次刷新。 */
    fun forget(serverId: String) {
        bindings = bindings.filterNot { it.serverId == serverId }
        clients.remove(serverId)?.close()
    }

    /**
     * 握手或复用会话拿工具。
     *
     * 配置没变就复用已握手的会话：既省一次握手，也避免每次刷新都在服务端新开一个
     * 永远不会被回收的会话。只有配置变了、会话死了或从来没连过才重新握手。
     */
    private suspend fun bind(server: McpServer): Result<List<McpTool>> {
        val cached = clients[server.id]
        if (cached != null && cached.matches(server)) {
            val reuse = cached.listTools()
            if (reuse.isSuccess) {
                // 复用会话不会重新拉 tools/list 里没有的东西，但显示名要跟上：
                // 用户可能只改了名称，工具清单里的 serverName 还停在建连那一刻
                return Result.success(reuse.getOrThrow().map { it.copy(serverName = server.name) })
            }
        }

        val fresh = McpClient(server)
        val tools = fresh.connect().getOrElse { return Result.failure(it) }
        val previous = clients.put(server.id, fresh)
        if (previous != null && previous !== fresh) previous.close()
        return Result.success(tools)
    }
}
