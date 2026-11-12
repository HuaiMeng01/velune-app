package com.veluneagent.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veluneagent.core.design.component.VxAvatar
import com.veluneagent.core.design.component.VxCard
import com.veluneagent.core.design.component.VxCollapsingScaffold
import com.veluneagent.core.design.component.VxRow
import com.veluneagent.core.design.component.VxSectionLabel
import com.veluneagent.core.design.component.VxIcon
import com.veluneagent.core.design.component.VxText
import com.veluneagent.core.design.l10n.trf
import com.veluneagent.core.design.theme.VxIconPalette
import com.veluneagent.core.design.theme.VeluneTheme
import com.veluneagent.core.design.theme.VxSpace

/** 真实统计：会话数、消息数、Token 用量、本地占用。全部来自本地库查询。 */
data class UsageStats(
    val sessions: Int = 0,
    val messages: Int = 0,
    val storage: String = "—",
    val totalTokens: Long = 0,
    val todayTokens: Long = 0,
)

@Composable
fun ProfileScreen(
    userName: String = "本地用户",
    version: String = "—",
    stats: UsageStats = UsageStats(),
    onOpenProfile: () -> Unit = {},
    /** 编辑昵称：提示词里的 {{user_name}} 用的是它 */
    onEditName: () -> Unit = {},
    onOpenSupport: () -> Unit = {},
    onOpenAgreement: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    onOpenLicenses: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type

    // 与设置页同一套头部：26sp 蓝色大标题 + 滚动折叠。
    VxCollapsingScaffold(title = "我的", modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = VxSpace.cardInset)) {
            // 头像卡：点它改昵称（原来这个箭头指向一个空回调，属于假入口）
            VxCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onEditName)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VxAvatar(text = userName.take(1), size = 48.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        VxText(text = userName, style = type.rowTitle, color = colors.textPrimary)
                        Spacer(modifier = Modifier.height(2.dp))
                        VxText(
                            text = "本地账户 · 数据不上传",
                            style = type.footnote,
                            color = colors.textSecondary,
                        )
                    }
                    VxIcon(
                        icon = VxIcon.ChevronDown,
                        tint = colors.textTertiary,
                        size = 14.dp,
                        strokeWidth = 1.9.dp,
                        modifier = Modifier.rotate(-90f),
                    )
                }
            }

            VxSectionLabel(text = "使用统计")

            VxCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    TokenMetric(value = stats.sessions.toString(), label = "会话")
                    MetricSeparator()
                    TokenMetric(value = stats.messages.toString(), label = "消息")
                    MetricSeparator()
                    TokenMetric(value = formatTokenCount(stats.totalTokens), label = "Token")
                    MetricSeparator()
                    TokenMetric(value = stats.storage, label = "存储")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            VxCard {
                VxRow(
                    title = "支持我们",
                    subtitle = "反馈、交流或写插件，都是对项目的帮助",
                    icon = VxIcon.Heart,
                    iconTint = VxIconPalette.Appearance.color(),
                    onClick = onOpenSupport,
                )
                VxRow(
                    title = "用户协议",
                    icon = VxIcon.Doc,
                    iconTint = VxIconPalette.Model.color(),
                    onClick = onOpenAgreement,
                )
                VxRow(
                    title = "隐私政策",
                    icon = VxIcon.Shield,
                    iconTint = VxIconPalette.Plugin.color(),
                    onClick = onOpenPrivacy,
                )
                VxRow(
                    title = "开源许可",
                    icon = VxIcon.Info,
                    iconTint = VxIconPalette.About.color(),
                    onClick = onOpenLicenses,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                VxText(
                    text = trf("Velune %s · 本地优先", version),
                    style = type.footnote,
                    color = colors.textTertiary,
                )
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun TokenMetric(value: String, label: String) {
    val colors = VeluneTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        VxText(
            text = value,
            style = VeluneTheme.type.title.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(2.dp))
        VxText(text = label, style = VeluneTheme.type.footnote, color = colors.textSecondary)
    }
}

/** 1.2k / 34.5k 这种紧凑写法，避免四位数把统计行撑爆。 */
internal fun formatTokenCount(value: Long): String = when {
    value < 1_000 -> value.toString()
    value < 1_000_000 -> String.format(java.util.Locale.US, "%.1fk", value / 1000f)
    else -> String.format(java.util.Locale.US, "%.1fm", value / 1_000_000f)
}

@Composable
private fun MetricSeparator() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(26.dp)
            .background(VeluneTheme.colors.divider),
    )
}

@Preview(widthDp = 400, heightDp = 900, showBackground = true)
@Composable
private fun ProfilePreview() {
    VeluneTheme(darkTheme = false) {
        Column(modifier = Modifier.background(VeluneTheme.colors.background)) {
            ProfileScreen()
        }
    }
}
