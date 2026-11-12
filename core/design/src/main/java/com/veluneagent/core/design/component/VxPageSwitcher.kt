package com.veluneagent.core.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import com.veluneagent.core.design.theme.VxMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * HyperOS 设置式「推页」容器。
 *
 * 推入：新页淡入 200ms(easeOutQuad) + 从右侧 8% 宽度推入 350ms(easeOutQuint)，
 * 同时旧页原地压暗到 0.7，被新页盖住。
 * 返回：旧页向右滑出 8% 并淡出 200ms(easeOutQuad)，底层页从 0.7 提亮回 1（217ms easeOutQuint）。
 * 深度相同（底部标签切换）不转场，原地替换。
 *
 * 方向由 depth 决定：目标页比当前页深 = 推入；浅 = 返回。
 *
 * 转场期间旧页会以**旧 state**继续渲染，所以 content 必须是 state 的纯函数——
 * 页面的路由参数（会话 id、详情 route 等）要随 page 一起传进来，不要在 content
 * 里读外层的导航开关，否则退场画面会被"偷换"成新页。
 */
@Composable
fun <T> VxPageSwitcher(
    page: T,
    depthOf: (T) -> Int,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    var currentPage by remember { mutableStateOf(page) }
    var outgoing by remember { mutableStateOf<T?>(null) }

    if (page != currentPage && outgoing == null) {
        if (depthOf(page) == depthOf(currentPage)) {
            // 同层切换（tab 之间）不走推页，原地替换
            currentPage = page
        } else {
            outgoing = currentPage
            currentPage = page
        }
    }

    val widthPx = remember { mutableIntStateOf(0) }

    // 新页层：推入时 0→1 淡入 + 8%→0 推入；返回时 0.7→1 提亮
    val currentAlpha = remember { Animatable(1f) }
    val currentSlide = remember { Animatable(0f) }
    // 旧页层：推入时 1→0.7 压暗；返回时 0→8% 滑出 + 淡出
    val outAlpha = remember { Animatable(1f) }
    val outSlide = remember { Animatable(0f) }

    LaunchedEffect(page) {
        val out = outgoing ?: return@LaunchedEffect
        val pushing = depthOf(currentPage) > depthOf(out)
        if (pushing) {
            currentSlide.snapTo(VxMotion.EnterSlideFraction)
            currentAlpha.snapTo(0f)
            outSlide.snapTo(0f)
            outAlpha.snapTo(1f)
            coroutineScope {
                launch {
                    currentAlpha.animateTo(
                        1f,
                        tween(VxMotion.EnterAlphaMs, easing = VxMotion.EaseOutQuad),
                    )
                }
                launch {
                    currentSlide.animateTo(
                        0f,
                        tween(VxMotion.EnterSlideMs, easing = VxMotion.EaseOutQuint),
                    )
                }
                // 旧页压暗跟随整个推入时长：退早了会在新页还差一点位移时露出硬边
                outAlpha.animateTo(
                    VxMotion.ExitAlphaTarget,
                    tween(VxMotion.EnterSlideMs, easing = VxMotion.EaseOutQuint),
                )
            }
        } else {
            // 返回：旧页压着新页滑出，底层页提亮
            currentAlpha.snapTo(VxMotion.ExitAlphaTarget)
            outSlide.snapTo(0f)
            outAlpha.snapTo(1f)
            coroutineScope {
                launch {
                    outSlide.animateTo(
                        VxMotion.EnterSlideFraction,
                        tween(VxMotion.EnterAlphaMs, easing = VxMotion.EaseOutQuad),
                    )
                }
                launch {
                    outAlpha.animateTo(
                        0f,
                        tween(VxMotion.EnterAlphaMs, easing = VxMotion.EaseOutQuad),
                    )
                }
                launch {
                    currentAlpha.animateTo(
                        1f,
                        tween(VxMotion.ExitAlphaMs, easing = VxMotion.EaseOutQuint),
                    )
                }
            }
        }
        outgoing = null
        currentSlide.snapTo(0f)
        currentAlpha.snapTo(1f)
        outSlide.snapTo(0f)
        outAlpha.snapTo(1f)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { widthPx.intValue = it.width },
    ) {
        val popping = outgoing != null && depthOf(outgoing!!) > depthOf(currentPage)
        Box(
            Modifier
                .fillMaxSize()
                .zIndex(if (popping) 0f else 1f)
                .graphicsLayer {
                    alpha = currentAlpha.value
                    translationX = currentSlide.value * widthPx.intValue
                },
        ) {
            content(currentPage)
        }

        outgoing?.let { out ->
            val onTop = depthOf(out) > depthOf(currentPage)
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(if (onTop) 1f else 0f)
                    .graphicsLayer {
                        alpha = outAlpha.value
                        translationX = outSlide.value * widthPx.intValue
                    },
            ) {
                content(out)
            }
        }
    }
}
