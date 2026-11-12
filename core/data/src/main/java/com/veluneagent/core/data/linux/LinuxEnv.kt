package com.veluneagent.core.data.linux

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * Linux 用户空间（Ubuntu base）的下载与安装。
 *
 * 为什么 rootfs 要**用户主动下载**而不是打进 APK：Ubuntu base 解压后 ~300MB，
 * 而这个能力只有一部分用户会用。打成安装包等于让所有人替少数人付体积。
 *
 * 为什么用 Ubuntu **base** 而不是完整版：base 只有内核外的最小用户空间（约 30MB 压缩），
 * 需要什么用 `apt` 在环境里装 —— 这与 Termux/proot 生态的通行做法一致。
 *
 * 注意：这里只负责"文件就位"，**不负责启动 proot**（那是 [LinuxRuntime] 的事）。
 * 拆开是因为 rootfs 的下载/解压/校验与可执行文件的存在与否完全解耦：
 * proot 二进制换了、rootfs 不用重下。
 */
object LinuxEnv {

    enum class State { NotInstalled, Downloading, Extracting, Verifying, Ready, Failed }

    /**
     * 下载镜像，按序尝试。
     *
     * cdimage.ubuntu.com 在中国大陆的可用性不稳定（直连经常超时/断流），所以把
     * TUNA / USTC 的 ubuntu-cdimage 镜像排在前面（已验证均返回 200）。
     * 镜像内容与官方一致（releases 目录原样镜像），不需要按架构再挑路径。
     */
    val MirrorUrls = listOf(
        "https://mirrors.tuna.tsinghua.edu.cn/ubuntu-cdimage/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-arm64.tar.gz",
        "https://mirrors.ustc.edu.cn/ubuntu-cdimage/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-arm64.tar.gz",
        "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-arm64.tar.gz",
    )

    /** 兼容旧引用：官方源（镜像列表的最后一个）。 */
    const val RootfsUrl = "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-arm64.tar.gz"

    /** 压缩包约 30MB；界面上先告诉用户要下多少，别让人点完才发现。 */
    const val ApproxDownloadMb = 30

    private val _state = MutableStateFlow(State.NotInstalled)
    val state: StateFlow<State> = _state

    /** 0..1，仅在 [State.Downloading] 时有意义。 */
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private var appContext: Context? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.MINUTES)
        .build()

    fun init(context: Context) {
        appContext = context.applicationContext
        _state.value = if (rootfsDir(context).resolve("bin").isDirectory) State.Ready else State.NotInstalled
    }

    fun rootfsDir(context: Context = appContext!!): File = File(context.filesDir, "linux/rootfs")

    fun homeDir(context: Context = appContext!!): File = File(context.filesDir, "linux/home").apply { mkdirs() }

    fun tmpDir(context: Context = appContext!!): File = File(context.filesDir, "linux/tmp").apply { mkdirs() }

    /** proot 的依赖库目录（libtalloc.so.2 等，来自 assets，安装时拷贝到这里）。 */
    fun libDir(context: Context = appContext!!): File = File(context.filesDir, "linux/lib").apply { mkdirs() }

    fun isReady(): Boolean = _state.value == State.Ready

    suspend fun install() = withContext(Dispatchers.IO) {
        val context = appContext ?: return@withContext
        if (_state.value == State.Downloading || _state.value == State.Extracting) return@withContext
        val archive = File(context.cacheDir, "ubuntu-base.tar.gz")
        _message.value = null
        try {
            _state.value = State.Downloading
            downloadWithMirrors(archive)

            _state.value = State.Extracting
            _progress.value = 0f
            val target = rootfsDir(context)
            if (target.exists()) target.deleteRecursively()
            target.mkdirs()
            TarGz.extract(archive, target) { done, total ->
                if (total > 0) _progress.value = (done.toFloat() / total).coerceIn(0f, 1f)
            }
            // 解压完必须能看见 /bin —— 缺这一步会出现"装完了但跑不了"的悬空状态
            if (!target.resolve("bin").isDirectory) {
                throw IllegalStateException("解压结果不完整：缺少 /bin")
            }
            // 把 assets 里的依赖库拷到 files/linux/lib（LinuxRuntime 的 LD_LIBRARY_PATH 指向这里）。
            // libtalloc.so.2 带版本号进不了 jniLibs（Gradle 只认 lib*.so），所以走 assets+拷贝。
            // （libproot.so 的 DT_NEEDED 只有 libtalloc/libandroid-shmem/libc，已用 ELF 解析核实；
            //   libtermux-exec 走的是 LD_PRELOAD hook，不是动态链接依赖。）
            val libTarget = libDir(context)
            context.assets.list("linux/lib").orEmpty().forEach { name ->
                context.assets.open("linux/lib/$name").use { input ->
                    File(libTarget, name).outputStream().use { output -> input.copyTo(output) }
                }
            }
            // DNS：rootfs 自带的 resolv.conf 指向 systemd-resolved，在 proot 里不可用
            File(target, "etc/resolv.conf").writeText("nameserver 1.1.1.1\nnameserver 223.5.5.5\n")
            archive.delete()

            // 冒烟验证：rootfs「文件就位」≠「跑得起来」。proot 起不来、/bin/sh 缺失、
            // SELinux/seccomp 拦截 —— 这些只有真跑一次才知道。失败在这里就暴露，
            // 并带上 proot 的真实 stderr；否则用户要等到 code_run 才撞一个语焉不详的错误。
            // （不要用 -lc：login shell 会把 profile 的噪声混进输出，误导失败判定。）
            _state.value = State.Verifying
            val smoke = LinuxRuntime.run(context, "echo __SMOKE_OK__", timeoutMs = 30_000)
            val smokeOut = smoke.getOrNull()
            val smokeOk = smokeOut != null && !smokeOut.timedOut &&
                smokeOut.exitCode == 0 && "__SMOKE_OK__" in smokeOut.stdout
            if (!smokeOk) {
                val detail = listOfNotNull(
                    smokeOut?.stderr?.lineSequence()?.firstOrNull { it.isNotBlank() },
                    smokeOut?.stdout?.lineSequence()?.firstOrNull { it.isNotBlank() },
                    smoke.exceptionOrNull()?.message,
                ).firstOrNull() ?: "未知原因（无输出）"
                throw IllegalStateException("rootfs 已就绪，但 proot 启动验证失败：${detail.take(300)}")
            }

            _state.value = State.Ready
            _progress.value = 1f
        } catch (t: Throwable) {
            _state.value = State.Failed
            // 截断到 400 字：message 直接进设置页的 Text，太长会撑爆卡片
            _message.value = (t.message ?: t.javaClass.simpleName).take(400)
        }
    }

    suspend fun remove() = withContext(Dispatchers.IO) {
        val context = appContext ?: return@withContext
        rootfsDir(context).deleteRecursively()
        _state.value = State.NotInstalled
        _progress.value = 0f
        _message.value = null
    }

    /** 依次尝试镜像；每个镜像都校验「下到的字节数 == Content-Length」，防断流解出残缺 rootfs。 */
    private fun downloadWithMirrors(target: File) {
        var lastError: Exception? = null
        for ((index, url) in MirrorUrls.withIndex()) {
            try {
                downloadOne(url, target)
                return
            } catch (e: Exception) {
                lastError = e
                _progress.value = 0f   // 下一个镜像从零开始
                _message.value = "镜像 ${index + 1}/${MirrorUrls.size} 失败（${e.message?.take(120)}），切换下一个…"
            }
        }
        throw IllegalStateException("全部 ${MirrorUrls.size} 个镜像下载失败：${lastError?.message?.take(200)}", lastError)
    }

    private fun downloadOne(url: String, target: File) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            val total = response.body?.contentLength() ?: -1L
            response.body?.byteStream()?.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            if (written > total) throw IllegalStateException("响应超过 Content-Length，数据异常")
                            _progress.value = (written.toFloat() / total).coerceIn(0f, 1f)
                        }
                    }
                    // 断流防护：连接被中途掐掉时 read 会正常返回 -1，但文件只有半截。
                    // 不校验这个，解压会解出残缺 rootfs，错误被推迟到 code_run 才出现。
                    if (total > 0 && written != total) {
                        throw IllegalStateException("下载不完整：${written}/${total} 字节")
                    }
                }
            }
        }
    }
}
