package com.veluneagent.core.design.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color

/**
 * 动效标尺。数值来自小米设置 16.4.261010.00 的实测：
 * 页面转场读 `res/anim`，点按反馈读 `miuix.animation` 的 `AnimConfig` 静态初始化。
 *
 * 只有三个基准动作，其余控件都是这三者的阻尼微调——**不要为每个控件单独调时长**，
 * 那正是"同应用内动效节奏散掉"的来源。
 */
object VxMotion {

    /** 点按叠加层：纯黑浮层按状态插值 alpha，不是 ripple。 */
    const val PressAlpha = 0.12f
    const val HoverAlpha = 0.06f
    const val ActivatedAlpha = 0.08f

    /** 按下进入：欠阻尼弹簧（damping 0.35 < 1 → 会轻微过冲）。 */
    val PressEnter: SpringSpec<Float> = spring(
        dampingRatio = 0.35f,
        stiffness = Spring.StiffnessMedium,
    )

    /** 松手回落：350ms 正弦缓出，无回弹。 */
    const val PressExitMs = 350

    /** 页面进入：alpha 200ms decelerate_quad + 位移 350ms decelerate_quint。 */
    const val EnterAlphaMs = 200
    const val EnterSlideMs = 350
    const val EnterSlideFraction = 0.08f

    /** 旧页退出：alpha 1→0.7，217ms decelerate_quint（不淡到 0，靠压暗表现层级）。 */
    const val ExitAlphaMs = 217
    const val ExitAlphaTarget = 0.7f

    /** 列表进场 stagger：各组错峰步长。位移与时长复用页面进入参数。 */
    const val StaggerStepMs = 40

    /** 折叠组展开 / 收起：高度 300ms decelerate_cubic / 250ms easeOutSine，箭头同步旋转。 */
    const val ExpandMs = 300
    const val CollapseMs = 250

    /** 弹窗：scale 0.8→1、alpha 同步，300ms decelerate_cubic。 */
    const val DialogMs = 300
    const val DialogFromScale = 0.8f

    /** 弹窗/菜单退场：150ms 快收（linear 基准动作），比进场快是为了"关比开利落"。 */
    const val DialogExitMs = 150

    /** Compose 没有内置 sine，标准 easeOutSine 的等价三次贝塞尔。 */
    val EaseOutSine: Easing = CubicBezierEasing(0.39f, 0.575f, 0.565f, 1f)

    /** easeOutQuad / easeOutCubic 的等价曲线，用于转场。 */
    val EaseOutQuad: Easing = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)
    val EaseOutCubic: Easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)
    val EaseOutQuint: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    /** 点按叠加层颜色：浅色模式压黑、深色模式提白（对应 miuix 的 tintColor 明暗两套）。 */
    fun pressTint(isLight: Boolean): Color =
        if (isLight) Color.Black else Color.White
}
