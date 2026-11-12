package com.veluneagent.feature.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veluneagent.core.data.sandbox.Sandbox
import com.veluneagent.core.design.component.VxActionSheet
import com.veluneagent.core.design.component.VxCard
import com.veluneagent.core.design.component.VxEmptyState
import com.veluneagent.core.design.component.VxIcon
import com.veluneagent.core.design.component.VxIconButton
import com.veluneagent.core.design.component.VxSheetAction
import com.veluneagent.core.design.component.VxText
import com.veluneagent.core.design.l10n.tr
import com.veluneagent.core.design.l10n.trf
import com.veluneagent.core.design.theme.VeluneTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 产出文件：AI 通过 `fs_write` 写进沙箱的东西都在这。
 *
 * 为什么需要单独一页：文件在 `filesDir/sandbox` 下，系统的文件管理器根本进不来，
 * 用户在聊天里划过去之后就再也找不到了。这一页是唯一的「出口」，且每个文件都能直接用
 * 系统应用打开（FileProvider + ACTION_VIEW）。
 */
@Composable
fun FilesScreen(
    sandbox: Sandbox?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type
    val context = LocalContext.current

    var files by remember { mutableStateOf(emptyList<Sandbox.SandboxFile>()) }
    var reload by remember { mutableStateOf(0) }
    var target by remember { mutableStateOf<Sandbox.SandboxFile?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reload, sandbox) {
        files = sandbox?.files().orEmpty()
    }

    LaunchedEffect(notice) {
        if (notice == null) return@LaunchedEffect
        delay(2000)
        notice = null
    }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        // 顶栏：与设置子页保持一致（左返回 + 居中标题）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            VxIconButton(
                icon = VxIcon.Back,
                contentDescription = "返回",
                onClick = onBack,
                tint = colors.textPrimary,
                size = 36.dp,
                iconSize = 18.dp,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            VxText(
                text = "产出文件",
                style = type.rowTitle,
                color = colors.textPrimary,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            if (files.isEmpty()) {
                VxCard {
                    VxEmptyState(
                        icon = VxIcon.Doc,
                        title = "还没有产出文件",
                        description = "让 AI 把结果存成文件，例如「把这份清单保存成 todo.md」",
                    )
                }
            } else {
                VxCard {
                    files.forEach { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { target = file }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            VxIcon(
                                icon = VxIcon.Doc,
                                tint = colors.accent,
                                size = 16.dp,
                                strokeWidth = 1.8.dp,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                VxText(
                                    text = file.path.substringAfterLast('/'),
                                    style = type.rowTitle,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                VxText(
                                    text = buildString {
                                        val dir = file.path.substringBeforeLast('/', "")
                                        if (dir.isNotEmpty()) append("$dir · ")
                                        append(formatSize(file.size))
                                        append(" · ")
                                        append(
                                            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                                                .format(Date(file.modifiedAt)),
                                        )
                                    },
                                    style = type.footnote.copy(fontSize = 11.sp),
                                    color = colors.textTertiary,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                VxText(
                    text = "文件保存在应用私有目录，其他应用无法直接访问；点击文件可选择用系统应用打开。",
                    style = type.footnote.copy(fontSize = 11.sp),
                    color = colors.textTertiary,
                )
            }

            if (notice != null) {
                Spacer(modifier = Modifier.height(10.dp))
                VxText(
                    text = notice.orEmpty(),
                    style = type.footnote,
                    color = colors.accent,
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    val file = target
    if (file != null) {
        VxActionSheet(
            visible = true,
            title = file.path.substringAfterLast('/'),
            caption = file.path,
            actions = listOf(
                VxSheetAction(
                    label = "用其他应用打开",
                    icon = VxIcon.SendUp,
                    onClick = {
                        val box = sandbox
                        notice = if (box != null && openSandboxFile(context, box, file.path)) {
                            "已交给系统打开"
                        } else {
                            "没有能打开该类型的应用，可试用「分享」"
                        }
                        target = null
                    },
                ),
                VxSheetAction(
                    label = "分享文件",
                    icon = VxIcon.Copy,
                    onClick = {
                        val box = sandbox
                        notice = if (box != null && shareSandboxFile(context, box, file.path)) {
                            "已选择分享目标"
                        } else {
                            "分享失败"
                        }
                        target = null
                    },
                ),
                VxSheetAction(
                    label = "删除",
                    icon = VxIcon.Trash,
                    destructive = true,
                    onClick = {
                        val removed = sandbox?.delete(file.path) == true
                        notice = if (removed) trf("已删除 %s", file.path) else tr("删除失败")
                        target = null
                        reload++
                    },
                ),
            ),
            onDismiss = { target = null },
        )
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024f)
    else -> "%.1f MB".format(bytes / 1024f / 1024f)
}
