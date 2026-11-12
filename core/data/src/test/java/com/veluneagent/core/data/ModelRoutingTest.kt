package com.veluneagent.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 模型分工的回落逻辑。
 *
 * 这段逻辑一旦错，表现是「摘要悄悄用了最贵的模型」或「子任务直接报没有服务商」——
 * 都不会抛异常，只是账单和可用性出问题，所以必须有测试兜住。
 */
class ModelRoutingTest {

    private fun provider(id: String) = Provider(
        id = id,
        name = id,
        baseUrl = "https://$id.example.com/v1",
        model = "model-$id",
        hasKey = true,
    )

    @Test
    fun `未绑定时跟随主模型`() {
        val main = provider("main")
        val providers = listOf(main, provider("cheap"))

        assertEquals(main, ModelRouting.providerFor(ModelRouting.Role.Summary, providers, main))
        assertEquals(main, ModelRouting.providerFor(ModelRouting.Role.Sub, providers, main))
    }

    @Test
    fun `绑定后使用指定服务商`() {
        val main = provider("main")
        val cheap = provider("cheap")
        ModelRouting.assign(ModelRouting.Role.Sub, "cheap")
        try {
            assertEquals(cheap, ModelRouting.providerFor(ModelRouting.Role.Sub, listOf(main, cheap), main))
            // 另一个角色不能被顺带改掉
            assertEquals(main, ModelRouting.providerFor(ModelRouting.Role.Summary, listOf(main, cheap), main))
        } finally {
            ModelRouting.assign(ModelRouting.Role.Sub, null)
        }
    }

    @Test
    fun `绑定的服务商被删后回落到主模型`() {
        val main = provider("main")
        ModelRouting.assign(ModelRouting.Role.Summary, "deleted-provider")
        try {
            // 只剩 main 时，绑定的 id 找不到 → 不能返回 null，否则摘要直接不可用
            assertEquals(main, ModelRouting.providerFor(ModelRouting.Role.Summary, listOf(main), main))
        } finally {
            ModelRouting.assign(ModelRouting.Role.Summary, null)
        }
    }

    @Test
    fun `主模型角色始终返回传入的 main`() {
        val main = provider("main")
        ModelRouting.assign(ModelRouting.Role.Main, "cheap")
        try {
            // Main 不参与分工：它由「使用中」决定，两个入口改同一件事只会打架
            assertEquals(main, ModelRouting.providerFor(ModelRouting.Role.Main, listOf(main), main))
        } finally {
            ModelRouting.assign(ModelRouting.Role.Main, null)
        }
    }
}
