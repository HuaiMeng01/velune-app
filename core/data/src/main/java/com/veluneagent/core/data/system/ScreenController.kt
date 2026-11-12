package com.veluneagent.core.data.system

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * 锁屏。
 *
 * Android 没有直接锁屏的普通 API：`DevicePolicyManager.lockNow()` 要求应用是**已激活的
 * 设备管理员**。激活必须由用户在系统弹窗里点确认，任何代码都绕不过去——这是设计使然。
 *
 * 因此这里的能力被拆成两半：
 * - [requestAdminIntent]：给 UI 用，拉起系统激活页；
 * - [lock]：给工具用，未激活时返回明确失败，而不是静默无效。
 *
 * 设备管理员策略只声明 `force-lock`（见 res/xml/device_admin.xml），不申请清除数据、
 * 重置密码这类高危权限——最小权限原则。
 */
class ScreenController(private val context: Context) {

    private val dpm: DevicePolicyManager?
        get() = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager

    private val admin: ComponentName
        get() = ComponentName(context.packageName, AdminClassName)

    fun isAdminActive(): Boolean =
        runCatching { dpm?.isAdminActive(admin) == true }.getOrDefault(false)

    fun lock(): Result<Unit> {
        val manager = dpm ?: return Result.failure(IllegalStateException("系统不支持设备管理"))
        if (!isAdminActive()) {
            return Result.failure(
                IllegalStateException("设备管理员未激活：设置 → 权限与能力 → 锁屏"),
            )
        }
        return runCatching { manager.lockNow() }
    }

    /** 系统激活页。必须由 Activity 发起（需要 startActivity 上下文）。 */
    fun requestAdminIntent(): Intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
        putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "仅用于执行锁屏指令，不会清除数据或修改密码。",
        )
    }

    fun removeAdmin() {
        runCatching { dpm?.removeActiveAdmin(admin) }
    }

    companion object {
        /** 与 AndroidManifest 中 receiver 的全限定名保持一致。 */
        const val AdminClassName = "com.veluneagent.app.VeluneDeviceAdmin"
    }
}
