package com.example.autoscreenagent.data.remote.model

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 通用模型对话会话 - 自动管理多模态对话历史
 *
 * 功能：
 * - 自动记录对话历史（支持多模态）
 * - N 轮后自动移除图片，节省 token
 * - 支持系统提示词
 * - 历史长度限制
 * - 支持多厂商切换
 *
 * 使用示例：
 * ```kotlin
 * // 使用 ModelManager 初始化
 * val manager = ModelManager.getInstance()
 * manager.initWithAlibaba("your-api-key", "qwen-vl-plus")
 *
 * // 创建对话会话
 * val conversation = ModelConversation(
 *     systemPrompt = "你是一个手机操作助手"
 * )
 *
 * // 文本对话
 * conversation.send("你好").collect { chunk ->
 *     print(chunk.getContent())
 * }
 *
 * // 图片对话
 * conversation.sendWithImage("描述这张截图", base64).collect { ... }
 * ```
 */
class ModelConversation(
    private val provider: ModelProvider? = null,
    private val config: ModelConfig? = null,
    private val systemPrompt: String? = null,
    private val maxHistoryMessages: Int = 20,
    private val removeImagesAfterRounds: Int = 3,
    private val autoRemoveImages: Boolean = true
) {
    companion object {
        private const val TAG = "ModelConversation"

        /**
         * 使用 ModelManager 创建对话会话
         */
        fun withManager(systemPrompt: String? = null): ModelConversation {
            val manager = ModelManager.getInstance()
            return ModelConversation(
                provider = manager.getProvider(),
                config = manager.getConfig(),
                systemPrompt = systemPrompt
            )
        }
    }

    /**
     * 消息元数据（包含图片移除状态）
     */
    private data class MessageWithMeta(
        val message: ModelMessage,
        var imagesRemoved: Boolean = false,
        val roundIndex: Int
    )

    private val _messagesWithMeta = mutableListOf<MessageWithMeta>()
    private var _currentRound = 0
    private var _lastToolCalls: List<ModelToolCall> = emptyList()

    // 内部 Provider
    private var internalProvider: ModelProvider = provider ?: ModelManager.getInstance().getProvider()

    init {
        // 如果没有传入 provider 且没有传入 config，则使用 ModelManager
        if (provider == null && config == null) {
            try {
                internalProvider = ModelManager.getInstance().getProvider()
            } catch (e: Exception) {
                Log.w(TAG, "ModelManager 未初始化，请先调用 setConfig 或 initWithXxx 方法")
            }
        } else if (provider == null && config != null) {
            internalProvider = ModelProvider.create(config)
        }
    }

    /**
     * 获取最后一次响应中的 tool_calls
     */
    fun getLastToolCalls(): List<ModelToolCall> = _lastToolCalls

    // ========================================
    // 发送消息
    // ========================================

    /**
     * 发送文本消息
     */
    fun send(text: String): Flow<ModelStreamChunk> {
        return sendInternal(ModelMessage.user(text))
    }

    /**
     * 发送文本 + 单张图片（Base64）
     */
    fun sendWithImage(text: String, imageBase64: String): Flow<ModelStreamChunk> {
        val contents = listOf(
            ModelContent.Text(text),
            ModelContent.Image(imageBase64, isBase64 = true)
        )
        return sendInternal(ModelMessage.multimodal("user", contents))
    }

    /**
     * 发送文本 + 多张图片
     */
    fun sendWithImages(text: String, images: List<String>, isBase64: Boolean = false): Flow<ModelStreamChunk> {
        val contents = mutableListOf<ModelContent>()
        contents.add(ModelContent.Text(text))
        images.forEach { img ->
            contents.add(ModelContent.Image(img, isBase64))
        }
        return sendInternal(ModelMessage.multimodal("user", contents))
    }

    /**
     * 发送消息（支持 Tool Calling）
     *
     * @param userMessage 用户消息
     * @param tools 工具定义列表
     * @return 流式响应
     */
    fun sendWithTools(userMessage: ModelMessage, tools: List<ModelTool>? = null): Flow<ModelStreamChunk> {
        return sendInternalWithTools(userMessage, tools)
    }

    /**
     * 添加助手消息（包含 tool_calls）
     */
    fun addAssistantMessageWithToolCalls(content: String, toolCalls: List<ModelToolCall>) {
        val message = ModelMessage.assistant(content).copy(toolCalls = toolCalls)
        _messagesWithMeta.add(MessageWithMeta(
            message = message,
            imagesRemoved = true,
            roundIndex = _currentRound
        ))
    }

    /**
     * 添加工具结果消息
     */
    fun addToolMessage(content: String, toolCallId: String) {
        val message = ModelMessage.tool(content, toolCallId)
        _messagesWithMeta.add(MessageWithMeta(
            message = message,
            imagesRemoved = true,
            roundIndex = _currentRound
        ))
    }

    // ========================================
    // 历史管理
    // ========================================

    /**
     * 获取对话历史
     */
    fun getHistory(): List<ModelMessage> = _messagesWithMeta.map { it.message }

    /**
     * 获取历史消息数量
     */
    fun getHistoryCount(): Int = _messagesWithMeta.size

    /**
     * 获取当前轮次
     */
    fun getCurrentRound(): Int = _currentRound

    /**
     * 清空对话历史
     */
    fun clear() {
        _messagesWithMeta.clear()
        _currentRound = 0
        Log.d(TAG, "历史已清空")
    }

    /**
     * 移除最近 N 轮对话
     */
    fun removeLastRounds(n: Int = 1) {
        repeat(n * 2) {
            if (_messagesWithMeta.isNotEmpty()) {
                _messagesWithMeta.removeLast()
            }
        }
        Log.d(TAG, "移除最近 $n 轮对话，剩余 ${_messagesWithMeta.size} 条消息")
    }

    /**
     * 手动移除所有历史中的图片
     */
    fun removeAllImages() {
        _messagesWithMeta.forEachIndexed { index, meta ->
            if (!meta.imagesRemoved) {
                val cleaned = removeImagesFromMessage(meta.message)
                _messagesWithMeta[index] = meta.copy(
                    message = cleaned,
                    imagesRemoved = true
                )
            }
        }
        Log.d(TAG, "已移除所有历史图片")
    }

    // ========================================
    // 内部实现
    // ========================================

    private fun sendInternal(userMessage: ModelMessage): Flow<ModelStreamChunk> = callbackFlow {
        _currentRound++
        Log.d(TAG, "开始第 $_currentRound 轮对话")

        // 1. 添加用户消息到历史
        _messagesWithMeta.add(MessageWithMeta(
            message = userMessage,
            imagesRemoved = false,
            roundIndex = _currentRound
        ))

        // 2. 构建完整消息列表（系统提示词 + 历史）
        val allMessages = buildMessagesForRequest()
        Log.d(TAG, "发送 ${allMessages.size} 条消息（含系统提示词）")

        // 3. 发送请求并收集响应
        val responseBuilder = StringBuilder()
        val reasoningBuilder = StringBuilder()

        try {
            internalProvider.sendMessages(allMessages).collect { chunk ->
                // 实时转发给调用者
                trySend(chunk)

                // 收集完整响应
                chunk.getReasoningContent()?.let { reasoningBuilder.append(it) }
                chunk.getContent()?.let { responseBuilder.append(it) }
            }

            // 4. 保存 AI 响应到历史
            val assistantMessage = ModelMessage.assistant(responseBuilder.toString())
            _messagesWithMeta.add(MessageWithMeta(
                message = assistantMessage,
                imagesRemoved = true,
                roundIndex = _currentRound
            ))

            Log.d(TAG, "AI 响应已保存，当前历史 ${_messagesWithMeta.size} 条")

            // 5. 自动移除旧图片
            if (autoRemoveImages) {
                maybeRemoveOldImages()
            }

            // 6. 裁剪历史长度
            trimHistoryIfNeeded()

        } catch (e: Exception) {
            Log.e(TAG, "发送消息失败: ${e.message}", e)
            // 移除失败的用户消息
            if (_messagesWithMeta.isNotEmpty() && _messagesWithMeta.last().roundIndex == _currentRound) {
                _messagesWithMeta.removeLast()
            }
            trySend(ModelStreamChunk(
                choices = listOf(ModelStreamChoice(
                    delta = ModelDelta(content = "请求失败: ${e.message}")
                ))
            ))
        }

        close()
    }.flowOn(Dispatchers.IO)

    /**
     * 发送消息（支持 Tool Calling）
     */
    private fun sendInternalWithTools(userMessage: ModelMessage, tools: List<ModelTool>?): Flow<ModelStreamChunk> = callbackFlow {
        _currentRound++
        Log.d(TAG, "开始第 $_currentRound 轮对话（工具调用模式）")

        // 1. 添加用户消息到历史
        _messagesWithMeta.add(MessageWithMeta(
            message = userMessage,
            imagesRemoved = false,
            roundIndex = _currentRound
        ))

        // 2. 构建完整消息列表（系统提示词 + 历史）
        val allMessages = buildMessagesForRequest()
        Log.d(TAG, "发送 ${allMessages.size} 条消息（含工具）")

        // 3. 发送请求并收集响应
        val responseBuilder = StringBuilder()
        val reasoningBuilder = StringBuilder()
        val toolCallsList = mutableListOf<ModelToolCall>()

        try {
            internalProvider.sendMessagesWithTools(allMessages, tools).collect { chunk ->
                // 实时转发给调用者
                trySend(chunk)

                // 调试：打印 chunk 的 tool_calls
                val delta = chunk.getDelta()
                if (delta?.toolCalls != null) {
                    Log.d(TAG, "收到 tool_calls: ${delta.toolCalls}")
                }

                // 收集完整响应
                chunk.getReasoningContent()?.let { reasoningBuilder.append(it) }
                chunk.getContent()?.let { responseBuilder.append(it) }

                // 收集 tool_calls
                chunk.getToolCalls()?.let {
                    toolCallsList.addAll(it)
                    _lastToolCalls = toolCallsList
                    Log.d(TAG, "已收集 tool_calls，数量: ${it.size}")
                }
            }

            // 4. 保存 AI 响应到历史
            val assistantMessage = ModelMessage.assistant(responseBuilder.toString())
            _messagesWithMeta.add(MessageWithMeta(
                message = assistantMessage,
                imagesRemoved = true,
                roundIndex = _currentRound
            ))

            Log.d(TAG, "AI 响应已保存，tool_calls 数量: ${toolCallsList.size}")

            // 5. 自动移除旧图片
            if (autoRemoveImages) {
                maybeRemoveOldImages()
            }

            // 6. 裁剪历史长度
            trimHistoryIfNeeded()

        } catch (e: Exception) {
            Log.e(TAG, "发送消息失败: ${e.message}", e)
            // 移除失败的用户消息
            if (_messagesWithMeta.isNotEmpty() && _messagesWithMeta.last().roundIndex == _currentRound) {
                _messagesWithMeta.removeLast()
            }
            trySend(ModelStreamChunk(
                choices = listOf(ModelStreamChoice(
                    delta = ModelDelta(content = "请求失败: ${e.message}")
                ))
            ))
        }

        close()
    }.flowOn(Dispatchers.IO)

    /**
     * 构建请求消息列表
     */
    private fun buildMessagesForRequest(): List<ModelMessage> {
        val result = mutableListOf<ModelMessage>()

        // 添加系统提示词
        systemPrompt?.let {
            result.add(ModelMessage.system(it))
        }

        // 添加历史消息
        result.addAll(_messagesWithMeta.map { it.message })

        return result
    }

    /**
     * 检查并移除历史中的图片
     */
    private fun maybeRemoveOldImages() {
        var removedCount = 0

        _messagesWithMeta.forEachIndexed { index, meta ->
            // 跳过已处理的
            if (meta.imagesRemoved) return@forEachIndexed

            // 跳过最近的几轮
            if (_currentRound - meta.roundIndex < removeImagesAfterRounds) return@forEachIndexed

            // 从消息中移除图片
            val cleanedMessage = removeImagesFromMessage(meta.message)
            if (cleanedMessage != meta.message) {
                _messagesWithMeta[index] = meta.copy(
                    message = cleanedMessage,
                    imagesRemoved = true
                )
                removedCount++
                Log.d(TAG, "第 ${meta.roundIndex} 轮消息的图片已移除")
            }
        }

        if (removedCount > 0) {
            Log.d(TAG, "共移除 $removedCount 条消息中的图片")
        }
    }

    /**
     * 从消息中移除图片，保留文本
     */
    private fun removeImagesFromMessage(message: ModelMessage): ModelMessage {
        val content = message.content

        // 纯文本，无需处理
        if (content is JsonPrimitive) return message

        // 多模态内容，提取文本
        if (content is JsonArray) {
            val textContents = mutableListOf<String>()

            content.forEach { item ->
                if (item is JsonObject) {
                    val typeElement = item["type"]
                    val type = if (typeElement is JsonPrimitive) typeElement.content else null
                    if (type == "text") {
                        val textElement = item["text"]
                        val text = if (textElement is JsonPrimitive) textElement.content else null
                        text?.let { textContents.add(it) }
                    }
                }
            }

            if (textContents.isEmpty()) {
                return ModelMessage(
                    role = message.role,
                    content = JsonPrimitive("[图片/视频/文件已过期]")
                )
            }

            return ModelMessage(
                role = message.role,
                content = JsonPrimitive(textContents.joinToString("\n"))
            )
        }

        return message
    }

    /**
     * 裁剪历史长度
     */
    private fun trimHistoryIfNeeded() {
        while (_messagesWithMeta.size > maxHistoryMessages) {
            if (_messagesWithMeta.size >= 2) {
                _messagesWithMeta.removeAt(0)
                _messagesWithMeta.removeAt(0)
                Log.d(TAG, "裁剪历史，移除最早的 1 轮对话")
            } else {
                _messagesWithMeta.removeAt(0)
            }
        }
    }
}