package com.veluneagent.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.veluneagent.core.design.component.VxCard
import com.veluneagent.core.design.component.VxCollapsingScaffold
import com.veluneagent.core.design.component.VxIcon
import com.veluneagent.core.design.component.VxRow
import com.veluneagent.core.design.component.VxStaggerIn
import com.veluneagent.core.design.theme.VeluneTheme
import com.veluneagent.core.design.l10n.tr
import com.veluneagent.core.design.l10n.trf
import com.veluneagent.core.design.theme.VxIconPalette
import com.veluneagent.core.design.theme.VxSpace

/**
 * 设置主页。居中紧凑标题 + 白卡分组 + 彩色图标，卡内不画分隔线（对齐小米设置）。
 *
 * **副标题只放状态，不放功能介绍**：小米设置的 summary 是"未连接"、"已开启"这类状态，
 * 不是"这个功能是干什么的"。之前 12 行里有 10 行带说明文案，行高从 56dp 涨到 69dp，
 * 一屏能看到的项从 11 行掉到 8 行，窄屏还会被截断成半句——信息密度与层级一起塌掉。
 * 功能说明属于子页，不属于首页。
 *
 * **一组卡一个域**：关于永远单独一张放最后，跨域的项分卡。
 */
@Composable
fun SettingsScreen(
    providerSummary: String = "未配置",
    storageSummary: String = "—",
    version: String = "—",
    pluginCount: Int = 0,
    agentEnabled: Boolean = false,
    permissionSummary: String = "—",
    enabledToolCount: Int = 0,
    onOpenModels: () -> Unit = {},
    onOpenPlugins: () -> Unit = {},
    onOpenPrompts: () -> Unit = {},
    onOpenMcp: () -> Unit = {},
    /** MCP 服务器数量（工具数是运行时拉取的，这里只报服务器数） */
    mcpSummary: String? = null,
    onOpenAppearance: () -> Unit = {},
    /** 外观当前状态，如「深色 · 大」 */
    appearanceSummary: String = "跟随系统",
    onOpenPermissions: () -> Unit = {},
    onOpenTools: () -> Unit = {},
    onOpenStorage: () -> Unit = {},
    onOpenFiles: () -> Unit = {},
    onOpenSsh: () -> Unit = {},
    onOpenLinux: () -> Unit = {},
    /** Linux 环境当前状态，如「已就绪」「未安装」 */
    linuxSummary: String = "未安装",
    onOpenAbout: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    VxCollapsingScaffold(title = "设置", modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = VxSpace.cardInset)) {
            // 一、AI 能力
            VxStaggerIn(index = 0) {
                VxCard {
                VxRow(
                    title = "模型与密钥",
                    subtitle = providerSummary,
                    icon = VxIcon.Key,
                    iconTint = VxIconPalette.Model.color(),
                    onClick = onOpenModels,
                )
                VxRow(
                    title = "Agent 与工具",
                    icon = VxIcon.Sliders,
                    iconTint = VxIconPalette.Agent.color(),
                    valueText = if (agentEnabled) trf("%d 个工具", enabledToolCount) else tr("未开启"),
                    onClick = onOpenTools,
                )
                VxRow(
                    title = "插件",
                    icon = VxIcon.Grid,
                    iconTint = VxIconPalette.Plugin.color(),
                    valueText = if (pluginCount == 0) tr("未安装") else trf("%d 个", pluginCount),
                    onClick = onOpenPlugins,
                )
                VxRow(
                    title = "角色",
                    icon = VxIcon.Person,
                    iconTint = VxIconPalette.Persona.color(),
                    onClick = onOpenPrompts,
                )
                VxRow(
                    title = "MCP 服务器",
                    icon = VxIcon.Sparkles,
                    iconTint = VxIconPalette.Protocol.color(),
                    valueText = mcpSummary,
                    onClick = onOpenMcp,
                )
                }
            }

            Spacer(modifier = Modifier.height(VxSpace.m))

            // 二、应用本身
            VxStaggerIn(index = 1) {
                VxCard {
                VxRow(
                    title = "应用偏好设置",
                    icon = VxIcon.Palette,
                    iconTint = VxIconPalette.Appearance.color(),
                    valueText = appearanceSummary,
                    onClick = onOpenAppearance,
                )
                VxRow(
                    title = "权限与能力",
                    icon = VxIcon.Shield,
                    iconTint = VxIconPalette.Permission.color(),
                    valueText = permissionSummary,
                    onClick = onOpenPermissions,
                )
                }
            }

            Spacer(modifier = Modifier.height(VxSpace.m))

            // 三、数据与运行环境
            VxStaggerIn(index = 2) {
                VxCard {
                VxRow(
                    title = "数据与存储",
                    icon = VxIcon.Database,
                    iconTint = VxIconPalette.Storage.color(),
                    valueText = storageSummary,
                    onClick = onOpenStorage,
                )
                VxRow(
                    title = "产出文件",
                    icon = VxIcon.Doc,
                    iconTint = VxIconPalette.Files.color(),
                    onClick = onOpenFiles,
                )
                VxRow(
                    title = "远程服务器",
                    icon = VxIcon.Refresh,
                    iconTint = VxIconPalette.Remote.color(),
                    onClick = onOpenSsh,
                )
                VxRow(
                    title = "Linux 环境",
                    icon = VxIcon.Play,
                    iconTint = VxIconPalette.Remote.color(),
                    valueText = linuxSummary,
                    onClick = onOpenLinux,
                )
                }
            }

            Spacer(modifier = Modifier.height(VxSpace.m))

            // 四、关于：永远单独一张，放最后
            VxStaggerIn(index = 3) {
                VxCard {
                VxRow(
                    title = "关于",
                    icon = VxIcon.Info,
                    iconTint = VxIconPalette.About.color(),
                    valueText = version,
                    onClick = onOpenAbout,
                )
                }
            }
        }
    }
}

@Preview(widthDp = 400, heightDp = 800, showBackground = true)
@Composable
private fun SettingsPreview() {
    VeluneTheme(darkTheme = false) {
        Column(modifier = Modifier.background(VeluneTheme.colors.background)) {
            SettingsScreen()
        }
    }
}

@Preview(widthDp = 400, heightDp = 800, showBackground = true)
@Composable
private fun SettingsDarkPreview() {
    VeluneTheme(darkTheme = true) {
        Column(modifier = Modifier.background(VeluneTheme.colors.background)) {
            SettingsScreen()
        }
    }
}
