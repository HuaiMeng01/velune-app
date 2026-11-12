package com.veluneagent.core.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class VeluneColors(
    val background: Color,
    val canvasAlt: Color,
    val surface: Color,
    val surfacePressed: Color,
    val inputSurface: Color,
    val fill: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,

    /**
     * 40% 文字 —— 列表行右侧的"状态值"专用。
     *
     * 小米设置的 `preferenceRightTextColor` 是 `#66000000`（black_level5），
     * 比 [textTertiary]（60%）还浅一档。用 60% 画「已开启」「跟随系统」这类值，
     * 会在视觉上和正文抢注意力，整列看起来比真机"重"。
     */
    val textQuaternary: Color,
    val divider: Color,
    val outline: Color,
    val accent: Color,
    val onAccent: Color,
    val bubbleUser: Color,
    val danger: Color,
    val dangerContainer: Color,
    val success: Color,
    val warning: Color,
    val scrim: Color,
    val isLight: Boolean,
)

val LightColors = VeluneColors(
    background = Color(0xFFF2F2F2),
    canvasAlt = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfacePressed = Color(0xFFF0F0F2),
    inputSurface = Color(0xFFF2F2F2),
    fill = Color(0x0D000000),
    textPrimary = Color(0xFF0A0A0A),
    // 次级 5.2:1 → 8.0:1；三级 2.55:1 → 5.25:1（WCAG AA 需 4.5:1）
    // 原三级色几乎只用于 11sp 的"代价/限制/失败原因"，恰恰是最需要看清的一类文字
    textSecondary = Color(0xB30A0A0A),
    textTertiary = Color(0x990A0A0A),
    textQuaternary = Color(0x660A0A0A),
    divider = Color(0x0F000000),
    outline = Color(0x52000000),
    accent = Color(0xFF3482FF),
    onAccent = Color(0xFFFFFFFF),
    bubbleUser = Color(0xFFD5E3FF),
    danger = Color(0xFFFF3B30),
    dangerContainer = Color(0x14FF3B30),
    success = Color(0xFF34C759),
    warning = Color(0xFFFF9500),
    scrim = Color(0x1F000000),
    isLight = true,
)

val DarkColors = VeluneColors(
    background = Color(0xFF000000),
    canvasAlt = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    surfacePressed = Color(0xFF2C2C2E),
    // 原来与 background 同为 #000000，输入框在暗色下没有边界
    inputSurface = Color(0xFF1C1C1E),
    fill = Color(0x14FFFFFF),
    textPrimary = Color(0xFFF2F2F2),
    // 次级 11:1、三级 5.9:1；原三级 38% 白在纯黑上只有 3.14:1
    textSecondary = Color(0xCCF2F2F2),
    textTertiary = Color(0x99F2F2F2),
    textQuaternary = Color(0x66F2F2F2),
    divider = Color(0x1AFFFFFF),
    outline = Color(0x33FFFFFF),
    accent = Color(0xFF4A90FF),
    onAccent = Color(0xFFFFFFFF),
    bubbleUser = Color(0xFF1D3352),
    danger = Color(0xFFFF453A),
    dangerContainer = Color(0x24FF453A),
    success = Color(0xFF30D158),
    warning = Color(0xFFFF9F0A),
    scrim = Color(0x66000000),
    isLight = false,
)
