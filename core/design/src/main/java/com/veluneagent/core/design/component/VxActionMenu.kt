package com.veluneagent.core.design.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.veluneagent.core.design.shape.SquircleShape
import com.veluneagent.core.design.theme.VeluneTheme
import com.veluneagent.core.design.theme.VxMotion

/** 浮动菜单项。 */
data class VxMenuItem(
    val icon: VxIcon,
    val label: String,
    val onClick: () -> Unit,
    val showChevron: Boolean = false,
)

/**
 * 锚定式浮动菜单：从触发元素上方展开的白卡列表。
 *
 * 不用系统 PopupWindow / DropdownMenu —— 它们的圆角、阴影、进出动画与整体风格不一致。
 * 缩放原点固定为左下角，与「加号在左下」的空间关系一致。
 */
@Composable
fun VxActionMenu(
    items: List<VxMenuItem>,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 240.dp,
    /** 菜单贴哪个角。顶部右上角的「+」用 TopEnd，底部输入框旁的「+」用 BottomStart。 */
    align: Alignment = Alignment.BottomStart,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type

    // 菜单开着时接管返回键。不接管的话，在根 tab（消息页）上按返回会直接退出应用，
    // 而同一个动作在压缩面板里是「关闭面板」—— 用户无法建立一致预期。
    BackHandler(enabled = visible) { onDismiss() }

    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        // 与 VxDropdownMenu / 弹窗同一套节奏：300ms easeOutCubic 进，150ms easeOutSine 收。
        // 之前是独立调的 180/140ms + 0.94 缩放，同一屏里菜单和下拉框的呼吸不一致。
        animationSpec = tween(
            durationMillis = if (visible) VxMotion.DialogMs else VxMotion.DialogExitMs,
            easing = if (visible) VxMotion.EaseOutCubic else VxMotion.EaseOutSine,
        ),
        label = "actionMenu",
    )
    if (progress <= 0.01f) return

    Box(modifier = modifier.fillMaxSize()) {
        // 透明遮罩：点空白关闭
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        Column(
            modifier = Modifier
                .align(align)
                .graphicsLayer {
                    alpha = progress
                    scaleX = VxMotion.DialogFromScale + (1f - VxMotion.DialogFromScale) * progress
                    scaleY = VxMotion.DialogFromScale + (1f - VxMotion.DialogFromScale) * progress
                    // 从锚点方向展开：贴右上角时从右上角长出来，贴左下角时从左下角长出来
                    translationY = (1f - progress) * if (align == Alignment.TopEnd) -10f else 10f
                    transformOrigin = if (align == Alignment.TopEnd) {
                        TransformOrigin(1f, 0f)
                    } else {
                        TransformOrigin(0f, 1f)
                    }
                }
                .padding(contentPadding)
                .width(width)
                .widthIn(max = width)
                // 浮层必须与白底分离：投影 + 极弱描边，缺一不可
                .shadow(
                    elevation = 14.dp,
                    shape = SquircleShape(16.dp),
                    clip = false,
                    ambientColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.10f),
                    spotColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.16f),
                )
                .clip(SquircleShape(16.dp))
                .background(colors.surface)
                .border(1.dp, colors.divider, SquircleShape(16.dp)),
        ) {
            items.forEachIndexed { index, item ->
                MenuRow(item = item, type = type, colors = colors)
                if (index != items.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 46.dp)
                            .height(1.dp)
                            .background(colors.divider),
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuRow(
    item: VxMenuItem,
    type: com.veluneagent.core.design.theme.VeluneTypography,
    colors: com.veluneagent.core.design.theme.VeluneColors,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = vxRipple(colors.textPrimary.copy(alpha = 0.06f)),
                onClick = item.onClick,
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VxIcon(icon = item.icon, tint = colors.textSecondary, size = 19.dp, strokeWidth = 1.8.dp)
        Spacer(modifier = Modifier.width(12.dp))
        VxText(
            text = item.label,
            style = type.rowTitle.copy(fontSize = androidx.compose.ui.unit.TextUnit(15f, androidx.compose.ui.unit.TextUnitType.Sp)),
            color = colors.textPrimary,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (item.showChevron) {
            VxIcon(
                icon = VxIcon.ChevronDown,
                tint = colors.textTertiary,
                size = 12.dp,
                strokeWidth = 1.8.dp,
                modifier = Modifier.graphicsLayer { rotationZ = -90f },
            )
        }
    }
}
