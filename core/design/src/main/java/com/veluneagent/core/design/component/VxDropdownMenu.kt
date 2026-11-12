package com.veluneagent.core.design.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.veluneagent.core.design.theme.VeluneTheme
import com.veluneagent.core.design.theme.VxMotion

/**
 * 行内下拉菜单（少量选项用）。
 *
 * 数值来自真机 miuix：`miuix_appcompat_drop_down_popup_list.xml` +
 * `Miuix.DropDownPopupList` 样式 + `Widget.DropDownItem.Spinner`：
 * - 卡片圆角 **24dp**（`miuix_theme_radius_demi_big`），底色 surface，上下留白 **8dp**；
 * - 最小宽度 **200dp**，选项最小高度 **44dp**、字号 **16sp**、左右留白 **12dp**；
 * - 选中项文字用品牌色并在右侧显示对勾（`spinnerDropDownItemTextColor` 的选中态）。
 *
 * **动效**：复刻 HyperOS 弹窗规格（30 号文档 4.2）——进：scale 0.8→1 + alpha 0→1，
 * 300ms decelerate_cubic；退：150ms 快收（VxMotion.DialogExitMs）。
 * pivot 固定在**右上角**（锚点行的 valueText 侧）：菜单从被点的那一行"长出来"，
 * 而不是凭空在屏幕中间弹出。真机下拉类控件均从锚点展开，居中缩放是对话框的行为。
 *
 * 放在被点行的同级 Box 里即可：`Popup(alignment = TopEnd)` 以父布局为锚点，
 * 再向下偏移一个行高，就落在行下方、与行右缘对齐。
 */
@Composable
fun VxDropdownMenu(
    expanded: Boolean,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    menuWidth: Dp = 220.dp,
    /** 锚点行的高度，菜单从行底部展开。 */
    anchorOffsetY: Dp = 52.dp,
) {
    // 退场动画期间 Popup 必须保持挂载：currentState 在退场播完后才落回 false，
    // 两个都为 false 才卸载——这是 Popup 带退出动画的标准写法
    val state = remember { MutableTransitionState(false) }
    state.targetState = expanded

    if (state.currentState || state.targetState) {
        val colors = VeluneTheme.colors
        val type = VeluneTheme.type
        val shape = RoundedCornerShape(24.dp)
        val density = LocalDensity.current
        val offsetY = with(density) { anchorOffsetY.roundToPx() }
        // 与行右缘留 8dp，避免菜单贴死卡片边
        val offsetX = with(density) { (-8).dp.roundToPx() }

        // pivot 右上角：展开方向与锚点一致（0f, 0f 是左上，1f 是右）
        val anchorPivot = TransformOrigin(1f, 0f)

        Popup(
            alignment = Alignment.TopEnd,
            offset = IntOffset(offsetX, offsetY),
            onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = true),
        ) {
            AnimatedVisibility(
                visibleState = state,
                enter = fadeIn(tween(VxMotion.DialogMs, easing = VxMotion.EaseOutCubic)) +
                    scaleIn(
                        initialScale = VxMotion.DialogFromScale,
                        animationSpec = tween(VxMotion.DialogMs, easing = VxMotion.EaseOutCubic),
                        transformOrigin = anchorPivot,
                    ),
                exit = fadeOut(tween(VxMotion.DialogExitMs, easing = VxMotion.EaseOutSine)) +
                    scaleOut(
                        targetScale = 0.9f,
                        animationSpec = tween(VxMotion.DialogExitMs, easing = VxMotion.EaseOutSine),
                        transformOrigin = anchorPivot,
                    ),
            ) {
                Column(
                    modifier = modifier
                        .width(menuWidth)
                        // 12dp 的默认阴影在浅色底上会拖出一圈灰雾（看起来像一道阴翳），
                        // 收到 6dp 并用更紧的 spot 色，只留贴边的一层
                        .shadow(
                            elevation = 6.dp,
                            shape = shape,
                            clip = false,
                            ambientColor = Color.Black.copy(alpha = 0.06f),
                            spotColor = Color.Black.copy(alpha = 0.10f),
                        )
                        .clip(shape)
                        .background(colors.surface)
                        .padding(vertical = 8.dp),
                ) {
                    options.forEachIndexed { index, label ->
                        val selected = index == selectedIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    onSelect(index)
                                    onDismiss()
                                }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            VxText(
                                text = label,
                                style = type.body,
                                color = if (selected) colors.accent else colors.textPrimary,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                VxIcon(
                                    icon = VxIcon.Check,
                                    tint = colors.accent,
                                    size = 18.dp,
                                    strokeWidth = 2.2.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
