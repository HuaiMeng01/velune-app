package com.veluneagent.core.data.linux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

/**
 * tar.gz 解压是"下载 Linux 环境"唯一的格式关口：解错一个字节，
 * 用户拿到的是一个装完却起不来的系统，而且现场很难复现。
 * 这里用**手写的最小 tar** 把边界钉死（普通文件 + 目录 + 多文件偏移）。
 */
class TarGzTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun header(name: String, size: Int, type: Char, mode: Int = 0b110_100_100): ByteArray {
        val h = ByteArray(512)
        fun put(offset: Int, text: String, length: Int) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            System.arraycopy(bytes, 0, h, offset, minOf(bytes.size, length))
        }
        put(0, name, 100)
        put(100, mode.toString(8).padStart(7, '0') + "\u0000", 8)
        put(108, "0000000\u0000", 8)
        put(116, "0000000\u0000", 8)
        put(124, size.toString(8).padStart(11, '0') + "\u0000", 12)
        put(136, "00000000000\u0000", 12)
        // 校验和字段先填空格，算完再写回
        for (i in 148 until 156) h[i] = ' '.code.toByte()
        h[156] = type.code.toByte()
        put(257, "ustar", 6)
        put(263, "00", 2)
        val sum = h.sumOf { it.toInt() and 0xFF }
        put(148, sum.toString(8).padStart(6, '0') + "\u0000 ", 8)
        return h
    }

    private fun tar(vararg entries: Triple<String, ByteArray, Char>): ByteArray {
        val out = ByteArrayOutputStream()
        entries.forEach { (name, content, type) ->
            out.write(header(name, content.size, type))
            if (content.isNotEmpty()) {
                out.write(content)
                val pad = (512 - content.size % 512) % 512
                out.write(ByteArray(pad))
            }
        }
        out.write(ByteArray(1024))
        return out.toByteArray()
    }

    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { bos ->
        GZIPOutputStream(bos).use { it.write(bytes) }
    }.toByteArray()

    @Test
    fun `解压出文件内容与目录`() {
        val archive = File(temp.root, "rootfs.tar.gz")
        archive.writeBytes(
            gzip(
                tar(
                    Triple("./bin/", ByteArray(0), '5'),
                    Triple("./bin/sh", "echo hi".toByteArray(), '0'),
                    Triple("./etc/hostname", "velune".toByteArray(), '0'),
                ),
            ),
        )
        val target = File(temp.root, "out").apply { mkdirs() }

        TarGz.extract(archive, target)

        assertTrue(File(target, "bin").isDirectory)
        assertEquals("echo hi", File(target, "bin/sh").readText())
        assertEquals("velune", File(target, "etc/hostname").readText())
    }

    @Test
    fun `目录穿越被拦截`() {
        val archive = File(temp.root, "evil.tar.gz")
        archive.writeBytes(
            gzip(tar(Triple("../../escape.txt", "bad".toByteArray(), '0'))),
        )
        val target = File(temp.root, "safe").apply { mkdirs() }
        TarGz.extract(archive, target)

        // 清理后的路径落到 target 内部，绝不能写到上级目录
        assertTrue(File(target, "escape.txt").isFile)
        assertTrue(!File(temp.root.parentFile, "escape.txt").exists())
    }

    @Test
    fun `进度回调走到 100%`() {
        val archive = File(temp.root, "p.tar.gz")
        archive.writeBytes(gzip(tar(Triple("./a.txt", "1234".toByteArray(), '0'))))
        val target = File(temp.root, "p").apply { mkdirs() }
        var lastDone = 0L
        var total = 0L
        TarGz.extract(archive, target) { done, all -> lastDone = done; total = all }

        assertTrue(lastDone > 0)
        assertEquals(archive.length(), total)
    }
}
