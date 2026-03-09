package com.example.autoscreenagent.data.remote.model

import kotlinx.coroutines.flow.Flow

/**
 * 模型厂商类型枚举
 */
enum class ModelProviderType {
    ZHIPU,      // 智谱 GLM
    ALIBABA,   // 阿里云百炼
    OPENAI,    // OpenAI
    CUSTOM     // 自定义厂商
}

/**
 * 抽象模型厂商接口
 *
 * 定义统一的模型交互接口，支持多厂商切换
 *
 * 使用示例：
 * ```kotlin
 * val config = ModelConfig(
 *     provider = ModelProviderType.ALIBABA,
 *     baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
 *     apiKey = "your-api-key",
 *     model = "qwen-vl-plus"
 * )
 * val provider = ModelProvider.create(config)
 * provider.sendMessage("你好").collect { chunk ->
 *     println(chunk.getContent())
 * }
 * ```
 */
abstract class ModelProvider(protected val config: ModelConfig) {

    /**
     * 获取厂商类型
     */
    abstract val providerType: ModelProviderType

    /**
     * 发送纯文本消息（流式）
     *
     * @param text 文本内容
     * @return 流式响应
     */
    abstract fun sendMessage(text: String): Flow<ModelStreamChunk>

    /**
     * 发送消息 + 单张图片（Base64）
     *
     * @param text 文本内容
     * @param imageBase64 图片 Base64 编码
     * @return 流式响应
     */
    abstract fun sendMessageWithImageBase64(text: String, imageBase64: String): Flow<ModelStreamChunk>

    /**
     * 发送消息 + 多张图片（Base64）
     *
     * @param text 文本内容
     * @param images 图片 Base64 列表
     * @return 流式响应
     */
    abstract fun sendMessageWithImagesBase64(text: String, images: List<String>): Flow<ModelStreamChunk>

    /**
     * 发送消息 + 图片 URL
     *
     * @param text 文本内容
     * @param imageUrls 图片 URL 列表
     * @return 流式响应
     */
    abstract fun sendMessageWithImageUrls(text: String, imageUrls: List<String>): Flow<ModelStreamChunk>

    /**
     * 发送完整消息列表（多轮对话）
     *
     * @param messages 消息列表
     * @return 流式响应
     */
    abstract fun sendMessages(messages: List<ModelMessage>): Flow<ModelStreamChunk>

    /**
     * 发送消息（支持 Tool Calling）
     *
     * @param messages 消息列表
     * @param tools 工具定义列表
     * @return 流式响应
     */
    abstract fun sendMessagesWithTools(
        messages: List<ModelMessage>,
        tools: List<ModelTool>? = null
    ): Flow<ModelStreamChunk>

    /**
     * 非流式调用
     *
     * @param messages 消息列表
     * @return 完整响应
     */
    abstract suspend fun chatCompletion(messages: List<ModelMessage>): ModelResponse

    /**
     * 更新配置
     */
    abstract fun updateConfig(newConfig: ModelConfig)

    /**
     * 设置 API Key
     */
    abstract fun setApiKey(apiKey: String)

    /**
     * 设置模型
     */
    abstract fun setModel(model: String)

    /**
     * 验证配置是否有效
     */
    abstract fun isValid(): Boolean

    /**
     * 关闭 provider，释放资源
     */
    abstract fun close()

    companion object {
        /**
         * 根据配置创建对应的 Provider
         */
        fun create(config: ModelConfig): ModelProvider {
            return when (config.provider) {
                ModelProviderType.ZHIPU -> ZhipuProvider(config)
                ModelProviderType.ALIBABA -> AlibabaProvider(config)
                ModelProviderType.OPENAI -> OpenAIProvider(config)
                ModelProviderType.CUSTOM -> CustomProvider(config)
            }
        }
    }
}