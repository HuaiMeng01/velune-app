package com.veluneagent.core.design.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.veluneagent.core.design.theme.VeluneTheme
import com.veluneagent.core.design.theme.VxSpace

/**
 * 可折叠大标题页脚手架（对齐小米设置的两级标题）。
 *
 * - 展开：26sp Medium 品牌蓝，左缩进 28dp；
 * - 折叠：滚动 44dp 后大标题淡出，顶部 56dp 栏出现居中 17sp 蓝色标题；
 * - 蒙层：**由深到浅的竖向渐变**（栏底不透明 → 24dp 处全透明），不是一整片半透明；
 * - 状态栏：栏底向上延伸到状态栏区域，避免滚动时状态栏与内容之间出现一条色带。
 *
 * [bottomContentPadding] 留在滚动内容**内部**：底部浮着的玻璃导航条要能盖到内容上，
 * 但最后一项还必须能滚出来，两者只能靠内容内边距同时满足。
 */
@Composable
fun VxCollapsingScaffold(
    title: String,
    modifier: Modifier = Modifier,
    topSpacer: Dp = 8.dp,
    collapseDistance: Dp = 44.dp,
    bottomContentPadding: Dp = 96.dp,
    titleTrailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.(ScrollState) -> Unit,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type
    val scroll = rememberScrollState()

    val density = LocalDensity.current
    val collapsePx = remember(density, collapseDistance) {
        with(density) { collapseDistance.toPx() }
    }
    val fraction by remember(collapsePx) {
        derivedStateOf { (scroll.value / collapsePx).coerceIn(0f, 1f) }
    }

    // 根容器已经吃掉了状态栏内边距，这里取状态栏高度只是为了让栏底能向上覆盖它
    val statusBar = WindowInsets.statusBars
    val statusTopDp = with(density) { statusBar.getTop(this).toDp() }

    val fadeHeight = 24.dp
    val barTotal = statusTopDp + VxSpace.collapsedBarHeight + fadeHeight
    val solidStop = ((statusTopDp + VxSpace.collapsedBarHeight) / barTotal).coerceIn(0.1f, 0.95f)

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll)) {
            Spacer(modifier = Modifier.height(topSpacer))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = VxSpace.titleInset)
                    .alpha(1f - fraction),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VxText(
                    text = title,
                    style = type.largeTitle,
                    color = colors.accent,
                )
                if (titleTrailing != null) {
                    Spacer(modifier = Modifier.weight(1f))
                    titleTrailing()
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            content(scroll)

            Spacer(modifier = Modifier.height(bottomContentPadding))
        }

        // 顶栏：向上顶进状态栏区域，底部用渐变收口，滚过的内容由深到浅化开
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = -statusTopDp)
                .height(barTotal)
                .background(
                    Brush.verticalGradient(
                        0f to colors.surface.copy(alpha = fraction),
                        solidStop to colors.surface.copy(alpha = 0.94f * fraction),
                        1f to Color.Transparent,
                    ),
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = statusTopDp)
                    .height(VxSpace.collapsedBarHeight),
                contentAlignment = Alignment.Center,
            ) {
                VxText(
                    text = title,
                    // 真机折叠标题 17sp Medium，与行标题同档
                    style = type.rowTitle,
                    color = colors.accent,
                    modifier = Modifier.alpha(fraction),
                )
            }
        }
    }
}
