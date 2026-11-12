package com.veluneagent.core.data.im

import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStore
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.util.Collections
import java.util.Date
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * **测试专用**：给 JVM 注册一个名为 `AndroidKeyStore` 的假 JCE Provider。
 *
 * ## 为什么需要它
 *
 * `ProviderStore.save(apiKey = …)` → `ProviderStore.secretKey()` →
 * `KeyStore.getInstance("AndroidKeyStore")`。真实 Android 上这是系统 Provider，
 * 而 **Robolectric 没有影子实现**（`shadows-framework-4.14.1.jar` 里搜 `AndroidKeyStore` 零命中），
 * 于是 `KeyStore.getInstance` 抛 `KeyStoreException: AndroidKeyStore not found`。
 *
 * 后果：测试里配不出「有 Key 的服务商」→ `ProviderStore.apiKeyOf()` 恒为 null →
 * `QqResponder.handle()` 在 `服务商缺少 API Key` 这一步就返回，**永远走不到被测的主链路**。
 *
 * ## 为什么是「预置密钥」而不是「提供 KeyGenerator」
 *
 * 第一版注册了 `KeyGenerator.AES` 服务，结果 JVM 拒绝：
 * `NoSuchProviderException: JCE cannot authenticate the provider AndroidKeyStore`
 * —— JCE 只接受**签名过**的 Provider 提供 `KeyGenerator` / `Cipher` 这类 JCE 服务，
 * 动态 `addProvider` 的未签名实现一律被挡。
 *
 * 但 `KeyStore` **不是** JCE 服务，不校验签名；而 `secretKey()` 的第一句就是
 * `keyStore.getEntry(alias, null)`，命中即返回、**根本走不到 `KeyGenerator`**。
 * 所以这里只提供 KeyStore，并在两个已知 alias 上预置 256 位 AES 密钥。
 *
 * 加密算法本身仍走 JDK 的 `SunJCE`（`Cipher.getInstance("AES/GCM/NoPadding")` 不带 Provider），
 * 本 Provider **不提供任何 Cipher**，不会影响其它测试的加密行为。
 *
 * ## 边界
 *
 * 这是测试基础设施，不是「实现替身」：它只让真实 KeyStore 调用能跑通，
 * 不改变被测代码的任何分支。生产的密钥仍然只存在 Android Keystore 里（不可导出）。
 */
internal object FakeAndroidKeyStore {

    const val ProviderName = "AndroidKeyStore"

    /**
     * 被测代码用到的两个 alias。
     *
     * - `velune_provider_master`：`ProviderStore.KEY_ALIAS`（服务商 API Key）
     * - `velune_secret_box_v1`：`SecretBox.ALIAS`（QQ 令牌等）
     *
     * 这两个是私有常量，测试里只能按字面量复制；一旦源码改名，这里会立刻抛出
     * 「服务商缺少 API Key」让测试变红 —— 比静默失效好。
     */
    private val SeededAliases = listOf("velune_provider_master", "velune_secret_box_v1")

    private var installed = false

    /** 幂等安装（`Security` 是 JVM 全局的）+ 预置密钥。 */
    fun install() {
        if (!installed) {
            if (Security.getProvider(ProviderName) == null) {
                Security.addProvider(FakeProvider())
            }
            installed = true
        }
        seedKeys()
    }

    /** 每个用例开头调用：丢掉上一轮生成的密钥与密文，避免互相污染。 */
    fun reset() {
        InMemoryKeyStoreSpi.keys.clear()
        seedKeys()
    }

    private fun seedKeys() {
        SeededAliases.forEach { alias ->
            if (!InMemoryKeyStoreSpi.keys.containsKey(alias)) {
                val bytes = ByteArray(32) // AES-256
                SecureRandom().nextBytes(bytes)
                InMemoryKeyStoreSpi.keys[alias] = SecretKeySpec(bytes, "AES")
            }
        }
    }

    class FakeProvider : Provider(ProviderName, 1.0, "Test-only AndroidKeyStore for Robolectric") {
        init {
            // 只注册 KeyStore：KeyGenerator 是 JCE 服务，未签名 Provider 会被 JceSecurity 拒绝，
            // 而 secretKey() 在 getEntry 命中时根本不会用到它。
            put("KeyStore.AndroidKeyStore", InMemoryKeyStoreSpi::class.java.name)
        }
    }

    class InMemoryKeyStoreSpi : KeyStoreSpi() {

        companion object {
            val keys: MutableMap<String, Key> = mutableMapOf()
        }

        override fun engineGetKey(alias: String?, password: CharArray?): Key? = keys[alias]

        override fun engineGetEntry(
            alias: String?,
            protParam: KeyStore.ProtectionParameter?,
        ): KeyStore.Entry? {
            val key = keys[alias] as? SecretKey ?: return null
            return KeyStore.SecretKeyEntry(key)
        }

        override fun engineSetEntry(
            alias: String?,
            entry: KeyStore.Entry?,
            protParam: KeyStore.ProtectionParameter?,
        ) {
            if (alias != null && entry is KeyStore.SecretKeyEntry) keys[alias] = entry.secretKey
        }

        override fun engineGetCertificateChain(alias: String?): Array<Certificate>? = null

        override fun engineGetCertificate(alias: String?): Certificate? = null

        override fun engineGetCreationDate(alias: String?): Date = Date(0L)

        override fun engineSetKeyEntry(
            alias: String?,
            key: Key?,
            password: CharArray?,
            chain: Array<out Certificate>?,
        ) {
            if (alias != null && key != null) keys[alias] = key
        }

        override fun engineSetKeyEntry(
            alias: String?,
            key: ByteArray?,
            chain: Array<out Certificate>?,
        ) = Unit

        override fun engineDeleteEntry(alias: String?) {
            keys.remove(alias)
        }

        override fun engineAliases(): java.util.Enumeration<String> =
            Collections.enumeration(keys.keys.toList())

        override fun engineContainsAlias(alias: String?): Boolean = keys.containsKey(alias)

        override fun engineSize(): Int = keys.size

        override fun engineIsKeyEntry(alias: String?): Boolean = keys.containsKey(alias)

        override fun engineIsCertificateEntry(alias: String?): Boolean = false

        override fun engineGetCertificateAlias(cert: Certificate?): String? = null

        override fun engineSetCertificateEntry(alias: String?, cert: Certificate?) = Unit

        override fun engineStore(stream: OutputStream?, password: CharArray?) = Unit

        override fun engineLoad(stream: InputStream?, password: CharArray?) = Unit
    }
}
