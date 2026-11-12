package com.veluneagent.core.data.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * 小米 MiMo 语音合成（TTS）。
 *
 * 接口契约（官方文档，核对于 2026-10-22）：
 * - `POST https://api.xiaomimimo.com/v1/chat/completions`
 * - 头：`api-key: <KEY>`（也接受 `Authorization: Bearer <KEY>`）
 * - body：`model = mimo-v2.5-tts`，**要朗读的文本必须放在 role=assistant 的消息里**，
 *   `role=user` 那条是风格指令（不朗读），`audio.format` 支持 wav/mp3/pcm/pcm16
 * - 返回：`choices[0].message.audio.data` 是 base64 音频
 *
 * 注意两个反直觉点，写错就只能是"接口 200 但没声音"：
 * 1. 它不是 OpenAI 的 `/audio/speech`，而是包在 chat/completions 里的；
 * 2. 文本放 user 里会变成"风格描述"，模型不会读它。
 */
object MimoTts {

    const val BaseUrl = "https://api.xiaomimimo.com/v1"

    /** 文档当前给的标准版模型；voicedesign 需要额外的音色描述，这里不做。 */
    const val DefaultModel = "mimo-v2.5-tts"

    const val DefaultVoice = "mimo_default"

    /** 官方列出的内置音色：前四个中文，后面是英文/多语。 */
    val Voices = listOf(
        "mimo_default" to "MiMo 默认",
        "冰糖" to "冰糖",
        "茉莉" to "茉莉",
        "苏打" to "苏打",
        "白桦" to "白桦",
        "Mia" to "Mia",
        "Chloe" to "Chloe",
        "Milo" to "Milo",
        "Dean" to "Dean",
    )

    /** 单次合成的文本上限：太长会撞上游限制，也会让一次工具调用卡几分钟。 */
    const val MaxChars = 600

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        // 语音合成比文本慢一个量级，读超时给足
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /**
     * @param text 要朗读的正文（会被原样读出）
     * @param style 风格指令，如「开心地说」「四川话」，可为空
     */
    suspend fun synthesize(
        apiKey: String,
        text: String,
        voice: String = DefaultVoice,
        style: String? = null,
        format: String = "wav",
        model: String = DefaultModel,
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                put("model", model)
                put(
                    "messages",
                    JSONArray().apply {
                        // 顺序有意义：user 是风格，assistant 才是要读的内容
                        if (!style.isNullOrBlank()) {
                            put(JSONObject().put("role", "user").put("content", style))
                        }
                        put(JSONObject().put("role", "assistant").put("content", text))
                    },
                )
                put("audio", JSONObject().put("format", format).put("voice", voice))
                put("stream", false)
            }.toString()

            val request = Request.Builder()
                .url("$BaseUrl/chat/completions")
                .addHeader("api-key", apiKey.trim())
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(jsonMedia))
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    // 上游的错误体通常带 code/message，截断保留可判断的部分
                    throw IllegalStateException("TTS 请求失败：HTTP ${response.code} ${raw.take(200)}")
                }
                val json = JSONObject(raw)
                val message = json.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?: throw IllegalStateException("TTS 返回里没有 message")
                val base64 = message.optJSONObject("audio")?.optString("data").orEmpty()
                if (base64.isBlank()) {
                    val reason = message.optString("content").take(120)
                    throw IllegalStateException("TTS 没有返回音频${if (reason.isBlank()) "" else "：$reason"}")
                }
                Base64.getDecoder().decode(base64)
            }
        }
    }

    /** 音频格式 → 文件扩展名。返回 pcm 时没有容器，直接给 .pcm 便于排查。 */
    fun extensionOf(format: String): String = when (format.lowercase()) {
        "mp3" -> "mp3"
        "pcm", "pcm16" -> "pcm"
        else -> "wav"
    }
}
