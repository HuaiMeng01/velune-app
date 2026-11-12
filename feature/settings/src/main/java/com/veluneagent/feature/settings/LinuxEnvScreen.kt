package com.veluneagent.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.veluneagent.core.data.linux.LinuxEnv
import com.veluneagent.core.design.component.VxFormCard
import com.veluneagent.core.design.component.VxSectionLabel
import com.veluneagent.core.design.component.VxSubPageScaffold
import com.veluneagent.core.data.linux.LinuxRuntime
import com.veluneagent.core.design.l10n.tr
import com.veluneagent.core.design.l10n.trf
import com.veluneagent.core.design.theme.VxSpace
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Linux 环境（PRoot Ubuntu）。
 *
 * 下载是**用户主动触发**的：rootfs 解压后几百 MB，不能默认让所有人付这个体积。
 * 页面必须同时暴露两件事：
 * 1. rootfs 是否就绪；
 * 2. proot 运行时是否存在 —— 缺了它即使 rootfs 装好也跑不起来，只显示"已安装"是骗人。
 */
@Composable
fun LinuxEnvScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val miuix = MiuixTheme.colorScheme
    val type = MiuixTheme.textStyles
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    val state by LinuxEnv.state.collectAsStateWithLifecycle()
    val progress by LinuxEnv.progress.collectAsStateWithLifecycle()
    val message by LinuxEnv.message.collectAsStateWithLifecycle()
    val runtimeReady = LinuxRuntime.isBinaryAvailable(context)

    VxSubPageScaffold(
        title = "Linux 环境",
        onBack = onBack,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // 与主设置页同一基准：卡片外距 12dp，块内 16dp，文字左缘落在 28dp
                .padding(horizontal = VxSpace.cardInset)
                .padding(bottom = 32.dp),
        ) {
            VxSectionLabel("状态")

            VxFormCard {
                Text(
                text = when (state) {
                    LinuxEnv.State.NotInstalled -> tr("未安装")
                    LinuxEnv.State.Downloading -> trf("正在下载 %d%%", (progress * 100).toInt())
                    LinuxEnv.State.Extracting -> trf("正在解压 %d%%", (progress * 100).toInt())
                    LinuxEnv.State.Verifying -> tr("已就位，正在验证 proot 能否启动…")
                    LinuxEnv.State.Ready -> tr("已就绪")
                    LinuxEnv.State.Failed -> tr("安装失败")
                },
                    style = type.title3,
                    color = miuix.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = trf("Ubuntu 24.04 base（arm64）· 下载约 %dMB · 解压后约 300MB", LinuxEnv.ApproxDownloadMb),
                    style = type.footnote2,
                    color = miuix.onSurfaceVariantSummary,
                )

                if (state == LinuxEnv.State.Downloading || state == LinuxEnv.State.Extracting) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                message?.let {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = it, style = type.footnote2, color = miuix.error)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                when (state) {
                    LinuxEnv.State.Ready -> Button(
                        onClick = { scope.launch { LinuxEnv.remove() } },
                        colors = ButtonDefaults.buttonColors(
                            color = miuix.errorContainer,
                            contentColor = miuix.error,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        minHeight = 42.dp,
                    ) {
                        Text(tr("删除已下载的环境"), style = type.button)
                    }

                    LinuxEnv.State.Downloading, LinuxEnv.State.Extracting, LinuxEnv.State.Verifying -> Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        minHeight = 42.dp,
                    ) {
                        Text(tr("安装中…"), style = type.button)
                    }

                    else -> Button(
                        onClick = { scope.launch { LinuxEnv.install() } },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.fillMaxWidth(),
                        minHeight = 42.dp,
                    ) {
                        Text(tr("下载并安装"), style = type.button)
                    }
                }
            }

            VxSectionLabel("运行时")

            VxFormCard {
                Text(
                    text = if (runtimeReady) tr("proot 已就位，可以直接运行代码") else tr("缺少 proot 运行时"),
                    style = type.title3,
                    color = if (runtimeReady) miuix.onSurface else miuix.error,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (runtimeReady) {
                        tr("Agent 的 code_run 在 where=local 时会走这里；也可以用 apt 装需要的软件包。")
                    } else {
                        tr("rootfs 与 proot 是两件事：前者是可下载的用户空间，后者是可执行文件。") +
                            tr("Android 10+ 不允许执行应用私有目录里的文件，proot 必须随安装包以 lib*.so 形式提供。")
                    },
                    style = type.footnote2,
                    color = miuix.onSurfaceVariantSummary,
                )
            }

            // 诊断：状态卡可能显示"失败"但 rootfs 其实完好（或反之），
            // 与其让用户对着截断的红字猜，不如直接真跑一条命令看完整输出。
            // exitCode/stdout/stderr 全部上屏 —— 这是唯一能定位 proot 层问题的入口。
            if (state == LinuxEnv.State.Ready || state == LinuxEnv.State.Failed) {
                var diagRunning by remember { mutableStateOf(false) }
                var diagOutput by remember { mutableStateOf<String?>(null) }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        diagRunning = true
                        diagOutput = null
                        scope.launch {
                            val sb = StringBuilder()
                            // 对照实验：不经 proot 直接 exec 系统命令。
                            // 失败 → 应用 exec 能力被系统策略拦截（与 proot 无关，机型/系统问题）；
                            // 成功 → 问题在 proot 侧，看下一条的异常原文。
                            try {
                                val p = ProcessBuilder("/system/bin/sh", "-c", "id")
                                    .redirectErrorStream(true).start()
                                val done = p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
                                sb.appendLine("[对照] 直接 exec /system/bin/sh：exit=${if (done) p.exitValue().toString() else "超时"}")
                                sb.appendLine("　输出：${p.inputStream.bufferedReader().readText().trim().take(200)}")
                            } catch (e: Exception) {
                                sb.appendLine("[对照] 直接 exec /system/bin/sh：启动失败 → ${e.javaClass.simpleName}: ${e.message}")
                            }
                            sb.appendLine()
                            val result = LinuxRuntime.run(
                                context,
                                "echo __SMOKE_OK__ && uname -m && head -2 /etc/os-release",
                                timeoutMs = 30_000,
                            )
                            val r = result.getOrNull()
                            if (r == null) {
                                val ex = result.exceptionOrNull()
                                sb.appendLine("[proot] 进程启动失败 → ${ex?.javaClass?.simpleName}: ${ex?.message}")
                                ex?.cause?.let { sb.appendLine("　cause: ${it.javaClass.simpleName}: ${it.message}") }
                            } else {
                                sb.appendLine("[proot] exitCode=${r.exitCode} 耗时=${r.durationMs}ms" + if (r.timedOut) "（超时被杀）" else "")
                                if (r.stdout.isNotBlank()) sb.appendLine("　stdout：${r.stdout.trim().take(300)}")
                                if (r.stderr.isNotBlank()) sb.appendLine("　stderr：${r.stderr.trim().take(300)}")
                                if (r.stdout.isBlank() && r.stderr.isBlank()) sb.appendLine("　（无输出）")
                            }
                            diagOutput = sb.toString()
                            diagRunning = false
                        }
                    },
                    enabled = !diagRunning,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    minHeight = 42.dp,
                ) {
                    Text(if (diagRunning) tr("正在运行诊断…") else tr("运行诊断命令"), style = type.button)
                }
                diagOutput?.let {
                    Text(
                        text = it,
                        style = type.footnote2,
                        color = miuix.onSurfaceVariantSummary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            VxSectionLabel("说明")

            Text(
                text = tr("这是用户态模拟（ptrace 拦截系统调用），不是容器：没有内核级隔离，I/O 比原生慢，") +
                    tr("需要内核模块的程序跑不起来。适合跑脚本、验算、看项目；不要把它当服务器用。"),
                style = type.footnote2,
                color = miuix.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}
