package com.veluneagent.feature.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.veluneagent.core.data.profile.AppearancePrefs
import com.veluneagent.core.design.component.VxCard
import com.veluneagent.core.design.component.VxSlider
import com.veluneagent.core.design.component.VxRow
import com.veluneagent.core.design.component.VxSectionLabel
import com.veluneagent.core.design.component.VxDropdownMenu
import com.veluneagent.core.design.component.VxSubPageScaffold
import com.veluneagent.core.design.component.VxSwitch
import com.veluneagent.core.design.component.VxText
import com.veluneagent.core.design.shape.SquircleShape
import com.veluneagent.core.design.theme.VeluneTheme
import com.veluneagent.core.design.theme.VxRadius
import com.veluneagent.core.design.theme.VxSpace

/**
 * 应用偏好设置（替代原「外观」页）。
 *
 * 结构照小米设置同名页面：返回条 → 大标题 → 两张主题预览卡 → 开关组 → 应用设置组。
 * 主题卡是"所见即所选"：卡片本身用当前色板画一份列表缩略图，不用先切过去才知道深色长什么样。
 */
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type

    val themeMode by AppearancePrefs.themeMode.collectAsStateWithLifecycle()
    val autoDark by AppearancePrefs.autoDark.collectAsStateWithLifecycle()
    val monetColor by AppearancePrefs.monetColor.collectAsStateWithLifecycle()
    val language by AppearancePrefs.language.collectAsStateWithLifecycle()
    val fontScale by AppearancePrefs.fontScale.collectAsStateWithLifecycle()

    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    // 自动切换打开时，卡片显示的是系统当前实际模式，而不是用户上次点的那张
    val darkNow = if (autoDark) systemDark else themeMode == AppearancePrefs.ThemeMode.Dark

    var languageOpen by remember { mutableStateOf(false) }
    val monetSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val fontLabel = AppearancePrefs.FontScaleOptions
        .firstOrNull { kotlin.math.abs(it.first - fontScale) < 0.01f }?.second ?: "标准"

    VxSubPageScaffold(title = "", onBack = onBack, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = VxSpace.subPageInset),
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            VxText(
                text = "应用偏好设置",
                style = type.largeTitle,
                color = colors.textPrimary,
                modifier = Modifier.padding(start = 14.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemePreviewCard(
                    dark = false,
                    selected = !darkNow,
                    onClick = {
                        AppearancePrefs.setAutoDark(false)
                        AppearancePrefs.setThemeMode(AppearancePrefs.ThemeMode.Light)
                    },
                    modifier = Modifier.weight(1f),
                )
                ThemePreviewCard(
                    dark = true,
                    selected = darkNow,
                    onClick = {
                        AppearancePrefs.setAutoDark(false)
                        AppearancePrefs.setThemeMode(AppearancePrefs.ThemeMode.Dark)
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeCaption("浅色模式", !darkNow, Modifier.weight(1f))
                ThemeCaption("深色模式", darkNow, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(18.dp))

            VxCard {
                VxRow(
                    title = "自动切换深色模式",
                    showChevron = false,
                    trailing = {
                        VxSwitch(
                            checked = autoDark,
                            onCheckedChange = { AppearancePrefs.setAutoDark(it) },
                        )
                    },
                )
                VxRow(
                    title = "莫奈取色",
                    subtitle = if (monetSupported) {
                        "跟随系统壁纸对应用进行配色"
                    } else {
                        "需要 Android 12 及以上系统"
                    },
                    showChevron = false,
                    trailing = {
                        VxSwitch(
                            checked = monetColor && monetSupported,
                            enabled = monetSupported,
                            onCheckedChange = { AppearancePrefs.setMonetColor(it) },
                        )
                    },
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            VxSectionLabel(text = "应用设置")

            VxCard {
                // 少量选项用行内下拉（真机同款），不用底部弹层：底部弹层要抬手、且会盖住上下文
                Box(modifier = Modifier.fillMaxWidth()) {
                    VxRow(
                        title = "语言",
                        subtitle = "切换应用界面显示语言",
                        valueText = language.label,
                        onClick = { languageOpen = true },
                    )
                    VxDropdownMenu(
                        expanded = languageOpen,
                        options = AppearancePrefs.Language.entries.map { it.label },
                        selectedIndex = AppearancePrefs.Language.entries.indexOf(language),
                        onSelect = { AppearancePrefs.setLanguage(AppearancePrefs.Language.entries[it]) },
                        onDismiss = { languageOpen = false },
                    )
                }

                // 字号：HyperOS 同款离散滑杆。拖动时吸附档位、头顶气泡报档位，
                // 松手生效；档位值与旧下拉菜单一致（小/标准/大/特大）。
                VxRow(
                    title = "字号",
                    showChevron = false,
                    valueText = fontLabel,
                )
                VxSlider(
                    labels = AppearancePrefs.FontScaleOptions.map { it.second },
                    selectedIndex = AppearancePrefs.FontScaleOptions
                        .indexOfFirst { kotlin.math.abs(it.first - fontScale) < 0.01f }
                        .coerceAtLeast(0),
                    onSelect = { AppearancePrefs.setFontScale(AppearancePrefs.FontScaleOptions[it].first) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            VxText(
                text = "界面已支持中英双语，选择即时生效；「跟随系统」时与设备语言保持一致。" +
                    "字号总放大上限为 1.8 倍（系统字号会与应用字号相乘）。",
                style = type.footnote,
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

}

/**
 * 主题预览卡。数值来自真机 `com.android.settings` 解包：
 * `widget_light_dark_mode_top.xml` + `dark_mode_*.dimen` + `image_light/dark_mode_top.png`。
 *
 * - 卡片 **170×170dp 正方形**（`dark_mode_view_width/height`），圆角 **20dp**（`dark_mode_picture_radius`）；
 * - 两卡间距 4dp + 各自 6dp 内留白 = 视觉 16dp；标签 16sp、上边距 8dp；
 * - **两张卡渐变完全相同**：`#4172FF → #59B8FF`，深浅只由内块表达——
 *   浅色卡内块 white 20%（更亮）、深色卡内块 black 20%（更暗），横线极性随之相反；
 * - 内块比例取自 PNG：左右 7.1%、上下 21.8%、块高 25.7%、间隙 5%。
 */
@Composable
private fun ThemePreviewCard(
    dark: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VeluneTheme.colors
    val shape = SquircleShape(VxRadius.card)

    // 用 accent 派生而不是写死色值：开了莫奈取色后主题卡会跟着壁纸色走
    val top = lerp(colors.accent, Color(0xFF4172FF), 0.75f)
    val bottom = lerp(colors.accent, Color(0xFF59B8FF), 0.6f)

    // 深色卡的内块是「深蓝」而不是「蒙黑」：黑叠加会发灰，真机是 #2F63CB 这类饱和深蓝
    val blockColor = if (dark) lerp(colors.accent, Color(0xFF2F63CB), 0.70f) else Color.White.copy(alpha = 0.22f)
    val lineColor = if (dark) Color.White.copy(alpha = 0.38f) else Color.Black.copy(alpha = 0.18f)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .then(
                if (selected) Modifier.border(2.dp, colors.accent, shape) else Modifier,
            )
            // 真机是 6dp 内留白（给选中描边+间隙留位）
            .padding(4.dp)
            .clip(SquircleShape(18.dp))
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val unit = maxWidth
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = unit * 0.071f, vertical = unit * 0.218f),
                verticalArrangement = Arrangement.spacedBy(unit * 0.05f),
            ) {
                MockBlock(Modifier.weight(1f), blockColor, lineColor, unit * 0.035f)
                MockBlock(Modifier.weight(1f), blockColor, lineColor, unit * 0.035f)
            }
        }
    }
}

/** 缩略图里的一块"列表卡"：半透明底 + 两条占位文本。 */
@Composable
private fun MockBlock(
    modifier: Modifier = Modifier,
    blockColor: Color,
    lineColor: Color,
    corner: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .background(blockColor)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            MockLine(0.72f, lineColor)
            MockLine(0.90f, lineColor)
        }
    }
}

@Composable
private fun MockLine(fraction: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth(fraction)
            .height(2.5.dp)
            .clip(RoundedCornerShape(1.25.dp))
            .background(color),
    )
}

/** 卡片下方标签：16sp（真机 `dark_mode_textSize`），选中为主题色。 */
@Composable
private fun ThemeCaption(text: String, selected: Boolean, modifier: Modifier = Modifier) {
    val colors = VeluneTheme.colors
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        VxText(
            text = text,
            style = VeluneTheme.type.body,
            color = if (selected) colors.accent else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
