package com.example.autoscreenagent.data.remote.zhipu

/**
 * 智谱 GLM API 配置
 *
 * @param apiKey API 密钥
 * @param baseUrl API 基础 URL
 * @param model 模型名称
 * @param enableThinking 是否启用思考模式
 * @param temperature 采样温度 (0.0 - 1.0)
 * @param topP Top-P 采样参数
 * @param maxTokens 最大输出 token 数
 * @param timeoutSeconds 请求超时时间（秒）
 * @param enableStreaming 是否启用流式输出
 */
data class ZhipuConfig(
    // API 配置
    val apiKey: String = "key",
    val baseUrl: String = "https://open.bigmodel.cn/api/paas/v4",
    val model: String = "glm-4.6v-flash",

    // 模型参数
    val enableThinking: Boolean = true,
    val temperature: Float = 0.7f,
    val topP: Float = 1.0f,
    val maxTokens: Int? = null,

    // 请求配置
    val timeoutSeconds: Int = 120,
    val enableStreaming: Boolean = true
) {
    /**
     * 获取 Authorization Header 值
     */
    fun getAuthorizationHeader(): String = "Bearer $apiKey"

    /**
     * 验证配置是否有效
     */
    fun isValid(): Boolean {
        return apiKey.isNotBlank() &&
                apiKey != "YOUR_API_KEY" &&
                baseUrl.isNotBlank() &&
                model.isNotBlank() &&
                temperature in 0.0f..2.0f &&
                topP in 0.0f..1.0f &&
                timeoutSeconds > 0
    }

    /**
     * 创建带自定义 API Key 的配置副本
     */
    fun withApiKey(newApiKey: String): ZhipuConfig = copy(apiKey = newApiKey)

    /**
     * 创建带自定义模型的配置副本
     */
    fun withModel(newModel: String): ZhipuConfig = copy(model = newModel)

    /**
     * 创建禁用思考模式的配置副本
     */
    fun withoutThinking(): ZhipuConfig = copy(enableThinking = false)

    companion object {
        /**
         * 默认配置
         */
        val DEFAULT = ZhipuConfig()

        /**
         * 创建用于测试的配置（使用较小超时）
         */
        fun forTesting(apiKey: String): ZhipuConfig = ZhipuConfig(
            apiKey = apiKey,
            timeoutSeconds = 30,
            enableStreaming = true
        )
    }
}
