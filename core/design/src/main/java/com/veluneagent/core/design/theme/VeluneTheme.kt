package com.veluneagent.core.design.theme

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

val LocalVeluneColors = staticCompositionLocalOf { LightColors }
val LocalVeluneTypography = staticCompositionLocalOf { VeluneType }

object VeluneTheme {
    val colors: VeluneColors
        @Composable @ReadOnlyComposable get() = LocalVeluneColors.current

    val type: VeluneTypography
        @Composable @ReadOnlyComposable get() = LocalVeluneTypography.current
}

/**
 * Velune 色板 → MIUIX ColorScheme 的映射。
 *
 * **只映射有品牌含义的槽位**（primary / error / background / surface / 文字层级 / 线），
 * 其余交给 MIUIX 默认值——默认值本身就是 HyperOS 真机取色，自己再猜一遍只会跑偏。
 *
 * 两套色板本来同源：MIUIX 默认 primary 就是 `#3482FF`，与 [LightColors.accent] 完全一致。
 * 所以这里不是"换肤"，而是把已经对齐的色值接进 MIUIX 的语义槽，
 * 让 Miuix 控件与手写界面取到同一个来源。
 */
/**
 * 用系统动态色（Monet）替换强调色。Android 12 以下没有动态取色，调用方已做版本判断。
 */
@RequiresApi(Build.VERSION_CODES.S)
private fun VeluneColors.withMonetAccent(context: Context, dark: Boolean): VeluneColors {
    val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    return copy(
        accent = scheme.primary,
        bubbleUser = scheme.primaryContainer,
    )
}

internal fun VeluneColors.toMiuixColors(): Colors = if (isLight) {
    lightColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryVariant = accent,
        primaryContainer = bubbleUser,
        onPrimaryContainer = textPrimary,
        error = danger,
        onError = onAccent,
        errorContainer = dangerContainer,
        onErrorContainer = danger,
        background = background,
        onBackground = textPrimary,
        onBackgroundVariant = textSecondary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = inputSurface,
        onSurfaceSecondary = textSecondary,
        onSurfaceVariantSummary = textTertiary,
        onSurfaceVariantActions = textSecondary,
        surfaceContainer = surface,
        onSurfaceContainer = textPrimary,
        surfaceContainerHigh = surfacePressed,
        onSurfaceContainerHigh = textPrimary,
        outline = outline,
        dividerLine = divider,
    )
} else {
    darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryVariant = accent,
        primaryContainer = bubbleUser,
        onPrimaryContainer = textPrimary,
        error = danger,
        onError = onAccent,
        errorContainer = dangerContainer,
        onErrorContainer = danger,
        background = background,
        onBackground = textPrimary,
        onBackgroundVariant = textSecondary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = inputSurface,
        onSurfaceSecondary = textSecondary,
        onSurfaceVariantSummary = textTertiary,
        onSurfaceVariantActions = textSecondary,
        surfaceContainer = surface,
        onSurfaceContainer = textPrimary,
        surfaceContainerHigh = surfacePressed,
        onSurfaceContainerHigh = textPrimary,
        outline = outline,
        dividerLine = divider,
    )
}

/**
 * 主题根。
 *
 * **内层必须是 MIUIX 的 [MiuixTheme]**：所有 Miuix 控件（Card / SmallTitle /
 * BasicComponent / Switch / TextField / TopAppBar / Button）都从
 * `MiuixTheme.colorScheme` 取色。只维护自有色板而不提供 MiuixTheme，
 * 控件与手写部分的色调就会分叉——这是"看起来不像 HyperOS"的根因。
 *
 * 外层保留 Velune 自有 token（[LocalVeluneColors] / [LocalVeluneTypography]），
 * 供聊天页这类高度定制、尚未迁移到 MIUIX 的界面继续使用。
 */
/**
 * 字号总倍率上限。
 *
 * 系统缩放最多约 2.0，应用内最多 1.3，连乘可达 2.6 —— 到这个程度，任何固定高度的行
 * 都放不下文字。1.8 已足够"看得清"，同时保住布局完整。
 */
private const val MaxFontScale = 1.8f

@Composable
fun VeluneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /**
     * 应用内字号倍率。
     *
     * 通过覆写 `LocalDensity.fontScale` 实现，而不是逐个 TextStyle 乘系数：
     * 这样 MIUIX 控件（走自己的 textStyles）和手写界面（走 [VeluneType]）会**同时**缩放，
     * 也不会漏掉任何一个硬编码的 sp —— 逐个改字阶必然会漏。
     */
    fontScale: Float = 1f,
    /**
     * 莫奈取色：用系统壁纸提取的主色替换品牌强调色（Android 12+）。
     *
     * 只覆盖 `accent` / `bubbleUser` 两个槽位，中性色与语义色保持不变——
     * 全盘套用动态色会让危险色、成功色也变成壁纸色，那就不再是同一套语义了。
     */
    monetColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val raw = if (darkTheme) DarkColors else LightColors
    val context = LocalContext.current
    val base = remember(raw, monetColor, darkTheme, context) {
        if (monetColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            raw.withMonetAccent(context, darkTheme)
        } else {
            raw
        }
    }
    val miuixColors = remember(base) { base.toMiuixColors() }
    val density = LocalDensity.current
    val scaledDensity = remember(density, fontScale) {
        // 系统字号（最大 2.0 左右）× 应用字号（最大 1.3）连乘能到 2.6 倍，
        // 固定高度的顶栏/菜单行根本放不下，文字会被裁掉半截。
        // 上限 1.8 是折中：仍然明显放大（相当于系统 1.8 倍），但一行还塞得下。
        // 另外，固定高度的组件已经改成 heightIn(min = …)，两者配合才不会裁字。
        val combined = (density.fontScale * fontScale).coerceAtMost(MaxFontScale)
        if (combined == density.fontScale) density else Density(density.density, combined)
    }
    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalVeluneColors provides base,
        LocalVeluneTypography provides VeluneType,
    ) {
        MiuixTheme(colors = miuixColors) {
            content()
        }
    }
}

@Composable
@ReadOnlyComposable
fun veluneColors(): VeluneColors = LocalVeluneColors.current

@Composable
@ReadOnlyComposable
fun veluneType(): VeluneTypography = LocalVeluneTypography.current
