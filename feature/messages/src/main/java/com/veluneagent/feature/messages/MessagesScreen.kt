package com.veluneagent.feature.messages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.veluneagent.core.data.Conversation
import com.veluneagent.core.data.ConversationSort
import com.veluneagent.core.design.component.VxActionMenu
import com.veluneagent.core.design.component.VxActionSheet
import com.veluneagent.core.design.component.VxEmptyState
import com.veluneagent.core.design.component.VxIcon
import com.veluneagent.core.design.component.VxIconButton
import com.veluneagent.core.design.component.VxMenuItem
import com.veluneagent.core.design.component.VxPromptSheet
import com.veluneagent.core.design.component.VxSearchBar
import com.veluneagent.core.design.component.VxSheetAction
import com.veluneagent.core.design.component.VxText
import com.veluneagent.core.design.component.vxRipple
import com.veluneagent.core.design.l10n.tr
import com.veluneagent.core.design.l10n.trf
import com.veluneagent.core.design.shape.SquircleCapsule
import com.veluneagent.core.design.shape.SquircleShape
import com.veluneagent.core.design.theme.VeluneTheme
import com.veluneagent.core.design.theme.VxMotion
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * 消息页：聊天式连续列表。
 *
 * 数据由上层传入（VeluneApp 持有 ChatStore 并收集 Flow），本页保持纯函数。
 */
@Composable
fun MessagesScreen(
    conversations: List<Conversation>,
    onOpenSession: (Conversation) -> Unit,
    onCreateSession: () -> Unit,
    /** 选了角色再新建：把 promptId 绑到新会话上 */
    onCreateSessionWithRole: (Long) -> Unit = {},
    /** 有角色时，新建对话先让用户挑一个 —— 否则多角色只是摆设 */
    prompts: List<com.veluneagent.core.data.prompt.Prompt> = emptyList(),
    onPin: (Conversation) -> Unit,
    onDelete: (Conversation) -> Unit,
    onRename: (Conversation, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    onSearch: (suspend (String) -> List<Conversation>)? = null,
    /** 「+ → 创建角色」：把名字与设定交给上层去建角色 + 建会话 + 绑定。 */
    onStartCharacterChat: (String, String) -> Unit = { _, _ -> },
    /** 首次查询还没回来：显示骨架，而不是先闪一句"还没有会话" */
    loading: Boolean = false,
    /** 当前排序方式，只用于在弹层里标记选中项 */
    sortMode: ConversationSort = ConversationSort.Recent,
    onSortChange: (ConversationSort) -> Unit = {},
    /** 单个归档/恢复：true = 归档，false = 从归档箱恢复 */
    onArchive: (Conversation, Boolean) -> Unit = { _, _ -> },
    /** 批量归档/恢复：true = 归档，false = 从归档箱恢复 */
    onArchiveMany: (List<Long>, Boolean) -> Unit = { _, _ -> },
    onDeleteMany: (List<Long>) -> Unit = {},
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type
    var query by remember { mutableStateOf("") }
    var menuTarget by remember { mutableStateOf<Conversation?>(null) }
    var renameTarget by remember { mutableStateOf<Conversation?>(null) }
    var deleteTarget by remember { mutableStateOf<Conversation?>(null) }
    var searchResults by remember { mutableStateOf<List<Conversation>?>(null) }
    var plusMenuOpen by remember { mutableStateOf(false) }
    var roleSheetOpen by remember { mutableStateOf(false) }
    var characterDialogOpen by remember { mutableStateOf(false) }
    // 选择模式：批量归档/删除。选中集合用 id，避免会话对象刷新后对不上
    var selectMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var archiveOpen by remember { mutableStateOf(false) }
    var sortSheetOpen by remember { mutableStateOf(false) }
    var batchDeleteConfirm by remember { mutableStateOf(false) }

    // 搜索落到库里：标题之外还要能命中消息正文。180ms 防抖，避免每敲一个字都查一次。
    LaunchedEffect(query, conversations) {
        val keyword = query.trim()
        if (keyword.isEmpty() || onSearch == null) {
            searchResults = null
            return@LaunchedEffect
        }
        delay(180)
        searchResults = onSearch(keyword)
    }

    // 归档的会话只在归档箱里出现。不在 SQL 里过滤是为了切换归档箱时不用重查，
    // 而且「已归档」这个状态本身也要显示在行上（归档箱里能一眼看出是归档态）
    val active = conversations.filter { !it.archived }
    val archived = conversations.filter { it.archived }
    val source = if (archiveOpen) archived else active

    val filtered = if (query.isBlank()) {
        source
    } else {
        // 搜索结果可能同时包含归档与未归档，按当前视图再滤一次
        (searchResults ?: conversations.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.preview.contains(query, ignoreCase = true)
        }).filter { it.archived == archiveOpen }
    }

    Box(modifier = modifier.fillMaxSize().background(colors.canvasAlt)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    selectMode -> {
                        VxText(
                            text = trf("已选 %d 项", selected.size),
                            style = type.largeTitle,
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        VxText(
                            text = "取消",
                            style = type.rowTitle,
                            color = colors.accent,
                            modifier = Modifier
                                .clip(SquircleCapsule())
                                .clickable {
                                    selectMode = false
                                    selected = emptySet()
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }

                    archiveOpen -> {
                        VxIconButton(
                            icon = VxIcon.Back,
                            contentDescription = tr("返回消息列表"),
                            onClick = { archiveOpen = false },
                            tint = colors.textPrimary,
                            size = 40.dp,
                            iconSize = 19.dp,
                        )
                        VxText(
                            text = "归档箱",
                            style = type.largeTitle,
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        VxText(
                            text = trf("%d 个", archived.size),
                            style = type.footnote,
                            color = colors.textTertiary,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }

                    else -> {
                        // 规范 3.4.3：标题永不用品牌色，与其他页的大标题一致
                        VxText(text = "消息", style = type.largeTitle, color = colors.textPrimary)
                        Spacer(modifier = Modifier.weight(1f))
                        NewChatButton(onClick = { plusMenuOpen = true })
                    }
                }
            }

            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                VxSearchBar(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "搜索",
                )
            }

            if (loading && conversations.isEmpty()) {
                SkeletonList()
            } else if (filtered.isEmpty()) {
                EmptyHint(hasSessions = source.isNotEmpty())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // 96dp：底部浮着的玻璃导航条约 76dp 高，留够让最后一条能滚到玻璃上方，
                    // 同时列表在滚动过程中确实从玻璃下面穿过（液态玻璃才有东西可折射）
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    items(items = filtered, key = { it.id }) { conversation ->
                        SwipeableSessionRow(
                            conversation = conversation,
                            selecting = selectMode,
                            selected = conversation.id in selected,
                            onOpen = {
                                if (selectMode) {
                                    selected = if (conversation.id in selected) {
                                        selected - conversation.id
                                    } else {
                                        selected + conversation.id
                                    }
                                } else {
                                    onOpenSession(conversation)
                                }
                            },
                            onLongPress = { if (!selectMode) menuTarget = conversation },
                            onPin = { onPin(conversation) },
                            // 左滑删除与长按菜单走同一条确认路径：
                            // 之前这里直接 onDelete，手滑一下就永久删掉整个会话。
                            onDelete = { deleteTarget = conversation },
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp)
                                .height(0.5.dp)
                                .background(colors.divider),
                        )
                    }
                }
            }
        }

        // 批量操作条浮在 tab bar 上方：贴着底部会被玻璃条压住
        if (selectMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 92.dp)
                    .clip(SquircleShape(18.dp))
                    .background(colors.surface)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VxText(
                    text = if (archiveOpen) "恢复" else "归档",
                    style = type.rowTitle,
                    color = if (selected.isEmpty()) colors.textTertiary else colors.accent,
                    modifier = Modifier
                        .clip(SquircleCapsule())
                        .clickable(enabled = selected.isNotEmpty()) {
                            onArchiveMany(selected.toList(), !archiveOpen)
                            selectMode = false
                            selected = emptySet()
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Spacer(modifier = Modifier.weight(1f))
                VxText(
                    text = "删除",
                    style = type.rowTitle,
                    color = if (selected.isEmpty()) colors.textTertiary else colors.danger,
                    modifier = Modifier
                        .clip(SquircleCapsule())
                        .clickable(enabled = selected.isNotEmpty()) { batchDeleteConfirm = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        if (sortSheetOpen) {
            VxActionSheet(
                visible = true,
                title = "排序方式",
                caption = "置顶的会话永远在最上面，其余按这里选的方式排。",
                actions = ConversationSort.entries.map { mode ->
                    val label = when (mode) {
                        ConversationSort.Recent -> "最近活跃"
                        ConversationSort.Created -> "创建时间"
                        ConversationSort.Title -> "标题"
                    }
                    VxSheetAction(
                        label = if (mode == sortMode) trf("%s（当前）", label) else label,
                        icon = if (mode == sortMode) VxIcon.Check else null,
                        onClick = {
                            onSortChange(mode)
                            sortSheetOpen = false
                        },
                    )
                },
                onDismiss = { sortSheetOpen = false },
            )
        }

        if (batchDeleteConfirm) {
            VxActionSheet(
                visible = true,
                title = trf("删除 %d 个会话", selected.size),
                caption = "消息会一并删除，无法恢复。",
                actions = listOf(
                    VxSheetAction(
                        label = "删除",
                        icon = VxIcon.Trash,
                        destructive = true,
                        onClick = {
                            onDeleteMany(selected.toList())
                            batchDeleteConfirm = false
                            selectMode = false
                            selected = emptySet()
                        },
                    ),
                ),
                onDismiss = { batchDeleteConfirm = false },
            )
        }

        val target = menuTarget
        if (target != null) {
            VxActionMenu(
                items = listOf(
                    VxMenuItem(
                        icon = VxIcon.Pencil,
                        label = "重命名",
                        onClick = {
                            menuTarget = null
                            renameTarget = target
                        },
                    ),
                    VxMenuItem(
                        icon = VxIcon.Pin,
                        label = if (target.pinned) "取消置顶" else "置顶会话",
                        onClick = {
                            menuTarget = null
                            onPin(target)
                        },
                    ),
                    VxMenuItem(
                        icon = VxIcon.Doc,
                        label = if (target.archived) "移出归档箱" else "归档",
                        onClick = {
                            menuTarget = null
                            onArchive(target, !target.archived)
                        },
                    ),
                    VxMenuItem(
                        icon = VxIcon.Trash,
                        label = "删除会话",
                        onClick = {
                            menuTarget = null
                            // 删除不可撤销，先弹二次确认，避免误触长按菜单
                            deleteTarget = target
                        },
                    ),
                ),
                visible = true,
                onDismiss = { menuTarget = null },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = 88.dp),
                width = 220.dp,
            )
        }

        val renaming = renameTarget
        VxPromptSheet(
            visible = renaming != null,
            title = "重命名会话",
            initialValue = renaming?.title.orEmpty(),
            placeholder = "会话标题",
            onConfirm = { value ->
                renaming?.let { onRename(it, value) }
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )

        if (plusMenuOpen) {
            VxActionMenu(
                items = buildList {
                    add(
                        VxMenuItem(
                            icon = VxIcon.Message,
                            label = "新建对话",
                            onClick = {
                                plusMenuOpen = false
                                if (prompts.isEmpty()) onCreateSession() else roleSheetOpen = true
                            },
                        ),
                    )
                    add(
                        VxMenuItem(
                            icon = VxIcon.Model,
                            label = "创建角色",
                            onClick = {
                                plusMenuOpen = false
                                characterDialogOpen = true
                            },
                        ),
                    )
                    add(
                        VxMenuItem(
                            icon = VxIcon.Sliders,
                            label = "排序方式",
                            onClick = {
                                plusMenuOpen = false
                                sortSheetOpen = true
                            },
                        ),
                    )
                    add(
                        VxMenuItem(
                            icon = VxIcon.Check,
                            label = "选择会话",
                            onClick = {
                                plusMenuOpen = false
                                selectMode = true
                                selected = emptySet()
                            },
                        ),
                    )
                    if (archived.isNotEmpty() || archiveOpen) {
                        add(
                            VxMenuItem(
                                icon = VxIcon.Doc,
                                label = if (archiveOpen) tr("退出归档箱") else trf("归档箱（%d）", archived.size),
                                onClick = {
                                    plusMenuOpen = false
                                    archiveOpen = !archiveOpen
                                },
                            ),
                        )
                    }
                },
                visible = true,
                onDismiss = { plusMenuOpen = false },
                align = Alignment.TopEnd,
                contentPadding = PaddingValues(top = 6.dp, end = 10.dp),
                width = 196.dp,
            )
        }

        if (characterDialogOpen) {
            CharacterDialog(
                onDismiss = { characterDialogOpen = false },
                onConfirm = { name, persona ->
                    characterDialogOpen = false
                    onStartCharacterChat(name, persona)
                },
            )
        }

        if (roleSheetOpen) {
            VxActionSheet(
                visible = true,
                title = "用哪个角色",
                caption = "选一个角色开始新对话；也可以不用角色，直接聊。",
                actions = buildList {
                    add(
                        VxSheetAction(
                            label = "不用角色",
                            icon = VxIcon.Message,
                            onClick = {
                                roleSheetOpen = false
                                onCreateSession()
                            },
                        ),
                    )
                    prompts.forEach { prompt ->
                        add(
                            VxSheetAction(
                                label = prompt.name,
                                onClick = {
                                    roleSheetOpen = false
                                    onCreateSessionWithRole(prompt.id)
                                },
                            ),
                        )
                    }
                },
                onDismiss = { roleSheetOpen = false },
            )
        }

        val deleting = deleteTarget
        if (deleting != null) {
            VxActionSheet(
                visible = true,
                title = trf("删除「%s」", deleting.title),
                caption = "该会话的全部消息会一并删除，无法恢复。",
                actions = listOf(
                    VxSheetAction(
                        label = "删除会话",
                        icon = VxIcon.Trash,
                        destructive = true,
                        onClick = {
                            deleteTarget = null
                            onDelete(deleting)
                        },
                    ),
                ),
                onDismiss = { deleteTarget = null },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    conversation: Conversation,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    background: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Transparent,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .combinedClickable(
                interactionSource = interaction,
                indication = vxRipple(colors.textPrimary.copy(alpha = 0.05f)),
                onClick = onClick,
                onLongClick = onLongPress,
            )
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VxText(
                    text = conversation.title,
                    style = type.rowTitle,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (conversation.pinned) {
                    Spacer(modifier = Modifier.width(6.dp))
                    VxIcon(
                        icon = VxIcon.Pin,
                        tint = colors.accent,
                        size = 11.dp,
                        strokeWidth = 1.8.dp,
                    )
                }
            }
            if (conversation.preview.isNotEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                VxText(
                    text = conversation.preview,
                    style = type.footnote,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            VxText(
                text = formatListTime(conversation.updatedAt),
                style = type.footnote.copy(fontSize = 12.sp),
                color = colors.textTertiary,
                maxLines = 1,
            )
            // 未读数只在有值时占位，否则整个列表的行高会被撑出空洞
            if (conversation.unread > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(colors.danger),
                    contentAlignment = Alignment.Center,
                ) {
                    VxText(
                        text = if (conversation.unread > 99) "99+" else conversation.unread.toString(),
                        style = type.footnote.copy(fontSize = 10.sp),
                        color = Color.White,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * 创建角色。
 *
 * 名字 + 设定两栏就够了：设定保存成一条 `scene=chat` 的提示词，然后绑到新建的会话上。
 * 不做"角色库"这类独立概念——提示词页面就是角色库，避免两套数据各自演进。
 */
@Composable
private fun CharacterDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, persona: String) -> Unit,
) {
    val miuix = MiuixTheme.colorScheme
    val type = MiuixTheme.textStyles
    val colors = VeluneTheme.colors
    var name by remember { mutableStateOf("") }
    var persona by remember { mutableStateOf("") }

    // 进场动画：遮罩淡入 + 卡片 0.8→1 缩放，与 VxDropdownMenu / VxActionMenu 同一套节奏。
    // 之前是硬切出现，同屏其他弹层都有动画，就它没有。
    // 用 Animatable 而不是 animateFloatAsState：后者首帧直接跳到目标值，动画不会播。
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            1f,
            androidx.compose.animation.core.tween(
                durationMillis = VxMotion.DialogMs,
                easing = VxMotion.EaseOutCubic,
            ),
        )
    }
    val maskAlpha = progress.value

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 统一遮罩 token（浅色 12% 黑），之前是手写的 36% 黑，比全 App 其他弹层深两倍
            .background(colors.scrim.copy(alpha = colors.scrim.alpha * maskAlpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .graphicsLayer {
                    alpha = maskAlpha
                    scaleX = VxMotion.DialogFromScale + (1f - VxMotion.DialogFromScale) * maskAlpha
                    scaleY = VxMotion.DialogFromScale + (1f - VxMotion.DialogFromScale) * maskAlpha
                }
                .padding(24.dp)
                .fillMaxWidth()
                // 吞掉卡片内的点击，否则点输入框会穿到遮罩上把弹窗关掉
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            insideMargin = PaddingValues(20.dp),
        ) {
            Text(tr("创建角色"), style = type.title3)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = tr("给它一个名字和设定，立刻开始聊。角色只在这个会话里生效，随时可以改。"),
                style = type.footnote2,
                color = miuix.onSurfaceVariantSummary,
            )
            Spacer(modifier = Modifier.height(14.dp))
            TextField(
                value = name,
                onValueChange = { name = it },
                label = tr("角色名，如「小鹿」"),
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = persona,
                onValueChange = { persona = it },
                label = tr("设定：你是谁、怎么说话、不要做什么"),
                useLabelAsPlaceholder = true,
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    minHeight = 40.dp,
                ) {
                    Text(tr("取消"), style = type.button)
                }
                Button(
                    onClick = { onConfirm(name.trim(), persona.trim()) },
                    enabled = name.isNotBlank() && persona.isNotBlank(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                    minHeight = 40.dp,
                ) {
                    Text(tr("开始聊天"), style = type.button)
                }
            }
        }
    }
}

/** 列表时间：今天 → HH:mm；昨天 → 「昨天」；更早 → M月d日。 */
internal fun formatListTime(epochMillis: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = epochMillis }
    val now = java.util.Calendar.getInstance()
    val sameYear = cal.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR)
    val dayOfYear = cal.get(java.util.Calendar.DAY_OF_YEAR)
    val nowDay = now.get(java.util.Calendar.DAY_OF_YEAR)
    val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(cal.time)
    return when {
        sameYear && dayOfYear == nowDay -> time
        sameYear && dayOfYear == nowDay - 1 -> tr("昨天")
        sameYear -> trf("%d月%d日", cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
        else -> java.text.SimpleDateFormat("yyyy/M/d", java.util.Locale.getDefault()).format(cal.time)
    }
}

/** 空状态。列表被搜索过滤空时给出更具体的提示。 */
@Composable
private fun EmptyHint(hasSessions: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            // 底部留出浮动 tab bar 的空间，空态才不会贴着玻璃条
            .padding(bottom = 120.dp),
        contentAlignment = Alignment.Center,
    ) {
        VxEmptyState(
            icon = VxIcon.Message,
            iconSize = 40.dp,
            titleStyle = VeluneTheme.type.rowTitle,
            title = if (hasSessions) "没有匹配的会话" else "还没有会话",
            description = if (hasSessions) "换个关键词试试" else "点右上角 + 开始第一段对话",
        )
    }
}

@Composable
private fun NewChatButton(onClick: () -> Unit) {
    val colors = VeluneTheme.colors
    val interaction = remember { MutableInteractionSource() }

    // 外层 40dp 承载点击（doc 27 触控线），内层 36dp 保持原视觉尺寸
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
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.fill),
            contentAlignment = Alignment.Center,
        ) {
            VxIcon(icon = VxIcon.Plus, tint = colors.textPrimary, size = 19.dp, strokeWidth = 2.1.dp)
        }
    }
}

@Preview(widthDp = 400, heightDp = 800, showBackground = true)
@Composable
private fun MessagesPreview() {
    val demo = listOf(
        Conversation(
            id = 1,
            title = "把 PDF 表格转成 CSV",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            pinned = true,
            preview = "已读取 3 张表，合并后共 47 行",
            messageCount = 12,
        ),
        Conversation(
            id = 2,
            title = "小米设置页的设计要点",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis() - 86_400_000L,
            pinned = false,
            preview = "层级、留白、圆角三个维度",
            messageCount = 5,
        ),
    )
    VeluneTheme(darkTheme = false) {
        MessagesScreen(
            conversations = demo,
            onOpenSession = {},
            onCreateSession = {},
            onPin = {},
            onDelete = {},
        )
    }
}

/**
 * 首屏骨架。
 *
 * 数据库查询通常只有几毫秒，但冷启动那次（首次建档、升级迁移）会明显慢一拍。
 * 之前这几毫秒里显示的是「还没有会话」，用户会以为数据没了 —— 骨架比空态诚实。
 * 不做闪烁动画：几毫秒的动画只会变成一次视觉抖动。
 */
@Composable
private fun SkeletonList() {
    val colors = VeluneTheme.colors
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 6.dp)) {
        repeat(5) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colors.fill),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (index % 2 == 0) 0.42f else 0.6f)
                            .height(12.dp)
                            .clip(SquircleShape(6.dp))
                            .background(colors.fill),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (index % 2 == 0) 0.72f else 0.55f)
                            .height(10.dp)
                            .clip(SquircleShape(5.dp))
                            .background(colors.fill.copy(alpha = 0.6f)),
                    )
                }
            }
        }
    }
}

/**
 * 左滑露出「置顶 / 删除」。
 *
 * 长按菜单已经能做同样的事，但列表里最自然的动作是滑：长按要停一下等菜单弹出来，
 * 滑动是"看到就想清掉"的即时动作。两者并存，不互相替代。
 *
 * 实现要点：
 * - 用 `Animatable` 而不是 `animateFloatAsState`：拖拽期间需要直接 `snapTo`，松手再 `animateTo`，
 *   否则每帧都在触发一次动画，手指会滞后；
 * - 内容必须有**不透明底色**，否则滑动时能透出背后的按钮；
 * - 已展开时点内容先收起，不要直接进会话 —— 否则用户想关掉却进了聊天页。
 */
@Composable
private fun SwipeableSessionRow(
    conversation: Conversation,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    selecting: Boolean = false,
    selected: Boolean = false,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val actionWidth = 136.dp
    val actionPx = with(density) { actionWidth.toPx() }
    val offsetX = remember(conversation.id) { androidx.compose.animation.core.Animatable(0f) }

    // 选择模式下不接管横向手势：左滑和「点一下选中」会互相打架，
    // 而且批量操作时用户不该还在滑动单个条目
    if (selecting) {
        SessionRow(
            conversation = conversation,
            onClick = onOpen,
            onLongPress = onLongPress,
            background = if (selected) colors.accent.copy(alpha = 0.10f) else colors.canvasAlt,
        )
        return
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        // 背后的操作按钮：只在滑动露出时可见
        Row(
            modifier = Modifier
                .matchParentSize()
                .background(colors.canvasAlt),
            horizontalArrangement = Arrangement.End,
        ) {
            SwipeActionButton(
                label = if (conversation.pinned) "取消置顶" else "置顶",
                tint = colors.accent,
                width = actionWidth / 2,
                onClick = {
                    scope.launch { offsetX.animateTo(0f) }
                    onPin()
                },
            )
            SwipeActionButton(
                label = "删除",
                tint = colors.danger,
                width = actionWidth / 2,
                onClick = {
                    scope.launch { offsetX.animateTo(0f) }
                    onDelete()
                },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { androidx.compose.ui.unit.IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(conversation.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val target = if (offsetX.value < -actionPx / 2) -actionPx else 0f
                            scope.launch {
                                offsetX.animateTo(
                                    target,
                                    androidx.compose.animation.core.tween(durationMillis = 180),
                                )
                            }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo((offsetX.value + dragAmount).coerceIn(-actionPx, 0f))
                        }
                    }
                },
        ) {
            SessionRow(
                conversation = conversation,
                onClick = {
                    if (offsetX.value < -1f) {
                        scope.launch { offsetX.animateTo(0f) }
                    } else {
                        onOpen()
                    }
                },
                onLongPress = onLongPress,
                background = colors.canvasAlt,
            )
        }
    }
}

@Composable
private fun SwipeActionButton(
    label: String,
    tint: Color,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(tint.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        VxText(
            text = label,
            style = VeluneTheme.type.footnote,
            color = tint,
            maxLines = 1,
        )
    }
}
