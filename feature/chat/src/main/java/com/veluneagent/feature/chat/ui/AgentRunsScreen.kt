package com.veluneagent.feature.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veluneagent.core.data.agent.AgentRunStore
import com.veluneagent.core.design.component.VxCard
import com.veluneagent.core.design.component.VxEmptyState
import com.veluneagent.core.design.component.VxIcon
import com.veluneagent.core.design.component.VxSectionLabel
import com.veluneagent.core.design.component.VxText
import com.veluneagent.core.design.l10n.tr
import com.veluneagent.core.design.l10n.trf
import com.veluneagent.core.design.theme.VeluneTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 执行轨迹回放。
 *
 * `agent_runs` / `agent_steps` 从第一版就在写，但一直没有读取方 —— 用户看不到
 * 「这轮 Agent 到底做了什么」。这个页面补上这个缺口：按时间倒序列出每次运行，
 * 展开能看到每一步的工具名、状态与耗时。
 *
 * 只读，不提供任何"重跑"能力：重跑会把已经发生的副作用（发消息、写文件）再来一遍。
 */
@Composable
fun AgentRunsScreen(
    conversationId: Long,
    runStore: AgentRunStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type

    var runs by remember { mutableStateOf<List<AgentRunStore.RunSummary>?>(null) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var steps by remember { mutableStateOf<List<AgentRunStore.RunStep>>(emptyList()) }

    LaunchedEffect(conversationId) {
        runs = runStore.listRuns(conversationId)
    }
    // 展开时才加载步骤：一次运行可能有几十步，进来就全查是浪费
    LaunchedEffect(expandedId) {
        steps = expandedId?.let { runStore.recentSteps(it) } ?: emptyList()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvasAlt),
    ) {
        SmallTopAppBar(
            title = "执行轨迹",
            color = colors.canvasAlt,
            defaultWindowInsetsPadding = false,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(MiuixIcons.Back, contentDescription = tr("返回"), modifier = Modifier.size(22.dp))
                }
            },
        )

        val loaded = runs
        when {
            loaded == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    VxText(text = "读取中…", style = type.footnote, color = colors.textTertiary)
                }
            }

            loaded.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    VxEmptyState(
                        icon = VxIcon.Sliders,
                        title = "还没有执行记录",
                        description = "开启 Agent 后，每次调用工具都会记在这里",
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    item {
                        VxSectionLabel(text = trf("最近 %d 次运行", loaded.size))
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    items(items = loaded, key = { it.id }) { run ->
                        RunCard(
                            run = run,
                            expanded = run.id == expandedId,
                            steps = if (run.id == expandedId) steps else emptyList(),
                            onToggle = {
                                expandedId = if (expandedId == run.id) null else run.id
                            },
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RunCard(
    run: AgentRunStore.RunSummary,
    expanded: Boolean,
    steps: List<AgentRunStore.RunStep>,
    onToggle: () -> Unit,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type
    val (label, tint) = runStatusOf(run.status, colors)

    VxCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(tint),
                )
                Spacer(modifier = Modifier.size(8.dp))
                VxText(
                    text = label,
                    style = type.rowTitle,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.weight(1f))
                VxText(
                    text = formatRunTime(run.startedAt),
                    style = type.footnote.copy(fontSize = 12.sp),
                    color = colors.textTertiary,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            VxText(
                text = buildString {
                    append(trf("%d 步 · %d 次工具", run.steps, run.toolCalls))
                    if (run.totalTokens > 0) append(trf(" · %d token", run.totalTokens))
                    if (run.durationMs > 0) append(trf(" · %.1f秒", run.durationMs / 1000.0))
                },
                style = type.footnote.copy(fontSize = 12.sp),
                color = colors.textSecondary,
            )

            run.error?.takeIf { it.isNotBlank() }?.let { error ->
                Spacer(modifier = Modifier.height(4.dp))
                VxText(
                    text = error,
                    style = type.footnote.copy(fontSize = 11.sp),
                    color = colors.danger,
                    maxLines = 3,
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))
                if (steps.isEmpty()) {
                    VxText(
                        text = "没有记录到步骤",
                        style = type.footnote.copy(fontSize = 12.sp),
                        color = colors.textTertiary,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        steps.forEach { step ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                VxText(
                                    text = "${step.index + 1}",
                                    style = type.footnote.copy(fontSize = 11.sp),
                                    color = colors.textTertiary,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                VxText(
                                    text = step.title,
                                    style = type.footnote.copy(fontSize = 12.sp),
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f),
                                )
                                if (step.durationMs > 0) {
                                    VxText(
                                        text = "${step.durationMs}ms",
                                        style = type.footnote.copy(fontSize = 11.sp),
                                        color = colors.textTertiary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun runStatusOf(
    status: String,
    colors: com.veluneagent.core.design.theme.VeluneColors,
): Pair<String, androidx.compose.ui.graphics.Color> = when (status.lowercase()) {
    "completed" -> "完成" to colors.success
    "budget_exceeded" -> "超出预算" to colors.warning
    "loop_detected" -> "检测到循环" to colors.warning
    "too_many_failures" -> "连续失败" to colors.danger
    "cancelled" -> "已取消" to colors.textTertiary
    "error" -> "出错" to colors.danger
    else -> status to colors.textSecondary
}

// 日期模式跟语言走：读状态值而非快照，切语言后下次进入页面生效（SimpleDateFormat 不能缓存成单例）
private fun runTimeFormat(): SimpleDateFormat = SimpleDateFormat(
    if (com.veluneagent.core.design.l10n.VxL10n.isEnglish) "MMM d, HH:mm" else "M月d日 HH:mm",
    Locale.getDefault(),
)

private fun formatRunTime(at: Long): String = runTimeFormat().format(Date(at))
