package com.veluneagent.core.data.ssh

import android.content.Context
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.veluneagent.core.data.security.SecretBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * SSH 远程执行。
 *
 * 为什么要有它：手机上跑不了完整的 Linux 用户空间（没有 root、不能 mount/chroot），
 * 与其塞一个 30MB 的 proot rootfs 进来，不如把「跑命令」这件事交给用户自己的服务器。
 * 这样零包体积、天然隔离，命令写坏了也只影响那台机器。
 *
 * 安全：主机指纹采用 TOFU（首次信任）。第一次连接记录指纹，之后不一致直接拒绝连接。
 * 这比旧版的 `StrictHostKeyChecking=no` 严格得多 —— 后者意味着任何能劫持网络的中间人
 * 都可以伪装成目标服务器，拿到这里提交的密码 / 私钥全文并篡改命令输出。
 * 指纹单独存一个 prefs，`clear()` 清配置时不会连带清掉保护。
 */
object Ssh {

    data class Host(
        val host: String = "",
        val port: Int = 22,
        val user: String = "",
        /** 密码鉴权；与私钥二选一 */
        val password: String = "",
        /** OpenSSH / PEM 私钥正文 */
        val privateKey: String = "",
        val name: String = "",
    ) {
        val configured: Boolean
            get() = host.isNotBlank() && user.isNotBlank() &&
                (password.isNotBlank() || privateKey.isNotBlank())
    }

    data class ExecResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val durationMs: Long,
    ) {
        val ok: Boolean get() = exitCode == 0
    }

    private const val PREFS = "velune_ssh"
    /** 指纹库独立存放：`clear()` 会清空 velune_ssh，混在一起等于一键清掉保护。 */
    private const val HOSTKEY_PREFS = "velune_ssh_hostkeys"
    private const val KEY_HOST = "host"
    private const val KEY_PORT = "port"
    private const val KEY_USER = "user"
    private const val KEY_PASSWORD = "password"
    private const val KEY_KEY = "private_key"
    private const val KEY_NAME = "name"

    private var appContext: Context? = null

    private val _host = MutableStateFlow(Host())
    val host: StateFlow<Host> = _host

    fun init(context: Context) {
        if (appContext != null) return
        val ctx = context.applicationContext
        appContext = ctx
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _host.value = Host(
            host = prefs.getString(KEY_HOST, "").orEmpty(),
            port = prefs.getInt(KEY_PORT, 22),
            user = prefs.getString(KEY_USER, "").orEmpty(),
            password = decryptOrEmpty(prefs.getString(KEY_PASSWORD, null)),
            privateKey = decryptOrEmpty(prefs.getString(KEY_KEY, null)),
            name = prefs.getString(KEY_NAME, "").orEmpty(),
        )
    }

    fun save(config: Host) {
        val normalized = config.copy(
            host = config.host.trim(),
            user = config.user.trim(),
            port = config.port.coerceIn(1, 65_535),
        )
        _host.value = normalized
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_HOST, normalized.host)
            putInt(KEY_PORT, normalized.port)
            putString(KEY_USER, normalized.user)
            putString(KEY_PASSWORD, encryptOrEmpty(normalized.password))
            putString(KEY_KEY, encryptOrEmpty(normalized.privateKey))
            putString(KEY_NAME, normalized.name)
        }.apply()
    }

    fun clear() {
        _host.value = Host()
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.clear()?.apply()
    }

    private fun decryptOrEmpty(cipher: String?): String =
        if (cipher.isNullOrBlank()) "" else runCatching { SecretBox.decrypt(cipher) }.getOrDefault("")

    private fun encryptOrEmpty(plain: String): String =
        if (plain.isBlank()) "" else runCatching { SecretBox.encrypt(plain) }.getOrDefault("")

    /**
     * 执行一条命令。
     *
     * 输出上限 20KB：远端一条 `cat` 就能产生几十兆，不截断会把上下文和 UI 一起拖死。
     * 超时同时作用于连接与读取，避免 `tail -f` 这类命令把工具卡挂到天荒地老。
     */
    suspend fun exec(
        command: String,
        config: Host = _host.value,
        timeoutMs: Int = 30_000,
    ): Result<ExecResult> = withContext(Dispatchers.IO) {
        if (!config.configured) {
            return@withContext Result.failure(
                IllegalStateException("SSH 未配置：设置 → 远程服务器，填写地址与凭据"),
            )
        }
        val cmd = command.trim()
        if (cmd.isEmpty()) return@withContext Result.failure(IllegalArgumentException("命令不能为空"))

        runCatching {
            val startedAt = System.currentTimeMillis()
            val jsch = JSch().apply {
                if (config.privateKey.isNotBlank()) {
                    addIdentity(
                        "velune",
                        config.privateKey.toByteArray(Charsets.UTF_8),
                        null,
                        null,
                    )
                }
                // 有指纹库就严格校验；只有 init 没跑（appContext 为空）时才降级，
                // 否则 JSch 遇到未知主机会因为没有 UserInfo 而直接抛异常。
                appContext?.let { ctx ->
                    setHostKeyRepository(
                        TofuHostKeyRepository(
                            ctx.getSharedPreferences(HOSTKEY_PREFS, Context.MODE_PRIVATE),
                        ),
                    )
                }
            }
            val session = jsch.getSession(config.user, config.host, config.port).apply {
                if (config.password.isNotBlank()) setPassword(config.password)
                if (appContext == null) setConfig("StrictHostKeyChecking", "no")
                timeout = timeoutMs
            }
            session.connect(timeoutMs)

            try {
                val channel = session.openChannel("exec") as ChannelExec
                channel.setCommand(cmd)
                val stdout = ByteArrayOutputStream()
                val stderr = ByteArrayOutputStream()
                channel.setInputStream(null)
                channel.setErrStream(stderr)
                channel.connect(10_000)

                val input = channel.inputStream
                val buffer = ByteArray(8_192)
                val deadline = System.currentTimeMillis() + timeoutMs
                while (true) {
                    while (input.available() > 0) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        if (stdout.size() < MaxOutputBytes) {
                            stdout.write(buffer, 0, minOf(read, MaxOutputBytes - stdout.size()))
                        }
                    }
                    if (channel.isClosed) break
                    if (System.currentTimeMillis() > deadline) {
                        error("命令超时（${timeoutMs / 1000}s）")
                    }
                    Thread.sleep(50)
                }

                ExecResult(
                    exitCode = channel.exitStatus,
                    stdout = stdout.toString(Charsets.UTF_8.name()),
                    stderr = stderr.toString(Charsets.UTF_8.name()),
                    durationMs = System.currentTimeMillis() - startedAt,
                )
            } finally {
                runCatching { session.disconnect() }
            }
        }.recoverCatching { error -> throw prettify(error) }
    }

    /**
     * JSch 的异常是英文的，直接抛给用户只会看到 "HostKey has been changed"。
     * 指纹不匹配是安全事件，必须给出「发生了什么 + 该怎么办」。
     */
    private fun prettify(error: Throwable): Throwable {
        val message = error.message.orEmpty()
        val fingerprintIssue = error is com.jcraft.jsch.JSchException &&
            (message.contains("HostKey", ignoreCase = true) ||
                message.contains("UnknownHostKey", ignoreCase = true))
        return if (fingerprintIssue) {
            IllegalStateException(
                "服务器指纹与上次记录不一致，连接已拒绝。如果你确实更换了服务器，" +
                    "请到 设置 → 远程服务器 点「清除」后重新填写；否则这台机器可能被冒充。",
            )
        } else {
            error
        }
    }

    private const val MaxOutputBytes = 20_000
}

/**
 * TOFU（首次信任）主机指纹库。
 *
 * 首次连接记录指纹，之后指纹不一致返回 [com.jcraft.jsch.HostKeyRepository.CHANGED]，
 * JSch 会抛 HostKeyException 终止连接 —— 这正是 OpenSSH `accept-new` 的语义，
 * 而 JSch 本身不提供，所以自己实现一份。
 */
private class TofuHostKeyRepository(
    private val prefs: android.content.SharedPreferences,
) : com.jcraft.jsch.HostKeyRepository {

    override fun check(host: String, key: ByteArray): Int {
        val current = android.util.Base64.encodeToString(key, android.util.Base64.NO_WRAP)
        val saved = prefs.getString(keyOf(host), null)
        return when {
            saved == null -> {
                // 首次见到的服务器：记下来。之后任何变化都会被下面的分支拦下。
                prefs.edit().putString(keyOf(host), current).apply()
                com.jcraft.jsch.HostKeyRepository.OK
            }

            saved == current -> com.jcraft.jsch.HostKeyRepository.OK
            else -> com.jcraft.jsch.HostKeyRepository.CHANGED
        }
    }

    override fun add(hostkey: com.jcraft.jsch.HostKey?, ui: com.jcraft.jsch.UserInfo?) {
        if (hostkey == null) return
        prefs.edit().putString(keyOf(hostkey.host), hostkey.key).apply()
    }

    override fun remove(host: String?, type: String?) {
        if (host != null) prefs.edit().remove(keyOf(host)).apply()
    }

    override fun remove(host: String?, type: String?, key: ByteArray?) {
        remove(host, type)
    }

    override fun getKnownHostsRepositoryID(): String = "velune-tofu"

    override fun getHostKey(): Array<com.jcraft.jsch.HostKey>? = null

    override fun getHostKey(host: String?, type: String?): Array<com.jcraft.jsch.HostKey>? = null

    /**
     * 去掉端口再存：JSch 在 check 里传 `host:port`，而 HostKey.host 通常只有主机名。
     * IPv6 地址含冒号的情况本项目暂不支持（SSH 配置页只接受主机名 / IPv4）。
     */
    private fun keyOf(host: String): String = "hostkey_" + host.substringBefore(':')
}
