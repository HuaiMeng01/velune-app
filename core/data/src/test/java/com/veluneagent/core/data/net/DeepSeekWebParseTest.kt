package com.veluneagent.core.data.net

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索响应解析。
 *
 * 这些用例守的是两类错：
 * 1. 字段缺失/为 null 时崩掉整次搜索（官方 schema 里 site_name / date 明确可为 null）；
 * 2. 没有 url 的结果被当成可用来源（模型会把空链接当引用发给用户）。
 */
class DeepSeekWebParseTest {

    @Test
    fun `解析完整结果`() {
        val json = JSONObject(
            """
            {"results":[
              {"rerank_score":2.4,"title":"DeepSeek V4 发布","url":"https://example.com/a",
               "snippet":"短摘要","summary":"长摘要","content":"","site_name":"Example",
               "site_icon":"https://example.com/favicon.ico","date":"2026-06-01T00:00:00+00:00",
               "authority":3}
            ]}
            """.trimIndent(),
        )
        val hits = DeepSeekWeb.parseSearch(json)
        assertEquals(1, hits.size)
        val hit = hits.first()
        assertEquals("DeepSeek V4 发布", hit.title)
        assertEquals("https://example.com/a", hit.url)
        assertEquals("长摘要", hit.summary)
        assertEquals("Example", hit.siteName)
        assertEquals("2026-06-01T00:00:00+00:00", hit.date)
        assertEquals(3, hit.authority)
        assertEquals(2.4, hit.score, 0.001)
    }

    @Test
    fun `site_name 与 date 为 null 时不报错且落为 null`() {
        val json = JSONObject(
            """{"results":[{"rerank_score":1.0,"title":"t","url":"https://a.com","summary":"s","snippet":"x","content":"","site_name":null,"site_icon":null,"date":null,"authority":-1}]}""",
        )
        val hit = DeepSeekWeb.parseSearch(json).single()
        assertNull(hit.siteName)
        assertNull(hit.date)
        assertEquals(-1, hit.authority)
    }

    @Test
    fun `没有 url 的条目被丢弃`() {
        val json = JSONObject(
            """{"results":[
              {"title":"无链接","url":"","summary":"s"},
              {"title":"有链接","url":"https://a.com","summary":"s"}
            ]}""",
        )
        val hits = DeepSeekWeb.parseSearch(json)
        assertEquals(1, hits.size)
        assertEquals("有链接", hits.first().title)
    }

    @Test
    fun `空结果与缺失字段都返回空列表`() {
        assertTrue(DeepSeekWeb.parseSearch(JSONObject("""{"results":[]}""")).isEmpty())
        assertTrue(DeepSeekWeb.parseSearch(JSONObject("{}")).isEmpty())
    }

    @Test
    fun `实体转义字符不会破坏解析`() {
        val json = JSONObject(
            """{"results":[{"title":"含 \"引号\" 与 \\ 斜杠","url":"https://a.com/qs?x=1&y=2","summary":"多行\n摘要"}]}""",
        )
        val hit = DeepSeekWeb.parseSearch(json).single()
        assertTrue(hit.title.contains("引号"))
        assertTrue(hit.url.contains("&"))
        assertTrue(hit.summary.contains("\n"))
    }
}
