package com.veluneagent.core.data.agent

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 工具开关。
 *
 * 「开放工具」不应该是全有或全无：用户可能只想让 Agent 管日历、不想让它写文件。
 * 停用的工具**不会出现在模型的 tools 数组里**（不是调用时才拒绝），
 * 这样模型根本不知道它存在，省 token 也少一层误调用。
 */
object ToolPrefs {

    private const val PREFS = "velune_tools"
    private const val KEY_DISABLED = "disabled"
    private const val KEY_CONFIRM_SMS = "confirm_sms"

    private var appContext: Context? = null

    private val _disabled = MutableStateFlow<Set<String>>(emptySet())
    val disabled: StateFlow<Set<String>> = _disabled

    /**
     * 读短信前是否每次都要确认。默认开。
     *
     * 短信里可能有验证码、银行通知这类「看一眼就被记住」的信息，
     * 默认让用户看见每一次读取，比事后在日志里发现要好。
     */
    private val _confirmSms = MutableStateFlow(true)
    val confirmSms: StateFlow<Boolean> = _confirmSms

    fun init(context: Context) {
        if (appContext != null) return
        val ctx = context.applicationContext
        appContext = ctx
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_DISABLED, emptySet())
            .orEmpty()
        _disabled.value = raw.toSet()
        _confirmSms.value = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_CONFIRM_SMS, true)
    }

    fun setConfirmSms(value: Boolean) {
        _confirmSms.value = value
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY_CONFIRM_SMS, value)?.apply()
    }

    fun setEnabled(name: String, enabled: Boolean) {
        val next = if (enabled) _disabled.value - name else _disabled.value + name
        _disabled.value = next
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putStringSet(KEY_DISABLED, next)?.apply()
    }

    fun isEnabled(name: String): Boolean = name !in _disabled.value

    /** 工具级审批：读短信按用户开关决定，其余由 BuiltinTools 的静态清单决定。 */
    fun requiresApproval(name: String): Boolean =
        name == BuiltinTools.NameReadSms && _confirmSms.value
}
