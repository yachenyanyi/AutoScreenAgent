package com.example.autoscreenagent.data.remote.model

/**
 * 通用模型配置
 *
 * 支持不同厂商的配置参数
 *
 * @param provider 厂商类型
 * @param baseUrl API 基础 URL
 * @param apiKey API 密钥
 * @param model 模型名称
 * @param temperature 采样温度 (0.0 - 2.0)
 * @param topP Top-P 采样参数
 * @param maxTokens 最大输出 token 数
 * @param timeoutSeconds 请求超时时间（秒）
 * @param enableStreaming 是否启用流式输出
 * @param enableThinking 是否启用思考模式
 * @param extraParams 额外参数（厂商特定）
 */
data class ModelConfig(
    val provider: ModelProviderType = ModelProviderType.ZHIPU,
    val baseUrl: String = DEFAULT_BASE_URLS[provider] ?: "",
    val apiKey: String = "",
    val model: String = "glm-4.6v-flash",
    val temperature: Float = 0.7f,
    val topP: Float = 1.0f,
    val maxTokens: Int? = null,
    val timeoutSeconds: Int = 120,
    val enableStreaming: Boolean = true,
    val enableThinking: Boolean = true,
    val extraParams: Map<String, Any> = emptyMap()
) {
    companion object {
        /**
         * 各厂商默认 baseUrl
         */
        val DEFAULT_BASE_URLS = mapOf(
            ModelProviderType.ZHIPU to "https://open.bigmodel.cn/api/paas/v4",
            ModelProviderType.ALIBABA to "https://dashscope.aliyuncs.com/compatible-mode/v1",
            ModelProviderType.OPENAI to "https://api.openai.com/v1",
            ModelProviderType.CUSTOM to ""
        )

        /**
         * 各厂商默认模型
         */
        val DEFAULT_MODELS = mapOf(
            ModelProviderType.ZHIPU to "glm-4.6v-flash",
            ModelProviderType.ALIBABA to "qwen-vl-plus",
            ModelProviderType.OPENAI to "gpt-4o-mini",
            ModelProviderType.CUSTOM to ""
        )

        /**
         * 创建智谱配置
         */
        fun forZhipu(
            apiKey: String = "",
            model: String = "glm-4.6v-flash",
            enableThinking: Boolean = true
        ) = ModelConfig(
            provider = ModelProviderType.ZHIPU,
            apiKey = apiKey,
            model = model,
            enableThinking = enableThinking
        )

        /**
         * 创建阿里云配置
         */
        fun forAlibaba(
            apiKey: String = "",
            model: String = "qwen-vl-plus",
            enableThinking: Boolean = true
        ) = ModelConfig(
            provider = ModelProviderType.ALIBABA,
            baseUrl = DEFAULT_BASE_URLS[ModelProviderType.ALIBABA]!!,
            apiKey = apiKey,
            model = model,
            enableThinking = enableThinking,
            extraParams = if (enableThinking) {
                mapOf("enable_thinking" to true)
            } else {
                emptyMap()
            }
        )

        /**
         * 创建 OpenAI 配置
         */
        fun forOpenAI(
            apiKey: String = "",
            model: String = "gpt-4o-mini"
        ) = ModelConfig(
            provider = ModelProviderType.OPENAI,
            apiKey = apiKey,
            model = model,
            enableThinking = false
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
     * 创建带自定义 API Key 的配置副本
     */
    fun withApiKey(newApiKey: String): ModelConfig = copy(apiKey = newApiKey)

    /**
     * 创建带自定义模型的配置副本
     */
    fun withModel(newModel: String): ModelConfig = copy(model = newModel)

    /**
     * 创建禁用思考模式的配置副本
     */
    fun withoutThinking(): ModelConfig = copy(
        enableThinking = false,
        extraParams = extraParams - "enable_thinking"
    )
}