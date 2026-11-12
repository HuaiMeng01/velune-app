package com.veluneagent.core.data.agent

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 四个「手机能力」工具的声明与分级。
 *
 * 这些断言的意义：审批清单一旦漏项，Agent 就能在用户不知情的情况下启动别的应用或锁屏；
 * schema 一旦不是合法 JSON，请求会被上游 400，而且报错信息完全指不到工具定义上。
 */
class SystemToolsTest {

    private val newTools = listOf(
        BuiltinTools.NameListApps,
        BuiltinTools.NameLaunchApp,
        BuiltinTools.NameReadSms,
        BuiltinTools.NameLockScreen,
    )

    @Test
    fun `四个手机能力工具都已注册`() {
        newTools.forEach { name ->
            assertNotNull("缺少工具：$name", BuiltinTools.find(name))
        }
    }

    @Test
    fun `参数 schema 都是合法 JSON 对象`() {
        newTools.forEach { name ->
            val spec = BuiltinTools.find(name)!!
            val json = runCatching { JSONObject(spec.parametersJson) }.getOrNull()
            assertNotNull("$name 的 parametersJson 不是合法 JSON", json)
            assertEquals("$name 的 type 必须是 object", "object", json!!.optString("type"))
        }
    }

    @Test
    fun `只读工具不审批 会改变手机状态的工具必须审批`() {
        assertFalse(BuiltinTools.approvalRequired.contains(BuiltinTools.NameListApps))
        assertFalse(BuiltinTools.approvalRequired.contains(BuiltinTools.NameReadSms))
        // 启动别的应用会把用户带走，锁屏更直接：两者都必须先问
        assertTrue(BuiltinTools.approvalRequired.contains(BuiltinTools.NameLaunchApp))
        assertTrue(BuiltinTools.approvalRequired.contains(BuiltinTools.NameLockScreen))
    }

    @Test
    fun `有副作用的工具都标了 sideEffect`() {
        assertTrue(BuiltinTools.find(BuiltinTools.NameLaunchApp)!!.sideEffect)
        assertTrue(BuiltinTools.find(BuiltinTools.NameLockScreen)!!.sideEffect)
        // 读短信不改变任何状态，但涉及隐私，靠「每次调用都要说明用途」的提示词约束
        assertFalse(BuiltinTools.find(BuiltinTools.NameReadSms)!!.sideEffect)
    }

    @Test
    fun `联网工具已注册且不需要审批`() {
        listOf(BuiltinTools.NameWebSearch, BuiltinTools.NameWebFetch).forEach { name ->
            val spec = BuiltinTools.find(name)
            assertNotNull("缺少工具：$name", spec)
            val json = runCatching { JSONObject(spec!!.parametersJson) }.getOrNull()
            assertNotNull("$name 的 schema 不是合法 JSON", json)
            assertEquals("object", json!!.optString("type"))
            // 只读网络请求，不改外部状态；费用很低，不值得每次打断用户
            assertFalse("$name 不应要求审批", BuiltinTools.approvalRequired.contains(name))
            assertFalse("$name 不应标记副作用", spec!!.sideEffect)
        }
    }

    @Test
    fun `联网搜索的描述说明依赖与成本`() {
        val spec = BuiltinTools.find(BuiltinTools.NameWebSearch)!!
        // 没有 DeepSeek Key 时工具必然失败，描述里必须先说清，否则模型会反复试
        assertTrue(spec.description.contains("DeepSeek"))
        assertTrue(spec.description.contains("0.005"))
    }

    @Test
    fun `语音搜索条数上限防止上下文被撑爆`() {
        // count 上限 10：每条 summary 原文 600-800 字，再多会挤掉后续推理空间
        val json = JSONObject(BuiltinTools.find(BuiltinTools.NameWebSearch)!!.parametersJson)
        val count = json.getJSONObject("properties").getJSONObject("count")
        assertEquals(10, count.getInt("maximum"))
    }

    @Test
    fun `读短信的审批跟随用户开关`() {
        // 默认开启：短信可能含验证码，默认让用户看见每次读取
        assertTrue(ToolPrefs.requiresApproval(BuiltinTools.NameReadSms))
        ToolPrefs.setConfirmSms(false)
        assertFalse(ToolPrefs.requiresApproval(BuiltinTools.NameReadSms))
        // 其它工具不受这个开关影响
        assertFalse(ToolPrefs.requiresApproval(BuiltinTools.NameListApps))
        ToolPrefs.setConfirmSms(true)
        assertTrue(ToolPrefs.requiresApproval(BuiltinTools.NameReadSms))
    }

    @Test
    fun `描述里写清前置条件`() {
        // 锁屏失败的主要原因是没激活设备管理员，描述必须提前说明，否则模型会反复重试
        assertTrue(
            BuiltinTools.find(BuiltinTools.NameLockScreen)!!.description.contains("设备管理员"),
        )
        // 启动应用最容易错在猜包名，描述要求先查
        assertTrue(
            BuiltinTools.find(BuiltinTools.NameLaunchApp)!!.description.contains("list_apps"),
        )
    }
}
