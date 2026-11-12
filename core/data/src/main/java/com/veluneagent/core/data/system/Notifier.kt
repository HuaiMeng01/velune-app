package com.veluneagent.core.data.system

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * 本地通知。
 *
 * Android 13+ 需要 POST_NOTIFICATIONS 运行时权限；没有权限时 **静默失败并如实返回 false**，
 * 不抛异常 —— 通知失败不该让一次工具调用变成错误。
 */
class Notifier(private val context: Context) {

    fun notify(title: String, body: String): Result<Boolean> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return@runCatching false
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return@runCatching false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    ChannelId,
                    UiText.t("Agent 提醒"),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = UiText.t("Agent 执行结果与提醒") },
            )
        }
        // 点通知要能回到应用。用 getLaunchIntentForPackage 而不是直接引用 MainActivity：
        // core:data 是 app 的下游，反向引用会把依赖方向弄反。
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val builder = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title.take(60))
            .setContentText(body.take(200))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.take(1_000)))
            .setAutoCancel(true)
        // 没有 contentIntent 的通知点下去毫无反应，用户会以为应用卡住了
        if (contentIntent != null) builder.setContentIntent(contentIntent)
        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), builder.build())
        true
    }

    companion object {
        private const val ChannelId = "velune_agent"
    }
}
