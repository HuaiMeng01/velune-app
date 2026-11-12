package com.veluneagent.feature.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veluneagent.core.data.ssh.Ssh
import com.veluneagent.core.design.component.VxCard
import com.veluneagent.core.design.component.VxIcon
import com.veluneagent.core.design.component.VxPillButton
import com.veluneagent.core.design.component.VxSectionLabel
import com.veluneagent.core.design.component.VxText
import com.veluneagent.core.design.l10n.tr
import com.veluneagent.core.design.l10n.trf
import com.veluneagent.core.design.theme.VeluneTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * SSH 远程服务器配置。
 *
 * 这是「在手机上跑 Linux 命令」的替代方案：不在本机塞 rootfs，
 * 而是把命令交给用户自己的服务器执行。页面把这一点写在最上面，
 * 免得用户以为填完地址手机就变成 Linux 终端。
 */
@Composable
fun SshScreen(
    host: Ssh.Host,
    onSave: (Ssh.Host) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type
    val scope = rememberCoroutineScope()

    var addr by remember(host.host) { mutableStateOf(host.host) }
    var port by remember(host.port) { mutableStateOf(host.port.toString()) }
    var user by remember(host.user) { mutableStateOf(host.user) }
    var password by remember(host.password) { mutableStateOf(host.password) }
    var privateKey by remember(host.privateKey) { mutableStateOf(host.privateKey) }
    var name by remember(host.name) { mutableStateOf(host.name) }
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeIsError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(notice) {
        if (notice != null) {
            delay(5000)
            notice = null
        }
    }

    fun current(): Ssh.Host = Ssh.Host(
        host = addr,
        port = port.toIntOrNull() ?: 22,
        user = user,
        password = password,
        privateKey = privateKey,
        name = name,
    )

    val canSave = current().configured

    Column(modifier = modifier.fillMaxSize().background(colors.background).imePadding()) {
        SettingsTopBar(
            title = "远程服务器",
            onBack = onBack,
            action = {
                VxPillButton(
                    icon = VxIcon.Check,
                    label = "保存",
                    active = canSave,
                    onClick = {
                        if (canSave) {
                            onSave(current())
                            notice = "已保存"
                        }
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
            VxSectionLabel(text = "这是什么")

            VxCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    VxText(
                        text = "Android 应用没有 root，跑不了完整的 Linux 环境。这里改成连接你自己的服务器：Agent 执行命令时通过 SSH 在那台机器上跑。",
                        style = type.footnote,
                        color = colors.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    VxText(
                        text = "零额外包体积，命令写坏了也只影响那台机器。手机本身不会变成 Linux。",
                        style = type.footnote.copy(fontSize = 11.sp),
                        color = colors.textTertiary,
                    )
                }
            }

            VxSectionLabel(text = "连接")

            VxCard {
                FormField(
                    label = "地址",
                    value = addr,
                    placeholder = "192.168.1.10 或 example.com",
                    onValueChange = { addr = it },
                    keyboardType = KeyboardType.Uri,
                )
                FormField(
                    label = "端口",
                    value = port,
                    placeholder = "22",
                    onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
                    keyboardType = KeyboardType.Number,
                )
                FormField(
                    label = "用户名",
                    value = user,
                    placeholder = "root",
                    onValueChange = { user = it },
                )
                FormField(
                    label = "备注名",
                    value = name,
                    placeholder = "可选，例如「测试机」",
                    onValueChange = { name = it },
                )
            }

            VxSectionLabel(text = "鉴权（密码与私钥二选一）")

            VxCard {
                FormField(
                    label = "密码",
                    value = password,
                    placeholder = "不填则用下面的私钥",
                    onValueChange = { password = it },
                    visualTransformation = PasswordVisualTransformation(),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            VxCard {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    VxText(
                        text = "私钥",
                        style = type.rowTitle,
                        color = colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 88.dp)
                            .padding(vertical = 2.dp),
                    ) {
                        if (privateKey.isEmpty()) {
                            VxText(
                                text = "粘贴 OpenSSH 私钥全文（-----BEGIN OPENSSH PRIVATE KEY----- 开始）",
                                style = type.footnote.copy(fontSize = 11.sp, lineHeight = 17.sp),
                                color = colors.textTertiary,
                            )
                        }
                        BasicTextField(
                            value = privateKey,
                            onValueChange = { privateKey = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = type.footnote.copy(
                                fontSize = 11.sp,
                                lineHeight = 17.sp,
                                color = colors.textPrimary,
                            ),
                            cursorBrush = SolidColor(colors.accent),
                            singleLine = false,
                            maxLines = 10,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VxPillButton(
                    icon = VxIcon.Refresh,
                    label = if (busy) tr("连接中…") else tr("测试连接"),
                    active = canSave && !busy,
                    onClick = {
                        if (busy) return@VxPillButton
                        busy = true
                        scope.launch {
                            val result = Ssh.exec("echo velune-ok && uname -sr", current(), 12_000)
                            busy = false
                            notice = result.fold(
                                onSuccess = { r ->
                                    if (r.stdout.contains("velune-ok")) {
                                        trf("连接成功：%s", r.stdout.replace("velune-ok", "").trim())
                                    } else {
                                        trf("已连接，但输出异常：%s", r.stdout.take(80))
                                    }
                                },
                                onFailure = { trf("连接失败：%s", it.message.orEmpty()) },
                            )
                            noticeIsError = result.isFailure
                        }
                    },
                )
                VxPillButton(
                    icon = VxIcon.Trash,
                    label = tr("清除"),
                    onClick = {
                        onClear()
                        addr = ""
                        port = "22"
                        user = ""
                        password = ""
                        privateKey = ""
                        name = ""
                        notice = tr("已清除")
                        noticeIsError = false
                    },
                )
            }

            notice?.let {
                Spacer(modifier = Modifier.height(12.dp))
                VxText(
                    text = it,
                    style = type.footnote,
                    color = if (noticeIsError) colors.danger else colors.success,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            VxText(
                text = "安全：密码与私钥用 Android Keystore 加密后只存本机。当前不校验主机指纹（首次连接会直接信任），只建议连接你自己的服务器或可信内网；每条命令执行前都会请你确认。",
                style = type.footnote.copy(fontSize = 11.sp, lineHeight = 17.sp),
                color = colors.textTertiary,
            )
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
