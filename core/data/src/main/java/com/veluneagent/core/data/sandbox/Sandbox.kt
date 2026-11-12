package com.veluneagent.core.data.sandbox

import android.content.Context
import java.io.File

/**
 * Agent 的本地文件沙箱。
 *
 * 这是「让 AI 能处理文件」的最小可信实现：所有路径都被限制在应用私有目录下，
 * 任何越界（`..`、绝对路径、软链）直接拒绝。
 *
 * 注意边界：这里是**文件沙箱**，不是 Linux 用户空间。
 * 没有 shell、没有二进制执行、没有 apt —— 想在手机上跑 Ubuntu 需要 proot + rootfs，
 * 属于另一套方案（proot + rootfs 用户空间），当前版本不提供，避免让用户误以为拿到了真终端。
 */
class Sandbox(context: Context) {

    private val root: File = File(context.filesDir, "sandbox").apply { mkdirs() }

    data class Entry(val name: String, val isDir: Boolean, val size: Long)

    fun rootPath(): String = root.absolutePath

    /**
     * 解析用户/模型给的相对路径。
     *
     * 三段防御：拒绝绝对路径 → 用 canonicalPath 消掉 `..` → 再校验前缀。
     * 只做字符串替换（比如把 `..` 换成空串）是挡不住的，`....//` 这类变形能绕过。
     */
    fun resolve(path: String): File = resolveInside(root, path)

    fun list(dir: String = "/"): List<Entry> {
        val target = resolve(dir)
        if (!target.exists()) return emptyList()
        if (!target.isDirectory) return listOf(target.toEntry())
        return (target.listFiles() ?: emptyArray())
            .sortedWith(compareBy({ !it.isDirectory }, { it.name }))
            .take(MaxEntries)
            .map { it.toEntry() }
    }

    /** 沙箱里的一个文件，路径相对于沙箱根，用于列表展示与后续打开。 */
    data class SandboxFile(val path: String, val size: Long, val modifiedAt: Long)

    /**
     * 递归列出所有文件，按修改时间倒序（最近产出的一定在最上面）。
     * 上限 500 条：这里是给人看的列表，不是文件系统索引。
     */
    fun files(): List<SandboxFile> {
        if (!root.exists()) return emptyList()
        return root.walkTopDown()
            .filter { it.isFile }
            .map {
                SandboxFile(
                    path = it.relativeTo(root).invariantSeparatorsPath,
                    size = it.length(),
                    modifiedAt = it.lastModified(),
                )
            }
            // 必须先排序再截断：原先 take(500) 在 sortedByDescending 之前，
            // 取的是目录遍历顺序里的前 500 个，文件一多，刚生成的那个反而看不到。
            .sortedByDescending { it.modifiedAt }
            .take(500)
            .toList()
    }

    fun read(path: String, maxChars: Int): String {
        val target = resolve(path)
        require(target.exists()) { "文件不存在：$path" }
        require(target.isFile) { "不是文件：$path" }
        require(target.length() <= MaxReadBytes) {
            "文件过大（${target.length() / 1024}KB），上限 ${MaxReadBytes / 1024}KB"
        }
        val text = target.readText()
        return if (text.length > maxChars) {
            text.take(maxChars) + "\n…（已截断，共 ${text.length} 字）"
        } else {
            text
        }
    }

    fun write(path: String, content: String, append: Boolean): Long {
        val target = resolve(path)
        require(content.toByteArray().size <= MaxWriteBytes) {
            "内容过大，单次写入上限 ${MaxWriteBytes / 1024}KB"
        }
        target.parentFile?.mkdirs()
        if (append) target.appendText(content) else target.writeText(content)
        return target.length()
    }

    fun delete(path: String): Boolean {
        val target = resolve(path)
        if (!target.exists()) return false
        return target.deleteRecursively()
    }

    companion object {
        /**
         * 纯函数版本，便于单测。
         * 三段防御：拒绝绝对路径 → canonicalPath 消掉 `..` → 再校验前缀。
         * 只做字符串替换挡不住 `....//` 这类变形。
         */
        internal fun resolveInside(root: File, path: String): File {
            val cleaned = path.trim().trimStart('/')
            require(!cleaned.contains('\u0000')) { "路径包含非法字符" }
            require(!File(cleaned).isAbsolute) { "只接受沙箱内的相对路径" }

            // 空串（含 "/"）表示沙箱根目录。fs_list 的默认 path 就是根，
            // 之前这里 require 非空，等于「不传 path 必然失败」——模型照工具说明
            // 调用就会连挂三次，直接触发连续失败熔断。
            val target = if (cleaned.isEmpty()) root else File(root, cleaned)
            val canonicalRoot = root.canonicalPath + File.separator
            val canonicalTarget = target.canonicalPath
            require(
                canonicalTarget.startsWith(canonicalRoot) ||
                    canonicalTarget == root.canonicalPath,
            ) { "路径越界：只能访问沙箱目录" }
            return target
        }

        private const val MaxEntries = 200
        private const val MaxReadBytes = 256 * 1024L
        private const val MaxWriteBytes = 512 * 1024
    }

    private fun File.toEntry() = Entry(
        name = name,
        isDir = isDirectory,
        size = if (isFile) length() else 0,
    )
}
