package com.veluneagent.core.data.linux

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 在 PRoot Ubuntu 里执行命令。
 *
 * 关键实现约束（Android 10+ 的 W^X 策略）：
 * 应用**不能** exec 私有目录里的可执行文件，但可以 exec `nativeLibraryDir` 下的 `.so`。
 * 所以 proot 必须以 `libproot.so` 的名字打进 jniLibs；而它依赖的 `libtalloc.so.2`
 * 这类带版本号的库不能直接进 jniLibs（Gradle 只打包 `lib*.so`），
 * 于是放在 `files/linux/lib` 并通过 `LD_LIBRARY_PATH` 加载 —— 加载不受 W^X 限制。
 *
 * 若两者都缺，直接给出"未就绪"的明确错误，不做任何隐式降级：
 * 静默回退到别的执行方式会让用户以为跑的是本地环境。
 */
object LinuxRuntime {

    data class ExecResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val durationMs: Long,
        val timedOut: Boolean = false,
    )

    /** proot 可执行文件：优先 jniLibs（可 exec），其次用户自己放进 files/linux/bin 的。 */
    fun prootBinary(context: Context): File? {
        val fromLibs = File(context.applicationInfo.nativeLibraryDir, "libproot.so")
        if (fromLibs.isFile) return fromLibs
        val manual = File(context.filesDir, "linux/bin/proot")
        if (manual.isFile) return manual
        return null
    }

    fun isBinaryAvailable(context: Context): Boolean = prootBinary(context) != null

    fun libDir(context: Context): File = LinuxEnv.libDir(context)

    suspend fun run(
        context: Context,
        command: String,
        timeoutMs: Long = 60_000,
    ): Result<ExecResult> = withContext(Dispatchers.IO) {
        val proot = prootBinary(context)
            ?: return@withContext Result.failure(
                IllegalStateException("本地 Linux 环境缺少 proot 运行时（未随包提供，也无法从私有目录执行）"),
            )
        val rootfs = LinuxEnv.rootfsDir(context)
        // 就绪检查基于**文件事实**而不是 LinuxEnv.isReady()（状态机）：
        // ① install() 的冒烟验证把状态设为 Verifying，用 isReady() 判会把自己的产物否决掉
        //   （自锁：验证需要 Ready，而 Ready 要等验证通过）；
        // ② code_run 也不应依赖 UI 状态的时序 —— rootfs 里的 /bin/sh 存在即可跑。
        if (!rootfs.resolve("bin/sh").isFile) {
            return@withContext Result.failure(
                IllegalStateException("Ubuntu 根文件系统尚未安装（或缺少 /bin/sh）"),
            )
        }
        val home = LinuxEnv.homeDir(context)
        val tmp = LinuxEnv.tmpDir(context)

        // ⚠️ 用 `-c` 而不是 `-lc`（非 login shell）：login 会去读 /etc/profile → ~/.profile →
        // ~/.bashrc，Ubuntu base 的这些文件在非交互场景下会产生噪声甚至非零退出 ——
        // 表现就是 stderr 里混进「HISTCONTROL=…」「source file doesn't exist」这类与命令无关的
        // 文本，既污染 code_run 的输出，也曾让安装验证把 profile 噪声误判成安装失败。
        // Termux 官方 wiki 给的口径就是 `proot -r rootfs -0 -w / -b /dev -b /proc -b /sys /bin/sh`。
        val args = listOf(
            proot.absolutePath,
            "--kill-on-exit",
            // 伪 root：让 apt/dpkg 这类工具以为自己有权限
            "-0",
            "-r", rootfs.absolutePath,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            // 手机存储挂到 /sdcard，方便把生成的文件拿出来
            "-b", context.getExternalFilesDir(null)?.absolutePath ?: rootfs.absolutePath + "/root",
            "-b", "${home.absolutePath}:/root",
            "-w", "/root",
            "/usr/bin/env", "-i",
            "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "LANG=C.UTF-8",
            "TERM=dumb",
            "/bin/sh", "-c", command,
        )

        val startedAt = System.currentTimeMillis()
        runCatching {
            val process = ProcessBuilder(args).apply {
                environment()["PROOT_TMP_DIR"] = tmp.absolutePath
                // 部分机型上 seccomp 会打断 proot 的 ptrace，官方建议关掉
                environment()["PROOT_NO_SECCOMP"] = "1"
                // Termux 官方口径的第一句就是 unset LD_PRELOAD：termux-exec 的
                // execve() hook 与 proot 的路径翻译互相打架。显式置空，不赌宿主环境。
                environment()["LD_PRELOAD"] = ""
                environment()["LD_LIBRARY_PATH"] = listOf(
                    libDir(context).absolutePath,
                    context.applicationInfo.nativeLibraryDir,
                ).joinToString(":")
                redirectErrorStream(false)
            }.start()

            val out = StringBuilder()
            val err = StringBuilder()
            val outThread = Thread { process.inputStream.bufferedReader().forEachLine { out.appendLine(it) } }
            val errThread = Thread { process.errorStream.bufferedReader().forEachLine { err.appendLine(it) } }
            outThread.start()
            errThread.start()

            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                outThread.join(500)
                errThread.join(500)
                return@runCatching ExecResult(
                    exitCode = -1,
                    stdout = out.toString(),
                    stderr = err.toString(),
                    durationMs = System.currentTimeMillis() - startedAt,
                    timedOut = true,
                )
            }
            outThread.join(1_000)
            errThread.join(1_000)
            ExecResult(
                exitCode = process.exitValue(),
                stdout = out.toString(),
                stderr = err.toString(),
                durationMs = System.currentTimeMillis() - startedAt,
            )
        }
    }
}
