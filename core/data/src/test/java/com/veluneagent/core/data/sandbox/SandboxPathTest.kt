package com.veluneagent.core.data.sandbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SandboxPathTest {

    private fun root(): File =
        Files.createTempDirectory("velune-sandbox-test").toFile().apply { mkdirs() }

    @Test
    fun `普通相对路径落在沙箱内`() {
        val base = root()
        val target = Sandbox.resolveInside(base, "notes/a.md")
        assertTrue(target.canonicalPath.startsWith(base.canonicalPath))
        assertEquals("a.md", target.name)
    }

    @Test
    fun `前导斜杠被当作沙箱根`() {
        val base = root()
        val target = Sandbox.resolveInside(base, "/report.csv")
        assertEquals(File(base, "report.csv").canonicalPath, target.canonicalPath)
    }

    @Test
    fun `父目录穿越无法逃出根目录`() {
        val base = root()
        listOf("../etc/passwd", "a/../../b", "x/../../../y").forEach { path ->
            val resolved = runCatching { Sandbox.resolveInside(base, path) }
            // 要么直接拒绝，要么仍落在沙箱内 —— 两种都可接受，唯一不可接受的是逃出去
            if (resolved.isSuccess) {
                assertTrue(
                    "应当仍在沙箱内：$path",
                    resolved.getOrThrow().canonicalPath.startsWith(base.canonicalPath),
                )
            }
        }
    }

    @Test
    fun `空路径视为沙箱根目录`() {
        val base = root()
        // 行为变更：以前空串直接拒绝，导致 fs_list 不传 path 必然失败。
        // 现在空串（含 "/" 与纯空白）解析为根目录，与工具声明里的「默认根目录」一致。
        assertEquals(base.canonicalPath, Sandbox.resolveInside(base, " ").canonicalPath)
        assertEquals(base.canonicalPath, Sandbox.resolveInside(base, "/").canonicalPath)
        assertEquals(base.canonicalPath, Sandbox.resolveInside(base, "").canonicalPath)
    }

    @Test
    fun `含空字符的路径被拒绝`() {
        val base = root()
        assertTrue(runCatching { Sandbox.resolveInside(base, "a\u0000b") }.isFailure)
    }
}
