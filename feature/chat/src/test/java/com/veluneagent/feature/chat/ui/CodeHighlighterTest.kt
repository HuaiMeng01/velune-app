package com.veluneagent.feature.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeHighlighterTest {

    private fun kinds(code: String, lang: String): List<CodeTokenKind> =
        CodeHighlighter.spans(code, lang).map { it.kind }

    @Test
    fun `kotlin 关键字与字符串分别着色`() {
        val spans = CodeHighlighter.spans("""val name = "Velune"""", "kotlin")
        assertTrue(spans.any { it.kind == CodeTokenKind.Keyword })
        assertTrue(spans.any { it.kind == CodeTokenKind.String })
    }

    @Test
    fun `行注释里的引号不产生字符串`() {
        val spans = CodeHighlighter.spans("""// 这里的 "引号" 不算字符串""", "kotlin")
        assertEquals(listOf(CodeTokenKind.Comment), kinds("""// 这里的 "引号" 不算字符串""", "kotlin"))
        assertTrue(spans.first().end == ("""// 这里的 "引号" 不算字符串""").length)
    }

    @Test
    fun `python 用井号注释`() {
        val spans = CodeHighlighter.spans("# 注释\ndef foo():", "python")
        assertTrue(spans.first().kind == CodeTokenKind.Comment)
        assertTrue(spans.any { it.kind == CodeTokenKind.Keyword })
    }

    @Test
    fun `数字被识别`() {
        val spans = CodeHighlighter.spans("count = 42", "python")
        assertTrue(spans.any { it.kind == CodeTokenKind.Number })
    }

    @Test
    fun `未知语言仍能识别字符串与数字`() {
        val spans = CodeHighlighter.spans("""x = "a" 12""", "unknownlang")
        assertTrue(spans.any { it.kind == CodeTokenKind.String })
        assertTrue(spans.any { it.kind == CodeTokenKind.Number })
        assertTrue(spans.none { it.kind == CodeTokenKind.Keyword })
    }

    @Test
    fun `别名映射到大类`() {
        assertEquals("kotlin", CodeHighlighter.normalize("kt"))
        assertEquals("python", CodeHighlighter.normalize("PY"))
        assertEquals("javascript", CodeHighlighter.normalize("ts"))
        assertEquals("shell", CodeHighlighter.normalize("bash"))
    }

    @Test
    fun `块注释整段覆盖`() {
        val code = "/* a\nb */ val x = 1"
        val spans = CodeHighlighter.spans(code, "kotlin")
        assertTrue(spans.first().kind == CodeTokenKind.Comment)
        assertTrue(code.substring(spans.first().start, spans.first().end).contains("*/"))
    }

    @Test
    fun `未闭合字符串不会吃掉整段`() {
        val code = "val s = \"unclosed"
        val spans = CodeHighlighter.spans(code, "kotlin")
        val stringSpan = spans.first { it.kind == CodeTokenKind.String }
        assertEquals(code.length, stringSpan.end)
    }
}
