package com.veluneagent.core.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 字阶（对齐 HyperOS 真机观察值）。
 * MiSans 未随包分发时回退系统字体，字形差异可接受，行高与字重必须一致。
 */
@Immutable
data class VeluneTypography(
    val largeTitle: TextStyle,
    val largeTitleCollapsed: TextStyle,
    val centerTitle: TextStyle,
    val title: TextStyle,
    val rowTitle: TextStyle,
    val body: TextStyle,
    val value: TextStyle,
    val footnote: TextStyle,
    val sectionLabel: TextStyle,
    val mono: TextStyle,
)

private val Sans = FontFamily.Default
private val Mono = FontFamily.Monospace

val VeluneType = VeluneTypography(
    largeTitle = TextStyle(
        // 26sp Medium：小米设置大标题实测（字高 76px ÷ 3.196 = 23.8dp，对应 26sp / title1_medium）。
        // 原来是 30sp Bold —— 比真机大一档、重两档，蓝色 Medium 才是它的观感。
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.2).sp,
    ),
    largeTitleCollapsed = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 24.sp,
    ),
    centerTitle = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 24.sp,
    ),
    title = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 28.sp,
    ),
    rowTitle = TextStyle(
        // 17sp Medium：小米设置行标题实测（字高约 50px ÷ 3.196 = 15.6dp，对应 17sp）。
        // 原来是 16sp SemiBold —— 字号小一档、字重重一档，与 14sp 正文拉不开层级。
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 17.sp, lineHeight = 22.sp,
    ),
    body = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 25.sp,
    ),
    value = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 19.sp,
    ),
    footnote = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 19.sp,
    ),
    sectionLabel = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.sp,
    ),
    mono = TextStyle(
        fontFamily = Mono, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 22.sp,
    ),
)
