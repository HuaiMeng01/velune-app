package com.veluneagent.core.design.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.Switch

/**
 * 开关 —— 转交 MIUIX [Switch]。
 *
 * 自绘版本的轨道高 26dp、无拖拽、无触感；MIUIX 版有拖拽跟手、边缘拉伸与触感反馈。
 * 这类"手感"是 HyperOS 观感的一半，靠截图是抄不出来的。
 */
@Composable
fun VxSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = { onCheckedChange(it) },
        modifier = modifier,
        enabled = enabled,
    )
}
