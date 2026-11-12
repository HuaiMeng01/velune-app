package com.veluneagent.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.veluneagent.core.data.mcp.McpRegistry
import com.veluneagent.core.data.mcp.McpServer
import com.veluneagent.core.data.mcp.McpStore
import com.veluneagent.core.design.component.VxCard
import com.veluneagent.core.design.component.VxEmptyState
import com.veluneagent.core.design.component.VxIcon
import com.veluneagent.core.design.component.VxRow
import com.veluneagent.core.design.component.VxSectionLabel
import com.veluneagent.core.design.component.VxSwitch
import com.veluneagent.core.design.component.VxText
import com.veluneagent.core.design.l10n.tr
import com.veluneagent.core.design.l10n.trf
import com.veluneagent.core.design.theme.VeluneTheme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * MCP 服务器管理。
 *
 * 为什么这个页面比"插件"短很多：MCP 是标准协议，服务器地址一填、握手成功就能拿到
 * 完整的工具列表，不需要像插件那样在本地声明工具。所以这里只需要「地址 + 请求头」两项。
 *
 * 只支持远程 HTTP 传输。stdio 需要子进程运行时（Node/Python），Android 上等于再带一套环境，
 * 那是另一个项目的体量。
 */
@Composable
fun McpScreen(
    store: McpStore,
    registry: McpRegistry,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type
    val scope = rememberCoroutineScope()

    var servers by remember { mutableStateOf(store.list()) }
    // 初值直接取注册表里的已拉取结果：启动时已经后台拉过一轮，
    // 进页面就以空白开场会让用户以为"没连上"，还得手动点一次刷新才知道有工具
    var toolCounts by remember {
        mutableStateOf(servers.associate { it.id to registry.toolCountOf(it.id) })
    }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    // 非空时进入编辑：id 为空表示新建
    var editing by remember { mutableStateOf<McpServer?>(null) }

    val current = editing
    if (current != null) {
        McpEditPage(
            initial = current,
            onBack = { editing = null },
            onSave = { server ->
                store.upsert(server)
                servers = store.list()
                editing = null
                if (!server.enabled) {
                    // 停用要立刻生效：工具从 Agent 的下一次调用里消失，不等下次刷新
                    registry.forget(server.id)
                    toolCounts = servers.associate { it.id to registry.toolCountOf(it.id) }
                    notice = trf("已停用「%s」", server.name)
                } else {
                    // 保存后立刻拉一次：用户加完就想知道"通不通、有几个工具"
                    scope.launch {
                        busy = true
                        val report = registry.refresh()
                        busy = false
                        toolCounts = servers.associate { it.id to registry.toolCountOf(it.id) }
                        notice = if (report.failures.isEmpty()) {
                            trf("已连接，共 %d 个工具", report.toolCount)
                        } else {
                            report.failures.joinToString("；")
                        }
                    }
                }
            },
            onDelete = { server ->
                store.delete(server.id)
                registry.forget(server.id)
                servers = store.list()
                toolCounts = servers.associate { it.id to registry.toolCountOf(it.id) }
                editing = null
                notice = trf("已删除「%s」", server.name)
            },
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        SettingsTopBar(
            title = "MCP 服务器",
            onBack = onBack,
            action = {
                if (busy) {
                    Text(tr("刷新中…"), style = type.footnote, color = colors.textTertiary)
                } else {
                    Text(
                        text = tr("刷新"),
                        style = type.footnote,
                        color = colors.accent,
                        modifier = Modifier
                            .clickable {
                                scope.launch {
                                    busy = true
                                    val report = registry.refresh()
                                    busy = false
                                    toolCounts = servers.associate { it.id to registry.toolCountOf(it.id) }
                                    notice = if (report.failures.isEmpty()) {
                                        trf("共 %d 个工具", report.toolCount)
                                    } else {
                                        report.failures.joinToString("；")
                                    }
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            VxSectionLabel(text = "已配置")

            if (servers.isEmpty()) {
                VxCard {
                    VxEmptyState(
                        icon = VxIcon.Grid,
                        title = "还没有 MCP 服务器",
                        description = "填一个 HTTP 地址，握手成功后它的工具会自动出现在 Agent 里",
                    )
                }
            } else {
                VxCard {
                    servers.forEach { server ->
                        VxRow(
                            title = server.name,
                            subtitle = server.url,
                            valueText = when {
                                !server.enabled -> tr("已停用")
                                busy -> "…"
                                else -> toolCounts[server.id]?.let { trf("%d 个工具", it) }
                            },
                            onClick = { editing = server },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            VxCard {
                VxRow(
                    title = "添加服务器",
                    icon = VxIcon.Plus,
                    iconTint = colors.accent,
                    onClick = {
                        editing = McpServer(
                            id = "mcp" + System.currentTimeMillis().toString(36).takeLast(6),
                            name = "",
                            url = "",
                        )
                    },
                )
            }

            notice?.let {
                Spacer(modifier = Modifier.height(12.dp))
                VxText(text = it, style = type.footnote, color = colors.textSecondary)
            }

            Spacer(modifier = Modifier.height(16.dp))
            VxText(
                text = "MCP 工具全部需要逐次确认：协议里没有「是否只读」的声明，我们无从判断，" +
                    "所以按有副作用处理。服务器由你自行配置，其行为由该服务器负责。",
                style = type.footnote,
                color = colors.textTertiary,
            )
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun McpEditPage(
    initial: McpServer,
    onBack: () -> Unit,
    onSave: (McpServer) -> Unit,
    onDelete: (McpServer) -> Unit,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type
    val miuix = MiuixTheme.colorScheme

    var name by remember { mutableStateOf(initial.name) }
    var url by remember { mutableStateOf(initial.url) }
    // 请求头只支持一行 `Key: Value`：绝大多数 MCP 服务器只需要一个 Authorization
    var header by remember {
        mutableStateOf(
            initial.headers.entries.firstOrNull()?.let { "${it.key}: ${it.value}" }.orEmpty(),
        )
    }
    var enabled by remember { mutableStateOf(initial.enabled) }
    var error by remember { mutableStateOf<String?>(null) }

    val isNew = initial.name.isBlank() && initial.url.isBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        SettingsTopBar(title = if (isNew) "添加服务器" else "编辑服务器", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            VxSectionLabel(text = "基本信息")

            VxCard {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        label = tr("名称，如「文件工具」"),
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = url,
                        onValueChange = { url = it },
                        label = tr("服务器地址，如 https://example.com/mcp"),
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            VxSectionLabel(text = "状态")

            VxCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        VxText(
                            text = "启用这个服务器",
                            style = type.rowTitle,
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        VxText(
                            text = if (enabled) {
                                "它的工具会出现在 Agent 的可调用列表里"
                            } else {
                                "已停用：工具从 Agent 里移除，配置与令牌保留"
                            },
                            style = type.footnote,
                            color = colors.textSecondary,
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    VxSwitch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }

            VxSectionLabel(text = "鉴权（可选）")

            VxCard {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    TextField(
                        value = header,
                        onValueChange = { header = it },
                        label = "Authorization: Bearer xxx",
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    VxText(
                        text = "只填一行，冒号前的部分是请求头名。留空表示不需要鉴权。",
                        style = type.footnote,
                        color = colors.textTertiary,
                    )
                }
            }

            error?.let {
                Spacer(modifier = Modifier.height(10.dp))
                VxText(text = it, style = type.footnote, color = colors.danger)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val trimmedUrl = url.trim()
                        if (trimmedUrl.isEmpty()) {
                            error = "服务器地址不能为空"
                            return@Button
                        }
                        checkUrl(trimmedUrl)?.let {
                            error = it
                            return@Button
                        }
                        val parsed = parseHeader(header)
                        if (header.isNotBlank() && parsed == null) {
                            // 之前这里静默丢掉：用户以为存了鉴权，实际发出去是裸请求，
                            // 只能看到一个没头没脑的 401
                            error = "请求头格式应为「名称: 值」，例如 Authorization: Bearer xxx"
                            return@Button
                        }
                        if (parsed != null && parsed.first.equals("cookie", true)) {
                            error = "不支持 Cookie 头：会话凭据无法单独撤销，请改用 Authorization"
                            return@Button
                        }
                        onSave(
                            initial.copy(
                                name = name.trim().ifBlank { "未命名服务器" },
                                url = trimmedUrl,
                                headers = parsed?.let { mapOf(it) } ?: emptyMap(),
                                enabled = enabled,
                            ),
                        )
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                    minHeight = 42.dp,
                    cornerRadius = 21.dp,
                ) {
                    Text(tr("保存并连接"))
                }
            }

            if (!isNew) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { onDelete(initial) },
                    colors = ButtonDefaults.buttonColors(
                        color = miuix.errorContainer,
                        contentColor = miuix.error,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 42.dp,
                    cornerRadius = 21.dp,
                ) {
                    Text(tr("删除这个服务器"))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            VxText(
                text = "保存后会立刻发起一次握手。连不上通常是：地址路径不对（很多服务器是 /mcp 或 /sse）、" +
                    "缺少鉴权头、或服务器只支持 stdio 传输。",
                style = type.footnote,
                color = colors.textTertiary,
            )
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

/**
 * 地址校验必须按 **host 精确匹配**，不能用 `startsWith("http://localhost")`。
 * 前缀匹配会放过 `http://localhost.evil.com` —— 看起来是本机，实际是公网地址。
 * 真正的拦截在 `SimpleHttp`（它只认 scheme + host），这里保持一致，别让两层规则打架。
 */
private fun checkUrl(raw: String): String? {
    val uri = runCatching { java.net.URI(raw) }.getOrNull()
        ?: return "地址不是合法的 URL"
    val scheme = uri.scheme?.lowercase()
    val host = uri.host?.lowercase().orEmpty()
    if (host.isBlank()) return "地址里没有主机名"
    val ok = (scheme == "https") ||
        (scheme == "http" && (host == "localhost" || host == "127.0.0.1"))
    return if (ok) null else "只支持 https://（本机调试可用 http://localhost 或 http://127.0.0.1）"
}

private fun parseHeader(raw: String): Pair<String, String>? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val index = trimmed.indexOf(':')
    if (index <= 0) return null
    val key = trimmed.substring(0, index).trim()
    val value = trimmed.substring(index + 1).trim()
    if (key.isEmpty() || value.isEmpty()) return null
    return key to value
}
