package com.veluneagent.feature.chat.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veluneagent.core.design.component.VxIcon
import com.veluneagent.core.design.l10n.tr
import com.veluneagent.core.design.l10n.trf
import com.veluneagent.core.design.component.VxText
import com.veluneagent.core.design.component.vxRipple
import com.veluneagent.core.design.theme.VeluneTheme
import kotlinx.coroutines.delay
import java.io.File

/**
 * 工具卡里的音频播放器。
 *
 * 为什么不用 Intent 跳系统播放器：语音是对话的一部分，跳出去就等于把用户从会话里赶走，
 * 回来还要重新找位置。这里就地播放，进度条可点跳转。
 *
 * 几个必须处理的边界：
 * - `prepareAsync` 未完成时点播放不能崩（`isPlaying` 在未准备状态下会抛 IllegalStateException）；
 * - 播放中离开页面要 `release()`，否则 MediaPlayer 会一直占着音频焦点；
 * - 文件不存在（沙箱被清空）时直接给一行提示，不要留一个永远转圈的按钮。
 */
@Composable
internal fun AudioPlayerBar(
    file: File,
    modifier: Modifier = Modifier,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type

    var playing by remember(file.path) { mutableStateOf(false) }
    var prepared by remember(file.path) { mutableStateOf(false) }
    var position by remember(file.path) { mutableLongStateOf(0L) }
    var duration by remember(file.path) { mutableLongStateOf(0L) }
    var failure by remember(file.path) { mutableStateOf<String?>(null) }
    var barWidth by remember { mutableFloatStateOf(1f) }

    val missing = !file.exists()

    val player = remember(file.path) { MediaPlayer() }
    DisposableEffect(file.path) {
        if (!missing) {
            runCatching {
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                player.setDataSource(file.absolutePath)
                player.setOnPreparedListener { mp ->
                    duration = mp.duration.coerceAtLeast(0).toLong()
                    prepared = true
                }
                player.setOnCompletionListener { mp ->
                    playing = false
                    position = 0L
                    runCatching { mp.seekTo(0) }
                }
                player.setOnErrorListener { _, what, extra ->
                    failure = trf("播放失败（%s/%s）", what.toString(), extra.toString())
                    playing = false
                    true
                }
                player.prepareAsync()
            }.onFailure { failure = it.message ?: "音频无法打开" }
        }
        onDispose {
            runCatching { player.release() }
        }
    }

    // 只在播放时轮询进度，暂停就停表，别让 200ms 的定时器一直跑
    LaunchedEffect(playing) {
        while (playing) {
            position = runCatching { player.currentPosition.toLong() }.getOrDefault(position)
            delay(200)
        }
    }

    val message = when {
        missing -> "音频文件已不在沙箱里"
        failure != null -> failure
        else -> null
    }

    if (message != null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(colors.fill.copy(alpha = 0.6f))
                .padding(horizontal = 10.dp, vertical = 7.dp),
        ) {
            VxText(
                text = message,
                style = type.footnote.copy(fontSize = 11.sp),
                color = colors.textTertiary,
            )
        }
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(colors.fill.copy(alpha = 0.6f))
            .padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(colors.accent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = vxRipple(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.2f)),
                    onClick = {
                        if (!prepared) return@clickable
                        runCatching {
                            if (player.isPlaying) {
                                player.pause()
                                playing = false
                            } else {
                                player.start()
                                playing = true
                            }
                        }.onFailure { failure = it.message ?: "播放失败" }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            VxIcon(
                icon = if (playing) VxIcon.Pause else VxIcon.Play,
                tint = androidx.compose.ui.graphics.Color.White,
                size = 13.dp,
                strokeWidth = 2.dp,
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .onSizeChanged { barWidth = it.width.toFloat().coerceAtLeast(1f) }
                    .pointerInput(duration) {
                        detectTapGestures { offset ->
                            if (duration <= 0L || !prepared) return@detectTapGestures
                            val ratio = (offset.x / barWidth).coerceIn(0f, 1f)
                            runCatching { player.seekTo((duration * ratio).toInt()) }
                            position = (duration * ratio).toLong()
                        }
                    }
                    .clip(CircleShape)
                    .background(colors.textTertiary.copy(alpha = 0.25f)),
            ) {
                val fraction = if (duration > 0L) {
                    (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                VxText(
                    text = formatClock(position),
                    style = type.footnote.copy(fontSize = 10.sp),
                    color = colors.textTertiary,
                )
                Spacer(modifier = Modifier.width(4.dp))
                VxText(
                    text = "/ ${formatClock(duration)}",
                    style = type.footnote.copy(fontSize = 10.sp),
                    color = colors.textTertiary,
                )
            }
        }
    }
}

private fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val minutes = total / 60
    val seconds = total % 60
    return "%d:%02d".format(minutes, seconds)
}
