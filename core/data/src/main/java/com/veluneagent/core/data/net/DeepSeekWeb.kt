package com.veluneagent.core.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * DeepSeek 的网页搜索与抓取（官方文档核对日期 2026-10-21）。
 *
 * - Search：`POST https://api.deepseek.com/v1/tools/search`
 * - Fetch：`POST https://api.deepseek.com/v1/tools/fetch`
 *
 * 鉴权与 chat 完全相同（`Authorization: Bearer <key>`），但**计费独立**：
 * 每 1000 次成功请求 5 元，只对 HTTP 200 且 results 非空（或 fetch 返回正文）的请求计费。
 * 所以这个能力必须走「用户显式要求联网」或模型明确判断需要实时信息时才调用，
 * 不能塞进每轮对话的默认流程。
 *
 * 限速：Search 100 QPS、Fetch 25 QPS（账号级）。超限返回 429，需要退避重试。
 */
object DeepSeekWeb {

    const val BaseUrl = "https://api.deepseek.com"

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    data class SearchHit(
        val title: String,
        val url: String,
        val summary: String,
        val snippet: String,
        val siteName: String?,
        val date: String?,
        val authority: Int,
        val score: Double,
    )

    data class WebPage(val title: String, val content: String)

    /**
     * 搜索网页。
     *
     * @param count 1..50，默认 10；调用方应按上下文预算调小（结果 summary 每条 600–800 字）
     */
    suspend fun search(
        apiKey: String,
        query: String,
        count: Int = 5,
        needContent: Boolean = false,
        originalQuestion: String? = null,
    ): Result<List<SearchHit>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext Result.failure(IllegalArgumentException("搜索词不能为空"))
        if (trimmed.length > 128) {
            return@withContext Result.failure(IllegalArgumentException("搜索词最多 128 个字符"))
        }

        val body = JSONObject().apply {
            put("query", trimmed)
            put("count", count.coerceIn(1, 50))
            put("need_content", needContent)
            originalQuestion?.trim()?.takeIf { it.isNotEmpty() && it.length <= 128 }?.let {
                put("ori_question", it)
            }
        }.toString()

        post("$BaseUrl/v1/tools/search", apiKey, body).map { json ->
            parseSearch(json)
        }
    }

    /** 抓取单个网页并转成 Markdown。 */
    suspend fun fetch(apiKey: String, url: String): Result<WebPage> = withContext(Dispatchers.IO) {
        val target = url.trim()
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            return@withContext Result.failure(IllegalArgumentException("只支持 http/https 链接"))
        }
        val body = JSONObject().put("url", target).toString()
        post("$BaseUrl/v1/tools/fetch", apiKey, body).map { json ->
            WebPage(
                title = json.optString("title").orEmpty(),
                content = json.optString("content").orEmpty(),
            )
        }
    }

    /**
     * 带退避的 POST。
     *
     * 文档明确：408 / 429 / 5xx 可以等一会儿重试；402 是余额不足，其它 4xx 重试没有意义。
     * 这里最多重试 2 次（共 3 次请求），退避 500ms → 1200ms 并带抖动，避免多设备同频重试。
     */
    private suspend fun post(path: String, apiKey: String, body: String): Result<JSONObject> {
        val request = Request.Builder()
            .url(path)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody(jsonMedia))
            .build()

        var lastError: String = "请求失败"
        repeat(MaxAttempts) { attempt ->
            val result = runCatching {
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    val code = response.code
                    if (response.isSuccessful) {
                        return@use Result.success(JSONObject(text))
                    }
                    val message = parseError(text).ifBlank { "HTTP $code" }
                    Result.failure<JSONObject>(
                        SearchHttpException(code, message),
                    )
                }
            }.getOrElse { Result.failure(it) }

            result.onSuccess { return Result.success(it) }
            val error = result.exceptionOrNull()
            val code = (error as? SearchHttpException)?.code
            lastError = error?.message ?: "请求失败"

            val retryable = code == 408 || code == 429 || code == null || code in 500..599
            if (!retryable || attempt == MaxAttempts - 1) {
                return Result.failure(IllegalStateException(lastError))
            }
            val backoff = if (code == 429) 1_200L else 500L
            delay(backoff * (attempt + 1) + Random.nextLong(0, 300))
        }
        return Result.failure(IllegalStateException(lastError))
    }

    private fun parseError(body: String): String = runCatching {
        JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
    }.getOrDefault("")

    /** 纯函数：便于单测。字段缺失时降级为空值，不让一条脏数据毁掉整次搜索。 */
    internal fun parseSearch(json: JSONObject): List<SearchHit> {
        val results = json.optJSONArray("results") ?: return emptyList()
        return buildList {
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val url = item.optString("url").orEmpty()
                if (url.isEmpty()) continue
                add(
                    SearchHit(
                        title = item.optString("title").orEmpty(),
                        url = url,
                        summary = item.optString("summary").orEmpty(),
                        snippet = item.optString("snippet").orEmpty(),
                        siteName = item.optString("site_name").takeIf { it.isNotBlank() && it != "null" },
                        date = item.optString("date").takeIf { it.isNotBlank() && it != "null" },
                        authority = item.optInt("authority", -1),
                        score = item.optDouble("rerank_score", 0.0),
                    ),
                )
            }
        }
    }

    private const val MaxAttempts = 3
}

/** 带上 HTTP 状态码，供重试判断使用。 */
internal class SearchHttpException(val code: Int, message: String) : Exception(message)
