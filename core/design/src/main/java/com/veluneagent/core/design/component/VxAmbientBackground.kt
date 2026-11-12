package com.veluneagent.core.design.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.runtime.withFrameNanos
import com.veluneagent.core.design.theme.VeluneTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

/**
 * 动态氛围背景（对齐 HyperOS 设置页的 `bgEffectView`）。
 *
 * 实现方式取自开源项目 Nexio 课程表（AGPL-3.0，[HaoZai000/NexioSchedule]），
 * 原文件：`ui/effects/background/{OS3BgFrag,BgEffectConfig,BgEffectPainter,BgEffectModifier}.kt`。
 *
 * **为什么不是"取壁纸色画渐变"**：小米这块背景是**运行时的 AGSL 着色器**在画——
 * 四个彩色径向光斑按 Perlin 噪声混合，再对饱和度/亮度做偏移，光斑位置随时间做
 * `sin/cos` 漂移，颜色每 5 秒在预设之间做弹簧插值。所以它"会动"且"不是纯渐变"，
 * 靠静态取色永远做不像。
 *
 * 三个关键行为：
 * 1. **只在控件上半部着墨**（`drawHeight = height * 0.5`），下半回到 surface ——
 *    这就是参考图里"由深到浅、化开"的来源，不是叠一层渐变蒙层；
 * 2. **光斑漂移**：`x = px + sin(t + py) * offset`，`y = py + cos(t + x) * offset`；
 * 3. **配色循环**：三组颜色按 `colors2 → colors1 → colors2 → colors3` 循环，
 *    弹簧 `damping 0.9 / stiffness 35` 插值，手机浅色 5s 一轮、深色 8s。
 *
 * 降级：Android 13 以下不支持 AGSL，直接不画（露出页面底色），不崩。
 */
@Composable
fun VxAmbientBackground(
    modifier: Modifier = Modifier,
    /** true 时着墨到控件 80% 高度（大页面背景）；false 时只到 50%（头图） */
    fullBleed: Boolean = false,
    /** 是否播放动画。滚动时不跟随手指停，保持常开更自然 */
    animated: Boolean = true,
    /** 整体不透明度。滚动淡出用；降到 0 时停止绘制与动画，避免白跑着色器 */
    alpha: Float = 1f,
) {
    val colors = VeluneTheme.colors
    val isDark = !colors.isLight
    // AGSL（RuntimeShader）从 Android 13 才有。低版本直接不画，露出页面底色。
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || alpha <= 0.01f) return

    val painter = remember { createAmbientPainter() }
    val preset = remember(isDark) { AmbientPresets.of(isDark) }

    Box(modifier = modifier) {
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .ambientDraw(
                    painter = painter,
                    preset = preset,
                    isDark = isDark,
                    surface = colors.background,
                    fullBleed = fullBleed,
                    playing = animated,
                    alpha = alpha,
                ),
        )
    }
}

// ---------------------------------------------------------------- 配色预设

/**
 * 配色表逐字取自 Nexio 的 `BgEffectConfig.PHONE_LIGHT / PHONE_DARK`（RGBA，0..1）。
 * 这是 OS3 风格的粉紫 → 蓝，和 HyperOS 设置页头图同源。
 *
 * 刻意**不**改成本项目品牌蓝：这一层是"系统氛围"，不是品牌表达；
 * 改成单色蓝会退化成普通渐变，正好丢掉参考图里粉紫与蓝交替的层次。
 */
private class AmbientPreset(
    val points: FloatArray,
    val colors1: FloatArray,
    val colors2: FloatArray,
    val colors3: FloatArray,
    val colorInterpPeriod: Float,
    val lightOffset: Float,
    val saturateOffset: Float,
    val pointOffset: Float,
)

private object AmbientPresets {

    /**
     * 手机浅色。**不是** Nexio 的原预设——原预设是"上粉下蓝"的竖向分布，
     * 与真机对不上。这里按小米「我的设备」截图四角实测重新配色：
     *
     * | 位置 | 实测 |
     * |---|---|
     * | 左上 | `#F9DDF2` 粉 |
     * | 右上 | `#FAE7EF` 粉白（最亮） |
     * | 左下 | `#CCC7FA` 蓝紫（最蓝） |
     * | 右下 | `#ECE0F8` 淡紫 |
     *
     * 光斑与四角的对应：p0 右下、p1 右上、p2 左上、p3 左下。
     * 三组颜色只在色相浓度上微差（偏粉/基准/偏蓝），循环时是"呼吸"而不是"跳色"。
     */
    private val LIGHT = AmbientPreset(
        // 半径 1.05 略大于 1：让光斑边缘落在画面外，四角不至于衰减成灰
        points = floatArrayOf(
            0.8f, 0.10f, 1.05f,
            0.8f, 1.05f, 1.05f,
            0.2f, 1.05f, 1.05f,
            0.2f, 0.10f, 1.05f,
        ),
        colors1 = floatArrayOf(
            0.940f, 0.890f, 0.970f, 1.0f, 0.999f, 0.919f, 0.949f, 1.0f,
            0.990f, 0.879f, 0.949f, 1.0f, 0.820f, 0.790f, 0.980f, 1.0f,
        ),
        colors2 = floatArrayOf(
            0.925f, 0.878f, 0.973f, 1.0f, 0.980f, 0.906f, 0.937f, 1.0f,
            0.976f, 0.867f, 0.949f, 1.0f, 0.800f, 0.780f, 0.980f, 1.0f,
        ),
        colors3 = floatArrayOf(
            0.900f, 0.860f, 0.980f, 1.0f, 0.960f, 0.890f, 0.940f, 1.0f,
            0.950f, 0.850f, 0.960f, 1.0f, 0.760f, 0.750f, 0.990f, 1.0f,
        ),
        colorInterpPeriod = 5.0f,
        // 提亮 0.03：每 +0.01 大约把中位亮度抬 2.5/255，0.1（原值）会整体发白
        lightOffset = 0.03f,
        // 降饱和 0.06：噪声会把色块边缘洗白，原值 0.2 让画面"糊成一片淡紫"
        saturateOffset = 0.06f,
        pointOffset = 0.12f,
    )

    private val DARK = AmbientPreset(
        points = floatArrayOf(0.8f, 0.2f, 1.0f, 0.8f, 0.9f, 1.0f, 0.2f, 0.9f, 1.0f, 0.2f, 0.2f, 1.0f),
        colors1 = floatArrayOf(
            0.2f, 0.06f, 0.88f, 0.4f, 0.3f, 0.14f, 0.55f, 0.5f,
            0.0f, 0.64f, 0.96f, 0.5f, 0.11f, 0.16f, 0.83f, 0.4f,
        ),
        colors2 = floatArrayOf(
            0.07f, 0.15f, 0.79f, 0.5f, 0.62f, 0.21f, 0.67f, 0.5f,
            0.06f, 0.25f, 0.84f, 0.5f, 0.0f, 0.2f, 0.78f, 0.5f,
        ),
        colors3 = floatArrayOf(
            0.58f, 0.3f, 0.74f, 0.4f, 0.27f, 0.18f, 0.6f, 0.5f,
            0.66f, 0.26f, 0.62f, 0.5f, 0.12f, 0.16f, 0.7f, 0.6f,
        ),
        colorInterpPeriod = 8.0f,
        lightOffset = 0.0f,
        saturateOffset = 0.17f,
        pointOffset = 0.4f,
    )

    fun of(isDark: Boolean): AmbientPreset = if (isDark) DARK else LIGHT
}

// ---------------------------------------------------------------- 着色器

/**
 * AGSL 片段。逐字移植自 Nexio 的 `OS3_BG_FRAG`。
 *
 * 做的事：把 uv 归一化到 `uBound` 区域 → 四个径向光斑按 `smoothstep` 叠加 →
 * 用 Perlin 噪声反向拉低饱和度、抬一点亮度（让色块之间"化开"而不是硬边）→
 * 叠 1/255 量级的抖动消除色带。
 */
private const val AMBIENT_FRAG = """
    uniform vec2 uResolution;
    uniform float uAnimTime;
    uniform vec4 uBound;
    uniform float uTranslateY;
    uniform vec3 uPoints[4];
    uniform vec2 uPointsAnim[4];
    uniform vec4 uColors[4];
    uniform float uAlphaMulti;
    uniform float uNoiseScale;
    uniform float uPointRadiusMulti;
    uniform float uSaturateOffset;
    uniform float uLightOffset;

    vec3 rgb2hsv(vec3 c) {
        vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
        vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
        vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
        float d = q.x - min(q.w, q.y);
        float e = 1.0e-10;
        return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
    }

    vec3 hsv2rgb(vec3 c) {
        vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
        vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
        return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
    }

    float hash(vec2 p) {
        vec3 p3 = fract(vec3(p.xyx) * 0.13);
        p3 += dot(p3, p3.yzx + 3.333);
        return fract((p3.x + p3.y) * p3.z);
    }

    float perlin(vec2 x) {
        vec2 i = floor(x); vec2 f = fract(x);

        float a = hash(i); float b = hash(i + vec2(1.0, 0.0));
        float c = hash(i + vec2(0.0, 1.0)); float d = hash(i + vec2(1.0, 1.0));

        vec2 u = f * f * (3.0 - 2.0 * f);
        return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
    }

    float gradientNoise(in vec2 uv) {
        return fract(52.9829189 * fract(dot(uv, vec2(0.06711056, 0.00583715))));
    }

    vec4 main(vec2 fragCoord){
        vec2 vUv = fragCoord/uResolution;
        vUv.y = 1.0-vUv.y;
        vec2 uv = vUv;
        uv -= vec2(0., uTranslateY);
        uv.xy -= uBound.xy;
        uv.xy /= uBound.zw;

        vec4 color = vec4(0.0);
        float noiseValue = perlin(vUv * uNoiseScale + vec2(-uAnimTime, -uAnimTime));

        for (int i = 0; i < 4; i++){
            vec4 pointColor = uColors[i];
            pointColor.rgb *= pointColor.a;
            vec2 point = uPointsAnim[i];
            float rad = uPoints[i].z * uPointRadiusMulti;

            float d = distance(uv, point);
            float pct = smoothstep(rad, 0., d);
            color.rgb = mix(color.rgb, pointColor.rgb, pct);
            color.a = mix(color.a, pointColor.a, pct);
        }

        float oppositeNoise = smoothstep(0., 1., noiseValue);
        color.rgb /= color.a;
        vec3 hsv = rgb2hsv(color.rgb);
        hsv.y = mix(hsv.y, 0.0, oppositeNoise * uSaturateOffset);
        color.rgb = hsv2rgb(hsv);
        color.rgb += oppositeNoise * uLightOffset;

        color.a = clamp(color.a, 0., 1.);
        color.a *= uAlphaMulti;

        color += (10.0 / 255.0) * gradientNoise(fragCoord.xy) - (5.0 / 255.0);
        return vec4(color.rgb * color.a, color.a);
    }
"""

// ---------------------------------------------------------------- 画笔

/**
 * 单独包一层 `@RequiresApi(33)`：让 26–32 的类校验器不去解析 [RuntimeShader]，
 * 否则低版本一进关于页就 `NoClassDefFoundError`。
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun createAmbientPainter(): AmbientPainter = AmbientPainter()

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AmbientPainter {

    val runtimeShader = RuntimeShader(AMBIENT_FRAG).also { shader ->
        shader.setFloatUniform("uTranslateY", 0f)
        shader.setFloatUniform("uNoiseScale", 1.5f)
        shader.setFloatUniform("uPointRadiusMulti", 1f)
        shader.setFloatUniform("uAlphaMulti", 1f)
    }

    /** ShaderBrush 里 createShader 的返回值直接给 RuntimeShader 本身。 */
    val brush: Brush = object : ShaderBrush() {
        override fun createShader(size: Size): Shader = runtimeShader
    }

    private val resolution = FloatArray(2)
    private val bound = FloatArray(4)
    private val colorsBuffer = FloatArray(16)
    private val pointsAnimBuffer = FloatArray(8)

    private var animTime = Float.NaN
    private var cachedStage = Float.NaN
    private var cachedStagePreset: AmbientPreset? = null
    private var cachedPointsTime = Float.NaN
    private var cachedPointsPreset: AmbientPreset? = null
    private var cachedHeight = Float.NaN
    private var cachedWidth = Float.NaN
    private var presetApplied = false

    fun updateResolution(width: Float, height: Float) {
        if (resolution[0] == width && resolution[1] == height) return
        resolution[0] = width
        resolution[1] = height
        runtimeShader.setFloatUniform("uResolution", resolution)
    }

    fun updateTime(time: Float) {
        if (animTime == time) return
        animTime = time
        runtimeShader.setFloatUniform("uAnimTime", animTime)
    }

    /** 光斑漂移：两个相互依赖的 sin/cos，避免四个点同步移动（那样像整体平移，很假）。 */
    fun updatePoints(time: Float, preset: AmbientPreset) {
        if (cachedPointsTime == time && cachedPointsPreset === preset) return
        val offset = preset.pointOffset
        for (i in 0 until 4) {
            val srcX = preset.points[i * 3]
            val srcY = preset.points[i * 3 + 1]
            val animX = srcX + sin(time + srcY) * offset
            val animY = srcY + cos(time + animX) * offset
            pointsAnimBuffer[i * 2] = animX
            pointsAnimBuffer[i * 2 + 1] = animY
        }
        runtimeShader.setFloatUniform("uPointsAnim", pointsAnimBuffer)
        cachedPointsTime = time
        cachedPointsPreset = preset
    }

    /** 三组颜色循环插值：2 → 1 → 2 → 3，所以不会出现"跳色"。 */
    fun updateColors(preset: AmbientPreset, stage: Float) {
        if (cachedStagePreset === preset && cachedStage == stage) return
        val base = stage.toInt()
        val fraction = stage - base
        val start = colorsFor(preset, base)
        val end = colorsFor(preset, base + 1)
        for (i in 0 until 16) {
            colorsBuffer[i] = start[i] + (end[i] - start[i]) * fraction
        }
        runtimeShader.setFloatUniform("uColors", colorsBuffer)
        cachedStagePreset = preset
        cachedStage = stage
    }

    private fun colorsFor(preset: AmbientPreset, index: Int): FloatArray =
        when ((index % 4 + 4) % 4) {
            1 -> preset.colors1
            3 -> preset.colors3
            else -> preset.colors2
        }

    fun applyPreset(preset: AmbientPreset) {
        if (presetApplied) return
        runtimeShader.setFloatUniform("uPoints", preset.points)
        runtimeShader.setFloatUniform("uLightOffset", preset.lightOffset)
        runtimeShader.setFloatUniform("uSaturateOffset", preset.saturateOffset)
        presetApplied = true
    }

    /**
     * uv 映射范围。竖屏时 `y` 从 `1 - ratio` 到 1，即**只覆盖上半部**；
     * 超出这个范围 uv.y > 1，`distance` 拉大 → 光斑衰减到 0 → 露出 surface。
     * 这就是"由深到浅"的几何来源。
     */
    fun updateBound(drawHeight: Float, totalHeight: Float, totalWidth: Float) {
        if (cachedHeight == drawHeight && cachedWidth == totalWidth) return
        val ratio = (drawHeight / totalHeight).coerceIn(0.05f, 1f)
        bound[0] = 0f
        bound[1] = 1f - ratio
        bound[2] = 1f
        bound[3] = ratio
        runtimeShader.setFloatUniform("uBound", bound)
        cachedHeight = drawHeight
        cachedWidth = totalWidth
    }
}

// ---------------------------------------------------------------- Modifier

private fun Modifier.ambientDraw(
    painter: AmbientPainter,
    preset: AmbientPreset,
    isDark: Boolean,
    surface: Color,
    fullBleed: Boolean,
    playing: Boolean,
    alpha: Float,
): Modifier = this then AmbientElement(painter, preset, isDark, surface, fullBleed, playing, alpha)

private data class AmbientElement(
    val painter: AmbientPainter,
    val preset: AmbientPreset,
    val isDark: Boolean,
    val surface: Color,
    val fullBleed: Boolean,
    val playing: Boolean,
    val alpha: Float,
) : ModifierNodeElement<AmbientNode>() {

    override fun create() = AmbientNode(painter, preset, isDark, surface, fullBleed, playing, alpha)

    override fun update(node: AmbientNode) {
        node.painter = painter
        node.preset = preset
        node.isDark = isDark
        node.surface = surface
        node.fullBleed = fullBleed
        node.playing = playing
        node.alpha = alpha
        node.invalidateDraw()
        node.syncAnimation()
    }
}

private class AmbientNode(
    var painter: AmbientPainter,
    var preset: AmbientPreset,
    var isDark: Boolean,
    var surface: Color,
    var fullBleed: Boolean,
    var playing: Boolean,
    var alpha: Float,
) : Modifier.Node(), DrawModifierNode {

    private var job: Job? = null
    private var animTime = 0f
    private var colorStage = 0f
    private var stageTarget = 1f
    private var lastStageSwap = 0L

    override fun onAttach() {
        syncAnimation()
    }

    override fun onDetach() {
        job?.cancel()
        job = null
    }

    fun syncAnimation() {
        if (!playing) {
            job?.cancel()
            job = null
            return
        }
        if (job?.isActive == true) return
        job = coroutineScope.launch {
            // 60fps 限流：着色器每帧重算成本不低，超过刷新率没有意义
            val minDelta = 1_000_000_000L / 60L
            val origin = withFrameNanos { it }
            lastStageSwap = origin
            var lastEmit = origin
            while (isActive) {
                val now = withFrameNanos { it }
                if (now - lastEmit < minDelta) continue
                lastEmit = now
                animTime = (now - origin) / 1_000_000_000f

                // 颜色阶段：到点就推进一格，用线性过渡代替弹簧，
                // 弹簧在这里收益很小（周期 5s），线性更好预测
                val periodNanos = (preset.colorInterpPeriod * 500_000_000L).toLong()
                if (now - lastStageSwap >= periodNanos) {
                    lastStageSwap = now
                    stageTarget += 1f
                }
                val target = stageTarget
                if (colorStage < target) {
                    // 用 1.2s 走完一整格，和原实现的弹簧观感接近但不抖
                    val step = minDelta / 1_200_000_000f
                    colorStage = (colorStage + step).coerceAtMost(target)
                }
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawRect(surface)
        val drawHeight = size.height * if (fullBleed) 0.8f else 0.5f
        painter.updateResolution(size.width, size.height)
        painter.updateBound(drawHeight, size.height, size.width)
        painter.applyPreset(preset)
        painter.updateColors(preset, colorStage)
        painter.updateTime(animTime)
        painter.updatePoints(animTime, preset)
        drawRect(painter.brush, alpha = alpha)
        drawContent()
    }
}

/**
 * 让元素向上溢出 [inset]，但占位高度不变。
 *
 * 用途：背景要盖住状态栏，而内容区（已被 `safeDrawingPadding` 让开状态栏）不该再下移。
 * 用 `offset` 做只会把底边也一起抬高，露出下面的空白；`layout` 才能只放大测量、再负向摆放。
 */
fun Modifier.bleedTop(inset: androidx.compose.ui.unit.Dp): Modifier =
    this.layout { measurable, constraints ->
        val extra = inset.roundToPx()
        val placeable = measurable.measure(
            constraints.copy(
                minHeight = 0,
                maxHeight = constraints.maxHeight + extra,
            ),
        )
        layout(placeable.width, (placeable.height - extra).coerceAtLeast(0)) {
            placeable.place(0, -extra)
        }
    }
