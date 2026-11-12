package com.veluneagent.core.data.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 待办闹钟的落脚点。
 *
 * 只做一件事：醒来时发一条通知。不做任何数据库写入 —— 广播接收器的生命周期很短，
 * 在里面开事务、等 IO 很容易被系统中途掐掉；通知发出去就够了。
 */
class TodoAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(TodoReminder.ExtraTitle)
            ?.takeIf { it.isNotBlank() }
            ?: UiText.t("你有一条到点的待办")
        // 通知失败（没有 POST_NOTIFICATIONS 权限）时静默：不能因为提醒失败就崩
        runCatching { Notifier(context).notify(UiText.t("待办提醒"), title) }
    }
}
