package com.veluneagent.core.design.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * 设置行图标的分类色板。
 *
 * 小米设置里每行图标是一个 28dp 彩色 squircle 方块，**颜色表示分类**，深浅两套各一份。
 * 之前这里把颜色写在调用点（`Color(0xFF3482FF)` 之类），带来三个问题：
 *
 * 1. 深色模式不跟随——同一块亮蓝在深色底上明显更"跳"，而小米深色下用的是压暗变体；
 * 2. 语义色被当分类色用——`colors.success` 同时给"权限与能力"和"远程服务器"，
 *    可绿色在别处代表"已连接/成功"，同一个颜色两套含义；
 * 3. 同色重复，让"颜色区分分类"这件事失效。
 *
 * 这里每类一个槽位，且不与语义色（success / warning / danger）混用——
 * 语义色只用于状态，不用于图标底色。
 */
class VxIconToken(private val light: Long, private val dark: Long) {
    @Composable
    @ReadOnlyComposable
    fun color(): Color = if (VeluneTheme.colors.isLight) Color(light) else Color(dark)
}

object VxIconPalette {
    /** 模型与密钥。 */
    val Model = VxIconToken(0xFF3482FF, 0xFF2E6FD9)

    /** Agent 与工具（滑杆类）。 */
    val Agent = VxIconToken(0xFF5856D6, 0xFF4B49B8)

    /** 插件 / 扩展。 */
    val Plugin = VxIconToken(0xFF30B0C7, 0xFF2A93A8)

    /** 角色 / 人设。 */
    val Persona = VxIconToken(0xFFAF52DE, 0xFF9444BB)

    /** MCP 与外部协议接入。 */
    val Protocol = VxIconToken(0xFF4F8DF0, 0xFF3C71C9)

    /** 应用偏好、外观。 */
    val Appearance = VxIconToken(0xFFFF375F, 0xFFD93050)

    /** 权限与能力。 */
    val Permission = VxIconToken(0xFF34C759, 0xFF2CA84C)

    /** 数据与存储。 */
    val Storage = VxIconToken(0xFFFF9500, 0xFFD97F00)

    /** 远程服务器与运行环境。 */
    val Remote = VxIconToken(0xFF5AC8FA, 0xFF3FA6D6)

    /** 产出文件。 */
    val Files = VxIconToken(0xFF00A6A6, 0xFF00898A)

    /** 关于等元信息。 */
    val About = VxIconToken(0xFF8E8E93, 0xFF77777C)
}
