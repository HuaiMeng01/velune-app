package com.veluneagent.feature.chat.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.veluneagent.core.design.component.VxIcon
import com.veluneagent.core.design.component.VxText
import com.veluneagent.core.design.component.vxRipple
import com.veluneagent.core.design.shape.SquircleCapsule
import com.veluneagent.core.design.shape.SquircleShape
import com.veluneagent.core.design.l10n.tr
import com.veluneagent.core.design.l10n.trf
import com.veluneagent.core.design.theme.VeluneTheme
import com.veluneagent.core.design.theme.VxMotion
import com.veluneagent.core.design.theme.VxRadius
import com.veluneagent.feature.chat.model.ContextBudget
import com.veluneagent.feature.chat.model.ModelInfo

/**
 * 聊天输入栏。
 *
 * 布局：`[+] … [上下文环] [✨] [模型名 ⌄] [发送]`
 *
 * 纯白容器 + 1dp 描边：对话页画布是白底，只靠底色差无法界定输入区，必须用描边。
 */
@Composable
fun ChatInputBar(
    input: String,
    onInputChange: (String) -> Unit,
    budget: ContextBudget,
    model: ModelInfo,
    onAdd: () -> Unit,
    onRingClick: () -> Unit,
    onModelClick: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    sendEnabled: Boolean = input.isNotBlank(),
    streaming: Boolean = false,
    onStop: () -> Unit = {},
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type
    val shape = SquircleShape(VxRadius.input)

    // 焦点态：描边亮起为品牌色。HyperOS 输入框聚焦有可见反馈，静默认下回落到描边色
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (focused) colors.accent.copy(alpha = 0.5f) else colors.outline,
        animationSpec = tween(durationMillis = 200),
        label = "inputBorder",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            // 多行输入长高时平滑过渡，不再一行一跳
            .animateContentSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(shape)
            .background(colors.surface)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        // 固定项：加号 40 + 上下文环 40 + 发送 40 + 间隙
        // （三者都是 40dp 的点击区，比原来的 34/22/36 好按得多）
        val fixedWidth = 40.dp + 40.dp + 40.dp + 10.dp
        val modelWidth = (maxWidth - fixedWidth).coerceAtLeast(64.dp)
        val modelTextMax = (modelWidth - 26.dp).coerceAtLeast(40.dp)

        Column {
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp, max = 132.dp)
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .onFocusChanged { focused = it.isFocused },
                textStyle = type.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                decorationBox = { inner ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (input.isEmpty()) {
                            VxText(
                                text = tr("输入消息…"),
                                style = type.body,
                                color = colors.textTertiary,
                            )
                        }
                        inner()
                    }
                },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                AddButton(onClick = onAdd)

                Spacer(modifier = Modifier.weight(1f))

                ContextRing(budget = budget, onClick = onRingClick)

                ModelSelector(
                    model = model,
                    maxWidth = modelWidth,
                    textMaxWidth = modelTextMax,
                    onClick = onModelClick,
                )

                // 发送 ↔ 停止：交叉缩放过渡，不再硬切——同尺寸互换，不挤布局。
                // AnimatedContent 必须常驻：若外层再包 if，首帧状态即目标值，动画不会播
                AnimatedContent(
                    targetState = streaming,
                    transitionSpec = {
                        (scaleIn(
                            initialScale = 0.6f,
                            animationSpec = tween(VxMotion.EnterAlphaMs),
                        ) + fadeIn(tween(VxMotion.EnterAlphaMs))) togetherWith
                            (scaleOut(targetScale = 0.6f) + fadeOut(tween(VxMotion.DialogExitMs)))
                    },
                    label = "sendStopSwap",
                ) { isStreaming ->
                    if (isStreaming) {
                        StopButton(onClick = onStop)
                    } else {
                        SendButton(enabled = sendEnabled, onClick = onSend)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    val colors = VeluneTheme.colors
    val interaction = remember { MutableInteractionSource() }

    // 外层 40dp 承载点击，内层 34dp 是视觉圆。
    // 直接把视觉尺寸做到 40 会让输入栏变高、挤掉模型名，分成两层就没有这个问题。
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = vxRipple(colors.textPrimary.copy(alpha = 0.08f)),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(colors.fill),
            contentAlignment = Alignment.Center,
        ) {
            VxIcon(
                icon = VxIcon.Plus,
                tint = colors.textPrimary,
                size = 18.dp,
                strokeWidth = 2.1.dp,
            )
        }
    }
}

@Composable
private fun ModelSelector(
    model: ModelInfo,
    maxWidth: Dp,
    textMaxWidth: Dp,
    onClick: () -> Unit,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type
    val interaction = remember { MutableInteractionSource() }

    // 空间不足时截取末两段，比尾部省略更有信息量：Deepseek-V4.1-Flash -> V4.1-Flash
    val displayName = if (textMaxWidth < 108.dp && model.displayName.contains('-')) {
        model.displayName.split('-').takeLast(2).joinToString("-")
    } else {
        model.displayName
    }

    Row(
        modifier = Modifier
            .widthIn(max = maxWidth)
            .clip(SquircleCapsule())
            .clickable(
                interactionSource = interaction,
                indication = vxRipple(colors.textPrimary.copy(alpha = 0.08f), bounded = true),
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VxText(
            text = displayName,
            style = type.footnote.copy(fontWeight = FontWeight.Medium),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = textMaxWidth),
        )
        Spacer(modifier = Modifier.width(3.dp))
        VxIcon(
            icon = VxIcon.ChevronDown,
            tint = colors.textTertiary,
            size = 11.dp,
            strokeWidth = 1.8.dp,
        )
    }
}

/** 流式输出中：发送位变成停止位。 */
@Composable
private fun StopButton(onClick: () -> Unit) {
    val colors = VeluneTheme.colors
    val interaction = remember { MutableInteractionSource() }

    // 同 AddButton：40dp 点击区 / 36dp 视觉，补上按压反馈（之前裸 clickable 无波纹）
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = vxRipple(colors.textPrimary.copy(alpha = 0.14f)),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.textPrimary),
            contentAlignment = Alignment.Center,
        ) {
            VxIcon(icon = VxIcon.Stop, tint = colors.surface, size = 16.dp, strokeWidth = 2.4.dp)
        }
    }
}

@Composable
private fun SendButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = VeluneTheme.colors
    val interaction = remember { MutableInteractionSource() }

    // 可用态灰↔黑用颜色过渡：之前是瞬切，发完一条消息背景会闪一下
    val bg by animateColorAsState(
        targetValue = if (enabled) colors.textPrimary else colors.fill,
        animationSpec = tween(durationMillis = 200),
        label = "sendBg",
    )
    val tint by animateColorAsState(
        targetValue = if (enabled) colors.surface else colors.textTertiary,
        animationSpec = tween(durationMillis = 200),
        label = "sendTint",
    )

    // 同 AddButton：40dp 点击区 / 36dp 视觉，补上按压反馈
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = vxRipple(tint.copy(alpha = 0.2f)),
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            VxIcon(
                icon = VxIcon.SendUp,
                tint = tint,
                size = 18.dp,
                strokeWidth = 2.4.dp,
            )
        }
    }
}

@Preview(name = "输入栏 · 浅色", widthDp = 400)
@Composable
private fun ChatInputBarPreview() {
    VeluneTheme(darkTheme = false) {
        Column(modifier = Modifier.background(VeluneTheme.colors.canvasAlt)) {
            ChatInputBar(
                input = "",
                onInputChange = {},
                budget = ContextBudget(messageCount = 12, compressAt = 40),
                // 预览里用一个占位模型：真实值来自用户配置的服务商
                model = ModelInfo(
                    id = "preview",
                    displayName = "deepseek-flash",
                    provider = "DeepSeek",
                    accent = VeluneTheme.colors.accent,
                ),
                onAdd = {},
                onRingClick = {},
                onModelClick = {},
                onSend = {},
            )
        }
    }
}
