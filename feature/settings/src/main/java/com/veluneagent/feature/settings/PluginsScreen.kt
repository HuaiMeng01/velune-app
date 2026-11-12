package com.veluneagent.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veluneagent.core.data.plugin.InstalledPlugin
import com.veluneagent.core.data.plugin.PluginManifestParser
import com.veluneagent.core.design.component.VxActionSheet
import com.veluneagent.core.design.component.VxCard
import com.veluneagent.core.design.component.VxEmptyState
import com.veluneagent.core.design.component.VxIcon
import com.veluneagent.core.design.component.VxPillButton
import com.veluneagent.core.design.component.VxPromptSheet
import com.veluneagent.core.design.component.VxRow
import com.veluneagent.core.design.component.VxSectionLabel
import com.veluneagent.core.design.component.VxSheetAction
import com.veluneagent.core.design.component.VxText
import com.veluneagent.core.design.l10n.tr
import com.veluneagent.core.design.l10n.trf
import com.veluneagent.core.design.theme.VxIconPalette
import com.veluneagent.core.design.theme.VeluneTheme

/**
 * 插件页。
 *
 * 只呈现「装了什么、能碰什么」：每个插件的 scope 都必须原样展示，
 * 而不是折叠成一句「需要网络权限」—— 权限写得越模糊，用户越会一律点同意。
 */
@Composable
fun PluginsScreen(
    plugins: List<InstalledPlugin>,
    onInstallUrl: (String) -> Unit,
    onInstallClipboard: () -> Unit,
    onInstallSample: () -> Unit,
    onToggle: (InstalledPlugin) -> Unit,
    onUninstall: (InstalledPlugin) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    messageIsError: Boolean = false,
    /** QQ 机器人配置；null 表示尚未初始化 */
    qqConfig: com.veluneagent.core.data.im.QqBot.Config? = null,
    onOpenQqBot: () -> Unit = {},
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type
    var urlSheet by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<InstalledPlugin?>(null) }

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsTopBar(title = "插件", onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
            ) {
                VxSectionLabel(text = "插件库")

                val sampleInstalled = plugins.any {
                    it.id == com.veluneagent.core.data.plugin.PluginInstaller.SampleId
                }
                VxCard {
                    VxRow(
                        title = "QQ 机器人",
                        subtitle = when {
                            qqConfig == null -> "发消息到 QQ（需要服务器侧协议端）"
                            qqConfig.configured -> buildString {
                                append(tr("已配置"))
                                if (qqConfig.displayName.isNotBlank()) append(" · ${qqConfig.displayName}")
                                append(" · ${if (qqConfig.target == com.veluneagent.core.data.im.QqBot.Target.Group) tr("群") else tr("私聊")}")
                                append(" ${qqConfig.targetId}")
                            }

                            else -> "未配置：填入协议端地址与目标号码后可用"
                        },
                        icon = VxIcon.Message,
                        iconTint = VxIconPalette.Plugin.color(),
                        valueText = if (qqConfig?.configured == true) "已启用" else "去配置",
                        onClick = onOpenQqBot,
                    )
                    VxRow(
                        title = "HTTP 回显示例",
                        subtitle = "内置清单，访问 httpbin.org 验证安装与调用链路",
                        icon = VxIcon.Sparkles,
                        iconTint = colors.warning,
                        valueText = if (sampleInstalled) "已安装" else "安装",
                        showChevron = !sampleInstalled,
                        onClick = if (sampleInstalled) null else onInstallSample,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                VxSectionLabel(text = trf("已安装（%d）", plugins.size))

                if (plugins.isEmpty()) {
                    VxCard {
                        VxEmptyState(
                            icon = VxIcon.Grid,
                            title = "还没有插件",
                            description = "也可以直接让 Agent 帮你装",
                        )
                    }
                } else {
                    VxCard {
                        plugins.forEach { plugin ->
                            PluginRow(
                                plugin = plugin,
                                onClick = { actionTarget = plugin },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                VxSectionLabel(text = "安装")

                VxCard {
                    VxRow(
                        title = "从 URL 安装",
                        subtitle = "https 清单地址，装完立即可用",
                        icon = VxIcon.Plus,
                        iconTint = colors.accent,
                        onClick = { urlSheet = true },
                    )
                    VxRow(
                        title = "从剪贴板安装",
                        subtitle = "粘贴 manifest JSON 后点这里",
                        icon = VxIcon.Doc,
                        iconTint = VxIconPalette.Agent.color(),
                        onClick = onInstallClipboard,
                    )
                }

                if (message != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    VxText(
                        text = message,
                        style = type.footnote,
                        // 颜色由组合根随 Outcome.ok 传入；不能按消息前缀猜（翻译后前缀会变）
                        color = if (messageIsError) colors.danger else colors.success,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                VxText(
                    text = "插件只做声明式 HTTP 调用，不执行任何代码。每次调用都会先让你确认目标与参数。",
                    style = type.footnote,
                    color = colors.textTertiary,
                )
                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        VxPromptSheet(
            visible = urlSheet,
            title = "从 URL 安装插件",
            initialValue = "",
            placeholder = "https://example.com/manifest.json",
            confirmLabel = "安装",
            onConfirm = {
                urlSheet = false
                onInstallUrl(it)
            },
            onDismiss = { urlSheet = false },
        )

        val target = actionTarget
        if (target != null) {
            VxActionSheet(
                visible = true,
                title = target.manifest.name,
                caption = "${target.id} · v${target.manifest.version}",
                actions = listOf(
                    VxSheetAction(
                        label = if (target.enabled) "停用插件" else "启用插件",
                        icon = if (target.enabled) VxIcon.Close else VxIcon.Check,
                        onClick = {
                            actionTarget = null
                            onToggle(target)
                        },
                    ),
                    VxSheetAction(
                        label = "卸载插件",
                        icon = VxIcon.Trash,
                        destructive = true,
                        onClick = {
                            actionTarget = null
                            onUninstall(target)
                        },
                    ),
                ),
                onDismiss = { actionTarget = null },
            )
        }
    }
}

@Composable
private fun PluginRow(plugin: InstalledPlugin, onClick: () -> Unit) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VxText(
                    text = plugin.manifest.name,
                    style = type.rowTitle,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(modifier = Modifier.width(6.dp))
                VxText(
                    text = "v${plugin.manifest.version}",
                    style = type.footnote.copy(fontSize = 11.sp),
                    color = colors.textTertiary,
                )
                if (!plugin.enabled) {
                    Spacer(modifier = Modifier.width(6.dp))
                    VxText(
                        text = "已停用",
                        style = type.footnote.copy(fontSize = 11.sp),
                        color = colors.warning,
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            VxText(
                text = plugin.manifest.description.ifBlank { trf("%d 个工具", plugin.manifest.tools.size) },
                style = type.footnote,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            VxText(
                text = plugin.manifest.scopes.joinToString(" · ") { scope ->
                    PluginManifestParser.describeScope(scope, plugin.manifest.scopesRationale)
                },
                style = type.footnote.copy(fontSize = 11.sp),
                color = colors.textTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))
        VxPillButton(
            icon = VxIcon.Sliders,
            label = "管理",
            onClick = onClick,
        )
    }
}
