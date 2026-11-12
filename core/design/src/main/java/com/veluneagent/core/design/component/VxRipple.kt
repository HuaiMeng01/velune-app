package com.veluneagent.core.design.component

import androidx.compose.foundation.Indication
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 统一涟漪。页面层不直接依赖 material3，因此由设计系统导出。
 * 默认涟漪在深色卡片上过亮，这里统一压到 10% 左右。
 */
@Composable
fun vxRipple(
    color: Color,
    bounded: Boolean = true,
): Indication = ripple(color = color, bounded = bounded)
