package com.veluneagent.core.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 通用凭据加密（Android Keystore + AES/GCM）。
 *
 * 与 ProviderStore 里的服务商 Key 分开一个 alias：两类凭据的生命周期不同
 * （清空服务商配置不该把 QQ 令牌一起清掉），共用一个 key 会让「删除」变得含糊。
 *
 * 密文格式：`base64(iv):base64(ciphertext)`，IV 随密文一起存，
 * 不需要额外字段，也不会因为 IV 复用而破坏 GCM 的安全性。
 */
object SecretBox {

    private const val KEYSTORE = "AndroidKeyStore"

    /** internal：测试要按这个 alias 预置密钥才能绕开 Robolectric 缺失的 AndroidKeyStore。 */
    internal const val ALIAS = "velune_secret_box_v1"

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(body, Base64.NO_WRAP)
    }

    fun decrypt(payload: String): String {
        val parts = payload.split(":")
        require(parts.size == 2) { "密文格式不正确" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val body = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(body), Charsets.UTF_8)
    }

    /** 是否已存在密钥：用于区分「没配过」和「密钥被系统清掉了」。 */
    fun hasKey(): Boolean = runCatching {
        KeyStore.getInstance(KEYSTORE).apply { load(null) }.containsAlias(ALIAS)
    }.getOrDefault(false)

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
