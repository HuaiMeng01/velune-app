package com.veluneagent.core.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 单个模型服务商配置。API Key 只以密文形式存在于本地。 */
data class Provider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val model: String,
    val hasKey: Boolean,
)

sealed interface ProbeResult {
    data class Success(val modelCount: Int) : ProbeResult
    data class HttpError(val code: Int, val message: String) : ProbeResult
    data class NetworkError(val message: String) : ProbeResult
}

/**
 * 服务商与密钥存储。
 *
 * 密钥方案：Android Keystore 生成不可导出的 AES-256 密钥（GCM 模式），
 * 用它加密 API Key 后只把 Base64 密文写进 SharedPreferences。
 * 明文不落盘、不写日志、不进备份（android:allowBackup=false）。
 */
object ProviderStore {

    // internal 而非 private：测试要按字面量预置密钥/读配置才能绕开 AndroidKeyStore。
    // 若测试自己复制字符串，源码一改名测试就会红成「缺少 API Key」，排查成本远高于放宽可见性。
    internal const val PREFS = "velune_providers"
    private const val KEY_STORE = "AndroidKeyStore"
    internal const val KEY_ALIAS = "velune_provider_master"
    private const val FIELD_ITEMS = "items"
    private const val FIELD_ACTIVE = "active_id"

    private var appContext: Context? = null

    private val _providers = MutableStateFlow<List<Provider>>(emptyList())
    val providers: StateFlow<List<Provider>> = _providers

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId

    private val _probe = MutableStateFlow<Map<String, ProbeResult>>(emptyMap())
    val probeResults: StateFlow<Map<String, ProbeResult>> = _probe

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        _providers.value = readAll()
        _activeId.value = prefs().getString(FIELD_ACTIVE, null)
    }

    fun save(
        id: String?,
        name: String,
        baseUrl: String,
        model: String,
        apiKey: String?,
    ): Provider {
        val ctx = requireContext()
        val items = readAll().toMutableList()
        val existingIndex = items.indexOfFirst { it.id == id }
        val finalId = id ?: "p_" + System.currentTimeMillis().toString(36)

        // 只有用户真的输入了新 Key 才覆盖密文；留空表示「保持原值」
        val cipher: String = when {
            !apiKey.isNullOrBlank() -> encrypt(apiKey.trim())
            existingIndex >= 0 -> readCipher(finalId).orEmpty()
            else -> ""
        }

        val provider = Provider(
            id = finalId,
            name = name.trim().ifEmpty { "未命名服务商" },
            // 容忍用户只填 api.example.com：少写协议头是最常见的配置错误，
            // 直接报「MalformedURLException」对用户毫无帮助，这里补全成 https。
            baseUrl = normalizeBaseUrl(baseUrl),
            model = model.trim(),
            hasKey = cipher.isNotEmpty(),
        )

        if (existingIndex >= 0) items[existingIndex] = provider
        else items += provider

        writeAll(items, ciphers = mapOf(finalId to cipher))
        _providers.value = items
        return provider
    }

    /** 当前使用的服务商：显式选中的，否则列表第一个。 */
    fun activeProvider(): Provider? {
        val items = _providers.value
        return items.firstOrNull { it.id == _activeId.value } ?: items.firstOrNull()
    }

    fun setActive(id: String) {
        _activeId.value = id
        prefs().edit().putString(FIELD_ACTIVE, id).apply()
    }

    fun delete(id: String) {
        val items = readAll().filterNot { it.id == id }
        writeAll(items, ciphers = emptyMap(), removeIds = setOf(id))
        _providers.value = items
        _probe.value = _probe.value - id
        // 删掉的正好是当前默认项时，立刻把默认项顺延到剩下的第一个，
        // 否则 activeId 会指向一个不存在的配置，直到用户手动切换才恢复。
        if (_activeId.value == id) {
            val next = items.firstOrNull()?.id
            _activeId.value = next
            prefs().edit().apply {
                if (next == null) remove(FIELD_ACTIVE) else putString(FIELD_ACTIVE, next)
            }.apply()
        }
    }

    /**
     * 归一化用户手填的 Base URL。
     *
     * 两个高频错误都在这里消化：
     * 1. 省略 scheme（补 https）；
     * 2. 把文档里的完整 endpoint 整行粘进来。请求侧固定拼 `/chat/completions`，
     *    不去掉这个后缀就会拼成 `.../chat/completions/chat/completions` → 404，
     *    而 404 的提示语还会反过来让人「检查要不要带 /v1」，越改越乱。
     */
    private fun normalizeBaseUrl(raw: String): String {
        var trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return trimmed
        trimmed = trimmed.removeSuffix("/chat/completions").trimEnd('/')
        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        return withScheme.trimEnd('/')
    }

    /** 依赖 Keystore 解密，只在发起请求的瞬间调用。 */
    fun apiKeyOf(id: String): String? {
        val cipher = readCipher(id) ?: return null
        if (cipher.isEmpty()) return null
        return runCatching { decrypt(cipher) }.getOrNull()
    }

    /**
     * 是否装了可用的 DeepSeek 服务商（搜索/抓取只认它的 Key）。
     *
     * 只读**内存**里的 `hasKey` 标记，不做 Keystore 解密：提示词拼装是每次请求都要跑的路径，
     * 为了一个布尔判断去解密太贵。`hasKey` 本身来自已解密的摘要，足够回答"配没配"。
     */
    fun hasDeepSeekKey(): Boolean = _providers.value.any {
        it.baseUrl.contains("api.deepseek.com", ignoreCase = true) && it.hasKey
    }

    /**
     * 取一个可用的 DeepSeek Key，供网页搜索/抓取接口使用。
     *
     * 搜索接口只认 DeepSeek 的 key，与当前对话用哪家模型无关：用户可能用 MiMo 聊天，
     * 但想联网搜索——这时仍然要找一个 DeepSeek 服务商来借 key。
     * 优先当前服务商（如果它本身就是 DeepSeek），否则取第一个。
     */
    fun deepSeekKey(): String? {
        val candidates = _providers.value.filter {
            it.baseUrl.contains("api.deepseek.com", ignoreCase = true) && it.hasKey
        }
        if (candidates.isEmpty()) return null
        val active = _activeId.value
        val provider = candidates.firstOrNull { it.id == active } ?: candidates.first()
        return apiKeyOf(provider.id)
    }

    /**
     * 取一个 MiMo Key，供语音合成（TTS）使用。
     *
     * 与 [deepSeekKey] 同构：TTS 只认 MiMo 的 key，与当前聊天用哪家模型无关。
     * 用户只需要在「模型与密钥」里加一个 baseUrl 为 api.xiaomimimo.com 的服务商即可——
     * 不为一个语音功能再造一套密钥管理界面。
     */
    fun mimoTtsKey(): String? {
        val candidates = _providers.value.filter {
            it.baseUrl.contains("api.xiaomimimo.com", ignoreCase = true) && it.hasKey
        }
        if (candidates.isEmpty()) return null
        val active = _activeId.value
        val provider = candidates.firstOrNull { it.id == active } ?: candidates.first()
        return apiKeyOf(provider.id)
    }

    /**
     * 分级连接探针：先做 HTTP 鉴权 + 列模型，成功即证明 Key、BaseURL、网络三者都通。
     * 不回显 Key，只回结果。
     */
    suspend fun probe(id: String): ProbeResult = withContext(Dispatchers.IO) {
        val provider = readAll().firstOrNull { it.id == id }
            ?: return@withContext ProbeResult.NetworkError("配置不存在")
        val key = apiKeyOf(id)
            ?: return@withContext ProbeResult.NetworkError("缺少 API Key")

        val url = provider.baseUrl.trimEnd('/') + "/models"
        val result = runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 12_000
                setRequestProperty("Authorization", "Bearer $key")
                setRequestProperty("Accept", "application/json")
            }
            try {
                val code = conn.responseCode
                if (code in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val count = runCatching {
                        JSONObject(body).optJSONArray("data")?.length()
                    }.getOrNull() ?: 0
                    ProbeResult.Success(count)
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    ProbeResult.HttpError(code, err.take(200))
                }
            } finally {
                conn.disconnect()
            }
        }.getOrElse { e ->
            ProbeResult.NetworkError(e.message ?: e.javaClass.simpleName)
        }

        _probe.value = _probe.value + (id to result)
        result
    }

    // ---------- 存储 ----------

    private fun readAll(): List<Provider> {
        val raw = prefs().getString(FIELD_ITEMS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    val id = o.getString("id")
                    add(
                        Provider(
                            id = id,
                            name = o.optString("name"),
                            baseUrl = o.optString("baseUrl"),
                            model = o.optString("model"),
                            hasKey = o.optString("key").isNotEmpty(),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeAll(
        items: List<Provider>,
        ciphers: Map<String, String>,
        removeIds: Set<String> = emptySet(),
    ) {
        val existing = runCatching {
            val raw = prefs().getString(FIELD_ITEMS, null) ?: "[]"
            val array = JSONArray(raw)
            buildMap {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    put(o.getString("id"), o.optString("key"))
                }
            }
        }.getOrDefault(emptyMap())

        val merged = existing + ciphers - removeIds

        val array = JSONArray()
        items.forEach { p ->
            array.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("baseUrl", p.baseUrl)
                    put("model", p.model)
                    put("key", merged[p.id] ?: "")
                },
            )
        }
        prefs().edit().putString(FIELD_ITEMS, array.toString()).apply()
    }

    private fun readCipher(id: String): String? = runCatching {
        val raw = prefs().getString(FIELD_ITEMS, null) ?: return null
        val array = JSONArray(raw)
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            if (o.getString("id") == id) return o.optString("key")
        }
        null
    }.getOrNull()

    private fun prefs() = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun requireContext(): Context =
        appContext ?: error("ProviderStore.init(context) 未调用")

    // ---------- 加解密 ----------

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEY_STORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        // 格式：base64(iv) + ":" + base64(ciphertext)
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(body, Base64.NO_WRAP)
    }

    private fun decrypt(payload: String): String {
        val parts = payload.split(":")
        require(parts.size == 2) { "密文格式不正确" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val body = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(body), Charsets.UTF_8)
    }
}
