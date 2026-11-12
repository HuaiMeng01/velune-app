package com.veluneagent.core.data.im

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 扫码绑定：手机 QQ 扫一下，把 appId / appSecret / 绑定者 openid 拿回本地。
 *
 * 官方这套接口是**纯 HTTPS**，没有私有签名，三步：
 * 1. `POST /lite/create_bind_task`，body `{"key": base64(32 随机字节)}` → `data.task_id`；
 * 2. 二维码内容 = `connect.html?task_id=<id>&source=velune&_wv=2`；
 * 3. `POST /lite/poll_bind_result`，body `{"task_id": ...}` → `data.{status, bot_appid,
 *    bot_encrypt_secret, user_openid}`，`status`: 0 NONE / 1 PENDING / 2 COMPLETED / 3 EXPIRED。
 *
 * 密钥生命周期（最重要的一条）：
 * - 步骤 1 的 32 字节 key **只在内存**，用来解步骤 3 的密文；
 * - 解出 appSecret 后交给 `QqBot.save()`（内部 SecretBox 加密落库），随后 `key.fill(0)`；
 * - key 一旦落盘就等于 appSecret 明文落盘。**绝不**写 prefs / logcat / agent_runs。
 *
 * 为什么用 `java.util.Base64` 而不是 `android.util.Base64`：minSdk=26 已支持前者，
 * 且它是纯 JVM 实现 —— 解密逻辑因此可以脱离真机做单元测试（这是本文件唯一能验证的部分）。
 */
object QqBind {

    const val HostProduction = "https://q.qq.com"

    /** 测试环境只影响 create/poll；二维码里的 connect.html 固定 production。 */
    const val HostTest = "https://test.q.qq.com"

    private const val ConnectPage = "https://q.qq.com/qqbot/openclaw/connect.html"
    private const val IvBytes = 12
    private const val TagBytes = 16
    private const val KeyBytes = 32

    /** 一次绑定任务：key 必须与 task 同生命周期（拿到结果就立刻清零）。 */
    class Task(
        val taskId: String,
        val key: ByteArray,
        val qrContent: String,
        val createdAt: Long,
    )

    /** 轮询结果。三种状态之外没有第四种：网络错误走 `Result.failure`。 */
    sealed interface Poll {
        data object Pending : Poll
        data object Expired : Poll
        class Completed(
            val appId: String,
            val appSecret: String,
            val ownerOpenId: String,
        ) : Poll
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun qrContentOf(taskId: String, source: String = "velune"): String =
        "$ConnectPage?task_id=$taskId&source=$source&_wv=2"

    /** 创建绑定任务：本地生成 32 字节 key，只把它的 base64 发给服务端。 */
    suspend fun createTask(host: String = HostProduction): Result<Task> = runCatching {
        val key = ByteArray(KeyBytes).also { SecureRandom().nextBytes(it) }
        val body = JSONObject()
            .put("key", Base64.getEncoder().encodeToString(key))
            .toString()
        val json = post("$host/lite/create_bind_task", body)
        if (json.optInt("retcode", -1) != 0) {
            error("创建绑定任务失败：retcode=${json.optInt("retcode")}")
        }
        val taskId = json.optJSONObject("data")?.optString("task_id").orEmpty()
        if (taskId.isBlank()) error("创建绑定任务失败：task_id 为空")
        Task(
            taskId = taskId,
            key = key,
            qrContent = qrContentOf(taskId),
            createdAt = System.currentTimeMillis(),
        )
    }

    /**
     * 轮询一次绑定结果。
     *
     * `status=2` 时才解密密文：解密失败必须让整次轮询失败（`Result.failure`），
     * 绝不能"返回一个空 appSecret 假装成功"——那会让用户填进一个用不了的凭证。
     */
    suspend fun poll(task: Task, host: String = HostProduction): Result<Poll> = runCatching {
        val json = post("$host/lite/poll_bind_result", JSONObject().put("task_id", task.taskId).toString())
        // retcode 缺失时按成功处理（字段稳定性未知），存在且非 0 才判失败：
        // 否则任务已失效也会一直 Pending，让用户白等到 180 秒 TTL
        if (json.has("retcode") && json.optInt("retcode", 0) != 0) {
            error("轮询绑定结果失败：retcode=${json.optInt("retcode")}")
        }
        val data = json.optJSONObject("data") ?: return@runCatching Poll.Pending
        when (data.optInt("status", 0)) {
            2 -> {
                val appId = data.optString("bot_appid")
                if (appId.isBlank()) error("绑定结果缺少 bot_appid")
                val secret = decryptSecret(data.optString("bot_encrypt_secret"), task.key).getOrThrow()
                Poll.Completed(appId, secret, data.optString("user_openid"))
            }
            // 0 NONE / 1 PENDING / 未知值都按"还在等"
            3 -> Poll.Expired
            else -> Poll.Pending
        }
    }

    /**
     * 密文布局：`[12B IV][ciphertext][16B authTag]`。
     *
     * javax 的 GCM 要求 tag 紧跟密文尾部，所以把 `[12, end)` 整段交给 `doFinal`。
     * 写错**不会**抛异常，而是解出乱码 —— 所以这里必须有固定向量单测兜住。
     */
    internal fun decryptSecret(encryptSecretB64: String, key: ByteArray): Result<String> = runCatching {
        require(key.size == KeyBytes) { "key 必须是 32 字节" }
        val payload = Base64.getDecoder().decode(encryptSecretB64)
        require(payload.size > IvBytes + TagBytes) { "密文长度不足" }
        val iv = payload.copyOfRange(0, IvBytes)
        val ctAndTag = payload.copyOfRange(IvBytes, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, iv),
        )
        String(cipher.doFinal(ctAndTag), Charsets.UTF_8)
    }

    private suspend fun post(url: String, body: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .post(body.toRequestBody(jsonMedia))
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("HTTP ${response.code}：${text.take(120)}")
            JSONObject(text)
        }
    }
}
