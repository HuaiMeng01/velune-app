package com.veluneagent.core.design.l10n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 应用内语言机制。
 *
 * 背景：全项目 UI 文案以中文原文硬编码在 Kotlin 里（没有 values-en 资源），
 * 走 Android 标准资源化要把 700+ 处调用点改成 context.getString，动静太大。
 * 这里采用「中文原文即 key」的进程内词典方案：
 *
 * - [tr] 是唯一入口：英文模式查 [EnStrings]，查不到**回落中文原文**——
 *   不会出现英文空白，未迁移的页面切了语言也只是维持中文，永不崩；
 * - 迁移一个页面 = 往 [EnStrings] 加词条 + 必要处包 tr()，可增量推进；
 * - [isEnglish] 是 Compose 状态：组合内读取会自动重组，切语言即时生效；
 *   非组合上下文（通知、Toast 回调）直接读它也安全，只是不触发重组——
 *   语言变化本身在组合根被收集，重组由那里驱动。
 *
 * 动态拼接的文案（buildString 里先拼 key 再上屏）要先对**部件**做 tr，
 * 不要把拼好的整句当 key——整句永远不会命中词典。
 */
object VxL10n {

    /** 由组合根（MainActivity）按 AppearancePrefs.language 设置；跟随系统时读系统 locale。 */
    var isEnglish by mutableStateOf(false)
}

/**
 * 翻译一个 UI 文案。中文模式原样返回；英文模式查词典，未收录回落原文。
 * 两个模式的返回都非空——调用点不需要判空。
 */
fun tr(s: String): String = if (VxL10n.isEnglish) EnStrings[s] ?: s else s

/** 带格式参数的翻译：词典 key 用 %1$s 这类占位符，命中后 format。 */
fun trf(s: String, vararg args: Any): String = tr(s).format(*args)
