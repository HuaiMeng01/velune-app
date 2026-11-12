package com.veluneagent.feature.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun `标题与段落分开成块`() {
        val blocks = parseMarkdown("## 标题\n\n正文一行\n正文二行")
        assertEquals(2, blocks.size)
        assertEquals(MdBlock.Heading(2, "标题"), blocks[0])
        assertEquals(MdBlock.Paragraph("正文一行\n正文二行"), blocks[1])
    }

    @Test
    fun `未闭合的围栏代码按到结尾处理`() {
        val blocks = parseMarkdown("```kotlin\nval a = 1\n")
        assertEquals(1, blocks.size)
        val code = blocks[0] as MdBlock.Code
        assertEquals("kotlin", code.language)
        assertTrue(code.code.contains("val a = 1"))
    }

    @Test
    fun `有序列表保留起始序号`() {
        val blocks = parseMarkdown("3. 第三\n4. 第四")
        val list = blocks[0] as MdBlock.ListBlock
        assertTrue(list.ordered)
        assertEquals(3, list.start)
        assertEquals(listOf("第三", "第四"), list.items)
    }

    @Test
    fun `无序列表的星号不会被当成斜体`() {
        val blocks = parseMarkdown("* 一条\n* 二条")
        val list = blocks[0] as MdBlock.ListBlock
        assertEquals(listOf("一条", "二条"), list.items)
    }

    @Test
    fun `表格识别表头分隔行`() {
        val blocks = parseMarkdown("| 文件 | 行数 |\n|---|---|\n| a.pdf | 18 |")
        val table = blocks[0] as MdBlock.Table
        assertEquals(2, table.rows.size)
        assertEquals(listOf("文件", "行数"), table.rows[0])
        assertEquals(listOf("a.pdf", "18"), table.rows[1])
    }

    @Test
    fun `引用与分隔线`() {
        val blocks = parseMarkdown("> 引用一行\n> 引用二行\n\n---")
        assertEquals(MdBlock.Quote("引用一行\n引用二行"), blocks[0])
        assertEquals(MdBlock.Divider, blocks[1])
    }

    @Test
    fun `空输入不产生块`() {
        assertTrue(parseMarkdown("").isEmpty())
        assertTrue(parseMarkdown("\n\n").isEmpty())
    }
}
