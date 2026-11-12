package com.veluneagent.core.data.im

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * openid → 本地别名。
 *
 * 官方只给 openid 不给昵称。取末 6 位当名字有三个毛病：
 * 不可读（问「我是谁」得到 7E8F90）、会撞号（100 人约 1.2%）、末位切片本身是 openid 的一部分。
 *
 * 键规则：私聊 `c2c:<openid>`；群聊 `group:<group_openid>/<member_openid>`。
 * **群维度必须带** —— 官方 member_openid 按群授权，同一人在两个群可能是不同值。
 *
 * 分配：首次出现分配「用户A」…「用户Z」，用尽后「用户27」递增。容量 200，超限按最久未出现淘汰。
 */
object QqAliasStore {

    /** 一条别名记录。`msgCount` 只增不减，用来在设置页说明「这个人说过多少话」。 */
    data class Alias(
        val key: String,
        val name: String,
        val seenAt: Long,
        val msgCount: Int,
    )

    /** 容量上限：超出后按 `seenAt` 最旧的先淘汰。QQ 自用场景下 200 人足够了。 */
    private const val MaxEntries = 200

    private const val PREFS = "velune_qq_aliases"
    private const val KEY_ALIASES = "aliases"

    /** 首字母池：26 个人之后只好用数字，所以前面的表达更友好。 */
    private const val Pool = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    private var appContext: Context? = null

    /** 并发保护：两个新用户同时出现时，「找下一个可用名字」必须串行，否则会撞名。 */
    private val mutex = Mutex()

    private val _all = MutableStateFlow<List<Alias>>(emptyList())

    /** 全部别名，按最近出现倒序（设置页直接用）。 */
    val all: StateFlow<List<Alias>> = _all.asStateFlow()

    fun init(context: Context) {
        if (appContext != null) return
        val ctx = context.applicationContext
        appContext = ctx
        _all.value = runCatching { read(ctx) }.getOrDefault(emptyList())
            .sortedByDescending { it.seenAt }
    }

    /**
     * 组装存储键。
     *
     * 群聊必须带上 `groupOpenId`：官方 `member_openid` 是**按群授权**的，
     * 同一个人在不同群里可能拿到不同值；不带群维度就会把两个人合并、或者一个人裂成两个。
     */
    fun keyOf(isGroup: Boolean, groupOpenId: String?, openId: String): String {
        val who = openId.trim()
        if (!isGroup) return "c2c:$who"
        val group = groupOpenId?.trim().orEmpty()
        return if (group.isEmpty()) "c2c:$who" else "group:$group/$who"
    }

    /**
     * 取别名，首次出现时分配并落盘。
     *
     * 名字一旦分配就不再变（除非用户 [rename]）：改名成本远高于错名，
     * 而"同一个人今天叫用户A、明天叫用户B"会让上下文里的发言人标签彻底失去意义。
     */
    suspend fun nameOf(key: String): String = mutex.withLock {
        val current = _all.value
        val existing = current.firstOrNull { it.key == key }
        if (existing != null) {
            // 只在内存里更新计数与最近出现时间，避免每来一条消息就写一次盘
            val touched = existing.copy(
                seenAt = System.currentTimeMillis(),
                msgCount = existing.msgCount + 1,
            )
            _all.value = (current - existing + touched).sortedByDescending { it.seenAt }
            persistAll(_all.value)
            return@withLock existing.name
        }

        val allocated = nextName(current)
        val fresh = Alias(
            key = key,
            name = allocated,
            seenAt = System.currentTimeMillis(),
            msgCount = 1,
        )
        val merged = (current + fresh)
            .sortedByDescending { it.seenAt }
            .take(MaxEntries)
        _all.value = merged
        persistAll(merged)
        allocated
    }

    /** 手动改名。空白名字或过长名字直接拒绝，不做静默截断（用户会以为改成功了）。 */
    suspend fun rename(key: String, name: String): Result<Unit> = mutex.withLock {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withLock Result.failure(IllegalArgumentException("名字不能为空"))
        if (trimmed.length > 20) return@withLock Result.failure(IllegalArgumentException("名字最多 20 个字"))
        val current = _all.value
        val existing = current.firstOrNull { it.key == key }
            ?: return@withLock Result.failure(IllegalArgumentException("这个人还没有别名记录"))
        val updated = existing.copy(name = trimmed)
        _all.value = (current - existing + updated).sortedByDescending { it.seenAt }
        persistAll(_all.value)
        Result.success(Unit)
    }

    /** 清空全部别名。清 QQ 配置时一并清，否则换号后旧别名会挂在陌生人身上。 */
    suspend fun clear(): Unit = mutex.withLock {
        _all.value = emptyList()
        val ctx = appContext ?: return@withLock
        withContext(Dispatchers.IO) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_ALIASES).apply()
        }
    }

    /**
     * 找下一个没被占用的名字。
     *
     * 顺序：先用 `用户A`…`用户Z`（26 个，好念），用尽后 `用户27`、`用户28`…
     * 从 27 起而不是 1，是为了不与「用户A」那批的可读性冲突，也避免和数字 1-26 混淆。
     */
    private fun nextName(existing: List<Alias>): String {
        val used = existing.map { it.name }.toSet()
        for (ch in Pool) {
            val candidate = "用户$ch"
            if (candidate !in used) return candidate
        }
        var index = Pool.length + 1
        while (true) {
            val candidate = "用户$index"
            if (candidate !in used) return candidate
            index += 1
        }
    }

    /**
     * 同步读。`SharedPreferences` 首次 `getSharedPreferences` 之后是内存缓存，读一个字符串
     * 不会真的碰磁盘，所以在 `init()`（非 suspend）里直接读是安全的。
     */
    private fun read(ctx: Context): List<Alias> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ALIASES, null)
            .orEmpty()
        if (raw.isBlank()) return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val key = obj.optString("key")
                val name = obj.optString("name")
                if (key.isBlank() || name.isBlank()) continue
                add(
                    Alias(
                        key = key,
                        name = name,
                        seenAt = obj.optLong("seenAt", 0L),
                        msgCount = obj.optInt("msgCount", 0),
                    ),
                )
            }
        }
    }

    private suspend fun persistAll(list: List<Alias>) {
        val ctx = appContext ?: return
        val payload = JSONArray().apply {
            for (item in list) {
                put(
                    JSONObject()
                        .put("key", item.key)
                        .put("name", item.name)
                        .put("seenAt", item.seenAt)
                        .put("msgCount", item.msgCount),
                )
            }
        }.toString()
        withContext(Dispatchers.IO) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_ALIASES, payload)
                .apply()
        }
    }
}
