package com.veluneagent.core.data.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * 应用的「看」与「开」。
 *
 * 只列**有桌面入口**的应用（MAIN/LAUNCHER），不用 QUERY_ALL_PACKAGES：
 * 后者能看到全部包（含系统服务、库模块），既没必要，也是隐私上更重的权限。
 * 相应地 manifest 里声明的 `<queries>` 也只匹配 launcher intent。
 */
class AppLauncher(private val context: Context) {

    data class AppInfo(val packageName: String, val label: String)

    /** 可启动应用，按名称排序。数量上限 200：这是给人看/给模型挑的列表，不是索引。 */
    fun launchable(query: String? = null): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = runCatching {
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }.getOrDefault(emptyList())

        val keyword = query?.trim()?.lowercase().orEmpty()
        return resolved.asSequence()
            .map { info ->
                AppInfo(
                    packageName = info.activityInfo.packageName,
                    label = info.loadLabel(pm).toString().ifBlank { info.activityInfo.packageName },
                )
            }
            // 同一个包可能有多个 launcher activity（如设置快捷方式），按包名去重
            .distinctBy { it.packageName }
            .filter {
                keyword.isEmpty() ||
                    it.label.lowercase().contains(keyword) ||
                    it.packageName.lowercase().contains(keyword)
            }
            .sortedBy { it.label.lowercase() }
            .take(200)
            .toList()
    }

    /** 按包名或应用名启动。返回实际启动的包名，找不到返回 null。 */
    fun launch(target: String): AppInfo? {
        val value = target.trim()
        if (value.isEmpty()) return null
        val pm = context.packageManager

        val app = launchable().firstOrNull {
            it.packageName.equals(value, ignoreCase = true) ||
                it.label.equals(value, ignoreCase = true)
        } ?: launchable(value).firstOrNull()

        val pkg = app?.packageName ?: value
        val intent = pm.getLaunchIntentForPackage(pkg) ?: return null
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (runCatching { context.startActivity(intent) }.isSuccess) {
            app ?: AppInfo(pkg, pkg)
        } else {
            null
        }
    }

    fun resolve(target: String): AppInfo? {
        val value = target.trim()
        if (value.isEmpty()) return null
        return launchable().firstOrNull {
            it.packageName.equals(value, ignoreCase = true) ||
                it.label.equals(value, ignoreCase = true)
        } ?: launchable(value).firstOrNull()
    }
}
