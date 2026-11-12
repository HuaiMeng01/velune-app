package com.veluneagent.core.data.im

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 扫码绑定的解密路径（唯一能脱离真机验证的部分）。
 *
 * 为什么必须有固定向量测试：`[12B IV][ct][16B tag]` 这个布局一旦读错，
 * AES-GCM **不会报错**，而是解出一段乱码——表现就是"绑定成功了，但凭证是错的、
 * 一调接口就鉴权失败"，排查起来极难。这里用固定 key/IV 正向验证布局，
 * 再用错 key 反向验证「失败必须抛，不能返回乱码」。
 *
 * 网络路径（createTask / poll）不在这里测：离线环境没有 MockWebServer，
 * 而且这两个方法只是薄薄一层 HTTP + JSON 取值。
 */
class QqBindTest {

    private val key = ByteArray(32) { it.toByte() }
    private val iv = ByteArray(12) { (0xA0 + it).toByte() }

    /** 模拟服务端：AES-256-GCM 加密后按 `IV || ciphertext(+tag)` 拼成 base64。 */
    private fun encryptLikeServer(plain: String, keyBytes: ByteArray = key): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(keyBytes, "AES"),
            GCMParameterSpec(128, iv),
        )
        // javax 的 doFinal 输出已经把 16 字节 tag 追加在密文尾部
        val ctAndTag = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val payload = ByteArray(iv.size + ctAndTag.size)
        iv.copyInto(payload, 0)
        ctAndTag.copyInto(payload, iv.size)
        return Base64.getEncoder().encodeToString(payload)
    }

    @Test
    fun `固定向量能解出 AppSecret`() {
        val secret = "s3cr3t-AppSecret_0123456789"
        val b64 = encryptLikeServer(secret)
        // 长度自检：12 IV + 密文 + 16 tag（GCM 密文与明文等长）
        assertEquals(
            12 + secret.toByteArray(Charsets.UTF_8).size + 16,
            Base64.getDecoder().decode(b64).size,
        )
        val result = QqBind.decryptSecret(b64, key)
        assertTrue(result.isSuccess)
        assertEquals(secret, result.getOrNull())
    }

    @Test
    fun `中文与 emoji 也按 UTF8 还原`() {
        val secret = "密钥：中文😀"
        assertEquals(secret, QqBind.decryptSecret(encryptLikeServer(secret), key).getOrNull())
    }

    @Test
    fun `错 key 必须失败而不是返回乱码`() {
        val b64 = encryptLikeServer("another-secret")
        val wrongKey = key.copyOf().also { it[0] = (it[0].toInt() xor 0xFF).toByte() }
        val result = QqBind.decryptSecret(b64, wrongKey)
        // GCM 的 tag 校验会抛 AEADBadTagException：这里必须 isFailure，绝不能"成功返回一段乱码"
        assertTrue("错 key 必须失败", result.isFailure)
        assertFalse("不能返回任何明文", result.getOrNull() != null)
    }

    @Test
    fun `短密文与非法 key 一律失败`() {
        // 只有 10 字节，连 12B IV 都不够
        val tooShort = Base64.getEncoder().encodeToString(ByteArray(10))
        assertTrue(QqBind.decryptSecret(tooShort, key).isFailure)
        // key 长度不是 32
        val good = encryptLikeServer("x")
        assertTrue(QqBind.decryptSecret(good, ByteArray(16)).isFailure)
        // 不是 base64
        assertTrue(QqBind.decryptSecret("这不是 base64!!", key).isFailure)
    }

    @Test
    fun `二维码内容按官方格式拼接`() {
        assertEquals(
            "https://q.qq.com/qqbot/openclaw/connect.html?task_id=TASK123&source=velune&_wv=2",
            QqBind.qrContentOf("TASK123"),
        )
    }
}
