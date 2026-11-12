package com.veluneagent.core.design.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.veluneagent.core.design.shape.SquircleCapsule
import com.veluneagent.core.design.theme.VeluneTheme

/**
 * 轻提示（胶囊）。不使用 Android Toast——系统 Toast 无法定制圆角与配色，
 * 在深色界面里会明显割裂。
 */
@Composable
fun VxSnackbar(
    message: String,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type

    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = if (visible) 200 else 160),
        label = "snackbar",
    )
    if (progress <= 0.01f) return

    val contentColor = if (colors.isLight) colors.surface else colors.background
    val containerColor = if (colors.isLight) {
        colors.textPrimary.copy(alpha = 0.90f)
    } else {
        colors.textPrimary.copy(alpha = 0.94f)
    }

    Row(
        modifier = modifier
            .graphicsLayer {
                alpha = progress
                translationY = (1f - progress) * 14f
            }
            .clip(SquircleCapsule())
            .background(containerColor)
            .padding(horizontal = 15.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VxIcon(
            icon = VxIcon.Check,
            tint = contentColor,
            size = 14.dp,
            strokeWidth = 2.2.dp,
        )
        Spacer(modifier = Modifier.width(7.dp))
        VxText(
            text = message,
            style = type.footnote,
            color = contentColor,
            maxLines = 1,
        )
    }
}
