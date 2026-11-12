package com.veluneagent.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.veluneagent.core.data.im.QqBindController
import com.veluneagent.core.design.component.VxIcon
import com.veluneagent.core.design.l10n.tr
import com.veluneagent.core.design.l10n.trf
import com.veluneagent.core.design.qr.QrImage
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 二维码边长。主区高度与它绑定，见 [StageHeight]。 */
private val QrSize: Dp = 220.dp

/**
 * 主区固定高度 = 二维码 + 上下各 12dp 白边。
 *
 * **固定高度是刻意的**：`Creating`（拉任务）与 `Waiting`（有码）之间来回切换时，
 * 变高会让下方按钮上下跳动，用户正要点「取消」的瞬间会点错。空态也占同样的位置。
 */
private val StageHeight: Dp = QrSize + 24.dp

/**
 * 扫码绑定的界面：把 [QqBindController] 的五种状态映射成「二维码 / 提示 / 按钮」。
 *
 * 为什么 `Done` 不自动关闭：用户需要先**看见** AppID 已经填好、密钥已保存，
 * 再自己点「完成」。自动关掉的话，扫码到底有没有生效就只能靠猜——
 * 而这正是手工填写最让人不放心的地方，改成扫码就是为了消除它。
 *
 * 密钥**永远不显示原文**，只显示长度：屏幕被旁人看到、截图外流都算密钥泄漏，
 * 而「已保存（N 位）」足以让用户确认"东西拿到了"。
 *
 * 组件本身不依赖外层容器（Sheet/Card 由调用方给），方便下一段接到 `QqBotScreen`。
 */
@Composable
fun QqBindSheet(
    controller: QqBindController,
    onCancel: () -> Unit,
    onBound: (appId: String, appSecret: String, ownerOpenId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val miuix = MiuixTheme.colorScheme
    val type = MiuixTheme.textStyles
    val state by controller.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = tr("扫码绑定机器人"),
            style = type.title3,
            color = miuix.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitleOf(state),
            style = type.footnote1,
            color = if (state is QqBindController.State.Failed) {
                miuix.error
            } else {
                miuix.onSurfaceVariantSummary
            },
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier.height(StageHeight),
            contentAlignment = Alignment.Center,
        ) {
            when (val s = state) {
                is QqBindController.State.Waiting -> QrImage(content = s.qrContent)
                // 占位方块：与二维码同尺寸同圆角，避免生成期间布局抖动
                is QqBindController.State.Creating -> Box(
                    modifier = Modifier
                        .size(QrSize)
                        .clip(RoundedCornerShape(12.dp))
                        .background(miuix.secondaryContainer),
                )
                is QqBindController.State.Done -> DoneSummary(s)
                // 原因已经写在副文案里（见表格「副文案 = state.reason」），主区只放图形，
                // 否则同一句话在同一块区域里出现两遍
                is QqBindController.State.Failed -> VxIcon(
                    icon = VxIcon.Warning,
                    tint = miuix.error,
                    size = 44.dp,
                    strokeWidth = 2.dp,
                )
                QqBindController.State.Idle -> Unit
            }
        }

        Spacer(Modifier.height(18.dp))
        Actions(
            state = state,
            controller = controller,
            onCancel = onCancel,
            onBound = onBound,
        )
    }
}

/** 副文案：一行说清"现在该干什么"。表里规定 Failed 用 `state.reason`。tr 在组合内读状态，切语言会重算。 */
private fun subtitleOf(state: QqBindController.State): String = when (state) {
    QqBindController.State.Idle -> tr("在 QQ 里扫一下，自动填好 AppID 和密钥")
    QqBindController.State.Creating -> tr("正在生成二维码…")
    is QqBindController.State.Waiting -> buildString {
        append(tr("用 QQ 扫这个码确认绑定 · "))
        append(state.secondsLeft.coerceAtLeast(0))
        append(tr("s 后失效"))
        // 换过码要写出来，否则用户会以为"刚才扫的那张怎么又变了"
        if (state.attempt > 1) append(trf("（第 %d 次）", state.attempt))
    }
    is QqBindController.State.Done -> tr("密钥已加密存到本机，不会再显示")
    // 字段名是 message（QqBindController.State.Failed.val message），不是任务书里写的 reason
    is QqBindController.State.Failed -> state.message
}

/** 成功摘要：三行文字，密钥只给长度。 */
@Composable
private fun DoneSummary(state: QqBindController.State.Done) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoLine(label = tr("AppID"), value = state.appId)
        InfoLine(label = tr("密钥"), value = trf("已保存（%d 位）", state.appSecret.length))
        InfoLine(label = tr("绑定者"), value = state.ownerOpenId)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val miuix = MiuixTheme.colorScheme
    val type = MiuixTheme.textStyles
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = type.footnote1, color = miuix.onSurfaceVariantSummary)
        Spacer(Modifier.width(10.dp))
        Text(
            text = value,
            style = type.footnote1,
            color = miuix.onSurface,
            maxLines = 1,
        )
    }
}

/**
 * 按钮区。五个状态各一套，**不做"聪明"的合并**：
 * 每个状态下用户能做的动作本来就不一样，按钮文案必须直白。
 */
@Composable
private fun Actions(
    state: QqBindController.State,
    controller: QqBindController,
    onCancel: () -> Unit,
    onBound: (appId: String, appSecret: String, ownerOpenId: String) -> Unit,
) {
    when (state) {
        QqBindController.State.Idle -> PrimaryAction(tr("开始绑定")) { controller.start() }

        // 生成中禁用：连点会创建多个绑定任务，最后 poll 的是哪一个都不确定
        QqBindController.State.Creating -> PrimaryAction(tr("开始绑定"), enabled = false) { }

        is QqBindController.State.Waiting -> SecondaryAction(tr("取消")) {
            controller.cancel()
            onCancel()
        }

        is QqBindController.State.Done -> PrimaryAction(tr("完成")) {
            onBound(state.appId, state.appSecret, state.ownerOpenId)
        }

        is QqBindController.State.Failed -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryAction(
                label = tr("重试"),
                modifier = Modifier.weight(1f),
                onClick = { controller.start() },
            )
            SecondaryAction(
                label = tr("改用手工填写"),
                modifier = Modifier.weight(1f),
                onClick = onCancel,
            )
        }
    }
}

@Composable
private fun PrimaryAction(
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val type = MiuixTheme.textStyles
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColorsPrimary(),
    ) {
        Text(text = label, style = type.button, maxLines = 1)
    }
}

@Composable
private fun SecondaryAction(
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onClick: () -> Unit,
) {
    val type = MiuixTheme.textStyles
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(),
    ) {
        Text(text = label, style = type.button, maxLines = 1)
    }
}
