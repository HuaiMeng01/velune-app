package com.veluneagent.feature.chat.ui

import org.json.JSONArray
import org.json.JSONObject

/**
 * 工具过程片段的落盘格式。
 *
 * 为什么在 UI 层做编解码：`ToolStepUi` 是展示模型（新增状态、文件路径这类字段会频繁变），
 * core 的 `ChatStore` 只负责原样存取一个字符串，不该跟着 UI 一起改表。
 *
 * 编解码必须容错：老版本存下的 JSON 缺字段、或者内容被截断，都不能让整个会话打不开。
 */
internal object ToolStepCodec {

    /** 只序列化工具片段；纯文本不落盘（正文已经存在 message.text 里）。 */
    fun encode(parts: List<StreamPart>): String {
        val tools = parts.filterIsInstance<StreamPart.Tool>()
        if (tools.isEmpty()) return ""
        val array = JSONArray()
        tools.forEach { part ->
            val step = part.step
            array.put(
                JSONObject().apply {
                    put("callId", step.callId)
                    put("name", step.name)
                    put("displayName", step.displayName)
                    put("origin", step.origin)
                    put("argsPreview", step.argsPreview)
                    put("status", step.status.name)
                    put("detail", step.detail ?: JSONObject.NULL)
                    put("durationMs", step.durationMs)
                    put("argsFull", step.argsFull)
                    put("resultFull", step.resultFull ?: JSONObject.NULL)
                    put("filePath", step.filePath ?: JSONObject.NULL)
                },
            )
        }
        return array.toString()
    }

    fun decode(json: String): List<StreamPart> {
        if (json.isBlank()) return emptyList()
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val callId = item.optString("callId")
                if (callId.isBlank()) continue
                add(
                    StreamPart.Tool(
                        key = callId,
                        step = ToolStepUi(
                            callId = callId,
                            name = item.optString("name"),
                            displayName = item.optString("displayName"),
                            origin = item.optString("origin"),
                            argsPreview = item.optString("argsPreview"),
                            status = runCatching {
                                ToolStepStatus.valueOf(item.optString("status"))
                            }.getOrDefault(ToolStepStatus.Ok),
                            detail = item.optString("detail").takeIf { it.isNotBlank() && it != "null" },
                            durationMs = item.optLong("durationMs"),
                            argsFull = item.optString("argsFull"),
                            resultFull = item.optString("resultFull").takeIf { it.isNotBlank() && it != "null" },
                            filePath = item.optString("filePath").takeIf { it.isNotBlank() && it != "null" },
                        ),
                    ),
                )
            }
        }
    }
}
