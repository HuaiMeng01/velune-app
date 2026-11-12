package com.veluneagent.feature.chat.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.veluneagent.core.design.component.vxRipple
import com.veluneagent.core.design.l10n.tr
import com.veluneagent.core.design.l10n.trf
import com.veluneagent.core.design.theme.VeluneTheme
import com.veluneagent.feature.chat.model.ContextBudget

/**
 * 上下文环：显示当前会话占压缩阈值的比例。
 * 点击打开压缩设置（用户可自定义多少条触发压缩）。
 */
@Composable
fun ContextRing(
    budget: ContextBudget,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: androidx.compose.ui.unit.Dp = 22.dp,
) {
    val colors = VeluneTheme.colors
    val track = if (colors.isLight) colors.textPrimary.copy(alpha = 0.12f)
    else colors.textPrimary.copy(alpha = 0.18f)
    val active = if (budget.nearLimit) colors.warning else colors.accent
    val interaction = remember { MutableInteractionSource() }
    // 视觉只有 22dp，点击区必须放大：22dp 的圆单手几乎点不中。
    // 外面套一层透明区域承载点击，里面的 Canvas 仍按 diameter 绘制，视觉不变。
    val hitSize = maxOf(40.dp, diameter + 18.dp)

    Box(
        modifier = modifier
            .size(hitSize)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = vxRipple(color = active.copy(alpha = 0.14f), bounded = true),
                onClick = onClick,
            )
            .semantics {
                contentDescription =
                    trf(
                        "上下文已用 %d/%d 条，剩余 %d 条后自动压缩",
                        budget.messageCount,
                        budget.compressAt,
                        budget.remaining,
                    )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(diameter)) {
            val stroke = 2.4.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )

            if (budget.progress > 0f) {
                drawArc(
                    color = active,
                    startAngle = -90f,
                    sweepAngle = 360f * budget.progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
    }
}
