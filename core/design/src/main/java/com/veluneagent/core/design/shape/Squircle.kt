package com.veluneagent.core.design.shape

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * 连续曲率圆角（G2 近似），四角可分别指定半径。
 *
 * 与 RoundedCornerShape 的差别：直线与圆弧之间用超椭圆指数过渡，没有硬切点，
 * 视觉上更「收」。同数值下视觉面积大于普通圆角，token 应比 Material 习惯值小 2–4dp。
 */
class SquircleCorners(
    private val topStart: Dp = 16.dp,
    private val topEnd: Dp = 16.dp,
    private val bottomEnd: Dp = 16.dp,
    private val bottomStart: Dp = 16.dp,
    private val smoothness: Float = 4f,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val w = size.width
        val h = size.height
        val maxR = min(w, h) / 2f

        fun px(d: Dp): Float = with(density) { d.toPx() }.coerceIn(0f, maxR)

        val rtl = layoutDirection == LayoutDirection.Rtl
        val rStart = if (rtl) px(topEnd) else px(topStart)
        val rEnd = if (rtl) px(topStart) else px(topEnd)
        val rBottomStart = if (rtl) px(bottomEnd) else px(bottomStart)
        val rBottomEnd = if (rtl) px(bottomStart) else px(bottomEnd)

        if (rStart == 0f && rEnd == 0f && rBottomStart == 0f && rBottomEnd == 0f) {
            return Outline.Rectangle(Rect(Offset.Zero, size))
        }

        val p = 2f / smoothness
        val steps = 18
        val half = (PI / 2).toFloat()
        val path = Path()

        // 浮点误差会让 cos(π/2) 落到 -4e-8，负底数取分数次幂得到 NaN，
        // 整个 Path 随之失效（裁剪区域为空 → 背景与内容一起消失）。必须夹到 0。
        fun comp(angle: Float, useCos: Boolean): Float {
            val v = if (useCos) cos(angle) else sin(angle)
            return v.coerceAtLeast(0f).pow(p)
        }

        path.moveTo(rStart, 0f)
        path.lineTo(w - rEnd, 0f)

        // 右上角：(w-rEnd, 0) -> (w, rEnd)
        if (rEnd > 0f) {
            for (i in 1..steps) {
                val t = half * i / steps
                path.lineTo(
                    w - rEnd + rEnd * comp(t, useCos = false),
                    rEnd - rEnd * comp(t, useCos = true),
                )
            }
        }

        path.lineTo(w, h - rBottomEnd)

        // 右下角：(w, h-rBottomEnd) -> (w-rBottomEnd, h)
        if (rBottomEnd > 0f) {
            for (i in 1..steps) {
                val t = half * i / steps
                path.lineTo(
                    w - rBottomEnd + rBottomEnd * comp(t, useCos = true),
                    h - rBottomEnd + rBottomEnd * comp(t, useCos = false),
                )
            }
        }

        path.lineTo(rBottomStart, h)

        // 左下角：(rBottomStart, h) -> (0, h-rBottomStart)
        if (rBottomStart > 0f) {
            for (i in 1..steps) {
                val t = half * i / steps
                path.lineTo(
                    rBottomStart - rBottomStart * comp(t, useCos = false),
                    h - rBottomStart + rBottomStart * comp(t, useCos = true),
                )
            }
        }

        path.lineTo(0f, rStart)

        // 左上角：(0, rStart) -> (rStart, 0)
        if (rStart > 0f) {
            for (i in 1..steps) {
                val t = half * i / steps
                path.lineTo(
                    rStart - rStart * comp(t, useCos = true),
                    rStart - rStart * comp(t, useCos = false),
                )
            }
        }

        path.close()
        return Outline.Generic(path)
    }
}

/** 四角同半径。 */
class SquircleShape(
    private val radius: Dp = 16.dp,
    private val smoothness: Float = 4f,
) : Shape {
    private val delegate = SquircleCorners(radius, radius, radius, radius, smoothness)

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = delegate.createOutline(size, layoutDirection, density)
}

/** 胶囊形（连续曲率），用于浮动导航、标签、输入容器。 */
class SquircleCapsule(private val smoothness: Float = 4f) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val radius = with(density) { (size.height / 2f).toDp() }
        return SquircleShape(radius, smoothness).createOutline(size, layoutDirection, density)
    }
}
