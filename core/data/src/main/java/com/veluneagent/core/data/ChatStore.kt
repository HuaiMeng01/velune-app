package com.veluneagent.core.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** 新建会话的占位标题；第一条用户消息写入时会被自动改名。 */
const val DefaultConversationTitle = "新对话"

/** 助手消息在等待首个 token 时的占位正文。落库后用它判断「这条消息还没写完」。 */
const val AssistantPlaceholder = "…"

/** 进程被杀后重启，占位消息会被收敛成这句话，避免永久显示「正在输入」。 */
const val InterruptedMarker = "（已中断）"

/** 自动标题最多保留的字数。 */
private const val AutoTitleMaxLength = 24

enum class MessageRole(val wire: String) {
    User("user"),
    Assistant("assistant"),
    ;

    companion object {
        fun fromWire(value: String): MessageRole = entries.firstOrNull { it.wire == value } ?: Assistant
    }
}

/**
 * 会话列表行。preview 与 messageCount 由同一条 SQL 带出，
 * 避免列表滚动时对每个会话再查一次库。
 */
data class Conversation(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean,
    val preview: String,
    val messageCount: Int,
    /** 未读的助手回复条数。由 `last_read_at` 与消息时间算出，不单独维护计数。 */
    val unread: Int = 0,
    /** 已归档的会话不出现在主列表，进「归档箱」才看得到。 */
    val archived: Boolean = false,
)

/** 会话列表排序。置顶永远在最前，这里只决定其余部分的顺序。 */
enum class ConversationSort {
    /** 最近有动静的在前（默认） */
    Recent,

    /** 按创建时间倒序 —— 找"我上周新建的那个"用这个 */
    Created,

    /** 按标题字母序 —— 会话多了按名字找更快 */
    Title,
    ;

    companion object {
        fun fromKey(key: String?): ConversationSort =
            entries.firstOrNull { it.name == key } ?: Recent
    }
}

data class StoredMessage(
    val id: Long,
    val conversationId: Long,
    val role: MessageRole,
    val text: String,
    val createdAt: Long,
    val attachment: ChatAttachment? = null,
    val model: String = "",
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    /** 思考过程（reasoning_content）。只用于展示，不回传给模型。 */
    val reasoning: String = "",
    /** 思考耗时（毫秒）。累计相邻 reasoning 增量的间隔，不含输出正文的时间。 */
    val reasoningMs: Long = 0,
    /**
     * 本轮工具调用过程（JSON，由 UI 层序列化）。
     *
     * 存成字符串而不是结构化列：工具卡片字段随 UI 演进（新增状态、文件路径…），
     * core 层不该跟着改表；它只需要"原样存取"。
     */
    val steps: String = "",
    /** 引用回复：被引用消息的 id（原消息可能已删除，所以同时存一段预览文本） */
    val replyToId: Long? = null,
    val replyPreview: String = "",
    /**
     * 消息来源标签（v11）。
     *
     * 只有远程入口（QQ）写入的用户消息才有值，例如「QQ 私聊 · 张三」。
     * App 内自己发的消息保持空串 —— 界面上「你自己说的话」不需要再标一遍来源，
     * 而模型拼历史时需要靠它区分「谁在说」。
     */
    val sender: String = "",
)

/**
 * 交给模型的历史正文。
 *
 * 带来源的消息加前缀（`[QQ 私聊 · 张三] 正文`），让模型知道这句话是谁说的；
 * **sender 为空时必须逐字返回原文** —— App 内的历史回传逻辑依赖这一点，
 * 多一个空格都可能让既有断言失效。
 *
 * 前缀格式不在这里拼，统一交给 [senderPrefixed]。
 */
fun StoredMessage.forModelText(): String =
    if (role == MessageRole.User) senderPrefixed(sender, text) else text

/**
 * 来源标签的唯一格式出处。
 *
 * 两个入口都调它，避免「改一处漏一处」：`StoredMessage.forModelText()`
 * （QQ 侧读库历史）与 `Bubble.toChatMessage()`（UI 侧历史/重新生成）。
 * sender 为空时逐字返回原文——App 内自己发的消息不能被改动一个字符。
 */
fun senderPrefixed(sender: String, text: String): String =
    if (sender.isBlank()) text else "[$sender] $text"

/** 本地用量汇总。真实来自服务端回报或本地估算，不再是一个装饰数字。 */
data class UsageSummary(
    val promptTokens: Long,
    val completionTokens: Long,
    val todayTokens: Long,
    val replies: Int,
) {
    val totalTokens: Long get() = promptTokens + completionTokens
}

/** 本地库的真实规模，供设置页展示，不再写死一个假数字。 */
data class StoreStats(
    val conversations: Long,
    val messages: Long,
    val bytes: Long,
) {
    val sizeLabel: String
        get() = when {
            bytes <= 0 -> "0 KB"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024f / 1024f)
        }
}

/**
 * 本地消息库。
 *
 * 设计取舍：这里没有引入 Room。KSP 处理器版本必须与 Kotlin 编译器版本严格配对，
 * 而本项目当前环境是 Kotlin 2.4.10 —— 为一层薄薄的 CRUD 承担这个版本耦合不划算。
 * 直接用手写 SQLite，表结构与查询都集中在这个文件里，后续要换 Room 也只需替换实现。
 */
class ChatStore(context: Context) {

    private val helper = VeluneDb.of(context)

    /**
     * 两条独立的失效信号。
     *
     * 流式输出时每秒要写十几次正文，如果和会话列表共用一条信号，
     * 消息页会跟着做十几次带相关子查询的列表扫描 —— 白烧 CPU 与电量。
     * 正文变化只通知「消息流」，列表只在增删改会话时重查。
     */
    private val listRevision = MutableStateFlow(0L)
    private val messageRevision = MutableStateFlow(0L)

    fun observeConversations(): Flow<List<Conversation>> =
        // 排序方式变了要重查：SQL 里做排序比在内存排序更省，
        // 而且标题排序需要 COLLATE NOCASE（中文按字节序，英文按字母）
        combine(listRevision, ChatPrefs.sortMode) { _, sort -> sort }
            .map { sort ->
                val rows = queryConversations(sort)
                // 首帧查完才置 true：界面靠它区分"还没读出来"和"真的没有会话"
                _conversationsLoaded.value = true
                rows
            }
            .flowOn(Dispatchers.IO)
            .flowOn(Dispatchers.IO)

    private val _conversationsLoaded = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** 首次查询是否完成。用于会话列表的骨架态，避免冷启动闪一下"还没有会话"。 */
    val conversationsLoaded: kotlinx.coroutines.flow.StateFlow<Boolean> = _conversationsLoaded

    /** 一次性读取（QQ 机器人这类后台调用不需要观察流）。 */
    suspend fun messages(conversationId: Long, limit: Int = 40): List<StoredMessage> =
        withContext(Dispatchers.IO) {
            val all = queryMessages(conversationId)
            if (all.size > limit) all.takeLast(limit) else all
        }

    /**
     * 会话是否存在。
     *
     * QQ 机器人绑定会话时用它做存在性判断。**不能用「有没有消息」来判断**：
     * 用户刚建的空会话一条消息都没有，会被误判成「已删除」而静默回落到新建的会话，
     * 绑定看起来保存成功却永远不生效。
     */
    suspend fun conversationExists(id: Long): Boolean = withContext(Dispatchers.IO) {
        if (id <= 0L) return@withContext false
        helper.readableDatabase.rawQuery(
            "SELECT 1 FROM conversations WHERE id = ?",
            arrayOf(id.toString()),
        ).use { cursor -> cursor.moveToFirst() }
    }

    fun observeMessages(conversationId: Long): Flow<List<StoredMessage>> =
        messageRevision.map { queryMessages(conversationId) }.flowOn(Dispatchers.IO)

    /**
     * 标记会话已读到当前时刻。
     *
     * 未读数是查询时用 `last_read_at` 与消息时间算出来的，所以这里只推进时间戳，
     * 不去改 messages 的任何列 —— 否则「已读」就变成了一个要到处维护的状态。
     */
    suspend fun markRead(conversationId: Long) = withContext(Dispatchers.IO) {
        helper.writableDatabase.update(
            "conversations",
            ContentValues().apply { put("last_read_at", System.currentTimeMillis()) },
            "id = ?",
            arrayOf(conversationId.toString()),
        )
        bumpList()
    }

    init {
        // 上次进程被系统杀掉时，最后一条助手消息可能停在占位符上。
        // 启动时统一收敛一次，否则用户每次进来都看到「正在输入」，而请求早已不存在。
        Thread {
            runCatching {
                helper.writableDatabase.execSQL(
                    "UPDATE messages SET text = ? WHERE role = ? AND text = ?",
                    arrayOf(InterruptedMarker, MessageRole.Assistant.wire, AssistantPlaceholder),
                )
                bumpMessages()
            }
        }.apply {
            name = "velune-heal"
            isDaemon = true
        }.start()
    }

    suspend fun createConversation(title: String = DefaultConversationTitle): Long =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val values = ContentValues().apply {
                put("title", title)
                put("created_at", now)
                put("updated_at", now)
                put("pinned", 0)
            }
            val id = helper.writableDatabase.insertOrThrow("conversations", null, values)
            bumpList(now)
            id
        }

    /** 会话绑定的角色（提示词 id）。null = 没有专属角色，用全局唯一生效的那条人设。 */
    suspend fun promptIdOf(conversationId: Long): Long? = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT prompt_id FROM conversations WHERE id = ?",
            arrayOf(conversationId.toString()),
        ).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }

    suspend fun setConversationPrompt(conversationId: Long, promptId: Long?) =
        withContext(Dispatchers.IO) {
            helper.writableDatabase.update(
                "conversations",
                ContentValues().apply { put("prompt_id", promptId) },
                "id = ?",
                arrayOf(conversationId.toString()),
            )
            bumpList(System.currentTimeMillis())
        }

    suspend fun renameConversation(id: Long, title: String) = withContext(Dispatchers.IO) {        val normalized = title.trim().ifEmpty { DefaultConversationTitle }
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("title", normalized)
            put("updated_at", now)
        }
        helper.writableDatabase.update("conversations", values, "id = ?", arrayOf(id.toString()))
        bumpList(now)
    }

    suspend fun deleteConversation(id: Long) = withContext(Dispatchers.IO) {
        // messages 上声明了 ON DELETE CASCADE，且 onConfigure 打开了外键约束
        helper.writableDatabase.delete("conversations", "id = ?", arrayOf(id.toString()))
        bumpList(System.currentTimeMillis())
    }

    /** 删光所有会话，同时级联清掉消息。设置里的「清空全部对话」用它。 */
    /**
     * 清洗历史上被 `null` 污染的助手消息。
     *
     * 曾经的解析 bug 会把工具调用帧里的 `content: null` 当成字符串 "null" 写进正文，
     * 结果是满屏 nullnullnull。解析已修，但已经落库的内容不会自己消失，所以这里做一次性修复。
     *
     * 只动「连续 4 个以上 null」的助手消息：正常正文里不可能出现这种串，
     * 用户自己发的消息一概不碰。
     */
    suspend fun cleanupNullPollutedMessages(): Int = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        // REPLACE 一次只去掉一层，连续 11 个 null 要跑几遍才干净
        val sql = """
            UPDATE messages
            SET text = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                text, 'nullnullnullnull', ''), 'nullnullnullnull', ''), 'nullnullnullnull', ''),
                'nullnullnullnull', ''), 'nullnullnullnull', '')
            WHERE role = ? AND text LIKE '%nullnullnullnull%'
        """.trimIndent()
        val affected = runCatching {
            db.execSQL(sql, arrayOf(MessageRole.Assistant.wire))
            db.rawQuery(
                "SELECT COUNT(*) FROM messages WHERE text LIKE '%nullnullnullnull%'",
                null,
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
        }.getOrDefault(0)

        // 清完可能留下空正文：这类消息对用户没有价值，直接删掉
        db.execSQL(
            "DELETE FROM messages WHERE role = ? AND TRIM(text) = ''",
            arrayOf(MessageRole.Assistant.wire),
        )
        bumpList(System.currentTimeMillis())
        bumpMessages()
        affected
    }

    suspend fun clearAllConversations() = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete("conversations", null, null)
        bumpList(System.currentTimeMillis())
        bumpMessages()
    }

    suspend fun setPinned(id: Long, pinned: Boolean) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put("pinned", if (pinned) 1 else 0) }
        helper.writableDatabase.update("conversations", values, "id = ?", arrayOf(id.toString()))
        bumpList(System.currentTimeMillis())
    }

    /** 归档 / 取消归档。归档不删消息，只是从主列表移走。 */
    suspend fun setArchived(ids: Collection<Long>, archived: Boolean) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val db = helper.writableDatabase
        val values = ContentValues().apply { put("archived", if (archived) 1 else 0) }
        db.beginTransaction()
        try {
            ids.forEach { id ->
                db.update("conversations", values, "id = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        bumpList(System.currentTimeMillis())
    }

    suspend fun setArchived(id: Long, archived: Boolean) = setArchived(listOf(id), archived)

    /** 批量删除。单个事务，避免删到一半失败留下半套数据。 */
    suspend fun deleteConversations(ids: Collection<Long>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            ids.forEach { id ->
                db.delete("conversations", "id = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        bumpList(System.currentTimeMillis())
        bumpMessages()
    }

    suspend fun appendMessage(
        conversationId: Long,
        role: MessageRole,
        text: String,
        attachment: ChatAttachment? = null,
        model: String = "",
        replyToId: Long? = null,
        replyPreview: String = "",
        /** 来源标签（如「QQ 私聊 · 张三」）。App 内发送保持空串。 */
        sender: String = "",
    ): Long = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        val now = System.currentTimeMillis()
        var messageId = 0L

        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("conversation_id", conversationId)
                put("role", role.wire)
                put("text", text)
                put("created_at", now)
                put("attachment", attachment?.toJson())
                put("model", model)
                if (replyToId != null) put("reply_to_id", replyToId) else putNull("reply_to_id")
                put("reply_preview", replyPreview)
                put("sender", sender)
            }
            messageId = db.insertOrThrow("messages", null, values)

            db.update(
                "conversations",
                ContentValues().apply { put("updated_at", now) },
                "id = ?",
                arrayOf(conversationId.toString()),
            )

            // QQ 发来的消息不参与自动改标题：用户给会话起的名字/角色名会被陌生人一句话顶掉
            if (role == MessageRole.User && sender.isBlank()) {
                autoTitleIfFirstMessage(db, conversationId, text, now)
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        bumpList(now)
        bumpMessages(now)
        messageId
    }

    /**
     * 「编辑并重发」的原子操作：删掉 fromMessageId 及其之后的全部消息，再插入编辑后的正文。
     *
     * 拆成两次调用（先 deleteMessagesFrom 再 appendMessage）时，中间崩溃会同时丢掉
     * 原文和新文 —— 用户那条消息就彻底没了，且没有任何恢复路径。
     */
    suspend fun replaceFrom(
        conversationId: Long,
        fromMessageId: Long,
        text: String,
        attachment: ChatAttachment? = null,
        replyToId: Long? = null,
        replyPreview: String = "",
    ): Long = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        val now = System.currentTimeMillis()
        var messageId = 0L

        db.beginTransaction()
        try {
            db.delete(
                "messages",
                "conversation_id = ? AND id >= ?",
                arrayOf(conversationId.toString(), fromMessageId.toString()),
            )
            val values = ContentValues().apply {
                put("conversation_id", conversationId)
                put("role", MessageRole.User.wire)
                put("text", text)
                put("created_at", now)
                put("attachment", attachment?.toJson())
                put("model", "")
                if (replyToId != null) put("reply_to_id", replyToId) else putNull("reply_to_id")
                put("reply_preview", replyPreview)
                put("sender", "")
            }
            messageId = db.insertOrThrow("messages", null, values)

            db.update(
                "conversations",
                ContentValues().apply { put("updated_at", now) },
                "id = ?",
                arrayOf(conversationId.toString()),
            )
            // 编辑的可能是会话第一条：删完之后它就是新的第一条，标题要跟着变
            autoTitleIfFirstMessage(db, conversationId, text, now)

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        bumpList(now)
        bumpMessages(now)
        messageId
    }

    /**
     * 落盘本轮工具调用过程。
     *
     * 为什么必须存：卡片原本只活在内存的 streamParts 里，流一结束（或页面重建）
     * 就整段消失，用户会以为"刚才调用的记录被吞了"。
     */
    suspend fun updateMessageSteps(messageId: Long, stepsJson: String) =
        withContext(Dispatchers.IO) {
            helper.writableDatabase.update(
                "messages",
                ContentValues().apply { put("steps", stepsJson) },
                "id = ?",
                arrayOf(messageId.toString()),
            )
            bumpMessages(System.currentTimeMillis())
        }

    /** 流式输出时反复改写同一条消息。只通知消息流，不惊动会话列表。 */
    suspend fun updateMessageText(messageId: Long, text: String) = withContext(Dispatchers.IO) {        val values = ContentValues().apply { put("text", text) }
        helper.writableDatabase.update("messages", values, "id = ?", arrayOf(messageId.toString()))
        bumpMessages(System.currentTimeMillis())
    }

    /**
     * 删掉某条消息之后的全部消息（含该条之后、不含该条本身）。
     * 「重新生成」先截断再重发，避免库里留下两套并列的回复。
     */
    suspend fun deleteMessagesAfter(conversationId: Long, messageId: Long) =
        withContext(Dispatchers.IO) {
            helper.writableDatabase.delete(
                "messages",
                "conversation_id = ? AND id > ?",
                arrayOf(conversationId.toString(), messageId.toString()),
            )
            bumpList(System.currentTimeMillis())
            bumpMessages(System.currentTimeMillis())
        }

    /** 连同这条消息一起删掉（含）之后的所有消息。「编辑并重发」用。 */
    suspend fun deleteMessagesFrom(conversationId: Long, messageId: Long) =
        withContext(Dispatchers.IO) {
            helper.writableDatabase.delete(
                "messages",
                "conversation_id = ? AND id >= ?",
                arrayOf(conversationId.toString(), messageId.toString()),
            )
            bumpList(System.currentTimeMillis())
            bumpMessages(System.currentTimeMillis())
        }

    /**
     * 会话内消息条数（含占位/中断行）。
     *
     * 用于算「摘要覆盖之外还剩多少条要带原文」：`total - coveredCount` 才是没被摘要吸收的部分。
     * 含占位行是有意的 —— 这里只要一个条数做窗口大小，精确过滤由随后的 `messages()` 负责。
     */
    suspend fun messageCount(conversationId: Long): Int = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM messages WHERE conversation_id = ?",
            arrayOf(conversationId.toString()),
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    /** 整个会话的统计，供「数据与存储」页展示。一次查询，不在 UI 层累加。 */    suspend fun stats(): StoreStats = withContext(Dispatchers.IO) {
        val conversations = helper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM conversations",
            null,
        ).use { if (it.moveToFirst()) it.getLong(0) else 0L }

        val messages = helper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM messages",
            null,
        ).use { if (it.moveToFirst()) it.getLong(0) else 0L }

        val bytes = runCatching { helper.readableDatabase.path?.let { java.io.File(it).length() } }
            .getOrNull() ?: 0L

        StoreStats(conversations = conversations, messages = messages, bytes = bytes)
    }

    /** 思考过程与正文分开写：思考不需要每帧落盘，用户展开时能看到完整内容即可。 */
    suspend fun updateMessageReasoning(
        messageId: Long,
        reasoning: String,
        durationMs: Long = 0,
    ) =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("reasoning", reasoning)
                put("reasoning_ms", durationMs.coerceAtLeast(0))
            }
            helper.writableDatabase.update(
                "messages",
                values,
                "id = ?",
                arrayOf(messageId.toString()),
            )
            // 思考过程不参与列表预览，不 bump 消息流，避免无谓重查
        }

    /** 写入这条回复的真实/估算用量。流式结束后调用一次，不参与每帧刷盘。 */
    suspend fun updateMessageUsage(messageId: Long, promptTokens: Int, completionTokens: Int, model: String) =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("prompt_tokens", promptTokens)
                put("completion_tokens", completionTokens)
                if (model.isNotEmpty()) put("model", model)
            }
            helper.writableDatabase.update("messages", values, "id = ?", arrayOf(messageId.toString()))
            bumpMessages(System.currentTimeMillis())
        }

    /**
     * 全文搜索：标题、预览之外还扫消息正文。
     * 命中正文时，预览换成命中的那条消息，用户才知道「为什么这个会话被搜出来」。
     */
    suspend fun searchConversations(query: String): List<Conversation> = withContext(Dispatchers.IO) {
        val keyword = query.trim()
        if (keyword.isEmpty()) return@withContext queryConversations()
        val like = "%" + keyword.replace("%", "\\%").replace("_", "\\_") + "%"
        val sql = """
            SELECT c.id, c.title, c.created_at, c.updated_at, c.pinned,
                   COALESCE((
                       SELECT m.text FROM messages m
                       WHERE m.conversation_id = c.id AND m.text LIKE ? ESCAPE '\'
                       ORDER BY m.created_at DESC, m.id DESC
                       LIMIT 1
                   ), (
                       SELECT m.text FROM messages m
                       WHERE m.conversation_id = c.id AND m.text <> ?
                       ORDER BY m.created_at DESC, m.id DESC
                       LIMIT 1
                   ), '') AS preview,
                   (
                       SELECT COUNT(*) FROM messages m WHERE m.conversation_id = c.id
                   ) AS message_count
            FROM conversations c
            WHERE c.title LIKE ? ESCAPE '\'
               OR EXISTS (
                   SELECT 1 FROM messages m
                   WHERE m.conversation_id = c.id AND m.text LIKE ? ESCAPE '\'
               )
            ORDER BY c.pinned DESC, c.updated_at DESC
        """.trimIndent()

        helper.readableDatabase.rawQuery(
            sql,
            arrayOf(like, AssistantPlaceholder, like, like),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Conversation(
                            id = cursor.getLong(0),
                            title = cursor.getString(1),
                            createdAt = cursor.getLong(2),
                            updatedAt = cursor.getLong(3),
                            pinned = cursor.getInt(4) != 0,
                            preview = cursor.getString(5),
                            messageCount = cursor.getInt(6),
                        ),
                    )
                }
            }
        }
    }

    /** 本地用量统计：总量 + 今日，全部来自 messages 表，不在 UI 层累加。 */
    suspend fun usageSummary(): UsageSummary = withContext(Dispatchers.IO) {
        val startOfDay = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        helper.readableDatabase.rawQuery(
            """
            SELECT COALESCE(SUM(prompt_tokens), 0),
                   COALESCE(SUM(completion_tokens), 0),
                   COALESCE(SUM(CASE WHEN created_at >= ? THEN prompt_tokens + completion_tokens ELSE 0 END), 0),
                   COUNT(*)
            FROM messages
            WHERE role = ?
            """.trimIndent(),
            arrayOf(startOfDay.toString(), MessageRole.Assistant.wire),
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                UsageSummary(
                    promptTokens = cursor.getLong(0),
                    completionTokens = cursor.getLong(1),
                    todayTokens = cursor.getLong(2),
                    replies = cursor.getInt(3),
                )
            } else {
                UsageSummary(0, 0, 0, 0)
            }
        }
    }

    private fun bumpList(at: Long = System.currentTimeMillis()) {
        // 用时间戳而非自增计数：同毫秒内的连续写入也不会被 StateFlow 去重丢掉
        listRevision.value = nextRevision(listRevision.value, at)
    }

    private fun bumpMessages(at: Long = System.currentTimeMillis()) {
        messageRevision.value = nextRevision(messageRevision.value, at)
    }

    private fun nextRevision(current: Long, at: Long): Long = if (at > current) at else current + 1

    /**
     * 会话还叫「新对话」且这是第一条用户消息时，用消息首行做标题。
     * 放在写入事务里，避免新建会话后闪一下「新对话」再跳标题。
     */
    private fun autoTitleIfFirstMessage(
        db: SQLiteDatabase,
        conversationId: Long,
        text: String,
        now: Long,
    ) {
        val currentTitle = db.rawQuery(
            "SELECT title FROM conversations WHERE id = ?",
            arrayOf(conversationId.toString()),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: return

        if (currentTitle != DefaultConversationTitle) return

        val userMessageCount = db.rawQuery(
            "SELECT COUNT(*) FROM messages WHERE conversation_id = ? AND role = ?",
            arrayOf(conversationId.toString(), MessageRole.User.wire),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

        if (userMessageCount != 1) return

        val derived = text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?.take(AutoTitleMaxLength)
            ?.trim()
            ?.ifEmpty { null }
            ?: return

        db.update(
            "conversations",
            ContentValues().apply {
                put("title", derived)
                put("updated_at", now)
            },
            "id = ?",
            arrayOf(conversationId.toString()),
        )
    }

    private fun queryConversations(sort: ConversationSort = ConversationSort.Recent): List<Conversation> {
        // 排序片段只从枚举取值，不接受外部字符串拼接
        val order = when (sort) {
            ConversationSort.Recent -> "c.pinned DESC, c.updated_at DESC"
            ConversationSort.Created -> "c.pinned DESC, c.created_at DESC"
            // 标题排序要 NOCASE：否则「apple」会排在「Banana」后面
            ConversationSort.Title -> "c.pinned DESC, c.title COLLATE NOCASE ASC"
        }
        val sql = """
            SELECT c.id, c.title, c.created_at, c.updated_at, c.pinned,
                   COALESCE((
                       SELECT m.text FROM messages m
                       WHERE m.conversation_id = c.id AND m.text <> ?
                       ORDER BY m.created_at DESC, m.id DESC
                       LIMIT 1
                   ), '') AS preview,
                   (
                       SELECT COUNT(*) FROM messages m WHERE m.conversation_id = c.id
                   ) AS message_count,
                   (
                       SELECT COUNT(*) FROM messages m
                       WHERE m.conversation_id = c.id
                         AND m.role = ?
                         AND m.created_at > c.last_read_at
                   ) AS unread_count,
                   c.archived
            FROM conversations c
            ORDER BY $order
        """.trimIndent()

        // 占位符不进预览：否则刚发完消息、首个 token 还没到时，列表会闪一下「…」
        return helper.readableDatabase.rawQuery(
            sql,
            arrayOf(AssistantPlaceholder, MessageRole.Assistant.wire),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Conversation(
                            id = cursor.getLong(0),
                            title = cursor.getString(1),
                            createdAt = cursor.getLong(2),
                            updatedAt = cursor.getLong(3),
                            pinned = cursor.getInt(4) != 0,
                            preview = cursor.getString(5),
                            messageCount = cursor.getInt(6),
                            unread = cursor.getInt(7),
                            archived = cursor.getInt(8) != 0,
                        ),
                    )
                }
            }
        }
    }

    private fun queryMessages(conversationId: Long): List<StoredMessage> =
        helper.readableDatabase.rawQuery(
            """
            SELECT id, conversation_id, role, text, created_at, attachment, model,
                   prompt_tokens, completion_tokens, reasoning, reasoning_ms, steps,
                   reply_to_id, reply_preview, sender
            FROM messages
            WHERE conversation_id = ?
            ORDER BY created_at ASC, id ASC
            """.trimIndent(),
            arrayOf(conversationId.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toStoredMessage())
            }
        }

    /**
     * 列顺序必须与 [queryMessages] 的 SELECT 一致。
     *
     * `sender` 追加在**最后**（下标 14）：插在中间会让后面所有下标整体错位，
     * 表现为"思考过程变成模型名""引用跑到正文里"这类看起来像解析崩了的症状。
     */
    private fun Cursor.toStoredMessage() = StoredMessage(
        id = getLong(0),
        conversationId = getLong(1),
        role = MessageRole.fromWire(getString(2)),
        text = getString(3),
        createdAt = getLong(4),
        attachment = ChatAttachment.fromJson(getString(5)),
        model = getString(6) ?: "",
        promptTokens = getInt(7),
        completionTokens = getInt(8),
        reasoning = getString(9).orEmpty(),
        reasoningMs = getLong(10),
        steps = getString(11).orEmpty(),
        replyToId = if (isNull(12)) null else getLong(12),
        replyPreview = getString(13).orEmpty(),
        sender = getString(14).orEmpty(),
    )
}

internal class VeluneDatabase(context: Context) :
    SQLiteOpenHelper(context, "velune.db", null, DatabaseVersion) {

    override fun onConfigure(db: SQLiteDatabase) {
        // 删除会话时依赖它级联清掉消息，否则会留下孤儿行
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE conversations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                pinned INTEGER NOT NULL DEFAULT 0,
                -- v8：会话可绑定一个角色（提示词 id）。全新安装走 onCreate，
                -- 老库走 onUpgrade 的 ALTER，两条路径都必须有这一列，否则查询直接崩
                prompt_id INTEGER,
                -- v12：最近一次读完会话的时间。未读数由「assistant 消息中晚于它」算出，
                -- 不在写入时维护计数 —— 那样 QqResponder / ChatScreen / 重发每条路径
                -- 都得记得改，漏一处未读就永久不准
                last_read_at INTEGER NOT NULL DEFAULT 0,
                -- v13：归档。归档不删除数据，只是从主列表里移走，
                -- 用户后悔了还能在归档箱里找回来
                archived INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                conversation_id INTEGER NOT NULL
                    REFERENCES conversations(id) ON DELETE CASCADE,
                role TEXT NOT NULL,
                text TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                attachment TEXT,
                model TEXT NOT NULL DEFAULT '',
                prompt_tokens INTEGER NOT NULL DEFAULT 0,
                completion_tokens INTEGER NOT NULL DEFAULT 0,
                reasoning TEXT NOT NULL DEFAULT '',
                reasoning_ms INTEGER NOT NULL DEFAULT 0,
                steps TEXT NOT NULL DEFAULT '',
                reply_to_id INTEGER,
                reply_preview TEXT NOT NULL DEFAULT '',
                -- v11：消息来源标签（QQ 私聊/群聊 · 谁）。App 内消息为空串
                sender TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_messages_conversation ON messages(conversation_id, created_at, id)")
        db.execSQL("CREATE INDEX idx_conversations_order ON conversations(pinned DESC, updated_at DESC)")
        createAgentSchema(db)
        createSummarySchema(db)
        createTodoSchema(db)
        createPromptSchema(db)
    }

    /**
     * 迁移必须逐版写清楚。之前这里是空的 —— 一旦加列，老用户升级就会在查询时崩
     * （列不存在），而这类问题只在真机升级路径上出现，debug 全新安装永远看不到。
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE messages ADD COLUMN attachment TEXT")
            db.execSQL("ALTER TABLE messages ADD COLUMN model TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE messages ADD COLUMN prompt_tokens INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE messages ADD COLUMN completion_tokens INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 3) {
            createAgentSchema(db)
        }
        if (oldVersion < 4) {
            createSummarySchema(db)
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE messages ADD COLUMN reasoning TEXT NOT NULL DEFAULT ''")
            createTodoSchema(db)
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE messages ADD COLUMN reasoning_ms INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 7) {
            createPromptSchema(db)
        }
        if (oldVersion < 10) {
            // v10：引用回复。预览冗余存一份，原消息被删后引用仍能显示"引用了什么"
            db.execSQL("ALTER TABLE messages ADD COLUMN reply_to_id INTEGER")
            db.execSQL("ALTER TABLE messages ADD COLUMN reply_preview TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 9) {
            // v9：把工具调用过程落到消息上，否则界面一刷新卡片就消失
            db.execSQL("ALTER TABLE messages ADD COLUMN steps TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 8) {
            // v8：会话可绑定一个角色（提示词），为空则用该场景的全局生效版本
            db.execSQL("ALTER TABLE conversations ADD COLUMN prompt_id INTEGER")
        }
        if (oldVersion < 11) {
            // v11-1：消息来源标签。QQ 写进共享会话后，需要能区分「谁说的」
            if (!db.hasColumn("messages", "sender")) {
                db.execSQL("ALTER TABLE messages ADD COLUMN sender TEXT NOT NULL DEFAULT ''")
            }
            // v11-2：提示词不再分场景，全局只能有一条启用。
            //
            // 顺序必须是「先选赢家 → 全部清零 → 置回赢家」：
            // 写成单条 `UPDATE ... SET is_active = CASE WHEN id = (SELECT ...)`
            // 时，SQLite 逐行扫描会看到本语句已经改写的值，结果不可预测。
            // 选赢家的优先级：chat 场景优先（老用户默认配的就是它）→ 最近更新 → id 最大。
            val winnerId = db.rawQuery(
                "SELECT id FROM prompts WHERE is_active = 1 " +
                    "ORDER BY (scene = 'chat') DESC, updated_at DESC, id DESC LIMIT 1",
                null,
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }
            db.execSQL("UPDATE prompts SET is_active = 0")
            if (winnerId > 0L) {
                db.execSQL("UPDATE prompts SET is_active = 1 WHERE id = ?", arrayOf(winnerId))
            }
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_prompts_single_active " +
                    "ON prompts(is_active) WHERE is_active = 1",
            )
        }

        // 兜底补齐业务索引。        //
        // 这两条索引原先只写在 onCreate 里，onUpgrade 里一条都没有：
        // 全新安装很快，升级用户则长期没有索引 —— 每次读消息全表扫 + 临时排序。
        // 用 IF NOT EXISTS，对新库与已建过索引的库都是幂等的。
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_messages_conversation " +
                "ON messages(conversation_id, created_at, id)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_conversations_order " +
                "ON conversations(pinned DESC, updated_at DESC)",
        )

        if (oldVersion < 12) {
            // v12：未读标记。只加一列「读到哪了」，未读数在查询时算 —— 写路径不用维护计数
            if (!db.hasColumn("conversations", "last_read_at")) {
                db.execSQL(
                    "ALTER TABLE conversations ADD COLUMN last_read_at INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        if (oldVersion < 13) {
            // v13：归档。不删数据，只加标记
            if (!db.hasColumn("conversations", "archived")) {
                db.execSQL(
                    "ALTER TABLE conversations ADD COLUMN archived INTEGER NOT NULL DEFAULT 0",
                )
            }
        }
    }

    /**
     * 降级时拆掉全局唯一索引。
     *
     * 作用范围要写清楚：**只有未来从 v12 回退到 v11 才会走到这里**（往回退到 v10 及更早时，
     * 旧 APK 用的是它自己那份 SQLiteOpenHelper，根本不会执行这段新代码 —— 旧版本会在
     * `user_version` 检查处按自己的逻辑处理）。留着它的理由是：v12 若再次改动提示词表，
     * 回退到 v11 的 `activate` 按场景各自置 1 会同时出现两条 `is_active = 1`，
     * 撞上本索引直接抛约束错误。
     */
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        runCatching { db.execSQL("DROP INDEX IF EXISTS idx_prompts_single_active") }
    }

    /**
     * v7：自定义提示词。
     *
     * 两条索引各有用途：`scene_name` 防同场景重名（避免列表里两条一模一样分不清）；
     * `is_active` 是**部分唯一索引**，把「同一场景只能有一条启用」压到数据库层 ——
     * 应用层判断一旦漏了某个分支（比如直接改 is_active），用户就会遇到"两条都说是启用的"。
     */
    private fun createPromptSchema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS prompts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                scene TEXT NOT NULL,
                name TEXT NOT NULL,
                body TEXT NOT NULL,
                is_active INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_prompts_scene_name ON prompts(scene, name)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_prompts_active ON prompts(scene) WHERE is_active = 1")
        // v11：提示词全局只有一条启用。数据库层强制，应用层漏了某个分支也演不出「两条都启用」
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_prompts_single_active " +
                "ON prompts(is_active) WHERE is_active = 1",
        )
    }

    /** v5：本地待办。系统日历由 CalendarContract 负责，这里只存「轻量清单」。 */
    private fun createTodoSchema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS todos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                done INTEGER NOT NULL DEFAULT 0,
                due_at INTEGER,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    /** v4：上下文摘要。与 conversations 级联删除，删会话时摘要不会变成孤儿。 */
    private fun createSummarySchema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS context_summaries (
                conversation_id INTEGER PRIMARY KEY
                    REFERENCES conversations(id) ON DELETE CASCADE,
                summary TEXT NOT NULL,
                covered_count INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    /** v3：插件、Agent 运行记录、步骤与 scope 审计。 */
    private fun createAgentSchema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS plugins (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                version TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                scopes TEXT NOT NULL,
                manifest TEXT NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1,
                installed_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS agent_runs (
                id TEXT PRIMARY KEY,
                conversation_id INTEGER NOT NULL,
                started_at INTEGER NOT NULL,
                finished_at INTEGER,
                status TEXT NOT NULL,
                steps INTEGER NOT NULL DEFAULT 0,
                tool_calls INTEGER NOT NULL DEFAULT 0,
                prompt_tokens INTEGER NOT NULL DEFAULT 0,
                completion_tokens INTEGER NOT NULL DEFAULT 0,
                error TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS agent_steps (
                id TEXT PRIMARY KEY,
                run_id TEXT NOT NULL REFERENCES agent_runs(id) ON DELETE CASCADE,
                idx INTEGER NOT NULL,
                type TEXT NOT NULL,
                title TEXT NOT NULL,
                input_json TEXT,
                output_json TEXT,
                status TEXT NOT NULL,
                started_at INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                error_code TEXT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_agent_steps_run ON agent_steps(run_id, idx)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS scope_audit (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts INTEGER NOT NULL,
                plugin_id TEXT NOT NULL,
                scope TEXT NOT NULL,
                target TEXT NOT NULL,
                result TEXT NOT NULL,
                run_id TEXT
            )
            """.trimIndent(),
        )
    }

    private companion object {
        const val DatabaseVersion = 13
    }
}

/**
 * 表里有没有这一列。
 *
 * `ALTER TABLE ADD COLUMN` 在 SQLite 里没有 `IF NOT EXISTS`，而迁移脚本可能被重复执行
 * （比如某次失败的升级后又退回到旧版本再升级）。先探一次，避免"duplicate column name"。
 */
private fun SQLiteDatabase.hasColumn(table: String, column: String): Boolean =
    rawQuery("PRAGMA table_info($table)", null).use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        if (nameIndex < 0) return@use false
        var found = false
        while (cursor.moveToNext()) {
            if (cursor.getString(nameIndex) == column) {
                found = true
                break
            }
        }
        found
    }

/**
 * 单例连接持有者。
 *
 * 插件库、运行记录与消息库共用一份连接：多个 SQLiteOpenHelper 指向同一个文件时，
 * 并发写会在没有 busy_timeout 的情况下直接抛 SQLITE_BUSY，且这类问题只在真机高频操作时出现。
 */
internal object VeluneDb {
    @Volatile
    private var helper: VeluneDatabase? = null

    fun of(context: Context): VeluneDatabase =
        helper ?: synchronized(this) {
            helper ?: VeluneDatabase(context.applicationContext).also { helper = it }
        }
}
