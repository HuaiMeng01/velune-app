package com.veluneagent.core.data

import org.json.JSONObject

enum class AttachmentKind(val wire: String) {
    Text("text"),
    Image("image"),
    ;

    companion object {
        fun fromWire(value: String): AttachmentKind =
            entries.firstOrNull { it.wire == value } ?: Text
    }
}

/**
 * 一条消息携带的附件。
 *
 * 只支持两类：文本类（直接读入正文，注入上下文）与图片（转成 data URL 走多模态字段）。
 * 二进制文档（PDF/DOCX）不在这一层解析 —— 需要额外的解析库与几十 MB 包体积，
 * 属于「插件市场」的职责，不应塞进首版主流程。
 */
data class ChatAttachment(
    val kind: AttachmentKind,
    val name: String,
    val mime: String,
    /** 文本类附件的内容，已按上限截断。 */
    val text: String = "",
    /** 图片的 data URL，形如 `data:image/jpeg;base64,xxx`。 */
    val dataUrl: String = "",
) {
    fun toJson(): String = JSONObject().apply {
        put("kind", kind.wire)
        put("name", name)
        put("mime", mime)
        if (text.isNotEmpty()) put("text", text)
        if (dataUrl.isNotEmpty()) put("dataUrl", dataUrl)
    }.toString()

    /** 列表/气泡上展示的一行摘要。 */
    fun summary(): String = when (kind) {
        AttachmentKind.Image -> name
        AttachmentKind.Text -> "$name · ${text.length} 字"
    }

    companion object {
        const val MaxTextChars = 16_000
        const val MaxImageBytes = 1_200_000

        fun fromJson(raw: String?): ChatAttachment? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(raw)
                ChatAttachment(
                    kind = AttachmentKind.fromWire(o.optString("kind")),
                    name = o.optString("name"),
                    mime = o.optString("mime"),
                    text = o.optString("text"),
                    dataUrl = o.optString("dataUrl"),
                )
            }.getOrNull()
        }
    }
}
