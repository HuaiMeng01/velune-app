package com.veluneagent.core.data.plugin

import com.veluneagent.core.data.agent.SimpleHttp
import com.veluneagent.core.data.system.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 安装入口的统一实现。
 *
 * 两条路径（用户手动装 / Agent 自己装）必须走同一段校验代码 ——
 * 只在 UI 侧校验、Agent 侧放行，等于给自己留了一个后门。
 */
object PluginInstaller {

    /**
     * 内置示例插件：指向 httpbin.org 的回显接口，用来验证「安装 → 审批 → 调用 → 结果回灌」整条链路。
     * 它只发一个 GET，不携带任何用户数据，装错了删掉即可。
     */
    /** 示例插件 id：UI 用它判断「是否已安装」。 */
    const val SampleId = "com.velune.sample.echo"

    val SampleManifest: String = """
        {
          "schemaVersion": 1,
          "id": "com.velune.sample.echo",
          "name": "示例 · HTTP 回显",
          "version": "1.0.0",
          "description": "演示用：把你的参数原样回显，用来验证插件链路是否打通",
          "scopes": ["net.domain.httpbin.org"],
          "scopesRationale": {"net.domain.httpbin.org": "访问 httpbin.org 的回显接口"},
          "baseUrl": "https://httpbin.org",
          "tools": [
            {
              "name": "echo",
              "title": "回显参数",
              "description": "把参数原样返回，用于验证插件安装与调用链路。用户要求测试插件或回显内容时调用。",
              "sideEffect": false,
              "timeoutMs": 8000,
              "method": "GET",
              "path": "/get",
              "parameters": {
                "type": "object",
                "properties": {"text": {"type": "string", "description": "要回显的文本"}},
                "required": ["text"]
              }
            }
          ]
        }
    """.trimIndent()

    suspend fun installSample(store: PluginStore): Outcome = installFromJson(store, SampleManifest)


    data class Outcome(val ok: Boolean, val message: String, val pluginId: String? = null)

    suspend fun installFromJson(store: PluginStore, raw: String): Outcome = withContext(Dispatchers.IO) {
        when (val parsed = PluginManifestParser.parse(raw)) {
            is ManifestResult.Invalid -> Outcome(false, parsed.errors.joinToString("；"))
            is ManifestResult.Ok -> when (val result = store.install(parsed.manifest)) {
                is InstallResult.Invalid -> Outcome(false, result.errors.joinToString("；"))
                is InstallResult.Ok -> Outcome(
                    true,
                    UiText.tf(
                        "已安装 %s（%d 个工具）",
                        arrayOf<Any?>(result.plugin.manifest.name, result.plugin.manifest.tools.size),
                    ),
                    result.plugin.id,
                )
            }
        }
    }

    suspend fun installFromUrl(store: PluginStore, url: String): Outcome = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("https://") && !trimmed.startsWith("http://localhost")) {
            return@withContext Outcome(false, UiText.t("只支持 https 清单地址（本机调试可用 http://localhost）"))
        }
        val response = SimpleHttp.request(
            method = "GET",
            url = trimmed,
            headers = mapOf("Accept" to "application/json"),
            timeoutMs = 10_000,
        )
        if (!response.ok) {
            return@withContext Outcome(
                false,
                UiText.tf("下载清单失败：%s", arrayOf(response.error ?: "HTTP ${response.code}")),
            )
        }
        installFromJson(store, response.body)
    }
}
