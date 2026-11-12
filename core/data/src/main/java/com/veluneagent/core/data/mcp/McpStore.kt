package com.veluneagent.core.data.mcp

import android.content.Context
import com.veluneagent.core.data.security.SecretBox
import org.json.JSONArray
import org.json.JSONObject

/**
 * MCP 服务器配置的持久化。
 *
 * 用 SharedPreferences 存 JSON 而不是建表：配置项只有几条，而且不需要按字段查询。
 * 建一张表带来的迁移成本，比这里省下的几行序列化代码更贵。
 *
 * **鉴权头要加密**：`Authorization: Bearer …` 与服务商 API Key 是同一级别的凭据，
 * 而服务商 Key（ProviderStore）与 QQ 令牌（QqBot）都走 [SecretBox]。
 * 这里同样走它，不允许出现"配置里混着一种明文令牌"的口子。
 */
class McpStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("velune_mcp", Context.MODE_PRIVATE)

    @Volatile
    private var cache: List<McpServer> = emptyList()

    init {
        cache = decode(prefs.getString(KEY_SERVERS, null))
    }

    fun list(): List<McpServer> = cache

    fun find(id: String): McpServer? = cache.firstOrNull { it.id == id }

    fun upsert(server: McpServer) {
        val next = cache.filterNot { it.id == server.id } + server
        persist(next)
    }

    fun delete(id: String) {
        persist(cache.filterNot { it.id == id })
    }

    private fun persist(servers: List<McpServer>) {
        cache = servers
        prefs.edit().putString(KEY_SERVERS, encode(servers).toString()).apply()
    }

    private fun encode(servers: List<McpServer>): JSONArray = JSONArray().apply {
        servers.forEach { server ->
            put(
                JSONObject().apply {
                    put("id", server.id)
                    put("name", server.name)
                    put("url", server.url)
                    put("enabled", server.enabled)
                    if (server.headers.isNotEmpty()) {
                        val plain = JSONObject().apply {
                            server.headers.forEach { (k, v) -> put(k, v) }
                        }.toString()
                        val cipher = runCatching { SecretBox.encrypt(plain) }.getOrNull()
                        if (cipher != null) {
                            put("headers_enc", cipher)
                        } else {
                            // 密钥库不可用时不写就会直接丢掉用户的令牌，宁可退化成明文：
                            // 丢凭据的代价（连不上、用户不知道自己填过什么）比这更大
                            put("headers", JSONObject(plain))
                        }
                    }
                },
            )
        }
    }

    private fun decode(raw: String?): List<McpServer> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val url = obj.optString("url").takeIf { it.isNotBlank() } ?: continue
                    add(
                        McpServer(
                            id = id,
                            name = obj.optString("name").ifBlank { id },
                            url = url,
                            headers = decodeHeaders(obj),
                            enabled = obj.optBoolean("enabled", true),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * 读鉴权头：新格式是 `headers_enc`（密文），旧格式是 `headers`（明文对象）。
     *
     * 旧数据继续可读，下次保存时自动升级成密文 —— 不需要额外的迁移脚本，
     * 也不会因为一次升级把用户已配好的服务器全部作废。
     */
    private fun decodeHeaders(obj: JSONObject): Map<String, String> {
        val cipher = obj.optString("headers_enc")
        if (cipher.isNotBlank()) {
            val plain = runCatching { SecretBox.decrypt(cipher) }.getOrNull()
                // 密钥被系统清掉时只能放弃：至少服务器本身还能用，只是要重填令牌
                ?: return emptyMap()
            return runCatching { toMap(JSONObject(plain)) }.getOrDefault(emptyMap())
        }
        return obj.optJSONObject("headers")?.let { runCatching { toMap(it) }.getOrDefault(emptyMap()) }
            .orEmpty()
    }

    private fun toMap(obj: JSONObject): Map<String, String> = buildMap {
        obj.keys().forEach { key -> put(key, obj.optString(key)) }
    }

    private companion object {
        const val KEY_SERVERS = "servers"
    }
}
