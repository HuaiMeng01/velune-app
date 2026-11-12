package com.veluneagent.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veluneagent.core.design.component.VxActionSheet
import com.veluneagent.core.design.component.VxAmbientBackground
import com.veluneagent.core.design.component.VxCard
import com.veluneagent.core.design.component.VxIcon
import com.veluneagent.core.design.component.VxIconButton
import com.veluneagent.core.design.component.VxRow
import com.veluneagent.core.design.component.VxSheetAction
import com.veluneagent.core.design.component.VxText
import com.veluneagent.core.design.component.bleedTop
import com.veluneagent.core.design.shape.SquircleShape
import com.veluneagent.core.design.theme.VeluneTheme
import com.veluneagent.core.design.theme.VxMotion
import com.veluneagent.core.design.theme.VxSpace

/**
 * 关于页。
 *
 * 版式对齐应用自己的关于页（参考 Nexio 课程表）：**居中大图标 → 应用昵称 → 版本号 → 卡片列表**。
 *
 * 和上一版的差别：上一版照小米「我的设备」做成了「品牌名 + 参数卡 + 型号卡」——
 * 那是**设备信息页**的结构；应用关于页的重点是"这是哪个应用、什么版本、去哪反馈"。
 */
@Composable
fun AboutScreen(
    version: String,
    /** 启动器图标资源（app 模块的 @mipmap/ic_launcher），关于页与桌面保持同一个图标。 */
    appIconRes: Int,
    onBack: () -> Unit,
    /** 反馈文档（金山文档） */
    onFeedbackDoc: () -> Unit,
    /** QQ 交流群 */
    onJoinGroup: () -> Unit,
    onRepository: () -> Unit,
    onDonate: () -> Unit,
    onLicenses: () -> Unit,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type

    val scroll = rememberScrollState()
    var feedbackOpen by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val statusTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }

    // 背景随滚动淡出，让卡片成为焦点
    val fadeDistance = with(density) { 320.dp.toPx() }
    val scrollProgress = (scroll.value / fadeDistance).coerceIn(0f, 1f)

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        // 背景层：整页铺，向上溢出到状态栏，不参与滚动
        VxAmbientBackground(
            modifier = Modifier.fillMaxSize().bleedTop(statusTop),
            fullBleed = true,
            alpha = 1f - scrollProgress,
        )

        // 返回键固定，不随内容滚动
        VxIconButton(
            icon = VxIcon.Back,
            contentDescription = "返回",
            onClick = onBack,
            tint = colors.textPrimary,
            size = 40.dp,
            iconSize = 20.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 6.dp, top = 6.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll),
        ) {
            // 图标上方留白：参考图里图标顶边在屏幕 21.6% 高度（约 194dp），
            // 这里的内容区已被 safeDrawing 让开状态栏，所以按 162dp 给到图标
            Spacer(modifier = Modifier.height(if (statusTop > 0.dp) 150.dp else 120.dp))

            // 图标与桌面启动器同源：之前是自绘的占位图标（项目没有图标资源），
            // 现在直接引用 @mipmap/ic_launcher，改图标只改一处
            Image(
                painter = painterResource(appIconRes),
                contentDescription = "应用图标",
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(96.dp),
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 昵称用品牌色：参考图里昵称也是彩色的（它用自家紫），
            // 32sp Bold 在浅底上对比度足够，且比黑字更像"品牌"
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                VxText(
                    text = "VeluneAgent",
                    style = type.largeTitle.copy(
                        fontSize = 32.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = colors.accent,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                VxText(text = "v$version", style = type.value, color = colors.textSecondary)
            }

            Spacer(modifier = Modifier.height(42.dp))

            Column(modifier = Modifier.padding(horizontal = VxSpace.cardInset)) {
                VxCard {
                    // 反馈有两个渠道（文档 / 群），用动作表让用户选，
                    // 比固定跳其中一个、把另一个藏起来更合理
                    VxRow(title = "交流与反馈", onClick = { feedbackOpen = true })
                    VxRow(title = "项目仓库", onClick = onRepository)
                    VxRow(title = "捐赠支持", valueText = "请作者喝咖啡", onClick = onDonate)
                    VxRow(title = "开源许可", onClick = onLicenses)
                }

                Spacer(modifier = Modifier.height(12.dp))

                ChangeLogCard()

                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        VxActionSheet(
            visible = feedbackOpen,
            title = "交流与反馈",
            caption = "遇到问题、想提建议，或者只是想聊聊，两个渠道都能找到我",
            actions = listOf(
                VxSheetAction(
                    label = "反馈文档",
                    icon = VxIcon.Doc,
                    onClick = {
                        feedbackOpen = false
                        onFeedbackDoc()
                    },
                ),
                VxSheetAction(
                    label = "加入 QQ 群",
                    icon = VxIcon.Message,
                    onClick = {
                        feedbackOpen = false
                        onJoinGroup()
                    },
                ),
            ),
            onDismiss = { feedbackOpen = false },
        )
    }
}

// ------------------------------------------------------------------ 更新日志

/** 一条版本记录。日期用 ISO 短格式，和参考图一致。 */
private class ChangeEntry(
    val version: String,
    val date: String,
    val items: List<String>,
)

/**
 * 更新日志数据。
 *
 * 只写用户能感知到的变化，不写实现细节——尺寸、字号、像素属于内部规格，不属于这里。
 */
private val CHANGE_LOG = listOf(
    ChangeEntry(
        version = "1.0.0.0",
        date = "2026-11-12",
        items = listOf(
            "正式开源：源代码与文档仓库公开",
            "全新应用图标，桌面与关于页同一来源",
            "设置界面全面统一：顶栏、列表、图标色板归到同一套规格",
            "开源许可页补全：新增 OkHttp、JSch、ZXing 等组件说明",
            "修正关于页与支持页中过时的描述",
        ),
    ),
    ChangeEntry(
        version = "0.5.4",
        date = "2026-11-11",
        items = listOf(
            "界面全面换新：设置、我的与底部导航按 HyperOS 观感重做",
            "新增应用偏好设置：浅色 / 深色、自动切换、取色跟随壁纸、字号调节",
            "新增 MCP 服务器支持：接入外部工具，工具列表自动同步",
            "新增 Linux 环境：在手机里运行 Python / Node / Java 等命令",
            "QQ 机器人：后台超时停止后可直接重新开启，停止原因会在设置页说明",
            "新增关于页与开源许可页",
        ),
    ),
)

/**
 * 更新日志卡：标题行 + 展开后的版本块。
 *
 * 参考图里它是**默认展开**的，点标题行收起。没有做成"点进去看列表"——
 * 目前只有一个版本的变更，跳转反而多一次操作。
 */
@Composable
private fun ChangeLogCard() {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type
    var expanded by remember { mutableStateOf(true) }

    // 箭头不换图标而是转 180°：切换图标会有停顿，旋转才有「这个面板是被我打开的」的因果感
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(
            durationMillis = if (expanded) VxMotion.ExpandMs else VxMotion.CollapseMs,
            easing = if (expanded) VxMotion.EaseOutCubic else VxMotion.EaseOutSine,
        ),
        label = "changeLogChevron",
    )

    VxCard {
        VxRow(
            title = "更新日志",
            showChevron = false,
            onClick = { expanded = !expanded },
            trailing = {
                VxIcon(
                    icon = VxIcon.ChevronDown,
                    tint = colors.textTertiary,
                    size = 18.dp,
                    modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
                )
            },
        )

        // HyperOS 同款折叠：高度随曲线展开 / 收起，同时内容淡入淡出。
        // 之前是 if 硬切——点一下整块内容瞬间消失，卡片像被抽掉了一截。
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = tween(VxMotion.ExpandMs, easing = VxMotion.EaseOutCubic),
            ) + fadeIn(tween(VxMotion.EnterAlphaMs, easing = VxMotion.EaseOutQuad)),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(VxMotion.CollapseMs, easing = VxMotion.EaseOutSine),
            ) + fadeOut(tween(VxMotion.CollapseMs, easing = VxMotion.EaseOutSine)),
        ) {
            CHANGE_LOG.forEach { entry ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VxText(
                            text = "v${entry.version}",
                            style = type.rowTitle,
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        VxText(
                            text = entry.date,
                            style = type.footnote,
                            color = colors.textTertiary,
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    entry.items.forEach { item ->
                        Row(
                            modifier = Modifier.padding(bottom = 7.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            // 圆点用 3dp 实心，比"·"字符更稳定（字体会让它左右飘）
                            Box(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .size(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(colors.textTertiary),
                            )
                            Spacer(modifier = Modifier.width(9.dp))
                            VxText(
                                text = item,
                                style = type.footnote.copy(lineHeight = 21.sp),
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}
