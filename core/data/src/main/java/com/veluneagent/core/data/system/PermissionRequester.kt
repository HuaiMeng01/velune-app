package com.veluneagent.core.data.system

/**
 * 运行时权限的桥接口。
 *
 * 工具执行发生在 IO 线程，而权限弹窗必须由 Activity 发起并回到主线程，
 * 所以这里只声明「查询 + 申请」两件事，具体实现放在 app 模块（持有 Activity）。
 *
 * 约定：**申请失败返回 false，不抛异常**。工具层据此返回可读错误，
 * 而不是让整轮 Agent 因为一个权限被拒就崩掉。
 */
interface PermissionRequester {
    fun isGranted(permission: String): Boolean
    suspend fun request(permission: String): Boolean
}

/** 没有 UI 环境（例如单测）时的空实现：一律视为未授权。 */
object DeniedPermissionRequester : PermissionRequester {
    override fun isGranted(permission: String): Boolean = false
    override suspend fun request(permission: String): Boolean = false
}
