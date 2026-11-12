package com.veluneagent.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.veluneagent.core.design.l10n.tr
import com.veluneagent.core.design.theme.VeluneTheme

/**
 * 统一文本入口。页面层不使用 material3 的 Text，避免行高与颜色被外部主题覆盖。
 *
 * 全部文案在渲染点过 [tr]：中文模式原样、英文模式查词典回落。
 * 这意味着 VxRow / VxSectionLabel / VxEmptyState / VxSnackbar 等一切走 VxText 的
 * 组件无需各自适配——传中文原文进来即可，词典命中则译、未命中回落，永不空白。
 */
@Composable
fun VxText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = VeluneTheme.type.body,
    color: Color = VeluneTheme.colors.textPrimary,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    // CJK 字形自带上下留白，开启 font padding 会让气泡上下不对称，
    // 统一关掉，靠 lineHeight 控制节奏。
    Text(
        // 语言翻译在这里统一做：key = 中文原文，tr 命中词典则译、未命中原样回落。
        // 调用点不需要预先翻译；已 tr 过的英文串再进来查不到词典，也会原样返回（幂等）。
        text = tr(text),
        modifier = modifier,
        style = style.copy(
            color = color,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
        maxLines = maxLines,
        overflow = overflow,
    )
}

/**
 * 图标按钮。点击区固定 38dp（视觉可更小），使用自定义 ripple。
 *
 * [contentDescription] 是给读屏用的。纯图标按钮**必须**传：Canvas 自绘的 VxIcon
 * 不产生任何语义节点，不传的话 TalkBack 只会念「按钮，双击即可激活」，
 * 用户分不清这是返回、新建还是关闭。
 */
@Composable
fun VxIconButton(
    icon: VxIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = VeluneTheme.colors.textSecondary,
    enabled: Boolean = true,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    background: Color = Color.Transparent,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = tint.copy(alpha = 0.10f), bounded = true),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                if (!contentDescription.isNullOrBlank()) this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        VxIcon(
            icon = icon,
            tint = if (enabled) tint else tint.copy(alpha = 0.35f),
            size = iconSize,
        )
    }
}
