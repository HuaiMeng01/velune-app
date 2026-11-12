package com.veluneagent.core.design.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.veluneagent.core.design.l10n.tr
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 胶囊小按钮：图标 + 文字，用于消息下方等次要动作。
 *
 * 转交 MIUIX [Button]：高度、圆角（= 高度的一半）、按压反馈、禁用色板都由 MIUIX 提供。
 * `active` 用 `primaryContainer` 表达"当前生效"，不再用绿色——绿色在色板里是
 * 状态色（成功），拿它表示"选中"会和错误/成功语义打架。
 */
@Composable
fun VxPillButton(
    icon: VxIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 28.dp,
    active: Boolean = false,
) {
    val miuix = MiuixTheme.colorScheme
    val colors = if (active) {
        ButtonDefaults.buttonColors(
            color = miuix.primaryContainer,
            // 用 onPrimaryContainer 而不是 primary：#3482FF 落在 #D5E3FF 上只有 3.0:1，
            // 13sp 文字不达标；深色文字在浅蓝底上是 12:1 以上
            contentColor = miuix.onPrimaryContainer,
        )
    } else {
        ButtonDefaults.buttonColors(
            color = miuix.secondaryContainer,
            contentColor = miuix.onSurfaceVariantActions,
        )
    }

    Button(
        onClick = onClick,
        modifier = modifier,
        colors = colors,
        minHeight = height,
        cornerRadius = height / 2,
        insideMargin = PaddingValues(horizontal = 10.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.contentColor) {
            VxIcon(icon = icon, tint = colors.contentColor, size = 13.dp, strokeWidth = 1.9.dp)
            Spacer(modifier = Modifier.width(5.dp))
            Text(text = tr(label), style = MiuixTheme.textStyles.footnote1, maxLines = 1)
        }
    }
}
