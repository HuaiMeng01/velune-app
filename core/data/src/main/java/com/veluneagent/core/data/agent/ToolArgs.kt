package com.veluneagent.core.data.agent

import org.json.JSONArray
import org.json.JSONObject

/**
 * 参数校验。
 *
 * 模型给的 arguments 是字符串，可能缺字段、类型不对、甚至粘着一堆解释文字。
 * 这里只做**必要且安全**的校验（必填、类型、枚举、范围），不做隐式类型转换：
 * 把 "3" 当 3 用看起来友好，实际会让插件收到意料之外的输入。
 */
object ToolArgs {

    data class Result(val ok: Boolean, val error: String? = null, val args: JSONObject = JSONObject())

    fun validate(argumentsJson: String, schemaJson: String): Result {
        val args = runCatching { JSONObject(argumentsJson.ifBlank { "{}" }) }.getOrElse {
            return Result(false, "参数不是合法 JSON：${it.message ?: "解析失败"}")
        }
        val schema = runCatching { JSONObject(schemaJson.ifBlank { "{}" }) }.getOrNull()
            ?: return Result(true, args = args)

        val properties = schema.optJSONObject("properties") ?: return Result(true, args = args)
        val required = buildList {
            schema.optJSONArray("required")?.let { array ->
                for (i in 0 until array.length()) add(array.optString(i))
            }
        }

        required.forEach { key ->
            if (!args.has(key) || args.isNull(key)) {
                return Result(false, "缺少必填参数：$key")
            }
        }

        properties.keys().forEach { key ->
            if (!args.has(key) || args.isNull(key)) return@forEach
            val spec = properties.optJSONObject(key) ?: return@forEach
            val typeError = typeError(key, args.opt(key), spec.optString("type"))
            if (typeError != null) return Result(false, typeError)
            val enumValues = spec.optJSONArray("enum")
            if (enumValues != null) {
                val value = args.opt(key).toString()
                val allowed = buildList {
                    for (i in 0 until enumValues.length()) add(enumValues.optString(i))
                }
                if (value !in allowed) return Result(false, "参数 $key 只能是：${allowed.joinToString("/")}")
            }
            if (args.opt(key) is Number) {
                val number = (args.opt(key) as Number).toDouble()
                if (spec.has("minimum") && number < spec.optDouble("minimum")) {
                    return Result(false, "参数 $key 不能小于 ${spec.optDouble("minimum")}")
                }
                if (spec.has("maximum") && number > spec.optDouble("maximum")) {
                    return Result(false, "参数 $key 不能大于 ${spec.optDouble("maximum")}")
                }
            }
        }
        return Result(true, args = args)
    }

    private fun typeError(key: String, value: Any, expected: String): String? {
        if (expected.isEmpty()) return null
        val actual = when (value) {
            is String -> "string"
            is Boolean -> "boolean"
            is Int, is Long -> "integer"
            is Double, is Float -> "number"
            is JSONObject -> "object"
            is JSONArray -> "array"
            else -> "unknown"
        }
        val compatible = actual == expected ||
            (expected == "number" && actual == "integer") ||
            (expected == "integer" && actual == "number" && value.toDoubleOrNullSafe())
        return if (compatible) null else "参数 $key 类型应为 $expected，实际为 $actual"
    }

    private fun Any.toDoubleOrNullSafe(): Boolean {
        val d = (this as? Number)?.toDouble() ?: return false
        return d == Math.floor(d) && !d.isInfinite()
    }
}
