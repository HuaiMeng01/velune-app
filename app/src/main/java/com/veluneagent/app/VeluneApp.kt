package com.veluneagent.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.veluneagent.core.data.Conversation
import com.veluneagent.core.design.component.VxActionSheet
import com.veluneagent.core.design.component.VxCard
import com.veluneagent.core.design.component.VxFloatingTabBar
import com.veluneagent.core.design.component.VxRow
import com.veluneagent.core.design.component.VxPageSwitcher
import com.veluneagent.core.design.component.VxSheetAction
import com.veluneagent.core.design.component.VxIcon
import com.veluneagent.core.design.component.VxTab
import com.veluneagent.core.design.component.VxText
import com.veluneagent.core.design.l10n.VxL10n
import com.veluneagent.core.design.l10n.tr
import com.veluneagent.core.design.l10n.trf
import com.veluneagent.core.design.theme.VeluneTheme
import com.veluneagent.core.design.theme.VxMotion
import com.veluneagent.feature.chat.ui.ChatScreen
import com.veluneagent.feature.chat.ui.AgentRunsScreen
import com.veluneagent.feature.messages.MessagesScreen
import com.veluneagent.feature.settings.DetailScreen
import com.veluneagent.core.data.Provider
import com.veluneagent.core.data.ProviderStore
import com.veluneagent.feature.settings.ProfileScreen
import com.veluneagent.feature.settings.ProviderEditScreen
import com.veluneagent.feature.settings.ProviderListScreen
import com.veluneagent.feature.settings.SettingsScreen
import com.veluneagent.feature.settings.StorageScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text

/** 数据库文件占用，真实读取而非写死。 */
private fun storageLabel(): String {
    val ctx = VeluneAppContextHolder.context ?: return "—"
    val bytes = ctx.getDatabasePath("velune.db").length()
    return when {
        bytes <= 0 -> "0 KB"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / 1024f / 1024f)
    }
}

private object VeluneAppContextHolder {
    var context: android.content.Context? = null
}

private data class DetailRoute(
    val title: String,
    val sections: List<Pair<String, String>>,
    val footer: String? = null,
)

/**
 * 页面路由快照：把十几个互斥的导航开关折叠成一个值，交给 VxPageSwitcher 做推页转场。
 *
 * 页面内容必须是它的纯函数——路由参数（会话 id、详情 route）随实例携带，
 * 退场时旧页才能按推入前的原样渲染，而不是被外层状态“偷换”成新页。
 * depth 决定转场方向：目标页更深 = 推入，更浅 = 返回。
 */
private sealed interface Page {
    data class Tab(val tab: Int) : Page
    data class Chat(val conversationId: Long) : Page
    data class Detail(val route: DetailRoute) : Page
    // Storage 不携带 stats 快照：stats 会在页内刷新，带上快照等于每次刷新都触发一次转场
    object Storage : Page
    object Plugins : Page
    object Permissions : Page
    object Tools : Page
    object Files : Page
    object Linux : Page
    object Appearance : Page
    object Prompts : Page
    object QqBot : Page
    object Mcp : Page
    object Ssh : Page
    object ProviderList : Page
    data class ProviderEdit(val providerId: String?) : Page
    data class Runs(val conversationId: Long) : Page

    val depth: Int
        get() = when (this) {
            is Tab -> 0
            is Chat -> 1
            is Detail -> 2
            is Storage -> 3
            Plugins -> 4
            Permissions -> 5
            Tools -> 6
            Files -> 7
            Linux -> 8
            Appearance -> 9
            Prompts -> 10
            QqBot -> 11
            Mcp -> 12
            Ssh -> 13
            ProviderList -> 14
            is ProviderEdit -> 15
            is Runs -> 16
        }
}

private val tabs = listOf(
    VxTab(icon = VxIcon.Message, label = "消息"),
    VxTab(icon = VxIcon.Sliders, label = "设置"),
    VxTab(icon = VxIcon.Person, label = "我的"),
)

private val agreementRoute = DetailRoute(
    title = "用户协议",
    sections = listOf(
        "提示" to "本协议是你与 Velune 开发者之间具有法律约束力的约定。请逐条阅读，特别是加粗提示的免责与责任限制条款。点击「同意并继续」即表示你已完整阅读、理解并接受本协议全部内容。若你不同意任一条款，请停止安装或使用本应用。",
        "1. 定义" to "1.1「本应用」指 Velune 客户端软件及其后续版本。\n1.2「开发者」指本应用的著作权人及运营者。\n1.3「第三方服务」指由你自行选择并配置的模型、搜索、语音等服务提供方。\n1.4「用户内容」指你输入、上传或通过本应用生成、存储的文字、图片、文件及其他信息。",
        "2. 协议的接受与变更" to "2.1 你在首次启动时点击「同意并继续」，即视为接受本协议。\n2.2 开发者可能因法律法规变化或功能调整修订本协议。修订后的协议将在应用内以显著方式提示，并提升版本号；你需重新确认后方可继续使用。\n2.3 若你不同意修订内容，应停止使用并卸载本应用。",
        "3. 服务内容与性质" to "3.1 本应用是一款本地优先的 AI 对话与自动化工具，提供会话管理、提示词与角色管理、工具调用编排等功能。\n3.2 本应用不提供模型推理服务。所有模型调用均由你自行配置的第三方服务完成，开发者不参与该等服务的设计、运营与结算。\n3.3 开发者不对第三方服务的可用性、响应速度、输出内容质量或合规性作出任何承诺或担保。",
        "4. 账号、凭证与安全" to "4.1 本应用无需注册账号，不设云端账户体系。\n4.2 你应妥善保管自行配置的 API Key、访问令牌、远程服务器地址与凭据。上述信息经加密后仅存储于你的设备。\n4.3 因你主动泄露、转借、设备丢失或被他人物理接触导致的凭证外泄及由此产生的费用与损失，由你自行承担。\n4.4 你发现凭证可能已泄露时，应立即在对应服务商处吊销并更换。",
        "5. 用户行为规范" to "5.1 你承诺不利用本应用从事下列行为：\n（1）生成、存储或传播违反法律法规、危害国家安全、破坏社会稳定、侮辱诽谤他人或侵犯他人合法权益的内容；\n（2）未经授权侵入、扫描、探测他人计算机系统或网络，或绕过其访问控制；\n（3）批量发送骚扰信息、垃圾信息，或实施诈骗、赌博、洗钱等违法活动；\n（4）制作、传播恶意程序，或利用工具调用能力破坏设备、数据或他人系统；\n（5）侵害他人知识产权、商业秘密、个人信息权益；\n（6）其他违反法律法规、公序良俗或本协议约定的行为。\n5.2 你使用工具调用能力（包括但不限于文件读写、命令执行、消息发送、日历修改、锁屏）所产生的一切后果，由你自行承担。开发者已通过逐次确认机制提示风险，但你仍是操作的最终决定者。\n5.3 若你违反上述约定，开发者有权在不另行通知的情况下中止或终止向你提供服务，并保留追究法律责任的权利。",
        "6. 用户内容与知识产权" to "6.1 你对输入本应用的内容享有合法权利，并对其合法性、真实性、准确性负责。\n6.2 通过第三方服务生成的内容，其权利归属与使用限制适用该第三方服务商的条款。\n6.3 本应用的源代码以 GNU Affero 通用公共许可证第 3.0 版（AGPL-3.0）授权开源，完整许可证文本见仓库根目录的 LICENSE 文件。你可以在该许可证允许的范围内使用、研究、修改和再分发本应用；除许可证另有规定外，界面设计、图标与文档等素材的权利仍归开发者所有。\n6.4 本应用使用的其他开源组件依其各自许可证授权，具体清单见「开源许可」页面。",
        "7. 第三方服务与费用" to "7.1 你在本应用中配置第三方服务，即表示你同意该服务商的服务条款与隐私政策。\n7.2 使用第三方服务产生的费用由你与该服务商直接结算。开发者不代收、不垫付、不开具发票，亦不对计费争议承担责任。\n7.3 你应自行关注账户额度与计费规则。因额度耗尽、密钥失效导致服务不可用，不属于本应用的缺陷。",
        "8. 免责声明与责任限制" to "8.1 人工智能生成内容具有不确定性，可能存在错误、过时、片面或与事实不符的情况。你应自行核实重要信息后再作决策。\n8.2 本应用不构成医疗、法律、金融、税务等专业意见。请勿将其用于诊断疾病、作出法律判断、进行投资决策等高风险场景。\n8.3 本应用按「现状」提供。在法律允许的最大范围内，开发者不对因使用或无法使用本应用导致的间接损失、利润损失、数据丢失、商誉损失承担责任。\n8.4 因不可抗力、网络故障、第三方服务中断、系统限制、设备故障等非开发者原因造成的服务异常，开发者不承担责任。\n8.5 开发者对用户内容不做主动审查，但有权在收到合法通知后移除涉嫌违法的内容。",
        "9. 服务的变更、中断与终止" to "9.1 开发者可能持续优化产品，包括新增、调整或下线部分功能。重大变更将通过应用内公告提示。\n9.2 你可随时停止使用并卸载本应用，无需通知开发者。\n9.3 本协议终止后，第 6、8、10、11 条及其他依其性质应当继续有效的条款继续有效。",
        "10. 未成年人保护" to "10.1 若你为未满 18 周岁的未成年人，应在监护人陪同下阅读本协议，并在取得监护人同意后使用。\n10.2 若你为未满 14 周岁的儿童，监护人应代为阅读并同意本协议及隐私政策，并对其使用行为进行监督与指导。\n10.3 监护人发现未成年人在未获同意的情况下使用本应用，可联系开发者协助处理。",
        "11. 通知与送达" to "11.1 开发者可通过应用内公告、版本更新说明等方式向你发送通知，该等通知于发送之日视为送达。\n11.2 你应向开发者提供有效联系方式（如通过应用内反馈渠道），并保证其真实有效。",
        "12. 法律适用与争议解决" to "12.1 本协议的订立、效力、解释、履行及争议解决均适用中华人民共和国法律（不含冲突法规则）。\n12.2 因本协议产生的争议，双方应首先友好协商解决；协商不成的，任何一方可向开发者所在地有管辖权的人民法院提起诉讼。\n12.3 若本协议的任何条款被认定无效或不可执行，不影响其余条款的效力。",
        "13. 其他" to "13.1 本协议构成双方就本应用达成的完整协议，取代此前的任何口头或书面沟通。\n13.2 开发者未行使或延迟行使本协议项下的任何权利，不构成对该权利的放弃。\n13.3 本协议标题仅为阅读方便，不影响条款的解释。",
        "14. 联系方式" to "如对本协议有疑问、异议或投诉，可通过「我的 → 支持与反馈」联系开发者。我们将在收到后 15 个工作日内答复。",
    ),
    footer = "版本 v2 · 生效日期：2026-11-08 · 替代此前所有版本",
)

private val privacyRoute = DetailRoute(
    title = "隐私政策",
    sections = listOf(
        "提示" to "本政策说明 Velune 如何收集、使用、存储、共享与保护你的个人信息，以及你享有的权利。本应用采用本地优先设计：在你不主动发起请求的情况下，数据不会离开这台设备。",
        "1. 适用范围" to "1.1 本政策适用于 Velune 客户端。\n1.2 你自行配置的第三方服务（模型服务商、搜索引擎、语音服务、QQ 开放平台）由该等主体独立处理你的信息，适用其各自的隐私政策，不适用本政策。",
        "2. 我们收集的信息" to "2.1 你主动提供的信息：\n（1）昵称：仅用于在界面与提示词中称呼你；\n（2）角色设定与人设文本：你为 AI 编写的角色描述；\n（3）API Key 及远程服务器凭据：用于向第三方服务发起请求；\n（4）你输入的对话内容与上传的附件。\n2.2 使用过程中产生的信息：\n（1）会话记录、消息内容与时间戳；\n（2）Token 用量统计（本地汇总，用于展示消耗）；\n（3）工具调用记录（调用了什么工具、参数摘要、执行结果）；\n（4）应用设置与偏好（主题、字号、压缩阈值等）。\n2.3 我们**不收集**：设备唯一标识符（IMEI/OAID/IDFA）、位置信息、通讯录、通话记录、应用列表（仅在功能需要时于本机读取，不回传）、剪贴板内容（仅在你主动点击「从剪贴板安装」时读取一次）。\n2.4 本应用不集成任何第三方统计、推送、广告或崩溃上报 SDK。",
        "3. 权限与对应信息" to "3.1 日历（读/写）：用于创建、查询与删除日程。仅在你使用日历功能时申请。\n3.2 短信（读）：仅在你明确要求查找验证码或查询短信时读取，读取范围限于你指定的条件，内容仅进入本次对话上下文，不入库、不回传至除你配置的模型服务商以外的任何主体。\n3.3 通知：用于 Agent 任务完成提醒。\n3.4 前台服务：用于维持 QQ 机器人的长连接，需常驻通知。\n3.5 设备管理员：仅用于执行锁屏，不涉及擦除、密码策略等其他能力。\n3.6 所有权限均在功能被使用时按需申请，你可以拒绝，拒绝不影响其他功能。你也可随时在系统设置中撤回授权。",
        "4. 处理目的与方式" to "4.1 展示与检索：将会话、消息、角色渲染在界面上供你查看与搜索。\n4.2 完成请求：将必要的对话内容、上下文与附件发送至你配置的模型服务商，以获得回复。\n4.3 上下文管理：当会话过长时，在本机或通过你配置的模型服务生成摘要，以控制上下文长度。\n4.4 用量统计：在本机汇总 Token 消耗，供你了解使用情况。\n4.5 我们不会将你的信息用于用户画像、个性化广告、模型训练或任何与上述目的无关的用途。",
        "5. 委托处理、共享、转让与公开披露" to "5.1 我们不会出售你的个人信息。\n5.2 仅在下列情形下，必要信息会被发送至你配置的第三方服务：\n（1）模型服务商：对话内容、上下文、附件（图片以编码形式传输）；\n（2）DeepSeek 搜索接口：你的搜索词、待抓取的网页地址；\n（3）MiMo 语音接口：待合成的文本；\n（4）QQ 开放平台：你要求机器人发送的消息内容；\n（5）你配置的远程服务器：你要求执行的命令与代码。\n5.3 除上述情形及法律法规要求外，我们不会向任何第三方共享、转让或公开披露你的信息。\n5.4 我们不会将你的信息传输至中华人民共和国境外。若你自行将第三方服务配置为境外地址，该等传输由你自主决定并承担相应后果。\n5.5 开发者不会访问、读取或导出你的会话内容——这些数据仅存在于你的设备上。",
        "6. 存储地点与期限" to "6.1 所有数据存储于应用私有目录（/data/data/包名/），其他应用无法访问。\n6.2 本应用已关闭系统云备份与设备间迁移，数据不会随备份上传。\n6.3 存储期限：\n（1）会话与消息：保存至你主动删除或卸载应用；\n（2）工具产出文件：保存至你主动删除；\n（3）用量统计：保存至你清空数据；\n（4）API Key：保存至你删除对应服务商或卸载应用。\n6.4 删除后不保留副本，亦无法恢复。",
        "7. 信息安全" to "7.1 技术措施：API Key 与服务器凭据经 Android Keystore 生成的非导出密钥以 AES-256-GCM 加密后落盘，明文仅在请求瞬间存在于内存；远程连接校验主机指纹；插件网络请求强制域名白名单并禁止跟随重定向；文件访问限制在应用沙箱内并做路径穿越校验。\n7.2 管理措施：应用不写入含密钥、令牌或消息正文的日志；不集成任何远程上报通道。\n7.3 风险提示：若你的设备已被 root、越狱或植入恶意程序，上述措施可能失效。请仅在可信设备上使用本应用。",
        "8. 你的权利" to "依据《中华人民共和国个人信息保护法》，你享有以下权利：\n8.1 查阅与复制：在应用内查看全部会话、角色与设置；通过「导出聊天记录」导出副本。\n8.2 更正与补充：随时修改昵称、角色设定、服务商配置。\n8.3 删除：删除单条会话、清空全部对话、移除插件与密钥、卸载应用。\n8.4 撤回同意：卸载应用即视为撤回全部同意；撤回后不影响此前基于同意已进行的处理。\n8.5 解释与投诉：可通过应用内反馈渠道要求解释本政策，或就个人信息处理提出投诉。我们将在 15 个工作日内答复。",
        "9. 未成年人信息" to "9.1 我们不会主动收集未成年人的个人信息，也不会针对未成年人进行个性化推荐。\n9.2 未满 14 周岁的儿童使用本应用，应由监护人代为阅读本政策并同意。\n9.3 监护人如发现儿童在未获同意的情况下使用本应用，可联系开发者协助删除相关数据。",
        "10. 政策更新" to "10.1 本政策修订时会提升版本号，并在下次启动时重新征求你的同意。\n10.2 涉及处理目的、信息种类、共享对象等实质性变更时，我们会在应用内以显著方式提示。\n10.3 你可在「设置 → 关于 → 隐私政策」随时查阅当前版本。",
        "11. 联系我们" to "如对本政策有任何疑问、意见或投诉，可通过「我的 → 支持与反馈」联系开发者。我们将在收到后 15 个工作日内处理并答复。",
    ),
    footer = "版本 v2 · 生效日期：2026-11-08 · 替代此前所有版本",
)

/**
 * 「关于」的路由键。
 *
 * 内容由 [AboutScreen] 自行绘制（大图标 / 版本号 / 更新日志 / 卡片列表），
 * 这里只作为导航键使用，不携带正文。
 */
private val aboutRoute = DetailRoute(title = "关于", sections = emptyList())

/**
 * 「开源许可」的路由键。
 *
 * 正文由 LicensesScreen 承载（按框架 / 设计交互 / 字体分组，含各项目主页指引）。
 * 各依赖的分组清单见 LicensesScreen。
 */
private val licensesRoute = DetailRoute(title = "开源许可", sections = emptyList())

/**
 * 外部链接。
 *
 * 留空时不做"假跳转"：直接提示尚未公布，比打开一个 404 页面诚实。
 * 填好地址后无需改其他代码。
 */
private const val REPOSITORY_URL = "https://github.com/HuaiMeng01/velune-app"
private const val DONATION_URL = ""

/** 反馈文档（金山文档）：issue 模板与已知问题都记在这里，比私聊更容易被后来的人搜到。 */
private const val FEEDBACK_DOC_URL = "https://www.kdocs.cn/l/cbaYAaq1FHfV"

/**
 * QQ 交流群分享链接。
 *
 * 注意这里是**分享链接**（带 authKey），不是普通的群号加群链接；
 * 分享链接里的 authKey 有时效，过期后需要在群设置里重新生成。
 */
private const val QQ_GROUP_URL = "https://qun.qq.com/universal-share/share?ac=1" +
    "&authKey=unPssp6R6NWTyIak3TTlky%2BVOMizU0PQVlEHF9NHg39hVGrw%2B54hKZ74wXhCrJ%2Bp" +
    "&busi_data=eyJncm91cENvZGUiOiI5NzgzNzg4MDgiLCJ0b2tlbiI6IlIwY2tXOTAxL2ZFb2VpbTk5cEQ2WW55WTNIRFNuKzZJSk5YNGdrZU1sQ2ZoRVFZMTRrcWhiN0F1M1A3N0ZMUzgiLCJ1aW4iOiI3NDI2NjM3MzkifQ%3D%3D" +
    "&data=NwsEspIwM-PBaSjXmj3guSd31bwfj0IuEfsGIfE4bnZEnveBmfIPiPAxbuHN-9Jz8Wer2DGQ2OKiHtAhdEzyUA" +
    "&svctype=4&tempid=h5_group_info"

private fun openExternal(context: android.content.Context, url: String, fallback: String) {
    if (url.isBlank()) {
        android.widget.Toast.makeText(context, tr(fallback), android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        android.widget.Toast.makeText(context, tr("没有可打开该链接的应用"), android.widget.Toast.LENGTH_SHORT).show()
    }
}

private val supportRoute = DetailRoute(
    title = "支持我们",
    sections = listOf(
        "" to "Velune 由个人开发与维护。如果它对你有用，下面这些方式都能帮到项目。",
        "反馈问题" to "遇到 bug 或想要新功能，通过「交流与反馈」里的反馈文档提交，写清现象与操作步骤，比口头描述更容易定位。",
        "交流分享" to "加入 QQ 群聊聊使用心得，也欢迎把应用分享给可能需要的朋友。",
        "写插件" to "把常用的能力或工作流封装成插件分享出去，是对社区最直接的帮助。",
    ),
    footer = "谢谢你使用 Velune。",
)

@Composable
fun VeluneApp() {
    val colors = VeluneTheme.colors
    val context = LocalContext.current
    VeluneAppContextHolder.context = context.applicationContext

    // 未同意条款时先过同意门：主界面完全不渲染。
    // 协议只作为设置里的静态页存在时，等于用户从未同意过 —— 既不合规，商店审核也过不了。
    val accepted by LegalConsent.accepted.collectAsState()
    if (!accepted) {
        ConsentGate()
        return
    }

    val app = remember(context) { context.applicationContext as VeluneApplication }
    val store = remember(context) { app.chatStore }
    // 提示词仓库：消息页的「创建角色」要在这里落库并绑到新会话上
    val promptStore = remember(context) { com.veluneagent.core.data.prompt.PromptStore(context) }
    // 新建对话时要让用户挑角色，所以这里需要角色列表
    val prompts by promptStore.observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    // 会话列表排序偏好，改完 store 的 observeConversations 会自动重查
    val sortMode by com.veluneagent.core.data.ChatPrefs.sortMode
        .collectAsStateWithLifecycle()
    val pluginStore = remember(app) { app.pluginStore }
    val agentDeps = remember(app) {
        com.veluneagent.feature.chat.ui.AgentDeps(
            catalog = app.toolCatalog,
            executor = app.toolExecutor,
            runStore = app.agentRunStore,
            pluginStore = app.pluginStore,
        )
    }
    val installedPlugins by pluginStore.plugins.collectAsState()
    val conversations by store.observeConversations()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    var tab by remember { mutableIntStateOf(0) }
    var activeConversationId by remember { mutableStateOf<Long?>(null) }
    var detail by remember { mutableStateOf<DetailRoute?>(null) }
    // 服务商页：null=关闭；List=列表；Edit(id=null)=新增
    var providerListOpen by remember { mutableStateOf(false) }
    var editingProviderId by remember { mutableStateOf<String?>(null) }
    var editingNew by remember { mutableStateOf(false) }
    // 数据与存储页 + 清空确认
    var storageOpen by remember { mutableStateOf(false) }
    var pluginsOpen by remember { mutableStateOf(false) }
    var permissionsOpen by remember { mutableStateOf(false) }
    var toolsOpen by remember { mutableStateOf(false) }
    var filesOpen by remember { mutableStateOf(false) }
    // 非空时显示该会话的执行轨迹页
    var runsFor by remember { mutableStateOf<Long?>(null) }
    var mcpOpen by remember { mutableStateOf(false) }
    var qqConfigOpen by remember { mutableStateOf(false) }
    var promptsOpen by remember { mutableStateOf(false) }
    var storageNotice by remember { mutableStateOf<String?>(null) }
    var nameDialogOpen by remember { mutableStateOf(false) }
    var appearanceOpen by remember { mutableStateOf(false) }
    var linuxOpen by remember { mutableStateOf(false) }
    val linuxState by com.veluneagent.core.data.linux.LinuxEnv.state.collectAsState()
    val conversationsLoaded by store.conversationsLoaded.collectAsState()
    val themeMode by com.veluneagent.core.data.profile.AppearancePrefs.themeMode.collectAsState()
    val fontScale by com.veluneagent.core.data.profile.AppearancePrefs.fontScale.collectAsState()
    val nickname by com.veluneagent.core.data.profile.UserProfile.nickname.collectAsState()
    var exporting by remember { mutableStateOf(false) }
    var sshOpen by remember { mutableStateOf(false) }
    val disabledTools by com.veluneagent.core.data.agent.ToolPrefs.disabled.collectAsState()
    val thinkingEnabled by com.veluneagent.core.data.ChatPrefs.thinkingEnabled.collectAsState()
    val reasoningEffort by com.veluneagent.core.data.ChatPrefs.reasoningEffort.collectAsState()
    var permissionTick by remember { mutableIntStateOf(0) }
    // 用户可能去系统设置里改了权限、或刚激活了设备管理员，回前台必须重读
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var pluginMessage by remember { mutableStateOf<String?>(null) }
    var pluginMessageOk by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf(com.veluneagent.core.data.StoreStats(0, 0, 0)) }
    var usage by remember { mutableStateOf(com.veluneagent.core.data.UsageSummary(0, 0, 0, 0)) }
    val providers by ProviderStore.providers.collectAsState()
    val activeId by ProviderStore.activeId.collectAsState()
    val probeResults by ProviderStore.probeResults.collectAsState()
    val qqConfig by com.veluneagent.core.data.im.QqBot.config.collectAsState()
    // 「自动」当前沿用的会话 id：设置页用它把隐式状态写出来（沿用哪条 / 下次新建）
    val qqAutoConvId by com.veluneagent.core.data.im.QqBot.autoConversationId.collectAsState()
    val sshHost by com.veluneagent.core.data.ssh.Ssh.host.collectAsState()
    val qqBridgeState by QqBridge.state.collectAsState()
    val qqBridgeMessage by QqBridge.message.collectAsState()
    val activeProviderModel = providers.firstOrNull { it.id == activeId }?.model
        ?: providers.firstOrNull()?.model
        ?: ""
    val hazeState = remember { HazeState() }

    // 页面快照：构造顺序 = 层级深度（越靠前越深），与返回键的剥离顺序一致。
    val page: Page = when {
        runsFor != null -> Page.Runs(runsFor!!)
        editingNew || editingProviderId != null -> Page.ProviderEdit(editingProviderId)
        providerListOpen -> Page.ProviderList
        sshOpen -> Page.Ssh
        qqConfigOpen -> Page.QqBot
        mcpOpen -> Page.Mcp
        promptsOpen -> Page.Prompts
        appearanceOpen -> Page.Appearance
        linuxOpen -> Page.Linux
        filesOpen -> Page.Files
        toolsOpen -> Page.Tools
        permissionsOpen -> Page.Permissions
        pluginsOpen -> Page.Plugins
        storageOpen -> Page.Storage
        detail != null -> Page.Detail(detail!!)
        activeConversationId != null -> Page.Chat(activeConversationId!!)
        else -> Page.Tab(tab)
    }

    val activeConversation = conversations.firstOrNull { it.id == activeConversationId }

    /** 当前是否停在主标签页（没有任何子页覆盖）。tab bar 与返回键都依赖这个判据。 */
    val isRootTab = detail == null &&
        activeConversationId == null &&
        !providerListOpen && !storageOpen && !pluginsOpen && !permissionsOpen &&
        !toolsOpen && !filesOpen && !qqConfigOpen && !sshOpen && !promptsOpen && !appearanceOpen &&
        !linuxOpen &&
        !editingNew && editingProviderId == null

    fun clearSubPages() {
        detail = null
        storageOpen = false
        pluginsOpen = false
        permissionsOpen = false
        toolsOpen = false
        filesOpen = false
        qqConfigOpen = false
        promptsOpen = false
        appearanceOpen = false
        linuxOpen = false
        sshOpen = false
        providerListOpen = false
        editingNew = false
        editingProviderId = null
    }

    /**
     * 离开会话时顺手回收空会话。
     *
     * 聊天页顶栏加了「新对话」之后，误点会更容易发生；如果每次误点都在列表里
     * 留一条「新对话」，一周后列表就没法看了。判据很干脆：一条消息都没有就删。
     */
    fun closeConversation() {
        val closing = activeConversationId
        activeConversationId = null
        if (closing != null) {
            scope.launch {
                if (store.messages(closing, 1).isEmpty()) store.deleteConversation(closing)
            }
        }
    }

    /** 新建前先回收「上一条还没说话的空会话」，避免连点多次堆出一排空行。 */
    fun startNewConversation() {
        val previous = activeConversationId
        scope.launch {
            if (previous != null && store.messages(previous, 1).isEmpty()) {
                store.deleteConversation(previous)
            }
            activeConversationId = store.createConversation()
        }
    }

    LaunchedEffect(storageOpen, conversations.size) {
        if (storageOpen) stats = store.stats()
    }

    // 用量随回复条数变化刷新；只查一次聚合，不在 UI 层遍历消息
    LaunchedEffect(conversations.sumOf { it.messageCount }) {
        usage = store.usageSummary()
    }

    // 系统返回：先退浮层，再退页面，最后才退出应用。
    // 之前漏了服务商与存储这两层，用户在这些页按返回会直接退出应用。
    BackHandler(enabled = !isRootTab || activeConversationId != null || tab != 0) {
        when {
            runsFor != null -> runsFor = null

            editingNew || editingProviderId != null -> {
                editingNew = false
                editingProviderId = null
            }

            providerListOpen -> providerListOpen = false
            filesOpen -> filesOpen = false
            qqConfigOpen -> qqConfigOpen = false
            promptsOpen -> promptsOpen = false
            mcpOpen -> mcpOpen = false
            appearanceOpen -> appearanceOpen = false
            linuxOpen -> linuxOpen = false
            sshOpen -> sshOpen = false
            toolsOpen -> toolsOpen = false
            permissionsOpen -> permissionsOpen = false
            pluginsOpen -> pluginsOpen = false
            storageOpen -> storageOpen = false
            detail != null -> detail = null
            activeConversationId != null -> closeConversation()
            else -> tab = 0
        }
    }

    // 根底色必须跟随当前页面：否则状态栏那条裸露区域会一直是根容器的灰底，
    // 与白底的对话/消息页形成明显接缝。
    val pageBackground = when {
        runsFor != null -> colors.canvasAlt
        detail != null || storageOpen || pluginsOpen || permissionsOpen || toolsOpen ||
            filesOpen || qqConfigOpen || promptsOpen || mcpOpen || appearanceOpen || linuxOpen || sshOpen || providerListOpen ||
            editingNew || editingProviderId != null -> colors.background
        activeConversationId != null -> colors.canvasAlt
        tab == 0 -> colors.canvasAlt
        else -> colors.background
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .safeDrawingPadding(),
    ) {
        // 内容层：被 hazeSource 标记，底栏从它取像素做液态玻璃。
        // 这里**不再**加底部内边距：一旦内缩，内容就被裁在玻璃条上方，玻璃背后是纯色，
        // 看起来就是一块不透明白板。让内容真正从玻璃下面穿过，底部留白改由各页自己加
        // （VxCollapsingScaffold 的 bottomContentPadding / 列表的 contentPadding）。
        // 切标签时的入场：轻微上浮 + 淡入。小米设置的页面切换是"推入"而不是硬切，
        // 但整体位移不能大，否则每个 tab 都像换了一个 App。
        val tabEnter = remember { Animatable(1f) }
        LaunchedEffect(tab) {
            tabEnter.snapTo(0.76f)
            tabEnter.animateTo(1f, animationSpec = tween(220, easing = VxMotion.EaseOutCubic))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .graphicsLayer {
                    alpha = tabEnter.value
                    translationY = (1f - tabEnter.value) * 20.dp.toPx()
                },
        ) {
        // 推页容器：新页淡入右移、旧页压暗；返回时反向。页面内容是 Page 的纯函数。
        VxPageSwitcher(page = page, depthOf = { it.depth }) { p ->
            // 语言切换时整棵页面子树按 key 重建。
            //
            // tr() 的即时生效理论上靠各 VxText 对 VxL10n.isEnglish 的状态重组，但实测
            // 在推页容器 + strong skipping 的组合下，部分页面的重组没有传播到位：
            // 切了语言文案不刷新，动一下字号（走 VeluneTheme 的 CompositionLocal
            // 全树重组）才生效。语言是低频操作，直接按语言值重建页面子树——
            // 导航开关、tab、返回栈都在子树外，重建不丢任何位置状态。
            // key 参数读 isEnglish 同时把它注册到本 scope，保证语言一变这里必然重组。
            key(VxL10n.isEnglish) {
                when (p) {
                is Page.Runs -> {
                    AgentRunsScreen(
                        conversationId = p.conversationId,
                        runStore = app.agentRunStore,
                        onBack = { runsFor = null },
                    )
                }

        is Page.ProviderEdit -> {
                    val existing = providers.firstOrNull { it.id == p.providerId }
                    ProviderEditScreen(
                        existing = existing,
                        onSave = { name, baseUrl, model, apiKey ->
                            ProviderStore.save(editingProviderId, name, baseUrl, model, apiKey)
                            editingNew = false
                            editingProviderId = null
                        },
                        onBack = {
                            editingNew = false
                            editingProviderId = null
                        },
                    )
                }

        is Page.ProviderList -> ProviderListScreen(
                    providers = providers,
                    probeResults = probeResults,
                    activeId = providers.firstOrNull { it.id == activeId }?.id
                        ?: providers.firstOrNull()?.id,
                    onAdd = { editingNew = true },
                    onEdit = { provider -> editingProviderId = provider.id },
                    onProbe = { provider -> scope.launch { ProviderStore.probe(provider.id) } },
                    onSetActive = { provider -> ProviderStore.setActive(provider.id) },
                    onDelete = { provider -> ProviderStore.delete(provider.id) },
                    onBack = { providerListOpen = false },
                )

        is Page.Ssh -> com.veluneagent.feature.settings.SshScreen(
                    host = sshHost,
                    onSave = { com.veluneagent.core.data.ssh.Ssh.save(it) },
                    onClear = { com.veluneagent.core.data.ssh.Ssh.clear() },
                    onBack = { sshOpen = false },
                )

        is Page.QqBot -> com.veluneagent.feature.settings.QqBotScreen(
                    config = qqConfig,
                    conversations = conversations,
                    conversationsLoaded = conversationsLoaded,
                    autoConversationId = qqAutoConvId,
                    onSave = { com.veluneagent.core.data.im.QqBot.save(it) },
                    onClear = {
                        com.veluneagent.core.data.im.QqBot.clear()
                        QqBridge.stop(context)
                    },
                    onBack = { qqConfigOpen = false },
                    bridgeState = qqBridgeState,
                    bridgeMessage = qqBridgeMessage,
                    onStartBridge = {
                        // 前台服务的常驻通知在 Android 13+ 需要权限，缺失时通知不可见
                        if (!app.permissionRequester.isGranted(
                                android.Manifest.permission.POST_NOTIFICATIONS,
                            )
                        ) {
                            scope.launch {
                                app.permissionRequester.request(
                                    android.Manifest.permission.POST_NOTIFICATIONS,
                                )
                            }
                        }
                        QqBridge.start(context)
                    },
                    onStopBridge = { QqBridge.stop(context) },
                )

        is Page.Mcp -> com.veluneagent.feature.settings.McpScreen(
                    store = app.mcpStore,
                    registry = app.mcpRegistry,
                    onBack = { mcpOpen = false },
                )

        is Page.Prompts -> com.veluneagent.feature.settings.PromptScreen(
                    onBack = { promptsOpen = false },                )

        is Page.Appearance -> com.veluneagent.feature.settings.AppearanceScreen(
                    onBack = { appearanceOpen = false },
                )

        is Page.Linux -> com.veluneagent.feature.settings.LinuxEnvScreen(
                    onBack = { linuxOpen = false },
                )

        is Page.Files -> com.veluneagent.feature.chat.ui.FilesScreen(
                    sandbox = app.sandbox,
                    onBack = { filesOpen = false },
                )

        is Page.Tools -> com.veluneagent.feature.settings.ToolsScreen(
                    tools = app.toolCatalog.allSpecs(),
                    isEnabled = { name -> name !in disabledTools },
                    onToggle = { name, enabled ->
                        com.veluneagent.core.data.agent.ToolPrefs.setEnabled(name, enabled)
                    },
                    thinkingEnabled = thinkingEnabled,
                    onThinkingChange = { com.veluneagent.core.data.ChatPrefs.setThinkingEnabled(it) },
                    effort = reasoningEffort,
                    onEffortChange = { com.veluneagent.core.data.ChatPrefs.setReasoningEffort(it) },
                    thinkingPolicy = com.veluneagent.core.data.ThinkingSupport
                        .policyOf(activeProviderModel),
                    confirmSms = com.veluneagent.core.data.agent.ToolPrefs.confirmSms
                        .collectAsState().value,
                    onConfirmSmsChange = {
                        com.veluneagent.core.data.agent.ToolPrefs.setConfirmSms(it)
                    },
                    onBack = { toolsOpen = false },
                )

        is Page.Permissions -> com.veluneagent.feature.settings.PermissionScreen(
                    entries = remember(permissionTick) {
                        PermissionCatalog.runtime.map { item ->
                            com.veluneagent.feature.settings.PermissionEntry(
                                title = item.title,
                                why = item.why,
                                granted = app.permissionRequester.isGranted(item.permission),
                            )
                        }
                    },
                    onRequest = { index ->
                        val permission = PermissionCatalog.runtime[index].permission
                        scope.launch {
                            app.permissionRequester.request(permission)
                            permissionTick++
                        }
                    },
                    adminActive = remember(permissionTick) {
                        app.systemCapabilities.screen.isAdminActive()
                    },
                    onRequestAdmin = {
                        runCatching { context.startActivity(app.systemCapabilities.screen.requestAdminIntent()) }
                    },
                    onOpenSystemSettings = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        }
                    },
                    onBack = { permissionsOpen = false },
                )

        is Page.Plugins -> com.veluneagent.feature.settings.PluginsScreen(
                    plugins = installedPlugins,
                    message = pluginMessage,
                    messageIsError = !pluginMessageOk,
                    onInstallUrl = { url ->
                        scope.launch {
                            val outcome = com.veluneagent.core.data.plugin.PluginInstaller
                                .installFromUrl(pluginStore, url)
                            pluginMessage = outcome.message
                            pluginMessageOk = outcome.ok
                        }
                    },
                    onInstallClipboard = {
                        val clip = (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager)
                            .primaryClip
                        val text = clip?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)?.coerceToText(context)?.toString()
                        if (text.isNullOrBlank()) {
                            pluginMessage = tr("剪贴板为空，先复制插件清单 JSON")
                            pluginMessageOk = false
                        } else {
                            scope.launch {
                                val outcome = com.veluneagent.core.data.plugin.PluginInstaller
                                    .installFromJson(pluginStore, text)
                                pluginMessage = outcome.message
                                pluginMessageOk = outcome.ok
                            }
                        }
                    },
                    onInstallSample = {
                        scope.launch {
                            val outcome = com.veluneagent.core.data.plugin.PluginInstaller
                                .installSample(pluginStore)
                            pluginMessage = outcome.message
                            pluginMessageOk = outcome.ok
                        }
                    },
                    qqConfig = qqConfig,
                    onOpenQqBot = { qqConfigOpen = true },
                    onToggle = { plugin ->
                        scope.launch { pluginStore.setEnabled(plugin.id, !plugin.enabled) }
                    },
                    onUninstall = { plugin ->
                        scope.launch { pluginStore.uninstall(plugin.id) }
                    },
                    onBack = { pluginsOpen = false },
                )

        is Page.Storage -> StorageScreen(
                    stats = stats,
                    onClearAll = { confirmClear = true },
                    onBack = { storageOpen = false },
                    notice = storageNotice,
                    onExport = {
                        if (exporting) return@StorageScreen
                        exporting = true
                        scope.launch {
                            val result = runCatching {
                                val sessions = conversations.map { conversation ->
                                    com.veluneagent.core.data.export.ChatExporter.Session(
                                        title = conversation.title,
                                        createdAt = conversation.createdAt,
                                        messages = store.messages(conversation.id, Int.MAX_VALUE),
                                    )
                                }
                                if (sessions.isEmpty()) {
                                    return@runCatching null
                                }
                                val exportedAt = System.currentTimeMillis()
                                val text = com.veluneagent.core.data.export.ChatExporter
                                    .toMarkdown(sessions, exportedAt)
                                val path = com.veluneagent.core.data.export.ChatExporter
                                    .fileNameFor(exportedAt)
                                app.sandbox.write(path, text, false)
                                path to sessions.size
                            }
                            exporting = false
                            storageNotice = result.fold(
                                onSuccess = { value ->
                                    if (value == null) {
                                        tr("还没有会话可以导出")
                                    } else {
                                        trf("已导出 %d 个会话 → %s（可在「产出文件」里打开或分享）", value.second, value.first)
                                    }
                                },
                                onFailure = { trf("导出失败：%s", it.message ?: tr("无法写入沙箱")) },
                            )
                        }
                    },
                )

        is Page.Detail -> {
                    val route = p.route
                    when (route) {
                        // 关于与许可是定制版式（头图 / 章节卡 / 提示框），不再走通用正文页
                        aboutRoute -> com.veluneagent.feature.settings.AboutScreen(
                            version = BuildConfig.VERSION_NAME,
                            appIconRes = R.mipmap.ic_launcher,
                            onBack = { detail = null },
                            onFeedbackDoc = { openExternal(context, FEEDBACK_DOC_URL, tr("反馈文档地址未配置")) },
                            onJoinGroup = { openExternal(context, QQ_GROUP_URL, tr("群链接未配置")) },
                            onRepository = { openExternal(context, REPOSITORY_URL, tr("项目仓库地址尚未公布")) },
                            onDonate = { openExternal(context, DONATION_URL, tr("捐赠入口尚未开放")) },
                            onLicenses = { detail = licensesRoute },
                        )

                        licensesRoute -> com.veluneagent.feature.settings.LicensesScreen(
                            onBack = { detail = null },
                        )

                        else -> DetailScreen(
                            title = route.title,
                            sections = route.sections,
                            footer = route.footer,
                            onBack = { detail = null },
                        )
                    }
                }

        is Page.Chat -> {
                    // 必须按会话 id 隔离状态：ChatScreen 里的 streaming / job / streamParts
                    // 都是无 key 的 remember，切会话时组件树位置不变、状态会被复用。
                    // 表现为「A 正在输出时新建对话，B 的输入框变成停止按钮，发不出消息」，
                    // 点停止取消的还是 A 的请求。
                    val currentId = p.conversationId
                    key(currentId) {
                        ChatScreen(
                            conversationId = currentId,
                            title = conversations.firstOrNull { it.id == currentId }?.title ?: tr("新对话"),
                            store = store,
                            onBack = { closeConversation() },
                            agentDeps = agentDeps,
                            onOpenRuns = { runsFor = currentId },
                            onOpenModelSetup = {
                                closeConversation()
                                tab = 1
                            },
                        )
                    }
                }

        is Page.Tab -> when (p.tab) {
                    0 -> MessagesScreen(
                        conversations = conversations,
                        onOpenSession = { activeConversationId = it.id },
                        onCreateSession = { startNewConversation() },
                        prompts = prompts,
                        onCreateSessionWithRole = { promptId ->
                            scope.launch {
                                val conversationId = store.createConversation()
                                store.setConversationPrompt(conversationId, promptId)
                                activeConversationId = conversationId
                            }
                        },
                        sortMode = sortMode,
                        onSortChange = { com.veluneagent.core.data.ChatPrefs.setSortMode(it) },
                        onArchive = { conversation, archived ->
                            scope.launch {
                                store.setArchived(conversation.id, archived)
                                // 归档正在看的会话时退出它，否则用户会停在一个列表里找不到的会话上
                                if (archived && conversation.id == activeConversationId) {
                                    activeConversationId = null
                                }
                            }
                        },
                        onArchiveMany = { ids, archived ->
                            scope.launch { store.setArchived(ids, archived) }
                        },
                        onDeleteMany = { ids ->
                            scope.launch {
                                store.deleteConversations(ids)
                                if (activeConversationId?.let { ids.contains(it) } == true) {
                                    activeConversationId = null
                                }
                            }
                        },
                        loading = !conversationsLoaded,
                        onStartCharacterChat = { name, persona ->
                            scope.launch {
                                // 角色就是一条 chat 场景的提示词 + 一个绑定了它的会话，
                                // 这样角色库天然复用提示词页面，不需要第二套数据
                                val promptId = promptStore.create(
                                    scene = com.veluneagent.core.data.prompt.PromptScene.Chat,
                                    name = name,
                                    body = persona,
                                )
                                val conversationId = store.createConversation(name)
                                store.setConversationPrompt(conversationId, promptId)
                                activeConversationId = conversationId
                            }
                        },
                        onPin = { conversation ->
                            scope.launch {
                                store.setPinned(conversation.id, !conversation.pinned)
                            }
                        },
                        onDelete = { conversation ->
                            scope.launch {
                                store.deleteConversation(conversation.id)
                                if (conversation.id == activeConversationId) {
                                    activeConversationId = null
                                }
                            }
                        },
                        onRename = { conversation, title ->
                            scope.launch { store.renameConversation(conversation.id, title) }
                        },
                        onSearch = { keyword -> store.searchConversations(keyword) },
                    )

                    1 -> SettingsScreen(
                        providerSummary = when {
                            providers.isEmpty() -> tr("未配置 · 点此添加")
                            else -> {
                                val first = providers.first()
                                val model = first.model.ifBlank { first.name }
                                trf("%d 个服务商 · %s", providers.size, model)
                            }
                        },
                        storageSummary = storageLabel(),
                        version = BuildConfig.VERSION_NAME,
                        pluginCount = installedPlugins.size,
                        permissionSummary = remember(permissionTick) {
                            val granted = PermissionCatalog.runtime.count {
                                app.permissionRequester.isGranted(it.permission)
                            }
                            "$granted/${PermissionCatalog.runtime.size}"
                        },
                        onOpenPermissions = { permissionsOpen = true },
                        onOpenTools = { toolsOpen = true },
                        onOpenFiles = { filesOpen = true },
                        onOpenSsh = { sshOpen = true },
                        onOpenLinux = { linuxOpen = true },
                        linuxSummary = when (linuxState) {
                            com.veluneagent.core.data.linux.LinuxEnv.State.Ready -> tr("已就绪")
                            com.veluneagent.core.data.linux.LinuxEnv.State.Downloading -> tr("下载中")
                            com.veluneagent.core.data.linux.LinuxEnv.State.Extracting -> tr("解压中")
                            com.veluneagent.core.data.linux.LinuxEnv.State.Failed -> tr("安装失败")
                            else -> tr("未安装")
                        },
                        enabledToolCount = app.toolCatalog.specs().size,
                        agentEnabled = com.veluneagent.core.data.ChatPrefs.agentEnabled
                            .collectAsState().value,
                        onOpenModels = { providerListOpen = true },
                        onOpenPlugins = { pluginsOpen = true },
                        onOpenPrompts = { promptsOpen = true },
                        onOpenMcp = { mcpOpen = true },
                        mcpSummary = remember(mcpOpen) {
                            app.mcpStore.list().size.takeIf { it > 0 }?.let { trf("%d 个服务器", it) }
                        },
                        onOpenAppearance = { appearanceOpen = true },
                        appearanceSummary = buildString {
                            append(
                                when (themeMode) {
                                    com.veluneagent.core.data.profile.AppearancePrefs.ThemeMode.System -> tr("跟随系统")
                                    com.veluneagent.core.data.profile.AppearancePrefs.ThemeMode.Light -> tr("浅色")
                                    com.veluneagent.core.data.profile.AppearancePrefs.ThemeMode.Dark -> tr("深色")
                                },
                            )
                            val sizeLabel = com.veluneagent.core.data.profile.AppearancePrefs.FontScaleOptions
                                .firstOrNull { kotlin.math.abs(it.first - fontScale) < 0.01f }
                                ?.second
                            if (sizeLabel != null && sizeLabel != "标准") append(" · " + tr(sizeLabel))
                        },
                        onOpenAbout = { detail = aboutRoute },
                        onOpenStorage = { storageOpen = true },
                    )

                    else -> ProfileScreen(
                        userName = nickname.ifBlank { tr("本地用户") },
                        onEditName = { nameDialogOpen = true },
                        version = BuildConfig.VERSION_NAME,
                        stats = com.veluneagent.feature.settings.UsageStats(
                            sessions = conversations.size,
                            messages = conversations.sumOf { it.messageCount },
                            storage = storageLabel(),
                            totalTokens = usage.totalTokens,
                            todayTokens = usage.todayTokens,
                        ),
                        onOpenSupport = { detail = supportRoute },
                        onOpenAgreement = { detail = agreementRoute },
                        onOpenPrivacy = { detail = privacyRoute },
                        onOpenLicenses = { detail = licensesRoute },
                    )
                }
            }
            }
            }
        }

        // 只在三个主标签页显示。子页（服务商/插件/存储/权限/工具/详情/对话）都可能自带底部
        // 操作区或弹窗，浮一条 tab bar 上去就会盖住内容——截图里的重叠就是这么来的。
        if (isRootTab) {
            VxFloatingTabBar(
                tabs = tabs,
                hazeState = hazeState,
                selectedIndex = tab,
                // 切标签时把所有子页状态清干净：否则点了「设置」看到的还是上层的插件页
                onSelect = { index ->
                    clearSubPages()
                    tab = index
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        if (nameDialogOpen) {
            com.veluneagent.core.design.component.VxPromptSheet(
                visible = true,
                title = "你的昵称",
                initialValue = nickname,
                // 占位提示是给用户看的：写 {{user_name}} 这种内部占位符，
                // 用户既看不懂也不知道该输什么
                placeholder = "比如：小林",
                onConfirm = {
                    com.veluneagent.core.data.profile.UserProfile.setNickname(it)
                    nameDialogOpen = false
                },
                onDismiss = { nameDialogOpen = false },
            )
        }

        if (confirmClear) {
            VxActionSheet(
                visible = true,
                title = "清空全部对话",
                caption = trf("将删除 %d 个会话、%d 条消息，无法恢复。", stats.conversations, stats.messages),
                actions = listOf(
                    VxSheetAction(
                        label = "清空全部",
                        icon = VxIcon.Trash,
                        destructive = true,
                        onClick = {
                            confirmClear = false
                            scope.launch {
                                store.clearAllConversations()
                                stats = store.stats()
                            }
                        },
                    ),
                ),
                onDismiss = { confirmClear = false },
            )
        }
    }
}

/**
 * 首次启动的同意门。
 *
 * 只有「同意 / 不同意」两条路，不做「仅浏览」这类降级模式：
 * 本应用的全部功能都建立在处理你的数据之上，先跑起来再问同意没有意义。
 * 不同意就直接结束任务，而不是留一个半残的界面。
 */
@Composable
private fun ConsentGate() {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type
    val activity = androidx.activity.compose.LocalActivity.current
    var detail by remember { mutableStateOf<DetailRoute?>(null) }

    val current = detail
    if (current != null) {
        DetailScreen(
            title = current.title,
            sections = current.sections,
            footer = current.footer,
            onBack = { detail = null },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvasAlt)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        VxText(text = "Velune", style = type.title, color = colors.textPrimary)
        Spacer(modifier = Modifier.height(10.dp))
        VxText(
            text = "本地优先的 AI 对话工具，会话与密钥只保存在这台设备上。\n" +
                "开始前请阅读并同意以下条款。",
            style = type.footnote,
            color = colors.textSecondary,
        )

        Spacer(modifier = Modifier.height(24.dp))

        VxCard {
            VxRow(
                title = "用户协议",
                subtitle = "服务说明、使用规范、免责声明",
                onClick = { detail = agreementRoute },
            )
            VxRow(
                title = "隐私政策",
                subtitle = "收集范围、第三方共享、你的权利",
                onClick = { detail = privacyRoute },
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = { LegalConsent.accept() },
            colors = ButtonDefaults.buttonColorsPrimary(),
            minHeight = 46.dp,
            cornerRadius = 23.dp,
            insideMargin = PaddingValues(horizontal = 20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = tr("同意并继续"))
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            VxText(
                text = "不同意",
                style = type.footnote,
                color = colors.textTertiary,
                modifier = Modifier
                    .clickable { activity?.finish() }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }
}
