package com.veluneagent.core.data.linux

import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream

/**
 * 最小 tar.gz 解压器。
 *
 * 为什么不引 commons-compress：只为一个 200 行的格式引入第三方 tar 实现，
 * 会把"能不能解压"这件事的责任转移到一个不需要我们调试的库上；而 rootfs 解压失败的
 * 排查成本很高（权限、symlink、硬链接）。自己写可控、可测。
 *
 * 支持：普通文件、目录、符号链接、硬链接、执行权限位（`/bin/sh` 必须可执行）。
 * 忽略：uid/gid/时间戳（Android 沙箱里没有意义）、设备文件（rootfs 里用不到）。
 */
internal object TarGz {

    private const val BlockSize = 512

    /**
     * @param onProgress 已处理字节数 / 压缩包总字节数（用于进度条，不追求精确）
     */
    fun extract(archive: File, target: File, onProgress: (Long, Long) -> Unit = { _, _ -> }) {
        val total = archive.length()
        var processed = 0L
        val longNames = HashMap<String, String>()

        GZIPInputStream(archive.inputStream().buffered(64 * 1024)).use { gzip ->
            val header = ByteArray(BlockSize)
            while (true) {
                if (!readFully(gzip, header)) break
                if (header.all { it == 0.toByte() }) break

                val name = readString(header, 0, 100)
                val prefix = readString(header, 345, 155)
                val fullName = if (prefix.isNotEmpty()) "$prefix/$name" else name
                val size = readOctal(header, 124, 12)
                val mode = readOctal(header, 100, 8).toInt()
                val type = header[156].toInt().toChar()

                // GNU 长文件名：上一个条目的内容里存着下一个条目的真实名字
                if (type == 'L') {
                    val longName = readEntryBytes(gzip, size).toString(Charsets.UTF_8).trimEnd('\u0000')
                    longNames["pending"] = longName
                    processed += BlockSize + padded(size)
                    onProgress(processed, total)
                    continue
                }

                val resolvedName = sanitize(longNames.remove("pending") ?: fullName)
                val outFile = File(target, resolvedName)

                when (type) {
                    '5' -> outFile.mkdirs()
                    '2' -> {
                        val link = readString(header, 157, 100)
                        outFile.parentFile?.mkdirs()
                        outFile.delete()
                        runCatching { java.nio.file.Files.createSymbolicLink(outFile.toPath(), File(link).toPath()) }
                        // 沙箱不允许建符号链接时退化为空文件，至少不让后续条目全部失败
                            .onFailure { runCatching { outFile.createNewFile() } }
                    }

                    '1' -> {
                        val linkName = sanitize(readString(header, 157, 100))
                        val source = File(target, linkName)
                        outFile.parentFile?.mkdirs()
                        outFile.delete()
                        runCatching { java.nio.file.Files.createLink(outFile.toPath(), source.toPath()) }
                            .onFailure { source.copyTo(outFile, overwrite = true) }
                    }

                    '0', '\u0000' -> {
                        outFile.parentFile?.mkdirs()
                        if (size > 0) {
                            FileOutputStream(outFile).use { output ->
                                copy(gzip, output, size)
                            }
                        } else {
                            outFile.createNewFile()
                        }
                        applyMode(outFile, mode)
                    }

                    else -> {
                        // 设备文件 / FIFO：跳过内容但不破坏流位置
                        if (size > 0) skip(gzip, size)
                    }
                }
                if (size > 0 && type != '0' && type != '\u0000') skip(gzip, size)
                processed += BlockSize + padded(size)
                onProgress(processed, total)
            }
        }
    }

    private fun sanitize(raw: String): String {
        val cleaned = raw.trim().removePrefix("./").removePrefix("/")
        // 目录穿越必须挡掉：rootfs 里的 `../` 一旦被跟随就会写到应用目录之外
        return cleaned.split('/').filter { it.isNotEmpty() && it != "." && it != ".." }.joinToString("/")
    }

    private fun padded(size: Long): Long = if (size % BlockSize == 0L) size else size + (BlockSize - size % BlockSize)

    private fun applyMode(file: File, mode: Int) {
        // 只关心可执行位：rootfs 里 /bin/sh 没有 x 位等于整个环境不可用
        val executable = mode and 0b001_000_000 != 0
        if (executable) file.setExecutable(true, false)
        file.setReadable(true, false)
    }

    private fun readFully(input: java.io.InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read < 0) return false
            offset += read
        }
        return true
    }

    private fun readString(buffer: ByteArray, offset: Int, length: Int): String {
        var end = offset
        val limit = offset + length
        while (end < limit && buffer[end] != 0.toByte()) end++
        return String(buffer, offset, end - offset, Charsets.UTF_8)
    }

    private fun readOctal(buffer: ByteArray, offset: Int, length: Int): Long {
        val text = readString(buffer, offset, length).trim().trim('\u0000')
        return text.toLongOrNull(8) ?: 0L
    }

    private fun readEntryBytes(input: java.io.InputStream, size: Long): ByteArray {
        val data = ByteArray(size.toInt())
        var read = 0
        while (read < size) {
            val n = input.read(data, read, (size - read).toInt())
            if (n < 0) break
            read += n
        }
        skip(input, padded(size) - size)
        return data
    }

    private fun copy(input: java.io.InputStream, output: java.io.OutputStream, size: Long) {
        val buffer = ByteArray(64 * 1024)
        var remaining = size
        while (remaining > 0) {
            val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (n < 0) break
            output.write(buffer, 0, n)
            remaining -= n
        }
        skip(input, padded(size) - size)
    }

    private fun skip(input: java.io.InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() < 0) return
                remaining -= 1
            } else {
                remaining -= skipped
            }
        }
    }
}
