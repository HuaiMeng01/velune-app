package com.veluneagent.core.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 模型分工。
 *
 * 三个角色各自可以绑一个服务商：
 * - [Role.Main]：对话与 Agent 主循环（默认就是「使用中」的那个服务商）
 * - [Role.Summary]：上下文压缩、自动标题这类后台小任务 —— 用便宜模型省钱
 * - [Role.Sub]：Agent 运行中通过 `delegate_task` 派发的子任务 —— 省钱 + 隔离上下文
 *
 * 只存服务商 id，不复制 baseUrl / model：用户改了服务商的模型名，分工自动跟着变，
 * 不会出现「设置里改了半天，摘要还在用旧模型」。
 *
 * 没绑定 = 跟随主模型。绑定的服务商被删掉时同样回落到主模型 ——
 * 一次误删不该把摘要或子模型整个打挂。
 */
object ModelRouting {

    enum class Role { Main, Summary, Sub }

    private const val PREFS = "velune_model_routing"

    private var appContext: Context? = null

    private val _assignments = MutableStateFlow<Map<Role, String>>(emptyMap())

    /** role -> providerId。缺省表示跟随主模型。 */
    val assignments: StateFlow<Map<Role, String>> = _assignments

    fun init(context: Context) {
        if (appContext != null) return
        val ctx = context.applicationContext
        appContext = ctx
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _assignments.value = buildMap {
            Role.entries.forEach { role ->
                prefs.getString(role.name, null)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { put(role, it) }
            }
        }
    }

    /** [providerId] 为空表示「跟随主模型」。 */
    fun assign(role: Role, providerId: String?) {
        val normalized = providerId?.takeIf { it.isNotBlank() }
        _assignments.value = _assignments.value.toMutableMap().apply {
            if (normalized == null) remove(role) else put(role, normalized)
        }
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()
            ?.apply {
                if (normalized == null) remove(role.name) else putString(role.name, normalized)
            }
            ?.apply()
    }

    /**
     * 解析某个角色实际该用哪个服务商。
     *
     * [main] 由调用方传入（通常是当前「使用中」的服务商），
     * 这样分工逻辑不依赖 ProviderStore 的单例状态，单测里可以直接喂数据。
     */
    fun providerFor(role: Role, providers: List<Provider>, main: Provider?): Provider? {
        if (role == Role.Main) return main
        val bound = _assignments.value[role]
        return providers.firstOrNull { it.id == bound } ?: main
    }

    /** 该角色是否显式绑定了别的模型（UI 用来显示「跟随主模型」还是具体名字）。 */
    fun isBound(role: Role): Boolean = _assignments.value[role] != null
}
