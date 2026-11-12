package com.veluneagent.core.data.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 短信读取。
 *
 * 只读收件箱，且**必须**用户显式授予 READ_SMS——工具调用时若没有权限会先申请，
 * 被拒绝就返回可读错误，不会静默返回空列表（空列表会被模型解读成「没有短信」，
 * 从而给出错误事实）。
 *
 * 注意：READ_SMS 属于 Google Play 的「敏感权限」，非默认短信应用上架需要额外声明与审核。
 * 本应用走的是本机安装路径，不受该商店政策约束；若将来上架需要重新评估。
 */
class SmsReader(
    private val context: Context,
    private val permissions: PermissionRequester,
) {

    data class Message(
        val address: String,
        val body: String,
        val date: Long,
        val read: Boolean,
    )

    suspend fun recent(limit: Int, from: String? = null): Result<List<Message>> {
        if (!permissions.isGranted(Manifest.permission.READ_SMS)) {
            val granted = permissions.request(Manifest.permission.READ_SMS)
            if (!granted) {
                return Result.failure(SecurityException("未授予短信权限"))
            }
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                val selection = from?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    "${Telephony.Sms.ADDRESS} LIKE ?"
                }
                val args = selection?.let { arrayOf("%${from!!.trim()}%") }
                val projection = arrayOf(
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.READ,
                )
                val out = mutableListOf<Message>()
                // ContentResolver 不支持 LIMIT（除非用 Bundle 查询参数），读到够数就停
                context.contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    projection,
                    selection,
                    args,
                    "${Telephony.Sms.DATE} DESC",
                )?.use { cursor ->
                    val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                    val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                    val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                    val readIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)
                    while (cursor.moveToNext() && out.size < limit.coerceIn(1, 20)) {
                        out += Message(
                            address = cursor.getString(addressIndex).orEmpty(),
                            body = cursor.getString(bodyIndex).orEmpty(),
                            date = cursor.getLong(dateIndex),
                            read = cursor.getInt(readIndex) != 0,
                        )
                    }
                }
                out
            }
        }
    }

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_SMS,
    ) == PackageManager.PERMISSION_GRANTED
}
