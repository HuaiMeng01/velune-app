package com.veluneagent.core.data.system

/**
 * 一组系统能力。集中注入，工具执行器只依赖这一个对象，
 * 避免构造函数参数一路膨胀到七八个。
 */
class SystemCapabilities(
    val permissions: PermissionRequester,
    val calendar: CalendarRepository,
    val todos: TodoStore,
    val notifier: Notifier,
    val apps: AppLauncher,
    val sms: SmsReader,
    val screen: ScreenController,
)
