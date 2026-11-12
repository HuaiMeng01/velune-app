package com.veluneagent.core.design.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veluneagent.core.design.shape.SquircleCapsule
import com.veluneagent.core.design.shape.SquircleShape
import com.veluneagent.core.design.theme.VeluneTheme
import com.veluneagent.core.design.theme.VxMotion
import com.veluneagent.core.design.theme.VxRadius
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

/** 搜索框：40dp 高，浅填充，无描边。 */
@Composable
fun VxSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "搜索",
    modifier: Modifier = Modifier,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clip(SquircleShape(VxRadius.inner))
            .background(colors.fill)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VxIcon(icon = VxIcon.Search, tint = colors.textTertiary, size = 17.dp, strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                VxText(text = placeholder, style = type.value, color = colors.textTertiary)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = type.value.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                singleLine = true,
            )
        }
    }
}

/** 头像：圆形，浅强调色底 + 首字。 */
@Composable
fun VxAvatar(
    text: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    background: Color = VeluneTheme.colors.accent.copy(alpha = 0.14f),
    contentColor: Color = VeluneTheme.colors.accent,
) {
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        VxText(
            text = text,
            // 首字大小应该跟头像尺寸走，而不是跟系统字号走。
            // 直接写 (size.value * 0.38f).sp 会被 fontScale 再乘一次，
            // 大字号下字会撑破圆形；这里先除掉当前倍率，渲染时正好抵消。
            style = VeluneTheme.type.rowTitle.copy(
                fontSize = with(density) { (size.value * 0.38f / density.fontScale).sp },
            ),
            color = contentColor,
            maxLines = 1,
        )
    }
}

data class VxTab(
    val icon: VxIcon,
    val label: String,
)

/**
 * 浮动胶囊导航。
 *
 * 数值取自 MiUiX `FloatingNavigationBarDefaults`：
 * 外侧左右留白 **36dp**（胶囊看起来内缩就是因为它）、内部内边距 12dp、
 * 项间距 12dp、最小高度 52dp、阴影 1dp。
 */
@Composable
fun VxFloatingTabBar(
    tabs: List<VxTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val colors = VeluneTheme.colors
    val shape = SquircleCapsule()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            // 用 min 而不是固定高度：字号放大后底栏要能跟着长高
            .heightIn(min = 56.dp)
            .shadow(
                elevation = 12.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.10f),
                spotColor = Color.Black.copy(alpha = 0.14f),
            )
            .clip(shape)
            // 液态玻璃：内容先被 hazeSource 标记，这里从背后取像素做模糊。
            // 底色几乎透明（12%）——之前 35% + 55% 两层叠加，玻璃被糊成了一块白板。
            .hazeChild(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = Color.Transparent,
                    tints = listOf(HazeTint(colors.surface.copy(alpha = 0.22f))),
                    blurRadius = 28.dp,
                ),
            )
            .background(colors.surface.copy(alpha = 0.10f))
            // 顶亮底暗的描边，是"玻璃厚度"的来源，单色 1dp 边会显得像塑料
            .border(
                width = 0.8.dp,
                brush = Brush.verticalGradient(
                    0f to colors.surface.copy(alpha = 0.95f),
                    0.5f to colors.divider,
                    1f to colors.surface.copy(alpha = 0.55f),
                ),
                shape = shape,
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            // 选中态用同一套弹簧，和列表点按保持同一节奏
            val f by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = VxMotion.PressEnter,
                label = "tabSelect",
            )
            val tint = lerp(colors.textSecondary, colors.accent, f)
            val interaction = remember(tab.label) { MutableInteractionSource() }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 4.dp)
                    .clip(shape)
                    .background(colors.accent.copy(alpha = 0.10f * f))
                    .clickable(
                        interactionSource = interaction,
                        indication = vxRipple(colors.textPrimary.copy(alpha = 0.04f)),
                        onClick = { onSelect(index) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier
                        .animateContentSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VxIcon(icon = tab.icon, tint = tint, size = 18.dp, strokeWidth = 1.9.dp)
                    if (f > 0.01f) {
                        Spacer(modifier = Modifier.width(6.dp))
                        VxText(
                            text = tab.label,
                            style = VeluneTheme.type.footnote.copy(fontSize = 11.sp),
                            color = tint,
                            maxLines = 1,
                            modifier = Modifier.alpha(f),
                        )
                    }
                }
            }
        }
    }
}
