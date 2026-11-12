package com.veluneagent.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veluneagent.core.design.theme.VeluneTheme
import com.veluneagent.core.design.theme.VxSpace

/**
 * 次级页顶栏（56dp 固定高度、居中 17sp Medium 标题、左侧 40dp 返回热区）。
 *
 * 这是全仓子页**唯一**的顶栏实现：`VxSubPageScaffold` 与设置模块的 `SettingsTopBar`
 * 都转调这里。以前是两份各写一遍（一份自绘、一份转交 MIUIX `SmallTopAppBar`），
 * 结果 14 个子页里 4 页返回图标 20dp、10 页 22dp，标题字号也随库走——
 * 跨页时顶栏一跳，整页就不像同一个应用了。
 *
 * 顶栏不画背景色：底色由页面根容器负责（各页都是 `background(colors.background)`）。
 */
@Composable
fun VxSubPageBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(VxSpace.collapsedBarHeight),
    ) {
        VxText(
            text = title,
            style = type.rowTitle,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                // 左右各留 64dp：给返回键与右侧动作让位，标题永远居中
                .padding(horizontal = 64.dp),
        )

        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VxIconButton(
                icon = VxIcon.Back,
                onClick = onBack,
                contentDescription = "返回",
                tint = colors.textPrimary,
                size = 40.dp,
                iconSize = 20.dp,
            )
        }

        if (actions != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

/**
 * 次级页骨架（用户协议 / 隐私政策 / 开源许可 / 支持我们，以及后续所有子页）。
 *
 * 与主标签页的区别：没有可折叠大标题，只有一条 56dp 固定顶栏 —— 次级页的标题
 * 是"定位"而不是"焦点"，跟着滚动收起反而会让人失去位置感。
 *
 * 顶栏规格见 [VxSubPageBar]；下方不画分割线（小米设置子页顶栏与内容同底色）。
 */
@Composable
fun VxSubPageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = VeluneTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        VxSubPageBar(title = title, onBack = onBack, actions = actions)
        content()
    }
}
