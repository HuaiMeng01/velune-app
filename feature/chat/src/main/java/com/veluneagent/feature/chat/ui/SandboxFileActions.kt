package com.veluneagent.feature.chat.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.veluneagent.core.data.sandbox.Sandbox
import java.io.File

/**
 * 沙箱文件的「交给系统」动作。
 *
 * 为什么必须走 FileProvider：沙箱在应用私有目录（`/data/data/<pkg>/files/sandbox`），
 * 直接把 `file://` 交给别的应用会抛 FileUriExposedException，而且外部应用本来也无权读。
 * 这里统一换成 content:// 并只授予一次性读权限。
 */

/** 按扩展名给 MIME。FileProvider 对不认识的类型只回 octet-stream，几乎没应用愿意接。 */
internal fun mimeOf(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "html", "htm" -> "text/html"
    "md", "markdown" -> "text/markdown"
    "txt", "log", "text" -> "text/plain"
    "json" -> "application/json"
    "csv" -> "text/csv"
    "xml" -> "text/xml"
    "pdf" -> "application/pdf"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    "svg" -> "image/svg+xml"
    "kt", "kts", "java", "py", "js", "ts", "sh", "rs", "go", "c", "cpp", "sql" -> "text/plain"
    "zip" -> "application/zip"
    else -> "*/*"
}

/**
 * 用系统里能处理该类型的应用打开。
 *
 * 不套 `createChooser`：直接 startActivity 时系统自己会在多应用时弹选择器，
 * 而一旦套了 chooser，「没有应用能打开」就变成 chooser 里的空列表，用户看到的是
 * 一个空白弹窗而不是可理解的失败。这里宁可捕获 ActivityNotFoundException 后降级。
 *
 * @return 成功交给系统返回 true；没有任何应用能打开返回 false
 */
internal fun openSandboxFile(context: Context, sandbox: Sandbox, path: String): Boolean {
    val file = runCatching { sandbox.resolve(path) }.getOrNull() ?: return false
    if (!file.isFile) return false

    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }.getOrNull() ?: return false

    // 先用按扩展名推断的类型；不认识就退回纯文本，再不行才算失败
    val candidates = listOf(mimeOf(path), "text/plain").distinct()
    for (mime in candidates) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { context.startActivity(intent) }.isSuccess) return true
    }
    return false
}

/** 分享沙箱文件；失败静默（分享失败不该打断对话）。 */
internal fun shareSandboxFile(context: Context, sandbox: Sandbox, path: String?): Boolean {
    if (path.isNullOrBlank()) return false
    return runCatching {
        val file: File = sandbox.resolve(path)
        if (!file.isFile) return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = context.contentResolver.getType(uri) ?: mimeOf(path)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "分享文件")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess
}
