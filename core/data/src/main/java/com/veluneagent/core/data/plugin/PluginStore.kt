package com.veluneagent.core.data.plugin

import android.content.ContentValues
import android.content.Context
import com.veluneagent.core.data.VeluneDb
import com.veluneagent.core.data.agent.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** 已安装插件。manifest 保留原文，卸载/执行时无需重新解析来源。 */
data class InstalledPlugin(
    val manifest: PluginManifest,
    val enabled: Boolean,
    val installedAt: Long,
) {
    val id: String get() = manifest.id
}

sealed interface InstallResult {
    data class Ok(val plugin: InstalledPlugin) : InstallResult
    data class Invalid(val errors: List<String>) : InstallResult
}

/**
 * Lite 插件仓库。
 *
 * 只存 JSON 清单，不落任何可执行文件 —— 安装动作不会给应用增加执行面。
 * 上限 30 个：工具集最终要全部塞进模型的 tools 数组，无上限会把上下文挤爆。
 */
class PluginStore(context: Context) {

    private val helper = VeluneDb.of(context)

    private val _plugins = MutableStateFlow<List<InstalledPlugin>>(emptyList())
    val plugins: StateFlow<List<InstalledPlugin>> = _plugins

    init {
        // 首次读取放到后台线程，避免 Application.onCreate 里做磁盘 IO
        Thread {
            runCatching { _plugins.value = readAll() }
        }.apply {
            name = "velune-plugins-load"
            isDaemon = true
        }.start()
    }

    suspend fun install(manifest: PluginManifest): InstallResult = withContext(Dispatchers.IO) {
        val current = readAll()
        if (current.size >= MaxPlugins && current.none { it.id == manifest.id }) {
            return@withContext InstallResult.Invalid(
                listOf("插件数量已达上限 $MaxPlugins 个，请先卸载不用的插件"),
            )
        }
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("id", manifest.id)
            put("name", manifest.name)
            put("version", manifest.version)
            put("description", manifest.description)
            put("scopes", JSONArray(manifest.scopes).toString())
            put("manifest", manifest.toJson())
            put("enabled", 1)
            put("installed_at", now)
        }
        helper.writableDatabase.insertWithOnConflict(
            "plugins",
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
        val refreshed = readAll()
        _plugins.value = refreshed
        InstallResult.Ok(
            refreshed.firstOrNull { it.id == manifest.id }
                ?: InstalledPlugin(manifest, enabled = true, installedAt = now),
        )
    }

    suspend fun uninstall(id: String): Boolean = withContext(Dispatchers.IO) {
        val removed = helper.writableDatabase.delete("plugins", "id = ?", arrayOf(id))
        _plugins.value = readAll()
        removed > 0
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put("enabled", if (enabled) 1 else 0) }
        helper.writableDatabase.update("plugins", values, "id = ?", arrayOf(id))
        _plugins.value = readAll()
    }

    fun find(id: String): InstalledPlugin? = _plugins.value.firstOrNull { it.id == id }

    /** 当前可用的全部插件工具。禁用插件不贡献工具。 */
    fun enabledToolSpecs(): List<ToolSpec> =
        _plugins.value.filter { it.enabled }.flatMap { it.manifest.toToolSpecs() }

    fun reload() {
        runCatching { _plugins.value = readAll() }
    }

    private fun readAll(): List<InstalledPlugin> {
        val list = mutableListOf<InstalledPlugin>()
        helper.readableDatabase.rawQuery(
            "SELECT manifest, enabled, installed_at FROM plugins ORDER BY installed_at DESC",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val raw = cursor.getString(0) ?: continue
                val parsed = PluginManifestParser.parse(raw)
                if (parsed !is ManifestResult.Ok) continue
                list += InstalledPlugin(
                    manifest = parsed.manifest,
                    enabled = cursor.getInt(1) != 0,
                    installedAt = cursor.getLong(2),
                )
            }
        }
        return list
    }

    private fun PluginManifest.toJson(): String = JSONObject().apply {
        put("schemaVersion", 1)
        put("id", id)
        put("name", name)
        put("version", version)
        put("description", description)
        if (author.isNotBlank()) put("author", author)
        put("scopes", JSONArray(scopes))
        put("baseUrl", baseUrl)
        if (headers.isNotEmpty()) {
            put("headers", JSONObject().apply { headers.forEach { (k, v) -> put(k, v) } })
        }
        put(
            "tools",
            JSONArray().apply {
                tools.forEach { tool ->
                    put(
                        JSONObject().apply {
                            put("name", tool.name)
                            put("title", tool.title)
                            put("description", tool.description)
                            put("method", tool.method)
                            put("path", tool.path)
                            put("sideEffect", tool.sideEffect)
                            put("timeoutMs", tool.timeoutMs)
                            put("parameters", JSONObject(tool.parametersJson))
                        },
                    )
                }
            },
        )
    }.toString()

    private companion object {
        const val MaxPlugins = 30
    }
}
