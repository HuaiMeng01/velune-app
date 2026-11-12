package com.veluneagent.feature.chat.ui

/**
 * 极简语法着色。
 *
 * 为什么自己写而不引库：Android 上的高亮库普遍依赖词法分析器 + 正则树，
 * 包体积几 MB 起，而聊天场景的代码块 90% 是短片段。这里用「注释 > 字符串 > 数字 > 关键字」
 * 的单遍扫描，够用且可预测；扫描结果与 Compose 无关，便于单测。
 */
enum class CodeTokenKind { Keyword, String, Comment, Number }

data class CodeSpan(val start: Int, val end: Int, val kind: CodeTokenKind)

object CodeHighlighter {

    private val keywordSets: Map<String, Set<String>> = mapOf(
        "kotlin" to setOf(
            "fun", "val", "var", "class", "object", "interface", "data", "sealed", "enum",
            "if", "else", "when", "for", "while", "do", "return", "break", "continue",
            "try", "catch", "finally", "throw", "import", "package", "init", "companion",
            "private", "public", "internal", "protected", "override", "open", "suspend",
            "null", "true", "false", "this", "super", "it", "in", "is", "as", "by",
        ),
        "java" to setOf(
            "class", "interface", "enum", "public", "private", "protected", "static",
            "final", "void", "int", "long", "double", "float", "boolean", "char",
            "if", "else", "for", "while", "do", "return", "break", "continue",
            "try", "catch", "finally", "throw", "new", "import", "package",
            "null", "true", "false", "this", "super", "extends", "implements",
        ),
        "python" to setOf(
            "def", "class", "if", "elif", "else", "for", "while", "return", "break",
            "continue", "import", "from", "as", "with", "try", "except", "finally",
            "raise", "lambda", "yield", "async", "await", "pass", "global", "nonlocal",
            "None", "True", "False", "and", "or", "not", "in", "is",
        ),
        "javascript" to setOf(
            "const", "let", "var", "function", "return", "if", "else", "for", "while",
            "class", "new", "import", "export", "from", "default", "async", "await",
            "try", "catch", "finally", "throw", "typeof", "instanceof", "this",
            "null", "undefined", "true", "false", "interface", "type", "extends",
        ),
        "sql" to setOf(
            "select", "from", "where", "join", "left", "right", "inner", "outer", "on",
            "group", "by", "order", "having", "limit", "offset", "insert", "into",
            "update", "set", "delete", "values", "create", "table", "index", "drop",
            "alter", "and", "or", "not", "null", "as", "distinct", "count", "sum",
        ),
        "shell" to setOf(
            "echo", "cd", "ls", "pwd", "export", "source", "if", "then", "else", "fi",
            "for", "do", "done", "while", "case", "esac", "function", "sudo", "apt",
            "curl", "wget", "git", "npm", "gradle", "mkdir", "rm", "cp", "mv", "cat",
        ),
        "json" to setOf("true", "false", "null"),
    )

    private val aliases = mapOf(
        "kt" to "kotlin", "kts" to "kotlin",
        "py" to "python", "python3" to "python",
        "js" to "javascript", "ts" to "javascript", "tsx" to "javascript",
        "jsx" to "javascript", "node" to "javascript",
        "sh" to "shell", "bash" to "shell", "zsh" to "shell", "console" to "shell",
        "postgres" to "sql", "mysql" to "sql", "sqlite" to "sql",
        "yml" to "yaml", "jsonc" to "json",
    )

    fun normalize(language: String): String {
        val key = language.trim().lowercase()
        return aliases[key] ?: key
    }

    fun spans(code: String, language: String): List<CodeSpan> {
        val lang = normalize(language)
        val keywords = keywordSets[lang] ?: emptySet()
        val lineComment = when (lang) {
            "python", "shell", "yaml", "toml" -> "#"
            "sql" -> "--"
            else -> "//"
        }
        val spans = mutableListOf<CodeSpan>()
        var index = 0
        while (index < code.length) {
            val ch = code[index]

            // 行注释优先：注释里的引号不构成字符串
            if (code.startsWith(lineComment, index)) {
                val end = code.indexOf('\n', index).let { if (it < 0) code.length else it }
                spans += CodeSpan(index, end, CodeTokenKind.Comment)
                index = end
                continue
            }
            // 块注释
            if (code.startsWith("/*", index)) {
                val end = code.indexOf("*/", index + 2).let { if (it < 0) code.length else it + 2 }
                spans += CodeSpan(index, end, CodeTokenKind.Comment)
                index = end
                continue
            }
            // 字符串（单/双/反引号，支持反斜杠转义）
            if (ch == '"' || ch == '\'' || ch == '`') {
                var cursor = index + 1
                while (cursor < code.length) {
                    val c = code[cursor]
                    if (c == '\\') {
                        cursor += 2
                        continue
                    }
                    if (c == ch) {
                        cursor++
                        break
                    }
                    if (c == '\n' && ch != '`') break
                    cursor++
                }
                spans += CodeSpan(index, cursor.coerceAtMost(code.length), CodeTokenKind.String)
                index = cursor
                continue
            }
            // 数字
            if (ch.isDigit()) {
                var cursor = index
                while (cursor < code.length && (code[cursor].isDigit() || code[cursor] == '.')) cursor++
                spans += CodeSpan(index, cursor, CodeTokenKind.Number)
                index = cursor
                continue
            }
            // 标识符 → 关键字
            if (ch.isLetter() || ch == '_') {
                var cursor = index
                while (cursor < code.length && (code[cursor].isLetterOrDigit() || code[cursor] == '_')) {
                    cursor++
                }
                val word = code.substring(index, cursor)
                if (keywords.contains(word) || (lang == "sql" && keywords.contains(word.lowercase()))) {
                    spans += CodeSpan(index, cursor, CodeTokenKind.Keyword)
                }
                index = cursor
                continue
            }
            index++
        }
        return spans
    }
}
