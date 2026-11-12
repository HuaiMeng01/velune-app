package com.veluneagent.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veluneagent.core.design.component.VxSubPageScaffold
import com.veluneagent.core.design.component.VxText
import com.veluneagent.core.design.theme.VeluneTheme
import com.veluneagent.core.design.theme.VxSpace

/**
 * 通用文本详情页（用户协议 / 隐私政策 / 开源许可 / 支持我们）。
 *
 * 结构对齐小米设置的次级页：56dp 居中标题栏 + 单栏正文。正文 16sp、行高 26sp，
 * 章节标题 17sp Medium，段落间距 12dp。
 *
 * 章节标题不用 `VxSectionLabel`（14sp 灰蓝分组标签）——放在法律文本里层级不够，
 * 读起来像目录而不是标题。
 */
@Composable
fun DetailScreen(
    title: String,
    sections: List<Pair<String, String>>,
    onBack: () -> Unit,
    footer: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type

    VxSubPageScaffold(title = title, onBack = onBack, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = VxSpace.subPageInset),
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            sections.forEach { (heading, body) ->
                if (heading.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    VxText(
                        text = heading,
                        style = type.rowTitle,
                        color = colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    VxText(
                        text = body,
                        style = type.body.copy(lineHeight = 26.sp),
                        color = colors.textPrimary,
                    )
                } else {
                    // 无标题段落是引言/摘要，压一档灰，与正文区分
                    VxText(
                        text = body,
                        style = type.body.copy(lineHeight = 26.sp),
                        color = colors.textSecondary,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (footer != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.75.dp)
                        .background(colors.divider),
                )
                Spacer(modifier = Modifier.height(10.dp))
                VxText(text = footer, style = type.footnote, color = colors.textTertiary)
            }

            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

@Preview(widthDp = 400, heightDp = 800, showBackground = true)
@Composable
private fun DetailPreview() {
    VeluneTheme(darkTheme = false) {
        DetailScreen(
            title = "用户协议",
            sections = listOf(
                "" to "欢迎使用 Velune。本应用是一款本地优先的 AI 对话工具，使用前请阅读以下条款。",
                "一、服务说明" to "本应用不提供模型服务，所有模型调用均由你自行配置的服务商完成，费用与额度由服务商结算。",
                "二、你的数据" to "会话、插件与密钥保存在你的设备上。除你主动发起的模型请求外，应用不会上传任何数据。",
                "三、使用规范" to "请勿使用本应用生成违反法律法规的内容。由你生成与传播的内容，责任由你承担。",
            ),
            onBack = {},
            footer = "最后更新：2026-10-18",
        )
    }
}
