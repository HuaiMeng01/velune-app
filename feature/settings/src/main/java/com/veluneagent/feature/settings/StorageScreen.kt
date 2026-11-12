package com.veluneagent.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.veluneagent.core.data.StoreStats
import com.veluneagent.core.design.component.VxCard
import com.veluneagent.core.design.component.VxIcon
import com.veluneagent.core.design.component.VxRow
import com.veluneagent.core.design.component.VxSectionLabel
import com.veluneagent.core.design.component.VxText
import com.veluneagent.core.design.theme.VxIconPalette
import com.veluneagent.core.design.l10n.tr
import com.veluneagent.core.design.l10n.trf
import com.veluneagent.core.design.theme.VeluneTheme

/**
 * 数据与存储。数字全部来自本地库的真实查询，清空操作需要二次确认。
 */
@Composable
fun StorageScreen(
    stats: StoreStats,
    onClearAll: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** 导出全部会话为 Markdown，写到沙箱后可在「产出文件」里分享 */
    onExport: () -> Unit = {},
    /** 导出结果提示（成功给路径，失败给原因） */
    notice: String? = null,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        SettingsTopBar(title = "数据与存储", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            VxSectionLabel(text = "本地占用")

            VxCard {
                VxRow(
                    title = "会话",
                    icon = VxIcon.Message,
                    iconTint = VxIconPalette.Model.color(),
                    valueText = trf("%d 个", stats.conversations),
                    showChevron = false,
                    onClick = null,
                )
                VxRow(
                    title = "消息",
                    icon = VxIcon.Doc,
                    iconTint = VxIconPalette.Agent.color(),
                    valueText = trf("%d 条", stats.messages),
                    showChevron = false,
                    onClick = null,
                )
                VxRow(
                    title = "数据库文件",
                    icon = VxIcon.Database,
                    iconTint = VxIconPalette.Storage.color(),
                    valueText = stats.sizeLabel,
                    showChevron = false,
                    onClick = null,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            VxCard {
                VxRow(
                    title = "导出聊天记录",
                    subtitle = "Markdown 格式，写到沙箱后可分享",
                    icon = VxIcon.Doc,
                    iconTint = VxIconPalette.Model.color(),
                    onClick = onExport,
                )
            }

            if (notice != null) {
                Spacer(modifier = Modifier.height(8.dp))
                VxText(
                    text = notice,
                    style = type.footnote,
                    color = colors.textSecondary,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            VxCard {
                VxRow(
                    title = "清空全部对话",
                    subtitle = "删除所有会话与消息，不可恢复",
                    icon = VxIcon.Trash,
                    iconTint = colors.danger,
                    onClick = onClearAll,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            VxText(
                text = "所有数据保存在应用私有目录，不参与云同步，也不会被系统备份导出。",
                style = type.footnote,
                color = colors.textTertiary,
            )
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Preview(widthDp = 400, heightDp = 700, showBackground = true)
@Composable
private fun StoragePreview() {
    VeluneTheme(darkTheme = false) {
        StorageScreen(
            stats = StoreStats(conversations = 3, messages = 42, bytes = 96 * 1024),
            onClearAll = {},
            onBack = {},
        )
    }
}
