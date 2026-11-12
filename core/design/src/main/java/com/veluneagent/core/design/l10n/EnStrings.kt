package com.veluneagent.core.design.l10n

/**
 * 英文词典。key = 中文原文（与调用点书写的字符串完全一致，包括标点与空格），
 * 未收录的 key 由 [tr] 回落中文——漏词条不会崩，只会该处显示中文。
 *
 * 词条按界面分节。插值类文案的 key 用 %d / %s 占位（配合 trf，%n$ 转义写成 %n\$）。
 * 法律文本（用户协议 / 隐私政策）与诊断输出暂未收录，维持中文原文。
 */
val EnStrings: Map<String, String> = mapOf(
    // ================= 通用 =================
    "取消" to "Cancel",
    "确认" to "Confirm",
    "确定" to "OK",
    "保存" to "Save",
    "删除" to "Delete",
    "返回" to "Back",
    "完成" to "Done",
    "刷新" to "Refresh",
    "刷新中…" to "Refreshing…",
    "重试" to "Retry",
    "重试连接" to "Retry connection",
    "清除" to "Clear",
    "已清除" to "Cleared",
    "展开" to "Expand",
    "收起" to "Collapse",
    "设置" to "Settings",
    "消息" to "Messages",
    "我的" to "Me",
    "编辑" to "Edit",
    "管理" to "Manage",
    "连接" to "Connect",
    "连接中…" to "Connecting…",
    "连接失败" to "Connection failed",
    "断开连接" to "Disconnect",
    "未连接" to "Not connected",
    "未配置" to "Not configured",
    "未安装" to "Not installed",
    "已保存" to "Saved",
    "已创建" to "Created",
    "已删除" to "Deleted",
    "已开启" to "On",
    "未开启" to "Off",
    "已启用" to "Enabled",
    "已停用" to "Disabled",
    "已安装" to "Installed",
    "测试" to "Test",
    "测试连接" to "Test connection",
    "检测中…" to "Checking…",
    "已就绪" to "Ready",
    "失败" to "Failed",
    "说明" to "About",
    "状态" to "Status",
    "名称" to "Name",
    "地址" to "Address",
    "端口" to "Port",
    "密钥" to "API key",
    "密码" to "Password",
    "用户名" to "Username",
    "私钥" to "Private key",
    "默认" to "Default",
    "默认 · " to "Default · ",
    "使用中" to "In use",
    "缺密钥" to "No key",
    "未命名" to "Unnamed",
    "未选择" to "None selected",
    "已配置" to "Configured",
    "去配置" to "Set up",
    "去授权" to "Grant",
    "去激活" to "Activate",
    "已授权" to "Granted",
    "已激活" to "Activated",
    "关于" to "About",
    "角色" to "Roles",
    "插件" to "Plugins",
    "存储" to "Storage",
    "模型" to "Model",
    "打开系统设置" to "Open system settings",
    "放弃修改" to "Discard changes",
    "放弃修改？" to "Discard changes?",
    "继续编辑" to "Keep editing",
    "重试连接" to "Retry connection",
    "%d 个" to "%d items",
    "%d 条" to "%d items",
    "%d 个工具" to "%d tools",
    "%d 个服务器" to "%d servers",
    "个工具" to "tools",

    // ================= 底栏 / 主壳 =================
    "新对话" to "New chat",
    "本地用户" to "Local user",
    "本地账户 · 数据不上传" to "Local account · data never leaves this device",
    "你的昵称" to "Your nickname",
    "比如：小林" to "e.g. Lin",
    "清空全部对话" to "Clear all chats",
    "将删除 %d 个会话、%d 条消息，无法恢复。" to
        "This will delete %d conversations and %d messages. This cannot be undone.",
    "清空全部" to "Clear all",
    "同意并继续" to "Agree and continue",
    "不同意" to "Decline",
    "本地优先的 AI 对话工具，会话与密钥只保存在这台设备上。\n开始前请阅读并同意以下条款。" to
        "A local-first AI chat tool. Chats and keys stay on this device only.\n" +
        "Please read and accept the terms below to continue.",
    "服务说明、使用规范、免责声明" to "Service terms, usage rules, disclaimers",
    "收集范围、第三方共享、你的权利" to "What we collect, third-party sharing, your rights",
    "欢迎使用 Velune。本应用是一款本地优先的 AI 对话工具，使用前请阅读以下条款。" to
        "Welcome to Velune. This is a local-first AI chat tool. Please read the terms below before use.",
    "Velune %s · 本地优先" to "Velune %s · Local-first",
    "剪贴板为空，先复制插件清单 JSON" to "Clipboard is empty. Copy a plugin manifest JSON first.",
    "没有可打开该链接的应用" to "No app can open this link",
    "反馈文档地址未配置" to "Feedback doc URL not set",
    "群链接未配置" to "Group link not set",
    "项目仓库地址尚未公布" to "Repository URL not published yet",
    "捐赠入口尚未开放" to "Donation not open yet",
    "还没有会话可以导出" to "No chats to export yet",
    "已导出 %d 个会话 → %s（可在「产出文件」里打开或分享）" to
        "Exported %d chats → %s (open or share from \"Output files\")",
    "导出失败：%s" to "Export failed: %s",
    "无法写入沙箱" to "cannot write to sandbox",

    // ================= 设置主页 =================
    "模型与密钥" to "Models & keys",
    "Agent 与工具" to "Agent & tools",
    "权限与能力" to "Permissions",
    "对话与 Agent" to "Chats & agent",
    "数据与存储" to "Data & storage",
    "远程服务器" to "Remote server",
    "Linux 环境" to "Linux environment",
    "开源许可" to "Open-source licenses",
    "支持我们" to "Support us",
    "应用偏好设置" to "Appearance",
    "应用偏好设置：浅色 / 深色、自动切换、莫奈取色、语言与字号" to
        "Appearance: light / dark, auto switch, Material You color, language & font size",
    "未配置 · 点此添加" to "Not configured · tap to add",
    "%d 个服务商 · %s" to "%d providers · %s",
    "下载中" to "Downloading",
    "解压中" to "Extracting",
    "安装失败" to "Install failed",
    "这个功能是干什么的" to "What does this do",
    "使用统计" to "Usage stats",
    "这是什么" to "What is this",
    "这是哪个应用、什么版本、去哪反馈" to "Which app, which version, where to give feedback",
    "给项目点个 Star，或请我喝杯咖啡" to "Star the project, or buy me a coffee",
    "遇到问题、想提建议，或者只是想聊聊，两个渠道都能找到我" to
        "Found a bug, have a suggestion, or just want to chat — both channels reach me",
    "加入 QQ 群" to "Join QQ group",
    "捐赠支持" to "Donate",
    "反馈文档" to "Feedback doc",
    "项目仓库" to "Repository",
    "用户协议" to "Terms of Service",
    "隐私政策" to "Privacy Policy",
    "隐私" to "Privacy",
    "一、服务说明" to "1. Service description",
    "二、你的数据" to "2. Your data",
    "三、使用规范" to "3. Usage rules",
    "点进去看列表" to "Tap to view the list",

    // ================= 应用偏好（外观） =================
    "语言" to "Language",
    "字号" to "Font size",
    "切换应用界面显示语言" to "Switch the app's display language",
    "调整应用内文字大小，不影响系统设置" to "Adjust text size inside the app; system setting untouched",
    "浅色模式" to "Light",
    "深色模式" to "Dark",
    "浅色" to "Light",
    "深色" to "Dark",
    "自动切换深色模式" to "Auto dark mode",
    "莫奈取色" to "Material You color",
    "跟随系统壁纸对应用进行配色" to "Color the app from your wallpaper",
    "需要 Android 12 及以上系统" to "Requires Android 12 or later",
    "应用设置" to "App settings",
    "跟随系统" to "Follow system",
    "简体中文" to "简体中文",
    "界面已支持中英双语，选择即时生效；「跟随系统」时与设备语言保持一致。字号总放大上限为 1.8 倍（系统字号会与应用字号相乘）。" to
        "The app supports Chinese and English; changes apply instantly. \"Follow system\" tracks the device language." +
        " Font size scales up to 1.8x (multiplied with the system font scale).",
    "小" to "Small",
    "标准" to "Standard",
    "大" to "Large",
    "特大" to "Extra large",

    // ================= 角色 =================
    "每个角色是一套人设。新建对话时可以挑一个，标为「默认」的会用在没挑角色的对话里。" to
        "Each role is a persona. Pick one when starting a chat; the one marked \"Default\" is used in chats without a role.",
    "我的角色" to "My roles",
    "还没有角色" to "No roles yet",
    "新建一个，写清「你是谁、怎么说话、不要做什么」。" to
        "Create one and write down: who you are, how you talk, what not to do.",
    "还没有写设定" to "No persona written",
    "新建角色" to "New role",
    "新角色" to "New role",
    "设定" to "Persona",
    "名称，如「温柔陪伴」" to "Name, e.g. \"Gentle companion\"",
    "你是……\n说话方式：……\n不要：……" to "You are…\nSpeaking style:…\nNever:…",
    "变量：{{now}} {{user_name}}" to "Variables: {{now}} {{user_name}}",
    "%1\$d 字 · 约 %2\$d token/轮" to "%1\$d chars · ~%2\$d tokens/turn",
    "这是当前的默认角色。" to "This is the current default role.",
    "设为默认" to "Set as default",
    "已保存并设为默认" to "Saved and set as default",
    "删除这个角色" to "Delete this role",
    "删除角色？" to "Delete role?",
    "「%s」会被删除，删除后无法恢复。" to "\"%s\" will be deleted. This cannot be undone.",
    "拼装预览" to "Prompt preview",
    "上面灰色部分是平台规则，不能改；你写的内容永远排在它后面——所以「不要用工具」这类话不会真的关掉工具。" to
        "The gray part above is platform policy and cannot be changed; your persona always comes after it — " +
        "so \"don't use tools\" won't actually disable tools.",
    "QQ 回复会在此基础上自动追加「三句以内、不用 markdown」，这部分由平台固定。" to
        "QQ replies auto-append \"within 3 sentences, no markdown\" on top of this; that part is fixed by the platform.",
    "已有同名角色，换个名字" to "A role with this name already exists. Try another.",
    "保存失败：%s" to "Save failed: %s",
    "未知错误" to "unknown error",
    "这次的改动还没有保存。" to "Your changes are not saved yet.",

    // ================= MCP =================
    "MCP 服务器" to "MCP servers",
    "还没有 MCP 服务器" to "No MCP servers yet",
    "填一个 HTTP 地址，握手成功后它的工具会自动出现在 Agent 里" to
        "Enter an HTTP URL; after a successful handshake its tools appear in the Agent automatically",
    "添加服务器" to "Add server",
    "编辑服务器" to "Edit server",
    "%d 个工具" to "%d tools",
    "已停用「%s」" to "Disabled \"%s\"",
    "已删除「%s」" to "Deleted \"%s\"",
    "已连接，共 %d 个工具" to "Connected, %d tools in total",
    "共 %d 个工具" to "%d tools in total",
    "基本信息" to "Basics",
    "名称，如「文件工具」" to "Name, e.g. \"File tools\"",
    "服务器地址，如 https://example.com/mcp" to "Server URL, e.g. https://example.com/mcp",
    "启用这个服务器" to "Enable this server",
    "它的工具会出现在 Agent 的可调用列表里" to "Its tools appear in the Agent's callable list",
    "已停用：工具从 Agent 里移除，配置与令牌保留" to
        "Disabled: tools removed from the Agent; config and token are kept",
    "鉴权（可选）" to "Auth (optional)",
    "只填一行，冒号前的部分是请求头名。留空表示不需要鉴权。" to
        "One line only; text before the colon is the header name. Leave empty if no auth is needed.",
    "服务器地址不能为空" to "Server URL cannot be empty",
    "地址不是合法的 URL" to "Not a valid URL",
    "地址里没有主机名" to "URL has no host name",
    "只支持 https://（本机调试可用 http://localhost 或 http://127.0.0.1）" to
        "Only https:// is allowed (for local debugging use http://localhost or http://127.0.0.1)",
    "请求头格式应为「名称: 值」，例如 Authorization: Bearer xxx" to
        "Header format should be \"Name: Value\", e.g. Authorization: Bearer xxx",
    "不支持 Cookie 头：会话凭据无法单独撤销，请改用 Authorization" to
        "Cookie headers are not supported: session credentials can't be revoked individually. Use Authorization instead.",
    "保存并连接" to "Save & connect",
    "删除这个服务器" to "Delete this server",
    "未命名服务器" to "Unnamed server",
    "保存后会立刻发起一次握手。连不上通常是：地址路径不对（很多服务器是 /mcp 或 /sse）、缺少鉴权头、或服务器只支持 stdio 传输。" to
        "A handshake fires right after saving. Common causes of failure: wrong path (many servers use /mcp or /sse), " +
        "missing auth header, or the server only supports stdio transport.",
    "MCP 工具全部需要逐次确认：协议里没有「是否只读」的声明，我们无从判断，所以按有副作用处理。服务器由你自行配置，其行为由该服务器负责。" to
        "Every MCP tool requires per-call confirmation: the protocol declares nothing about read-only-ness, " +
        "so we treat them all as side-effecting. You configure the server yourself; its behavior is its own responsibility.",

    // ================= Linux 环境 =================
    "未安装" to "Not installed",
    "正在下载 %d%%" to "Downloading %d%%",
    "正在解压 %d%%" to "Extracting %d%%",
    "已就位，正在验证 proot 能否启动…" to "In place; verifying that proot can start…",
    "Ubuntu 24.04 base（arm64）· 下载约 %dMB · 解压后约 300MB" to
        "Ubuntu 24.04 base (arm64) · ~%dMB download · ~300MB extracted",
    "删除已下载的环境" to "Delete downloaded environment",
    "安装中…" to "Installing…",
    "下载并安装" to "Download & install",
    "运行时" to "Runtime",
    "proot 已就位，可以直接运行代码" to "proot is ready; code can run directly",
    "缺少 proot 运行时" to "proot runtime missing",
    "Agent 的 code_run 在 where=local 时会走这里；也可以用 apt 装需要的软件包。" to
        "Agent code_run uses this when where=local; you can also install packages with apt.",
    "rootfs 与 proot 是两件事：前者是可下载的用户空间，后者是可执行文件。" to
        "rootfs and proot are two things: the former is a downloadable user space, the latter an executable.",
    "Android 10+ 不允许执行应用私有目录里的文件，proot 必须随安装包以 lib*.so 形式提供。" to
        "Android 10+ forbids executing files in app-private directories, so proot ships inside the APK as lib*.so.",
    "这是用户态模拟（ptrace 拦截系统调用），不是容器：没有内核级隔离，I/O 比原生慢，" to
        "This is user-space emulation (ptrace syscall interception), not a container: no kernel-level isolation, slower I/O, ",
    "需要内核模块的程序跑不起来。适合跑脚本、验算、看项目；不要把它当服务器用。" to
        "and programs that need kernel modules won't run. Good for scripts, math checks, and exploring projects; not a server.",
    "正在运行诊断…" to "Running diagnostics…",
    "运行诊断命令" to "Run diagnostics",

    // ================= QQ 机器人 =================
    "QQ 机器人" to "QQ bot",
    "接入方式" to "Connection mode",
    "官方 Bot" to "Official bot",
    "OneBot 协议端" to "OneBot bridge",
    "只靠手机就能用：手机主动连 QQ 官方网关，收发都不需要公网地址、不需要协议端、不需要另一台设备。" to
        "Works with just the phone: the app connects out to QQ's official gateway — no public address, no bridge, no second device.",
    "需要另一台设备常开：在电脑或 NAS 上跑 NapCat，登录一个 QQ 小号，手机连它的接口。" to
        "Needs an always-on second device: run NapCat on a PC or NAS with a spare QQ account, and the phone connects to it.",
    "代价：要去 q.qq.com 注册机器人拿 AppID/AppSecret；群里只有 @ 它时才收得到消息。" to
        "Cost: register a bot at q.qq.com for an AppID/AppSecret; in groups it only receives messages when @-mentioned.",
    "好处：能收群内全部消息、能用真实 QQ 号；代价：那台设备必须一直在线。" to
        "Upside: receives all group messages and uses a real QQ number; cost: that device must stay online.",
    "沙箱（推荐）" to "Sandbox (recommended)",
    "正式" to "Production",
    "沙箱环境免 IP 白名单，手机换网也不会失效 —— 只用一部手机时选这个。" to
        "The sandbox skips IP whitelisting and survives network changes — pick this when using only one phone.",
    "正式环境要求把公网 IP 填进白名单（只支持单个 IPv4）。手机 IP 每次切网都会变，不建议。" to
        "Production requires whitelisting your public IP (single IPv4 only). Phone IPs change on every network switch — not recommended.",
    "代价：只能和「沙箱配置 → 沙箱单聊」名单里的 QQ 号对话（自用场景把自己加进去即可）。" to
        "Cost: it can only chat with QQ numbers in \"Sandbox config → sandbox DMs\" (for personal use, just add yourself).",
    "机器人提审上线后才可用；接口报「源IP不在白名单」时先来这里核对。" to
        "Available only after the bot passes review; if the API reports \"source IP not whitelisted\", check here first.",
    "扫码绑定" to "Scan to bind",
    "用手机 QQ 扫一下，自动填好 AppID 与密钥（不用手抄）" to
        "Scan with mobile QQ to fill in the AppID and secret automatically (no typing)",
    "机器人凭证" to "Bot credentials",
    "AppSecret（只显示一次，只存本机）" to "AppSecret (shown once, stored on this device only)",
    "步骤：q.qq.com 创建机器人 → 抄下 AppID/AppSecret（密钥只显示一次）→「开发管理 → 事件订阅与回调地址」必须选 WebSocket（选 Webhook 时会连上也收不到消息）→「沙箱配置」里把自己的 QQ 号加进沙箱单聊 → 点「开始接收」，然后在 QQ 里给机器人发第一句话。" to
        "Steps: create a bot at q.qq.com → note the AppID/AppSecret (secret shown once) → under \"Dev management → event subscription & callback\", you MUST pick WebSocket (with Webhook it connects but receives nothing) → add your QQ number to sandbox DMs in \"Sandbox config\" → tap \"Start receiving\", then message the bot in QQ.",
    "协议端" to "Bridge",
    "HTTP 地址，如 192.168.1.10:3000" to "HTTP address, e.g. 192.168.1.10:3000",
    "访问令牌（没有就留空）" to "Access token (leave empty if none)",
    "WS 地址，如 ws://192.168.1.10:3001（接收消息用）" to
        "WS address, e.g. ws://192.168.1.10:3001 (for receiving messages)",
    "默认目标" to "Default target",
    "私聊" to "DM",
    "群聊" to "Group",
    "群" to "group",
    "群号，如 123456" to "Group ID, e.g. 123456",
    "QQ 号，如 10001" to "QQ number, e.g. 10001",
    "备注名（可选）" to "Display name (optional)",
    "自动回复" to "Auto reply",
    "收到消息后让模型回复" to "Let the model reply to incoming messages",
    "私聊直接回；群里只回 @ 机器人的消息。回复会写进你选择的会话（默认「QQ 消息」）。" to
        "DMs get direct replies; in groups only @-mentions get replies. Replies go into the chat you pick (default \"QQ messages\").",
    "深度思考" to "Deep thinking",
    "默认关闭：QQ 里等 20 秒等于没回。开启后更聪明，但明显更慢" to
        "Off by default: a 20-second wait on QQ is as good as no reply. On makes it smarter, but noticeably slower",
    "回复到哪个会话" to "Which chat replies go to",
    "自动（QQ 消息）" to "Auto (QQ messages)",
    "第一次收到消息时新建「QQ 消息」会话" to "Creates a \"QQ messages\" chat on first incoming message",
    "启用中 · 第一次收到消息时新建「QQ 消息」会话" to "Active · creates a \"QQ messages\" chat on first incoming message",
    "启用中 · 沿用上次的 QQ 会话：《%s》" to "Active · continuing last QQ chat: “%s”",
    "启用中 · 上次的 QQ 会话已删除，下次会新建「QQ 消息」" to "Active · last QQ chat was deleted; a new one will be created",
    "未命名会话" to "Untitled chat",
    "启用中 · 回复写进这条会话" to "Active · replies go into this chat",
    "回复写进这条会话" to "Replies go into this chat",
    "所选会话已被删除。会自动沿用上次的 QQ 会话：《%s》，可在上面改选。" to
        "The selected chat was deleted. Falling back to the last QQ chat: “%s”. You can change it above.",
    "所选会话已被删除。会自动新建「QQ 消息」，可在上面改选。" to
        "The selected chat was deleted. A new \"QQ messages\" chat will be created automatically; change it above.",
    "写进同一条会话的好处：App 与 QQ 接着同一段上文，人设也只有一份，不会「换个入口就变了个人」。" to
        "Why one chat: the app and QQ share the same context and the same persona — the bot doesn't \"become someone else\" when you switch entry points.",
    "群里的人" to "People in groups",
    "给这个人的备注" to "Nickname for this person",
    "改名失败" to "Rename failed",
    "收到 %d 条 · 点一下改名" to "%d messages · tap to rename",
    "官方只给 openid 不给昵称，所以这里用本地别名代替。改名只影响这一台设备，也只会用在提示词里。" to
        "QQ only provides openids, not nicknames, so local aliases stand in. Renaming affects only this device and is used in prompts.",
    "能力" to "Capabilities",
    "允许在 QQ 里查东西" to "Allow lookups from QQ",
    "只能用只读工具：当前时间、计算、联网搜索、已装应用。改文件、执行命令、读短信一律不可用。" to
        "Read-only tools only: current time, math, web search, installed apps. No file writes, no commands, no SMS access.",
    "默认关闭的原因：QQ 里没有「你点确认」这一步，任何能给你发消息的人都能触发它。开启后最坏后果是多花点 token，不会动你的设备。" to
        "Why off by default: QQ has no \"you tap confirm\" step — anyone who can message the bot can trigger it. " +
        "Worst case when on: a few extra tokens; it cannot touch your device.",
    "开始接收" to "Start receiving",
    "连接成功：%s" to "Connected: %s",
    "连接失败：%s" to "Connection failed: %s",
    "已保存，按新配置重连中" to "Saved; reconnecting with new config",
    "绑定成功，已用新凭证重连" to "Bound; reconnected with the new credentials",
    "绑定成功，点「开始接收」生效" to "Bound. Tap \"Start receiving\" to take effect",
    "安全：AppSecret 用 Android Keystore 加密后只存本机。只做被动回复（单聊 60 分钟内、群聊 5 分钟内有效），不做主动推送。" to
        "Security: AppSecret is encrypted with Android Keystore and stored on this device only. Passive replies only " +
        "(valid 60 min for DMs, 5 min in groups); no proactive pushes.",
    "安全：令牌用 Android Keystore 加密后只存本机；消息发送前会展示目标与内容。" to
        "Security: the token is encrypted with Android Keystore and stored on this device only; targets and content are shown before sending.",
    "机器人提审上线后才可用；接口报「源IP不在白名单」时先来这里核对。" to
        "Available only after review; if the API reports \"source IP not whitelisted\", check here first.",

    // ================= 扫码绑定 =================
    "扫码绑定机器人" to "Scan to bind bot",
    "在 QQ 里扫一下，自动填好 AppID 和密钥" to "Scan in QQ to fill in the AppID and secret automatically",
    "正在生成二维码…" to "Generating QR code…",
    "用 QQ 扫这个码确认绑定 · " to "Scan this code in QQ to confirm binding · ",
    "s 后失效" to "s until it expires",
    "（第 %d 次）" to "(attempt %d)",
    "密钥已加密存到本机，不会再显示" to "Secret encrypted and stored on this device; it won't be shown again",
    "绑定者" to "Binder",
    "已保存（%d 位）" to "Saved (%d chars)",
    "开始绑定" to "Start binding",
    "改用手工填写" to "Enter manually instead",

    // ================= 插件 =================
    "插件库" to "Plugin library",
    "发消息到 QQ（需要服务器侧协议端）" to "Send QQ messages (needs a server-side bridge)",
    "未配置：填入协议端地址与目标号码后可用" to "Not configured: fill in bridge address and target number to enable",
    "HTTP 回显示例" to "HTTP echo sample",
    "内置清单，访问 httpbin.org 验证安装与调用链路" to
        "Built-in manifest; calls httpbin.org to verify install and call chain",
    "还没有插件" to "No plugins yet",
    "从 URL 安装插件" to "Install plugin from URL",
    "从 URL 安装" to "Install from URL",
    "从剪贴板安装" to "Install from clipboard",
    "粘贴 manifest JSON 后点这里" to "Paste manifest JSON, then tap here",
    "停用插件" to "Disable plugin",
    "启用插件" to "Enable plugin",
    "卸载插件" to "Uninstall plugin",
    "插件只做声明式 HTTP 调用，不执行任何代码。每次调用都会先让你确认目标与参数。" to
        "Plugins only make declarative HTTP calls and never execute code. Every call asks for confirmation of target and params first.",
    "已安装（%d）" to "Installed (%d)",
    "https 清单地址，装完立即可用" to "https manifest URL; usable right after install",
    "可选，例如「测试机」" to "Optional, e.g. \"Test phone\"",
    "Android 应用没有 root，跑不了完整的 Linux 环境。这里改成连接你自己的服务器：Agent 执行命令时通过 SSH 在那台机器上跑。" to
        "Android apps have no root and can't run a full Linux environment. Instead, connect your own server: " +
        "Agent runs commands over SSH on that machine.",

    // ================= 工具 =================
    "思考" to "Thinking",
    "推理强度" to "Reasoning effort",
    "更快" to "Faster",
    "均衡" to "Balanced",
    "最深" to "Deepest",
    "开启后先输出思考过程再回答，更准但更慢" to "Show the thinking process before answering; more accurate but slower",
    "当前模型强制思考，无法关闭" to "This model always thinks; cannot be turned off",
    "未知模型，关闭请求不会下发，将保持服务端默认" to
        "Unknown model: the off request won't be sent; the server default stays",
    "读短信前先确认" to "Confirm before reading SMS",
    "每次 read_sms 都会先弹确认，能看到 Agent 想读什么" to
        "Every read_sms asks for confirmation first, so you can see what the Agent wants to read",
    "已关闭：Agent 会在你提问时直接读取，不再确认" to
        "Off: the Agent reads directly when you ask, without confirming",
    "停用的工具不会出现在模型可调用的列表里。带副作用的工具（写文件、建日程、启动应用、锁屏、装插件）每次调用都会先请你确认。" to
        "Disabled tools never appear in the model's callable list. Side-effecting tools (file writes, calendar, app launch, " +
        "screen lock, plugin install) ask for confirmation on every call.",
    "内置工具（%d）" to "Built-in tools (%d)",
    "插件工具（%d）" to "Plugin tools (%d)",
    "MCP 工具（%d）" to "MCP tools (%d)",

    // ================= 模型分工（ProviderScreens） =================
    "模型分工" to "Model routing",
    "摘要用哪个模型" to "Model for summaries",
    "子任务用哪个模型" to "Model for subtasks",
    "子任务模型" to "Subtask model",
    "上下文摘要" to "Context summary",
    "Agent 用 delegate_task 派发的子任务" to "Subtasks dispatched via delegate_task",
    "这两个角色不需要最强模型，选便宜的能明显省钱；留空则跟随主模型。" to
        "These two roles don't need the strongest model — a cheap one saves real money; empty follows the main model.",
    "跟随主模型" to "Follow main model",
    "未填模型" to "No model",
    "添加服务商" to "Add provider",
    "编辑服务商" to "Edit provider",
    "删除服务商" to "Delete provider",
    "还没有服务商" to "No providers yet",
    "点下方「添加服务商」，填入 Base URL、模型与密钥" to
        "Tap \"Add provider\" below, then fill in the Base URL, model, and key",
    "未命名服务商" to "Unnamed provider",
    "主模型，也就是下面标记「使用中」的那个" to "The main model — the one marked \"In use\" below",
    "服务商文档里的 model 参数" to "The model parameter from your provider's docs",
    "模型名按服务商文档填写，换模型只需改这一项" to
        "Fill in the model name from your provider's docs; switching models only changes this field",
    "必填：填服务商文档请求示例里的 model 值" to "Required: the model value from your provider's request example",
    "示例：%s" to "Example: %s",
    "连接正常 · %d 个模型" to "Connected · %d models",
    "当前可用模型以 %s 为准；应用不内置清单，模型迭代比发版快" to
        "Available models are listed at %s; the app ships no hardcoded list — models iterate faster than releases",
    "应用不内置模型清单：模型迭代比发版快，硬编码的列表迟早全部失效" to
        "No hardcoded model list: models iterate faster than releases, a hardcoded list would rot",
    "DeepSeek / 自建网关" to "DeepSeek / self-hosted gateway",
    "当前地址使用 http://，API Key 会以明文经过网络。请改用 https://，或只对本地服务使用 http。" to
        "This address uses http:// — the API key travels in plaintext. Switch to https://, or use http only for local services.",
    "密钥保存后将以密文形式落盘，界面不再回显完整内容。" to
        "Keys are stored encrypted after saving; the full value is never shown again.",
    "密钥经 Android Keystore 加密后仅存于本机，不会上传、不参与备份。长按可删除或设为默认。" to
        "Keys are encrypted with Android Keystore, stored on this device only — never uploaded, never backed up. " +
        "Long-press to delete or set as default.",
    "已保存，留空则不修改" to "Saved; leave empty to keep unchanged",
    "快速配置" to "Quick setup",
    "填入示例" to "Fill example",
    "接入信息" to "Endpoint",
    "模型与密钥" to "Model & key",
    "本应用不提供模型服务，所有模型调用均由你自行配置的服务商完成，费用与额度由服务商结算。" to
        "This app provides no model service. All model calls go through providers you configure; costs and quotas settle with them.",

    // ================= SSH =================
    "鉴权（密码与私钥二选一）" to "Auth (password or private key)",
    "不填则用下面的私钥" to "Leave empty to use the private key below",
    "粘贴 OpenSSH 私钥全文（-----BEGIN OPENSSH PRIVATE KEY----- 开始）" to
        "Paste the full OpenSSH private key (starting with -----BEGIN OPENSSH PRIVATE KEY-----)",
    "192.168.1.10 或 example.com" to "192.168.1.10 or example.com",
    "安全：密码与私钥用 Android Keystore 加密后只存本机。当前不校验主机指纹（首次连接会直接信任），只建议连接你自己的服务器或可信内网；每条命令执行前都会请你确认。" to
        "Security: password and key are encrypted with Android Keystore and stored on this device only. Host fingerprints are not " +
        "checked (first connection is trusted blindly), so only connect to your own servers or trusted LANs; every command asks first.",
    "已连接，但输出异常：%s" to "Connected, but output looks wrong: %s",

    // ================= 存储 =================
    "本地占用" to "Local usage",
    "会话" to "Chats",
    "消息" to "Messages",
    "数据库文件" to "Database file",
    "导出聊天记录" to "Export chats",
    "Markdown 格式，写到沙箱后可分享" to "Markdown format; written to sandbox for sharing",
    "删除所有会话与消息，不可恢复" to "Deletes all chats and messages; cannot be undone",
    "会话、插件与密钥保存在你的设备上。除你主动发起的模型请求外，应用不会上传任何数据。" to
        "Chats, plugins, and keys live on your device. Nothing is uploaded except model requests you initiate.",
    "所有数据保存在应用私有目录，不参与云同步，也不会被系统备份导出。" to
        "All data stays in the app's private directory — no cloud sync, excluded from system backups.",

    // ================= 权限 =================
    "特殊权限" to "Special permissions",
    "锁屏（设备管理员）" to "Screen lock (device admin)",
    "Android 没有普通锁屏接口，需要激活设备管理员；只申请锁屏，不涉及数据" to
        "Android has no ordinary lock-screen API; device admin is required. Lock only — no data access",
    "已激活：可以直接对 Agent 说「锁屏」" to "Activated: just tell the Agent \"lock the screen\"",
    "权限被永久拒绝时，需要到系统里手动开启" to "If permanently denied, enable it manually in system settings",
    "所有权限都在真正用到时才申请：不建日程就不会要日历权限，不发通知就不会要通知权限。" to
        "Permissions are requested only when actually needed: no calendar permission unless you schedule, " +
        "no notification permission unless you're notified.",
    "需确认" to "Needs confirmation",
    "这个功能是干什么的" to "What does this do",

    // ================= 关于 / 许可 =================
    "更新日志" to "What's new",
    "这次改了什么" to "What changed in this version",
    "最后更新：2026-10-18" to "Last updated: 2026-10-18",
    "Velune 使用的开源项目" to "Open-source projects used by Velune",
    "以下项目以各自的许可分发，完整许可文本随安装包提供。" to
        "The following projects are distributed under their own licenses; full license texts ship with the APK.",
    "完整许可文本随安装包分发。" to "Full license texts ship with the APK.",
    "一、核心框架" to "1. Core framework",
    "二、设计与交互" to "2. Design & interaction",
    "三、字体" to "3. Fonts",
    "界面的度量、动效曲线与控件形态参考了 HyperOS 的设计语言，实现全部为本项目自绘。" to
        "The UI's metrics, motion curves, and control shapes follow HyperOS's design language; all implementation is hand-drawn for this project.",
    "设置页列表按 HyperOS 规格重做：卡片 20dp 圆角、卡内不再画分割线、行高 56dp" to
        "Settings lists rebuilt to HyperOS spec: 20dp card corners, no dividers inside cards, 56dp rows",
    "底部导航换成液态玻璃，列表内容从玻璃下方穿过" to
        "Bottom navigation turned into liquid glass; list content passes beneath it",
    "关于页改为 AGSL 动态底色，随滚动淡出" to "About page now has an AGSL dynamic backdrop that fades with scrolling",
    "新增可折叠大标题：26sp 品牌蓝，滚动后收起为居中的 17sp 标题" to
        "New collapsible large title: 26sp brand blue, collapsing to a centered 17sp title on scroll",
    "其中「关于」页的动态底色移植自 Nexio 课程表的 AGSL 着色器（AGPL-3.0），配色与绘制参数按真机截图重新测定。AGPL-3.0 要求衍生作品以同一许可开源，若需闭源分发，应替换该实现。" to
        "The About page's dynamic backdrop is ported from Nexio's AGSL shader (AGPL-3.0), with colors and draw params re-measured " +
        "from device screenshots. AGPL-3.0 requires derivatives to stay open under the same license; replace the implementation for closed distribution.",
    "MiSans · 小米科技" to "MiSans · Xiaomi",
    "MiSans 允许免费商用，但字体文件本身不可修改、不可单独分发。本项目不打包 MiSans。" to
        "MiSans allows free commercial use, but the font files may not be modified or redistributed separately. This project does not bundle MiSans.",
    "界面优先使用系统字体。若设备安装了 MiSans，中文会以 MiSans 渲染；否则回退系统默认字体，字形有差异，但字号与行高保持一致。" to
        "The UI prefers system fonts. With MiSans installed, Chinese renders in MiSans; otherwise it falls back to the system font — " +
        "glyphs differ, but sizes and line heights are consistent.",
    "开源的部分只用作参考与组件来源，不包含小米的私有资源。" to
        "Open-source parts are used only as reference and component sources; no Xiaomi proprietary assets are included.",
    "本应用未内置任何小米的图片、图标或字体文件；视觉规格是测量结果的重写，不是资源搬运。" to
        "This app bundles no Xiaomi images, icons, or fonts; the visual specs are re-implemented from measurements, not copied assets.",
    "本应用不提供模型服务，所有模型调用均由你自行配置的服务商完成，费用与额度由服务商结算。" to
        "This app provides no model service. All model calls go through providers you configure; costs and quotas settle with them.",
    "本应用未内置任何小米的图片、图标或字体文件；视觉规格是测量结果的重写，不是资源搬运。" to
        "This app bundles no Xiaomi images, icons, or fonts; the visual specs are re-implemented from measurements, not copied assets.",
    "请勿使用本应用生成违反法律法规的内容。由你生成与传播的内容，责任由你承担。" to
        "Do not use this app to generate content that violates laws or regulations. You are responsible for what you generate and share.",
    "若你认为某处使用超出了上述许可范围，请通过「交流与反馈」联系，我们会尽快处理。" to
        "If you believe any use exceeds the scope above, contact us via \"Feedback\" and we'll address it promptly.",
    "交流与反馈" to "Community & feedback",
    "请作者喝咖啡" to "Buy the author a coffee",
    "如果 Velune 对你有用，这几种方式都能帮到项目。" to "If Velune is useful to you, here are ways to help the project.",
    "点个 Star" to "Give a star",
    "项目开源在 GitCode / GitHub，一个 Star 能让更多人看到它。" to
        "The project is open on GitCode / GitHub; a star helps more people find it.",
    "提 Issue" to "File an issue",
    "把遇到的 bug 与想要的功能写清楚，比口头反馈有效得多。" to
        "Describe the bug or the feature clearly — far more effective than verbal feedback.",
    "写插件" to "Write a plugin",
    "插件体系上线后，一个好用的小插件比十句夸奖更有价值。" to
        "Now that the plugin system is live, one useful plugin is worth more than ten compliments.",
    "赞助" to "Sponsor",
    "赞助入口将在正式版开放，当前阶段不收取任何费用。" to
        "Sponsorship opens with the stable release; nothing is charged at this stage.",
    "谢谢你使用 Velune。" to "Thank you for using Velune.",
    "Velune 是一款本地优先的 AI 对话应用：模型你自己接，数据留在本机，界面按 HyperOS 的克制风格重做。" to
        "Velune is a local-first AI chat app: bring your own model, data stays on device, UI rebuilt in HyperOS's restrained style.",
    "反馈" to "Feedback",
    "遇到问题或有建议，欢迎在项目仓库提 Issue。" to "Found a problem or have an idea? File an issue in the repository.",
    "产出文件" to "Output files",
    "免费商用（不随包分发）" to "Free for commercial use (not bundled)",
    "取出函数引用" to "Extract function reference",

    // ===== 消息中心（feature/messages）=====
    "消息" to "Messages",
    "归档箱" to "Archive",
    "退出归档箱" to "Exit archive",
    "归档箱（%d）" to "Archive (%d)",
    "归档" to "Archive",
    "移出归档箱" to "Unarchive",
    "置顶" to "Pin",
    "取消置顶" to "Unpin",
    "置顶会话" to "Pin conversation",
    "置顶的会话永远在最上面，其余按这里选的方式排。" to
        "Pinned conversations always stay on top; the rest sort by the option below.",
    "排序方式" to "Sort by",
    "最近活跃" to "Recent activity",
    "创建时间" to "Created",
    "标题" to "Title",
    "%s（当前）" to "%s (current)",
    "已选 %d 项" to "%d selected",
    "删除 %d 个会话" to "Delete %d conversations",
    "消息会一并删除，无法恢复。" to "Messages will be deleted too. This cannot be undone.",
    "删除「%s」" to "Delete \"%s\"",
    "该会话的全部消息会一并删除，无法恢复。" to
        "All messages in this conversation will be deleted. This cannot be undone.",
    "删除会话" to "Delete conversation",
    "删除" to "Delete",
    "重命名" to "Rename",
    "重命名会话" to "Rename conversation",
    "会话标题" to "Conversation title",
    "用哪个角色" to "Choose a persona",
    "选一个角色开始新对话；也可以不用角色，直接聊。" to
        "Pick a persona for the new chat, or skip it and just talk.",
    "不用角色" to "No persona",
    "角色库" to "Persona library",
    "选择会话" to "Pick a conversation",
    "新建对话" to "New chat",
    "创建角色" to "Create persona",
    "给它一个名字和设定，立刻开始聊。角色只在这个会话里生效，随时可以改。" to
        "Give it a name and a persona and start chatting. It only applies to this conversation and can be edited anytime.",
    "角色名，如「小鹿」" to "Name, e.g. \"Deer\"",
    "设定：你是谁、怎么说话、不要做什么" to "Persona: who you are, how you talk, what not to do",
    "开始聊天" to "Start chat",
    "还没有会话" to "No conversations yet",
    "没有匹配的会话" to "No matching conversations",
    "换个关键词试试" to "Try a different keyword",
    "点右上角 + 开始第一段对话" to "Tap + in the top-right to start your first chat",
    "返回消息列表" to "Back to messages",
    "搜索" to "Search",
    "恢复" to "Restore",
    "看到就想清掉" to "One tap to clear",
    "昨天 " to "Yesterday ",
    "%d月%d日 " to "%d/%d ",
    "昨天" to "Yesterday",
    "%d月%d日" to "%d/%d",
    "%d 个" to "%d",

    // ===== 聊天页（feature/chat）=====
    "输入消息…" to "Type a message…",
    "你的消息" to "Your message",
    "在本次对话中搜索" to "Search this conversation",
    "搜索对话内容" to "Search in conversation",
    "关闭搜索" to "Close search",
    "上一条" to "Previous match",
    "下一条" to "Next match",
    "无匹配" to "No matches",
    "返回" to "Back",
    "关闭" to "Close",
    "关闭预览" to "Close preview",
    "关闭 Agent 模式" to "Turn off Agent mode",
    "开启 Agent 模式" to "Turn on Agent mode",
    "Agent 模式已开启" to "Agent mode is on",
    "Agent 模式 · 会调用工具，逐步执行" to "Agent mode · calls tools and works step by step",
    "先在设置里配置服务商与 API Key，\n然后就能直接对话。" to
        "Set up a provider and API key in Settings first,\nthen you can chat right away.",
    "去配置服务商" to "Set up provider",
    "尚未配置服务商 · 去设置" to "No provider yet · Go to Settings",
    "尚未配置模型服务商：设置 → 模型与密钥" to "No model provider yet: Settings → Models & Keys",
    "当前服务商缺少 API Key：设置 → 模型与密钥" to "Current provider has no API key: Settings → Models & Keys",
    "当前服务商未填写模型名：设置 → 模型与密钥 → 编辑" to
        "Current provider has no model name: Settings → Models & Keys → Edit",
    "未配置模型" to "No model configured",
    "管理服务商与模型" to "Manage providers & models",
    "新对话" to "New chat",
    "直接发消息就能开始对话。" to "Just send a message to start.",
    "你好，我是 Velune。" to "Hi, I'm Velune.",
    "你好，我是 Velune。可以直接发消息，也可以让我读文件、查资料。" to
        "Hi, I'm Velune. Send a message, or ask me to read files or look things up.",
    "上传图片 / 文本" to "Attach image / text",
    "附件" to "Attachment",
    "无法读取文件" to "Could not read the file",
    "文件内容为空" to "File is empty",
    "图片过大（%d KB），上限 %d KB" to "Image too large (%d KB), limit is %d KB",
    "暂不支持该类型（仅支持图片与文本类文件）：%s" to
        "Unsupported type (images and text files only): %s",
    "已添加 %s" to "Added %s",
    "读取失败" to "Read failed",
    "读取中…" to "Loading…",
    "已引用，发送时会把这段原文一起带上" to "Quoted — the original text will be sent along",
    "引用" to "Quote",
    "引用：%s" to "Quote: %s",
    "修改后发送，将替换这条消息之后的内容" to
        "Sending will replace everything after this message",
    "编辑并重发" to "Edit & resend",
    "编辑中 · 发送后替换后续内容" to "Editing · sending replaces what follows",
    "重新生成" to "Regenerate",
    "复制" to "Copy",
    "已复制" to "Copied",
    "复制文件路径" to "Copy file path",
    "已复制路径" to "Path copied",
    "分享文件" to "Share file",
    "分享失败" to "Share failed",
    "已选择分享目标" to "Share target chosen",
    "用其他应用打开" to "Open with another app",
    "在应用内预览" to "Preview in app",
    "没有能打开 %s 的应用，可试用「分享」" to "No app can open %s. Try \"Share\".",
    "没有能打开该类型的应用，可试用「分享」" to "No app opens this type. Try \"Share\".",
    "已交给系统打开" to "Handed off to the system",
    "打开 %s" to "Open %s",
    "文件已写入沙箱" to "File written to sandbox",
    "沙箱路径：%s" to "Sandbox path: %s",
    "已删除 %s" to "Deleted %s",
    "删除失败" to "Delete failed",
    "文件保存在应用私有目录，其他应用无法直接访问；点击文件可选择用系统应用打开。" to
        "Files live in the app's private storage and aren't directly accessible to other apps; tap a file to open it with a system app.",
    "还没有产出文件" to "No output files yet",
    "产出文件" to "Output files",
    "让 AI 把结果存成文件，例如「把这份清单保存成 todo.md」" to
        "Ask the AI to save results as files, e.g. \"save this list as todo.md\"",
    "上下文压缩" to "Context compression",
    "保留最近几条原文" to "Keep the latest N messages verbatim",
    "%d 条" to "%d",
    "折叠内容超过多少 token 才触发摘要（越小越省钱，也越容易「忘事」）" to
        "Summarize only when folded content exceeds this many tokens (smaller saves money but forgets more)",
    "当前 %d 条消息。超出的旧消息会被压成摘要后发送，本地记录始终完整保留。" to
        "You have %d messages. Older ones beyond the limit are summarized before sending; local history always stays intact.",
    "摘要生成失败时会自动退回「只发最近几条」，不会让消息发不出去。" to
        "If summarization fails it falls back to sending only the latest messages — nothing gets stuck.",
    "摘要生成失败，已回退为最近 %d 条" to "Summarization failed; sending the latest %d messages instead",
    "已把 %d 条旧消息压缩为摘要（约 %d→%d token）" to
        "Compressed %d older messages into a summary (~%d→%d tokens)",
    "播放失败" to "Playback failed",
    "播放失败（%s/%s）" to "Playback failed (%s/%s)",
    "音频文件已不在沙箱里" to "The audio file is no longer in the sandbox",
    "音频无法打开" to "Cannot open the audio",
    "暂停跟随" to "Pause follow",
    "用户还在底部" to "User is at the bottom",
    "思考过程" to "Reasoning",
    "思考中…" to "Thinking…",
    "思考 %s · %s" to "Reasoned %s · %s",
    "思考中… · %s" to "Thinking… · %s",
    "思考过程 · %s" to "Reasoning · %s",
    "%d 字" to "%d chars",
    "%.1fk 字" to "%.1fk chars",
    "提醒在回哪句话" to "Shows which message is being replied to",
    "有工具调用的那条回答" to "The reply that called tools",
    "执行轨迹" to "Run history",
    "还没有执行记录" to "No runs yet",
    "开启 Agent 后，每次调用工具都会记在这里" to
        "Once Agent mode is on, every tool call is logged here",
    "最近 %d 次运行" to "Last %d runs",
    "%d 步 · %d 次工具" to "%d steps · %d tool calls",
    "本次 %d 步 · %d 次工具" to "%d steps · %d tool calls this run",
    " · %d token" to " · %d tokens",
    " · %.1f秒" to " · %.1fs",
    "没有记录到步骤" to "No steps recorded",
    "现在到哪一步" to "Where it is now",
    "正在发生" to "In progress",
    "计划 %d/%d" to "Plan %d/%d",
    "还有 %d 步" to "%d more steps",
    "最多 %d 步" to "Up to %d steps",
    "最多 %d 次工具" to "Up to %d tool calls",
    "步数不限" to "Unlimited steps",
    "工具不限" to "Unlimited tool calls",
    "超出预算" to "Budget exceeded",
    "检测到循环" to "Loop detected",
    "连续失败" to "Repeated failures",
    "未注册工具" to "Unregistered tool",
    "未知来源" to "Unknown source",
    "本次 N 步" to "N steps this run",
    "已停止（%s）" to "Stopped (%s)",
    "（已停止）" to "(stopped)",
    "（已停止：%s）" to "(stopped: %s)",
    "（模型没有返回内容）" to "(the model returned nothing)",
    "请求失败：%s" to "Request failed: %s",
    "出错" to "Error",
    "需要确认：%s" to "Approval needed: %s",
    " · 有副作用" to " · has side effects",
    "\n参数：" to "\nArguments: ",
    "\n权限：" to "\nScopes: ",
    "、" to ", ",
    "允许这一次" to "Allow once",
    "本轮内都允许" to "Allow for this run",
    "拒绝" to "Deny",
    "参数" to "Arguments",
    "助手消息" to "Assistant message",
    "轮" to "turn",
    "本轮" to "This run",
    "上下文已用 %d/%d 条，剩余 %d 条后自动压缩" to
        "Context: %d/%d messages; auto-compresses %d messages from now",
    "重跑" to "Re-run",
    "已取消" to "Cancelled",
    "沙箱不可用" to "Sandbox unavailable",

    // ================= 法律文档：用户协议（VeluneApp.kt agreementRoute） =================
    "提示" to "Note",
    "本协议是你与 Velune 开发者之间具有法律约束力的约定。请逐条阅读，特别是加粗提示的免责与责任限制条款。点击「同意并继续」即表示你已完整阅读、理解并接受本协议全部内容。若你不同意任一条款，请停止安装或使用本应用。" to
        "This agreement is a legally binding contract between you and the Velune developer. Read every clause, especially the bold disclaimers and limitation-of-liability terms. Tapping \"Agree & continue\" means you have read, understood and accepted the entire agreement. If you disagree with any clause, do not install or use this app.",
    "1. 定义" to "1. Definitions",
    "1.1「本应用」指 Velune 客户端软件及其后续版本。\n1.2「开发者」指本应用的著作权人及运营者。\n1.3「第三方服务」指由你自行选择并配置的模型、搜索、语音等服务提供方。\n1.4「用户内容」指你输入、上传或通过本应用生成、存储的文字、图片、文件及其他信息。" to
        "1.1 \"This app\" means the Velune client software and its later versions.\n1.2 \"Developer\" means the copyright holder and operator of this app.\n1.3 \"Third-party services\" means the model, search, speech and other service providers you choose and configure yourself.\n1.4 \"User content\" means text, images, files and other information you enter, upload, or generate and store through this app.",
    "2. 协议的接受与变更" to "2. Acceptance and Changes",
    "2.1 你在首次启动时点击「同意并继续」，即视为接受本协议。\n2.2 开发者可能因法律法规变化或功能调整修订本协议。修订后的协议将在应用内以显著方式提示，并提升版本号；你需重新确认后方可继续使用。\n2.3 若你不同意修订内容，应停止使用并卸载本应用。" to
        "2.1 Tapping \"Agree & continue\" on first launch constitutes acceptance of this agreement.\n2.2 The developer may revise this agreement due to legal changes or feature adjustments. Revised terms will be prominently announced in the app with a bumped version number; you must re-confirm before continuing to use the app.\n2.3 If you disagree with a revision, stop using and uninstall this app.",
    "3. 服务内容与性质" to "3. Scope and Nature of the Service",
    "3.1 本应用是一款本地优先的 AI 对话与自动化工具，提供会话管理、提示词与角色管理、工具调用编排等功能。\n3.2 本应用不提供模型推理服务。所有模型调用均由你自行配置的第三方服务完成，开发者不参与该等服务的设计、运营与结算。\n3.3 开发者不对第三方服务的可用性、响应速度、输出内容质量或合规性作出任何承诺或担保。" to
        "3.1 This app is a local-first AI chat and automation tool providing conversation management, prompt/persona management, and tool-call orchestration.\n3.2 This app does not provide model inference. All model calls are performed by third-party services you configure yourself; the developer takes no part in their design, operation or billing.\n3.3 The developer makes no commitment or warranty regarding third-party availability, latency, output quality or compliance.",
    "4. 账号、凭证与安全" to "4. Accounts, Credentials and Security",
    "4.1 本应用无需注册账号，不设云端账户体系。\n4.2 你应妥善保管自行配置的 API Key、访问令牌、远程服务器地址与凭据。上述信息经加密后仅存储于你的设备。\n4.3 因你主动泄露、转借、设备丢失或被他人物理接触导致的凭证外泄及由此产生的费用与损失，由你自行承担。\n4.4 你发现凭证可能已泄露时，应立即在对应服务商处吊销并更换。" to
        "4.1 This app requires no account registration and has no cloud account system.\n4.2 Safeguard the API keys, access tokens, remote server addresses and credentials you configure. They are encrypted and stored only on your device.\n4.3 You bear any credential leaks caused by your own disclosure, lending, device loss or physical access by others, and any resulting costs and losses.\n4.4 If you suspect a credential has leaked, revoke and replace it at the provider immediately.",
    "5. 用户行为规范" to "5. Acceptable Use",
    "5.1 你承诺不利用本应用从事下列行为：\n（1）生成、存储或传播违反法律法规、危害国家安全、破坏社会稳定、侮辱诽谤他人或侵犯他人合法权益的内容；\n（2）未经授权侵入、扫描、探测他人计算机系统或网络，或绕过其访问控制；\n（3）批量发送骚扰信息、垃圾信息，或实施诈骗、赌博、洗钱等违法活动；\n（4）制作、传播恶意程序，或利用工具调用能力破坏设备、数据或他人系统；\n（5）侵害他人知识产权、商业秘密、个人信息权益；\n（6）其他违反法律法规、公序良俗或本协议约定的行为。\n5.2 你使用工具调用能力（包括但不限于文件读写、命令执行、消息发送、日历修改、锁屏）所产生的一切后果，由你自行承担。开发者已通过逐次确认机制提示风险，但你仍是操作的最终决定者。\n5.3 若你违反上述约定，开发者有权在不另行通知的情况下中止或终止向你提供服务，并保留追究法律责任的权利。" to
        "5.1 You agree not to use this app to:\n(1) generate, store or disseminate content that violates laws, endangers national security, destabilizes society, defames others or infringes their lawful rights;\n(2) intrude into, scan or probe other people's computer systems or networks without authorization, or bypass their access controls;\n(3) send bulk harassment or spam, or engage in fraud, gambling, money laundering or other illegal activities;\n(4) create or spread malware, or use tool-call capabilities to damage devices, data or other people's systems;\n(5) infringe intellectual property, trade secrets or personal information rights;\n(6) otherwise violate laws, public order or this agreement.\n5.2 You bear all consequences of using tool-call capabilities (including but not limited to file read/write, command execution, message sending, calendar changes, screen lock). The developer has warned of the risks via per-action confirmation, but you remain the final decision-maker.\n5.3 If you breach the above, the developer may suspend or terminate service without further notice and reserves the right to pursue legal liability.",
    "6. 用户内容与知识产权" to "6. User Content and Intellectual Property",
    "6.1 你对输入本应用的内容享有合法权利，并对其合法性、真实性、准确性负责。\n6.2 通过第三方服务生成的内容，其权利归属与使用限制适用该第三方服务商的条款。\n6.3 本应用的软件、界面设计、图标、文档及源代码的著作权归开发者所有。未经书面许可，你不得反向工程、反编译、修改、分发、出租、出售本应用，或将其用于商业运营。\n6.4 本应用使用的开源组件依其各自许可证授权，具体清单见「开源许可」页面。" to
        "6.1 You have lawful rights to the content you enter and are responsible for its legality, truthfulness and accuracy.\n6.2 Ownership and usage limits of content generated via third-party services are governed by that provider's terms.\n6.3 The software, UI design, icons, documentation and source code of this app are copyrighted by the developer. Without written permission you may not reverse-engineer, decompile, modify, distribute, rent or sell this app, or use it for commercial operation.\n6.4 Open-source components are licensed under their respective licenses; see the \"Open-source licenses\" page.",
    "7. 第三方服务与费用" to "7. Third-Party Services and Fees",
    "7.1 你在本应用中配置第三方服务，即表示你同意该服务商的服务条款与隐私政策。\n7.2 使用第三方服务产生的费用由你与该服务商直接结算。开发者不代收、不垫付、不开具发票，亦不对计费争议承担责任。\n7.3 你应自行关注账户额度与计费规则。因额度耗尽、密钥失效导致服务不可用，不属于本应用的缺陷。" to
        "7.1 Configuring a third-party service in this app constitutes your agreement to that provider's terms of service and privacy policy.\n7.2 Fees arising from third-party services are settled directly between you and the provider. The developer does not collect, advance or invoice fees, and is not liable for billing disputes.\n7.3 Monitor your own quota and billing rules. Service unavailability caused by exhausted quota or invalid keys is not a defect of this app.",
    "8. 免责声明与责任限制" to "8. Disclaimers and Limitation of Liability",
    "8.1 人工智能生成内容具有不确定性，可能存在错误、过时、片面或与事实不符的情况。你应自行核实重要信息后再作决策。\n8.2 本应用不构成医疗、法律、金融、税务等专业意见。请勿将其用于诊断疾病、作出法律判断、进行投资决策等高风险场景。\n8.3 本应用按「现状」提供。在法律允许的最大范围内，开发者不对因使用或无法使用本应用导致的间接损失、利润损失、数据丢失、商誉损失承担责任。\n8.4 因不可抗力、网络故障、第三方服务中断、系统限制、设备故障等非开发者原因造成的服务异常，开发者不承担责任。\n8.5 开发者对用户内容不做主动审查，但有权在收到合法通知后移除涉嫌违法的内容。" to
        "8.1 AI-generated content is inherently uncertain and may be wrong, outdated, one-sided or factually incorrect. Verify important information before acting on it.\n8.2 This app does not constitute professional medical, legal, financial or tax advice. Do not use it for high-stakes scenarios such as diagnosing illness, making legal judgments or investment decisions.\n8.3 This app is provided \"as is\". To the maximum extent permitted by law, the developer is not liable for indirect losses, lost profits, data loss or reputational harm arising from use of or inability to use this app.\n8.4 The developer is not liable for service disruptions caused by force majeure, network failures, third-party outages, system restrictions, device faults or other non-developer causes.\n8.5 The developer does not proactively review user content, but may remove suspected illegal content after receiving lawful notice.",
    "9. 服务的变更、中断与终止" to "9. Changes, Interruption and Termination",
    "9.1 开发者可能持续优化产品，包括新增、调整或下线部分功能。重大变更将通过应用内公告提示。\n9.2 你可随时停止使用并卸载本应用，无需通知开发者。\n9.3 本协议终止后，第 6、8、10、11 条及其他依其性质应当继续有效的条款继续有效。" to
        "9.1 The developer may keep improving the product, including adding, adjusting or retiring features. Major changes will be announced in the app.\n9.2 You may stop using and uninstall this app at any time without notifying the developer.\n9.3 Upon termination, clauses 6, 8, 10 and 11 and any others that should survive by their nature remain in effect.",
    "10. 未成年人保护" to "10. Protection of Minors",
    "10.1 若你为未满 18 周岁的未成年人，应在监护人陪同下阅读本协议，并在取得监护人同意后使用。\n10.2 若你为未满 14 周岁的儿童，监护人应代为阅读并同意本协议及隐私政策，并对其使用行为进行监督与指导。\n10.3 监护人发现未成年人在未获同意的情况下使用本应用，可联系开发者协助处理。" to
        "10.1 If you are under 18, read this agreement with a guardian and use the app only with their consent.\n10.2 If you are under 14, your guardian must read and accept this agreement and the privacy policy on your behalf, and supervise your use.\n10.3 Guardians who discover unauthorized use by a minor may contact the developer for assistance.",
    "11. 通知与送达" to "11. Notices",
    "11.1 开发者可通过应用内公告、版本更新说明等方式向你发送通知，该等通知于发送之日视为送达。\n11.2 你应向开发者提供有效联系方式（如通过应用内反馈渠道），并保证其真实有效。" to
        "11.1 The developer may send you notices via in-app announcements, release notes and similar channels; such notices are deemed delivered on the day they are sent.\n11.2 You should provide the developer valid contact information (e.g. via the in-app feedback channel) and keep it true and effective.",
    "12. 法律适用与争议解决" to "12. Governing Law and Dispute Resolution",
    "12.1 本协议的订立、效力、解释、履行及争议解决均适用中华人民共和国法律（不含冲突法规则）。\n12.2 因本协议产生的争议，双方应首先友好协商解决；协商不成的，任何一方可向开发者所在地有管辖权的人民法院提起诉讼。\n12.3 若本协议的任何条款被认定无效或不可执行，不影响其余条款的效力。" to
        "12.1 The formation, validity, interpretation, performance and dispute resolution of this agreement are governed by the laws of the People's Republic of China (excluding conflict-of-law rules).\n12.2 Disputes arising from this agreement shall first be resolved through friendly negotiation; failing that, either party may sue in a competent people's court where the developer is located.\n12.3 Invalidity or unenforceability of any clause does not affect the remaining clauses.",
    "13. 其他" to "13. Miscellaneous",
    "13.1 本协议构成双方就本应用达成的完整协议，取代此前的任何口头或书面沟通。\n13.2 开发者未行使或延迟行使本协议项下的任何权利，不构成对该权利的放弃。\n13.3 本协议标题仅为阅读方便，不影响条款的解释。" to
        "13.1 This agreement constitutes the entire agreement between the parties regarding this app and supersedes any prior oral or written communications.\n13.2 The developer's failure or delay in exercising any right under this agreement does not waive that right.\n13.3 Section headings are for readability only and do not affect interpretation.",
    "14. 联系方式" to "14. Contact",
    "如对本协议有疑问、异议或投诉，可通过「我的 → 支持与反馈」联系开发者。我们将在收到后 15 个工作日内答复。" to
        "For questions, objections or complaints about this agreement, contact the developer via \"Me → Support & Feedback\". We will respond within 15 business days.",

    // ================= 法律文档：隐私政策（VeluneApp.kt privacyRoute） =================
    "本政策说明 Velune 如何收集、使用、存储、共享与保护你的个人信息，以及你享有的权利。本应用采用本地优先设计：在你不主动发起请求的情况下，数据不会离开这台设备。" to
        "This policy explains how Velune collects, uses, stores, shares and protects your personal information, and the rights you have. The app is local-first by design: unless you actively initiate a request, data never leaves this device.",
    "1. 适用范围" to "1. Scope",
    "1.1 本政策适用于 Velune 客户端。\n1.2 你自行配置的第三方服务（模型服务商、搜索引擎、语音服务、QQ 开放平台）由该等主体独立处理你的信息，适用其各自的隐私政策，不适用本政策。" to
        "1.1 This policy applies to the Velune client.\n1.2 Third-party services you configure yourself (model providers, search engines, speech services, the QQ Open Platform) process your information independently under their own privacy policies, which do not govern this app.",
    "2. 我们收集的信息" to "2. Information We Collect",
    "2.1 你主动提供的信息：\n（1）昵称：仅用于在界面与提示词中称呼你；\n（2）角色设定与人设文本：你为 AI 编写的角色描述；\n（3）API Key 及远程服务器凭据：用于向第三方服务发起请求；\n（4）你输入的对话内容与上传的附件。\n2.2 使用过程中产生的信息：\n（1）会话记录、消息内容与时间戳；\n（2）Token 用量统计（本地汇总，用于展示消耗）；\n（3）工具调用记录（调用了什么工具、参数摘要、执行结果）；\n（4）应用设置与偏好（主题、字号、压缩阈值等）。\n2.3 我们**不收集**：设备唯一标识符（IMEI/OAID/IDFA）、位置信息、通讯录、通话记录、应用列表（仅在功能需要时于本机读取，不回传）、剪贴板内容（仅在你主动点击「从剪贴板安装」时读取一次）。\n2.4 本应用不集成任何第三方统计、推送、广告或崩溃上报 SDK。" to
        "2.1 Information you actively provide:\n(1) Nickname: used only to address you in the UI and prompts;\n(2) Persona settings and text: character descriptions you write for the AI;\n(3) API keys and remote-server credentials: used to make requests to third-party services;\n(4) Conversation content you type and attachments you upload.\n2.2 Information generated during use:\n(1) Conversation records, message content and timestamps;\n(2) Token usage statistics (aggregated locally, to display consumption);\n(3) Tool-call records (which tool, argument summary, result);\n(4) App settings and preferences (theme, font size, compression threshold, etc.).\n2.3 We do **not** collect: device identifiers (IMEI/OAID/IDFA), location, contacts, call logs, app lists (read locally only when a feature requires it, never uploaded), or clipboard contents (read once only when you tap \"Install from clipboard\").\n2.4 This app integrates no third-party analytics, push, ads or crash-reporting SDK.",
    "3. 权限与对应信息" to "3. Permissions and Related Data",
    "3.1 日历（读/写）：用于创建、查询与删除日程。仅在你使用日历功能时申请。\n3.2 短信（读）：仅在你明确要求查找验证码或查询短信时读取，读取范围限于你指定的条件，内容仅进入本次对话上下文，不入库、不回传至除你配置的模型服务商以外的任何主体。\n3.3 通知：用于 Agent 任务完成提醒。\n3.4 前台服务：用于维持 QQ 机器人的长连接，需常驻通知。\n3.5 设备管理员：仅用于执行锁屏，不涉及擦除、密码策略等其他能力。\n3.6 所有权限均在功能被使用时按需申请，你可以拒绝，拒绝不影响其他功能。你也可随时在系统设置中撤回授权。" to
        "3.1 Calendar (read/write): to create, query and delete events. Requested only when you use calendar features.\n3.2 SMS (read): read only when you explicitly ask to find verification codes or query messages, limited to the conditions you specify; the content enters only the current conversation context, is never stored, and is sent to no one except the model provider you configured.\n3.3 Notifications: for Agent task-completion reminders.\n3.4 Foreground service: to maintain the QQ bot's persistent connection; requires a permanent notification.\n3.5 Device admin: solely for locking the screen; no wipe, password policy or other capabilities.\n3.6 All permissions are requested on demand when a feature is used. You may refuse; refusal does not affect other features. You can revoke grants anytime in system settings.",
    "4. 处理目的与方式" to "4. Purposes and Methods of Processing",
    "4.1 展示与检索：将会话、消息、角色渲染在界面上供你查看与搜索。\n4.2 完成请求：将必要的对话内容、上下文与附件发送至你配置的模型服务商，以获得回复。\n4.3 上下文管理：当会话过长时，在本机或通过你配置的模型服务生成摘要，以控制上下文长度。\n4.4 用量统计：在本机汇总 Token 消耗，供你了解使用情况。\n4.5 我们不会将你的信息用于用户画像、个性化广告、模型训练或任何与上述目的无关的用途。" to
        "4.1 Display and retrieval: render conversations, messages and personas in the UI for you to view and search.\n4.2 Fulfilling requests: send necessary conversation content, context and attachments to the model provider you configured, to obtain replies.\n4.3 Context management: when a conversation grows long, generate a summary locally or via your configured model service to control context length.\n4.4 Usage statistics: aggregate token consumption locally so you can see your usage.\n4.5 Your information will never be used for profiling, personalized ads, model training, or any purpose unrelated to the above.",
    "5. 委托处理、共享、转让与公开披露" to "5. Entrusted Processing, Sharing, Transfer and Public Disclosure",
    "5.1 我们不会出售你的个人信息。\n5.2 仅在下列情形下，必要信息会被发送至你配置的第三方服务：\n（1）模型服务商：对话内容、上下文、附件（图片以编码形式传输）；\n（2）DeepSeek 搜索接口：你的搜索词、待抓取的网页地址；\n（3）MiMo 语音接口：待合成的文本；\n（4）QQ 开放平台：你要求机器人发送的消息内容；\n（5）你配置的远程服务器：你要求执行的命令与代码。\n5.3 除上述情形及法律法规要求外，我们不会向任何第三方共享、转让或公开披露你的信息。\n5.4 我们不会将你的信息传输至中华人民共和国境外。若你自行将第三方服务配置为境外地址，该等传输由你自主决定并承担相应后果。\n5.5 开发者不会访问、读取或导出你的会话内容——这些数据仅存在于你的设备上。" to
        "5.1 We do not sell your personal information.\n5.2 Only in the following cases is necessary information sent to third-party services you configured:\n(1) Model provider: conversation content, context, attachments (images sent encoded);\n(2) DeepSeek search API: your search terms and the URLs to fetch;\n(3) MiMo speech API: the text to synthesize;\n(4) QQ Open Platform: message content you ask the bot to send;\n(5) Remote servers you configured: commands and code you ask to run.\n5.3 Except for the above and legal requirements, we will not share, transfer or publicly disclose your information to any third party.\n5.4 We do not transmit your information outside the People's Republic of China. If you configure third-party services hosted abroad, such transmission is your own decision and you bear its consequences.\n5.5 The developer will never access, read or export your conversation content — it exists only on your device.",
    "6. 存储地点与期限" to "6. Storage Location and Retention",
    "6.1 所有数据存储于应用私有目录（/data/data/包名/），其他应用无法访问。\n6.2 本应用已关闭系统云备份与设备间迁移，数据不会随备份上传。\n6.3 存储期限：\n（1）会话与消息：保存至你主动删除或卸载应用；\n（2）工具产出文件：保存至你主动删除；\n（3）用量统计：保存至你清空数据；\n（4）API Key：保存至你删除对应服务商或卸载应用。\n6.4 删除后不保留副本，亦无法恢复。" to
        "6.1 All data is stored in the app's private directory (/data/data/<package name>/), inaccessible to other apps.\n6.2 Cloud backup and device-to-device migration are disabled; data is never uploaded with backups.\n6.3 Retention:\n(1) Conversations and messages: kept until you delete them or uninstall;\n(2) Tool output files: kept until you delete them;\n(3) Usage statistics: kept until you clear data;\n(4) API keys: kept until you remove the provider or uninstall.\n6.4 After deletion no copies remain and recovery is impossible.",
    "7. 信息安全" to "7. Information Security",
    "7.1 技术措施：API Key 与服务器凭据经 Android Keystore 生成的非导出密钥以 AES-256-GCM 加密后落盘，明文仅在请求瞬间存在于内存；远程连接校验主机指纹；插件网络请求强制域名白名单并禁止跟随重定向；文件访问限制在应用沙箱内并做路径穿越校验。\n7.2 管理措施：应用不写入含密钥、令牌或消息正文的日志；不集成任何远程上报通道。\n7.3 风险提示：若你的设备已被 root、越狱或植入恶意程序，上述措施可能失效。请仅在可信设备上使用本应用。" to
        "7.1 Technical measures: API keys and server credentials are encrypted at rest with AES-256-GCM using a non-exportable Android Keystore key; plaintext exists in memory only for the instant of a request; remote connections verify host fingerprints; plugin network requests are restricted to a domain whitelist and redirects are blocked; file access is sandboxed with path-traversal checks.\n7.2 Administrative measures: the app never logs keys, tokens or message bodies; no remote reporting channel exists.\n7.3 Risk notice: if your device is rooted, jailbroken or infected with malware, these measures may fail. Use this app only on trusted devices.",
    "8. 你的权利" to "8. Your Rights",
    "依据《中华人民共和国个人信息保护法》，你享有以下权利：\n8.1 查阅与复制：在应用内查看全部会话、角色与设置；通过「导出聊天记录」导出副本。\n8.2 更正与补充：随时修改昵称、角色设定、服务商配置。\n8.3 删除：删除单条会话、清空全部对话、移除插件与密钥、卸载应用。\n8.4 撤回同意：卸载应用即视为撤回全部同意；撤回后不影响此前基于同意已进行的处理。\n8.5 解释与投诉：可通过应用内反馈渠道要求解释本政策，或就个人信息处理提出投诉。我们将在 15 个工作日内答复。" to
        "Under the Personal Information Protection Law of the People's Republic of China you have the following rights:\n8.1 Access and copy: view all conversations, personas and settings in the app; export a copy via \"Export chat history\".\n8.2 Correction: edit your nickname, personas and provider configuration anytime.\n8.3 Deletion: delete individual conversations, clear all chats, remove plugins and keys, uninstall the app.\n8.4 Withdraw consent: uninstalling the app withdraws all consent; withdrawal does not affect processing already carried out based on consent.\n8.5 Explanation and complaints: request clarification of this policy or complain about personal-information processing via the in-app feedback channel. We will respond within 15 business days.",
    "9. 未成年人信息" to "9. Minors' Information",
    "9.1 我们不会主动收集未成年人的个人信息，也不会针对未成年人进行个性化推荐。\n9.2 未满 14 周岁的儿童使用本应用，应由监护人代为阅读本政策并同意。\n9.3 监护人如发现儿童在未获同意的情况下使用本应用，可联系开发者协助删除相关数据。" to
        "9.1 We do not proactively collect minors' personal information or personalize recommendations for them.\n9.2 Use by children under 14 requires a guardian to read and accept this policy on their behalf.\n9.3 Guardians who discover unauthorized use by a child may contact the developer to have related data deleted.",
    "10. 政策更新" to "10. Policy Updates",
    "10.1 本政策修订时会提升版本号，并在下次启动时重新征求你的同意。\n10.2 涉及处理目的、信息种类、共享对象等实质性变更时，我们会在应用内以显著方式提示。\n10.3 你可在「设置 → 关于 → 隐私政策」随时查阅当前版本。" to
        "10.1 Revisions to this policy bump the version number and re-request your consent at next launch.\n10.2 Material changes — purpose, data categories, sharing targets — will be prominently announced in the app.\n10.3 The current version is always available under \"Settings → About → Privacy Policy\".",
    "11. 联系我们" to "11. Contact Us",
    "如对本政策有任何疑问、意见或投诉，可通过「我的 → 支持与反馈」联系开发者。我们将在收到后 15 个工作日内处理并答复。" to
        "For questions, comments or complaints about this policy, contact the developer via \"Me → Support & Feedback\". We will handle and respond within 15 business days.",
    "版本 v2 · 生效日期：2026-11-08 · 替代此前所有版本" to "Version 2 · Effective 2026-11-08 · Supersedes all previous versions",

    // ================= 开源许可 / 关于 =================
    "本应用基于以下开源项目构建，在此致谢。" to "This app is built on the following open-source projects, with thanks.",
    "MiUiX（设计参考）" to "MiUiX (design reference)",
    "Capsule（连续曲率圆角参考）" to "Capsule (continuous-curvature corner reference)",
    "字体" to "Fonts",
    "MiSans · 小米科技，允许免费商用" to "MiSans · Xiaomi, free for commercial use",
    "完整许可文本随安装包分发。" to "Full license texts are distributed with the installer.",
    "关于 Velune" to "About Velune",
    "版本" to "Version",
    "当前状态" to "Status",
    "M1：会话与消息已落本地 SQLite，可新建 / 重命名 / 置顶 / 删除，重启后仍在。模型通道是下一步。" to
        "M1: conversations and messages persist in local SQLite — create / rename / pin / delete, surviving restarts. Model connectivity comes next.",

    // ================= 通知（core/data/system，经 UiText 桥） =================
    "Agent 提醒" to "Agent reminders",
    "Agent 执行结果与提醒" to "Agent results and reminders",
    "实时动态" to "Live update",
    "可跟踪的进行中任务状态（Android 16 实时动态）" to "Trackable in-progress task status (Android 16 Live Updates)",
    "待办提醒" to "Todo reminder",
    "你有一条到点的待办" to "You have a todo that just came due",

    // ================= Agent 工具步骤 display（core/data ToolExecutor，经 UiText 桥） =================
    "已卸载 %s" to "Uninstalled %s",
    "空目录" to "Empty directory",
    "%d 项" to "%d items",
    "%s · %d 字" to "%s · %d chars",
    "已追加 " to "Appended ",
    "已写入 " to "Wrote ",
    "%s（%d 字节）" to "%s (%d bytes)",
    "不存在：%s" to "Not found: %s",
    "已更新日程 #%d" to "Updated event #%d",
    "已删除日程 #%d" to "Deleted event #%d",
    "这段时间没有日程" to "No events in this period",
    "%d 个日程" to "%d events",
    "已加入待办：%s" to "Added to todo: %s",
    "没有未完成的待办" to "No open todos",
    "%d 条待办" to "%d todos",
    "已完成 #%d" to "Completed #%d",
    "没有找到 #%d" to "#%d not found",
    "已发送通知" to "Notification sent",
    "未发送：缺少通知权限" to "Not sent: notification permission missing",
    "没有匹配的应用" to "No matching apps",
    "%d 个应用" to "%d apps",
    "已打开 %s" to "Opened %s",
    "收件箱为空" to "Inbox empty",
    "%d 条短信" to "%d messages",
    "本地" to "Local",
    "语音已生成" to "Speech generated",
    "已锁屏" to "Screen locked",
    "已发送到 %s" to "Sent to %s",
    "已发送到" to "Sent to",
    "群" to "Group ",
    "%s%d" to "%s %d",
    "已安装 %s，%d 个工具" to "Installed %s, %d tools",
    "%d 字符" to "%d chars",

    // ================= 权限说明（AndroidPermissionRequester / PermissionScreen） =================
    "Agent 可申请" to "Agent may request",
    "读取日历" to "Read calendar",
    "让你问「今天有什么安排」时能查到系统日程" to "So you can ask \"what's on today\" and see your calendar",
    "写入日历" to "Write calendar",
    "让 Agent 帮你新建日程与提醒" to "Let the Agent create events and reminders for you",
    "发送通知" to "Notifications",
    "任务完成或到点提醒时通知你" to "Notify you when tasks finish or reminders come due",
    "读取短信" to "Read SMS",
    "让你问「谁给我发短信」「有没有验证码」时能读到收件箱" to
        "So you can ask \"who texted me\" or \"any verification codes\" and get inbox answers",

    // ================= QQ 机器人服务（QqBridgeService / 状态与通知） =================
    "QQ 机器人运行中" to "QQ bot running",
    "QQ 机器人" to "QQ bot",
    "保持与 QQ 的连接" to "Keeps the connection to QQ alive",
    "自动回复未开启：收到消息未处理" to "Auto-reply off: message received but not handled",
    "正在连接 QQ 网关…" to "Connecting to QQ gateway…",
    "已连接 QQ 网关，等待消息" to "Connected to QQ gateway, waiting for messages",
    "正在连接协议端…" to "Connecting to protocol endpoint…",
    "已连接协议端，等待消息" to "Connected to protocol endpoint, waiting for messages",
    "缺少 AppID / AppSecret" to "Missing AppID / AppSecret",
    "未填写 WS 地址" to "WS address not set",
    "获取凭证失败，消息未发出" to "Failed to get credentials; message not sent",
    "群消息缺少 group_openid" to "Group message missing group_openid",
    "QQ 机器人已停止" to "QQ bot stopped",
    "已停止：后台时长达到系统上限，请重新开启" to "Stopped: background time limit reached. Re-enable it in the app",
    "系统限制后台运行时长已达上限，请在 App 内重新开启 QQ 机器人" to
        "System background time limit reached. Re-enable the QQ bot in the app",
    "已把第一位发消息的用户设为绑定者，其他来源将被忽略" to
        "First message sender set as the owner; other sources will be ignored",
    "收到未授权来源的消息，已忽略" to "Message from an unauthorized source, ignored",
    "已收到消息，但自动回复未开启" to "Message received, but auto-reply is off",
    "有一条消息超过被动回复窗口，未发送" to "A message exceeded the passive-reply window and was not sent",
    "发送失败：%s" to "Send failed: %s",
    "回复失败：%s" to "Reply failed: %s",
    "我这会儿答不上来，稍后再试" to "I can't answer right now — try again later",
    "我暂时只看得懂文字，图片/表情回 App 里发给我吧" to
        "I can only read text for now — send images/stickers in the app instead",

    // ================= 插件安装（PluginInstaller / PluginsScreen） =================
    "已安装 %s（%d 个工具）" to "Installed %s (%d tools)",
    "只支持 https 清单地址（本机调试可用 http://localhost）" to
        "Only https manifest URLs are supported (use http://localhost for local debugging)",
    "下载清单失败：%s" to "Failed to download manifest: %s",
    "已安装" to "Installed",
    "安装" to "Install",
    "也可以直接让 Agent 帮你装" to "You can also just ask the Agent to install one",

    // ================= 杂项（Provider / Ssh / QqBot / Appearance / Mcp / Prompt / Consent） =================
    "压缩历史时用，建议选便宜的" to "Used for history summarization; a cheap model is fine",
    "示例：%s" to "Example: %s",
    "当前可用模型以 %s 为准；应用不内置清单，模型迭代比发版快" to
        "Available models follow %s; the app ships no hardcoded list — models move faster than releases",
    "运行环境" to "Runtime",
    "备注名" to "Alias",
    "零额外包体积，命令写坏了也只影响那台机器。手机本身不会变成 Linux。" to
        "Zero extra APK size; a broken command only affects that machine. Your phone itself does not become Linux.",
    "界面已支持中英双语，选择即时生效；「跟随系统」时与设备语言保持一致。字号总放大上限为 1.8 倍（系统字号会与应用字号相乘）。" to
        "The UI supports Chinese and English; changes apply instantly. \"Follow system\" matches the device language. Total font scaling caps at 1.8x (system and app scale multiply).",
    "MCP 工具全部需要逐次确认：协议里没有「是否只读」的声明，我们无从判断，所以按有副作用处理。服务器由你自行配置，其行为由该服务器负责。" to
        "Every MCP tool requires per-call confirmation: the protocol has no read-only declaration, so we treat them all as side-effecting. You configure the server yourself; its behavior is its own responsibility.",
    "保存后会立刻发起一次握手。连不上通常是：地址路径不对（很多服务器是 /mcp 或 /sse）、缺少鉴权头、或服务器只支持 stdio 传输。" to
        "A handshake fires right after saving. Common failures: wrong path (many servers use /mcp or /sse), missing auth header, or a stdio-only server.",
    "编辑角色" to "Edit persona",
    "本地优先的 AI 对话工具，会话与密钥只保存在这台设备上。\n开始前请阅读并同意以下条款。" to
        "A local-first AI chat tool — conversations and keys stay on this device only.\nPlease read and accept the terms below before starting.",

    // ================= VeluneApplication（子模型路由错误） =================
    "还没有配置任何服务商：设置 → 模型与密钥" to "No provider configured yet: Settings → Models & Keys",
    "子模型缺少 API Key：设置 → 模型与密钥" to "Sub-model has no API key: Settings → Models & Keys",
    "子模型没有返回内容" to "Sub-model returned nothing",

    // ================= 开源许可页正文（LicensesScreen） =================
    "一、核心框架" to "1. Core frameworks",
    "界面与运行时建立在 Kotlin 与 Jetpack Compose 之上，这两者决定了应用的基本行为与无障碍能力。它们均为 Apache-2.0，可以自由修改与分发，但需保留版权声明。" to
        "The UI and runtime are built on Kotlin and Jetpack Compose, which define the app's core behavior and accessibility. Both are Apache-2.0: free to modify and redistribute, copyright notice required.",
    "Apache-2.0 要求：分发二进制时附带许可证副本，并声明你对原始代码的修改。它不要求公开你自己的源代码。" to
        "Apache-2.0 terms: include a license copy when distributing binaries and state your changes to the original code. It does not require open-sourcing your own code.",
    "二、设计与交互" to "2. Design and interaction",
    "界面的度量、动效曲线与控件形态参考了 HyperOS 的设计语言，实现全部为本项目自绘。开源的部分只用作参考与组件来源，不包含小米的私有资源。" to
        "The UI's metrics, motion curves and control shapes follow HyperOS's design language; all implementation is drawn by this project. Open-source parts serve only as reference and component sources — no Xiaomi proprietary assets are included.",
    "本应用未内置任何小米的图片、图标或字体文件；视觉规格是测量结果的重写，不是资源搬运。其中「关于」页的动态底色移植自 Nexio 课程表的 AGSL 着色器（AGPL-3.0），配色与绘制参数按真机截图重新测定。AGPL-3.0 要求衍生作品以同一许可开源，若需闭源分发，应替换该实现。" to
        "The app bundles no Xiaomi images, icons or fonts; visual specs are re-measured, not copied assets. The dynamic gradient on the About page is ported from Nexio timetable's AGSL shader (AGPL-3.0), with colors and draw parameters re-measured on device. AGPL-3.0 requires derivatives to be open-sourced under the same license — replace the implementation if you need closed-source distribution.",
    "Haze（背景模糊）· Chris Banes" to "Haze (background blur) · Chris Banes",
    "Nexio 课程表（动态底色着色器）· HaoZai000" to "Nexio timetable (dynamic background shader) · HaoZai000",
    "Capsule（连续曲率圆角）· Kyant0" to "Capsule (continuous-curvature corners) · Kyant0",
    "三、字体" to "3. Fonts",
    "界面优先使用系统字体。若设备安装了 MiSans，中文会以 MiSans 渲染；否则回退系统默认字体，字形有差异，但字号与行高保持一致。" to
        "The UI prefers system fonts. If MiSans is installed, Chinese renders in MiSans; otherwise the system default is used — glyph shapes differ, but size and line height stay consistent.",
    "如果你认为某处使用超出了上述许可范围，请通过「交流与反馈」联系，我们会尽快处理。" to
        "If you believe any use exceeds the licenses above, contact us via \"Community & Feedback\" and we will respond promptly.",
)
