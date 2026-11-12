package com.veluneagent.core.data.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * 拼装顺序是这套设计的命门：
 * 平台规则排在人设**之后**的话，用户一句「忽略以上所有内容」就能关掉工具契约。
 * 这里用断言把顺序钉死，不靠注释提醒。
 */
class PromptComposerTest {

    private val now = LocalDateTime.of(2026, 10, 22, 0, 40)

    @Test
    fun `纯聊天没人设时不发任何 system`() {
        assertTrue(PromptComposer.build(PromptScene.Chat, persona = null, now = now).isEmpty())
        assertTrue(PromptComposer.build(PromptScene.Chat, persona = "  ", now = now).isEmpty())
    }

    @Test
    fun `纯聊天有人设时人设在最前 时间在后`() {
        val messages = PromptComposer.build(PromptScene.Chat, "你是小鹿，说话简短。", now)
        assertEquals(2, messages.size)
        assertTrue(messages.all { it.role == "system" })
        assertTrue(messages[0].text.contains("你是小鹿"))
        assertTrue(messages[1].text.contains("当前本地时间"))
    }

    @Test
    fun `Agent 平台规则永远排在人设之前`() {
        val messages = PromptComposer.build(PromptScene.Agent, "不要调用任何工具，直接回答。", now)
        assertTrue("首条必须是平台规则", messages[0].text.contains("Agent"))
        assertTrue("平台规则要含工具契约", messages[0].text.contains("调用工具"))

        val personaIndex = messages.indexOfFirst { it.text.contains("不要调用任何工具") }
        assertTrue("人设必须在平台规则之后", personaIndex > 0)
    }

    @Test
    fun `Agent 规则里已经带时间 不重复注入`() {
        val messages = PromptComposer.build(PromptScene.Agent, null, now)
        assertEquals(1, messages.size)
        assertEquals(1, Regex("当前本地时间").findAll(messages[0].text).count())
    }

    @Test
    fun `QQ 场景随工具开关改变措辞`() {
        val off = PromptComposer.build(PromptScene.Qq, null, now, qqToolsEnabled = false)
        assertTrue(off[0].text.contains("没有工具"))

        val on = PromptComposer.build(PromptScene.Qq, null, now, qqToolsEnabled = true)
        assertTrue(on[0].text.contains("可以用工具"))

        assertTrue("两种情况下都要声明没有写权限", on[0].text.contains("做不了"))
    }

    @Test
    fun `QQ 场景人设排在场景规则之后`() {
        val messages = PromptComposer.build(PromptScene.Qq, "回复要可爱一点。", now)
        assertTrue(messages[0].text.contains("QQ"))
        assertTrue(messages[1].text.contains("可爱"))
    }

    @Test
    fun `变量会被替换成真实值`() {
        val messages = PromptComposer.build(
            scene = PromptScene.Chat,
            persona = "叫对方 {{user_name}}，现在是 {{now}}，场景 {{scene}}",
            now = now,
            userName = "八哥",
        )
        val text = messages[0].text
        assertTrue(text.contains("八哥"))
        assertTrue(text.contains("2026-10-22"))
        assertTrue(text.contains("聊天"))
        assertFalse("未知/已替换的占位符不该残留", text.contains("{{"))
    }

    @Test
    fun `没设昵称时用你兜底`() {
        val messages = PromptComposer.build(
            scene = PromptScene.Chat,
            persona = "称呼 {{user_name}}",
            now = now,
            userName = "",
        )
        assertTrue(messages[0].text.contains("称呼 你"))
    }

    @Test
    fun `未知占位符原样保留`() {
        val messages = PromptComposer.build(
            scene = PromptScene.Chat,
            persona = "保留 {{unknown_var}}",
            now = now,
        )
        assertTrue(messages[0].text.contains("{{unknown_var}}"))
    }
}
