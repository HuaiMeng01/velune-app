package com.veluneagent.core.data.im

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QQ 文本按字节截断。
 *
 * 这段逻辑的三种错法都不会抛异常，只会让对面收到一条奇怪的回复（整条发送失败 / 出现 `?`），
 * 所以每条都要有用例兜住：预算含后缀、多字节不被劈开、短文本不被无谓改动。
 */
class QqTextTest {

    private val suffixBytes = QqText.Suffix.toByteArray(Charsets.UTF_8).size

    private fun bytes(s: String): Int = s.toByteArray(Charsets.UTF_8).size

    @Test
    fun `短文本与空串原样返回`() {
        assertEquals("", QqText.truncate(""))
        assertEquals("你好", QqText.truncate("你好"))
        // 恰好等于上限时也不该被裁（边界不能多切一刀）
        val exact = "a".repeat(QqText.MaxBytes)
        assertEquals(exact, QqText.truncate(exact))
        // 超一个字节就开始裁，且裁完必须带后缀
        val over = QqText.truncate("a".repeat(QqText.MaxBytes + 1))
        assertTrue(over.endsWith(QqText.Suffix))
    }

    @Test
    fun `纯中文超长时输出不超过上限且带后缀`() {
        val text = "汉".repeat(2_000)          // 6000 字节
        val out = QqText.truncate(text)
        assertTrue("输出字节数必须 ≤ ${QqText.MaxBytes}，实际 ${bytes(out)}", bytes(out) <= QqText.MaxBytes)
        assertTrue(out.endsWith(QqText.Suffix))
        // 只保留了文字本体 + 后缀，没有别的附加
        val body = out.removeSuffix(QqText.Suffix)
        assertTrue(body.isNotEmpty())
        assertTrue(body.all { it == '汉' })
    }

    @Test
    fun `emoji 密集串不会被劈开 也不出现问号`() {
        val out = QqText.truncate("😀".repeat(2_000))   // 8000 字节
        assertTrue(bytes(out) <= QqText.MaxBytes)
        assertTrue(out.endsWith(QqText.Suffix))
        assertFalse("不能出现替换字符（说明代理对被劈开）", out.contains('?'))
        assertFalse("不能出现 U+FFFD", out.contains('\uFFFD'))

        val body = out.removeSuffix(QqText.Suffix)
        // 最后一个必须是**完整码点**：既不能是高代理位，也不能是孤立的低代理位
        val lastCp = body.codePointBefore(body.length)
        assertEquals(0x1F600, lastCp)
        assertFalse(Character.isHighSurrogate(body.last()))
        // 正向复核：正文正好是若干个完整 emoji（没有混进半个字符）
        assertTrue(body == "😀".repeat(body.codePointCount(0, body.length)))
    }

    @Test
    fun `后缀在预算内 输出永远不超过 MaxBytes`() {
        // 四种典型输入：ASCII、中文、emoji、中英 emoji 混合
        val samples = listOf(
            "a".repeat(10_000),
            "汉".repeat(10_000),
            "😀".repeat(10_000),
            "汉a😀".repeat(5_000),
        )
        for (text in samples) {
            val out = QqText.truncate(text)
            assertTrue("超预算：${bytes(out)} > ${QqText.MaxBytes}", bytes(out) <= QqText.MaxBytes)
            assertTrue(out.endsWith(QqText.Suffix))
            // 正文部分不能超过 1500 - 后缀
            assertTrue(bytes(out.removeSuffix(QqText.Suffix)) <= QqText.MaxBytes - suffixBytes)
        }
    }

    @Test
    fun `裁剪点落在多字节字符边界上`() {
        // 构造「正好在预算边界处放不下下一个字符」的串：
        // 489 个汉字 = 1467 字节（预算 1468），下一个是 4 字节 emoji → 放不下，必须整块丢弃。
        // 后面再补一段让总长度超过 1500，否则 truncate 会原样返回、测不到裁剪点。
        val filler = "汉".repeat((QqText.MaxBytes - suffixBytes) / 3)
        val out = QqText.truncate(filler + "😀" + "汉".repeat(100))
        val body = out.removeSuffix(QqText.Suffix)
        assertFalse("emoji 应当整块被丢掉，不能出现半个", body.contains('\uD83D'))
        assertTrue(body.all { it == '汉' })
        assertEquals(filler, body)          // 裁剪点正好停在 emoji 之前
        assertTrue(bytes(out) <= QqText.MaxBytes)
    }
}
