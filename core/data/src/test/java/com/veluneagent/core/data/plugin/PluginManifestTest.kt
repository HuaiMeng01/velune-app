package com.veluneagent.core.data.plugin

import com.veluneagent.core.data.agent.PluginUrlBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginManifestTest {

    private val validManifest = """
        {
          "schemaVersion": 1,
          "id": "com.example.weather",
          "name": {"zh-CN": "天气", "default": "Weather"},
          "version": "1.0.0",
          "description": "查询城市天气",
          "scopes": ["net.domain.api.weather.example"],
          "baseUrl": "https://api.weather.example",
          "tools": [
            {
              "name": "get_forecast",
              "title": "查天气",
              "description": "查询指定城市未来几天的天气，用户问天气时调用",
              "sideEffect": false,
              "timeoutMs": 8000,
              "method": "GET",
              "path": "/v1/forecast",
              "parameters": {
                "type": "object",
                "properties": {"city": {"type": "string"}},
                "required": ["city"]
              }
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `合法清单通过校验`() {
        val result = PluginManifestParser.parse(validManifest)
        assertTrue(result is ManifestResult.Ok)
        val manifest = (result as ManifestResult.Ok).manifest
        assertEquals("com.example.weather", manifest.id)
        assertEquals("天气", manifest.name)
        assertEquals(1, manifest.tools.size)
        assertEquals(
            "com_example_weather__get_forecast",
            manifest.toToolSpecs().single().name,
        )
    }

    @Test
    fun `http 地址被拒绝`() {
        val raw = validManifest.replace("https://api.weather.example", "http://api.weather.example")
        val errors = errorsOf(raw)
        assertTrue(errors.any { it.contains("https") })
    }

    @Test
    fun `域名未被 scope 覆盖时拒绝`() {
        val raw = validManifest.replace(
            """"scopes": ["net.domain.api.weather.example"]""",
            """"scopes": ["net.domain.api.other.example"]""",
        )
        assertTrue(errorsOf(raw).any { it.contains("未被 scopes 覆盖") })
    }

    @Test
    fun `Lite 不支持的高危 scope 明确拒绝`() {
        val raw = validManifest.replace(
            """"scopes": ["net.domain.api.weather.example"]""",
            """"scopes": ["net.domain.api.weather.example", "chat.read"]""",
        )
        val errors = errorsOf(raw)
        assertTrue(errors.any { it.contains("暂不支持") && it.contains("chat.read") })
    }

    @Test
    fun `工具名不符合规范时拒绝`() {
        val raw = validManifest.replace("\"get_forecast\"", "\"Get Forecast\"")
        assertTrue(errorsOf(raw).any { it.contains("name 必须匹配") })
    }

    @Test
    fun `清单里不允许携带 Authorization 头`() {
        val raw = validManifest.replace(
            """"baseUrl": "https://api.weather.example",""",
            """"baseUrl": "https://api.weather.example",
              "headers": {"Authorization": "Bearer sk-xxx"},""",
        )
        assertTrue(errorsOf(raw).any { it.contains("凭据必须走密钥托管") })
    }

    @Test
    fun `路径穿越被拒绝`() {
        val raw = validManifest.replace("\"path\": \"/v1/forecast\"", "\"path\": \"/../admin\"")
        assertTrue(errorsOf(raw).any { it.contains("path 必须以 / 开头") })
    }

    @Test
    fun `工具缺少描述时拒绝`() {
        val raw = validManifest.replace(
            """"description": "查询指定城市未来几天的天气，用户问天气时调用",""",
            """"description": "",""",
        )
        assertTrue(errorsOf(raw).any { it.contains("description 必填") })
    }

    @Test
    fun `参数 schema 必须是 object`() {
        val raw = validManifest.replace("\"type\": \"object\"", "\"type\": \"array\"")
        assertTrue(errorsOf(raw).any { it.contains("JSON Schema") })
    }

    @Test
    fun `空清单给出可读错误`() {
        val result = PluginManifestParser.parse("")
        assertTrue(result is ManifestResult.Invalid)
        assertTrue((result as ManifestResult.Invalid).errors.single().contains("为空"))
    }

    @Test
    fun `非法 JSON 不抛异常`() {
        assertTrue(PluginManifestParser.parse("{oops") is ManifestResult.Invalid)
    }

    @Test
    fun `域名匹配支持一级通配且不越级`() {
        val scopes = listOf("net.domain.*.example.com")
        assertTrue(PluginHosts.isHostAllowed("api.example.com", scopes))
        assertTrue(!PluginHosts.isHostAllowed("a.b.example.com", scopes))
        assertTrue(!PluginHosts.isHostAllowed("example.com", scopes))
        assertTrue(PluginHosts.isHostAllowed("anything.host", listOf("net.http")))
        assertTrue(PluginHosts.isValidScope("net.domain.api.example.com"))
        assertTrue(!PluginHosts.isValidScope("net.domain."))
        assertTrue(!PluginHosts.isValidScope("chat.read"))
    }

    @Test
    fun `URL 拼装会编码参数并忽略占位参数`() {
        val args = org.json.JSONObject("""{"city":"北京市/东城","days":3}""")
        val url = PluginUrlBuilder.build(
            baseUrl = "https://api.weather.example",
            path = "/v1/{city}/forecast",
            method = "GET",
            args = args,
        )
        val resolved = url!!
        assertTrue(resolved.startsWith("https://api.weather.example/v1/"))
        assertTrue(resolved.contains("%E5%8C%97%E4%BA%AC%E5%B8%82%2F%E4%B8%9C%E5%9F%8E"))
        assertTrue(resolved.endsWith("?days=3"))
    }

    @Test
    fun `路径穿越无法通过拼装绕开`() {
        val url = PluginUrlBuilder.build(
            baseUrl = "https://api.weather.example",
            path = "/v1/../../admin",
            method = "GET",
            args = org.json.JSONObject("{}"),
        )
        assertTrue(url == null)
    }

    private fun errorsOf(raw: String): List<String> {
        val result = PluginManifestParser.parse(raw)
        assertTrue("预期校验失败，实际通过：$result", result is ManifestResult.Invalid)
        return (result as ManifestResult.Invalid).errors
    }
}
