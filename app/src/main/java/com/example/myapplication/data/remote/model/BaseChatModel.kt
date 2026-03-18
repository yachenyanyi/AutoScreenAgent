package com.example.autoscreenagent.data.remote.model

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.JsonObject
import kotlin.reflect.KClass

/**
 * 可运行的 ChatModel 接口
 *
 * 参考 LangChain 的 Runnable[LanguageModelInput, AIMessage] 设计
 * 支持链式调用和组合操作
 */
interface ChatModelRunnable {
    /**
     * 同步调用（纯文本）
     */
    suspend fun invoke(input: String): ChatResult

    /**
     * 同步调用（文本 + 图片）
     */
    suspend fun invoke(input: String, imageBase64: String): ChatResult

    /**
     * 流式调用
     */
    fun stream(input: String): Flow<ChatChunk>

    /**
     * 绑定工具（返回新的 Runnable）
     */
    fun bindTools(tools: List<Tool>, toolChoice: ToolChoice? = null): ChatModelRunnable

    /**
     * 结构化输出
     */
    fun <T : Any> withStructuredOutput(schema: KClass<T>): StructuredOutputRunnable<T>
}

/**
 * 结构化输出 Runnable
 *
 * 参考 LangChain 的 with_structured_output 返回类型
 */
interface StructuredOutputRunnable<T : Any> {
    /**
     * 调用并返回结构化结果
     */
    suspend fun invoke(input: String): T?

    /**
     * 获取原始结果（当 includeRaw = true 时）
     */
    suspend fun invokeWithRaw(input: String): Pair<T?, ChatResult>
}

/**
 * ChatModel 抽象基类
 *
 * 参考 LangChain 的 BaseChatModel 设计，提供：
 * - 核心抽象方法（子类实现）
 * - 公共 API（invoke, stream）
 * - 高级功能（bindTools, withRetry）
 * - 对话历史管理
 *
 * 使用示例：
 * ```kotlin
 * val model = ZhipuChatModel(config)
 *
 * // 简单调用
 * val result = model.invoke("你好")
 *
 * // 流式调用
 * model.stream("你好").collect { chunk ->
 *     print(chunk.content)
 * }
 *
 * // 带工具调用
 * val modelWithTools = model.bindTools(myTools)
 * val result = modelWithTools.invoke("帮我查天气")
 * ```
 */
abstract class BaseChatModel(
    protected val config: ChatModelConfig,
    protected val conversationConfig: ConversationConfig = ConversationConfig.DEFAULT
) : ChatModelRunnable {
    companion object {
        private const val TAG = "BaseChatModel"
    }

    // ========================================
    // 核心抽象方法（子类必须实现）
    // ========================================

    /**
     * 同步生成响应（核心方法）
     *
     * 参考 LangChain: _generate(messages, stop=None, run_manager=None, **kwargs)
     *
     * @param messages 消息列表
     * @param tools 工具列表（可选）
     * @param stop 停止词列表（可选）
     * @param toolChoice 工具选择策略（可选）
     * @return 完整响应
     */
    protected abstract suspend fun _generate(
        messages: List<ChatMessage>,
        tools: List<Tool>? = null,
        stop: List<String>? = null,
        toolChoice: ToolChoice? = null
    ): ChatResult

    /**
     * 流式生成响应（核心方法）
     *
     * 参考 LangChain: _stream(messages, stop=None, run_manager=None, **kwargs)
     *
     * @param messages 消息列表
     * @param tools 工具列表（可选）
     * @param stop 停止词列表（可选）
     * @param toolChoice 工具选择策略（可选）
     * @return 流式响应块
     */
    protected abstract fun _stream(
        messages: List<ChatMessage>,
        tools: List<Tool>? = null,
        stop: List<String>? = null,
        toolChoice: ToolChoice? = null
    ): Flow<ChatChunk>

    /**
     * 获取厂商类型
     */
    abstract val provider: ModelProvider

    // ========================================
    // 公共 API
    // ========================================

    /**
     * 同步调用（纯文本）
     *
     * 实现 ChatModelRunnable 接口
     */
    override suspend fun invoke(input: String): ChatResult {
        return invoke(ChatMessage.user(input), stop = null)
    }

    /**
     * 同步调用（文本 + 图片）
     *
     * 实现 ChatModelRunnable 接口
     */
    override suspend fun invoke(input: String, imageBase64: String): ChatResult {
        return invoke(ChatMessage.userWithImage(input, imageBase64), stop = null)
    }

    /**
     * 同步调用（纯文本，支持停止词）
     *
     * @param input 用户输入
     * @param stop 停止词列表（可选）
     */
    suspend fun invoke(input: String, stop: List<String>?): ChatResult {
        return invoke(ChatMessage.user(input), stop = stop)
    }

    /**
     * 同步调用（文本 + 图片，支持停止词）
     *
     * @param input 用户输入
     * @param imageBase64 图片 Base64 编码
     * @param stop 停止词列表（可选）
     */
    suspend fun invoke(input: String, imageBase64: String, stop: List<String>?): ChatResult {
        return invoke(ChatMessage.userWithImage(input, imageBase64), stop = stop)
    }

    /**
     * 同步调用（消息）
     *
     * 参考 LangChain: invoke(input, config, stop, **kwargs) -> AIMessage
     *
     * @param message 消息
     * @param tools 工具列表（可选）
     * @param stop 停止词列表（可选）
     * @param toolChoice 工具选择策略（可选）
     */
    suspend fun invoke(
        message: ChatMessage,
        tools: List<Tool>? = null,
        stop: List<String>? = null,
        toolChoice: ToolChoice? = null
    ): ChatResult {
        addToHistory(message)
        val messages = getHistoryForRequest()

        val result = try {
            _generate(messages, tools, stop, toolChoice)
        } catch (e: Exception) {
            Log.e(TAG, "调用失败: ${e.message}", e)
            // 异常时不添加历史（参考审查报告建议）
            return ChatResult.error(e.message ?: "Unknown error")
        }

        // 保存响应到历史
        if (result.toolCalls != null) {
            addToHistory(ChatMessage.assistantWithToolCalls(result.toolCalls, result.content))
        } else {
            addToHistory(ChatMessage.assistant(result.content))
        }

        // 自动清理图片
        if (conversationConfig.autoRemoveImages) {
            maybeRemoveOldImages()
        }

        // 裁剪历史
        trimHistoryIfNeeded()

        return result
    }

    /**
     * 流式调用（纯文本）
     *
     * 实现 ChatModelRunnable 接口
     */
    override fun stream(input: String): Flow<ChatChunk> {
        return stream(ChatMessage.user(input), stop = null)
    }

    /**
     * 流式调用（纯文本，支持停止词）
     *
     * @param input 用户输入
     * @param stop 停止词列表（可选）
     */
    fun stream(input: String, stop: List<String>?): Flow<ChatChunk> {
        return stream(ChatMessage.user(input), stop = stop)
    }

    /**
     * 流式调用（消息）
     *
     * 参考 LangChain: stream(input, config, stop, **kwargs) -> Iterator[AIMessageChunk]
     *
     * @param message 消息
     * @param tools 工具列表（可选）
     * @param stop 停止词列表（可选）
     * @param toolChoice 工具选择策略（可选）
     */
    fun stream(
        message: ChatMessage,
        tools: List<Tool>? = null,
        stop: List<String>? = null,
        toolChoice: ToolChoice? = null
    ): Flow<ChatChunk> {
        addToHistory(message)
        val messages = getHistoryForRequest()

        val contentBuilder = StringBuilder()
        val reasoningBuilder = StringBuilder()
        val toolCallsList = mutableListOf<ToolCall>()
        var hasError = false

        return _stream(messages, tools, stop, toolChoice)
            .onEach { chunk ->
                // 检查是否有错误标志
                if (chunk.error != null) {
                    hasError = true
                }
                chunk.content?.let { contentBuilder.append(it) }
                chunk.reasoningContent?.let { reasoningBuilder.append(it) }
                chunk.toolCalls?.let { toolCallsList.addAll(it) }
            }
            .onCompletion { cause ->
                // 区分正常结束和异常结束（参考审查报告建议）
                if (cause != null || hasError) {
                    // 异常或有错误时不保存历史
                    Log.w(TAG, "流式调用异常结束: ${cause?.message}")
                    return@onCompletion
                }

                // 保存响应到历史
                val content = contentBuilder.toString()
                if (toolCallsList.isNotEmpty()) {
                    addToHistory(ChatMessage.assistantWithToolCalls(toolCallsList, content))
                } else {
                    addToHistory(ChatMessage.assistant(content))
                }

                // 自动清理
                if (conversationConfig.autoRemoveImages) {
                    maybeRemoveOldImages()
                }
                trimHistoryIfNeeded()
            }
    }

    // ========================================
    // 高级功能
    // ========================================

    /**
     * 绑定工具
     *
     * 参考 LangChain: bind_tools(tools, *, tool_choice=None) -> Runnable
     * 返回一个新的 ChatModelRunnable，自动使用指定的工具
     *
     * @param tools 工具列表
     * @param toolChoice 工具选择策略：AUTO（自动）、ANY（必须调用）、NONE（不调用）
     * @return ChatModelRunnable 实例，支持链式调用
     */
    override fun bindTools(tools: List<Tool>, toolChoice: ToolChoice?): ChatModelRunnable {
        return ToolBoundChatModel(this, tools, toolChoice)
    }

    /**
     * 绑定工具（可变参数）
     */
    fun bindTools(vararg tools: Tool): ChatModelRunnable {
        return bindTools(tools.toList())
    }

    /**
     * 结构化输出
     *
     * 参考 LangChain: with_structured_output(schema, *, include_raw=False) -> Runnable
     * 返回一个 Runnable，输出符合指定 schema 的结构化数据
     *
     * @param schema 数据类类型（支持 Kotlin data class）
     * @return StructuredOutputRunnable 实例
     */
    override fun <T : Any> withStructuredOutput(schema: KClass<T>): StructuredOutputRunnable<T> {
        return StructuredOutputRunnableImpl(this, schema, false)
    }

    /**
     * 结构化输出（带原始结果选项）
     *
     * @param schema 数据类类型（支持 Kotlin data class）
     * @param includeRaw 是否包含原始结果
     * @return StructuredOutputRunnable 实例
     */
    fun <T : Any> withStructuredOutput(schema: KClass<T>, includeRaw: Boolean): StructuredOutputRunnable<T> {
        return StructuredOutputRunnableImpl(this, schema, includeRaw)
    }

    /**
     * 添加重试机制
     */
    fun withRetry(maxRetries: Int = 3): RetryChatModel {
        return RetryChatModel(this, maxRetries)
    }

    // ========================================
    // 对话历史管理
    // ========================================

    private val _history = mutableListOf<HistoryEntry>()
    private var _currentRound = 0

    /**
     * 获取对话历史
     */
    val history: List<ChatMessage> get() = _history.map { it.message }

    /**
     * 获取当前轮次
     */
    val currentRound: Int get() = _currentRound

    /**
     * 系统提示词
     */
    var systemPrompt: String? = conversationConfig.systemPrompt

    /**
     * 添加消息到历史
     */
    fun addToHistory(message: ChatMessage) {
        if (message.role == ChatRole.USER || message.role == ChatRole.TOOL) {
            _currentRound++
        }
        _history.add(HistoryEntry(message, _currentRound))
        Log.d(TAG, "添加消息到历史，当前轮次: $_currentRound，历史长度: ${_history.size}")
    }

    /**
     * 添加工具结果到历史
     */
    fun addToolResult(result: ToolResult) {
        addToHistory(ChatMessage.toolResult(result.result, result.toolCallId))
    }

    /**
     * 清空历史
     */
    fun clearHistory() {
        _history.clear()
        _currentRound = 0
        Log.d(TAG, "历史已清空")
    }

    /**
     * 获取用于请求的消息列表
     */
    fun getHistoryForRequest(): List<ChatMessage> {
        val result = mutableListOf<ChatMessage>()

        // 添加系统提示词
        systemPrompt?.let {
            result.add(ChatMessage.system(it))
        }

        // 添加历史消息
        result.addAll(_history.map { it.message })

        return result
    }

    /**
     * 检查并移除历史中的旧图片
     */
    private fun maybeRemoveOldImages() {
        val removeAfter = conversationConfig.removeImagesAfterRounds
        var removedCount = 0

        _history.forEachIndexed { index, entry ->
            // 跳过已处理的
            if (entry.imagesRemoved) return@forEachIndexed

            // 跳过最近的几轮
            if (_currentRound - entry.round < removeAfter) return@forEachIndexed

            // 移除图片
            val cleaned = entry.message.removeImages()
            if (cleaned != entry.message) {
                _history[index] = entry.copy(
                    message = cleaned,
                    imagesRemoved = true
                )
                removedCount++
            }
        }

        if (removedCount > 0) {
            Log.d(TAG, "移除了 $removedCount 条消息中的图片")
        }
    }

    /**
     * 裁剪历史长度
     */
    private fun trimHistoryIfNeeded() {
        val maxMessages = conversationConfig.maxHistoryMessages
        while (_history.size > maxMessages) {
            _history.removeAt(0)
            Log.d(TAG, "裁剪历史，当前长度: ${_history.size}")
        }
    }

    // ========================================
    // 辅助类
    // ========================================

    /**
     * 历史记录条目
     */
    private data class HistoryEntry(
        val message: ChatMessage,
        val round: Int,
        val imagesRemoved: Boolean = false
    )
}

/**
 * 工具绑定的 ChatModel
 *
 * 参考 LangChain 的 ToolRunnable 设计
 * 实现 ChatModelRunnable 接口，支持链式调用
 */
class ToolBoundChatModel(
    private val baseModel: BaseChatModel,
    private val tools: List<Tool>,
    private val toolChoice: ToolChoice? = null
) : ChatModelRunnable {

    /**
     * 将 ToolChoice 转换为 API 参数
     */
    internal fun getToolChoiceParam(): String? {
        return when (toolChoice) {
            ToolChoice.AUTO -> "auto"
            ToolChoice.ANY -> "any"
            ToolChoice.NONE -> "none"
            null -> null
        }
    }

    override suspend fun invoke(input: String): ChatResult {
        return baseModel.invoke(ChatMessage.user(input), tools, toolChoice = toolChoice)
    }

    override suspend fun invoke(input: String, imageBase64: String): ChatResult {
        return baseModel.invoke(ChatMessage.userWithImage(input, imageBase64), tools, toolChoice = toolChoice)
    }

    override fun stream(input: String): Flow<ChatChunk> {
        return baseModel.stream(ChatMessage.user(input), tools, toolChoice = toolChoice)
    }

    override fun bindTools(newTools: List<Tool>, newToolChoice: ToolChoice?): ChatModelRunnable {
        // 合并工具列表
        val mergedTools = tools + newTools
        return ToolBoundChatModel(baseModel, mergedTools, newToolChoice ?: toolChoice)
    }

    override fun <T : Any> withStructuredOutput(schema: KClass<T>): StructuredOutputRunnable<T> {
        // 将 schema 转换为工具，然后绑定
        val schemaTool = SchemaTool.fromKClass(schema)
        return StructuredOutputRunnableImpl(
            ToolBoundChatModel(baseModel, tools + schemaTool, ToolChoice.ANY),
            schema,
            false
        )
    }
}

/**
 * Schema 工具（用于结构化输出）
 */
internal class SchemaTool private constructor(
    tool: Tool
) {
    companion object {
        fun fromKClass(kClass: KClass<*>): Tool {
            val name = kClass.simpleName ?: "output"
            val description = "Extract structured data matching the schema"
            // 简化实现：返回空的 parameters，实际实现需要通过反射或注解解析 schema
            val parameters = JsonObject(emptyMap())
            return Tool.create(name, description, parameters)
        }
    }
}

/**
 * 结构化输出 Runnable 实现
 */
internal class StructuredOutputRunnableImpl<T : Any>(
    private val runnable: ChatModelRunnable,
    private val schema: KClass<T>,
    private val includeRaw: Boolean
) : StructuredOutputRunnable<T> {

    override suspend fun invoke(input: String): T? {
        val result = runnable.invoke(input)
        if (result.hasError()) return null

        // 简化实现：尝试解析工具调用参数或 JSON 内容
        // 实际实现需要根据 schema 解析
        return null
    }

    override suspend fun invokeWithRaw(input: String): Pair<T?, ChatResult> {
        val result = runnable.invoke(input)
        val parsed = if (!result.hasError()) {
            // 尝试解析结构化数据
            null
        } else null
        return parsed to result
    }
}

/**
 * 带重试的 ChatModel
 *
 * 参考 LangChain 的 with_retry 设计
 * 支持指数退避和异常过滤
 */
class RetryChatModel(
    private val baseModel: BaseChatModel,
    private val maxRetries: Int,
    private val config: RetryConfig = RetryConfig.DEFAULT
) : ChatModelRunnable {

    /**
     * 重试配置
     */
    data class RetryConfig(
        val initialDelayMs: Long = 1000,
        val maxDelayMs: Long = 10000,
        val multiplier: Double = 2.0,
        val jitter: Double = 0.1,
        val retryOn: (Exception) -> Boolean = { true }
    ) {
        companion object {
            val DEFAULT = RetryConfig()
        }
    }

    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var lastError: Exception? = null
        var delay = config.initialDelayMs

        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                // 检查是否应该重试
                if (!config.retryOn(e)) {
                    throw e
                }
                lastError = e
                Log.w("RetryChatModel", "第 ${attempt + 1} 次尝试失败: ${e.message}")

                // 最后一次尝试不需要等待
                if (attempt < maxRetries - 1) {
                    // 添加抖动
                    val jitterMs = (delay * config.jitter * Math.random()).toLong()
                    kotlinx.coroutines.delay(delay + jitterMs)

                    // 指数退避
                    delay = minOf((delay * config.multiplier).toLong(), config.maxDelayMs)
                }
            }
        }
        throw lastError ?: Exception("重试 $maxRetries 次后仍然失败")
    }

    override suspend fun invoke(input: String): ChatResult {
        return try {
            withRetry { baseModel.invoke(input) }
        } catch (e: Exception) {
            ChatResult.error(e.message ?: "Unknown error")
        }
    }

    override suspend fun invoke(input: String, imageBase64: String): ChatResult {
        return try {
            withRetry { baseModel.invoke(input, imageBase64) }
        } catch (e: Exception) {
            ChatResult.error(e.message ?: "Unknown error")
        }
    }

    override fun stream(input: String): Flow<ChatChunk> {
        // 流式调用暂不支持重试（会导致重复流）
        return baseModel.stream(input)
    }

    override fun bindTools(tools: List<Tool>, toolChoice: ToolChoice?): ChatModelRunnable {
        return RetryChatModel(baseModel.bindTools(tools, toolChoice) as BaseChatModel, maxRetries, config)
    }

    override fun <T : Any> withStructuredOutput(schema: KClass<T>): StructuredOutputRunnable<T> {
        return baseModel.withStructuredOutput(schema)
    }
}