package com.veluneagent.core.design.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.veluneagent.core.design.l10n.tr
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/** 底部表的单项。字段保持不变，13 处调用点无需改动。 */
data class VxSheetAction(
    val label: String,
    val icon: VxIcon? = null,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * 底部动作表。
 *
 * 实现从「自绘遮罩 + 白卡 + 手写进出动画」换成 MIUIX 的 [WindowBottomSheet]：
 * - 圆角、拖拽指示条、遮罩透明度、进出曲线跟随 HyperOS，不再是"长得像"的仿制品；
 * - 自带**预测式返回**（Android 14 手势下拉时整卡跟随），手写动画做不到；
 * - 用 Window 级而非 Overlay 级：Overlay 版要求整棵树挂在 `Scaffold` 的
 *   `MiuixPopupHost` 下，而本项目是自绘底部导航、没有 Miuix Scaffold，
 *   用 Overlay 会直接渲染不出来。
 *
 * 对外签名保持不变 —— 换皮不该引发业务层连锁改动。
 */
@Composable
fun VxActionSheet(
    visible: Boolean,
    actions: List<VxSheetAction>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    caption: String? = null,
) {
    val miuix = MiuixTheme.colorScheme
    val type = MiuixTheme.textStyles

    WindowBottomSheet(
        show = visible,
        // miuix 组件不经 VxText，标题在这里手动过 tr
        title = title?.let(::tr),
        modifier = modifier,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            if (caption != null) {
                Text(
                    text = tr(caption),
                    style = type.footnote2,
                    color = miuix.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }
            actions.forEach { action ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = action.onClick,
                        )
                        .padding(horizontal = 4.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    action.icon?.let { icon ->
                        VxIcon(
                            icon = icon,
                            tint = if (action.destructive) miuix.error else miuix.onSurface,
                            size = 18.dp,
                            strokeWidth = 1.9.dp,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Text(
                        text = tr(action.label),
                        style = type.main,
                        color = if (action.destructive) miuix.error else miuix.onSurface,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * 单行输入弹层（重命名、昵称等）。
 *
 * 与动作表同源：MIUIX [WindowBottomSheet] + [TextField] + [Button]。
 * 输入值按 `visible/initialValue` 重置——同一个弹层被复用时，关掉再打开要回到最新初值，
 * 否则会残留上一次的输入。
 */
@Composable
fun VxPromptSheet(
    visible: Boolean,
    title: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String = "保存",
    placeholder: String = "",
) {
    val type = MiuixTheme.textStyles
    var value by remember(visible, initialValue) { mutableStateOf(initialValue) }

    WindowBottomSheet(
        show = visible,
        // miuix 组件不经 VxText，标题在这里手动过 tr
        title = title?.let(::tr),
        modifier = modifier,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                // 键盘弹出时把按钮顶上来：Window 级弹层不会自动 reflow 内容
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            TextField(
                value = value,
                onValueChange = { value = it },
                label = tr(placeholder.ifBlank { title }),
                useLabelAsPlaceholder = placeholder.isNotBlank(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    minHeight = 40.dp,
                ) {
                    Text(tr("取消"), style = type.button)
                }
                Button(
                    onClick = { onConfirm(value) },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                    minHeight = 40.dp,
                ) {
                    Text(tr(confirmLabel), style = type.button)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
