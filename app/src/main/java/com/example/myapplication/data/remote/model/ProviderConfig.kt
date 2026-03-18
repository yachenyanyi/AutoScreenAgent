package com.example.autoscreenagent.data.remote.model

import kotlinx.serialization.Serializable

/**
 * 单个厂商的配置
 *
 * @param apiKey API 密钥
 * @param baseUrl API 基础 URL
 * @param defaultModel 默认模型
 * @param temperature 采样温度 (0.0 - 2.0)
 * @param topP Top-P 采样参数
 * @param maxTokens 最大输出 token 数
 * @param timeoutSeconds 请求超时时间（秒）
 * @param enableThinking 是否启用思考模式
 */
@Serializable
data class ProviderConfig(
    val apiKey: String = "",
    val baseUrl: String = "",
    val defaultModel: String = "",
    val temperature: Float = 0.7f,
    val topP: Float = 1.0f,
    val maxTokens: Int? = null,
    val timeoutSeconds: Int = 120,
    val enableThinking: Boolean = true
) {
    /**
     * 验证配置是否有效
     */
    fun isValid(): Boolean {
        return apiKey.isNotBlank() &&
                baseUrl.isNotBlank() &&
                defaultModel.isNotBlank() &&
                temperature in 0.0f..2.0f &&
                topP in 0.0f..1.0f &&
                timeoutSeconds > 0
    }

    /**
     * 是否已配置 API Key
     */
    fun hasApiKey(): Boolean = apiKey.isNotBlank()

    companion object {
        /**
         * 智谱默认配置
         */
        fun zhipuDefault() = ProviderConfig(
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            defaultModel = "glm-4.6v-flash",
            enableThinking = true
        )

        /**
         * 阿里云默认配置
         */
        fun qwenDefault() = ProviderConfig(
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            defaultModel = "qwen-vl-plus",
            enableThinking = true
        )

        /**
         * OpenAI 默认配置
         */
        fun openaiDefault() = ProviderConfig(
            baseUrl = "https://api.openai.com/v1",
            defaultModel = "gpt-4o-mini",
            enableThinking = false
        )

        /**
         * 自定义默认配置
         */
        fun customDefault() = ProviderConfig(
            enableThinking = false
        )
    }
}

/**
 * 多厂商配置管理
 *
 * 管理所有厂商的独立配置，支持：
 * - 每个厂商独立的 API Key
 * - 每个厂商独立的 Base URL
 * - 每个厂商独立的默认模型
 * - 每个厂商独立的参数配置
 */
@Serializable
data class MultiProviderConfig(
    val zhipu: ProviderConfig = ProviderConfig.zhipuDefault(),
    val qwen: ProviderConfig = ProviderConfig.qwenDefault(),
    val openai: ProviderConfig = ProviderConfig.openaiDefault(),
    val custom: ProviderConfig = ProviderConfig.customDefault()
) {
    /**
     * 获取指定厂商的配置
     */
    fun getProviderConfig(provider: ModelProvider): ProviderConfig = when (provider) {
        ModelProvider.ZHIPU -> zhipu
        ModelProvider.QWEN -> qwen
        ModelProvider.OPENAI -> openai
        ModelProvider.CUSTOM -> custom
    }

    /**
     * 更新指定厂商的配置
     */
    fun updateProviderConfig(provider: ModelProvider, config: ProviderConfig): MultiProviderConfig = when (provider) {
        ModelProvider.ZHIPU -> copy(zhipu = config)
        ModelProvider.QWEN -> copy(qwen = config)
        ModelProvider.OPENAI -> copy(openai = config)
        ModelProvider.CUSTOM -> copy(custom = config)
    }

    /**
     * 获取所有已配置的厂商列表
     */
    fun getConfiguredProviders(): List<ModelProvider> {
        return ModelProvider.entries.filter { provider ->
            getProviderConfig(provider).hasApiKey()
        }
    }

    /**
     * 检查指定厂商是否已配置
     */
    fun isConfigured(provider: ModelProvider): Boolean {
        return getProviderConfig(provider).hasApiKey()
    }

    companion object {
        /**
         * 默认配置
         */
        val DEFAULT = MultiProviderConfig()
    }
}