package com.example.autoscreenagent.data.remote.model

import kotlinx.serialization.Serializable

/**
 * 模型厂商类型
 */
enum class ModelProvider {
    ZHIPU,      // 智谱 GLM
    QWEN,       // 阿里云千问
    OPENAI,     // OpenAI
    CUSTOM      // 自定义
}

/**
 * 模型厂商类型（远程配置兼容）
 *
 * 与 ModelProvider 的区别：
 * - ALIBABA 对应 ModelProvider.QWEN
 */
enum class ModelProviderType {
    ZHIPU,      // 智谱 GLM
    ALIBABA,    // 阿里云（对应 ModelProvider.QWEN）
    OPENAI,     // OpenAI
    CUSTOM;     // 自定义

    /**
     * 转换为新的 ModelProvider
     */
    fun toModelProvider(): ModelProvider = when (this) {
        ZHIPU -> ModelProvider.ZHIPU
        ALIBABA -> ModelProvider.QWEN
        OPENAI -> ModelProvider.OPENAI
        CUSTOM -> ModelProvider.CUSTOM
    }

    companion object {
        /**
         * 从 ModelProvider 转换
         */
        fun fromModelProvider(provider: ModelProvider): ModelProviderType = when (provider) {
            ModelProvider.ZHIPU -> ZHIPU
            ModelProvider.QWEN -> ALIBABA
            ModelProvider.OPENAI -> OPENAI
            ModelProvider.CUSTOM -> CUSTOM
        }
    }
}

/**
 * ChatModel 配置
 *
 * @param provider 厂商类型
 * @param baseUrl API 基础 URL
 * @param apiKey API 密钥
 * @param model 模型名称
 * @param temperature 采样温度 (0.0 - 2.0)
 * @param topP Top-P 采样参数
 * @param maxTokens 最大输出 token 数
 * @param timeoutSeconds 请求超时时间（秒）
 * @param enableThinking 是否启用思考模式
 * @param extraParams 额外参数（厂商特定）
 */
@Serializable
data class ChatModelConfig(
    val provider: ModelProvider = ModelProvider.ZHIPU,
    val baseUrl: String = DEFAULT_BASE_URLS[provider] ?: "",
    val apiKey: String = "",
    val model: String = DEFAULT_MODELS[provider] ?: "",
    val temperature: Float = 0.7f,
    val topP: Float = 1.0f,
    val maxTokens: Int? = null,
    val timeoutSeconds: Int = 120,
    val enableThinking: Boolean = true,
    val extraParams: Map<String, String> = emptyMap()
) {
    companion object {
        /**
         * 各厂商默认 baseUrl
         */
        val DEFAULT_BASE_URLS = mapOf(
            ModelProvider.ZHIPU to "https://open.bigmodel.cn/api/paas/v4",
            ModelProvider.QWEN to "https://dashscope.aliyuncs.com/compatible-mode/v1",
            ModelProvider.OPENAI to "https://api.openai.com/v1",
            ModelProvider.CUSTOM to ""
        )

        /**
         * 各厂商默认模型
         */
        val DEFAULT_MODELS = mapOf(
            ModelProvider.ZHIPU to "glm-4.6v-flash",
            ModelProvider.QWEN to "qwen-vl-plus",
            ModelProvider.OPENAI to "gpt-4o-mini",
            ModelProvider.CUSTOM to ""
        )

        /**
         * 创建智谱配置
         */
        fun zhipu(
            apiKey: String,
            model: String = "glm-4.6v-flash",
            enableThinking: Boolean = true
        ) = ChatModelConfig(
            provider = ModelProvider.ZHIPU,
            apiKey = apiKey,
            model = model,
            enableThinking = enableThinking
        )

        /**
         * 创建阿里云配置
         */
        fun qwen(
            apiKey: String,
            model: String = "qwen-vl-plus",
            enableThinking: Boolean = true
        ) = ChatModelConfig(
            provider = ModelProvider.QWEN,
            apiKey = apiKey,
            model = model,
            enableThinking = enableThinking,
            extraParams = if (enableThinking) mapOf("enable_thinking" to "true") else emptyMap()
        )

        /**
         * 创建 OpenAI 配置
         */
        fun openai(
            apiKey: String,
            model: String = "gpt-4o-mini"
        ) = ChatModelConfig(
            provider = ModelProvider.OPENAI,
            apiKey = apiKey,
            model = model,
            enableThinking = false
        )

        /**
         * 创建自定义配置
         */
        fun custom(
            baseUrl: String,
            apiKey: String,
            model: String
        ) = ChatModelConfig(
            provider = ModelProvider.CUSTOM,
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model
        )
    }

    /**
     * 获取 Authorization Header 值
     */
    fun getAuthorizationHeader(): String = "Bearer $apiKey"

    /**
     * 验证配置是否有效
     */
    fun isValid(): Boolean {
        return apiKey.isNotBlank() &&
                baseUrl.isNotBlank() &&
                model.isNotBlank() &&
                temperature in 0.0f..2.0f &&
                topP in 0.0f..1.0f &&
                timeoutSeconds > 0
    }

    /**
     * 创建带 API Key 的副本
     */
    fun withApiKey(newApiKey: String): ChatModelConfig = copy(apiKey = newApiKey)

    /**
     * 创建带模型的副本
     */
    fun withModel(newModel: String): ChatModelConfig = copy(model = newModel)

    /**
     * 创建禁用思考模式的副本
     */
    fun withoutThinking(): ChatModelConfig = copy(
        enableThinking = false,
        extraParams = extraParams - "enable_thinking"
    )
}

/**
 * 对话配置（用于历史管理）
 */
@Serializable
data class ConversationConfig(
    val systemPrompt: String? = null,
    val maxHistoryMessages: Int = 20,
    val removeImagesAfterRounds: Int = 3,
    val autoRemoveImages: Boolean = true
) {
    companion object {
        val DEFAULT = ConversationConfig()
    }
}