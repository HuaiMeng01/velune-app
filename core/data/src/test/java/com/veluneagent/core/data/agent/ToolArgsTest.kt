package com.veluneagent.core.data.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolArgsTest {

    private val schema = """
        {"type":"object","properties":{
          "city":{"type":"string"},
          "days":{"type":"integer","minimum":1,"maximum":7},
          "unit":{"type":"string","enum":["c","f"]}
        },"required":["city"]}
    """.trimIndent()

    @Test
    fun `缺少必填参数被拒绝`() {
        val result = ToolArgs.validate("""{"days":3}""", schema)
        assertFalse(result.ok)
        assertTrue(result.error!!.contains("city"))
    }

    @Test
    fun `类型不匹配被拒绝且不做隐式转换`() {
        val result = ToolArgs.validate("""{"city":"北京","days":"3"}""", schema)
        assertFalse(result.ok)
        assertTrue(result.error!!.contains("integer"))
    }

    @Test
    fun `整数写成年内合法值通过`() {
        val result = ToolArgs.validate("""{"city":"北京","days":3}""", schema)
        assertTrue(result.ok)
    }

    @Test
    fun `超出范围被拒绝`() {
        val result = ToolArgs.validate("""{"city":"北京","days":30}""", schema)
        assertFalse(result.ok)
        assertTrue(result.error!!.contains("不能大于"))
    }

    @Test
    fun `枚举之外的值被拒绝`() {
        val result = ToolArgs.validate("""{"city":"北京","unit":"k"}""", schema)
        assertFalse(result.ok)
        assertTrue(result.error!!.contains("只能是"))
    }

    @Test
    fun `非法 JSON 不抛异常`() {
        val result = ToolArgs.validate("{oops", schema)
        assertFalse(result.ok)
        assertTrue(result.error!!.contains("不是合法 JSON"))
    }

    @Test
    fun `没有 schema 时按原样放行`() {
        val result = ToolArgs.validate("""{"a":1}""", "")
        assertTrue(result.ok)
        assertEquals(1, result.args.optInt("a"))
    }
}

class CalculatorTest {

    @Test
    fun `加减乘除与括号`() {
        assertEquals(10.0, Calculator.evaluate("(2+3)*2").getOrThrow(), 0.0001)
        assertEquals(1.5, Calculator.evaluate("3/2").getOrThrow(), 0.0001)
        assertEquals(2.0, Calculator.evaluate("10%4").getOrThrow(), 0.0001)
        assertEquals(-3.0, Calculator.evaluate("2-5").getOrThrow(), 0.0001)
    }

    @Test
    fun `优先级正确`() {
        assertEquals(14.0, Calculator.evaluate("2+3*4").getOrThrow(), 0.0001)
        assertEquals(20.0, Calculator.evaluate("(2+3)*4").getOrThrow(), 0.0001)
    }

    @Test
    fun `除零返回失败而不是 Infinity`() {
        assertTrue(Calculator.evaluate("1/0").isFailure)
    }

    @Test
    fun `拒绝非算术输入`() {
        assertTrue(Calculator.evaluate("java.lang.Runtime.getRuntime()").isFailure)
        assertTrue(Calculator.evaluate("1+2)").isFailure)
        assertTrue(Calculator.evaluate("").isFailure)
    }

    @Test
    fun `整数结果不带小数点`() {
        assertEquals("12", Calculator.format(12.0))
        assertEquals("0.5", Calculator.format(0.5))
    }
}
