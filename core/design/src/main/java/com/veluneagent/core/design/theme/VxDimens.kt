package com.veluneagent.core.design.theme

import androidx.compose.ui.unit.dp

/**
 * 间距标尺。
 *
 * 基准 4dp。[cardInset] 是卡片外距 12dp（小米设置实测 38px ÷ 3.196 px/dp = 11.9dp）；
 * [l] = 14dp 是迁移前旧值，仍被部分页面引用，**新代码不要再用**（破坏 4dp 栅格）。
 */
object VxSpace {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp

    /** 卡片左右外距：12dp。列表卡一律用它。 */
    val cardInset = 12.dp

    /** 大标题左缩进 28dp（实测 95px ÷ 3.196 = 29.7dp）= 卡片 12 + 行内 16，与图标列对齐。 */
    val titleInset = 28.dp

    /** 折叠后顶栏高度 56dp。 */
    val collapsedBarHeight = 56.dp

    /** 次级页（协议/许可等）正文左右内边距 16dp：正文行宽比列表更宽，读起来更顺。 */
    val subPageInset = 16.dp

    /** 旧值 14dp，非 4dp 栅格，仅保留兼容。 */
    val l = 14.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/**
 * 圆角标尺，全部用于 SquircleShape。
 *
 * [card] = 20dp：小米设置分组卡片实测（`card_radius_global` 20dp，截图反算一致）。
 * 迁移前是 16dp，比真机"方"一档。
 */
object VxRadius {
    val card = 20.dp
    val icon = 9.dp
    val inner = 12.dp
    val sheet = 28.dp
    val button = 16.dp
    val input = 26.dp
}

/** 尺寸标尺。行与图标的数值由真机截图反算（行高 179px ÷ 56dp = 3.196 px/dp）。 */
object VxSize {
    /** 单行最小高 56dp；带副标题时自然增高到约 69dp（14+22+19+14）。 */
    val rowSingle = 56.dp
    val rowDouble = 72.dp

    /** 行内左右内边距：16dp（实测图标距卡片左缘 16.0dp）。 */
    val rowPaddingHorizontal = 16.dp

    /** 行内上下内边距：14dp。 */
    val rowPaddingVertical = 14.dp

    /** 图标方块 28dp（实测 89px ÷ 3.196 = 27.8dp）。 */
    val iconContainer = 28.dp

    /** 图标 → 文字间距：16dp（实测 54px）。 */
    val iconGap = 16.dp

    val iconGlyph = 16.dp
    val hitTarget = 48.dp
    val touchButton = 38.dp
    val sendButton = 38.dp
    val contextRing = 22.dp
}
