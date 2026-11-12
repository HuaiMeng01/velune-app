package com.veluneagent.core.data.im

/**
 * QQ 单条消息的文本规整。
 *
 * 为什么放到 core:data 而不是留在 `QqBridgeService` 里：这是纯函数，也是**最容易悄悄写错**的
 * 一段逻辑（三个坑各踩过一次），而 Android 的 `Service` 私有方法永远没法做单元回归 ——
 * 一改就得靠真机发一条超长消息去试。
 *
 * 三个细节都必须对：
 * 1. **按字节裁而不是字符**：1800 个汉字 = 5400 字节，整条会被平台拒绝，用户什么也收不到；
 * 2. **后缀算进预算**：否则裁到 1500 再加 32 字节后缀，实际发出 1532 —— 你以为的上限就不是真上限；
 * 3. **按码点迭代**：emoji 是代理对（4 字节），按 `Char` 逐个编码会把它劈成两个非法片段（变成 `?`）。
 */
object QqText {

    /** 裁剪后的字节上限（含后缀）。平台实际限制更宽，这里留了余量。 */
    const val MaxBytes = 1500

    /** 截断提示。**它占用 [MaxBytes] 的预算**，不是额外追加。 */
    const val Suffix = "\n\n（内容过长，已截断）"

    /**
     * 把 [text] 裁到不超过 [MaxBytes] 个 UTF-8 字节，超长时追加 [Suffix]。
     *
     * - 没超长时原样返回（含空串）；
     * - 裁断点永远落在**完整码点**边界上，绝不会切出半个代理对；
     * - 返回值总字节数 ≤ [MaxBytes]。
     */
    fun truncate(text: String): String {
        if (text.isEmpty()) return text
        if (text.toByteArray(Charsets.UTF_8).size <= MaxBytes) return text

        val budget = (MaxBytes - Suffix.toByteArray(Charsets.UTF_8).size).coerceAtLeast(0)
        val sb = StringBuilder()
        var used = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val piece = String(Character.toChars(cp))
            val len = piece.toByteArray(Charsets.UTF_8).size
            if (used + len > budget) break
            sb.append(piece)
            used += len
            i += Character.charCount(cp)
        }
        return sb.toString() + Suffix
    }
}
