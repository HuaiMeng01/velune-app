package com.veluneagent.core.design.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 图标全部用 Canvas 自绘，不依赖 material-icons，也不受系统图标风格影响。
 * 坐标系统一 24×24，与常见图标网格一致，便于后续替换为 SVG 资源。
 */
enum class VxIcon {
    Plus, ChevronDown, ChevronUp, ChevronRight, Sparkles, Mic, MicOff, SendUp,
    Warning, Stop, Check, Search, Model, Paperclip, Close, Copy, Refresh,
    Message, Sliders, Person, Heart, Shield, Chart, Info, Bell, Back, Key, Palette, Grid, Database, Doc,
    Pin, Pencil, Trash, Play, Pause,
}

@Composable
fun VxIcon(
    icon: VxIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    strokeWidth: Dp = 2.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val sw = strokeWidth.toPx()
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, p(x1, y1), p(x2, y2), strokeWidth = sw, cap = StrokeCap.Round)

        when (icon) {
            VxIcon.Plus -> {
                line(12f, 5f, 12f, 19f)
                line(5f, 12f, 19f, 12f)
            }

            VxIcon.ChevronDown -> {
                val path = Path().apply {
                    moveTo(6f * u, 9.5f * u)
                    lineTo(12f * u, 15.5f * u)
                    lineTo(18f * u, 9.5f * u)
                }
                drawPath(path, tint, style = stroke)
            }

            VxIcon.ChevronUp -> {
                val path = Path().apply {
                    moveTo(6f * u, 15f * u)
                    lineTo(12f * u, 9f * u)
                    lineTo(18f * u, 15f * u)
                }
                drawPath(path, tint, style = stroke)
            }

            VxIcon.ChevronRight -> {
                val path = Path().apply {
                    moveTo(9.5f * u, 6f * u)
                    lineTo(15.5f * u, 12f * u)
                    lineTo(9.5f * u, 18f * u)
                }
                drawPath(path, tint, style = stroke)
            }

            VxIcon.SendUp -> {
                line(12f, 19f, 12f, 5f)
                val path = Path().apply {
                    moveTo(6f * u, 11f * u)
                    lineTo(12f * u, 5f * u)
                    lineTo(18f * u, 11f * u)
                }
                drawPath(
                    path,
                    tint,
                    style = Stroke(width = sw * 1.1f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }

            VxIcon.Sparkles -> {
                // 主四角星
                val star = Path().apply {
                    moveTo(11f * u, 2.5f * u)
                    cubicTo(
                        11.7f * u, 7.6f * u, 12.4f * u, 8.3f * u, 17.5f * u, 9f * u,
                    )
                    cubicTo(
                        12.4f * u, 9.7f * u, 11.7f * u, 10.4f * u, 11f * u, 15.5f * u,
                    )
                    cubicTo(
                        10.3f * u, 10.4f * u, 9.6f * u, 9.7f * u, 4.5f * u, 9f * u,
                    )
                    cubicTo(
                        9.6f * u, 8.3f * u, 10.3f * u, 7.6f * u, 11f * u, 2.5f * u,
                    )
                    close()
                }
                drawPath(star, tint, style = Fill)
                // 小星
                val small = Path().apply {
                    moveTo(17.5f * u, 14f * u)
                    cubicTo(
                        17.9f * u, 16.6f * u, 18.4f * u, 17.1f * u, 21f * u, 17.5f * u,
                    )
                    cubicTo(
                        18.4f * u, 17.9f * u, 17.9f * u, 18.4f * u, 17.5f * u, 21f * u,
                    )
                    cubicTo(
                        17.1f * u, 18.4f * u, 16.6f * u, 17.9f * u, 14f * u, 17.5f * u,
                    )
                    cubicTo(
                        16.6f * u, 17.1f * u, 17.1f * u, 16.6f * u, 17.5f * u, 14f * u,
                    )
                    close()
                }
                drawPath(small, tint, style = Fill)
            }

            VxIcon.Mic -> {
                drawRoundRect(
                    color = tint,
                    topLeft = p(9f, 2.5f),
                    size = Size(6f * u, 11f * u),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * u),
                    style = stroke,
                )
                val arcRect = androidx.compose.ui.geometry.Rect(
                    offset = p(5.5f, 7.5f),
                    size = Size(13f * u, 13f * u),
                )
                drawArc(
                    color = tint,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = arcRect.topLeft,
                    size = arcRect.size,
                    style = stroke,
                )
                line(12f, 20.5f, 12f, 22f)
            }

            VxIcon.MicOff -> {
                drawRoundRect(
                    color = tint,
                    topLeft = p(9f, 2.5f),
                    size = Size(6f * u, 11f * u),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * u),
                    style = stroke,
                )
                drawArc(
                    color = tint,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = p(5.5f, 7.5f),
                    size = Size(13f * u, 13f * u),
                    style = stroke,
                )
                line(5f, 21f, 19f, 3f)
            }

            VxIcon.Warning -> {
                drawCircle(tint, radius = 9f * u, center = p(12f, 12f), style = stroke)
                line(12f, 7f, 12f, 13f)
                drawCircle(tint, radius = 1.1f * u, center = p(12f, 16.4f))
            }

            VxIcon.Stop -> {
                drawRoundRect(
                    color = tint,
                    topLeft = p(8f, 8f),
                    size = Size(8f * u, 8f * u),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * u),
                    style = Fill,
                )
            }

            VxIcon.Check -> {
                val path = Path().apply {
                    moveTo(5f * u, 12.5f * u)
                    lineTo(10f * u, 17.5f * u)
                    lineTo(19f * u, 6.5f * u)
                }
                drawPath(path, tint, style = stroke)
            }

            VxIcon.Search -> {
                drawCircle(tint, radius = 6.5f * u, center = p(11f, 11f), style = stroke)
                line(15.5f, 15.5f, 20f, 20f)
            }

            VxIcon.Model -> {
                // 一个简化的立方体轮廓，表示模型
                val path = Path().apply {
                    moveTo(12f * u, 3f * u)
                    lineTo(20f * u, 7.5f * u)
                    lineTo(20f * u, 16.5f * u)
                    lineTo(12f * u, 21f * u)
                    lineTo(4f * u, 16.5f * u)
                    lineTo(4f * u, 7.5f * u)
                    close()
                }
                drawPath(path, tint, style = stroke)
                line(4f, 7.5f, 12f, 12f)
                line(20f, 7.5f, 12f, 12f)
                line(12f, 12f, 12f, 21f)
            }

            VxIcon.Paperclip -> {
                val path = Path().apply {
                    moveTo(16.5f * u, 7f * u)
                    lineTo(8.5f * u, 15f * u)
                    cubicTo(
                        6.5f * u, 17f * u, 9f * u, 19.5f * u, 11f * u, 17.5f * u,
                    )
                    lineTo(19f * u, 9.5f * u)
                    cubicTo(
                        21f * u, 7.5f * u, 18.5f * u, 5f * u, 16.5f * u, 7f * u,
                    )
                    close()
                }
                drawPath(path, tint, style = stroke)
            }


            VxIcon.Message -> {
                drawRoundRect(
                    color = tint,
                    topLeft = p(4f, 5f),
                    size = Size(16f * u, 12f * u),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.5f * u),
                    style = stroke,
                )
                val tail = Path().apply {
                    moveTo(9f * u, 17f * u)
                    lineTo(9f * u, 20f * u)
                    lineTo(13f * u, 17f * u)
                }
                drawPath(tail, tint, style = stroke)
            }

            VxIcon.Sliders -> {
                line(4f, 7f, 20f, 7f)
                line(4f, 12f, 20f, 12f)
                line(4f, 17f, 20f, 17f)
                drawCircle(tint, radius = 2.1f * u, center = p(9f, 7f))
                drawCircle(tint, radius = 2.1f * u, center = p(15f, 12f))
                drawCircle(tint, radius = 2.1f * u, center = p(9f, 17f))
            }

            VxIcon.Person -> {
                drawCircle(tint, radius = 3.4f * u, center = p(12f, 8.2f), style = stroke)
                val body = Path().apply {
                    moveTo(4.8f * u, 19.5f * u)
                    cubicTo(5.6f * u, 15.6f * u, 8.4f * u, 13.9f * u, 12f * u, 13.9f * u)
                    cubicTo(15.6f * u, 13.9f * u, 18.4f * u, 15.6f * u, 19.2f * u, 19.5f * u)
                }
                drawPath(body, tint, style = stroke)
            }

            VxIcon.Heart -> {
                val heart = Path().apply {
                    moveTo(12f * u, 20.2f * u)
                    cubicTo(3.4f * u, 14.4f * u, 4.6f * u, 6.6f * u, 9f * u, 5.9f * u)
                    cubicTo(10.8f * u, 5.6f * u, 12f * u, 7.1f * u, 12f * u, 8.2f * u)
                    cubicTo(12f * u, 7.1f * u, 13.2f * u, 5.6f * u, 15f * u, 5.9f * u)
                    cubicTo(19.4f * u, 6.6f * u, 20.6f * u, 14.4f * u, 12f * u, 20.2f * u)
                    close()
                }
                drawPath(heart, tint, style = stroke)
            }

            VxIcon.Shield -> {
                val shield = Path().apply {
                    moveTo(12f * u, 3f * u)
                    lineTo(20f * u, 6.2f * u)
                    lineTo(20f * u, 12f * u)
                    cubicTo(20f * u, 17f * u, 16.4f * u, 20f * u, 12f * u, 21f * u)
                    cubicTo(7.6f * u, 20f * u, 4f * u, 17f * u, 4f * u, 12f * u)
                    lineTo(4f * u, 6.2f * u)
                    close()
                }
                drawPath(shield, tint, style = stroke)
            }

            VxIcon.Chart -> {
                drawRoundRect(
                    color = tint,
                    topLeft = p(4.5f, 13f),
                    size = Size(3.6f * u, 7.2f * u),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.4f * u),
                )
                drawRoundRect(
                    color = tint,
                    topLeft = p(10.2f, 9f),
                    size = Size(3.6f * u, 11.2f * u),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.4f * u),
                )
                drawRoundRect(
                    color = tint,
                    topLeft = p(15.9f, 4.5f),
                    size = Size(3.6f * u, 15.7f * u),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.4f * u),
                )
            }

            VxIcon.Info -> {
                drawCircle(tint, radius = 8.6f * u, center = p(12f, 12f), style = stroke)
                line(12f, 11f, 12f, 16.4f)
                drawCircle(tint, radius = 1.1f * u, center = p(12f, 7.9f))
            }

            VxIcon.Bell -> {
                val bell = Path().apply {
                    moveTo(6f * u, 17f * u)
                    lineTo(6f * u, 11.4f * u)
                    cubicTo(6f * u, 8f, 8.6f * u, 5.6f * u, 12f * u, 5.6f * u)
                    cubicTo(15.4f * u, 5.6f * u, 18f * u, 8f, 18f * u, 11.4f * u)
                    lineTo(18f * u, 17f * u)
                }
                drawPath(bell, tint, style = stroke)
                line(4.8f, 17f, 19.2f, 17f)
                line(10.2f, 20f, 13.8f, 20f)
            }

            VxIcon.Back -> {
                val path = Path().apply {
                    moveTo(15f * u, 5.6f * u)
                    lineTo(8.8f * u, 12f * u)
                    lineTo(15f * u, 18.4f * u)
                }
                drawPath(path, tint, style = stroke)
            }

            VxIcon.Key -> {
                drawCircle(tint, radius = 3.6f * u, center = p(8.4f, 8.4f), style = stroke)
                line(11f, 11f, 19.4f, 19.4f)
                line(16.2f, 19.4f, 18.2f, 17.4f)
                line(14f, 17.2f, 16f, 15.2f)
            }

            VxIcon.Palette -> {
                drawCircle(tint, radius = 8.6f * u, center = p(12f, 12f), style = stroke)
                drawCircle(tint, radius = 1.35f * u, center = p(9.2f, 9.4f))
                drawCircle(tint, radius = 1.35f * u, center = p(14.8f, 9.4f))
                drawCircle(tint, radius = 1.35f * u, center = p(12f, 14.6f))
            }

            VxIcon.Grid -> {
                listOf(4.5f to 4.5f, 13.4f to 4.5f, 4.5f to 13.4f, 13.4f to 13.4f).forEach { (x, y) ->
                    drawRoundRect(
                        color = tint,
                        topLeft = p(x, y),
                        size = Size(6.1f * u, 6.1f * u),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.8f * u),
                        style = stroke,
                    )
                }
            }

            VxIcon.Database -> {
                drawOval(
                    color = tint,
                    topLeft = p(4f, 4.4f),
                    size = Size(16f * u, 5.4f * u),
                    style = stroke,
                )
                line(4f, 7.1f, 4f, 17.4f)
                line(20f, 7.1f, 20f, 17.4f)
                drawArc(
                    color = tint,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = p(4f, 14.7f),
                    size = Size(16f * u, 5.4f * u),
                    style = stroke,
                )
            }

            VxIcon.Doc -> {
                drawRoundRect(
                    color = tint,
                    topLeft = p(5f, 3.5f),
                    size = Size(14f * u, 17f * u),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.2f * u),
                    style = stroke,
                )
                line(8.4f, 9f, 15.6f, 9f)
                line(8.4f, 12.6f, 15.6f, 12.6f)
                line(8.4f, 16.2f, 13f, 16.2f)
            }

            VxIcon.Pin -> {
                // 图钉：帽檐 + 收窄的针身 + 针尖
                line(7.8f, 4.4f, 16.2f, 4.4f)
                val body = Path().apply {
                    moveTo(9.6f * u, 4.4f * u)
                    lineTo(10.3f * u, 12.4f * u)
                    lineTo(13.7f * u, 12.4f * u)
                    lineTo(14.4f * u, 4.4f * u)
                }
                drawPath(body, tint, style = stroke)
                line(12f, 12.4f, 12f, 19.6f)
            }

            VxIcon.Pencil -> {
                val body = Path().apply {
                    moveTo(4.4f * u, 19.6f * u)
                    lineTo(5.2f * u, 15.2f * u)
                    lineTo(15.4f * u, 5f * u)
                    lineTo(19f * u, 8.6f * u)
                    lineTo(8.8f * u, 18.8f * u)
                    close()
                }
                drawPath(body, tint, style = stroke)
                // 笔杆与笔尖的分界，避免整体看成一个斜方块
                line(13.6f, 6.8f, 17.2f, 10.4f)
            }

            VxIcon.Trash -> {
                line(4.6f, 7.2f, 19.4f, 7.2f)
                line(9.4f, 7.2f, 9.4f, 4.6f)
                line(9.4f, 4.6f, 14.6f, 4.6f)
                line(14.6f, 7.2f, 14.6f, 4.6f)
                val body = Path().apply {
                    moveTo(6.2f * u, 7.2f * u)
                    lineTo(7.2f * u, 19.8f * u)
                    lineTo(16.8f * u, 19.8f * u)
                    lineTo(17.8f * u, 7.2f * u)
                }
                drawPath(body, tint, style = stroke)
                line(10.4f, 11f, 10.4f, 16.4f)
                line(13.6f, 11f, 13.6f, 16.4f)
            }

            VxIcon.Close -> {
                line(6.5f, 6.5f, 17.5f, 17.5f)
                line(17.5f, 6.5f, 6.5f, 17.5f)
            }

            VxIcon.Copy -> {
                // 后层：只画右上两段，避免与前层重叠成糊
                val back = Path().apply {
                    moveTo(9f * u, 5f * u)
                    lineTo(18f * u, 5f * u)
                    cubicTo(19.1f * u, 5f * u, 20f * u, 5.9f * u, 20f * u, 7f * u)
                    lineTo(20f * u, 15f * u)
                }
                drawPath(back, tint, style = stroke)
                drawRoundRect(
                    color = tint,
                    topLeft = p(4f, 9f),
                    size = Size(11f * u, 11f * u),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f * u),
                    style = stroke,
                )
            }

            VxIcon.Refresh -> {
                drawArc(
                    color = tint,
                    startAngle = 40f,
                    sweepAngle = 280f,
                    useCenter = false,
                    topLeft = p(4f, 4f),
                    size = Size(16f * u, 16f * u),
                    style = stroke,
                )
                // 箭头
                val arrow = Path().apply {
                    moveTo(16.2f * u, 2.6f * u)
                    lineTo(20.2f * u, 5.6f * u)
                    lineTo(16.2f * u, 8.2f * u)
                }
                drawPath(arrow, tint, style = stroke)
            }

            VxIcon.Play -> {
                val triangle = Path().apply {
                    moveTo(8f * u, 5f * u)
                    lineTo(19f * u, 12f * u)
                    lineTo(8f * u, 19f * u)
                    close()
                }
                drawPath(triangle, tint, style = stroke)
            }

            VxIcon.Pause -> {
                line(9f, 5f, 9f, 19f)
                line(15f, 5f, 15f, 19f)
            }
        }
    }
}
