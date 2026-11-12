package com.veluneagent.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.veluneagent.core.design.theme.VeluneTheme

class MainActivity : ComponentActivity() {

    /** 权限弹窗结果回传给桥接层；必须在 onCreate 之前注册，否则系统会拒绝。 */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        (application as VeluneApplication).permissionRequester.onResult(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as VeluneApplication).permissionRequester.attach(permissionLauncher)
        super.onCreate(savedInstanceState)
        // 关键：显式透明 scrim。默认的 auto() 会给状态栏叠一层半透明遮罩，
        // 导致状态栏和 App 顶栏之间出现一道色差与接缝。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                Color.Transparent.toArgb(),
                Color.Transparent.toArgb(),
            ),
            navigationBarStyle = SystemBarStyle.auto(
                Color.Transparent.toArgb(),
                Color.Transparent.toArgb(),
            ),
        )
        // 必须显式关闭 decor 内边距，否则状态栏区域仍由 windowBackground 填充，
        // 会与页面底色形成一条色差带（实测状态栏取色 #F2F2F2，页面 #FFFFFF）。
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val themeMode by com.veluneagent.core.data.profile.AppearancePrefs.themeMode.collectAsState()
            val fontScale by com.veluneagent.core.data.profile.AppearancePrefs.fontScale.collectAsState()
            val autoDark by com.veluneagent.core.data.profile.AppearancePrefs.autoDark.collectAsState()
            val monetColor by com.veluneagent.core.data.profile.AppearancePrefs.monetColor.collectAsState()
            val language by com.veluneagent.core.data.profile.AppearancePrefs.language.collectAsState()
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            // 「自动切换深色模式」打开时以系统为准，优先于显式的浅色/深色选择
            val mode = if (autoDark) {
                com.veluneagent.core.data.profile.AppearancePrefs.ThemeMode.System
            } else {
                themeMode
            }
            val dark = when (mode) {
                com.veluneagent.core.data.profile.AppearancePrefs.ThemeMode.System -> systemDark
                com.veluneagent.core.data.profile.AppearancePrefs.ThemeMode.Light -> false
                com.veluneagent.core.data.profile.AppearancePrefs.ThemeMode.Dark -> true
            }
            // 强制浅色/深色时状态栏图标必须跟着翻转，否则深色底上是黑图标（看不见）
            androidx.compose.runtime.SideEffect {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
            // 语言开关：显式选了 English 直接开；「跟随系统」看系统首语言是不是中文。
            // 资源配置（resources.configuration）始终反映系统语言，不受 Locale.setDefault 影响，
            // 所以这里不会把自己设的英文误判成“系统是英文”。
            androidx.compose.runtime.SideEffect {
                val systemEnglish = resources.configuration.locales[0].language != "zh"
                val english = when (language) {
                    com.veluneagent.core.data.profile.AppearancePrefs.Language.English -> true
                    com.veluneagent.core.data.profile.AppearancePrefs.Language.Chinese -> false
                    com.veluneagent.core.data.profile.AppearancePrefs.Language.System -> systemEnglish
                }
                com.veluneagent.core.design.l10n.VxL10n.isEnglish = english
                // 同步 core/data 的文案桥：通知渠道名、待办提醒、Agent 步骤 display 摘要
                com.veluneagent.core.data.system.UiText.t =
                    { key -> com.veluneagent.core.design.l10n.tr(key) }
                com.veluneagent.core.data.system.UiText.tf =
                    { key, args -> com.veluneagent.core.design.l10n.tr(key).format(*args) }
            }
            VeluneTheme(darkTheme = dark, fontScale = fontScale, monetColor = monetColor) {
                VeluneApp()
            }
        }
    }
}
