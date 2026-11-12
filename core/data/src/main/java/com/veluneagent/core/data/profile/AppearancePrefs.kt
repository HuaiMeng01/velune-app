package com.veluneagent.core.data.profile

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 外观偏好：主题模式 + 字号缩放。
 *
 * 字号用「倍率」而不是绝对 sp：Compose 里可以通过覆写 `LocalDensity.fontScale`
 * 让**所有** sp 一起缩放（包括 MIUIX 控件的字阶），不需要给两套字阶各写一遍缩放逻辑。
 *
 * 只影响本应用，不写系统设置——改系统字号是全局副作用，不该由一个 App 偷偷做。
 */
object AppearancePrefs {

    private const val PREFS = "velune_appearance"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_FONT_SCALE = "font_scale"
    private const val KEY_AUTO_DARK = "auto_dark"
    private const val KEY_MONET = "monet_color"
    private const val KEY_LANGUAGE = "language"

    enum class ThemeMode(val key: String, val label: String) {
        System("system", "跟随系统"),
        Light("light", "浅色"),
        Dark("dark", "深色"),
        ;

        companion object {
            fun of(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: System
        }
    }

    /** 界面语言。跟随系统时不覆盖 Locale。 */
    enum class Language(val key: String, val label: String, val tag: String?) {
        System("system", "跟随系统", null),
        Chinese("zh-Hans", "简体中文", "zh-Hans"),
        English("en", "English", "en"),
        ;

        companion object {
            fun of(key: String?): Language = entries.firstOrNull { it.key == key } ?: System
        }
    }

    /** 四档：小 / 标准 / 大 / 特大。倍率写死，避免用户拖出一个"半档"的诡异排版。 */
    val FontScaleOptions = listOf(0.9f to "小", 1.0f to "标准", 1.15f to "大", 1.3f to "特大")

    private var prefs: android.content.SharedPreferences? = null

    private val _themeMode = MutableStateFlow(ThemeMode.System)
    val themeMode: StateFlow<ThemeMode> = _themeMode

    private val _fontScale = MutableStateFlow(1.0f)
    val fontScale: StateFlow<Float> = _fontScale

    /** 自动切换深色模式（跟随系统）。打开时以系统当前模式为准，卡片仍可点，点了会自动关掉它。 */
    private val _autoDark = MutableStateFlow(false)
    val autoDark: StateFlow<Boolean> = _autoDark

    /** 莫奈取色：从系统壁纸提取主色，覆盖品牌强调色。 */
    private val _monetColor = MutableStateFlow(false)
    val monetColor: StateFlow<Boolean> = _monetColor

    private val _language = MutableStateFlow(Language.System)
    val language: StateFlow<Language> = _language

    fun init(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        _themeMode.value = ThemeMode.of(store.getString(KEY_THEME, null))
        _fontScale.value = store.getFloat(KEY_FONT_SCALE, 1.0f).coerceIn(0.85f, 1.35f)
        _autoDark.value = store.getBoolean(KEY_AUTO_DARK, false)
        _monetColor.value = store.getBoolean(KEY_MONET, false)
        _language.value = Language.of(store.getString(KEY_LANGUAGE, null))
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs?.edit()?.putString(KEY_THEME, mode.key)?.apply()
    }

    fun setAutoDark(enabled: Boolean) {
        _autoDark.value = enabled
        prefs?.edit()?.putBoolean(KEY_AUTO_DARK, enabled)?.apply()
        // 打开自动切换时把显式选择收回成「跟随系统」，否则两个开关会互相打架
        if (enabled) setThemeMode(ThemeMode.System)
    }

    fun setMonetColor(enabled: Boolean) {
        _monetColor.value = enabled
        prefs?.edit()?.putBoolean(KEY_MONET, enabled)?.apply()
    }

    fun setLanguage(language: Language) {
        _language.value = language
        prefs?.edit()?.putString(KEY_LANGUAGE, language.key)?.apply()
        val tag = language.tag
        if (tag != null) {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag(tag))
        }
    }

    fun setFontScale(scale: Float) {
        val normalized = FontScaleOptions.map { it.first }
            .minByOrNull { kotlin.math.abs(it - scale) } ?: 1.0f
        _fontScale.value = normalized
        prefs?.edit()?.putFloat(KEY_FONT_SCALE, normalized)?.apply()
    }
}
