package com.veluneagent.core.design.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.veluneagent.core.design.theme.VeluneTheme

/**
 * 行式表单字段：左侧固定宽度标签，右侧可编辑值，占位文案在值为空时显示。
 *
 * 这是 MIUIX 设置页的形态（左侧标签、右侧值、整行可点）。项目里原来有三套做法：
 * 服务商页自绘 BasicTextField、SSH 页与 QQ 页用填充式 TextField、角色弹窗又是另一套，
 * 导致「同样是填一个地址」在三处的视觉与点击行为都不一样。
 *
 * [trailing] 放「显示/隐藏密钥」这类行内操作；不要放主要按钮，那会让一行变得拥挤。
 */
@Composable
fun VxFormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = VeluneTheme.colors
    val type = VeluneTheme.type

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VxText(
            text = label,
            style = type.rowTitle,
            color = colors.textPrimary,
            modifier = Modifier.width(84.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            // 占位文案只在空值时出现：BasicTextField 本身没有 placeholder 概念，
            // 用一层叠放的 VxText 实现，比引入 material 的 TextField 更可控
            if (value.isEmpty()) {
                VxText(text = placeholder, style = type.value, color = colors.textTertiary)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = type.value.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                visualTransformation = visualTransformation,
            )
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(6.dp))
            trailing()
        }
    }
}
