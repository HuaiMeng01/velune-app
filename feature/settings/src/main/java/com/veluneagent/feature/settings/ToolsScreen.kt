package com.veluneagent.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veluneagent.core.data.agent.ToolSpec
import com.veluneagent.core.data.agent.ToolSource
import com.veluneagent.core.design.component.VxCard
import com.veluneagent.core.design.component.VxSwitch
import com.veluneagent.core.design.component.VxSectionLabel
import com.veluneagent.core.design.component.VxText
import com.veluneagent.core.design.shape.SquircleCapsule
import com.veluneagent.core.design.l10n.tr
import com.veluneagent.core.design.l10n.trf
import com.veluneagent.core.design.theme.VeluneTheme

/**
 * Agent 与工具。
 *
 * 两件事：
 * 1. 思考开关与推理强度（关不掉的模型会明确提示，而不是静默失效）；
 * 2. 逐个工具开关——停用的工具不会进入模型的 tools 数组。
 */
@Composable
fun ToolsScreen(
    tools: List<ToolSpec>,
    isEnabled: (String) -> Boolean,
    onToggle: (String, Boolean) -> Unit,
    thinkingEnabled: Boolean,
    onThinkingChange: (Boolean) -> Unit,
    effort: String?,
    onEffortChange: (String?) -> Unit,
    thinkingPolicy: com.veluneagent.core.data.ThinkingPolicy,
    onBack: () -> Unit,
    /** 读短信前是否每次确认（默认开） */
    confirmSms: Boolean = true,
    onConfirmSmsChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        SettingsTopBar(title = "Agent 与工具", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            VxSectionLabel(text = "思考")

            VxCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        VxText(
                            text = "深度思考",
                            style = type.rowTitle,
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        VxText(
                            text = when (thinkingPolicy) {
                                com.veluneagent.core.data.ThinkingPolicy.CanDisable ->
                                    "开启后先输出思考过程再回答，更准但更慢"

                                com.veluneagent.core.data.ThinkingPolicy.ForcedOn ->
                                    "当前模型强制思考，无法关闭"

                                // 用户自填的模型名：不猜，避免为了关思考把请求打回 400
                                com.veluneagent.core.data.ThinkingPolicy.Unknown ->
                                    "未知模型，关闭请求不会下发，将保持服务端默认"
                            },
                            style = type.footnote,
                            color = colors.textSecondary,
                        )
                    }
                    VxSwitch(
                        checked = thinkingEnabled,
                        onCheckedChange = onThinkingChange,
                        enabled = thinkingPolicy !=
                            com.veluneagent.core.data.ThinkingPolicy.ForcedOn,
                    )
                }

                if (thinkingEnabled) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        VxText(
                            text = "推理强度",
                            style = type.footnote,
                            color = colors.textTertiary,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("low" to "更快", "high" to "均衡", "max" to "最深").forEach { (value, label) ->
                                EffortChip(
                                    label = label,
                                    selected = effort == value,
                                    onClick = { onEffortChange(value) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            VxSectionLabel(text = "隐私")

            VxCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        VxText(
                            text = "读短信前先确认",
                            style = type.rowTitle,
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        VxText(
                            text = if (confirmSms) {
                                "每次 read_sms 都会先弹确认，能看到 Agent 想读什么"
                            } else {
                                "已关闭：Agent 会在你提问时直接读取，不再确认"
                            },
                            style = type.footnote,
                            color = colors.textSecondary,
                        )
                    }
                    VxSwitch(
                        checked = confirmSms,
                        onCheckedChange = onConfirmSmsChange,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            VxSectionLabel(text = trf("内置工具（%d）", tools.count { it.source == ToolSource.Builtin }))

            VxCard {
                tools.filter { it.source == ToolSource.Builtin }.forEach { tool ->
                    ToolRow(
                        spec = tool,
                        enabled = isEnabled(tool.name),
                        onToggle = { onToggle(tool.name, it) },
                    )
                }
            }

            val pluginTools = tools.filter { it.source == ToolSource.Plugin }
            if (pluginTools.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                VxSectionLabel(text = trf("插件工具（%d）", pluginTools.size))
                VxCard {
                    pluginTools.forEach { tool ->
                        ToolRow(
                            spec = tool,
                            enabled = isEnabled(tool.name),
                            onToggle = { onToggle(tool.name, it) },
                        )
                    }
                }
            }

            val mcpTools = tools.filter { it.source == ToolSource.Mcp }
            if (mcpTools.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                VxSectionLabel(text = trf("MCP 工具（%d）", mcpTools.size))
                VxCard {
                    mcpTools.forEach { tool ->
                        ToolRow(
                            spec = tool,
                            enabled = isEnabled(tool.name),
                            onToggle = { onToggle(tool.name, it) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            VxText(
                text = "停用的工具不会出现在模型可调用的列表里。带副作用的工具（写文件、建日程、启动应用、锁屏、装插件）每次调用都会先请你确认。",
                style = type.footnote,
                color = colors.textTertiary,
            )
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ToolRow(
    spec: ToolSpec,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
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
                    text = spec.displayName.ifBlank { spec.name },
                    style = type.rowTitle,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (spec.sideEffect) {
                    Spacer(modifier = Modifier.width(6.dp))
                    VxText(
                        text = "需确认",
                        style = type.footnote.copy(fontSize = 10.sp),
                        color = colors.warning,
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            VxText(
                text = spec.description,
                style = type.footnote.copy(fontSize = 11.sp),
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        VxSwitch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun EffortChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VeluneTheme.colors
    Box(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(SquircleCapsule())
            .background(if (selected) colors.accent.copy(alpha = 0.12f) else colors.fill)
            .border(
                width = 1.dp,
                color = if (selected) colors.accent.copy(alpha = 0.5f) else Color.Transparent,
                shape = SquircleCapsule(),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        VxText(
            text = label,
            style = VeluneTheme.type.footnote.copy(fontSize = 12.sp),
            color = if (selected) colors.accent else colors.textPrimary,
        )
    }
}
