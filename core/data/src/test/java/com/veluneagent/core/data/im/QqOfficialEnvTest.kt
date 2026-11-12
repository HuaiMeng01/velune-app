package com.veluneagent.core.data.im

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 官方 Bot 的运行环境。
 *
 * 这不是"可选项"级别的配置：新增机器人默认强制 IP 白名单，而手机的公网 IP 每次切网都会变，
 * 所以纯手机方案只能连沙箱（官方规则：IP 白名单只作用于正式环境）。
 * 域名写错的表现是"能连上但一条消息都收不到"，不会报错，所以这里锁死断言。
 */
class QqOfficialEnvTest {

    @Test
    fun `沙箱与正式用不同域名`() {
        assertEquals("https://sandbox.api.sgroup.qq.com", QqOfficial.apiBase(sandbox = true))
        assertEquals("https://api.sgroup.qq.com", QqOfficial.apiBase(sandbox = false))
    }

    @Test
    fun `官方模式默认走沙箱`() {
        val config = QqBot.Config(
            mode = QqBot.Mode.Official,
            appId = "102000000",
            appSecret = "secret",
            baseUrl = "",
            token = "",
            target = QqBot.Target.Private,
            targetId = "",
            displayName = "",
        )
        assertTrue("默认必须是沙箱，否则纯手机场景会被 IP 白名单挡住", config.sandbox)
        // 沙箱开关不影响"是否配置完整"的判断
        assertTrue(config.configured)
        assertTrue(config.copy(sandbox = false).configured)
        assertFalse(config.copy(appSecret = "").canReceive)
    }
}
