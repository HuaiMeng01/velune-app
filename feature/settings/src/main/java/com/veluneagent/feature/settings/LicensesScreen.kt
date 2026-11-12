package com.veluneagent.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veluneagent.core.design.component.VxCard
import com.veluneagent.core.design.component.VxSubPageScaffold
import com.veluneagent.core.design.component.VxText
import com.veluneagent.core.design.theme.VeluneTheme
import com.veluneagent.core.design.theme.VxSpace

/**
 * 开源许可。
 *
 * 版式：大标题 → 项目名 → 白卡分组（章节标题 + 正文 + 强调提示框 + 圆点条目列表）。
 * 分组按「核心框架 / 设计与交互 / 功能组件 / 字体」。
 *
 * 写作口径：这里的每句话都是给**用户**看的——说明用了什么、以什么许可、
 * 到哪看全文。不要再把开发过程中的合规讨论（"若需闭源应替换该实现"之类）
 * 写进来，那些属于 `docs/`。
 */
@Composable
fun LicensesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type

    VxSubPageScaffold(title = "", onBack = onBack, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = VxSpace.subPageInset),
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            VxText(text = "开源许可", style = type.largeTitle, color = colors.textPrimary)
            Spacer(modifier = Modifier.height(12.dp))

            VxText(
                text = "Velune 使用的开源项目",
                style = type.rowTitle,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            VxText(
                text = "以下项目以各自的许可分发，许可全文可在对应项目主页查阅。",
                style = type.footnote,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(12.dp))

            LicenseSection(
                heading = "一、核心框架",
                body = "界面与运行时建立在 Kotlin 与 Jetpack Compose 之上，网络请求由 OkHttp 完成。",
                note = "这些项目均以 Apache-2.0 授权：允许自由使用、修改与分发，分发时需保留版权与许可声明。",
                items = listOf(
                    "Kotlin · JetBrains" to "Apache-2.0",
                    "Jetpack Compose · Google" to "Apache-2.0",
                    "AndroidX · Google" to "Apache-2.0",
                    "Kotlinx Coroutines · JetBrains" to "Apache-2.0",
                    "OkHttp · Square" to "Apache-2.0",
                ),
            )

            Spacer(modifier = Modifier.height(12.dp))

            LicenseSection(
                heading = "二、设计与交互",
                body = "界面的度量与动效参考 HyperOS 的设计语言，实现为本项目自绘，未使用小米的私有资源。",
                note = "「关于」页的动态底色移植自 Nexio 课程表的 AGSL 着色器（AGPL-3.0），"
                    + "配色与绘制参数在移植时重新测定。该组件的源码与许可见其项目主页。",
                items = listOf(
                    "MIUIX-Compose（miuix.kmp）· yukonga" to "Apache-2.0",
                    "Haze（背景模糊）· Chris Banes" to "Apache-2.0",
                    "Nexio 课程表（动态底色着色器）· HaoZai000" to "AGPL-3.0",
                    "Capsule（连续曲率圆角）· Kyant0" to "Apache-2.0",
                ),
            )

            Spacer(modifier = Modifier.height(12.dp))

            LicenseSection(
                heading = "三、功能组件",
                body = "远程服务器能力基于 JSch 实现 SSH 连接；扫码绑定用 ZXing 生成二维码；"
                    + "Linux 环境基于 proot 在用户空间运行 Ubuntu 文件系统。",
                note = "JSch 沿用上游 JCraft 的 BSD 风格许可；ZXing 以 Apache-2.0 授权；"
                    + "proot 以 GPL 授权，随应用分发的二进制对应其公开源码。",
                items = listOf(
                    "JSch · JCraft / mwiede" to "BSD 风格",
                    "ZXing · ZXing 项目组" to "Apache-2.0",
                    "proot · proot 作者及贡献者" to "GPL",
                    "libtalloc · Samba 项目" to "LGPL-3.0",
                ),
            )

            Spacer(modifier = Modifier.height(12.dp))

            LicenseSection(
                heading = "四、字体",
                body = "界面文字使用系统字体渲染；在 HyperOS 设备上，系统字体即为 MiSans，"
                    + "中文会以它显示，无需本应用做任何处理。",
                note = "MiSans 允许免费商用，但字体文件不可修改、不可单独分发。本应用不打包字体文件，"
                    + "MiSans 的授权条款由小米提供。",
                items = listOf(
                    "MiSans · 小米科技" to "免费商用（不随包分发）",
                ),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 本应用自身的授权：与仓库根目录 LICENSE 一致
            VxText(
                text = "Velune 自身以 GNU AGPL-3.0 授权开源，源码见项目仓库。",
                style = type.footnote,
                color = colors.textSecondary,
            )

            Spacer(modifier = Modifier.height(12.dp))

            VxText(
                text = "如果你认为某处使用超出了上述许可范围，请通过「交流与反馈」联系，我们会尽快处理。",
                style = type.footnote,
                color = colors.textTertiary,
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LicenseSection(
    heading: String,
    body: String,
    note: String,
    items: List<Pair<String, String>>,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type

    VxCard {
        Column(modifier = Modifier.padding(16.dp)) {
            VxText(text = heading, style = type.rowTitle, color = colors.textPrimary)
            Spacer(modifier = Modifier.height(10.dp))
            VxText(
                text = body,
                style = type.value.copy(lineHeight = 24.sp),
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 提示框：浅紫底（深色模式自动换成低透明度的强调色）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.accent.copy(alpha = if (colors.isLight) 0.06f else 0.12f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                VxText(
                    text = note,
                    style = type.value.copy(lineHeight = 22.sp),
                    color = colors.textSecondary,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            items.forEach { (name, license) ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 7.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(colors.accent),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        VxText(
                            text = name,
                            style = type.value,
                            color = colors.textPrimary,
                        )
                        VxText(
                            text = license,
                            style = type.footnote,
                            color = colors.textTertiary,
                        )
                    }
                }
            }
        }
    }
}
