package com.veluneagent.core.data.agent

import com.veluneagent.core.data.plugin.InstalledPlugin
import com.veluneagent.core.data.plugin.ManifestResult
import com.veluneagent.core.data.plugin.PluginHosts
import com.veluneagent.core.data.plugin.PluginManifestParser
import com.veluneagent.core.data.plugin.PluginStore
import com.veluneagent.core.data.plugin.PluginToolDef
import com.veluneagent.core.data.system.CalendarRepository
import com.veluneagent.core.data.system.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 工具执行器。
 *
 * 所有分支都必须返回 ToolResult（不抛异常）：异常会被引擎包装成通用错误，
 * 丢掉「为什么失败」的信息，模型就失去了自我纠正的机会。
 */
class ToolExecutor(
    private val plugins: PluginStore,
    private val auditor: ScopeAuditor? = null,
    private val sandbox: com.veluneagent.core.data.sandbox.Sandbox? = null,
    private val system: com.veluneagent.core.data.system.SystemCapabilities? = null,
    /** 取 DeepSeek Key（搜索/抓取接口只认它）；返回 null 表示用户没配 DeepSeek 服务商。 */
    private val webKey: suspend () -> String? = { null },
    /** 取 MiMo Key（语音合成只认它）；返回 null 表示用户没配 MiMo 服务商。 */
    private val mimoKey: suspend () -> String? = { null },
    /**
     * 手机内 Ubuntu 执行器；null 表示本机没有可用的 Linux 环境。
     * 用 lambda 注入而不是传 Context：执行器不该知道 Android 的存在。
     */
    private val localRun: (suspend (language: String, code: String, timeoutSec: Int) -> Result<com.veluneagent.core.data.linux.LinuxRuntime.ExecResult>)? = null,
    /**
     * 子模型执行器；null 表示没配（delegate_task 会返回一个可操作的失败提示）。
     * 与 [localRun] 同样的注入方式：执行器不依赖 Android，也不依赖 ProviderStore 的单例状态。
     */
    private val subAgent: (suspend (task: String, context: String) -> com.veluneagent.core.data.agent.SubAgentOutcome)? = null,
    /** MCP 工具注册表。null 表示没启用，`mcp_*` 工具不会出现 */
    private val mcp: com.veluneagent.core.data.mcp.McpRegistry? = null,
) : ToolRunner {

    override suspend fun execute(spec: ToolSpec, call: ToolCall, runId: String?): ToolResult =
        withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            val validated = ToolArgs.validate(call.argumentsJson, spec.parametersJson)
            if (!validated.ok) {
                return@withContext ToolResult.failure(
                    callId = call.id,
                    name = call.name,
                    message = validated.error ?: "参数校验失败",
                    code = "invalid_args",
                )
            }
            val args = validated.args

            when {
                spec.source == ToolSource.Plugin -> runPlugin(spec, call, args, startedAt, runId)
                spec.source == ToolSource.Mcp -> runMcp(spec, call, args, startedAt)
                else -> runBuiltin(spec, call, args, startedAt)
            }
        }

    /**
     * 调用 MCP 服务器上的工具。
     *
     * 工具列表可能在拉取后被服务器改动，所以每次都用名字重新查一次绑定；
     * 查不到就明确告诉用户去刷新，而不是抛一个 `NullPointerException`。
     */
    private suspend fun runMcp(
        spec: ToolSpec,
        call: ToolCall,
        args: JSONObject,
        startedAt: Long,
    ): ToolResult {
        val registry = mcp ?: return fail(call, "MCP 未启用", "mcp_unavailable", startedAt)
        val tool = registry.find(spec.name)
            ?: return fail(
                call,
                "这个 MCP 工具已经不可用了，请到设置 → MCP 服务器里刷新",
                "mcp_tool_missing",
                startedAt,
            )
        return registry.call(tool, args.toString()).fold(
            onSuccess = { result ->
                // 必须裁剪：协议层放宽到 512KB 是为了不让响应被截断，
                // 但工具结果会原样进对话上下文 —— 一次抓整页网页就能把上下文撑爆，
                // 顺带让本轮 token 预算失去意义。与插件工具同一把尺子（见 runPlugin）。
                val clipped = ToolOutput.clip(result.text)
                if (result.isError) {
                    fail(
                        call,
                        clipped.ifBlank { "MCP 工具返回了错误" },
                        "mcp_tool_error",
                        startedAt,
                    )
                } else {
                    ok(
                        call = call,
                        modelContent = """{"ok":true,"result":${clipped.toJsonString()}}""",
                        display = "${tool.serverName} · ${tool.name}",
                        startedAt = startedAt,
                    )
                }
            },
            onFailure = { error ->
                fail(call, error.message ?: "调用 MCP 工具失败", "mcp_failed", startedAt)
            },
        )
    }

    // ---------- 内置工具 ----------

    private suspend fun runBuiltin(
        spec: ToolSpec,
        call: ToolCall,
        args: JSONObject,
        startedAt: Long,
    ): ToolResult = when (spec.name) {
        BuiltinTools.NameNow -> {
            val zone = args.optString("timezone").takeIf { it.isNotBlank() }
            val zoneId = runCatching { ZoneId.of(zone ?: ZoneId.systemDefault().id) }.getOrNull()
                ?: return fail(call, "无法识别的时区：$zone", "invalid_timezone", startedAt)
            val now = ZonedDateTime.now(zoneId)
            val text = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            ok(
                call = call,
                modelContent = """{"ok":true,"datetime":"$text","timezone":"${zoneId.id}","weekday":"${now.dayOfWeek}"}""",
                display = "$text（${zoneId.id}）",
                startedAt = startedAt,
            )
        }

        BuiltinTools.NameCalc -> {
            val expression = args.optString("expression")
            Calculator.evaluate(expression).fold(
                onSuccess = { value ->
                    val formatted = Calculator.format(value)
                    ok(
                        call = call,
                        modelContent = """{"ok":true,"expression":${expression.toJsonString()},"value":$formatted}""",
                        display = "$expression = $formatted",
                        startedAt = startedAt,
                    )
                },
                onFailure = { fail(call, "计算失败：${it.message}", "calc_error", startedAt) },
            )
        }

        BuiltinTools.NameListPlugins -> {
            val installed = plugins.plugins.value
            val modelText = if (installed.isEmpty()) {
                """{"ok":true,"plugins":[],"note":"尚未安装任何插件"}"""
            } else {
                buildString {
                    append("""{"ok":true,"plugins":[""")
                    installed.forEachIndexed { index, plugin ->
                        if (index > 0) append(',')
                        append('{')
                        append("\"id\":").append(plugin.id.toJsonString()).append(',')
                        append("\"name\":").append(plugin.manifest.name.toJsonString()).append(',')
                        append("\"version\":").append(plugin.manifest.version.toJsonString()).append(',')
                        append("\"enabled\":").append(plugin.enabled).append(',')
                        append("\"tools\":[")
                        plugin.manifest.tools.forEachIndexed { toolIndex, tool ->
                            if (toolIndex > 0) append(',')
                            append(tool.name.toJsonString())
                        }
                        append("]}")
                    }
                    append("]}")
                }
            }
            val display = if (installed.isEmpty()) {
                "未安装插件"
            } else {
                installed.joinToString("、") { "${it.manifest.name} ${it.manifest.version}" }
            }
            ok(call, modelText, display, startedAt)
        }

        BuiltinTools.NameInstallPlugin -> installPlugin(call, args, startedAt)

        BuiltinTools.NameUninstallPlugin -> {
            val id = args.optString("plugin_id")
            val target = plugins.find(id)
                ?: return fail(call, "没有找到插件 $id", "not_found", startedAt)
            plugins.uninstall(id)
            ok(
                call = call,
                modelContent = """{"ok":true,"removed":${target.id.toJsonString()}}""",
                display = UiText.tf("已卸载 %s", arrayOf(target.manifest.name)),
                startedAt = startedAt,
            )
        }

        BuiltinTools.NameFsList -> withSandbox(call, startedAt) { box ->
            val path = args.optString("path").ifBlank { "/" }
            val entries = box.list(path)
            val json = buildString {
                append("""{"ok":true,"path":""").append(path.toJsonString()).append(""","entries":[""")
                entries.forEachIndexed { index, entry ->
                    if (index > 0) append(',')
                    append('{')
                    append("\"name\":").append(entry.name.toJsonString()).append(',')
                    append("\"dir\":").append(entry.isDir)
                    if (!entry.isDir) append(",\"size\":").append(entry.size)
                    append('}')
                }
                append("]}")
            }
            ok(
                call = call,
                modelContent = json,
                display = if (entries.isEmpty()) UiText.t("空目录") else UiText.tf("%d 项", arrayOf(entries.size)),
                startedAt = startedAt,
            )
        }

        BuiltinTools.NameFsRead -> withSandbox(call, startedAt) { box ->
            val path = args.optString("path")
            val maxChars = args.optInt("max_chars", 4_000).coerceIn(200, 20_000)
            val text = box.read(path, maxChars)
            ok(
                call = call,
                modelContent = text,
                display = UiText.tf("%s · %d 字", arrayOf(path, text.length)),
                startedAt = startedAt,
            )
        }

        BuiltinTools.NameFsWrite -> withSandbox(call, startedAt) { box ->
            val path = args.optString("path")
            val content = args.optString("content")
            val append = args.optBoolean("append", false)
            val size = box.write(path, content, append)
            ok(
                call = call,
                modelContent = """{"ok":true,"path":${path.toJsonString()},"bytes":$size}""",
                display = UiText.t(if (append) "已追加 " else "已写入 ") +
                    UiText.tf("%s（%d 字节）", arrayOf(path, size)),
                startedAt = startedAt,
            )
        }

        BuiltinTools.NameFsDelete -> withSandbox(call, startedAt) { box ->
            val path = args.optString("path")
            val removed = box.delete(path)
            ok(
                call = call,
                modelContent = """{"ok":$removed,"path":${path.toJsonString()}}""",
                display = if (removed) UiText.tf("已删除 %s", arrayOf(path)) else UiText.tf("不存在：%s", arrayOf(path)),
                startedAt = startedAt,
            )
        }

        BuiltinTools.NameCalendarCreate -> withSystem(call, startedAt) { sys ->
            val title = args.optString("title").ifBlank {
                return@withSystem fail(call, "缺少事件标题", "invalid_args", startedAt)
            }
            val allDay = args.optBoolean("all_day", false)
            val startInMinutes = args.optInt("start_in_minutes", -1)
            val hasExplicitClock = args.has("start_hour")

            // 开始时间三选一：全天 / 相对分钟 / 日期+时刻。都没有就报错，绝不替用户猜
            if (!allDay && startInMinutes < 0 && !hasExplicitClock) {
                return@withSystem fail(
                    call,
                    "缺少开始时间：请提供 start_in_minutes（多少分钟后）或 start_hour（几点）",
                    "missing_start_time",
                    startedAt,
                )
            }

            // 时间窗口统一由 CalendarWindow 计算，避免「跨夜 / 全天 / 相对分钟」三套规则各写一遍
            val window = CalendarWindow.compute(
                now = System.currentTimeMillis(),
                startInMinutes = startInMinutes,
                dayOffset = args.optInt("start_day_offset", 0),
                hour = if (hasExplicitClock) args.optInt("start_hour") else null,
                minute = args.optInt("start_minute", 0),
                endHour = if (args.has("end_hour")) args.optInt("end_hour") else null,
                endMinute = args.optInt("end_minute", 0),
                durationMinutes = args.optInt("duration_minutes", 60),
                allDay = allDay,
            )
            val start = window.start
            val end = window.end

            val reminder = args.optInt("reminder_minutes", 10).let { if (it < 0) null else it }
            // 模型只需要说「每周/每天」，RRULE 的拼写由这里负责：
            // 让模型直接写 FREQ=WEEKLY;BYDAY=MO 这类字符串，错一个分号就静默不重复
            val repeatRule = when (args.optString("repeat").lowercase()) {
                "daily" -> "FREQ=DAILY"
                "weekly" -> "FREQ=WEEKLY"
                "monthly" -> "FREQ=MONTHLY"
                else -> null
            }

            sys.calendar.createEvent(
                title = title,
                beginMillis = start,
                endMillis = end,
                description = args.optString("description"),
                location = args.optString("location"),
                allDay = allDay,
                reminderMinutes = reminder,
                repeatRule = repeatRule,
            ).fold(
                onSuccess = { id ->
                    val label = CalendarRepository.format(start)
                    val endLabel = CalendarRepository.format(end)
                    ok(
                        call = call,
                        modelContent = """{"ok":true,"event_id":$id,"start":"$label","end":"$endLabel","reminder_minutes":${reminder ?: 0},"repeat":"${repeatRule ?: "none"}"}""",
                        display = "$label → $endLabel · $title" +
                            if (repeatRule != null) "（重复）" else "",
                        startedAt = startedAt,
                    )
                },
                onFailure = { fail(call, it.message ?: "写入日历失败", "calendar_failed", startedAt) },
            )
        }

        BuiltinTools.NameCalendarUpdate -> withSystem(call, startedAt) { sys ->
            val eventId = args.optLong("event_id", 0L)
            if (eventId <= 0L) {
                return@withSystem fail(
                    call,
                    "缺少 event_id：先用 calendar_list_events 查出来",
                    "invalid_args",
                    startedAt,
                )
            }

            // 只有真的给了时间参数才重算窗口，否则改标题会把原时间一并覆盖成"现在"
            val hasClock = args.has("start_hour")
            val hasWindow = hasClock || args.has("start_in_minutes") ||
                args.has("start_day_offset") || args.has("duration_minutes") ||
                args.has("end_hour")
            val window = if (hasWindow) {
                CalendarWindow.compute(
                    now = System.currentTimeMillis(),
                    startInMinutes = args.optInt("start_in_minutes", -1),
                    dayOffset = args.optInt("start_day_offset", 0),
                    hour = if (hasClock) args.optInt("start_hour") else null,
                    minute = args.optInt("start_minute", 0),
                    endHour = if (args.has("end_hour")) args.optInt("end_hour") else null,
                    endMinute = args.optInt("end_minute", 0),
                    durationMinutes = args.optInt("duration_minutes", 60),
                    allDay = args.optBoolean("all_day", false),
                )
            } else {
                null
            }
            val newTitle = args.optString("title").takeIf { it.isNotBlank() }
            val reminder = if (args.has("reminder_minutes")) args.optInt("reminder_minutes", 10) else null
            val allDay = if (args.has("all_day")) args.optBoolean("all_day") else null

            if (window == null && newTitle == null && reminder == null && allDay == null) {
                return@withSystem fail(call, "没有要修改的内容", "invalid_args", startedAt)
            }

            sys.calendar.updateEvent(
                eventId = eventId,
                title = newTitle,
                beginMillis = window?.start,
                endMillis = window?.end,
                allDay = allDay,
                reminderMinutes = reminder,
            ).fold(
                onSuccess = {
                    val startLabel = window?.let { CalendarRepository.format(it.start) }.orEmpty()
                    val endLabel = window?.let { CalendarRepository.format(it.end) }.orEmpty()
                    ok(
                        call = call,
                        modelContent = """{"ok":true,"event_id":$eventId,"start":"$startLabel","end":"$endLabel"}""",
                        display = if (startLabel.isNotEmpty()) "$startLabel → $endLabel" else UiText.tf("已更新日程 #%d", arrayOf(eventId)),
                        startedAt = startedAt,
                    )
                },
                onFailure = { fail(call, it.message ?: "修改日程失败", "calendar_update_failed", startedAt) },
            )
        }

        BuiltinTools.NameCalendarDelete -> withSystem(call, startedAt) { sys ->
            val eventId = args.optLong("event_id", 0L)
            if (eventId <= 0L) {
                return@withSystem fail(
                    call,
                    "缺少 event_id：先用 calendar_list_events 查出来",
                    "invalid_args",
                    startedAt,
                )
            }
            sys.calendar.deleteEvent(eventId).fold(
                onSuccess = {
                    ok(
                        call = call,
                        modelContent = """{"ok":true,"deleted_event_id":$eventId}""",
                        display = UiText.tf("已删除日程 #%d", arrayOf(eventId)),
                        startedAt = startedAt,
                    )
                },
                onFailure = { fail(call, it.message ?: "删除日程失败", "calendar_delete_failed", startedAt) },
            )
        }

        BuiltinTools.NameCalendarList -> withSystem(call, startedAt) { sys ->
            val dayOffset = args.optInt("day_offset", 0)
            val days = args.optInt("days", 1).coerceIn(1, 14)
            val from = com.veluneagent.core.data.system.CalendarRepository
                .resolveRelative(dayOffset, 0, 0)
            val to = from + days * 86_400_000L - 1
            sys.calendar.listEvents(from, to).fold(
                onSuccess = { events ->
                    val json = buildString {
                        append("""{"ok":true,"events":[""")
                        events.forEachIndexed { index, event ->
                            if (index > 0) append(',')
                            append('{')
                            append("\"title\":").append(event.title.toJsonString()).append(',')
                            append("\"begin\":").append(
                                com.veluneagent.core.data.system.CalendarRepository
                                    .format(event.begin).toJsonString(),
                            ).append(',')
                            append("\"end\":").append(
                                com.veluneagent.core.data.system.CalendarRepository
                                    .format(event.end).toJsonString(),
                            )
                            append('}')
                        }
                        append("]}")
                    }
                    ok(
                        call = call,
                        modelContent = json,
                        display = if (events.isEmpty()) UiText.t("这段时间没有日程") else UiText.tf("%d 个日程", arrayOf(events.size)),
                        startedAt = startedAt,
                    )
                },
                onFailure = { fail(call, it.message ?: "读取日历失败", "calendar_failed", startedAt) },
            )
        }

        BuiltinTools.NameTodoAdd -> withSystem(call, startedAt) { sys ->
            val title = args.optString("title")
            val due = if (args.has("day_offset")) {
                // 默认 9:00 而不是 23:59：晚上十一点半提醒「明天交表」没有意义，
                // 大多数提醒都希望在一早看到
                com.veluneagent.core.data.system.CalendarRepository.resolveRelative(
                    args.optInt("day_offset"),
                    args.optInt("hour", 9).coerceIn(0, 23),
                    args.optInt("minute", 0).coerceIn(0, 59),
                )
            } else {
                null
            }
            val id = sys.todos.add(title, due)
            ok(
                call = call,
                modelContent = """{"ok":true,"id":$id}""",
                display = UiText.tf("已加入待办：%s", arrayOf(title)),
                startedAt = startedAt,
            )
        }

        BuiltinTools.NameTodoList -> withSystem(call, startedAt) { sys ->
            val items = sys.todos.list(args.optBoolean("include_done", false))
            val json = buildString {
                append("""{"ok":true,"todos":[""")
                items.forEachIndexed { index, item ->
                    if (index > 0) append(',')
                    append('{')
                    append("\"id\":").append(item.id).append(',')
                    append("\"title\":").append(item.title.toJsonString()).append(',')
                    append("\"done\":").append(item.done)
                    item.dueAt?.let { append(",\"due\":").append(it) }
                    append('}')
                }
                append("]}")
            }
            ok(
                call = call,
                modelContent = json,
                display = if (items.isEmpty()) UiText.t("没有未完成的待办") else UiText.tf("%d 条待办", arrayOf(items.size)),
                startedAt = startedAt,
            )
        }

        BuiltinTools.NameTodoDone -> withSystem(call, startedAt) { sys ->
            val id = args.optLong("id", -1)
            val changed = sys.todos.setDone(id, true)
            ok(
                call = call,
                modelContent = """{"ok":$changed,"id":$id}""",
                display = if (changed) UiText.tf("已完成 #%d", arrayOf(id)) else UiText.tf("没有找到 #%d", arrayOf(id)),
                startedAt = startedAt,
            )
        }

        BuiltinTools.NameNotify -> withSystem(call, startedAt) { sys ->
            val title = args.optString("title")
            val body = args.optString("body")
            val sent = sys.notifier.notify(title, body).getOrDefault(false)
            ok(
                call = call,
                modelContent = """{"ok":$sent,"reason":${if (sent) "\"sent\"" else "\"notification_permission_denied\""}}""",
                display = if (sent) UiText.t("已发送通知") else UiText.t("未发送：缺少通知权限"),
                startedAt = startedAt,
            )
        }

        BuiltinTools.NameListApps -> withSystem(call, startedAt) { sys ->
            val query = args.optString("query").ifBlank { null }
            val apps = sys.apps.launchable(query)
            val json = buildString {
                append("""{"ok":true,"apps":[""")
                apps.forEachIndexed { index, app ->
                    if (index > 0) append(',')
                    append('{')
                    append("\"name\":").append(app.label.toJsonString()).append(',')
                    append("\"package\":").append(app.packageName.toJsonString())
                    append('}')
                }
                append("]}")
            }
            ok(
                call = call,
                modelContent = json,
                display = if (apps.isEmpty()) UiText.t("没有匹配的应用") else UiText.tf("%d 个应用", arrayOf(apps.size)),
                startedAt = startedAt,
            )
        }

        BuiltinTools.NameLaunchApp -> withSystem(call, startedAt) { sys ->
            val target = args.optString("target").trim()
            if (target.isEmpty()) {
                return@withSystem fail(call, "缺少应用名或包名", "invalid_args", startedAt)
            }
            val launched = sys.apps.launch(target)
            if (launched == null) {
                fail(call, "找不到可启动的应用：$target", "app_not_found", startedAt)
            } else {
                ok(
                    call = call,
                    modelContent = """{"ok":true,"package":${launched.packageName.toJsonString()},"name":${launched.label.toJsonString()}}""",
                    display = UiText.tf("已打开 %s", arrayOf(launched.label)),
                    startedAt = startedAt,
                )
            }
        }

        BuiltinTools.NameReadSms -> {
            val sys = system ?: return fail(call, "系统能力未初始化", "system_unavailable", startedAt)
            val limit = args.optInt("limit", 5).coerceIn(1, 20)
            val from = args.optString("from").ifBlank { null }
            try {
                sys.sms.recent(limit, from).fold(
                    onSuccess = { messages ->
                        val json = buildString {
                            append("""{"ok":true,"messages":[""")
                            messages.forEachIndexed { index, sms ->
                                if (index > 0) append(',')
                                append('{')
                                append("\"from\":").append(sms.address.toJsonString()).append(',')
                                append("\"body\":").append(sms.body.take(500).toJsonString())
                                    .append(',')
                                append("\"date\":").append(sms.date).append(',')
                                append("\"read\":").append(sms.read)
                                append('}')
                            }
                            append("]}")
                        }
                        ok(
                            call = call,
                            modelContent = json,
                            display = if (messages.isEmpty()) UiText.t("收件箱为空") else UiText.tf("%d 条短信", arrayOf(messages.size)),
                            startedAt = startedAt,
                        )
                    },
                    onFailure = {
                        // 权限被拒/系统拒绝都走这里：把原因交给模型，而不是返回空列表
                        fail(call, it.message ?: "读取短信失败", "sms_failed", startedAt)
                    },
                )
            } catch (t: Throwable) {
                fail(call, t.message ?: "读取短信失败", "sms_failed", startedAt)
            }
        }

        BuiltinTools.NameUpdatePlan -> {
            val array = args.optJSONArray("steps")
                ?: return fail(call, "steps 必须是数组", "invalid_args", startedAt)
            val steps = buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val title = item.optString("title").trim()
                    if (title.isEmpty()) continue
                    val status = when (item.optString("status")) {
                        "done" -> PlanStatus.Done
                        "running" -> PlanStatus.Running
                        else -> PlanStatus.Pending
                    }
                    add(PlanStep(title = title, status = status))
                }
            }
            if (steps.isEmpty()) {
                return fail(call, "steps 里没有有效的 title", "invalid_args", startedAt)
            }
            AgentPlan.update(steps)
            val (done, total) = AgentPlan.progress(steps)
            ok(
                call = call,
                modelContent = """{"ok":true,"total":$total,"done":$done}""",
                display = UiText.tf("计划 %d/%d", arrayOf(done, total)),
                startedAt = startedAt,
            )
        }

        BuiltinTools.NameCodeRun -> {
            val language = args.optString("language").lowercase()
            val code = args.optString("code")
            if (code.isBlank()) {
                return fail(call, "code 不能为空", "invalid_args", startedAt)
            }
            // 源码上限 20KB：再大的东西该走文件工具，而不是塞进工具参数
            if (code.length > 20_000) {
                return fail(call, "代码 ${code.length} 字符，超过 20KB 上限，请拆分成多次或用 fs_write", "too_long", startedAt)
            }
            val (ext, runner) = when (language) {
                "python", "python3", "py" -> "py" to "python3"
                "node", "js", "javascript" -> "js" to "node"
                "java" -> "java" to "java"
                "bash", "sh", "shell" -> "sh" to "bash"
                else -> return fail(call, "不支持的语言：$language（可用 python / node / java / bash）", "invalid_args", startedAt)
            }
            // 默认 60s、上限 300s：装依赖 / 编译这类任务的耗时由任务决定，不该被 60s 一刀切
            val timeoutSec = args.optInt("timeout_sec", 60).coerceIn(5, 300)
            val where = args.optString("where").ifBlank { "auto" }.lowercase()

            // local：手机内的 Ubuntu。auto 时只要环境就绪就走本地，省一次网络往返
            val localReady = localRun != null && com.veluneagent.core.data.linux.LinuxEnv.isReady()
            if ((where == "local" || (where == "auto" && localReady)) && localReady) {
                val result = localRun.invoke(language, code, timeoutSec).getOrElse { error ->
                    return fail(call, "本地执行失败：${error.message}", "local_failed", startedAt)
                }
                val out = result.stdout.take(8_000)
                val err = result.stderr.take(2_000)
                val json = """{"ok":${result.exitCode == 0},"where":"local","language":"$language","exit_code":${result.exitCode},"stdout":${out.toJsonString()},"stderr":${err.toJsonString()},"timed_out":${result.timedOut}}"""
                return ok(
                    call = call,
                    modelContent = json,
                    display = UiText.t("本地") + " $language · exit ${result.exitCode} · ${result.durationMs}ms",
                    startedAt = startedAt,
                )
            }
            if (where == "local" && !localReady) {
                return fail(call, "本地 Linux 环境未就绪：请先在 设置 → Linux 环境 下载 Ubuntu（约 30MB）", "local_not_ready", startedAt)
            }

            // 用 base64 传源码：heredoc 会被代码里的引号/反引号/$ 打乱，
            // 编码后只剩 [A-Za-z0-9+/=]，放进单引号里绝对安全
            val encoded = android.util.Base64.encodeToString(
                code.toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP,
            )
            val script = buildString {
                append("f=\$(mktemp /tmp/velune.XXXXXX.").append(ext).append("); ")
                append("echo '").append(encoded).append("' | base64 -d > \"\$f\" && ")
                append("cd /tmp && ").append(runner).append(" \"\$f\" 2>&1; ")
                append("rc=\$?; rm -f \"\$f\"; exit \$rc")
            }

            com.veluneagent.core.data.ssh.Ssh.exec(script, timeoutMs = timeoutSec * 1_000).fold(
                onSuccess = { result ->
                    val out = result.stdout.take(8_000)
                    val err = result.stderr.take(2_000)
                    val json = """{"ok":${result.ok},"language":"$language","exit_code":${result.exitCode},"stdout":${out.toJsonString()},"stderr":${err.toJsonString()},"truncated":${result.stdout.length > 8_000}}"""
                    ok(
                        call = call,
                        modelContent = json,
                        display = "$language · exit ${result.exitCode} · ${result.durationMs}ms",
                        startedAt = startedAt,
                    )
                },
                onFailure = { error ->
                    // 连不上服务器时的提示要能直接指导用户：多半是没配 SSH
                    fail(call, "运行失败：${error.message}（需先在 设置 → 远程服务器 配置主机）", "ssh_failed", startedAt)
                },
            )
        }

        BuiltinTools.NameTtsSpeak -> {
            val box = sandbox ?: return fail(call, "沙箱不可用，无法保存音频", "no_sandbox", startedAt)
            val text = args.optString("text").trim()
            if (text.isEmpty()) {
                return fail(call, "text 不能为空", "invalid_args", startedAt)
            }
            if (text.length > com.veluneagent.core.data.audio.MimoTts.MaxChars) {
                return fail(
                    call,
                    "文本 ${text.length} 字，超过单次上限 ${com.veluneagent.core.data.audio.MimoTts.MaxChars} 字，请分段",
                    "too_long",
                    startedAt,
                )
            }
            val key = mimoKey()
                ?: return fail(
                    call,
                    "没有可用的 MiMo Key：请在「模型与密钥」里添加服务商，Base URL 填 https://api.xiaomimimo.com/v1",
                    "no_key",
                    startedAt,
                )

            val voice = args.optString("voice").ifBlank { com.veluneagent.core.data.audio.MimoTts.DefaultVoice }
            val style = args.optString("style").ifBlank { null }
            val audio = com.veluneagent.core.data.audio.MimoTts
                .synthesize(apiKey = key, text = text, voice = voice, style = style)
                .getOrElse { error ->
                    return fail(call, error.message ?: "语音合成失败", "tts_failed", startedAt)
                }

            val relPath = "voice/tts-${System.currentTimeMillis()}.wav"
            runCatching {
                box.resolve(relPath).also { it.parentFile?.mkdirs() }.writeBytes(audio)
            }.getOrElse { error ->
                return fail(call, "音频写入沙箱失败：${error.message}", "write_failed", startedAt)
            }

            val kb = (audio.size / 1024f).let { String.format(java.util.Locale.US, "%.1f", it) }
            ok(
                call = call,
                // 告诉模型"已经生成"，并给出路径供后续 fs 操作；不要让模型编造音频内容
                modelContent = """{"ok":true,"path":"$relPath","bytes":${audio.size},"voice":"$voice"}""",
                display = UiText.t("语音已生成") + " · $kb KB",
                startedAt = startedAt,
            )
        }

        BuiltinTools.NameLockScreen -> withSystem(call, startedAt) { sys ->
            sys.screen.lock().fold(
                onSuccess = {
                    ok(
                        call = call,
                        modelContent = """{"ok":true}""",
                        display = UiText.t("已锁屏"),
                        startedAt = startedAt,
                    )
                },
                onFailure = {
                    fail(call, it.message ?: "锁屏失败", "lock_failed", startedAt)
                },
            )
        }

        BuiltinTools.NameSshExec -> {
            val command = args.optString("command").trim()
            if (command.isEmpty()) {
                return fail(call, "命令不能为空", "invalid_args", startedAt)
            }
            val timeoutMs = args.optInt("timeout_sec", 60).coerceIn(5, 300) * 1_000
            com.veluneagent.core.data.ssh.Ssh.exec(command, timeoutMs = timeoutMs).fold(
                onSuccess = { result ->
                    // 远端输出可能很大，进上下文前再截一刀（Ssh 内部已限 20KB）
                    val out = result.stdout.take(8_000)
                    val err = result.stderr.take(2_000)
                    val json = """{"ok":${result.ok},"exit_code":${result.exitCode},"stdout":${out.toJsonString()},"stderr":${err.toJsonString()},"truncated":${result.stdout.length > 8_000}}"""
                    ok(
                        call = call,
                        modelContent = json,
                        display = "exit ${result.exitCode} · " + UiText.tf("%d 字", arrayOf(result.stdout.length)) + " · ${result.durationMs}ms",
                        startedAt = startedAt,
                    )
                },
                onFailure = { fail(call, it.message ?: "SSH 执行失败", "ssh_failed", startedAt) },
            )
        }

        BuiltinTools.NameQqSend -> {
            val text = args.optString("text").trim()
            if (text.isEmpty()) {
                return fail(call, "消息内容不能为空", "invalid_args", startedAt)
            }
            val base = com.veluneagent.core.data.im.QqBot.config.value
            // 官方模式没有「默认目标」概念：OpenID 只能从收到的消息里拿，
            // 所以要主动推送必须显式给 to，否则不猜。
            if (base.mode == com.veluneagent.core.data.im.QqBot.Mode.Official) {
                val openId = args.optString("to").trim()
                if (openId.isEmpty()) {
                    return fail(
                        call,
                        "官方模式不支持盲发：请把 to 填成对方的 openid（可从收到的消息里拿），或等对方先给机器人发消息",
                        "qq_no_target",
                        startedAt,
                    )
                }
                val token = com.veluneagent.core.data.im.QqOfficial
                    .accessToken(base.appId, base.appSecret)
                    .getOrNull()
                    ?: return fail(call, "获取 QQ 凭证失败，请检查 AppID/AppSecret", "qq_token_failed", startedAt)
                com.veluneagent.core.data.im.QqOfficial.sendC2C(
                    token = token,
                    userOpenId = openId,
                    content = text,
                    sandbox = base.sandbox,
                ).fold(
                    onSuccess = {
                        ok(
                            call = call,
                            modelContent = """{"ok":true,"target":${openId.toJsonString()}}""",
                            display = UiText.tf("已发送到 %s", arrayOf(openId)),
                            startedAt = startedAt,
                        )
                    },
                    onFailure = { fail(call, it.message ?: "发送失败", "qq_send_failed", startedAt) },
                )
            } else if (!base.configured) {
                return fail(
                    call,
                    "QQ 机器人未配置：设置 → 插件 → QQ 机器人，填写协议端地址与目标号码",
                    "qq_not_configured",
                    startedAt,
                )
            }
            val overrideTo = args.optString("to").trim()
            val target = base.copy(
                target = if (args.has("as_group")) {
                    if (args.optBoolean("as_group")) {
                        com.veluneagent.core.data.im.QqBot.Target.Group
                    } else {
                        com.veluneagent.core.data.im.QqBot.Target.Private
                    }
                } else {
                    base.target
                },
                targetId = overrideTo.ifBlank { base.targetId },
            )
            com.veluneagent.core.data.im.QqBot.send(text, target).fold(
                onSuccess = {
                    ok(
                        call = call,
                        modelContent = """{"ok":true,"target":"${target.targetId}"}""",
                        display = UiText.t("已发送到") + " " + UiText.tf("%s%d", arrayOf(if (target.target == com.veluneagent.core.data.im.QqBot.Target.Group) UiText.t("群") else "", target.targetId)),
                        startedAt = startedAt,
                    )
                },
                onFailure = { fail(call, it.message ?: "发送失败", "qq_send_failed", startedAt) },
            )
        }

        BuiltinTools.NameDelegateTask -> {
            val task = args.optString("task").trim()
            if (task.isEmpty()) return fail(call, "task 不能为空", "invalid_args", startedAt)
            val runner = subAgent ?: return fail(
                call,
                "子模型未配置：设置 → 模型与密钥 → 模型分工",
                "sub_agent_unavailable",
                startedAt,
            )
            // context 不设上限：子任务的原文本来就该留在子模型那边，
            // 它不回灌主上下文，这正是这个工具存在的意义
            val context = args.optString("context")
            val outcome = runCatching { runner(task, context) }.getOrElse { error ->
                return fail(call, error.message ?: "子模型调用失败", "sub_agent_failed", startedAt)
            }
            val tokens = outcome.promptTokens + outcome.completionTokens
            ToolResult(
                callId = call.id,
                name = call.name,
                ok = true,
                contentForModel = """{"ok":true,"result":${outcome.text.toJsonString()}}""",
                displayText = buildString {
                    append("子模型返回 ${outcome.text.length} 字")
                    if (tokens > 0) append(" · $tokens token")
                },
                durationMs = System.currentTimeMillis() - startedAt,
                // 这两项会被引擎计入本轮预算，否则子任务的费用在主账上不可见
                promptTokens = outcome.promptTokens,
                completionTokens = outcome.completionTokens,
            )
        }

        BuiltinTools.NameWebSearch -> {
            val key = runCatching { webKey() }.getOrNull()
            if (key.isNullOrBlank()) {
                return fail(
                    call,
                    "联网搜索需要 DeepSeek 的 Key：请到「模型与密钥」添加一个 DeepSeek 服务商",
                    "web_key_missing",
                    startedAt,
                )
            }
            val query = args.optString("query").trim()
            val count = args.optInt("count", 5).coerceIn(1, 10)
            com.veluneagent.core.data.net.DeepSeekWeb
                .search(apiKey = key, query = query, count = count)
                .fold(
                    onSuccess = { hits ->
                        val json = buildString {
                            append("""{"ok":true,"count":${hits.size},"results":[""")
                            hits.forEachIndexed { index, hit ->
                                if (index > 0) append(',')
                                append('{')
                                append("\"title\":").append(hit.title.toJsonString()).append(',')
                                append("\"url\":").append(hit.url.toJsonString()).append(',')
                                // summary 原文 600-800 字，按条数累积很容易吃掉上下文
                                append("\"summary\":")
                                    .append(hit.summary.take(400).toJsonString()).append(',')
                                hit.siteName?.let {
                                    append("\"site\":").append(it.toJsonString()).append(',')
                                }
                                hit.date?.let {
                                    append("\"date\":").append(it.toJsonString()).append(',')
                                }
                                append("\"score\":").append(hit.score)
                                append('}')
                            }
                            append("]}")
                        }
                        ok(
                            call = call,
                            modelContent = json,
                            display = if (hits.isEmpty()) {
                                "没有搜索结果"
                            } else {
                                "${hits.size} 条 · ${hits.first().title.take(24)}"
                            },
                            startedAt = startedAt,
                        )
                    },
                    onFailure = {
                        fail(call, it.message ?: "搜索失败", "search_failed", startedAt)
                    },
                )
        }

        BuiltinTools.NameWebFetch -> {
            val key = runCatching { webKey() }.getOrNull()
            if (key.isNullOrBlank()) {
                return fail(
                    call,
                    "抓取网页需要 DeepSeek 的 Key：请到「模型与密钥」添加一个 DeepSeek 服务商",
                    "web_key_missing",
                    startedAt,
                )
            }
            val url = args.optString("url").trim()
            com.veluneagent.core.data.net.DeepSeekWeb.fetch(key, url).fold(
                onSuccess = { page ->
                    // 8000 字上限：一整篇长文塞进上下文会把后续推理挤没
                    val body = page.content.take(8_000)
                    val json = """{"ok":true,"title":${page.title.toJsonString()},"content":${body.toJsonString()},"truncated":${page.content.length > 8_000}}"""
                    ok(
                        call = call,
                        modelContent = json,
                        display = "${page.title.ifBlank { url }.take(28)} · " + UiText.tf("%d 字", arrayOf(body.length)),
                        startedAt = startedAt,
                    )
                },
                onFailure = { fail(call, it.message ?: "抓取失败", "fetch_failed", startedAt) },
            )
        }

        else -> fail(call, "未知工具：${call.name}", "unknown_tool", startedAt)
    }

    /** 系统能力工具的统一包装：未注入、权限被拒、ContentResolver 异常都转成可读错误。 */
    private inline fun withSystem(
        call: ToolCall,
        startedAt: Long,
        block: (com.veluneagent.core.data.system.SystemCapabilities) -> ToolResult,
    ): ToolResult {
        val sys = system ?: return fail(call, "系统能力未初始化", "system_unavailable", startedAt)
        return try {
            block(sys)
        } catch (t: IllegalArgumentException) {
            fail(call, t.message ?: "参数不合法", "invalid_args", startedAt)
        } catch (t: SecurityException) {
            fail(call, t.message ?: "缺少系统权限", "permission_denied", startedAt)
        } catch (t: Throwable) {
            fail(call, t.message ?: t.javaClass.simpleName, "system_error", startedAt)
        }
    }

    /** 沙箱工具的统一包装：未初始化与越界都在这里变成可读错误，而不是抛异常。 */
    private inline fun withSandbox(
        call: ToolCall,
        startedAt: Long,
        block: (com.veluneagent.core.data.sandbox.Sandbox) -> ToolResult,
    ): ToolResult {
        val box = sandbox
            ?: return fail(call, "沙箱未初始化", "sandbox_unavailable", startedAt)
        return try {
            block(box)
        } catch (t: IllegalArgumentException) {
            fail(call, t.message ?: "路径不合法", "invalid_path", startedAt)
        } catch (t: Throwable) {
            fail(call, t.message ?: t.javaClass.simpleName, "sandbox_error", startedAt)
        }
    }

    private suspend fun installPlugin(
        call: ToolCall,
        args: JSONObject,
        startedAt: Long,
    ): ToolResult {
        val rawJson = args.optString("manifest_json").takeIf { it.isNotBlank() }
        val url = args.optString("manifest_url").takeIf { it.isNotBlank() }
        val raw = when {
            rawJson != null -> rawJson
            url != null -> {
                val uri = runCatching { URI(url) }.getOrNull()
                if (uri?.host.isNullOrBlank()) {
                    return fail(call, "manifest_url 不是合法地址", "invalid_url", startedAt)
                }
                val outcome = SimpleHttp.request(
                    method = "GET",
                    url = url,
                    headers = mapOf("Accept" to "application/json"),
                    timeoutMs = 10_000,
                )
                if (!outcome.ok) {
                    return fail(
                        call,
                        "下载插件清单失败：${outcome.error ?: "HTTP ${outcome.code}"}",
                        "fetch_failed",
                        startedAt,
                    )
                }
                outcome.body
            }

            else -> return fail(
                call,
                "需要提供 manifest_url 或 manifest_json",
                "invalid_args",
                startedAt,
            )
        }

        return when (val parsed = PluginManifestParser.parse(raw)) {
            is ManifestResult.Invalid -> fail(
                call,
                "清单校验未通过：" + parsed.errors.joinToString("；"),
                "invalid_manifest",
                startedAt,
            )

            is ManifestResult.Ok -> {
                when (val installed = plugins.install(parsed.manifest)) {
                    is com.veluneagent.core.data.plugin.InstallResult.Invalid -> fail(
                        call,
                        installed.errors.joinToString("；"),
                        "install_failed",
                        startedAt,
                    )

                    is com.veluneagent.core.data.plugin.InstallResult.Ok -> ok(
                        call = call,
                        modelContent = buildString {
                            append("""{"ok":true,"plugin_id":""")
                            append(installed.plugin.id.toJsonString())
                            append(""","name":""").append(installed.plugin.manifest.name.toJsonString())
                            append(""","tools":[""")
                            append(
                                installed.plugin.manifest.tools.joinToString(",") {
                                    it.name.toJsonString()
                                },
                            )
                            append("]}")
                        },
                        display = UiText.tf(
                            "已安装 %s，%d 个工具",
                            arrayOf<Any?>(installed.plugin.manifest.name, installed.plugin.manifest.tools.size),
                        ),
                        startedAt = startedAt,
                    )
                }
            }
        }
    }

    // ---------- 插件工具 ----------

    private fun runPlugin(
        spec: ToolSpec,
        call: ToolCall,
        args: JSONObject,
        startedAt: Long,
        runId: String?,
    ): ToolResult {
        val pluginId = spec.pluginId
            ?: return fail(call, "工具缺少插件来源", "broken_spec", startedAt)
        val plugin: InstalledPlugin = plugins.find(pluginId)
            ?: return fail(call, "插件 $pluginId 不存在或已卸载", "plugin_missing", startedAt)
        if (!plugin.enabled) {
            return fail(call, "插件 ${plugin.manifest.name} 已被停用", "plugin_disabled", startedAt)
        }
        val tool = plugin.manifest.tools.firstOrNull {
            PluginSpecName.matches(spec.name, pluginId, it.name)
        } ?: return fail(call, "插件里没有找到该工具", "tool_missing", startedAt)

        val url = PluginUrlBuilder.build(
            baseUrl = plugin.manifest.baseUrl,
            path = tool.path,
            method = tool.method,
            args = args,
        ) ?: return fail(call, "无法拼装请求地址", "invalid_url", startedAt)

        val host = runCatching { URI(url).host }.getOrNull()
        if (host.isNullOrBlank() || !PluginHosts.isHostAllowed(host, plugin.manifest.scopes)) {
            return fail(
                call,
                "目标域名 ${host ?: "未知"} 不在插件授权范围内（${plugin.manifest.scopes.joinToString()}）",
                "scope_denied",
                startedAt,
            )
        }

        val body = if (tool.method == "GET" || tool.method == "DELETE") {
            null
        } else {
            PluginUrlBuilder.bodyJson(args)
        }
        val outcome = SimpleHttp.request(
            method = tool.method,
            url = url,
            headers = plugin.manifest.headers,
            jsonBody = body,
            timeoutMs = tool.timeoutMs,
        )
        if (!outcome.ok) {
            return fail(
                call,
                outcome.error ?: "HTTP ${outcome.code}：${outcome.body.take(200)}",
                if (outcome.code == 0) "network_error" else "http_${outcome.code}",
                startedAt,
            )
        }
        val clipped = ToolOutput.clip(outcome.body)
        return ok(
            call = call,
            modelContent = clipped,
            display = "${plugin.manifest.name} · HTTP ${outcome.code} · " + UiText.tf("%d 字符", arrayOf(outcome.body.length)),
            startedAt = startedAt,
            runId = runId,
            pluginId = pluginId,
        )
    }

    // ---------- 结果构造 ----------

    private fun ok(
        call: ToolCall,
        modelContent: String,
        display: String,
        startedAt: Long,
        runId: String? = null,
        pluginId: String? = null,
    ): ToolResult {
        if (pluginId != null) {
            // scope 审计：每次插件网络调用都留痕，出问题能回溯到哪次运行
            val scope = plugins.find(pluginId)?.manifest?.scopes
                ?.firstOrNull { it.startsWith("net.") } ?: PluginHosts.ScopeHttp
            runCatching { auditor?.record(pluginId, scope, call.name, true, runId) }
        }
        return ToolResult(
            callId = call.id,
            name = call.name,
            ok = true,
            contentForModel = modelContent,
            displayText = display,
            durationMs = System.currentTimeMillis() - startedAt,
        )
    }

    private fun fail(call: ToolCall, message: String, code: String, startedAt: Long): ToolResult =
        ToolResult.failure(
            callId = call.id,
            name = call.name,
            message = message,
            code = code,
            durationMs = System.currentTimeMillis() - startedAt,
        )

    /** 把 spec 名还原成「插件 id + 工具名」的匹配。 */
    private object PluginSpecName {
        fun matches(specName: String, pluginId: String, toolName: String): Boolean =
            specName == com.veluneagent.core.data.plugin.PluginNaming.wireName(pluginId, toolName)
    }
}

/**
 * Lite 插件的 URL / body 拼装。
 *
 * 纯函数，单测覆盖：路径占位符必须编码（否则 `../` 能穿越到别的路由），
 * 未被占位符消费的参数才作为 query/body 发送。
 */
object PluginUrlBuilder {

    fun build(baseUrl: String, path: String, method: String, args: JSONObject): String? {
        var resolvedPath = path
        val used = mutableSetOf<String>()

        Regex("""\{([a-zA-Z0-9_]+)}""").findAll(path).forEach { match ->
            val key = match.groupValues[1]
            val value = args.opt(key)?.toString().orEmpty()
            used += key
            resolvedPath = resolvedPath.replace(match.value, encode(value))
        }

        if (resolvedPath.contains("..") || resolvedPath.contains("://")) return null

        val base = baseUrl.trimEnd('/')
        val full = base + (if (resolvedPath.startsWith("/")) resolvedPath else "/$resolvedPath")
        if (method != "GET" && method != "DELETE") return full

        val query = args.keys().asSequence()
            .filter { it !in used }
            .mapNotNull { key ->
                val value = args.opt(key)?.toString() ?: return@mapNotNull null
                "${encode(key)}=${encode(value)}"
            }
            .toList()
        return if (query.isEmpty()) full else "$full?" + query.joinToString("&")
    }

    fun bodyJson(args: JSONObject): String = args.toString()

    internal fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}


