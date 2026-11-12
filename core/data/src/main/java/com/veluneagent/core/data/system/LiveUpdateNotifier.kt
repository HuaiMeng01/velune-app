package com.veluneagent.core.data.system

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Android 16 Live Updates（实时动态）适配封装。
 *
 * 「实时活动」在 Android 上的官方实现是 Live Updates：系统把满足条件的通知
 * 提升到通知抽屉顶部、锁屏和状态栏 chip，用户无需打开应用即可跟踪一个
 * 用户发起、正在进行、有时效性的活动（连接、导航、任务执行…）。
 *
 * 提升准入条件（官方文档：developer.android.com/develop/ui/notifications/live-update）：
 * - 清单声明 android.permission.POST_PROMOTED_NOTIFICATIONS（非运行时权限，app 清单里已加）；
 * - 通知用 EXTRA_REQUEST_PROMOTED_ONGOING / compat 的 setRequestPromotedOngoing 请求提升；
 * - ongoing（FLAG_ONGOING_EVENT）且设置了 contentTitle；
 * - 样式为 Standard / BigText / Call / Progress / Metric 之一；
 * - 不是自定义 RemoteViews、分组摘要、colorized，渠道不是 IMPORTANCE_MIN。
 *
 * 满足后由系统决定是否提升；用户可以在设置里关掉（用户降级优先于一切）。
 * API < 36 的设备上自动退化为普通常驻通知——[NotificationCompat] 的 extra
 * 写进老系统只是无效字段，不会崩。
 */
class LiveUpdateNotifier(private val context: Context) {

    /** 旅程中的一段：[length] 是轴上的长度，[color] 为 ARGB int。 */
    data class Segment(val length: Int, val color: Int)

    /** 旅程中的一个里程碑点：[position] 是轴上的位置。 */
    data class Point(val position: Int, val color: Int)

    /**
     * 构建（不发送）一条满足 Live Update 准入条件的通知。
     *
     * 渠道由调用方负责创建（保持 app 层对渠道文案/重要级的控制权）；
     * [post] 走默认渠道时会自行建渠道。
     *
     * @param ongoing    进行中的活动必须 true——这是提升的硬性条件；
     *                   一次性告知类通知传 false，同时不会请求提升。
     * @param requestPromotion  是否请求系统提升；默认跟随 [ongoing]。
     * @param usesChronometer   头部时间显示为已进行时长（计时器）。
     * @param whenMillis        计时器起点（毫秒）。**必须由调用方固定**：通知的 `when`
     *                          默认取构建时刻，而状态每刷新一次就重新 build 一次，
     *                          不显式传就会把秒表清零（用户看到已运行时长往回跳）。
     * @param smallIcon         状态栏小图标资源 id。
     * @param progress   进度值，以进度轴总长为基准（分段长度之和，无分段时为 100）；
     *                   null 表示不显示确定进度。
     * @param indeterminate     进行中但无终点的活动：画不确定进度轴（滚动的轨道）。
     *                   与 [progress] 互斥；老系统上直接不画，避免常驻通知挂一条永不前进的进度条。
     * @param segments   进度轴分段（连接/运行等阶段着色），仅 API 36+ 渲染。
     * @param points     进度轴里程碑点，仅 API 36+ 渲染。
     */
    fun build(
        channelId: String,
        title: String,
        text: String,
        ongoing: Boolean = true,
        requestPromotion: Boolean = ongoing,
        usesChronometer: Boolean = false,
        whenMillis: Long = System.currentTimeMillis(),
        smallIcon: Int = android.R.drawable.ic_dialog_info,
        progress: Int? = null,
        indeterminate: Boolean = false,
        segments: List<Segment>? = null,
        points: List<Point>? = null,
    ): Notification {
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

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIcon)
            .setContentTitle(safeTake(title, 60))
            .setContentText(safeTake(text, 200))
            .setOngoing(ongoing)
            // Live Updates 准入硬条件：主动请求系统提升。老系统上只是多一个无效 extra。
            .setRequestPromotedOngoing(requestPromotion)
            // 活动类通知会被反复刷新，只提示一次，不能每条状态都响
            .setOnlyAlertOnce(true)
            // 官方建议的分类标记；不参与准入判定，但有助于系统理解这是进度型通知
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            // 提升后卡片会出现在锁屏顶部。不设 visibility 时默认 PRIVATE，
            // 系统开启「隐藏敏感内容」后卡片正文会被遮成「内容已隐藏」，提升等于白做。
            // 约束：本类只服务连接/任务状态这类固定话术，不得把用户内容塞进 title/text。
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (contentIntent != null) builder.setContentIntent(contentIntent)
        if (usesChronometer) {
            // 计时器算的是「自 when 起」：显式固定起点，否则每次重新 build 都会归零
            builder.setWhen(whenMillis).setShowWhen(true).setUsesChronometer(true)
        }

        // 进度轴：progress = 确定进度，indeterminate = 进行中但没有终点
        val usesProgressAxis = progress != null || indeterminate
        val style: NotificationCompat.Style? = when {
            usesProgressAxis && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA ->
                progressStyle(progress, indeterminate, segments, points)
            // 老 API：确定进度由下面的 setProgress 兜底；不确定进度干脆不画
            usesProgressAxis -> null
            else -> NotificationCompat.BigTextStyle().bigText(safeTake(text, 1_000))
        }
        if (style != null) builder.setStyle(style)
        if (progress != null && !indeterminate && Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            builder.setProgress(100, progress, false)
        }
        return builder.build()
    }

    /** API 36+ 的进度轴样式：分段 + 里程碑点，或不确定进度。 */
    private fun progressStyle(
        progress: Int?,
        indeterminate: Boolean,
        segments: List<Segment>?,
        points: List<Point>?,
    ): NotificationCompat.ProgressStyle {
        val style = NotificationCompat.ProgressStyle()
        // 两者语义互斥：不确定进度会忽略进度值
        if (indeterminate) {
            style.setProgressIndeterminate(true)
        } else if (progress != null) {
            style.setProgress(progress)
        }
        // setProgressSegments/Points 是整体替换语义：每次传完整列表
        segments?.let {
            style.setProgressSegments(
                it.map { s -> NotificationCompat.ProgressStyle.Segment(s.length).setColor(s.color) },
            )
        }
        points?.let {
            style.setProgressPoints(
                it.map { p -> NotificationCompat.ProgressStyle.Point(p.position).setColor(p.color) },
            )
        }
        return style
    }

    /** 用默认渠道发布一条实时动态通知（自建渠道 + POST_NOTIFICATIONS 权限检查）。 */
    fun post(
        id: Int,
        title: String,
        text: String,
        progress: Int? = null,
        segments: List<Segment>? = null,
        points: List<Point>? = null,
    ): Result<Boolean> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return@runCatching false
        }
        val manager = context.getSystemService(NotificationManager::class.java)
            ?: return@runCatching false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(ChannelId) == null
        ) {
            // 渠道不能是 IMPORTANCE_MIN（提升准入条件之一）；LOW 静默常驻正合适
            manager.createNotificationChannel(
                NotificationChannel(ChannelId, UiText.t("实时动态"), NotificationManager.IMPORTANCE_LOW).apply {
                    description = UiText.t("可跟踪的进行中任务状态（Android 16 实时动态）")
                },
            )
        }
        val notification = build(
            channelId = ChannelId,
            title = title,
            text = text,
            progress = progress,
            segments = segments,
            points = points,
        )
        NotificationManagerCompat.from(context).notify(id, notification)
        true
    }

    /** 结束一次实时动态：活动到终点后必须收掉，不能留一张永不结束的卡片。 */
    fun cancel(id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    /** 设备是否具备 Live Updates 能力（Android 16 / API 36 起）。 */
    fun isLiveUpdateSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA

    /**
     * 当前应用能否发布提升通知（用户是否在设置里启用了实时动态）。
     * 仅 API 36+ 有意义；返回 false 时通知仍会以普通形式出现，只是没有 chip/锁屏卡片。
     */
    fun canPostPromotedNotifications(): Boolean =
        isLiveUpdateSupported() && NotificationManagerCompat.from(context).canPostPromotedNotifications()

    /**
     * 跳转系统设置里本应用的实时动态开关（用户拒绝后引导恢复用）。
     *
     * 两个易错点，改动前务必核对平台常量而不是官方网页文档：
     * - action：网页文档写的是 `ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS`，
     *   但 API 36/37 框架里**没有**这个常量，最终发布的是
     *   `ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS`（字符串
     *   `android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS`）。照抄文档会抛
     *   ActivityNotFoundException，被下面的 runCatching 吞掉后恒返回 false。
     * - extra：官方 API 参考要求输入 `EXTRA_APP_PACKAGE`，其值为
     *   `android.provider.extra.APP_PACKAGE`，不是 `android.intent.extra.APP_PACKAGE`。
     *
     * 不用 resolveActivity 预检：Android 11+ 的包可见性会让它对本机 Settings 返回 null，
     * 造成「明明能跳却判成不支持」的误判；真失败由 startActivity 抛异常后兜底。
     */
    fun openPromotionSettings(): Boolean = runCatching {
        if (!isLiveUpdateSupported()) return@runCatching false
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // 直达本应用页，避免用户在全局列表里翻找
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
        true
    }.getOrDefault(false)

    /**
     * 按 code point 边界截断。
     *
     * 通知文案有一部分来自网络错误等外部字符串，`take(n)` 按 UTF-16 code unit 切，
     * 截断点落在代理对中间时会把一个 emoji 劈成两个替换符（项目里 QqText 处理的是同一类问题）。
     */
    private fun safeTake(value: String, max: Int): String {
        if (value.length <= max) return value
        val end = if (Character.isHighSurrogate(value[max - 1])) max - 1 else max
        return value.substring(0, end)
    }

    companion object {
        const val ChannelId = "velune_live_updates"
    }
}
