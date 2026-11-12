package com.veluneagent.core.data.system

/**
 * 文案翻译桥：core/data 不能依赖 core/design（依赖方向是 design → 无，data 层保持无 UI 依赖），
 * 但通知渠道名、待办提醒、Agent 工具步骤的 display 摘要都是用户可见文案。
 *
 * 组合根（MainActivity）在组合前把 [t] / [tf] 指向 core/design 的 tr / trf：
 *     UiText.t  = com.veluneagent.core.design.l10n::tr
 *     UiText.tf = { key, args -> com.veluneagent.core.design.l10n.trf(key, *args) }
 * 未设置时原样返回（key 即中文原文），行为与词典未命中回落一致。
 */
object UiText {
    @Volatile
    var t: (String) -> String = { it }

    @Volatile
    var tf: (String, Array<out Any?>) -> String = { key, args -> String.format(key, *args) }
}
