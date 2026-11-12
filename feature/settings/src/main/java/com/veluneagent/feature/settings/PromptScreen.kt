package com.veluneagent.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.veluneagent.core.data.prompt.Prompt
import com.veluneagent.core.data.prompt.PromptComposer
import com.veluneagent.core.data.prompt.PromptScene
import com.veluneagent.core.data.prompt.PromptStore
import com.veluneagent.core.design.component.VxActionSheet
import com.veluneagent.core.design.component.VxCard
import com.veluneagent.core.design.component.VxEmptyState
import com.veluneagent.core.design.component.VxFormCard
import com.veluneagent.core.design.component.VxIcon
import com.veluneagent.core.design.component.VxRow
import com.veluneagent.core.design.component.VxSectionLabel
import com.veluneagent.core.design.component.VxSheetAction
import com.veluneagent.core.design.component.VxSnackbar
import com.veluneagent.core.design.component.VxSubPageScaffold
import com.veluneagent.core.design.l10n.tr
import com.veluneagent.core.design.l10n.trf
import com.veluneagent.core.design.theme.VxSpace
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 角色。
 *
 * 一个角色 = 一套人设。可以建多个、各自独立；标为「默认」的那个用在没有绑定角色的对话里。
 * 新建对话时可以从这里挑一个（+ 菜单 → 新建对话 → 选角色）。
 *
 * 页面组织为「列表 → 编辑」两层，用内部状态切换、不引入导航：角色数量小，
 * 两层各自只回答一个问题——列表回答"我有几个角色"，编辑回答"这个角色是谁"。
 * 之前单页塞下列表 + 按钮排 + 表单 + 预览，动线和反馈都是断的：
 * 「保存」悬在它操作的对象（表单）上方、「已保存」提示沉在页面底部屏幕外、
 * 空态里一排灰按钮让用户不知道为什么点不了。
 *
 * 细节约定（都是上一版踩过的坑）：
 * - 编辑器带脏检查（[dirty]）：带着未保存改动返回时先确认，不再静默丢内容；
 * - 「设为默认」**先保存再激活**——直接激活的是库里旧内容，用户会疑惑"我刚改的怎么没生效"；
 * - 删除走确认弹层（destructive 操作不裸奔，模式同消息页的删除会话）；
 * - 拼装预览默认收起：它是工程信息，不该把操作按钮挤出屏幕；
 * - 操作反馈（[notice]）走 [VxSnackbar]（深底浅字胶囊、自动消失）；
 * - 空态用 [VxEmptyState]，与服务商/插件/消息等处同构。
 *
 * 平台规则（工具契约、安全边界、QQ 短回复要求）仍由 [PromptComposer] 按场景追加，
 * 且永远排在角色设定之前；展开预览可以看见这个顺序。
 */
@Composable
fun PromptScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val miuix = MiuixTheme.colorScheme
    val type = MiuixTheme.textStyles
    val context = LocalContext.current.applicationContext
    val store = remember(context) { PromptStore(context) }
    val scope = rememberCoroutineScope()

    val prompts by store.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())

    // ---- 双层结构的状态。editingId == null 且 editorMode 时表示"正在新建"。
    var editorMode by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var name by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    // 脏检查基线：进编辑器时记录库里内容，保存成功后同步。
    // 比较 trim 后的值——store 保存时会 trim，不 trim 会出现"刚保存完就被判为有未保存修改"。
    var savedName by remember { mutableStateOf("") }
    var savedBody by remember { mutableStateOf("") }
    val dirty = name.trim() != savedName.trim() || body.trim() != savedBody.trim()

    var notice by remember { mutableStateOf<String?>(null) }

    // Snackbar 自动消失：文案都是「已保存」级别的一句话，2.2s 足够；
    // 连发时后一条覆盖前一条，不会堆积
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(2200)
            notice = null
        }
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var previewExpanded by remember { mutableStateOf(false) }

    /** 正在编辑的那条（库里最新值）。新建时为 null。 */
    val editing = editingId?.let { id -> prompts.firstOrNull { it.id == id } }

    fun openEditor(prompt: Prompt?) {
        editingId = prompt?.id
        name = prompt?.name.orEmpty()
        body = prompt?.body.orEmpty()
        savedName = name
        savedBody = body
        notice = null
        previewExpanded = false
        editorMode = true
    }

    fun exitEditor() {
        editorMode = false
        previewExpanded = false
    }

    /**
     * 把编辑器内容写入库。新建 / 更新共用，返回落库的 id；失败弹过提示后返回 null。
     * 「设为默认」也走这里——先落库再激活，保证激活的就是用户刚写的内容。
     */
    suspend fun persist(): Long? {
        val id = editingId
        return runCatching {
            if (id == null) {
                store.create(
                    name = name.ifBlank { "未命名" },
                    body = body,
                    // 第一条角色自动成为默认：空着没有意义
                    activate = prompts.isEmpty(),
                )
            } else {
                store.update(id, name.ifBlank { "未命名" }, body)
                id
            }
        }.fold(
            onSuccess = { saved ->
                editingId = saved
                savedName = name
                savedBody = body
                notice = if (id == null) "已创建" else "已保存"
                saved
            },
            onFailure = { error ->
                // 唯一索引的原始报错是
                // "UNIQUE constraint failed: prompts.scene, prompts.name"，
                // 直接抛给用户等于没说话
                val raw = error.message.orEmpty()
                notice = if (raw.contains("UNIQUE", ignoreCase = true)) {
                    "已有同名角色，换个名字"
                } else {
                    trf("保存失败：%s", raw.ifBlank { "未知错误" })
                }
                null
            },
        )
    }

    VxSubPageScaffold(
        // 编辑态标题告诉用户"正在改谁"，修掉上一版"表单里是谁要看运气"的问题
        title = if (editorMode) {
            if (editingId == null) "新建角色" else "编辑角色"
        } else {
            "角色"
        },
        onBack = {
            when {
                // 带着未保存的改动返回：先拦一道，不再静默丢弃
                editorMode && dirty -> showDiscardConfirm = true
                editorMode -> exitEditor()
                else -> onBack()
            }
        },
        modifier = modifier.imePadding(),
    ) {
        // Snackbar 要盖在页面内容上（BottomCenter 对齐），两层页面共用一个宿主 Box
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (!editorMode) {
                // ======== 列表层：回答"我有几个角色" ========
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = VxSpace.cardInset)
                        .padding(bottom = 32.dp),
                ) {
                    Text(
                        text = "每个角色是一套人设。新建对话时可以挑一个，标为「默认」的会用在没挑角色的对话里。",
                        style = type.footnote2,
                        color = miuix.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )

                    VxSectionLabel("我的角色")

                    VxCard {
                        if (prompts.isEmpty()) {
                            // 空态与全项目同构：VxCard + VxEmptyState（服务商/插件/消息同款）
                            VxEmptyState(
                                icon = VxIcon.Person,
                                title = "还没有角色",
                                description = "新建一个，写清「你是谁、怎么说话、不要做什么」。",
                            )
                        } else {
                            prompts.forEach { prompt ->
                                VxRow(
                                    title = prompt.name,
                                    // 列表里给「这个角色是什么样」而不是 token 数：
                                    // 用户判断要不要用这个角色，靠的是设定内容，不是工程指标
                                    subtitle = buildString {
                                        if (prompt.isActive) append(tr("默认 · "))
                                        append(
                                            prompt.body.lineSequence()
                                                .firstOrNull { it.isNotBlank() }
                                                ?.take(40)
                                                ?: tr("还没有写设定"),
                                        )
                                    },
                                    trailing = {
                                        if (prompt.isActive) {
                                            Text(tr("默认"), style = type.footnote1, color = miuix.primary)
                                        }
                                    },
                                    onClick = { openEditor(prompt) },
                                )
                            }
                        }
                    }

                    // 主操作放列表正下方：空态时它就是引导（替代上一版悬在半空的三个按钮）
                    Button(
                        onClick = { openEditor(null) },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                        minHeight = 40.dp,
                        cornerRadius = 20.dp,
                    ) {
                        Text(tr("新建角色"), style = type.button)
                    }
                }
            } else {
                // ======== 编辑层：回答"这个角色是谁" ========
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = VxSpace.cardInset)
                        .padding(bottom = 32.dp),
                ) {
                    VxSectionLabel(if (editingId == null) "新角色" else "设定")

                    VxFormCard {
                        TextField(
                            value = name,
                            onValueChange = { name = it },
                            label = tr("名称，如「温柔陪伴」"),
                            useLabelAsPlaceholder = true,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = body,
                            onValueChange = { body = it },
                            label = tr("你是……\n说话方式：……\n不要：……"),
                            useLabelAsPlaceholder = true,
                            minLines = 6,
                            maxLines = 14,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = trf("%1\$d 字 · 约 %2\$d token/轮", body.length, estimateTokens(body)),
                                style = type.footnote2,
                                color = miuix.onSurfaceVariantSummary,
                            )
                            Text(
                                text = tr("变量：{{now}} {{user_name}}"),
                                style = type.footnote2,
                                color = miuix.onSurfaceVariantSummary,
                            )
                        }
                    }

                    if (editing?.isActive == true) {
                        Text(
                            text = tr("这是当前的默认角色。"),
                            style = type.footnote2,
                            color = miuix.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }

                    Button(
                        onClick = {
                            scope.launch { persist() }
                        },
                        enabled = body.isNotBlank(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                        minHeight = 40.dp,
                        cornerRadius = 20.dp,
                    ) {
                        Text(tr("保存"), style = type.button)
                    }

                    // 仅编辑已有角色且不是默认时出现；新建时没有 id，无所谓默认
                    if (editingId != null && editing?.isActive != true) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val id = persist() ?: return@launch
                                    store.activate(id)
                                    notice = "已保存并设为默认"
                                }
                            },
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth(),
                            minHeight = 40.dp,
                            cornerRadius = 20.dp,
                        ) {
                            Text(tr("设为默认"), style = type.button)
                        }
                    }

                    if (editingId != null) {
                        Button(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.buttonColors(
                                color = miuix.errorContainer,
                                contentColor = miuix.error,
                            ),
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .fillMaxWidth(),
                            minHeight = 40.dp,
                            cornerRadius = 20.dp,
                        ) {
                            Text(tr("删除这个角色"), style = type.button)
                        }
                    }

                    // 拼装预览默认收起：用户主要来写人设，平台规则的完整拼装是进阶信息
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { previewExpanded = !previewExpanded }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = tr("拼装预览"),
                            style = type.main,
                            color = miuix.onSurfaceVariantSummary,
                        )
                        Text(
                            text = if (previewExpanded) tr("收起") else tr("展开"),
                            style = type.footnote2,
                            color = miuix.primary,
                        )
                    }

                    if (previewExpanded) {
                        VxFormCard {
                            Text(
                                // 固定用 Agent 场景预览：它是三种里规则最多的一种，
                                // 用户看到的就是「人设被压到最后」的最坏情况
                                text = PromptComposer.preview(
                                    scene = PromptScene.Agent,
                                    persona = body.ifBlank { null },
                                    qqToolsEnabled = false,
                                    userName = com.veluneagent.core.data.profile.UserProfile.nickname.value,
                                ),
                                style = type.footnote2,
                                color = miuix.onSurfaceVariantSummary,
                            )
                        }

                        Text(
                            text = tr("上面灰色部分是平台规则，不能改；你写的内容永远排在它后面——所以「不要用工具」这类话不会真的关掉工具。"),
                            style = type.footnote2,
                            color = miuix.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )

                        Text(
                            text = tr("QQ 回复会在此基础上自动追加「三句以内、不用 markdown」，这部分由平台固定。"),
                            style = type.footnote2,
                            color = miuix.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // 反馈浮层：深底浅字胶囊、自动消失（ChatScreen 同款用法）。
            // 放在 Box 尾部保证盖在内容上，且不随页面滚动
            VxSnackbar(
                message = notice.orEmpty(),
                visible = notice != null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
            )
        }
    }

    // ---- 确认弹层 ----

    if (showDeleteConfirm) {
        VxActionSheet(
            visible = true,
            title = "删除角色？",
            caption = trf("「%s」会被删除，删除后无法恢复。", editing?.name.orEmpty().ifBlank { tr("未命名") }),
            actions = listOf(
                VxSheetAction(label = "取消") { showDeleteConfirm = false },
                VxSheetAction(
                    label = "删除",
                    destructive = true,
                ) {
                    showDeleteConfirm = false
                    val id = editingId
                    if (id != null) {
                        scope.launch {
                            store.delete(id)
                            exitEditor()
                            notice = "已删除"
                        }
                    }
                },
            ),
            onDismiss = { showDeleteConfirm = false },
        )
    }

    if (showDiscardConfirm) {
        VxActionSheet(
            visible = true,
            title = "放弃修改？",
            caption = "这次的改动还没有保存。",
            actions = listOf(
                VxSheetAction(label = "继续编辑") { showDiscardConfirm = false },
                VxSheetAction(
                    label = "放弃修改",
                    destructive = true,
                ) {
                    showDiscardConfirm = false
                    exitEditor()
                },
            ),
            onDismiss = { showDiscardConfirm = false },
        )
    }
}

private fun estimateTokens(text: String): Int {
    if (text.isEmpty()) return 0
    val cjk = text.count { it in '\u4e00'..'\u9fff' }
    return cjk + maxOf(1, (text.length - cjk) / 4)
}
