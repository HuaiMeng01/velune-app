package com.veluneagent.core.data.im

import com.veluneagent.core.data.agent.BuiltinTools
import com.veluneagent.core.data.agent.ToolProvider
import com.veluneagent.core.data.agent.ToolSource
import com.veluneagent.core.data.agent.ToolSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QQ 只读工具边界。
 *
 * 这是整个「QQ 能执行操作」功能唯一的护栏，所以必须锁死断言：
 * 一旦有人把过滤条件改宽（比如只看 sideEffect），短信、文件、日历、待办、远程命令
 * 就会直接暴露给任何能给机器人发消息的人 —— 而这种回归在真机上不会报错，只会静默越权。
 *
 * 因此这里不用"逐条手工造 spec"的 delegate，而是直接用**真实的内置工具表**
 * （[BuiltinTools.all]）+ 一条合成插件工具。这样任何一个工具被新加上 `sensitive = false`
 * 的误判都会立刻让断言变红，而不是等用户发现"QQ 能读我短信"。
 */
class ReadOnlyCatalogTest {

    /** 一条合成插件工具：插件工具在 QQ 侧一律不可见，与它标了什么无关。 */
    private val pluginSpec = ToolSpec(
        name = "plugin_tool",
        displayName = "插件工具",
        description = "",
        parametersJson = "{}",
        sideEffect = false,
        source = ToolSource.Plugin,
        pluginId = "com.example.tools",
        origin = "插件",
    )

    private val delegate = object : ToolProvider {
        private val all = BuiltinTools.all + pluginSpec

        override fun specs(): List<ToolSpec> = all
        override fun find(name: String): ToolSpec? = all.firstOrNull { it.name == name }
        override fun requiresApproval(spec: ToolSpec): Boolean =
            spec.name in BuiltinTools.approvalRequired || spec.source == ToolSource.Plugin

        override fun scopesOf(spec: ToolSpec): List<String> = emptyList()
    }

    private val catalog = ReadOnlyCatalog(delegate)

    /**
     * 白名单必须**恰好**是这 4 个。
     *
     * 顺序跟着 [BuiltinTools.all] 的声明顺序走 —— 这里写死顺序不是为了好看，
     * 而是让"有人往中间插了一个工具"这种改动也变红，逼他来这里补一次理由。
     */
    @Test
    fun `白名单恰好是这四个工具`() {
        assertEquals(
            listOf("now", "calc", "list_plugins", "list_apps"),
            catalog.specs().map { it.name },
        )
    }

    @Test
    fun `隐私工具即使只读也不可见`() {
        assertNull("read_sms 是只读但能读出验证码，必须排除", catalog.find("read_sms"))
        assertNull("fs_list 能列出目录结构", catalog.find("fs_list"))
        assertNull("fs_read 能读出文件内容", catalog.find("fs_read"))
        assertNull("calendar_list_events 能读出私人日程", catalog.find("calendar_list_events"))
        assertNull("todo_list 能读出用户在做什么", catalog.find("todo_list"))
    }

    @Test
    fun `副作用工具与插件工具都不可见`() {
        assertNull(catalog.find("ssh_exec"))
        assertNull(catalog.find("lock_screen"))
        assertNull(catalog.find("fs_write"))
        assertNull(catalog.find("plugin_tool"))
    }

    @Test
    fun `花钱的工具不可见`() {
        // tts_speak 不改世界也不泄露数据，但每次调用都扣配额；
        // QQ 远程入口没有"用户点确认"这一步，必须挡住
        assertNull(catalog.find("tts_speak"))
        // web_search / web_fetch 按次计费（约 0.005 元/次），
        // 且 web_fetch 能把任意 URL 的查询串拼上用户内容发出去。
        // 未授权来源若能触发它们，等于替用户无限花钱、顺便开一条外泄通道。
        assertNull("搜索按次计费", catalog.find("web_search"))
        assertNull("抓取按次计费且可外发", catalog.find("web_fetch"))
    }

    @Test
    fun `计划类工具隔离`() {
        // update_plan 不碰世界也不泄隐私，但它只会往本地 UI 塞一张没人看得见的进度卡
        assertNull(catalog.find("update_plan"))
    }

    @Test
    fun `可见工具都不需要审批`() {
        // QQ 侧不存在审批人：任何"可见但要审批"的工具等于给了模型一个永远失败的按钮，
        // 还会浪费一步预算。真正的边界是 specs() 少给模型看什么。
        val allowed = catalog.specs()
        assertFalse(allowed.isEmpty())
        assertTrue(
            "白名单里不允许出现需要审批的工具",
            allowed.intersect(BuiltinTools.approvalRequired.toSet()).isEmpty(),
        )
        allowed.forEach { spec -> assertFalse(catalog.requiresApproval(spec)) }
    }

    @Test
    fun `放行的工具确实可以按名字查到`() {
        assertNotNull(catalog.find("now"))
        assertNotNull(catalog.find("calc"))
    }
}
