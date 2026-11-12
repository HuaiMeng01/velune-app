package com.veluneagent.core.data.plugin

import com.veluneagent.core.data.agent.ToolSource
import com.veluneagent.core.data.agent.ToolSpec
import org.json.JSONObject
import java.net.URI

/**
 * Lite 插件的一个工具声明。
 *
 * 只支持声明式 HTTP：method + path 模板，参数来自 JSON Schema。
 * **没有代码执行** —— 这是「Agent 可以自己装插件」能够成立的前提：
 * 装进来的东西最多发一次经过白名单校验的 HTTP 请求，而不是拿到进程执行权。
 */
data class PluginToolDef(
    val name: String,
    val title: String,
    val description: String,
    val parametersJson: String,
    val method: String = "GET",
    val path: String = "/",
    val sideEffect: Boolean = true,
    val timeoutMs: Int = 5_000,
)

/** Lite 插件清单。字段与 05 章 manifest 规范对齐，只实现声明式工具这一条分支。 */
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val scopes: List<String>,
    val scopesRationale: Map<String, String>,
    val baseUrl: String,
    val headers: Map<String, String>,
    val tools: List<PluginToolDef>,
) {
    /** 转成注册表用的 ToolSpec。工具名加插件前缀，避免两个插件撞名。 */
    fun toToolSpecs(): List<ToolSpec> = tools.map { tool ->
        ToolSpec(
            name = PluginNaming.wireName(id, tool.name),
            displayName = tool.title.ifBlank { tool.name },
            description = tool.description,
            parametersJson = tool.parametersJson,
            sideEffect = tool.sideEffect,
            timeoutMs = tool.timeoutMs,
            source = ToolSource.Plugin,
            pluginId = id,
            origin = name,
        )
    }
}

sealed interface ManifestResult {
    data class Ok(val manifest: PluginManifest) : ManifestResult
    data class Invalid(val errors: List<String>) : ManifestResult
}

/** 工具名的线上格式：插件 id 里的点会被模型端拒绝，统一压成下划线。 */
object PluginNaming {
    private val illegal = Regex("[^a-zA-Z0-9_]")

    fun wireName(pluginId: String, toolName: String): String {
        val prefix = pluginId.replace(illegal, "_")
        val full = "${prefix}__$toolName"
        return if (full.length <= 64) full else full.take(64)
    }
}

/**
 * 域名校验。所有网络工具执行前都必须过这一关，包括重定向后的目标。
 *
 * 规则（对应 04 章「用解析后的 host 比对，不能用原始 URL 字符串」）：
 * - `net.http` → 任意 https 主机
 * - `net.domain.a.example.com` → 只允许该 host
 * - `net.domain.*.example.com` → 允许一级子域（a.example.com，不允许 a.b.example.com）
 */
object PluginHosts {
    const val ScopeHttp = "net.http"
    private const val DomainPrefix = "net.domain."

    fun isHostAllowed(host: String, scopes: List<String>): Boolean {
        if (host.isBlank()) return false
        val normalized = host.lowercase()
        if (scopes.contains(ScopeHttp)) return true
        return scopes.filter { it.startsWith(DomainPrefix) }
            .map { it.removePrefix(DomainPrefix).lowercase() }
            .any { matches(normalized, it) }
    }

    private fun matches(host: String, pattern: String): Boolean {
        if (pattern == host) return true
        if (!pattern.startsWith("*.")) return false
        val suffix = pattern.removePrefix("*.")
        if (suffix == host) return false
        if (!host.endsWith(".$suffix")) return false
        // 只允许一级通配，避免 *.example.com 顺带放行 a.b.example.com
        return !host.removeSuffix(".$suffix").contains('.')
    }

    /** 声明是否合法（安装时用）。 */
    fun isValidScope(scope: String): Boolean {
        if (scope == ScopeHttp) return true
        if (!scope.startsWith(DomainPrefix)) return false
        val pattern = scope.removePrefix(DomainPrefix)
        if (pattern.isBlank()) return false
        val hostPart = pattern.removePrefix("*.")
        return hostPart.isNotBlank() &&
            hostPart.all { it.isLetterOrDigit() || it == '.' || it == '-' } &&
            !hostPart.startsWith('.') && !hostPart.endsWith('.') && !hostPart.contains("..")
    }
}

/**
 * manifest 解析与校验。
 *
 * 校验失败返回**全部**问题而不是第一条：用户要一次改完，而不是改一条试一次。
 */
object PluginManifestParser {

    const val MaxManifestBytes = 64 * 1024
    private const val MaxTools = 20

    private val idPattern = Regex("^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+$")
    private val toolNamePattern = Regex("^[a-z][a-z0-9_]{1,40}$")
    private val versionPattern = Regex("^\\d+\\.\\d+\\.\\d+([-+][0-9A-Za-z.-]+)?$")
    private val forbiddenHeaderNames = setOf(
        "authorization", "cookie", "set-cookie", "proxy-authorization",
    )

    /** 本轮不支持的能力：如实拒绝，而不是「装了但不生效」。 */
    private val unsupportedScopeHints = mapOf(
        "chat.read" to "读取会话内容",
        "chat.write" to "向会话写入消息",
        "agent.hook.prompt" to "改写系统提示词",
        "agent.hook.tool" to "拦截工具调用",
        "secret.store" to "托管密钥",
        "provider.register" to "注册模型服务商",
        "ui.surface" to "注册界面入口",
        "fs.user.pick" to "读取用户选择的文件",
        "sys.clipboard.read" to "读取剪贴板",
        "sys.notify" to "发送通知",
    )

    fun parse(raw: String): ManifestResult {
        if (raw.isBlank()) return ManifestResult.Invalid(listOf("清单内容为空"))
        if (raw.toByteArray().size > MaxManifestBytes) {
            return ManifestResult.Invalid(listOf("清单超过 ${MaxManifestBytes / 1024}KB 上限"))
        }
        val json = runCatching { JSONObject(raw) }.getOrElse {
            return ManifestResult.Invalid(listOf("不是合法的 JSON：${it.message ?: "解析失败"}"))
        }
        val errors = validate(json)
        if (errors.isNotEmpty()) return ManifestResult.Invalid(errors)
        return ManifestResult.Ok(toManifest(json))
    }

    fun validate(json: JSONObject): List<String> {
        val errors = mutableListOf<String>()

        if (json.optInt("schemaVersion", 0) != 1) {
            errors += "schemaVersion 必须为 1"
        }

        val id = json.optString("id")
        if (!idPattern.matches(id) || id.length > 64) {
            errors += "id 必须形如 com.example.tool（小写、反向域名、≤64 字符）"
        }

        val name = localize(json, "name")
        if (name.isBlank() || name.length > 24) errors += "name 必填且不超过 24 字符"

        val version = json.optString("version")
        if (!versionPattern.matches(version)) errors += "version 必须是语义化版本，例如 1.0.0"

        if (json.optString("description").length > 120) errors += "description 不超过 120 字符"

        val baseUrl = json.optString("baseUrl")
        val baseHost = runCatching { URI(baseUrl).host }.getOrNull()
        if (baseHost.isNullOrBlank()) {
            errors += "baseUrl 必须是合法 URL"
        } else {
            val scheme = runCatching { URI(baseUrl).scheme?.lowercase() }.getOrNull()
            val localHost = baseHost == "localhost" || baseHost == "127.0.0.1"
            if (scheme != "https" && !localHost) {
                errors += "baseUrl 必须使用 https（仅本机调试允许 http://localhost）"
            }
            if (runCatching { URI(baseUrl).rawQuery }.getOrNull() != null) {
                errors += "baseUrl 不允许带查询串"
            }
        }

        val scopesJson = json.optJSONArray("scopes")
        val scopes = buildList {
            if (scopesJson != null) {
                for (i in 0 until scopesJson.length()) add(scopesJson.optString(i))
            }
        }
        if (scopes.isEmpty()) {
            errors += "scopes 不能为空：插件必须显式声明它要访问什么"
        }
        scopes.forEach { scope ->
            val hint = unsupportedScopeHints.entries.firstOrNull { scope == it.key || scope.startsWith(it.key + ".") }
            when {
                hint != null -> errors += "Lite 插件暂不支持「$scope」（${hint.value}），需要 Full 插件运行时"
                !PluginHosts.isValidScope(scope) -> errors += "无法识别的 scope：$scope"
            }
        }
        if (baseHost != null && scopes.isNotEmpty() && scopes.none { it in unsupportedScopeHints.keys } &&
            !PluginHosts.isHostAllowed(baseHost, scopes)
        ) {
            errors += "baseUrl 的域名 $baseHost 未被 scopes 覆盖，请声明 net.domain.$baseHost"
        }

        val headersJson = json.optJSONObject("headers")
        if (headersJson != null) {
            headersJson.keys().forEach { key ->
                if (key.lowercase() in forbiddenHeaderNames) {
                    errors += "headers 不允许携带 $key：凭据必须走密钥托管，不能写进清单"
                }
                val value = headersJson.optString(key)
                if (value.contains('\n') || value.contains('\r')) {
                    errors += "headers.$key 含非法换行"
                }
            }
        }

        val toolsJson = json.optJSONArray("tools")
        if (toolsJson == null || toolsJson.length() == 0) {
            errors += "tools 至少声明一个工具"
        } else if (toolsJson.length() > MaxTools) {
            errors += "单插件工具数不超过 $MaxTools"
        } else {
            val seen = mutableSetOf<String>()
            for (i in 0 until toolsJson.length()) {
                val tool = toolsJson.optJSONObject(i) ?: continue
                val toolName = tool.optString("name")
                val label = "tools[$i]"
                if (!toolNamePattern.matches(toolName)) {
                    errors += "$label.name 必须匹配 ^[a-z][a-z0-9_]{1,40}$"
                } else if (!seen.add(toolName)) {
                    errors += "$label.name 与同插件内其他工具重名：$toolName"
                }
                if (localize(tool, "description").isBlank()) {
                    errors += "$label.description 必填：模型靠它决定是否调用"
                } else if (localize(tool, "description").length > 300) {
                    errors += "$label.description 不超过 300 字符"
                }
                val params = tool.opt("parameters")
                if (params !is JSONObject || params.optString("type") != "object") {
                    errors += "$label.parameters 必须是 type=object 的 JSON Schema"
                }
                val method = tool.optString("method", "GET").uppercase()
                if (method !in setOf("GET", "POST", "PUT", "DELETE")) {
                    errors += "$label.method 只支持 GET/POST/PUT/DELETE"
                }
                val path = tool.optString("path", "/")
                if (!path.startsWith("/") || path.contains("..") || path.contains("://") ||
                    path.any { it.isWhitespace() }
                ) {
                    errors += "$label.path 必须以 / 开头，且不能包含 .. / :// / 空白"
                }
                val timeout = tool.optInt("timeoutMs", 5_000)
                if (timeout !in 1_000..30_000) errors += "$label.timeoutMs 需在 1000–30000 之间"
            }
        }
        return errors
    }

    private fun toManifest(json: JSONObject): PluginManifest {
        val scopes = buildList {
            val array = json.optJSONArray("scopes")
            if (array != null) for (i in 0 until array.length()) add(array.optString(i))
        }
        val rationale = buildMap {
            json.optJSONObject("scopesRationale")?.let { obj ->
                obj.keys().forEach { key -> put(key, obj.optString(key)) }
            }
        }
        val headers = buildMap {
            json.optJSONObject("headers")?.let { obj ->
                obj.keys().forEach { key -> put(key, obj.optString(key)) }
            }
        }
        val tools = buildList {
            val array = json.optJSONArray("tools")
            if (array != null) {
                for (i in 0 until array.length()) {
                    val tool = array.optJSONObject(i) ?: continue
                    add(
                        PluginToolDef(
                            name = tool.optString("name"),
                            title = localize(tool, "title"),
                            description = localize(tool, "description"),
                            parametersJson = tool.optJSONObject("parameters")?.toString()
                                ?: """{"type":"object","properties":{}}""",
                            method = tool.optString("method", "GET").uppercase(),
                            path = tool.optString("path", "/"),
                            sideEffect = tool.optBoolean("sideEffect", true),
                            timeoutMs = tool.optInt("timeoutMs", 5_000),
                        ),
                    )
                }
            }
        }
        return PluginManifest(
            id = json.optString("id"),
            name = localize(json, "name"),
            version = json.optString("version"),
            description = localize(json, "description"),
            author = json.opt("author")?.toString()?.trim('"').orEmpty(),
            scopes = scopes,
            scopesRationale = rationale,
            baseUrl = json.optString("baseUrl").trimEnd('/'),
            headers = headers,
            tools = tools,
        )
    }

    /**
     * 解析 LocalizedText：字符串直接用；对象按 zh-CN → zh → default → 第一个值的顺序取。
     * 05 章允许两种写法，这里必须都吃得下，否则中文名会显示成一串 JSON。
     */
    private fun localize(json: JSONObject, key: String): String {
        val value = json.opt(key) ?: return ""
        if (value is JSONObject) {
            for (candidate in listOf("zh-CN", "zh", "default")) {
                val text = value.optString(candidate)
                if (text.isNotBlank()) return text
            }
            val firstKey = value.keys().asSequence().firstOrNull() ?: return ""
            return value.optString(firstKey)
        }
        return json.optString(key)
    }

    /** 供工具卡与安装弹窗展示的人话说明。 */
    fun describeScope(scope: String, rationale: Map<String, String> = emptyMap()): String {
        rationale[scope]?.let { if (it.isNotBlank()) return it }
        return when {
            scope == PluginHosts.ScopeHttp -> "访问任意网络地址"
            scope.startsWith("net.domain.") -> {
                val host = scope.removePrefix("net.domain.")
                "访问 $host"
            }

            else -> scope
        }
    }
}
