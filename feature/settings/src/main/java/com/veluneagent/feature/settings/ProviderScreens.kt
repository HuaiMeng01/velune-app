package com.veluneagent.feature.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veluneagent.core.data.ProbeResult
import com.veluneagent.core.data.ProviderPresets
import com.veluneagent.core.data.Provider
import com.veluneagent.core.design.component.VxActionSheet
import com.veluneagent.core.design.component.VxCard
import com.veluneagent.core.design.component.VxEmptyState
import com.veluneagent.core.design.component.VxIcon
import com.veluneagent.core.design.component.VxIconButton
import com.veluneagent.core.design.component.VxPillButton
import com.veluneagent.core.design.component.VxRow
import com.veluneagent.core.design.component.VxSectionLabel
import com.veluneagent.core.design.component.VxSheetAction
import com.veluneagent.core.design.component.VxSubPageBar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.extended.Back
import com.veluneagent.core.design.component.VxText
import com.veluneagent.core.design.shape.SquircleCapsule
import com.veluneagent.core.design.shape.SquircleShape
import com.veluneagent.core.data.ModelRouting
import com.veluneagent.core.design.theme.VxIconPalette
import com.veluneagent.core.design.l10n.tr
import com.veluneagent.core.design.l10n.trf
import com.veluneagent.core.design.theme.VeluneTheme
import com.veluneagent.core.design.theme.VxRadius

/** 服务商列表：一眼看到每个服务的连通状态。 */
@Composable
fun ProviderListScreen(
    providers: List<Provider>,
    probeResults: Map<String, ProbeResult>,
    onAdd: () -> Unit,
    onEdit: (Provider) -> Unit,
    onProbe: (Provider) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    activeId: String? = null,
    onSetActive: (Provider) -> Unit = {},
    onDelete: (Provider) -> Unit = {},
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type
    var actionTarget by remember { mutableStateOf<Provider?>(null) }
    // 分工只在本页修改，用普通状态即可；每次改完同步一次最新值
    var routing by remember { mutableStateOf(ModelRouting.assignments.value) }
    var routingTarget by remember { mutableStateOf<ModelRouting.Role?>(null) }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        SettingsTopBar(title = "模型与密钥", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            VxSectionLabel(text = "模型分工")

            VxCard {
                VxRow(
                    title = "对话与 Agent",
                    subtitle = "主模型，也就是下面标记「使用中」的那个",
                    icon = VxIcon.Model,
                    iconTint = colors.accent,
                    valueText = providers.firstOrNull { it.id == activeId }
                        ?.let { "${it.name} · ${it.model.ifBlank { "未填模型" }}" }
                        ?: "未选择",
                    // 主模型跟随「使用中」，不单独设：两个入口改同一件事只会打架
                    onClick = {},
                )
                VxRow(
                    title = "上下文摘要",
                    subtitle = "压缩历史时用，建议选便宜的",
                    icon = VxIcon.Doc,
                    iconTint = VxIconPalette.Plugin.color(),
                    valueText = routingLabel(routing, providers, ModelRouting.Role.Summary),
                    onClick = { routingTarget = ModelRouting.Role.Summary },
                )
                VxRow(
                    title = "子任务模型",
                    subtitle = "Agent 用 delegate_task 派发的子任务",
                    icon = VxIcon.Sparkles,
                    iconTint = VxIconPalette.Agent.color(),
                    valueText = routingLabel(routing, providers, ModelRouting.Role.Sub),
                    onClick = { routingTarget = ModelRouting.Role.Sub },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            VxSectionLabel(text = "已配置")

            if (providers.isEmpty()) {
                VxCard {
                    VxEmptyState(
                        icon = VxIcon.Key,
                        title = "还没有服务商",
                        description = "点下方「添加服务商」，填入 Base URL、模型与密钥",
                    )
                }
            } else {
                VxCard {
                    providers.forEach { provider ->
                        ProviderRow(
                            provider = provider,
                            result = probeResults[provider.id],
                            inUse = provider.id == activeId ||
                                (activeId == null && provider.id == providers.first().id),
                            onEdit = { onEdit(provider) },
                            onProbe = { onProbe(provider) },
                            onLongPress = { actionTarget = provider },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            VxCard {
                VxRow(
                    title = "添加服务商",
                    icon = VxIcon.Plus,
                    iconTint = colors.accent,
                    onClick = onAdd,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            VxText(
                text = "密钥经 Android Keystore 加密后仅存于本机，不会上传、不参与备份。长按可删除或设为默认。",
                style = type.footnote,
                color = colors.textTertiary,
            )
            Spacer(modifier = Modifier.height(40.dp))
        }

        val target = actionTarget
        if (target != null) {
            VxActionSheet(
                visible = true,
                title = target.name.ifEmpty { "未命名服务商" },
                caption = target.baseUrl,
                actions = buildList {
                    if (target.id != activeId) {
                        add(
                            VxSheetAction(
                                label = "设为默认",
                                icon = VxIcon.Check,
                                onClick = {
                                    actionTarget = null
                                    onSetActive(target)
                                },
                            ),
                        )
                    }
                    add(
                        VxSheetAction(
                            label = "删除服务商",
                            icon = VxIcon.Trash,
                            destructive = true,
                            onClick = {
                                actionTarget = null
                                onDelete(target)
                            },
                        ),
                    )
                },
                onDismiss = { actionTarget = null },
            )
        }

        val role = routingTarget
        if (role != null) {
            VxActionSheet(
                visible = true,
                title = if (role == ModelRouting.Role.Summary) "摘要用哪个模型" else "子任务用哪个模型",
                caption = "这两个角色不需要最强模型，选便宜的能明显省钱；留空则跟随主模型。",
                actions = buildList {
                    add(
                        VxSheetAction(
                            label = "跟随主模型",
                            icon = VxIcon.Model,
                            onClick = {
                                ModelRouting.assign(role, null)
                                routing = ModelRouting.assignments.value
                                routingTarget = null
                            },
                        ),
                    )
                    providers.forEach { candidate ->
                        add(
                            VxSheetAction(
                                label = "${candidate.name} · ${candidate.model.ifBlank { tr("未填模型") }}",
                                onClick = {
                                    ModelRouting.assign(role, candidate.id)
                                    routing = ModelRouting.assignments.value
                                    routingTarget = null
                                },
                            ),
                        )
                    }
                },
                onDismiss = { routingTarget = null },
            )
        }
    }
}

/** 「跟随主模型」还是具体某个服务商，右侧统一用这一行文案。 */
private fun routingLabel(
    assignments: Map<ModelRouting.Role, String>,
    providers: List<Provider>,
    role: ModelRouting.Role,
): String {
    val bound = assignments[role] ?: return "跟随主模型"
    val provider = providers.firstOrNull { it.id == bound } ?: return "跟随主模型"
    return "${provider.name} · ${provider.model.ifBlank { "未填模型" }}"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProviderRow(
    provider: Provider,
    result: ProbeResult?,
    inUse: Boolean,
    onEdit: () -> Unit,
    onProbe: () -> Unit,
    onLongPress: () -> Unit,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onEdit, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VxText(
                    text = provider.name.ifEmpty { "未命名" },
                    style = type.rowTitle,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (inUse) {
                    Spacer(modifier = Modifier.width(6.dp))
                    VxText(
                        text = "使用中",
                        style = type.footnote.copy(fontSize = 11.sp),
                        color = colors.accent,
                    )
                }
                if (!provider.hasKey) {
                    Spacer(modifier = Modifier.width(6.dp))
                    VxText(
                        text = "缺密钥",
                        style = type.footnote.copy(fontSize = 11.sp),
                        color = colors.danger,
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            VxText(
                text = buildString {
                    append(provider.baseUrl)
                    if (provider.model.isNotBlank()) append(" · ${provider.model}")
                },
                style = type.footnote,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val status = when (result) {
                null -> null
                is ProbeResult.Success -> trf("连接正常 · %d 个模型", result.modelCount)
                is ProbeResult.HttpError -> "HTTP ${result.code}"
                is ProbeResult.NetworkError -> result.message
            }
            if (status != null) {
                Spacer(modifier = Modifier.height(4.dp))
                VxText(
                    text = status,
                    style = type.footnote.copy(fontSize = 11.sp),
                    color = when (result) {
                        is ProbeResult.Success -> colors.success
                        else -> colors.danger
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))
        VxPillButton(
            icon = VxIcon.Refresh,
            label = "测试",
            onClick = onProbe,
        )
    }
}

/** 新增 / 编辑服务商。 */
@Composable
fun ProviderEditScreen(
    existing: Provider?,
    onSave: (name: String, baseUrl: String, model: String, apiKey: String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var baseUrl by remember { mutableStateOf(existing?.baseUrl ?: "") }
    var model by remember { mutableStateOf(existing?.model ?: "") }
    var apiKey by remember { mutableStateOf("") }
    // 编辑已有配置时，按 Base URL 反推预设，让用户能直接换模型
    var preset by remember {
        mutableStateOf(existing?.baseUrl?.let { ProviderPresets.byBaseUrl(it) })
    }

    // 模型名必填：留空请求只会换回一个 400，不如在保存时挡住
    val canSave = name.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank() &&
        (existing != null || apiKey.isNotBlank())

    Column(modifier = modifier.fillMaxSize().background(colors.background).imePadding()) {
        SettingsTopBar(
            title = if (existing == null) "添加服务商" else "编辑服务商",
            onBack = onBack,
            action = {
                VxPillButton(
                    icon = VxIcon.Check,
                    label = "保存",
                    active = canSave,
                    onClick = {
                        if (canSave) onSave(name, baseUrl, model, apiKey.takeIf { it.isNotBlank() })
                    },
                )
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            VxSectionLabel(text = "快速配置")

            VxCard {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    ProviderPresets.all.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            row.forEach { item ->
                                PresetChip(
                                    label = item.name,
                                    selected = preset?.id == item.id,
                                    onClick = {
                                        // 切换服务商时清空模型：把 glm-5.3 发到 DeepSeek 只会得到 400
                                        if (preset?.id != item.id) model = ""
                                        preset = item
                                        name = item.name
                                        baseUrl = item.baseUrl
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(2 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                    preset?.let { item ->
                        VxText(
                            text = item.note,
                            style = type.footnote.copy(fontSize = 11.sp),
                            color = colors.textTertiary,
                        )
                    }
                }
            }

            VxSectionLabel(text = "接入信息")

            VxCard {
                FormField(
                    label = "名称",
                    value = name,
                    placeholder = "DeepSeek / 自建网关",
                    onValueChange = { name = it },
                )
                FormField(
                    label = "Base URL",
                    value = baseUrl,
                    placeholder = "https://api.example.com/v1",
                    onValueChange = { baseUrl = it },
                    keyboardType = KeyboardType.Uri,
                )
                FormField(
                    label = "模型",
                    value = model,
                    placeholder = preset?.modelHint ?: "服务商文档里的 model 参数",
                    onValueChange = { model = it },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VxText(
                        text = if (model.isBlank()) {
                            "必填：填服务商文档请求示例里的 model 值"
                        } else {
                            preset?.let { trf("示例：%s", it.modelHint) }
                                ?: "模型名按服务商文档填写，换模型只需改这一项"
                        },
                        style = type.footnote.copy(fontSize = 11.sp),
                        color = if (model.isBlank()) colors.warning else colors.textTertiary,
                        modifier = Modifier.weight(1f),
                    )
                    // 只给一个可改的示例值，不是候选清单：省掉首次接入的一次查文档
                    if (model.isBlank() && preset != null) {
                        VxText(
                            text = "填入示例",
                            style = type.footnote.copy(fontSize = 11.sp),
                            color = colors.accent,
                            modifier = Modifier
                                .clip(SquircleCapsule())
                                .clickable { model = preset?.modelHint.orEmpty() }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                VxText(
                    text = preset?.let { trf("当前可用模型以 %s 为准；应用不内置清单，模型迭代比发版快", it.docsUrl) }
                        ?: "应用不内置模型清单：模型迭代比发版快，硬编码的列表迟早全部失效",
                    style = type.footnote.copy(fontSize = 11.sp),
                    color = colors.textTertiary,
                )
            }

            VxSectionLabel(text = "API Key")

            VxCard {
                FormField(
                    label = "密钥",
                    value = apiKey,
                    placeholder = if (existing?.hasKey == true) "已保存，留空则不修改" else "sk-...",
                    onValueChange = { apiKey = it },
                    visualTransformation = PasswordVisualTransformation(),
                )
            }

            // 明文 HTTP 会把 Bearer Token 暴露在链路上，只对 localhost 放行
            val insecure = baseUrl.trim().startsWith("http://") &&
                !baseUrl.contains("localhost") && !baseUrl.contains("127.0.0.1")
            if (insecure) {
                Spacer(modifier = Modifier.height(10.dp))
                VxText(
                    text = "当前地址使用 http://，API Key 会以明文经过网络。请改用 https://，或只对本地服务使用 http。",
                    style = type.footnote,
                    color = colors.warning,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            VxText(
                text = "密钥保存后将以密文形式落盘，界面不再回显完整内容。",
                style = type.footnote,
                color = colors.textTertiary,
            )
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
internal fun FormField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) = com.veluneagent.core.design.component.VxFormField(
    label = label,
    value = value,
    onValueChange = onValueChange,
    placeholder = placeholder,
    keyboardType = keyboardType,
    visualTransformation = visualTransformation,
    trailing = trailing,
)

/** 预设选择芯片：选中态用主色描边，与压缩阈值面板保持一致的视觉语言。 */
@Composable
private fun PresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type
    Box(
        modifier = modifier
            .heightIn(min = 38.dp)
            .clip(SquircleCapsule())
            .background(if (selected) colors.accent.copy(alpha = 0.12f) else colors.fill)
            .border(
                width = 1.dp,
                color = if (selected) colors.accent.copy(alpha = 0.5f) else Color.Transparent,
                shape = SquircleCapsule(),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        VxText(
            text = label,
            style = type.footnote.copy(fontSize = 12.sp),
            color = if (selected) colors.accent else colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 子页顶栏（设置模块的统一入口）：左返回 + 居中标题 + 右侧可选操作。
 *
 * 统一转调 [VxSubPageBar]，与 `VxSubPageScaffold` 的顶栏是同一个实现：
 * 56dp 高、居中 17sp Medium 标题、40dp 返回热区。设置模块所有子页共用这一条规格。
 */
@Composable
fun SettingsTopBar(
    title: String,
    onBack: () -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    VxSubPageBar(title = title, onBack = onBack) { action?.invoke() }
}
