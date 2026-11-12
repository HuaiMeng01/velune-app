package com.veluneagent.feature.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veluneagent.core.design.component.VxIcon
import com.veluneagent.core.design.component.VxText
import com.veluneagent.core.design.shape.SquircleShape
import com.veluneagent.core.design.l10n.tr
import com.veluneagent.core.design.l10n.trf
import com.veluneagent.core.design.theme.VeluneTheme

/**
 * 轻量 Markdown 渲染（自绘，不引 WebView）。
 *
 * 支持的块级元素：标题、围栏代码、无序/有序列表、引用、表格、分隔线、段落。
 * 未闭合的围栏代码按「到结尾都是代码」处理——流式输出时这个状态会持续几百毫秒，
 * 不能等到闭合才渲染，否则用户会看到一大段裸反引号。
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = VeluneTheme.colors.textPrimary,
) {
    val blocks = remember(text) { parseMarkdown(text) }

    SelectionContainer {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            blocks.forEach { block ->
                when (block) {
                    is MdBlock.Heading -> HeadingBlock(block, color)
                    is MdBlock.Paragraph -> ParagraphBlock(block.text, color)
                    is MdBlock.Code -> CodeBlock(block)
                    is MdBlock.ListBlock -> ListBlock(block, color)
                    is MdBlock.Quote -> QuoteBlock(block.text, color)
                    is MdBlock.Table -> TableBlock(block, color)
                    MdBlock.Divider -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(VeluneTheme.colors.divider),
                    )
                }
            }
        }
    }
}

// ---------- 块级渲染 ----------

@Composable
private fun HeadingBlock(block: MdBlock.Heading, color: Color) {
    val size = when (block.level) {
        1 -> 21.sp
        2 -> 19.sp
        3 -> 17.sp
        else -> 16.sp
    }
    // 行内解析要遍历整段文本，流式输出时它会在每个 token 到达时被重组，必须缓存
    InlineText(
        text = block.text,
        style = VeluneTheme.type.body.copy(
            color = color,
            fontSize = size,
            fontWeight = FontWeight.SemiBold,
            lineHeight = (size.value * 1.35f).sp,
        ),
        modifier = Modifier.padding(top = if (block.level <= 2) 4.dp else 2.dp),
    )
}

@Composable
private fun ParagraphBlock(text: String, color: Color) {
    InlineText(
        text = text,
        style = VeluneTheme.type.body.copy(color = color, lineHeight = 25.sp),
    )
}

/**
 * 行内文本：Markdown 行内样式 + 链接点击。
 *
 * 用字符串注解 + 命中测试而不是 ClickableText（已废弃）：
 * 让「链接可点」与「整段可选」共存，长按仍然交给 SelectionContainer。
 */
@Composable
private fun InlineText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val accent = VeluneTheme.colors.accent
    val annotated = remember(text, accent) { inlineAnnotated(text, accent) }
    val context = LocalContext.current
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    BasicText(
        text = annotated,
        style = style,
        modifier = modifier.pointerInput(annotated) {
            detectTapGestures { position ->
                val result = layout ?: return@detectTapGestures
                val offset = result.getOffsetForPosition(position)
                annotated.getStringAnnotations(UrlAnnotationTag, offset, offset)
                    .firstOrNull()
                    ?.let { annotation -> openUrl(context, annotation.item) }
            }
        },
        onTextLayout = { layout = it },
    )
}

private const val UrlAnnotationTag = "URL"

/** 只放行 http/https：其他 scheme（intent://、file://）可能被用来触发外部组件。 */
private fun openUrl(context: Context, url: String) {
    if (!url.startsWith("http://") && !url.startsWith("https://")) return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
private fun ListBlock(block: MdBlock.ListBlock, color: Color) {
    val colors = VeluneTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        block.items.forEachIndexed { index, item ->
            Row(modifier = Modifier.fillMaxWidth()) {
                VxText(
                    text = if (block.ordered) "${block.start + index}." else "•",
                    style = VeluneTheme.type.body.copy(lineHeight = 25.sp),
                    color = colors.textTertiary,
                    modifier = Modifier.width(if (block.ordered) 22.dp else 14.dp),
                )
                InlineText(
                    text = item,
                    style = VeluneTheme.type.body.copy(color = color, lineHeight = 25.sp),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun QuoteBlock(text: String, color: Color) {
    val colors = VeluneTheme.colors
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(20.dp)
                .background(colors.accent.copy(alpha = 0.45f)),
        )
        Spacer(modifier = Modifier.width(10.dp))
        InlineText(
            text = text,
            style = VeluneTheme.type.body.copy(
                color = colors.textSecondary,
                lineHeight = 24.sp,
            ),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CodeBlock(block: MdBlock.Code) {
    val colors = VeluneTheme.colors
    val context = LocalContext.current
    var copied = remember { androidx.compose.runtime.mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(12.dp))
            .background(colors.fill),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VxText(
                text = block.language.ifBlank { "code" },
                style = VeluneTheme.type.footnote.copy(fontSize = 11.sp),
                color = colors.textTertiary,
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(SquircleShape(8.dp))
                    .clickable {
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("code", block.code))
                        copied.value = true
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VxIcon(
                    icon = VxIcon.Copy,
                    tint = colors.textSecondary,
                    size = 13.dp,
                    strokeWidth = 1.8.dp,
                )
                Spacer(modifier = Modifier.width(4.dp))
                VxText(
                    text = if (copied.value) tr("已复制") else tr("复制"),
                    style = VeluneTheme.type.footnote.copy(fontSize = 11.sp),
                    color = colors.textSecondary,
                )
            }
        }

        val code = block.code.trimEnd('\n')
        val highlighted = remember(code, block.language) {
            highlightCode(code, block.language)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
        ) {
            BasicText(
                text = highlighted,
                style = VeluneTheme.type.body.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = colors.textPrimary,
                ),
            )
        }
    }
}

@Composable
private fun TableBlock(block: MdBlock.Table, color: Color) {
    val colors = VeluneTheme.colors
    val columns = block.rows.maxOfOrNull { it.size } ?: return

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // 列宽按列数均分，最少 96dp：两列表会撑满，八列表会横向滚动而不是挤成一条
        val cellWidth = ((maxWidth - 20.dp) / columns).coerceAtLeast(96.dp)
        Column(
            modifier = Modifier
                .clip(SquircleShape(10.dp))
                .background(colors.fill)
                .horizontalScroll(rememberScrollState()),
        ) {
        block.rows.forEachIndexed { rowIndex, cells ->
            Row(
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                for (c in 0 until columns) {
                    val cell = cells.getOrElse(c) { "" }
                    val annotated = remember(cell) { inlineAnnotated(cell, colors.accent) }
                    BasicText(
                        text = annotated,
                        style = VeluneTheme.type.footnote.copy(
                            color = color,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontWeight = if (rowIndex == 0) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        modifier = Modifier.width(cellWidth),
                    )
                }
            }
            if (rowIndex == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(colors.divider),
                )
            }
        }
        }
    }
}

/** 把 CodeHighlighter 的扫描结果转成带颜色的 AnnotatedString。 */
private fun highlightCode(code: String, language: String): AnnotatedString =
    buildAnnotatedString {
        append(code)
        CodeHighlighter.spans(code, language).forEach { span ->
            addStyle(
                when (span.kind) {
                    CodeTokenKind.Keyword -> SpanStyle(color = Color(0xFF7B5CFF))
                    CodeTokenKind.String -> SpanStyle(color = Color(0xFF2E9E5B))
                    CodeTokenKind.Comment -> SpanStyle(
                        color = Color(0xFF8F9CAE),
                        fontStyle = FontStyle.Italic,
                    )

                    CodeTokenKind.Number -> SpanStyle(color = Color(0xFFD9822B))
                },
                span.start,
                span.end,
            )
        }
    }

// ---------- 行内样式 ----------

/**
 * 行内解析：`code`、**bold**、*italic*、[text](url)。
 * 标记不闭合时按字面量输出，避免半截文本被吞掉。
 */
private fun inlineAnnotated(text: String, accent: Color): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        val buffer = StringBuilder()

        fun flush() {
            if (buffer.isNotEmpty()) {
                append(buffer.toString())
                buffer.clear()
            }
        }

        while (i < text.length) {
            val c = text[i]

            // 行内代码
            if (c == '`') {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    flush()
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, background = accent.copy(alpha = 0.10f)))
                    append(text.substring(i + 1, end))
                    pop()
                    i = end + 1
                    continue
                }
            }

            // 加粗 / 斜体
            if (c == '*') {
                val bold = text.startsWith("**", i)
                val marker = if (bold) "**" else "*"
                val end = text.indexOf(marker, i + marker.length)
                if (end > i + marker.length) {
                    flush()
                    pushStyle(
                        if (bold) {
                            SpanStyle(fontWeight = FontWeight.SemiBold)
                        } else {
                            SpanStyle(fontStyle = FontStyle.Italic)
                        },
                    )
                    append(text.substring(i + marker.length, end))
                    pop()
                    i = end + marker.length
                    continue
                }
            }

            // 链接：[text](url)
            if (c == '[') {
                val close = text.indexOf(']', i + 1)
                if (close > i && close + 1 < text.length && text[close + 1] == '(') {
                    val urlEnd = text.indexOf(')', close + 2)
                    if (urlEnd > close) {
                        flush()
                        val start = length
                        pushStyle(
                            SpanStyle(
                                color = accent,
                                textDecoration = TextDecoration.Underline,
                            ),
                        )
                        append(text.substring(i + 1, close))
                        pop()
                        // 记录 URL 区间，交给点击处理打开浏览器（只允许 http/https）
                        addStringAnnotation(
                            tag = UrlAnnotationTag,
                            annotation = text.substring(close + 2, urlEnd),
                            start = start,
                            end = length,
                        )
                        i = urlEnd + 1
                        continue
                    }
                }
            }

            buffer.append(c)
            i++
        }
        flush()
    }

// ---------- 解析 ----------

sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Code(val language: String, val code: String) : MdBlock
    data class ListBlock(val ordered: Boolean, val start: Int, val items: List<String>) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Table(val rows: List<List<String>>) : MdBlock
    data object Divider : MdBlock
}

internal fun parseMarkdown(source: String): List<MdBlock> {
    val lines = source.replace("\r\n", "\n").split("\n")
    val blocks = mutableListOf<MdBlock>()
    var i = 0

    fun flushParagraph(buffer: MutableList<String>) {
        if (buffer.isEmpty()) return
        blocks += MdBlock.Paragraph(buffer.joinToString("\n").trim())
        buffer.clear()
    }

    val paragraph = mutableListOf<String>()

    while (i < lines.size) {
        val raw = lines[i]
        val line = raw.trimEnd()
        val trimmed = line.trim()

        // 围栏代码
        if (trimmed.startsWith("```")) {
            flushParagraph(paragraph)
            val lang = trimmed.removePrefix("```").trim()
            val code = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                code.append(lines[i]).append('\n')
                i++
            }
            // 未闭合也当作代码结束（流式中间态）
            if (i < lines.size) i++
            blocks += MdBlock.Code(lang, code.toString())
            continue
        }

        // 空行
        if (trimmed.isEmpty()) {
            flushParagraph(paragraph)
            i++
            continue
        }

        // 分隔线
        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            flushParagraph(paragraph)
            blocks += MdBlock.Divider
            i++
            continue
        }

        // 标题
        if (trimmed.startsWith("#")) {
            val level = trimmed.takeWhile { it == '#' }.length
            if (level in 1..6 && trimmed.length > level && trimmed[level] == ' ') {
                flushParagraph(paragraph)
                blocks += MdBlock.Heading(level, trimmed.substring(level + 1).trim())
                i++
                continue
            }
        }

        // 引用
        if (trimmed.startsWith(">")) {
            flushParagraph(paragraph)
            val quote = mutableListOf(trimmed.removePrefix(">").trim())
            i++
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quote += lines[i].trim().removePrefix(">").trim()
                i++
            }
            blocks += MdBlock.Quote(quote.joinToString("\n"))
            continue
        }

        // 表格：当前行含 | 且下一行是分隔行
        if (trimmed.contains('|') && i + 1 < lines.size &&
            lines[i + 1].trim().matches(Regex("""^\|?[\s:|-]+\|?$""")) &&
            lines[i + 1].contains('-')
        ) {
            flushParagraph(paragraph)
            val rows = mutableListOf(splitRow(trimmed))
            i += 2
            while (i < lines.size && lines[i].trim().contains('|')) {
                rows += splitRow(lines[i].trim())
                i++
            }
            blocks += MdBlock.Table(rows)
            continue
        }

        // 列表
        val bullet = Regex("""^([-*+])\s+(.*)$""")
        val ordered = Regex("""^(\d+)[.)]\s+(.*)$""")
        if (bullet.matches(trimmed) || ordered.matches(trimmed)) {
            flushParagraph(paragraph)
            val isOrdered = ordered.matches(trimmed)
            val start = if (isOrdered) ordered.find(trimmed)!!.groupValues[1].toInt() else 1
            val items = mutableListOf<String>()
            while (i < lines.size) {
                val t = lines[i].trim()
                val m = if (isOrdered) ordered.find(t) else bullet.find(t)
                if (m == null) break
                items += m.groupValues[2]
                i++
            }
            blocks += MdBlock.ListBlock(isOrdered, start, items)
            continue
        }

        paragraph += trimmed
        i++
    }

    flushParagraph(paragraph)
    return blocks
}

private fun splitRow(line: String): List<String> =
    line.trim().trim('|').split('|').map { it.trim() }

@androidx.compose.ui.tooling.preview.Preview(name = "Markdown 渲染", widthDp = 400)
@Composable
private fun MarkdownPreview() {
    VeluneTheme(darkTheme = false) {
        MarkdownText(
            text = """
                ## 表格转换结果

                已处理 **3 张表**，合并后共 `47 行`。

                | 文件 | 行数 |
                |---|---|
                | a.pdf | 18 |
                | b.pdf | 21 |

                - 表头已统一
                - 空行已剔除
                - 数字列转为数值类型

                > 源文件未修改，输出另存为 output.csv

                ```kotlin
                val rows = tables.flatMap { it.rows }
                println(rows.size)
                ```
            """.trimIndent(),
            modifier = Modifier.padding(16.dp),
        )
    }
}
