package com.veluneagent.core.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 对话相关偏好。
 *
 * 目前只有一项：上下文压缩阈值（保留最近多少条消息发给模型）。
 * 之前这个值只存在于 ChatScreen 的 remember 里 —— 关掉页面就丢，
 * 也没有参与请求构造，等于一个纯装饰的环。现在它落盘并真正决定发送的历史长度。
 */
object ChatPrefs {

    private const val PREFS = "velune_chat"
    private const val KEY_COMPRESS_AT = "compress_at"
    private const val KEY_AGENT_ENABLED = "agent_enabled"
    private const val KEY_COMPRESS_TRIGGER = "compress_trigger_tokens"
    private const val KEY_THINKING = "thinking_enabled"
    private const val KEY_EFFORT = "reasoning_effort"
    private const val KEY_SORT = "conversation_sort"

    /** 可选的保留条数。数值越小越省钱，但模型越容易「忘事」。 */
    val CompressOptions = listOf(8, 16, 24, 40, 60, 100)

    /** 待折叠部分的 token 超过该值才触发摘要压缩。 */
    val TriggerOptions = listOf(8_000, 16_000, 32_000, 64_000)

    const val DefaultCompressAt = 24

    const val DefaultTriggerTokens = 16_000

    private var appContext: Context? = null

    private val _compressAt = MutableStateFlow(DefaultCompressAt)
    val compressAt: StateFlow<Int> = _compressAt

    private val _compressTrigger = MutableStateFlow(DefaultTriggerTokens)
    val compressTrigger: StateFlow<Int> = _compressTrigger

    /** 深度思考开关。三家默认都开启，所以默认值取 true，且「开」时不额外下发参数。 */
    private val _thinkingEnabled = MutableStateFlow(true)
    val thinkingEnabled: StateFlow<Boolean> = _thinkingEnabled

    /** 推理强度：low / high / max；null 表示用服务商默认（deepseek 默认 high）。 */
    private val _reasoningEffort = MutableStateFlow<String?>(null)
    val reasoningEffort: StateFlow<String?> = _reasoningEffort

    val EffortOptions = listOf("low", "high", "max")

    /** Agent 模式开关。默认关：工具调用会产生额外费用与外部副作用，不能默认替用户打开。 */
    private val _agentEnabled = MutableStateFlow(false)
    val agentEnabled: StateFlow<Boolean> = _agentEnabled

    /** 会话列表排序。置顶永远在最前，这里只决定其余部分的顺序。 */
    private val _sortMode = MutableStateFlow(ConversationSort.Recent)
    val sortMode: StateFlow<ConversationSort> = _sortMode

    fun init(context: Context) {
        if (appContext != null) return
        val ctx = context.applicationContext
        appContext = ctx
        val saved = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_COMPRESS_AT, DefaultCompressAt)
        // 存量值可能来自旧版本的选项集，越界就回落到默认值，避免出现 0 导致除零
        _compressAt.value = if (saved in CompressOptions) saved else DefaultCompressAt
        _agentEnabled.value = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AGENT_ENABLED, false)
        val trigger = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_COMPRESS_TRIGGER, DefaultTriggerTokens)
        _compressTrigger.value = if (trigger in TriggerOptions) trigger else DefaultTriggerTokens
        _thinkingEnabled.value = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_THINKING, true)
        _reasoningEffort.value = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_EFFORT, null)
            ?.takeIf { it in EffortOptions }
        _sortMode.value = ConversationSort.fromKey(
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_SORT, null),
        )
    }

    fun setSortMode(mode: ConversationSort) {
        _sortMode.value = mode
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putString(KEY_SORT, mode.name)?.apply()
    }

    fun setThinkingEnabled(enabled: Boolean) {
        _thinkingEnabled.value = enabled
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY_THINKING, enabled)?.apply()
    }

    fun setReasoningEffort(effort: String?) {
        val normalized = effort?.takeIf { it in EffortOptions }
        _reasoningEffort.value = normalized
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putString(KEY_EFFORT, normalized)?.apply()
    }

    fun setCompressTrigger(value: Int) {
        val normalized = if (value in TriggerOptions) value else DefaultTriggerTokens
        _compressTrigger.value = normalized
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()
            ?.putInt(KEY_COMPRESS_TRIGGER, normalized)
            ?.apply()
    }

    fun setAgentEnabled(enabled: Boolean) {
        _agentEnabled.value = enabled
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()
            ?.putBoolean(KEY_AGENT_ENABLED, enabled)
            ?.apply()
    }

    fun setCompressAt(value: Int) {
        val normalized = if (value in CompressOptions) value else DefaultCompressAt
        _compressAt.value = normalized
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()
            ?.putInt(KEY_COMPRESS_AT, normalized)
            ?.apply()
    }
}
