package com.example.autoscreenagent.data.remote.zhipu

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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 智谱对话会话 - 自动管理多模态对话历史
 *
 * 功能：
 * - 自动记录对话历史（支持多模态）
 * - N 轮后自动移除图片，节省 token
 * - 支持系统提示词
 * - 历史长度限制
 *
 * 使用示例：
 * ```kotlin
 * val conversation = ZhipuConversation(
 *     systemPrompt = "你是一个手机操作助手",
 *     removeImagesAfterRounds = 3
 * )
 *
 * // 文本对话
 * conversation.send("你好").collect { chunk ->
 *     print(chunk.getContent())
 * }
 *
 * // 图片对话
 * conversation.sendWithImage("描述这张截图", base64).collect { ... }
 *
 * // AI 会记住历史（包括图片）
 * conversation.send("刚才那张图里有几个按钮？").collect { ... }
 * ```
 */
class ZhipuConversation(
    private val client: ZhipuClient = ZhipuClient(),
    private val systemPrompt: String? = null,
    private val maxHistoryMessages: Int = 20,
    private val removeImagesAfterRounds: Int = 3,
    private val autoRemoveImages: Boolean = true
) {
    companion object {
        private const val TAG = "ZhipuConversation"
    }

    /**
     * 消息元数据（包含图片移除状态）
     */
    private data class MessageWithMeta(
        val message: ZhipuMessage,
        var imagesRemoved: Boolean = false,
        val roundIndex: Int
    )

    private val _messagesWithMeta = mutableListOf<MessageWithMeta>()
    private var _currentRound = 0
    private var _lastToolCalls: List<ToolCall> = emptyList()

    /**
     * 获取最后一次响应中的 tool_calls
     */
    fun getLastToolCalls(): List<ToolCall> = _lastToolCalls

    // ========================================
    // 发送消息
    // ========================================

    /**
     * 发送文本消息
     */
    fun send(text: String): Flow<ZhipuStreamChunk> {
        return sendInternal(ZhipuMessage.user(text))
    }

    /**
     * 发送文本 + 单张图片（Base64）
     */
    fun sendWithImage(text: String, imageBase64: String): Flow<ZhipuStreamChunk> {
        val contents = listOf(
            ZhipuContent.Text(text),
            ZhipuContent.Image(imageBase64, isBase64 = true)
        )
        return sendInternal(ZhipuMessage.multimodal("user", contents))
    }

    /**
     * 发送文本 + 多张图片
     */
    fun sendWithImages(text: String, images: List<String>, isBase64: Boolean = false): Flow<ZhipuStreamChunk> {
        val contents = mutableListOf<ZhipuContent>()
        contents.add(ZhipuContent.Text(text))
        images.forEach { contents.add(ZhipuContent.Image(it, isBase64)) }
        return sendInternal(ZhipuMessage.multimodal("user", contents))
    }

    /**
     * 发送文本 + 视频
     */
    fun sendWithVideo(text: String, videoUrl: String): Flow<ZhipuStreamChunk> {
        val contents = listOf(
            ZhipuContent.Text(text),
            ZhipuContent.Video(videoUrl)
        )
        return sendInternal(ZhipuMessage.multimodal("user", contents))
    }

    /**
     * 发送文本 + 文件
     */
    fun sendWithFile(text: String, fileUrl: String): Flow<ZhipuStreamChunk> {
        val contents = listOf(
            ZhipuContent.Text(text),
            ZhipuContent.File(fileUrl)
        )
        return sendInternal(ZhipuMessage.multimodal("user", contents))
    }

    /**
     * 发送消息（支持 Tool Calling）
     *
     * @param userMessage 用户消息
     * @param tools 工具定义列表
     * @return 流式响应
     */
    fun sendWithTools(userMessage: ZhipuMessage, tools: List<Tool>? = null): Flow<ZhipuStreamChunk> {
        return sendInternalWithTools(userMessage, tools)
    }

    /**
     * 添加助手消息（包含 tool_calls）
     */
    fun addAssistantMessageWithToolCalls(content: String, toolCalls: List<ToolCall>) {
        val message = ZhipuMessage(
            role = "assistant",
            content = kotlinx.serialization.json.JsonPrimitive(content),
            toolCalls = toolCalls
        )
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
        val message = ZhipuMessage.tool(content, toolCallId)
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
    fun getHistory(): List<ZhipuMessage> = _messagesWithMeta.map { it.message }

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
        repeat(n * 2) {  // 每轮 = 2 条消息
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

    private fun sendInternal(userMessage: ZhipuMessage): Flow<ZhipuStreamChunk> = callbackFlow {
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
            client.sendMessages(allMessages).collect { chunk ->
                // 实时转发给调用者
                trySend(chunk)

                // 收集完整响应
                chunk.getReasoningContent()?.let { reasoningBuilder.append(it) }
                chunk.getContent()?.let { responseBuilder.append(it) }
            }

            // 4. 保存 AI 响应到历史
            val assistantMessage = ZhipuMessage.assistant(responseBuilder.toString())
            _messagesWithMeta.add(MessageWithMeta(
                message = assistantMessage,
                imagesRemoved = true,  // 助手消息没有图片
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
            trySend(ZhipuStreamChunk(
                choices = listOf(ZhipuStreamChoice(
                    delta = ZhipuDelta(content = "请求失败: ${e.message}")
                ))
            ))
        }

        close()
    }.flowOn(Dispatchers.IO)

    /**
     * 发送消息（支持 Tool Calling）
     */
    private fun sendInternalWithTools(userMessage: ZhipuMessage, tools: List<Tool>?): Flow<ZhipuStreamChunk> = callbackFlow {
        _lastToolCalls = emptyList()  // 每次请求前清空，避免保留上一轮的 tool_calls
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
        val toolCallsList = mutableListOf<ToolCall>()

        try {
            client.sendMessagesWithTools(allMessages, tools).collect { chunk ->
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
                chunk.getDelta()?.toolCalls?.let {
                    toolCallsList.addAll(it)
                    _lastToolCalls = toolCallsList
                    Log.d(TAG, "已收集 tool_calls，数量: ${it.size}")
                }
            }

            // 4. 保存 AI 响应到历史
            val assistantMessage = ZhipuMessage.assistant(responseBuilder.toString())
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
            trySend(ZhipuStreamChunk(
                choices = listOf(ZhipuStreamChoice(
                    delta = ZhipuDelta(content = "请求失败: ${e.message}")
                ))
            ))
        }

        close()
    }.flowOn(Dispatchers.IO)

    /**
     * 构建请求消息列表
     */
    private fun buildMessagesForRequest(): List<ZhipuMessage> {
        val result = mutableListOf<ZhipuMessage>()

        // 添加系统提示词
        systemPrompt?.let {
            result.add(ZhipuMessage.system(it))
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
    private fun removeImagesFromMessage(message: ZhipuMessage): ZhipuMessage {
        val content = message.content

        // 纯文本，无需处理
        if (content is JsonPrimitive) return message

        // 多模态内容，提取文本
        if (content is JsonArray) {
            val textContents = mutableListOf<String>()

            content.forEach { item ->
                if (item is JsonObject) {
                    val type = item["type"]?.jsonPrimitive?.content
                    if (type == "text") {
                        item["text"]?.jsonPrimitive?.content?.let { textContents.add(it) }
                    }
                    // 跳过 image_url, video_url, file_url
                }
            }

            if (textContents.isEmpty()) {
                // 没有文本，用占位符替换
                return ZhipuMessage(
                    role = message.role,
                    content = JsonPrimitive("[图片/视频/文件已过期]")
                )
            }

            // 合并文本内容
            return ZhipuMessage(
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
            // 移除最早的一对（用户 + 助手）
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