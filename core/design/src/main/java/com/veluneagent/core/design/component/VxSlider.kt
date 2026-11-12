package com.veluneagent.core.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.veluneagent.core.design.theme.VeluneTheme
import com.veluneagent.core.design.theme.VxMotion
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * HyperOS 设置同款离散滑杆（字号、音量这类档位选择）：
 *
 * - 拖动时值吸附到档位，滑块头顶冒出当前档位气泡（scale 0.8→1 + fade，与弹窗同节奏）；
 * - 滑块按下轻微放大（欠阻尼弹簧，与点按反馈同源），松手回落；
 * - 松手时选中最近档位并回弹吸附；
 * - 单击轨道任意位置直接跳到最近档位。
 *
 * 视觉规格参照小米设置字号滑杆：4dp 圆角轨道、档位圆点、白色滑块带投影、
 * 气泡为 accent 底 + 白字，宽 64dp 居中于滑块（在两端内收不越界）。
 */
@Composable
fun VxSlider(
    /** 档位标签，从左到右；至少两项。 */
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    require(labels.size >= 2) { "VxSlider 至少需要两个档位" }
    val last = labels.size - 1

    // 连续位置 0..1：拖动期间实时跟随手势，松手后弹簧吸附到选中档位
    val fraction = remember { Animatable(if (last == 0) 0f else selectedIndex / last.toFloat()) }
    var dragging by remember { mutableStateOf(false) }

    // 外部选中值变化（松手提交 / 程序改值）→ 吸附过去；拖动进行中不接管
    LaunchedEffect(selectedIndex) {
        if (!dragging) {
            fraction.animateTo(
                selectedIndex / last.toFloat(),
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            )
        }
    }

    val bubble by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (dragging) 150 else VxMotion.DialogExitMs,
            easing = if (dragging) VxMotion.EaseOutCubic else VxMotion.EaseOutSine,
        ),
        label = "sliderBubble",
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (dragging) 1.2f else 1f,
        animationSpec = if (dragging) VxMotion.PressEnter else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "sliderThumb",
    )

    fun commit() {
        dragging = false
        val idx = (fraction.value * last).roundToInt().coerceIn(0, last)
        if (idx != selectedIndex) {
            onSelect(idx)
        } else {
            scope.launch {
                fraction.animateTo(
                    idx / last.toFloat(),
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                )
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .height(64.dp)
            .pointerInput(last) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { commit() },
                    onDragCancel = { commit() },
                ) { change, amount ->
                    change.consume()
                    val per = amount / size.width.toFloat()
                    val next = (fraction.value + per).coerceIn(0f, 1f)
                    scope.launch { fraction.snapTo(next) }
                }
            }
            .pointerInput(last) {
                detectTapGestures { offset ->
                    val idx = ((offset.x / size.width.toFloat()) * last).roundToInt().coerceIn(0, last)
                    if (idx != selectedIndex) {
                        onSelect(idx)
                    } else {
                        scope.launch {
                            fraction.animateTo(
                                idx / last.toFloat(),
                                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                            )
                        }
                    }
                }
            },
    ) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val padPx = with(density) { 16.dp.toPx() } // 滑块半径 + 呼吸空间
        val trackW = wPx - padPx * 2
        val cy = hPx - with(density) { 18.dp.toPx() } // 轨道中心：贴底留出气泡空间

        Canvas(Modifier.fillMaxSize()) {
            val f = fraction.value
            // 轨道
            drawRoundRect(
                color = colors.fill,
                topLeft = Offset(padPx, cy - 2.dp.toPx()),
                size = Size(trackW, 4.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
            // 已选中段
            drawRoundRect(
                color = colors.accent,
                topLeft = Offset(padPx, cy - 2.dp.toPx()),
                size = Size(trackW * f, 4.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
            // 档位圆点
            val dot = 2.5.dp.toPx()
            for (i in 0..last) {
                drawCircle(
                    color = colors.textQuaternary,
                    radius = dot,
                    center = Offset(padPx + trackW * i / last, cy),
                )
            }
        }

        // 滑块
        val thumbX = padPx + trackW * fraction.value
        val thumbR = with(density) { 9.dp.toPx() }
        Box(
            modifier = Modifier
                .offset { IntOffset((thumbX - thumbR * thumbScale).roundToInt(), (cy - thumbR * thumbScale).roundToInt()) }
                .size(18.dp)
                .graphicsLayer {
                    scaleX = thumbScale
                    scaleY = thumbScale
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                }
                .shadow(3.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(colors.surface),
        )

        // 档位气泡：拖动时浮现于滑块头顶
        if (bubble > 0.01f) {
            val bubbleW = with(density) { 64.dp.toPx() }
            val bubbleH = with(density) { 26.dp.toPx() }
            val bubbleTop = cy - thumbR * 2 - with(density) { 14.dp.toPx() }
            val bx = (thumbX - bubbleW / 2).coerceIn(4f, wPx - bubbleW - 4f)
            Box(
                modifier = Modifier
                    .offset { IntOffset(bx.roundToInt(), bubbleTop.roundToInt()) }
                    .size(with(density) { 64.dp }, with(density) { 26.dp })
                    .graphicsLayer {
                        alpha = bubble
                        scaleX = VxMotion.DialogFromScale + (1f - VxMotion.DialogFromScale) * bubble
                        scaleY = VxMotion.DialogFromScale + (1f - VxMotion.DialogFromScale) * bubble
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    }
                    .clip(RoundedCornerShape(13.dp))
                    .background(colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                val activeIdx = (fraction.value * last).roundToInt().coerceIn(0, last)
                VxText(
                    text = labels[activeIdx],
                    style = type.footnote.copy(fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp)),
                    color = colors.onAccent,
                    maxLines = 1,
                )
            }
        }
    }
}
