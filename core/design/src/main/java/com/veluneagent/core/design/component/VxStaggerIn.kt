package com.veluneagent.core.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.veluneagent.core.design.theme.VxMotion
import kotlinx.coroutines.delay

/**
 * 列表进场 stagger：淡入 + 上浮，各组按 [index] 错峰 40ms 依次入场。
 *
 * HyperOS 设置首页每次进入都会重放这段进场；这里也一样——页面重新组合
 * （进页、返回、切 tab）时 remember 重置、动画重放，停留期间不重放。
 * 位移与时长复用页面进入参数（350ms easeOutQuint / 8% 位移），节奏与推页一致。
 */
@Composable
fun VxStaggerIn(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val density = LocalDensity.current
    LaunchedEffect(Unit) {
        if (index > 0) delay(VxMotion.StaggerStepMs.toLong() * index)
        progress.animateTo(1f, tween(VxMotion.EnterSlideMs, easing = VxMotion.EaseOutQuint))
    }
    val riseDistance = with(density) { 16.dp.toPx() }
    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * riseDistance
        },
    ) {
        content()
    }
}
