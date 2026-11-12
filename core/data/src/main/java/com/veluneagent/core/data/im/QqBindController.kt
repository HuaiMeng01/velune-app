package com.veluneagent.core.data.im

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 扫码绑定的状态机。
 *
 * `Idle → Creating → Waiting(qr, secondsLeft, attempt) → Done / Failed`
 *
 * 为什么不放进 Service：绑定是**一次性交互**，用户切后台就该停 ——
 * 常驻服务里挂一个 3 分钟的轮询，等于用户离开页面后还在偷偷联网。
 * 所以 scope 由 UI 提供，页面销毁即自动取消。
 *
 * 二维码会过期（默认 180 秒），过期后自动换一张，最多 [maxRegenerate] 张。
 * 每张二维码对应一个新的 32 字节 key：换了任务却不换 key，解密必然失败。
 */
class QqBindController(
    private val scope: CoroutineScope,
    private val host: String = QqBind.HostProduction,
    private val pollIntervalMs: Long = 2_000L,
    private val taskTtlMs: Long = 180_000L,
    private val maxRegenerate: Int = 3,
    /**
     * 创建任务的入口。默认指向 [QqBind.createTask]：生产行为零变化。
     *
     * 之所以做成构造参数而不是全局 `var`：测试要替换网络响应，
     * 但**不能**有全局可变状态（并行跑用例会互相污染，且忘记还原就会污染真实运行）。
     * 与 `QqResponder` 注入 `stream` 是同一个模式。
     */
    private val createTask: suspend (String) -> Result<QqBind.Task> = QqBind::createTask,
    /**
     * 轮询入口。**仅供测试注入**：默认值指向 [QqBind.poll]，生产行为零变化。
     *
     * 做成构造参数而不是 `QqBind.pollOverride` 全局 `var`：全局可变状态会让并行用例
     * 互相污染，忘记还原还会污染真实运行。
     */
    private val poll: suspend (QqBind.Task, String) -> Result<QqBind.Poll> = QqBind::poll,
) {

    /** 绑定界面需要渲染的全部状态。 */
    sealed interface State {
        data object Idle : State
        data object Creating : State
        data class Waiting(
            val qrContent: String,
            /** 当前二维码剩余秒数（用于 UI 倒计时）。 */
            val secondsLeft: Int,
            /** 第几张二维码，从 1 开始。 */
            val attempt: Int,
        ) : State

        /** 成功：**appSecret 只在内存**，调用方需立刻 `QqBot.save()` 落库（内部会加密）。 */
        data class Done(
            val appId: String,
            val appSecret: String,
            val ownerOpenId: String,
        ) : State

        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private var job: Job? = null

    val running: Boolean get() = job?.isActive == true

    /** 开始绑定：重复调用无副作用（已在跑就直接返回）。 */
    fun start() {
        if (running) return
        _state.value = State.Creating
        job = scope.launch { runLoop() }
    }

    /** 取消并回到 Idle：用户关页面、或已经拿 `Done` 落库之后都该调。 */
    fun cancel() {
        job?.cancel()
        job = null
        _state.value = State.Idle
    }

    private suspend fun runLoop() {
        var attempt = 1
        while (scope.isActive) {
            _state.value = State.Creating
            val task = createTask(host).getOrElse {
                _state.value = State.Failed(it.message ?: "创建绑定任务失败")
                return
            }
            val deadline = System.currentTimeMillis() + taskTtlMs
            var expired = false
            var networkErrors = 0
            var done = false

            // 用普通 while + 标志位：Result.fold 的 lambda 里不能 break（Kotlin 不允许非局部跳转）
            while (!done && !expired && System.currentTimeMillis() < deadline) {
                _state.value = State.Waiting(
                    qrContent = task.qrContent,
                    secondsLeft = ((deadline - System.currentTimeMillis()) / 1000).toInt(),
                    attempt = attempt,
                )
                delay(pollIntervalMs)
                val result = poll(task, host)
                when (val value = result.getOrNull()) {
                    is QqBind.Poll.Completed -> {
                        // 拿到结果立刻销毁 key：它已经完成使命，多留一秒都是风险
                        task.key.fill(0)
                        _state.value = State.Done(value.appId, value.appSecret, value.ownerOpenId)
                        done = true
                    }
                    QqBind.Poll.Expired -> expired = true
                    QqBind.Poll.Pending -> networkErrors = 0
                    null -> {
                        networkErrors++
                        // 偶发一次网络抖动不该终止整个绑定流程：连续 3 次才认输
                        if (networkErrors >= 3) {
                            task.key.fill(0)
                            _state.value = State.Failed(
                                result.exceptionOrNull()?.message ?: "轮询失败",
                            )
                            return
                        }
                    }
                }
            }
            task.key.fill(0)
            if (done) return
            attempt++
            if (attempt > maxRegenerate) {
                _state.value = State.Failed("二维码多次过期，请重试或改用手工填写")
                return
            }
        }
    }
}
