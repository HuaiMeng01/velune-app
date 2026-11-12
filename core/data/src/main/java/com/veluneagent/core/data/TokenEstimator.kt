package com.veluneagent.core.data

/**
 * Token 估算。
 *
 * 不是精确分词：开源兼容端点大多不返回 `usage`（尤其是流式），而做精确分词要背一个
 * 几 MB 的 BPE 词表。这里用「中日韩字符按 1 token、其他字符按 4 字符 1 token」的近似，
 * 用于本地统计与上下文预算提示，误差在可接受范围内。
 */
object TokenEstimator {

    /** 每条消息的固定开销：role、分隔符等。 */
    private const val PerMessageOverhead = 4

    fun estimate(text: String): Int {
        if (text.isEmpty()) return 0
        var cjk = 0
        var other = 0
        for (ch in text) {
            if (isCjk(ch)) cjk++ else other++
        }
        return cjk + (other + 3) / 4 + PerMessageOverhead
    }

    fun estimateMessages(messages: List<ChatMessage>): Int = messages.sumOf { estimate(it.text) }

    private fun isCjk(ch: Char): Boolean {
        val code = ch.code
        return code in 0x4E00..0x9FFF || // 基本汉字
            code in 0x3400..0x4DBF || // 扩展 A
            code in 0x3000..0x303F || // 中文标点
            code in 0xFF00..0xFFEF || // 全角字符
            code in 0x3040..0x30FF // 假名
    }
}
