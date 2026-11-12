package com.veluneagent.core.data.im

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 网关重投去重。
 *
 * 去重错了会两头出问题：漏去重 = 同一句话答两遍；过度去重 = 所有消息被当成重复而丢弃。
 * 第二种更难排查（用户看到的是"机器人彻底不理人"），所以空 id 必须**不去重**。
 */
class IncomingDedupTest {

    @Test
    fun `同一个 id 第二次算重复`() {
        IncomingDedup.clear()
        assertFalse(IncomingDedup.seenBefore("msg-1"))
        assertTrue(IncomingDedup.seenBefore("msg-1"))
        // 不同 id 互不影响
        assertFalse(IncomingDedup.seenBefore("msg-2"))
    }

    @Test
    fun `空 id 一律不去重`() {
        IncomingDedup.clear()
        // OneBot 部分实现拿不到 message_id：如果空值也当 key，
        // 第二条无 id 的消息就会被误判重复，永远丢弃
        assertFalse(IncomingDedup.seenBefore(""))
        assertFalse(IncomingDedup.seenBefore(""))
        assertFalse(IncomingDedup.seenBefore("   "))
    }

    @Test
    fun `超出容量按插入顺序淘汰最老的`() {
        IncomingDedup.clear()
        for (i in 0 until 200) {
            assertFalse(IncomingDedup.seenBefore("id-$i"))
        }
        // 容量 200：再塞一条，最老的 id-0 被淘汰，重新出现时不再算重复
        assertFalse(IncomingDedup.seenBefore("id-200"))
        assertFalse(IncomingDedup.seenBefore("id-0"))
        // 刚插入的仍然记得
        assertTrue(IncomingDedup.seenBefore("id-200"))
        IncomingDedup.clear()
    }
}
