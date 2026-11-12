package com.veluneagent.feature.chat.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.veluneagent.core.design.component.VxIcon
import com.veluneagent.core.design.l10n.tr
import com.veluneagent.core.design.l10n.trf
import com.veluneagent.core.design.component.VxIconButton
import com.veluneagent.core.design.component.vxRipple
import com.veluneagent.core.design.component.VxText
import com.veluneagent.core.data.ChatPrefs
import com.veluneagent.core.design.shape.SquircleCapsule
import com.veluneagent.core.design.shape.SquircleShape
import com.veluneagent.core.design.theme.VeluneTheme
import com.veluneagent.core.design.theme.VxRadius

/**
 * 上下文窗口设置面板。
 *
 * 语义是「每次请求最多带多少条历史」，不是「超过就摘要」——
 * 摘要需要一次额外的模型调用，当前版本没有实现，文案不能承诺做不到的事。
 */
@Composable
fun CompressSheet(
    visible: Boolean,
    currentThreshold: Int,
    messageCount: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    triggerTokens: Int = ChatPrefs.DefaultTriggerTokens,
    onSelectTrigger: (Int) -> Unit = {},
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type

    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = if (visible) 240 else 180),
        label = "compressSheet",
    )

    if (progress <= 0.01f) return

    androidx.activity.compose.BackHandler(enabled = visible) { onDismiss() }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress }
                .background(colors.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = progress
                    translationY = (1f - progress) * 240f
                }
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .clip(SquircleShape(VxRadius.sheet))
                .background(colors.surface)
                // 吞掉空白区域的点击，避免穿透到遮罩触发关闭
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VxText(
                    text = "上下文压缩",
                    style = type.title,
                    modifier = Modifier.weight(1f),
                )
                VxIconButton(
                    icon = VxIcon.Close,
                    contentDescription = tr("关闭"),
                    onClick = onDismiss,
                    tint = colors.textTertiary,
                    size = 30.dp,
                    iconSize = 15.dp,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            VxText(
                text = trf(
                    "当前 %d 条消息。超出的旧消息会被压成摘要后发送，本地记录始终完整保留。",
                    messageCount,
                ),
                style = type.footnote,
                color = colors.textSecondary,
            )

            Spacer(modifier = Modifier.height(14.dp))

            VxText(text = "保留最近几条原文", style = type.footnote, color = colors.textTertiary)
            Spacer(modifier = Modifier.height(6.dp))

            ChatPrefs.CompressOptions.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { option ->
                        ThresholdChip(
                            label = trf("%d 条", option),
                            selected = option == currentThreshold,
                            onClick = { onSelect(option) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(3 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            VxText(
                text = "折叠内容超过多少 token 才触发摘要（越小越省钱，也越容易「忘事」）",
                style = type.footnote,
                color = colors.textTertiary,
            )
            Spacer(modifier = Modifier.height(8.dp))

            ChatPrefs.TriggerOptions.chunked(4).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { option ->
                        ThresholdChip(
                            label = "${option / 1000}k",
                            selected = option == triggerTokens,
                            onClick = { onSelectTrigger(option) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            VxText(
                text = "摘要生成失败时会自动退回「只发最近几条」，不会让消息发不出去。",
                style = type.footnote,
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun ThresholdChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type

    Box(
        modifier = modifier
            .heightIn(min = 42.dp)
            .clip(SquircleCapsule())
            .background(if (selected) colors.accent.copy(alpha = 0.12f) else colors.fill)
            .border(
                width = 1.dp,
                color = if (selected) colors.accent.copy(alpha = 0.5f) else androidx.compose.ui.graphics.Color.Transparent,
                shape = SquircleCapsule(),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        VxText(
            text = label,
            style = type.value,
            color = if (selected) colors.accent else colors.textPrimary,
        )
    }
}
