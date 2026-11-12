package com.veluneagent.core.data.im

/**
 * 网关重投去重：同一个 messageId 只处理一次。
 *
 * 为什么必须去重：官方网关在客户端没有及时 ACK、或 Resume 补发时，会把同一条事件
 * **再推一遍**。不去重的表现是机器人对同一句话连答两三次，而且每次都花一份模型额度。
 *
 * 容量 200 是刻意的取舍：正常对话里同期在途的消息远小于这个数，
 * 超过容量按插入顺序淘汰最老的，内存恒定（不会因为跑一个月而涨）。
 *
 * **空 id 一律不去重**：OneBot 部分实现拿不到 `message_id`（为 0 或空串），
 * 如果把空值也当成 key，第二条空 id 的消息就会被误判成重复而永远丢弃。
 */
object IncomingDedup {

    private const val Capacity = 200

    private val seen = object : LinkedHashMap<String, Boolean>(Capacity, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean =
            size > Capacity
    }

    /**
     * 返回 true 表示这条之前见过，应当丢弃。
     *
     * 线程安全：两套协议的 WebSocket 回调跑在 OkHttp 的读线程上，服务侧也在别的线程，
     * 不加锁会出现「检查与写入」之间的竞态，去重就会漏。
     */
    @Synchronized
    fun seenBefore(id: String): Boolean {
        if (id.isBlank()) return false
        if (seen.containsKey(id)) return true
        seen[id] = true
        return false
    }

    /**
     * 仅供测试重置状态。**生产代码不要调用**：
     * 跨断线重连必须保留去重表，否则 Resume 补发的事件会被当成新消息，Q9（同一消息答两遍）立刻复活。
     */
    @Synchronized
    fun clear() {
        seen.clear()
    }
}
