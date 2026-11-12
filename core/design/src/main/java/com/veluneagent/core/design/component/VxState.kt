package com.veluneagent.core.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veluneagent.core.design.theme.VeluneTheme

/**
 * 空态。
 *
 * 之前每个页面的空态都是手写一遍「Box 居中 + 大图标 + 一行小字」，
 * 图标大小、间距、字号各不相同（插件页 26dp、服务商页 28dp、文件页又是另一套）。
 * 统一到这里后，改一处即可影响全部，也避免新页面再复制一份。
 *
 * [action] 给需要「立刻能做点什么」的场合（例如「新建一个」按钮），
 * 没有行动项时不要传 —— 空态里塞按钮容易变成第二个工具栏。
 */
@Composable
fun VxEmptyState(
    icon: VxIcon,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    /** 主页面（消息列表）的空态可以更大：图标 26dp 在整屏留白里显得太小。 */
    iconSize: Dp = 26.dp,
    titleStyle: TextStyle = VeluneTheme.type.footnote,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 26.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        VxIcon(
            icon = icon,
            tint = colors.textTertiary,
            size = iconSize,
            strokeWidth = 1.6.dp,
        )
        Spacer(modifier = Modifier.height(if (iconSize > 30.dp) 12.dp else 10.dp))
        VxText(
            text = title,
            style = titleStyle,
            color = colors.textSecondary,
        )
        if (!description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            VxText(
                text = description,
                style = type.footnote.copy(fontSize = 12.sp),
                color = colors.textTertiary,
            )
        }
        if (action != null) {
            Spacer(modifier = Modifier.height(14.dp))
            action()
        }
    }
}
