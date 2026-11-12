package com.veluneagent.core.data.im

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 扫码绑定状态机。
 *
 * 为什么值得测：这台状态机会自己「换二维码 / 重试 / 放弃」，而它调的是真网络。
 * 逻辑写错的表现不是崩溃，而是**用户盯着二维码等到 180 秒然后什么也没发生**——
 * 无日志、无提示，完全无法从用户描述里复现。
 *
 * 这里用「真实时间但很短」：`pollIntervalMs = 20`，单条用例 50~150ms，
 * 全套 < 1 秒。**没有引入 `kotlinx-coroutines-test`**：离线缓存里只有 1.9.0，
 * 而 `kotlinx-coroutines-core-jvm:1.10.2` 的 BOM 会把它强制升到 1.10.2（离线没有），
 * 强行 `force` 版本又要冒 test/core 二进制不兼容的风险，不值得。
 *
 * 所有断言都基于 `state.value` 的**同步采样**，不依赖 StateFlow 收集的时序（conflate 会丢中间值）。
 */
class QqBindControllerTest {

    private fun task(id: String) = QqBind.Task(
        taskId = id,
        key = ByteArray(32) { it.toByte() },
        qrContent = "qr-$id",
        createdAt = 0L,
    )

    private fun controller(
        scope: CoroutineScope,
        createTask: suspend (String) -> Result<QqBind.Task>,
        poll: suspend (QqBind.Task, String) -> Result<QqBind.Poll>,
        maxRegenerate: Int = 3,
    ) = QqBindController(
        scope = scope,
        host = QqBind.HostTest,
        pollIntervalMs = 20L,
        // TTL 只影响「用户不扫码时等多久」，这里给足 2 秒，用例都在 150ms 内结束
        taskTtlMs = 2_000L,
        maxRegenerate = maxRegenerate,
        createTask = createTask,
        poll = poll,
    )

    /** 同步等待条件成立；超时直接 fail（避免用例挂死）。 */
    private fun awaitUntil(
        timeoutMs: Long = 3_000L,
        describe: String = "条件",
        block: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (block()) return
            Thread.sleep(2)
        }
        fail("等待${describe}超时（${timeoutMs}ms）")
    }

    @Test
    fun `正常完成`() {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            var created = 0
            var polls = 0
            var lastTask: QqBind.Task? = null
            val c = controller(
                scope = scope,
                createTask = { id ->
                    created++
                    task(id).also { lastTask = it }.let { Result.success(it) }
                },
                poll = { _, _ ->
                    polls++
                    // 前两次还在等用户扫码，第三次扫码完成
                    if (polls <= 2) {
                        Result.success(QqBind.Poll.Pending)
                    } else {
                        Result.success(QqBind.Poll.Completed("APPID", "SECRET", "OWNER"))
                    }
                },
            )
            val waits = mutableListOf<Int>()

            c.start()
            awaitUntil(describe = "进入 Done") {
                (c.state.value as? QqBindController.State.Waiting)?.let { waits += it.attempt }
                c.state.value is QqBindController.State.Done
            }

            assertEquals(
                QqBindController.State.Done("APPID", "SECRET", "OWNER"),
                c.state.value,
            )
            assertEquals("只应创建一张二维码", 1, created)
            assertEquals("Pending 两次后 Completed", 3, polls)
            assertTrue("必须真的进过 Waiting（否则等于没轮询）", waits.isNotEmpty())
            assertEquals("全程只能是第 1 张码，实际采样：$waits", setOf(1), waits.toSet())
            // 安全不变量：拿到结果后 key 必须被清零（它就是 AppSecret 的钥匙，多留一秒都是风险）
            assertTrue("绑定成功后 key 必须 fill(0)", lastTask!!.key.all { it == 0.toByte() })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `过期后换一张码`() {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            var created = 0
            var polls = 0
            val c = controller(
                scope = scope,
                createTask = { id ->
                    created++
                    Result.success(task(id))
                },
                poll = { _, _ ->
                    polls++
                    when {
                        polls == 1 -> Result.success(QqBind.Poll.Expired)
                        // 第二张码上多等两轮，保证 Waiting(attempt=2) 能被采样到
                        polls <= 3 -> Result.success(QqBind.Poll.Pending)
                        else -> Result.success(QqBind.Poll.Completed("A2", "S2", "O2"))
                    }
                },
            )
            val waits = mutableListOf<Int>()

            c.start()
            awaitUntil(describe = "进入 Done") {
                (c.state.value as? QqBindController.State.Waiting)?.let { waits += it.attempt }
                c.state.value is QqBindController.State.Done
            }

            assertEquals(QqBindController.State.Done("A2", "S2", "O2"), c.state.value)
            assertEquals("过期后必须重新创建一张码", 2, created)
            assertTrue("中途必须出现过第 2 张码的 Waiting，实际采样：$waits", 2 in waits)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `连续过期到上限就放弃`() {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            var created = 0
            val c = controller(
                scope = scope,
                createTask = { id ->
                    created++
                    Result.success(task(id))
                },
                poll = { _, _ -> Result.success(QqBind.Poll.Expired) },
                maxRegenerate = 2,
            )
            c.start()
            awaitUntil(describe = "进入 Failed") {
                c.state.value is QqBindController.State.Failed
            }

            val state = c.state.value
            assertTrue("终态必须是 Failed，实际：$state", state is QqBindController.State.Failed)
            assertTrue(
                "文案要说清是多次过期：${(state as QqBindController.State.Failed).message}",
                state.message.contains("多次过期"),
            )
            assertEquals("最多 maxRegenerate 张码", 2, created)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `取消后回到 Idle 且停止轮询`() {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            var polls = 0
            val c = controller(
                scope = scope,
                createTask = { id -> Result.success(task(id)) },
                poll = { _, _ ->
                    polls++
                    Result.success(QqBind.Poll.Pending)
                },
            )
            c.start()
            awaitUntil(describe = "轮询起来") { polls >= 2 }

            c.cancel()
            assertEquals(QqBindController.State.Idle, c.state.value)

            // 取消是协作式的：可能有一次 poll 已经在飞。先让它落地，再断言「不再增长」
            Thread.sleep(100)
            val settled = polls
            Thread.sleep(200)
            assertEquals("cancel() 之后不能再发任何 poll", settled, polls)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `创建任务失败直接 Failed 不进 Waiting`() {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            var polls = 0
            var sawWaiting = false
            val c = controller(
                scope = scope,
                createTask = { Result.failure(IllegalStateException("网络炸了")) },
                poll = { _, _ ->
                    polls++
                    Result.success(QqBind.Poll.Pending)
                },
            )
            c.start()
            awaitUntil(describe = "进入 Failed") {
                if (c.state.value is QqBindController.State.Waiting) sawWaiting = true
                c.state.value is QqBindController.State.Failed
            }

            val state = c.state.value
            assertTrue("终态必须是 Failed", state is QqBindController.State.Failed)
            assertTrue((state as QqBindController.State.Failed).message.contains("网络炸了"))
            assertEquals("创建失败不该轮询", 0, polls)
            assertFalse("创建失败不该进入 Waiting", sawWaiting)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `连续 3 次网络失败才放弃`() {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            var polls = 0
            val c = controller(
                scope = scope,
                createTask = { id -> Result.success(task(id)) },
                poll = { _, _ ->
                    polls++
                    Result.failure(IllegalStateException("timeout"))
                },
            )
            c.start()
            awaitUntil(describe = "进入 Failed") {
                c.state.value is QqBindController.State.Failed
            }

            val state = c.state.value
            assertTrue("终态必须是 Failed", state is QqBindController.State.Failed)
            assertTrue((state as QqBindController.State.Failed).message.contains("timeout"))
            // 偶发一次抖动不该放弃：必须攒够 3 次
            assertEquals(3, polls)
        } finally {
            scope.cancel()
        }
    }
}
