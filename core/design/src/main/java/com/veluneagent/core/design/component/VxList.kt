package com.veluneagent.core.design.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veluneagent.core.design.shape.SquircleShape
import com.veluneagent.core.design.theme.VeluneTheme
import com.veluneagent.core.design.theme.VxMotion
import com.veluneagent.core.design.theme.VxRadius
import com.veluneagent.core.design.theme.VxSize
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward

/**
 * 分组卡片。
 *
 * 自己画，不再转交 MIUIX `Card`。原因有两个：
 * 1. 圆角必须是 20dp（真机实测），MIUIX 的默认值不受我们控制，改 token 也影响不到它；
 * 2. 真机卡内**没有分割线**，MIUIX `Card` 会把子项之间的分隔线画出来。
 *
 * 卡片自己不带内边距 —— 行组件（[VxRow]）自带 16dp，若这里再加会变成 32dp。
 */
@Composable
fun VxCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = VeluneTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SquircleShape(VxRadius.card))
            .background(colors.surface),
        content = content,
    )
}

/**
 * 分组标签。
 *
 * 14sp Medium，`textTertiary`。缩进 16dp 与行内图标左缘对齐（卡片外距 12 + 卡内 16 = 28dp，
 * 与真机图标距屏幕左边 27.8dp 一致）。原实现用 MIUIX `SmallTitle`，字号 13sp 且带字距。
 */@Composable
fun VxSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    VxText(
        text = text,
        style = VeluneTheme.type.sectionLabel,
        color = VeluneTheme.colors.textTertiary,
        modifier = modifier.padding(
            start = VxSize.rowPaddingHorizontal,
            top = 8.dp,
            bottom = 6.dp,
        ),
    )
}

/**
 * 表单/控件块：`VxCard` + 16dp 内边距。
 *
 * 卡片外距由页面统一给 12dp，块内再 16dp，内容左缘落在 28dp —— 与 [VxRow] 的图标列、
 * [VxSectionLabel] 同一基准。表单页的输入框、分段选择与说明文字都用它承载。
 */
@Composable
fun VxFormCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    VxCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

/** 28dp 彩色 squircle 图标容器 + 16dp 白色描边图形（真机同构：方块 28dp、圆角 9dp、glyph 16dp）。 */
@Composable
fun VxBadge(
    icon: VxIcon,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(VxSize.iconContainer)
            .clip(SquircleShape(VxRadius.icon))
            .background(tint),
        contentAlignment = Alignment.Center,
    ) {
        VxIcon(icon = icon, tint = Color.White, size = VxSize.iconGlyph, strokeWidth = 1.7.dp)
    }
}

/**
 * 设置类列表行。
 *
 * 尺寸与反馈全部按小米设置实测值铺：
 * - 最小高 56dp，上下 14dp，左右 16dp，图标 28dp + 间距 16dp；
 * - 标题 17sp Medium、副标题 14sp、右侧值 14sp @40%；
 * - 点按是**纯色叠加层**（不是 ripple）：按下 12% 黑，进入走欠阻尼弹簧（会轻微过冲），
 *   松手 350ms 正弦缓出。
 *
 * 参数保持不变（31 处调用不动）。
 */
@Composable
fun VxRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: VxIcon? = null,
    iconTint: Color = VeluneTheme.colors.textSecondary,
    valueText: String? = null,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val overlay by animateFloatAsState(
        targetValue = if (pressed) VxMotion.PressAlpha else 0f,
        animationSpec = if (pressed) {
            VxMotion.PressEnter
        } else {
            tween(durationMillis = VxMotion.PressExitMs, easing = VxMotion.EaseOutSine)
        },
        label = "VxRowPressOverlay",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = VxSize.rowSingle)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .drawWithContent {
                drawContent()
                if (overlay > 0f) {
                    drawRect(VxMotion.pressTint(colors.isLight).copy(alpha = overlay))
                }
            }
            .padding(
                horizontal = VxSize.rowPaddingHorizontal,
                vertical = VxSize.rowPaddingVertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            VxBadge(icon = icon, tint = iconTint)
            Spacer(modifier = Modifier.width(VxSize.iconGap))
        }

        Column(modifier = Modifier.weight(1f)) {
            VxText(
                text = title,
                style = type.rowTitle,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                VxText(
                    text = subtitle,
                    style = type.value,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (trailing != null) {
            trailing()
        } else {
            if (valueText != null) {
                VxText(
                    text = valueText,
                    style = type.value,
                    color = colors.textQuaternary,
                    maxLines = 1,
                )
            }
            if (showChevron) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = MiuixIcons.ChevronForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colors.textQuaternary,
                )
            }
        }
    }
}

/** 供行内右侧放自定义内容时复用：与右侧状态值同一档灰，避免又冒出一个硬编码色。 */
@Composable
internal fun RowScope.VxActionHint(text: String) {
    VxText(
        text = text,
        style = VeluneTheme.type.value,
        color = VeluneTheme.colors.textQuaternary,
        maxLines = 1,
    )
}
