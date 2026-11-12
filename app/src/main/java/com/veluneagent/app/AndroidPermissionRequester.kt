package com.veluneagent.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.veluneagent.core.data.system.PermissionRequester
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * 运行时权限桥。
 *
 * 工具执行在 IO 线程，权限弹窗只能在主线程由 Activity 发起；
 * 这里用 CompletableDeferred 把「弹窗 → 用户点击 → 结果」串成一次挂起调用，
 * 期间不占线程。同一时刻只允许一个待决请求：并发申请会被系统排队，
 * 我们直接复用同一个结果，避免出现「两次弹窗、一次结果」的错位。
 */
class AndroidPermissionRequester(
    private val context: android.content.Context,
) : PermissionRequester {

    private var launcher: ActivityResultLauncher<String>? = null
    private val pending = AtomicReference<CompletableDeferred<Boolean>?>(null)

    fun attach(launcher: ActivityResultLauncher<String>) {
        this.launcher = launcher
    }

    fun onResult(granted: Boolean) {
        pending.getAndSet(null)?.complete(granted)
    }

    override fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun request(permission: String): Boolean {
        if (isGranted(permission)) return true
        val activityLauncher = launcher ?: return false
        // 已有同权限的请求在飞：等它的结果，不再弹第二次
        pending.get()?.let { return it.await() }

        val deferred = CompletableDeferred<Boolean>()
        pending.set(deferred)
        withContext(Dispatchers.Main) {
            runCatching { activityLauncher.launch(permission) }
                .onFailure {
                    pending.set(null)
                    deferred.complete(false)
                }
        }
        return deferred.await()
    }

    /** 权限列表，用于「权限中心」展示。 */
    fun snapshot(): Map<String, Boolean> =
        PermissionCatalog.runtime.associate { it.permission to isGranted(it.permission) }
}

/** 权限清单：集中定义，避免各处硬编码字符串。 */
object PermissionCatalog {
    data class Item(
        val permission: String,
        val title: String,
        val why: String,
    )

    val runtime = listOf(
        Item(
            Manifest.permission.READ_CALENDAR,
            "读取日历",
            "让你问「今天有什么安排」时能查到系统日程",
        ),
        Item(
            Manifest.permission.WRITE_CALENDAR,
            "写入日历",
            "让 Agent 帮你新建日程与提醒",
        ),
        Item(
            Manifest.permission.POST_NOTIFICATIONS,
            "发送通知",
            "任务完成或到点提醒时通知你",
        ),
        Item(
            Manifest.permission.READ_SMS,
            "读取短信",
            "让你问「谁给我发短信」「有没有验证码」时能读到收件箱",
        ),
    )
}
