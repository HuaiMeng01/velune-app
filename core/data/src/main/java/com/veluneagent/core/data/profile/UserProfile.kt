package com.veluneagent.core.data.profile

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 用户资料（目前只有昵称）。
 *
 * 为什么单独做成一层：提示词里的 `{{user_name}}` 需要一个稳定的数据源。
 * 把昵称塞进 ChatPrefs 也能跑，但"人的资料"和"对话开关"混在一个类里，
 * 后面再加头像/称呼偏好就会失控。
 *
 * 只存本地：昵称属于个人信息，不做任何联网上报。
 */
object UserProfile {

    private const val PREFS = "velune_profile"
    private const val KEY_NICKNAME = "nickname"

    private var prefs: android.content.SharedPreferences? = null

    private val _nickname = MutableStateFlow("")
    val nickname: StateFlow<String> = _nickname

    fun init(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        _nickname.value = store.getString(KEY_NICKNAME, "").orEmpty()
    }

    fun setNickname(value: String) {
        val normalized = value.trim().take(24)
        _nickname.value = normalized
        prefs?.edit()?.putString(KEY_NICKNAME, normalized)?.apply()
    }

    /** 自动标题/开场白里需要的称呼；没设昵称时给一个中性称呼，不要空字符串。 */
    fun displayName(fallback: String = "你"): String =
        _nickname.value.ifBlank { fallback }
}
