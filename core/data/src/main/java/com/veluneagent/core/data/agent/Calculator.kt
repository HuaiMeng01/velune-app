package com.veluneagent.core.data.agent

/**
 * 四则运算求值器。
 *
 * 为什么不用 `eval` / Nashorn：模型输出属于不可信输入，任何「执行字符串」的路径
 * 都是把注入风险从提示层带到代码层。这里用递归下降手写，只认数字与 + - * / % 和括号。
 */
object Calculator {

    fun evaluate(expression: String): Result<Double> = runCatching {
        val parser = Parser(expression)
        val value = parser.parseExpression()
        parser.skipSpaces()
        require(parser.atEnd()) { "表达式里有多余内容：${parser.rest()}" }
        require(!value.isNaN() && !value.isInfinite()) { "结果不是有限数" }
        value
    }

    /** 展示用：整数不带小数点，小数最多保留 6 位并裁掉尾零。 */
    fun format(value: Double): String = when {
        value == Math.floor(value) && Math.abs(value) < 1e15 -> value.toLong().toString()
        Math.abs(value) < 1e-6 -> String.format(java.util.Locale.US, "%.3e", value)
        else -> String.format(java.util.Locale.US, "%.6f", value)
            .trimEnd('0')
            .trimEnd('.')
    }

    private class Parser(private val input: String) {
        private var pos = 0

        fun atEnd(): Boolean = pos >= input.length

        fun rest(): String = input.substring(pos.coerceAtMost(input.length))

        fun skipSpaces() {
            while (pos < input.length && input[pos].isWhitespace()) pos++
        }

        fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                skipSpaces()
                if (atEnd()) return value
                when (input[pos]) {
                    '+' -> {
                        pos++
                        value += parseTerm()
                    }

                    '-' -> {
                        pos++
                        value -= parseTerm()
                    }

                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parseFactor()
            while (true) {
                skipSpaces()
                if (atEnd()) return value
                when (input[pos]) {
                    '*' -> {
                        pos++
                        value *= parseFactor()
                    }

                    '/' -> {
                        pos++
                        val divisor = parseFactor()
                        require(divisor != 0.0) { "除数为 0" }
                        value /= divisor
                    }

                    '%' -> {
                        pos++
                        val divisor = parseFactor()
                        require(divisor != 0.0) { "取余的除数为 0" }
                        value %= divisor
                    }

                    else -> return value
                }
            }
        }

        private fun parseFactor(): Double {
            skipSpaces()
            if (atEnd()) error("表达式不完整")
            val c = input[pos]
            when (c) {
                '-' -> {
                    pos++
                    return -parseFactor()
                }

                '+' -> {
                    pos++
                    return parseFactor()
                }

                '(' -> {
                    pos++
                    val value = parseExpression()
                    skipSpaces()
                    require(!atEnd() && input[pos] == ')') { "括号不匹配" }
                    pos++
                    return value
                }
            }
            if (c.isDigit() || c == '.') return parseNumber()
            error("无法识别的字符：$c")
        }

        private fun parseNumber(): Double {
            val start = pos
            while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
            // 科学计数法
            if (pos < input.length && (input[pos] == 'e' || input[pos] == 'E')) {
                val save = pos
                pos++
                if (pos < input.length && (input[pos] == '+' || input[pos] == '-')) pos++
                if (pos < input.length && input[pos].isDigit()) {
                    while (pos < input.length && input[pos].isDigit()) pos++
                } else {
                    pos = save
                }
            }
            val text = input.substring(start, pos)
            return text.toDoubleOrNull() ?: error("无法解析数字：$text")
        }
    }
}
