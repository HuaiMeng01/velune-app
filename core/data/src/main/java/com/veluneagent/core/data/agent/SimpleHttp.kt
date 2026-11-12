package com.veluneagent.core.data.agent

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class HttpOutcome(
    val code: Int,
    val body: String,
    val error: String? = null,
    /**
     * 只保留白名单响应头（content-type / mcp-session-id）。
     *
     * 不能直接 `headers.toMap()`：那会把 Set-Cookie 之类的凭据一并带出来，
     * 而这些值一旦进了工具结果，就等于写进了对话上下文。
     */
    val headers: Map<String, String> = emptyMap(),
) {
    val ok: Boolean get() = error == null && code in 200..299
}

/**
 * Agent 侧唯一的出网通道。
 *
 * 三条硬规则：
 * 1. 只允许 https（本机调试放行 http://localhost）；
 * 2. 不跟随重定向 —— 跨域跳转是绕过域名白名单最常见的手法；
 * 3. 响应体截断到 32KB，避免一个超大响应把内存和上下文一起打爆。
 *
 * 密钥不写日志：这里没有任何日志调用，错误信息只带状态码与 URL 的 host。
 */
object SimpleHttp {

    private const val MaxResponseBytes = 32_768

    /** 允许回传的响应头白名单。 */
    private val KeepResponseHeaders = listOf("content-type", "mcp-session-id")

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val client by lazy {
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        jsonBody: String? = null,
        timeoutMs: Int = 10_000,
        /**
         * 允许携带 Authorization 头。
         *
         * 插件永远不能开这个开关：插件的网络请求由第三方作者定义，放行等于给它们一把
         * 通往用户凭据的钥匙。MCP 服务器是用户自己填的地址与令牌，属于同一信任级别。
         */
        allowAuthHeaders: Boolean = false,
        /** 响应体上限。MCP 的 tools/call 可能返回整页网页，32KB 不够用。 */
        maxBytes: Long = MaxResponseBytes.toLong(),
    ): HttpOutcome {
        val uri = runCatching { java.net.URI(url) }.getOrElse {
            return HttpOutcome(0, "", "URL 不合法：${it.message}")
        }
        val scheme = uri.scheme?.lowercase()
        val host = uri.host.orEmpty()
        val isLocal = host == "localhost" || host == "127.0.0.1"
        if (scheme != "https" && !(scheme == "http" && isLocal)) {
            return HttpOutcome(0, "", "只允许 https 请求（本机调试可用 http://localhost）")
        }

        val builder = Request.Builder().url(url)
        headers.forEach { (key, value) ->
            val isAuth = key.equals("authorization", true)
            // Cookie 永远不放行：会话 Cookie 比 Bearer 更难撤销，泄露后果也更重
            val isCookie = key.equals("cookie", true)
            if (!isCookie && (allowAuthHeaders || !isAuth)) {
                runCatching { builder.addHeader(key, value) }
            }
        }
        val body = when {
            jsonBody == null -> null
            method == "GET" || method == "DELETE" -> null
            else -> jsonBody.toRequestBody(jsonMedia)
        }
        builder.method(method, body)

        val call = client.newCall(builder.build())
        call.timeout().timeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        return try {
            call.execute().use { response ->
                val text = response.body?.source()?.let { source ->
                    source.readUtf8Limited(maxBytes)
                }.orEmpty()
                if (response.code in 300..399) {
                    HttpOutcome(
                        response.code,
                        text,
                        "服务端返回 ${response.code} 重定向，出于安全策略不予跟随",
                    )
                } else {
                    HttpOutcome(
                        code = response.code,
                        body = text,
                        // 只挑出协议确实需要的两个头
                        headers = KeepResponseHeaders.mapNotNull { name ->
                            response.header(name)?.let { name to it }
                        }.toMap(),
                    )
                }
            }
        } catch (t: Throwable) {
            HttpOutcome(0, "", t.message ?: t.javaClass.simpleName)
        }
    }
}

/** 读取至多 limit 字节的 UTF-8 文本，超出部分直接丢弃（工具结果不需要全文）。 */
private fun okio.BufferedSource.readUtf8Limited(limit: Long): String {
    val builder = StringBuilder()
    var remaining = limit
    while (remaining > 0) {
        val line = readUtf8Line() ?: break
        builder.append(line).append('\n')
        remaining -= line.length + 1L
        if (remaining <= 0) break
    }
    return builder.toString()
}

/** 工具结果的上下文裁剪：短结果全文，长结果头 + 尾。 */
object ToolOutput {
    private const val HeadChars = 2_400
    private const val TailChars = 1_200

    fun clip(text: String): String {
        if (text.length <= HeadChars + TailChars + 200) return text
        val omitted = text.length - HeadChars - TailChars
        return text.take(HeadChars) +
            "\n…（中间省略 $omitted 字，完整内容见运行记录）…\n" +
            text.takeLast(TailChars)
    }
}
