package com.veluneagent.core.data

/**
 * 内置服务商预设。
 *
 * **只预填名称与 Base URL，不预置模型列表。**
 *
 * 模型名是这套系统里迭代最快的东西：`deepseek-chat` / `deepseek-reasoner` 已在 2026-07 弃用，
 * GLM 从 4.6 一路走到 5.3，MiMo 也在换版本号。任何硬编码的模型列表，过几个月就会变成
 * 「列出来的都调不通，能用的又填不进去」，而用户还以为是应用坏了。
 *
 * 所以模型名交给用户按服务商文档自己填，输入框只给一个格式示例（[modelHint]）。
 * 换模型时改一个输入框即可，不需要等应用发版。
 *
 * 参数核对日期 2026-10-21：
 * - DeepSeek：https://api.deepseek.com
 * - 智谱：https://open.bigmodel.cn/api/paas/v4（必须带 /v4，漏了 404）
 * - 小米 MiMo：https://api.xiaomimimo.com/v1
 */
data class ProviderPreset(
    val id: String,
    val name: String,
    val baseUrl: String,
    /** 模型输入框的占位示例，仅提示取值格式，不参与校验、不参与请求。 */
    val modelHint: String,
    /** 该服务商接入时最容易踩的坑，显示在预设芯片下方。 */
    val note: String,
    /** 官方文档，用户去这里查当前可用的模型 ID。 */
    val docsUrl: String,
)

object ProviderPresets {

    val deepSeek = ProviderPreset(
        id = "deepseek",
        name = "DeepSeek",
        baseUrl = "https://api.deepseek.com",
        modelHint = "deepseek-flash",
        note = "Base URL 不需要加 /v1。模型名以官方文档为准（deepseek-chat / reasoner 已弃用）",
        docsUrl = "https://api-docs.deepseek.com",
    )

    val zhiPu = ProviderPreset(
        id = "zhipu",
        name = "智谱 GLM",
        baseUrl = "https://open.bigmodel.cn/api/paas/v4",
        modelHint = "glm-5.3",
        note = "Base URL 必须带 /v4，漏了会 404。模型名以官方文档为准",
        docsUrl = "https://docs.bigmodel.cn",
    )

    val miMo = ProviderPreset(
        id = "mimo",
        name = "小米 MiMo",
        baseUrl = "https://api.xiaomimimo.com/v1",
        modelHint = "mimo-v2.6-flash",
        note = "Token Plan 套餐要换成专属 Base URL。模型名以官方文档为准",
        docsUrl = "https://platform.xiaomimimo.com",
    )

    val all: List<ProviderPreset> = listOf(deepSeek, zhiPu, miMo)

    /** 编辑已有配置时按 Base URL 反推预设，只为显示对应的提示文案。 */
    fun byBaseUrl(baseUrl: String): ProviderPreset? {
        val normalized = baseUrl.trim().trimEnd('/')
        if (normalized.isEmpty()) return null
        return all.firstOrNull { normalized.startsWith(it.baseUrl.trimEnd('/')) }
    }
}
