package com.veluneagent.core.data.agent

import com.veluneagent.core.data.plugin.PluginStore

/**
 * 内置工具集。
 *
 * 设计原则：**能不给模型的能力就不给**。这里只有时间、计算、插件管理四件，
 * 没有文件读写、没有剪贴板、没有任意 HTTP —— 那些要么需要 scope 授权，要么属于插件。
 */
object BuiltinTools {

    const val NameNow = "now"
    const val NameCalc = "calc"
    const val NameListPlugins = "list_plugins"
    const val NameInstallPlugin = "install_plugin"
    const val NameUninstallPlugin = "uninstall_plugin"
    const val NameFsList = "fs_list"
    const val NameFsRead = "fs_read"
    const val NameFsWrite = "fs_write"
    const val NameFsDelete = "fs_delete"
    const val NameCalendarCreate = "calendar_create_event"
    const val NameCalendarList = "calendar_list_events"
    const val NameCalendarDelete = "calendar_delete_event"
    const val NameCalendarUpdate = "calendar_update_event"
    const val NameTodoAdd = "todo_add"
    const val NameTodoList = "todo_list"
    const val NameTodoDone = "todo_done"
    const val NameNotify = "notify"
    const val NameListApps = "list_apps"
    const val NameLaunchApp = "launch_app"
    const val NameReadSms = "read_sms"
    const val NameLockScreen = "lock_screen"
    const val NameWebSearch = "web_search"
    const val NameWebFetch = "web_fetch"
    const val NameQqSend = "qq_send"
    const val NameSshExec = "ssh_exec"
    const val NameTtsSpeak = "tts_speak"
    const val NameCodeRun = "code_run"
    const val NameUpdatePlan = "update_plan"
    const val NameDelegateTask = "delegate_task"

    /** 需要用户确认的内置工具：它们会改变应用状态、外部世界或引入新的网络能力。 */
    val approvalRequired = setOf(
        NameInstallPlugin,
        NameUninstallPlugin,
        NameFsWrite,
        NameFsDelete,
        // 在服务器上跑任意代码，影响面等同 ssh_exec
        NameCodeRun,
        // 写日历会在系统里留下真实事件、可能触发提醒，必须让用户看见
        NameCalendarCreate,
        // 删日程同样是不可逆的写操作，必须让用户看见
        NameCalendarDelete,
        NameCalendarUpdate,
        // 通知会打扰用户，同样需要确认
        NameNotify,
        // 启动别的应用会把用户从当前界面带走；锁屏更直接，两者都必须先问
        NameLaunchApp,
        NameLockScreen,
        // 消息一旦发出去就收不回来，必须是用户点过确认才发
        NameQqSend,
        // 在远端执行任意命令：破坏力取决于那台机器，必须逐次确认
        NameSshExec,
    )

    val all: List<ToolSpec> = listOf(
        ToolSpec(
            name = NameNow,
            displayName = "当前时间",
            description = "获取当前日期与时间。当用户询问今天几号、现在几点、距某日还有几天时调用。",
            parametersJson = """
                {"type":"object","properties":{"timezone":{"type":"string","description":"IANA 时区，例如 Asia/Shanghai；不传则用设备时区"}},"required":[]}
            """.trimIndent(),
            sideEffect = false,
            timeoutMs = 2_000,
            origin = "内置",
        ),
        ToolSpec(
            name = NameCalc,
            displayName = "四则运算",
            description = "计算只含数字、括号与 + - * / % 的算术表达式。复杂公式请分步计算。",
            parametersJson = """
                {"type":"object","properties":{"expression":{"type":"string","description":"例如 (128*3+56)/4"}},"required":["expression"]}
            """.trimIndent(),
            sideEffect = false,
            timeoutMs = 2_000,
            origin = "内置",
        ),
        ToolSpec(
            name = NameListPlugins,
            displayName = "列出已装插件",
            description = "列出当前已安装的插件及其可用工具。用户问「你有哪些插件/能力」时调用。",
            parametersJson = """{"type":"object","properties":{},"required":[]}""",
            sideEffect = false,
            timeoutMs = 2_000,
            origin = "内置",
        ),
        ToolSpec(
            name = NameInstallPlugin,
            displayName = "安装插件",
            description = "安装一个声明式 HTTP 插件。用户提供了插件清单 URL 时传 manifest_url；用户直接粘贴了 JSON 清单时传 manifest_json。安装需要用户确认，安装后工具立即可用。",
            parametersJson = """
                {"type":"object","properties":{"manifest_url":{"type":"string","description":"清单的 https 地址"},"manifest_json":{"type":"string","description":"清单 JSON 原文"}},"required":[]}
            """.trimIndent(),
            sideEffect = true,
            timeoutMs = 15_000,
            origin = "内置",
        ),
        ToolSpec(
            name = NameUninstallPlugin,
            displayName = "卸载插件",
            description = "按插件 id 卸载已安装的插件。用户要求移除某个插件时调用。",
            parametersJson = """
                {"type":"object","properties":{"plugin_id":{"type":"string","description":"插件 id，例如 com.example.tools"}},"required":["plugin_id"]}
            """.trimIndent(),
            sideEffect = true,
            timeoutMs = 3_000,
            origin = "内置",
        ),
        ToolSpec(
            name = NameFsList,
            displayName = "列目录",
            description = "列出沙箱工作目录下的文件与子目录。用户让你看看有哪些文件、找某个文件时调用。",
            parametersJson = """
                {"type":"object","properties":{"path":{"type":"string","description":"相对路径，默认根目录 /"}},"required":[]}
            """.trimIndent(),
            sideEffect = false,
            // 只读，但能列出用户目录结构（文件名本身就带信息量）：自动批准的场景必须排除
            sensitive = true,
            timeoutMs = 3_000,
            origin = "内置",
        ),
        ToolSpec(
            name = NameFsRead,
            displayName = "读文件",
            description = "读取沙箱内文本文件的内容。文件超过 256KB 会被拒绝，读取长度可限制。",
            parametersJson = """
                {"type":"object","properties":{"path":{"type":"string","description":"相对路径"},"max_chars":{"type":"integer","minimum":200,"maximum":20000,"description":"最多读取多少字符，默认 4000"}},"required":["path"]}
            """.trimIndent(),
            sideEffect = false,
            // 只读，但文件内容可能包含密钥、日志、私人笔记：只能由用户在本机触发
            sensitive = true,
            timeoutMs = 5_000,
            origin = "内置",
        ),
        ToolSpec(
            name = NameFsWrite,
            displayName = "写文件",
            description = "把文本写入沙箱文件（可追加）。生成报告、保存中间结果、改写文件时调用。单次上限 512KB。",
            parametersJson = """
                {"type":"object","properties":{"path":{"type":"string","description":"相对路径"},"content":{"type":"string","description":"要写入的文本"},"append":{"type":"boolean","description":"true 表示追加，默认覆盖"}},"required":["path","content"]}
            """.trimIndent(),
            sideEffect = true,
            timeoutMs = 5_000,
            origin = "内置",
        ),
        ToolSpec(
            name = NameFsDelete,
            displayName = "删除文件",
            description = "删除沙箱内的文件或目录（目录会递归删除）。仅在用户明确要求清理时调用。",
            parametersJson = """
                {"type":"object","properties":{"path":{"type":"string","description":"相对路径"}},"required":["path"]}
            """.trimIndent(),
            sideEffect = true,
            timeoutMs = 3_000,
            origin = "内置",
        ),
        ToolSpec(
            name = NameCalendarCreate,
            displayName = "新建日程",
            description = "在系统日历里创建一条事件。必须提供开始时间：用户说「半小时后」用 start_in_minutes；说「明天下午三点」用 start_day_offset + start_hour；说「三点到四点」再给 end_hour/end_minute；说「开一小时」用 duration_minutes。不要自己编造日期，拿不准就先调用 now 工具。",
            parametersJson = """
                {"type":"object","properties":{
                  "title":{"type":"string","description":"事件标题"},
                  "start_in_minutes":{"type":"integer","minimum":0,"maximum":10080,"description":"从现在起多少分钟后开始（半小时后=30，两小时后=120）"},
                  "start_day_offset":{"type":"integer","minimum":0,"maximum":365,"description":"0=今天，1=明天，2=后天；配合 start_hour 使用"},
                  "start_hour":{"type":"integer","minimum":0,"maximum":23,"description":"开始小时（24 小时制，下午三点=15）"},
                  "start_minute":{"type":"integer","minimum":0,"maximum":59,"description":"开始分钟，默认 0"},
                  "duration_minutes":{"type":"integer","minimum":5,"maximum":1440,"description":"持续时长（分钟），默认 60"},
                  "end_hour":{"type":"integer","minimum":0,"maximum":23,"description":"结束小时，仅当用户明确说了结束时间才用"},
                  "end_minute":{"type":"integer","minimum":0,"maximum":59,"description":"结束分钟"},
                  "location":{"type":"string","description":"地点，可选"},
                  "description":{"type":"string","description":"备注，可选"},
                  "reminder_minutes":{"type":"integer","minimum":0,"maximum":1440,"description":"提前多少分钟提醒，默认 10；用户说不用提醒传 0"},
                  "all_day":{"type":"boolean","description":"是否全天事件"},
                  "repeat":{"type":"string","enum":["none","daily","weekly","monthly"],"description":"重复规则：none=不重复（默认）；用户说「每周一」用 weekly，说「每天」用 daily"}
                },"required":["title"]}
            """.trimIndent(),
            sideEffect = true,
            timeoutMs = 8_000,
            origin = "内置",
        ),
        ToolSpec(
            name = NameCalendarList,
            displayName = "查日程",
            description = "查询某天（默认今天）的系统日历事件。用户问「我今天有什么安排」时调用。",
            parametersJson = """
                {"type":"object","properties":{
                  "day_offset":{"type":"integer","minimum":0,"maximum":365,"description":"0=今天，1=明天"},
                  "days":{"type":"integer","minimum":1,"maximum":14,"description":"查询天数，默认 1"}
                },"required":[]}
            """.trimIndent(),
            sideEffect = false,
            // 只读，但日程标题/地点属于私人生活信息：自动批准的场景必须排除
            sensitive = true,
            timeoutMs = 8_000,
            origin = "内置",
        ),
        ToolSpec(
            name = NameCalendarDelete,
            displayName = "删除日程",
            description = "按 event_id 删除一条日程（重复事件会删掉整个系列）。先用 calendar_list_events 拿到 event_id，删除前向用户复述是哪一条，不要凭标题猜。",
            parametersJson = """
                {"type":"object","properties":{
                  "event_id":{"type":"integer","description":"calendar_list_events 返回的 id"}
                },"required":["event_id"]}
            """.trimIndent(),
            sideEffect = true,
            timeoutMs = 8_000,
            origin = "内置",
        ),
        ToolSpec(
            name = NameCalendarUpdate,
            displayName = "修改日程",
            description = "按 event_id 修改已有日程的标题、时间或提醒，只传要改的字段。用户说「把明天那个会挪到三点」「改成两小时」「提前半小时提醒」时用它，不要删了重建——重建会换掉 event_id。先用 calendar_list_events 拿到 id。",
            parametersJson = """
                {"type":"object","properties":{
                  "event_id":{"type":"integer","description":"calendar_list_events 返回的 id"},
                  "title":{"type":"string","description":"新标题，不改就别传"},
                  "start_in_minutes":{"type":"integer","minimum":0,"maximum":10080,"description":"改到从现在起多少分钟后开始"},
                  "start_day_offset":{"type":"integer","minimum":0,"maximum":365,"description":"0=今天，1=明天；配合 start_hour"},
                  "start_hour":{"type":"integer","minimum":0,"maximum":23,"description":"新开始小时（24 小时制）"},
                  "start_minute":{"type":"integer","minimum":0,"maximum":59,"description":"新开始分钟"},
                  "duration_minutes":{"type":"integer","minimum":5,"maximum":1440,"description":"改时长（分钟）"},
                  "end_hour":{"type":"integer","minimum":0,"maximum":23,"description":"新结束小时"},
                  "end_minute":{"type":"integer","minimum":0,"maximum":59,"description":"新结束分钟"},
                  "all_day":{"type":"boolean","description":"改成/取消全天"},
                  "reminder_minutes":{"type":"integer","minimum":0,"maximum":1440,"description":"新提醒提前分钟数"}
                },"required":["event_id"]}
            """.trimIndent(),
            sideEffect = true,
            timeoutMs = 8_000,
            origin = "内置",
        ),
        ToolSpec(
            name = NameTodoAdd,
            displayName = "加待办",
            description = "把一件事加入本地待办清单，到点会发通知。用户说「记一下要做 X」「明天提醒我 Y」时调用。给了 day_offset 才会提醒；不传只是清单里的一条。",
            parametersJson = """
                {"type":"object","properties":{
                  "title":{"type":"string","description":"待办内容"},
                  "day_offset":{"type":"integer","minimum":0,"maximum":365,"description":"截止日相对天数：0=今天，1=明天；不传表示无截止日、不会提醒"},
                  "hour":{"type":"integer","minimum":0,"maximum":23,"description":"提醒时刻（24 小时制），默认 9 点"},
                  "minute":{"type":"integer","minimum":0,"maximum":59,"description":"提醒分钟，默认 0"}
                },"required":["title"]}
            """.trimIndent(),
            sideEffect = true,
            timeoutMs = 3_000,
            origin = "内置",
        ),
        ToolSpec(
            name = NameTodoList,
            displayName = "看待办",
            description = "列出本地待办。用户问「我还有什么事没做」时调用。",
            parametersJson = """
                {"type":"object","properties":{"include_done":{"type":"boolean","description":"是否包含已完成，默认否"}},"required":[]}
            """.trimIndent(),
            sideEffect = false,
            // 只读，但待办标题能反映用户在做什么：自动批准的场景必须排除
            sensitive = true,
            timeoutMs = 3_000,
            origin = "内置",
        ),
        ToolSpec(
            name = NameTodoDone,
            displayName = "完成待办",
            description = "把某条待办标记为完成。需要先从 todo_list 拿到 id。",
            parametersJson = """
                {"type":"object","properties":{"id":{"type":"integer","description":"待办 id"}},"required":["id"]}
            """.trimIndent(),
            sideEffect = true,
            timeoutMs = 3_000,
            origin = "内置",
        ),
        ToolSpec(
            name = NameNotify,
            displayName = "发通知",
            description = "发一条本地通知提醒用户。适合「到点提醒我」这类即时提醒；长期提醒请用日历事件。",
            parametersJson = """
                {"type":"object","properties":{"title":{"type":"string"},"body":{"type":"string","description":"通知正文"}},"required":["title","body"]}
            """.trimIndent(),
            sideEffect = true,
            timeoutMs = 3_000,
            origin = "内置",
        ),
        ToolSpec(
            name = NameListApps,
            displayName = "应用列表",
            description = "列出手机里可启动的应用（名称与包名），可用 query 按名称过滤。用户问「装了哪些应用」「有没有某个 app」时调用。",
            parametersJson = """
                {"type":"object","properties":{"query":{"type":"string","description":"按名称或包名过滤，可选"}},"required":[]}
            """.trimIndent(),
            sideEffect = false,
            timeoutMs = 5_000,
            origin = "内置",
        ),
        ToolSpec(
            name = NameLaunchApp,
            displayName = "启动应用",
            description = "打开手机上的一个应用。参数用应用名或包名都可以；不确定名字时先调用 list_apps 查。",
            parametersJson = """
                {"type":"object","properties":{"target":{"type":"string","description":"应用名（如 微信）或包名（如 com.tencent.mm）"}},"required":["target"]}
            """.trimIndent(),
            sideEffect = true,
            timeoutMs = 8_000,
            origin = "内置",
        ),
        ToolSpec(
            name = NameReadSms,
            displayName = "读短信",
            description = "读取短信收件箱里最近的几条。用户问「最近谁给我发短信」「有没有验证码」时调用；不要主动读取无关短信。",
            parametersJson = """
                {"type":"object","properties":{"limit":{"type":"integer","minimum":1,"maximum":20,"description":"读取条数，默认 5"},"from":{"type":"string","description":"按号码或备注过滤，可选"}},"required":[]}
            """.trimIndent(),
            sideEffect = false,
            // 只读，但能读出验证码与私人短信：自动批准的场景（QQ 远程）必须排除
            sensitive = true,
            timeoutMs = 8_000,
            origin = "内置",
        ),
        ToolSpec(
            name = NameSshExec,
            displayName = "远程执行",
            description = "在用户配置的 Linux 服务器上通过 SSH 执行一条命令，返回退出码与输出。适合查日志、看磁盘、重启服务这类运维操作；需要先在 设置 → 远程服务器 配好主机。命令要写完整（可用 && 串联），不要假设交互式环境。装依赖、构建这类慢命令用 timeout_sec 放长（最长 300 秒）。",
            parametersJson = """
                {"type":"object","properties":{
                  "command":{"type":"string","description":"要执行的 shell 命令，例如 df -h"},
                  "timeout_sec":{"type":"integer","minimum":5,"maximum":300,"description":"超时秒数，默认 60；装依赖/构建可给到 300"}
                },"required":["command"]}
            """.trimIndent(),
            sideEffect = true,
            timeoutMs = 310_000,
            origin = "内置",
        ),
        ToolSpec(
            name = NameQqSend,
            displayName = "发 QQ 消息",
            description = "把一条消息发送到 QQ 私聊或群。需要先在 设置 → 插件 → QQ 机器人 配好协议端地址与默认目标。用户说「发到我 QQ」「同步到群里」时调用；内容必须是你已经整理好的最终版本。",
            parametersJson = """
                {"type":"object","properties":{
                  "text":{"type":"string","description":"消息正文，最多 4000 字"},
                  "to":{"type":"string","description":"QQ 号或群号，不填则用配置里的默认目标"},
                  "as_group":{"type":"boolean","description":"true 表示发到群，默认按配置"}
                },"required":["text"]}
            """.trimIndent(),
            sideEffect = true,
            timeoutMs = 20_000,
            origin = "插件",
        ),
        ToolSpec(
            name = NameWebSearch,
            displayName = "联网搜索",
            description = "用搜索引擎查最新信息。适合新闻、价格、版本发布、实时事实等模型知识可能过期的内容；已知常识不要搜。支持 site:、after:、before:、lang:、\"-词\" 等搜索语法。需要已配置 DeepSeek 服务商（搜索接口使用 DeepSeek 的 Key），每次调用约 0.005 元。",
            parametersJson = """
                {"type":"object","properties":{
                  "query":{"type":"string","description":"搜索词，1-128 字符，支持 site:/after:/before:/lang: 等语法"},
                  "count":{"type":"integer","minimum":1,"maximum":10,"description":"返回条数，默认 5；条数越多越占上下文"}
                },"required":["query"]}
            """.trimIndent(),
            sideEffect = false,
            // 按次计费（约 0.005 元/次）。不标 costly 的话，QQ 只读白名单
            // 会把它放进来 —— 未授权来源可以无限次替用户花钱。
            costly = true,
            timeoutMs = 25_000,
            origin = "内置",
        ),
        ToolSpec(
            name = NameWebFetch,
            displayName = "抓取网页",
            description = "抓取一个网页并返回 Markdown 正文。用于读搜索结果里最相关那一篇的完整内容；只支持 http/https，正文会被截断。同样需要 DeepSeek 服务商。",
            parametersJson = """
                {"type":"object","properties":{"url":{"type":"string","description":"完整的 http/https 链接"}},"required":["url"]}
            """.trimIndent(),
            sideEffect = false,
            // 同样按次计费；更重要的是它能把任意 URL 的查询串拼上用户内容发出去，
            // 不该在没有人为成本把关的情况下对 QQ 来源开放。
            costly = true,
            timeoutMs = 30_000,
            origin = "内置",
        ),
        ToolSpec(
            name = NameDelegateTask,
            displayName = "派发子任务",
            description = "把一个独立子任务交给子模型执行，只拿回结论。适合「读一大段材料再总结/抽取」这类会撑爆主上下文的工作：原文留在子模型那边，主对话只收到结果。子模型没有工具权限，不能执行命令、读文件或联网，只能推理与写作。",
            parametersJson = """
                {"type":"object","properties":{
                  "task":{"type":"string","description":"要子模型完成的事，写成一句明确指令，例如「把下面的日志归成三类故障并各给一条建议」"},
                  "context":{"type":"string","description":"完成任务所需的材料，可较长；会整段交给子模型，不占用主对话上下文"}
                },"required":["task"]}
            """.trimIndent(),
            sideEffect = false,
            // 真实产生一次模型调用（可能用另一家的 Key），属于花钱的工具
            costly = true,
            timeoutMs = 180_000,
            origin = "内置",
        ),
        ToolSpec(
            name = NameLockScreen,
            displayName = "锁屏",
            description = "立即锁屏。需要先在应用设置的「权限与能力」里激活设备管理员；未激活时会返回失败原因。",
            parametersJson = """
                {"type":"object","properties":{},"required":[]}
            """.trimIndent(),
            sideEffect = true,
            timeoutMs = 3_000,
            origin = "内置",
        ),
        ToolSpec(
            name = NameTtsSpeak,
            displayName = "发送语音",
            description = "把一段文字合成语音（小米 MiMo TTS），生成可播放的音频文件。用户说「用语音说」「发条语音」「读给我听」时调用。text 必须是最终要朗读的文本：不要带 markdown、不要带链接清单、不要复述「好的我来读」。voice 可选（mimo_default / 冰糖 / 茉莉 / 苏打 / 白桦 等），style 可写「开心地说」「四川话」这类语气要求。",
            parametersJson = """
                {"type":"object","properties":{"text":{"type":"string","description":"要朗读的正文，1-600 字"},"voice":{"type":"string","description":"音色，默认 mimo_default"},"style":{"type":"string","description":"语气/风格指令，如「温柔地」「四川话」，可选"}},"required":["text"]}
            """.trimIndent(),
            // 不改外部世界，只生成本地文件；但每一次都花 TTS 配额
            sideEffect = false,
            costly = true,
            timeoutMs = 120_000,
            origin = "内置（小米 MiMo TTS）",
        ),
        ToolSpec(
            name = NameUpdatePlan,
            displayName = "更新计划",
            description = "多步任务开始时先列计划，每完成一步就重写整份清单（状态 pending/running/done）。用户能看到进度，也能纠正方向。单步任务不要用。",
            parametersJson = """
                {"type":"object","properties":{"steps":{"type":"array","maxItems":10,"items":{"type":"object","properties":{"title":{"type":"string","description":"这一步做什么，短句"},"status":{"type":"string","enum":["pending","running","done"]}},"required":["title","status"]}}},"required":["steps"]}
            """.trimIndent(),
            sideEffect = false,
            timeoutMs = 2_000,
            origin = "内置",
        ),
        ToolSpec(
            name = NameCodeRun,
            displayName = "运行代码",
            description = "运行一段代码（python3 / node / java / bash），返回退出码与输出。where=local 在手机内的 Ubuntu 里跑（需用户先下载环境），where=remote 在用户服务器上跑，auto 优先本地。适合验算、处理文本、验证脚本逻辑。装依赖、编译这类慢任务用 timeout_sec 放长（最长 300 秒）。代码写在临时文件里执行完即删；不能用它做长期服务。",
            parametersJson = """
                {"type":"object","properties":{"language":{"type":"string","enum":["python","node","java","bash"],"description":"语言"},"code":{"type":"string","description":"完整可运行的源码，不要片段"},"where":{"type":"string","enum":["auto","local","remote"],"description":"在哪跑：local=手机内的 Ubuntu（需已下载），remote=SSH 服务器，auto=本地就绪则本地"},"timeout_sec":{"type":"integer","minimum":5,"maximum":300,"description":"超时秒数，默认 60；装依赖/编译可给到 300"}},"required":["language","code"]}
            """.trimIndent(),
            // 在别人的机器上执行任意代码：必须走审批，不能静默跑
            sideEffect = true,
            // 工具级兜底必须比 timeout_sec 上限更大，否则模型要求 300s 也会被这里先掐断
            timeoutMs = 310_000,
            origin = "内置（远端）",
        ),
    )

    fun find(name: String): ToolSpec? = all.firstOrNull { it.name == name }
}

/**
 * 工具目录：内置工具 + 已启用插件工具。
 *
 * 每次取 spec 都重新拼装，所以插件装完/卸载后**下一次模型调用**就能看到新工具，
 * 不需要重启进程 —— 这是「Agent 自己装插件」能被用户感知的前提。
 */
class ToolCatalog(
    private val plugins: PluginStore,
    /** MCP 工具是运行时拉取的，可能为空（没配服务器，或还没刷新成功） */
    private val mcp: com.veluneagent.core.data.mcp.McpRegistry? = null,
) : ToolProvider {

    private fun mcpSpecs(): List<ToolSpec> = mcp?.specs().orEmpty()

    /** 全部工具（含被用户停用的）：设置页要能重新打开它们。 */
    fun allSpecs(): List<ToolSpec> =
        BuiltinTools.all + plugins.enabledToolSpecs() + mcpSpecs()

    override fun specs(): List<ToolSpec> =
        allSpecs().filter { ToolPrefs.isEnabled(it.name) }

    override fun find(name: String): ToolSpec? {
        if (!ToolPrefs.isEnabled(name)) return null
        return BuiltinTools.find(name)
            ?: plugins.enabledToolSpecs().firstOrNull { it.name == name }
            ?: mcpSpecs().firstOrNull { it.name == name }
    }

    /**
     * 是否需要用户确认。
     * 插件工具一律 Ask：它们的网络行为在安装时被授权，但每次调用仍要让用户看见目标。
     * MCP 同理，而且更进一步：MCP 协议里没有"这个工具是否只读"的声明，我们无从判断。
     */
    override fun requiresApproval(spec: ToolSpec): Boolean =
        spec.name in BuiltinTools.approvalRequired ||
            ToolPrefs.requiresApproval(spec.name) ||
            spec.source == ToolSource.Plugin ||
            spec.source == ToolSource.Mcp

    /** 审批卡上要展示这个工具会用到哪些权限；内置工具不涉及插件 scope。 */
    override fun scopesOf(spec: ToolSpec): List<String> =
        spec.pluginId?.let { id -> plugins.find(id)?.manifest?.scopes }.orEmpty()
}
