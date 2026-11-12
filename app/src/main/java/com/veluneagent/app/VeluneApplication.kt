package com.veluneagent.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.veluneagent.core.data.ChatPrefs
import com.veluneagent.core.data.ChatStore
import com.veluneagent.core.data.ProviderStore
import com.veluneagent.core.data.agent.AgentRunStore
import com.veluneagent.core.data.agent.ToolPrefs
import com.veluneagent.core.data.agent.ToolCatalog
import com.veluneagent.core.data.agent.ToolExecutor
import com.veluneagent.core.data.plugin.PluginStore

class VeluneApplication : Application() {

    /**
     * 进程内单例。数据库只开一份连接，各页面通过 Flow 观察变化；
     * 放在 Application 而不是 Activity，旋转屏幕或返回栈重排都不会重建。
     */
    val chatStore: ChatStore by lazy { ChatStore(this) }

    /** 插件仓库：只存 JSON 清单，安装不增加可执行面。 */
    val pluginStore: PluginStore by lazy { PluginStore(this) }

    /** Agent 运行记录与 scope 审计。 */
    val agentRunStore: AgentRunStore by lazy { AgentRunStore(this) }

    /** 工具目录：内置 + 已启用插件，每次取 spec 都重新拼装，装完即可用。 */
    val toolCatalog: ToolCatalog by lazy { ToolCatalog(pluginStore, mcpRegistry) }

    /** MCP 服务器配置与运行时工具表 */
    val mcpStore: com.veluneagent.core.data.mcp.McpStore by lazy {
        com.veluneagent.core.data.mcp.McpStore(this)
    }
    val mcpRegistry: com.veluneagent.core.data.mcp.McpRegistry by lazy {
        com.veluneagent.core.data.mcp.McpRegistry(mcpStore)
    }

    /** 文件沙箱：Agent 唯一能落盘的地方，限制在应用私有目录内。 */
    val sandbox: com.veluneagent.core.data.sandbox.Sandbox by lazy {
        com.veluneagent.core.data.sandbox.Sandbox(this)
    }

    /** 运行时权限桥：MainActivity 负责把 launcher 挂上来。 */
    val permissionRequester: AndroidPermissionRequester by lazy {
        AndroidPermissionRequester(this)
    }

    /** 系统能力：日历、待办、通知。 */
    val systemCapabilities: com.veluneagent.core.data.system.SystemCapabilities by lazy {
        com.veluneagent.core.data.system.SystemCapabilities(
            permissions = permissionRequester,
            calendar = com.veluneagent.core.data.system.CalendarRepository(
                this,
                permissionRequester,
            ),
            todos = com.veluneagent.core.data.system.TodoStore(this),
            notifier = com.veluneagent.core.data.system.Notifier(this),
            apps = com.veluneagent.core.data.system.AppLauncher(this),
            sms = com.veluneagent.core.data.system.SmsReader(this, permissionRequester),
            screen = com.veluneagent.core.data.system.ScreenController(this),
        )
    }

    val toolExecutor: ToolExecutor by lazy {
        ToolExecutor(
            plugins = pluginStore,
            auditor = agentRunStore,
            sandbox = sandbox,
            system = systemCapabilities,
            // 搜索/抓取接口只认 DeepSeek Key，与当前对话用哪家模型无关
            webKey = { ProviderStore.deepSeekKey() },
            // 语音合成只认 MiMo Key，同理与聊天用的模型无关
            mimoKey = { ProviderStore.mimoTtsKey() },
            // 手机内 Ubuntu 执行器：把代码写进 rootfs 的 /root 再跑，跑完删掉
            localRun = { language, code, timeoutSec ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val ext = when (language) {
                        "python", "python3", "py" -> "py"
                        "node", "js", "javascript" -> "js"
                        "java" -> "java"
                        else -> "sh"
                    }
                    val runner = when (ext) {
                        "py" -> "python3"
                        "js" -> "node"
                        "java" -> "java"
                        else -> "sh"
                    }
                    val dir = com.veluneagent.core.data.linux.LinuxEnv.homeDir(this@VeluneApplication)
                    val file = java.io.File(dir, "run-${System.currentTimeMillis()}.$ext")
                    file.writeText(code)
                    com.veluneagent.core.data.linux.LinuxRuntime.run(
                        context = this@VeluneApplication,
                        command = "$runner /root/${file.name} 2>&1; rc=\$?; rm -f /root/${file.name}; exit \$rc",
                        timeoutMs = timeoutSec * 1_000L,
                    )
                }
            },
            // 子模型：delegate_task 把子任务交给「模型分工 → 子模型」指定的服务商。
            // 没有单独配置时跟随主模型 —— 功能可用优先于省钱。
            subAgent = { task, context ->
                val providers = ProviderStore.providers.value
                val main = providers.firstOrNull { it.id == ProviderStore.activeId.value }
                    ?: providers.firstOrNull()
                    ?: error("还没有配置任何服务商：设置 → 模型与密钥")
                val provider = com.veluneagent.core.data.ModelRouting.providerFor(
                    com.veluneagent.core.data.ModelRouting.Role.Sub,
                    providers,
                    main,
                ) ?: main
                val key = ProviderStore.apiKeyOf(provider.id)
                    ?: error("子模型缺少 API Key：设置 → 模型与密钥")
                val messages = listOf(
                    com.veluneagent.core.data.ChatMessage(
                        role = "system",
                        text = "你是子任务执行者。只完成用户给出的那一件事，直接给出结果：" +
                            "不要复述任务、不要寒暄、不要反问。你没有任何工具，" +
                            "不能执行命令、读文件或联网，只能推理与写作。",
                    ),
                    com.veluneagent.core.data.ChatMessage(
                        role = "user",
                        text = if (context.isBlank()) task else "$task\n\n---\n$context",
                    ),
                )
                val text = StringBuilder()
                var promptTokens = 0
                var completionTokens = 0
                com.veluneagent.core.data.ChatClient.stream(provider, key, messages).collect { event ->
                    when (event) {
                        is com.veluneagent.core.data.StreamEvent.Delta -> text.append(event.text)
                        is com.veluneagent.core.data.StreamEvent.Usage -> {
                            promptTokens = event.promptTokens
                            completionTokens = event.completionTokens
                        }
                        is com.veluneagent.core.data.StreamEvent.Failed -> error(event.message)
                        else -> Unit
                    }
                }
                val result = text.toString().trim()
                if (result.isEmpty()) error("子模型没有返回内容")
                com.veluneagent.core.data.agent.SubAgentOutcome(
                    text = result,
                    promptTokens = promptTokens,
                    completionTokens = completionTokens,
                )
            },
            mcp = mcpRegistry,
        )
    }

    override fun onCreate() {
        super.onCreate()
        // 偏好与密钥仓库都依赖 Context，统一在这里初始化，
        // 避免只在 MainActivity 里 init 导致进程重建（如崩溃恢复）后读到空配置。
        ChatPrefs.init(this)
        com.veluneagent.core.data.profile.UserProfile.init(this)
        com.veluneagent.core.data.profile.AppearancePrefs.init(this)
        com.veluneagent.core.data.linux.LinuxEnv.init(this)
        ToolPrefs.init(this)
        ProviderStore.init(this)
        // 模型分工（主 / 摘要 / 子模型）跟着 ProviderStore：它存的是服务商 id
        com.veluneagent.core.data.ModelRouting.init(this)
        // 协议同意状态：没同意过就先过同意门，别让主界面带着用户数据先跑起来
        LegalConsent.init(this)
        // QQ 机器人的配置（含加密令牌）也要在进程启动时恢复
        com.veluneagent.core.data.im.QqBot.init(this)
        // 别名表紧跟着 QqBot：服务收消息时要同步取名字，晚初始化就会先落一批 openid 切片
        com.veluneagent.core.data.im.QqAliasStore.init(this)
        com.veluneagent.core.data.ssh.Ssh.init(this)

        // 重启后 AlarmManager 里的待办闹钟全丢了，按库重建一次。
        // 放后台线程：读库 + 设闹钟不该拖慢冷启动
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { systemCapabilities.todos.rescheduleReminders() }
        }

        // MCP 工具表是运行时拉取的，进程重启后缓存就没了，这里补拉一次。
        // 失败不影响启动：没拉到工具时 Agent 只是少几个可选项。
        // 带重试：冷启动这一刻网络栈常常还没就绪，只试一次会让本次进程的 MCP 工具永久缺失。
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { mcpRegistry.refreshWithRetry() }
        }

        // 一次性修复历史里的 null 污染（解析 bug 已修，但旧数据不会自己好）
        val migrations = getSharedPreferences("migrations", MODE_PRIVATE)
        if (!migrations.getBoolean("null_cleanup_v1", false)) {
            CoroutineScope(Dispatchers.IO).launch {
                val changed = runCatching { chatStore.cleanupNullPollutedMessages() }.getOrDefault(0)
                migrations.edit().putBoolean("null_cleanup_v1", true).apply()
                if (changed > 0) {
                    android.util.Log.i("Velune", "已清洗 $changed 条被 null 污染的消息")
                }
            }
        }
    }
}
