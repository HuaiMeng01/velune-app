package com.veluneagent.core.data.agent

import com.veluneagent.core.data.ChatClient
import com.veluneagent.core.data.ChatMessage
import com.veluneagent.core.data.Provider
import com.veluneagent.core.data.StreamEvent
import kotlinx.coroutines.flow.Flow

/**
 * 基于 OpenAI 兼容端点的网关。
 *
 * `tools` 用 lambda 而不是构造时快照：Agent 在运行中装好一个插件后，
 * **下一次模型调用**就要能看到新工具。如果构造时就把工具列表钉死，
 * 「自己给自己装插件」就变成了「装完要重启才能用」。
 */
class OpenAiGateway(
    private val provider: Provider,
    private val apiKey: String,
    private val tools: () -> List<ToolSpec>,
    private val thinking: Boolean = true,
    private val reasoningEffort: String? = null,
    /**
     * 「多久没有新内容」才判断流，不是总时长上限。
     * 默认 180s：足够覆盖长思考与首包慢的推理模型，又能在真挂死时及时收尾。
     */
    private val idleTimeoutSeconds: Long = ChatClient.DefaultIdleTimeoutSeconds,
) : ModelGateway {

    override fun stream(messages: List<ChatMessage>): Flow<StreamEvent> =
        ChatClient.stream(
            provider = provider,
            apiKey = apiKey,
            history = messages,
            tools = tools(),
            thinking = thinking,
            reasoningEffort = reasoningEffort,
            idleTimeoutSeconds = idleTimeoutSeconds,
        )
}
