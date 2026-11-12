package com.veluneagent.core.design.qr

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * 二维码渲染。
 *
 * **永远白底黑码，不跟随主题**：深色模式下做成反色（黑底白码）时，手机相机的识别率极差，
 * 部分实现（尤其系统相机直出的扫一扫）会直接扫不出来——而扫码绑定是唯一「不用手抄密钥」的入口，
 * 扫不出来等于这个功能不存在。所以这里强制白底 + 12dp 静区，**不接** MiuixTheme / VeluneTheme 的颜色。
 *
 * 这是全仓库唯一允许硬编码白色的地方。理由写在这里，避免下一个人"顺手适配深色"把它改坏：
 * 二维码是**给相机看的图**，不是 UI 的一部分 —— 它不该跟随用户的主题设置。
 *
 * 编码失败（内容过长/含非法字符）时**什么都不画**，由调用方用文案兜底：
 * 比起画一个扫不开的假二维码，缺图更容易被发现。
 */
@Composable
fun QrImage(
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
) {
    // 位图按 3 倍像素生成再缩小显示：高 DPI 屏上直接按 dp 尺寸生成会发虚，
    // 而二维码一旦发虚（模块边缘模糊）相机就很难对齐
    val bitmap = remember(content, size) {
        runCatching { qrBitmap(content, (size.value * 3).toInt()) }.getOrNull()
    } ?: return

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "扫码绑定二维码",
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(12.dp)
            .size(size),
    )
}

/**
 * 把 [content] 编码成方形二维码位图。
 *
 * - `CHARACTER_SET = UTF-8`：绑定链接里带 `&`、`?` 等 ASCII，但将来若带中文也必须能编；
 * - `MARGIN = 1`：最小静区，剩下的静区由 Compose 侧的 12dp 白边提供（那层是 dp，能跟着屏幕缩放）；
 * - `ERROR_CORRECTION = M`：绑定链接约 80~120 字节，M 级（15%）在"扫码时手抖/反光"与"模块不过密"之间最平衡。
 */
internal fun qrBitmap(content: String, sizePx: Int): Bitmap {
    val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.MARGIN to 1,
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val width = matrix.width
    val pixels = IntArray(width * matrix.height) { i ->
        if (matrix[i % width, i / width]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
    return Bitmap.createBitmap(pixels, width, matrix.height, Bitmap.Config.ARGB_8888)
}
