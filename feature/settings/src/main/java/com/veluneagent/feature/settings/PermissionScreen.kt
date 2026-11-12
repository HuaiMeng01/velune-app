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
import androidx.compose.ui.unit.dp
import com.veluneagent.core.design.component.VxCard
import com.veluneagent.core.design.component.VxIcon
import com.veluneagent.core.design.component.VxRow
import com.veluneagent.core.design.component.VxSectionLabel
import com.veluneagent.core.design.component.VxText
import com.veluneagent.core.design.theme.VxIconPalette
import com.veluneagent.core.design.theme.VeluneTheme

data class PermissionEntry(
    val title: String,
    val why: String,
    val granted: Boolean,
)

/**
 * 权限中心。
 *
 * 只展示「Agent 真正会用到的权限」，并说清每种权限换来的能力。
 * 不做「一键全部授权」：批量同意会让用户失去判断，权限页就失去了意义。
 */
@Composable
fun PermissionScreen(
    entries: List<PermissionEntry>,
    onRequest: (Int) -> Unit,
    onOpenSystemSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** 设备管理员是否已激活：锁屏能力的唯一前置条件 */
    adminActive: Boolean = false,
    onRequestAdmin: () -> Unit = {},
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        SettingsTopBar(title = "权限与能力", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            VxSectionLabel(text = "Agent 可申请")

            VxCard {
                entries.forEachIndexed { index, entry ->
                    VxRow(
                        title = entry.title,
                        subtitle = entry.why,
                        icon = if (entry.granted) VxIcon.Check else VxIcon.Shield,
                        iconTint = if (entry.granted) colors.success else colors.warning,
                        valueText = if (entry.granted) "已授权" else "去授权",
                        showChevron = !entry.granted,
                        onClick = if (entry.granted) null else ({ onRequest(index) }),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            VxSectionLabel(text = "特殊权限")
            VxCard {
                VxRow(
                    title = "锁屏（设备管理员）",
                    subtitle = if (adminActive) {
                        "已激活：可以直接对 Agent 说「锁屏」"
                    } else {
                        "Android 没有普通锁屏接口，需要激活设备管理员；只申请锁屏，不涉及数据"
                    },
                    icon = VxIcon.Shield,
                    iconTint = if (adminActive) colors.success else VxIconPalette.Permission.color(),
                    valueText = if (adminActive) "已激活" else "去激活",
                    showChevron = !adminActive,
                    // lambda 体必须真正调用函数：({ onRequestAdmin }) 只是取出引用再丢弃，
                    // 点击不会有任何效果
                    onClick = if (adminActive) null else onRequestAdmin,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            VxCard {
                VxRow(
                    title = "打开系统设置",
                    subtitle = "权限被永久拒绝时，需要到系统里手动开启",
                    icon = VxIcon.Sliders,
                    iconTint = VxIconPalette.About.color(),
                    onClick = onOpenSystemSettings,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            VxText(
                text = "所有权限都在真正用到时才申请：不建日程就不会要日历权限，不发通知就不会要通知权限。",
                style = type.footnote,
                color = colors.textTertiary,
            )
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
