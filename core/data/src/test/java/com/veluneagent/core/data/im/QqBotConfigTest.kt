package com.veluneagent.core.data.im

import com.veluneagent.core.data.ssh.Ssh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** QQ 机器人地址归一化 + SSH 配置完整性判断。两个都会直接决定「点了发送/测试有没有反应」。 */
class QqBotConfigTest {

    @Test
    fun `只填主机端口时补 http`() {
        assertEquals("http://192.168.1.10:3000", QqBot.normalizeBaseUrl("192.168.1.10:3000"))
        assertEquals("http://localhost:3000", QqBot.normalizeBaseUrl(" localhost:3000 "))
    }

    @Test
    fun `已带协议与末尾斜杠时只去斜杠`() {
        assertEquals("https://bot.example.com", QqBot.normalizeBaseUrl("https://bot.example.com/"))
        assertEquals("http://10.0.0.2:5700", QqBot.normalizeBaseUrl("http://10.0.0.2:5700/"))
    }

    @Test
    fun `空字符串保持为空`() {
        assertEquals("", QqBot.normalizeBaseUrl("   "))
        assertEquals("", QqBot.normalizeWsUrl("   "))
    }

    @Test
    fun `WS 地址归一化`() {
        // 最常填的形式：只有主机端口
        assertEquals("ws://192.168.1.10:3001", QqBot.normalizeWsUrl("192.168.1.10:3001"))
        assertEquals("ws://localhost:3001", QqBot.normalizeWsUrl(" localhost:3001 "))
        // 已经是 WS 就原样（路径有含义，不能去尾斜杠）
        assertEquals("ws://10.0.0.2:3001/onebot/v11/ws", QqBot.normalizeWsUrl("ws://10.0.0.2:3001/onebot/v11/ws"))
        assertEquals("wss://bot.example.com/ws", QqBot.normalizeWsUrl("wss://bot.example.com/ws"))
        // 把 REST 地址粘进 WS 框：换 scheme，不能原样交给 OkHttp（会抛异常）
        assertEquals("ws://10.0.0.2:5700", QqBot.normalizeWsUrl("http://10.0.0.2:5700"))
        assertEquals("wss://bot.example.com/ws", QqBot.normalizeWsUrl("https://bot.example.com/ws"))
    }

    @Test
    fun `OneBot 模式要求地址与目标号码`() {
        fun cfg(base: String, to: String) = QqBot.Config(
            mode = QqBot.Mode.OneBot,
            baseUrl = base,
            token = "",
            target = QqBot.Target.Private,
            targetId = to,
            displayName = "",
        )
        assertFalse(cfg("", "10001").configured)
        assertFalse(cfg("http://a", "").configured)
        assertTrue(cfg("http://a", "10001").configured)
        // OneBot 只有地址没有 WS 时能发不能收
        assertTrue(cfg("http://a", "10001").configured)
        assertFalse(cfg("http://a", "10001").canReceive)
    }

    @Test
    fun `官方模式只要求凭证 不需要任何外部设备地址`() {
        val official = QqBot.Config(
            mode = QqBot.Mode.Official,
            appId = "102000000",
            appSecret = "secret",
            baseUrl = "",
            token = "",
            target = QqBot.Target.Private,
            targetId = "",
            displayName = "",
        )
        // 关键断言：官方模式没有任何局域网地址也能收发
        assertTrue(official.configured)
        assertTrue(official.canReceive)
        assertFalse(official.copy(appSecret = "").configured)
        assertFalse(official.copy(appId = "").canReceive)
    }

    @Test
    fun `SSH 配置要求地址用户与至少一种凭据`() {
        assertFalse(Ssh.Host(host = "1.2.3.4", user = "root").configured)
        assertFalse(Ssh.Host(host = "", user = "root", password = "x").configured)
        assertTrue(Ssh.Host(host = "1.2.3.4", user = "root", password = "x").configured)
        assertTrue(
            Ssh.Host(host = "1.2.3.4", user = "root", privateKey = "-----BEGIN").configured,
        )
    }

    @Test
    fun `绑定会话不影响是否可用`() {
        val base = QqBot.Config(
            mode = QqBot.Mode.Official,
            appId = "102000000",
            appSecret = "secret",
            baseUrl = "",
            token = "",
            target = QqBot.Target.Private,
            targetId = "",
            displayName = "",
        )
        // 0 = 自动（QQ 消息）
        assertEquals(0L, base.conversationId)

        // 绑定一条已有会话既不构成"配置完整"，也不影响能否收消息：
        // 它只是"回复写进哪"，漏填/填错都不该把机器人卡在未配置状态
        val bound = base.copy(conversationId = 12L)
        assertEquals(12L, bound.conversationId)
        assertEquals(base.configured, bound.configured)
        assertEquals(base.canReceive, bound.canReceive)
    }
}
