plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

/**
 * UI 约束检查。
 *
 * 文档里写了「禁止 material3、原生 Dialog / Toast / PopupWindow，全部走 Vx* 包装层，
 * 用 lint 强制」，但项目里一直没有对应检查 —— 规范靠自觉等于没有规范。
 * 这里用 Gradle task 做静态扫描：不引额外依赖，任何机器 `gradle checkUiConstraints` 都能跑。
 */
val forbiddenUiPatterns = mapOf(
    "androidx.compose.material3" to "不要用 material3 组件，改用 core:design 的 Vx* 包装层",
    "android.widget.Toast" to "不要用系统 Toast，改用 VxSnackbar",
    "android.app.Dialog" to "不要用系统 Dialog，改用 VxActionSheet / VxPromptSheet",
    "android.app.AlertDialog" to "不要用 AlertDialog，改用 VxActionSheet",
    "android.widget.PopupWindow" to "不要用 PopupWindow，改用 VxActionMenu",
    "androidx.compose.ui.window.Dialog" to "不要用 Compose Dialog，改用 VxActionSheet",
)

tasks.register("checkUiConstraints") {
    group = "verification"
    description = "禁止 material3 与原生弹窗 / Toast / PopupWindow"

    val projectRoot = rootProject.projectDir

    doLast {
        val offenders = mutableListOf<String>()
        var bridgeUsages = 0
        projectRoot.walkTopDown()
            .onEnter { dir ->
                dir.name != "build" && dir.name != ".gradle" && dir.name != ".workbuddy"
            }
            .filter { it.isFile && it.extension == "kt" && it.invariantSeparatorsPath.contains("/src/main/") }
            .forEach { file ->
                val relative = file.relativeTo(projectRoot).invariantSeparatorsPath
                // core:design 是唯一允许触碰 material3 的桥接层（VxText / VxRipple 在它里面封装）
                val isBridgeLayer = relative.startsWith("core/design/")
                val text = file.readText()
                forbiddenUiPatterns.forEach { (pattern, hint) ->
                    if (text.contains(pattern)) {
                        if (isBridgeLayer) {
                            bridgeUsages++
                        } else {
                            offenders += "$relative: $pattern —— $hint"
                        }
                    }
                }
            }

        if (offenders.isNotEmpty()) {
            throw GradleException(
                "UI 约束检查未通过（${offenders.size} 处）：\n" + offenders.joinToString("\n"),
            )
        }
        println("checkUiConstraints: 通过（core:design 桥接层内 $bridgeUsages 处 material3 引用已豁免）")
    }
}
