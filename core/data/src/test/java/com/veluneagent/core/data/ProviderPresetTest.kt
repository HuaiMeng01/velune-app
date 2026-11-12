package com.veluneagent.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 服务商预设只保留「名称 + Base URL」这类稳定事实。
 *
 * 模型清单**故意不存在**：这套系统里模型名迭代比发版快，硬编码一份列表的结果
 * 是「列出来的都调不通、能用的填不进去」。这里断言的就是「不许把模型清单加回来」，
 * 以及 Base URL 这类写错一个字符就 404 的事实没有被人手滑改坏。
 */
class ProviderPresetTest {

    @Test
    fun `三家预设的 Base URL 符合官方文档`() {
        val deepSeek = ProviderPresets.all.first { it.id == "deepseek" }
        assertEquals("https://api.deepseek.com", deepSeek.baseUrl)

        val zhiPu = ProviderPresets.all.first { it.id == "zhipu" }
        // 漏掉 /v4 会直接 404，这条最容易被「顺手改简洁」改坏
        assertEquals("https://open.bigmodel.cn/api/paas/v4", zhiPu.baseUrl)

        val miMo = ProviderPresets.all.first { it.id == "mimo" }
        assertEquals("https://api.xiaomimimo.com/v1", miMo.baseUrl)
    }

    @Test
    fun `预设只给模型示例不给候选列表`() {
        ProviderPresets.all.forEach { preset ->
            assertTrue("${preset.id} 的 Base URL 不是 https", preset.baseUrl.startsWith("https://"))
            // modelHint 只是输入框占位，不能当成候选；这里保证它短、可辨认
            assertTrue("${preset.id} 缺少模型示例", preset.modelHint.isNotBlank())
            assertTrue("${preset.id} 的模型示例过长", preset.modelHint.length <= 40)
            // 提示里必须点名去官方文档查，否则用户只会照抄示例
            assertTrue(
                "${preset.id} 的说明没有指向官方文档",
                preset.note.contains("文档") || preset.note.contains("Base URL"),
            )
            assertTrue("${preset.id} 缺少文档地址", preset.docsUrl.startsWith("https://"))
        }
    }

    @Test
    fun `Base URL 能反查出预设`() {
        assertEquals("deepseek", ProviderPresets.byBaseUrl("https://api.deepseek.com/")?.id)
        assertEquals("zhipu", ProviderPresets.byBaseUrl("https://open.bigmodel.cn/api/paas/v4/")?.id)
        assertEquals("mimo", ProviderPresets.byBaseUrl("https://api.xiaomimimo.com/v1")?.id)
    }

    @Test
    fun `自建网关不匹配任何预设`() {
        assertNull(ProviderPresets.byBaseUrl("https://my-gateway.internal/v1"))
        // 空地址不能误判成第一个预设，否则编辑页会显示错误的提示文案
        assertNull(ProviderPresets.byBaseUrl("   "))
    }
}
