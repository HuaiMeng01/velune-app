package com.veluneagent.feature.chat.ui

import com.veluneagent.core.design.l10n.tr
import com.veluneagent.core.design.l10n.trf

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.veluneagent.core.data.AttachmentKind
import com.veluneagent.core.data.ChatAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把用户选中的文件读成可发送的附件。
 *
 * 只认两类：图片（转 data URL 走多模态）与文本（读进上下文）。
 * 其余类型明确拒绝并说明原因，而不是发出去然后让服务商报一个看不懂的 400。
 */
object AttachmentReader {

    private val textExtensions = setOf(
        "txt", "md", "markdown", "json", "csv", "tsv", "log", "xml", "yaml", "yml",
        "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "go", "rs", "c", "h", "cpp",
        "sql", "sh", "gradle", "toml", "ini", "conf", "html", "css",
    )

    suspend fun read(context: Context, uri: Uri): Result<ChatAttachment> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val name = queryName(context, uri)
            val mime = resolver.getType(uri).orEmpty()
            val ext = name.substringAfterLast('.', "").lowercase()

            when {
                mime.startsWith("image/") -> {
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error(tr("无法读取文件"))
                    require(bytes.size <= ChatAttachment.MaxImageBytes) {
                        trf("图片过大（%d KB），上限 %d KB", bytes.size / 1024, ChatAttachment.MaxImageBytes / 1024)
                    }
                    val safeMime = mime.ifBlank { "image/jpeg" }
                    val dataUrl = "data:$safeMime;base64," +
                        Base64.encodeToString(bytes, Base64.NO_WRAP)
                    ChatAttachment(
                        kind = AttachmentKind.Image,
                        name = name,
                        mime = safeMime,
                        dataUrl = dataUrl,
                    )
                }

                mime.startsWith("text/") || ext in textExtensions -> {
                    val text = resolver.openInputStream(uri)?.use { input ->
                        // 多读一个字符用于判断是否被截断，避免把上百万字的日志全塞进上下文
                        val buffer = CharArray(ChatAttachment.MaxTextChars + 1)
                        val reader = input.reader(Charsets.UTF_8)
                        var read = 0
                        while (read < buffer.size) {
                            val n = reader.read(buffer, read, buffer.size - read)
                            if (n <= 0) break
                            read += n
                        }
                        String(buffer, 0, read)
                    }.orEmpty()
                    require(text.isNotBlank()) { tr("文件内容为空") }
                    ChatAttachment(
                        kind = AttachmentKind.Text,
                        name = name,
                        mime = mime.ifBlank { "text/plain" },
                        text = if (text.length > ChatAttachment.MaxTextChars) {
                            text.take(ChatAttachment.MaxTextChars) + "\n…（内容过长，已截断）"
                        } else {
                            text
                        },
                    )
                }

                else -> error(trf("暂不支持该类型（仅支持图片与文本类文件）：%s", name))
            }
        }
    }

    private fun queryName(context: Context, uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
            }
        }.getOrNull().orEmpty().ifEmpty { uri.lastPathSegment ?: tr("附件") }
}
