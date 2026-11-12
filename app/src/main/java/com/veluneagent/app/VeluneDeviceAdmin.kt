package com.veluneagent.app

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * 设备管理员接收器。
 *
 * 存在的唯一目的：让 `DevicePolicyManager.lockNow()` 有权限可用。
 * 策略文件里只声明 `force-lock`，不申请清除数据 / 重置密码 / 锁定屏幕密码，
 * 因此即使被滥用，最坏结果也只是「把手机锁上」。
 *
 * 类名被 core 层的 ScreenController 以字符串引用（AdminClassName），改名要同步。
 */
class VeluneDeviceAdmin : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }
}
