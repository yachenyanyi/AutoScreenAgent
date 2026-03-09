package com.example.autoscreenagent.data.remote.model

import android.util.Log
import com.example.autoscreenagent.data.remote.zhipu.ZhipuClient
import com.example.autoscreenagent.data.remote.zhipu.ZhipuConfig
import com.example.autoscreenagent.data.remote.zhipu.ZhipuContent
import com.example.autoscreenagent.data.remote.zhipu.ZhipuMessage
import com.example.autoscreenagent.data.remote.zhipu.ZhipuStreamChunk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * 智谱 GLM 模型 Provider 实现
 *
 * 包装现有的 ZhipuClient，提供统一的 ModelProvider 接口
 */
class ZhipuProvider(config: ModelConfig) : ModelProvider(config) {

    companion object {
        private const val TAG = "ZhipuProvider"
    }

    private val zhipuClient: ZhipuClient = createClient()

    override val providerType: ModelProviderType = ModelProviderType.ZHIPU

    private fun createClient(): ZhipuClient {
        val zhipuConfig = ZhipuConfig(
            apiKey = config.apiKey,
            baseUrl = config.baseUrl,
            model = config.model,
            enableThinking = config.enableThinking,
            temperature = config.temperature,
            topP = config.topP,
            maxTokens = config.maxTokens,
            timeoutSeconds = config.timeoutSeconds,
            enableStreaming = config.enableStreaming
        )
        return ZhipuClient(zhipuConfig)
    }

    override fun sendMessage(text: String): Flow<ModelStreamChunk> {
        return zhipuClient.sendMessage(text).map { it.toModel() }
    }

    override fun sendMessageWithImageBase64(text: String, imageBase64: String): Flow<ModelStreamChunk> {
        return zhipuClient.sendMessageWithImageBase64(text, imageBase64).map { it.toModel() }
    }

    override fun sendMessageWithImagesBase64(text: String, images: List<String>): Flow<ModelStreamChunk> {
        return zhipuClient.sendMessageWithImagesBase64(text, images).map { it.toModel() }
    }

    override fun sendMessageWithImageUrls(text: String, imageUrls: List<String>): Flow<ModelStreamChunk> {
        return zhipuClient.sendMessageWithImages(text, imageUrls).map { it.toModel() }
    }

    override fun sendMessages(messages: List<ModelMessage>): Flow<ModelStreamChunk> {
        val zhipuMessages = messages.map { it.toZhipu() }
        return zhipuClient.sendMessages(zhipuMessages).map { it.toModel() }
    }

    override fun sendMessagesWithTools(
        messages: List<ModelMessage>,
        tools: List<ModelTool>?
    ): Flow<ModelStreamChunk> {
        val zhipuMessages = messages.map { it.toZhipu() }
        val zhipuTools = tools?.map { it.toZhipu() }
        return zhipuClient.sendMessagesWithTools(zhipuMessages, zhipuTools).map { it.toModel() }
    }

    override suspend fun chatCompletion(messages: List<ModelMessage>): ModelResponse {
        val zhipuMessages = messages.map { it.toZhipu() }
        val response = zhipuClient.chatCompletion(zhipuMessages)
        return response.toModel()
    }

    override fun updateConfig(newConfig: ModelConfig) {
        val zhipuConfig = ZhipuConfig(
            apiKey = newConfig.apiKey,
            baseUrl = newConfig.baseUrl,
            model = newConfig.model,
            enableThinking = newConfig.enableThinking,
            temperature = newConfig.temperature,
            topP = newConfig.topP,
            maxTokens = newConfig.maxTokens,
            timeoutSeconds = newConfig.timeoutSeconds,
            enableStreaming = newConfig.enableStreaming
        )
        zhipuClient.updateConfig(zhipuConfig)
    }

    override fun setApiKey(apiKey: String) {
        zhipuClient.setApiKey(apiKey)
    }

    override fun setModel(model: String) {
        zhipuClient.setModel(model)
    }

    override fun isValid(): Boolean = config.isValid()

    override fun close() {
        // ZhipuClient 不需要显式关闭
        Log.d(TAG, "智谱 Provider 已关闭")
    }

    // ========================================
    // 转换扩展函数
    // ========================================

    private fun ZhipuStreamChunk.toModel(): ModelStreamChunk {
        return ModelStreamChunk(
            id = id,
            choices = choices.map { choice ->
                ModelStreamChoice(
                    index = choice.index,
                    delta = choice.delta?.let { delta ->
                        ModelDelta(
                            role = delta.role,
                            content = delta.content,
                            reasoningContent = delta.reasoningContent,
                            toolCalls = delta.toolCalls?.map { ModelToolCall.fromZhipu(it) }
                        )
                    },
                    finishReason = choice.finishReason
                )
            },
            usage = usage?.let {
                ModelUsage(
                    promptTokens = it.promptTokens,
                    completionTokens = it.completionTokens,
                    totalTokens = it.totalTokens
                )
            },
            created = created,
            model = model
        )
    }

    private fun com.example.autoscreenagent.data.remote.zhipu.ZhipuResponse.toModel(): ModelResponse {
        return ModelResponse(
            id = id,
            choices = choices.map { choice ->
                ModelResponseChoice(
                    index = choice.index,
                    message = choice.message?.let { msg ->
                        ModelResponseMessage(
                            role = msg.role,
                            content = msg.content,
                            reasoningContent = msg.reasoningContent,
                            toolCalls = msg.toolCalls?.map { ModelToolCall.fromZhipu(it) }
                        )
                    },
                    finishReason = choice.finishReason
                )
            },
            usage = usage?.let {
                ModelUsage(
                    promptTokens = it.promptTokens,
                    completionTokens = it.completionTokens,
                    totalTokens = it.totalTokens
                )
            },
            created = created,
            model = model,
            error = error?.let {
                ModelError(
                    message = it.message,
                    type = it.type,
                    code = it.code
                )
            }
        )
    }
}