package com.veluneagent.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 用户协议与隐私政策的同意状态。
 *
 * 为什么必须有这一步：条款只做成设置里的静态页，等于「用户从没同意过」——
 * 既是合规缺口（《个人信息保护法》要求处理前取得同意），也是应用商店审核的硬性门槛。
 *
 * 版本号参与判断：条款有实质修改时把 [CurrentVersion] +1，老用户会被要求重新同意一次，
 * 而不是让旧同意自动覆盖新条款。
 */
object LegalConsent {

    private const val PREFS = "velune_legal"
    private const val KEY_ACCEPTED = "accepted_version"

    /** 条款版本。改动协议或隐私政策的实质内容时 +1。 */
    const val CurrentVersion = 2

    private val _accepted = MutableStateFlow(false)

    /** 是否已同意当前版本的条款。 */
    val accepted: StateFlow<Boolean> = _accepted

    private var appContext: Context? = null

    fun init(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        val stored = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_ACCEPTED, 0)
        _accepted.value = stored >= CurrentVersion
    }

    fun accept() {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_ACCEPTED, CurrentVersion)
            .apply()
        _accepted.value = true
    }

    /** 仅用于调试/重置；正式流程里不会调用。 */
    fun revoke() {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ACCEPTED)
            .apply()
        _accepted.value = false
    }
}
