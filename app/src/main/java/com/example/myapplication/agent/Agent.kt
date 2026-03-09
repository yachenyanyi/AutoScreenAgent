package com.example.autoscreenagent.agent

import android.content.Context
import android.util.Log
import com.example.autoscreenagent.ai.AIResponseParser
import com.example.autoscreenagent.ai.CommandExecutor
import com.example.autoscreenagent.ai.ExecutableAction
import com.example.autoscreenagent.data.remote.zhipu.Tool
import com.example.autoscreenagent.data.remote.zhipu.ToolCall
import com.example.autoscreenagent.data.remote.zhipu.ZhipuConversation
import com.example.autoscreenagent.data.remote.zhipu.ZhipuMessage
import com.example.autoscreenagent.data.remote.zhipu.ZhipuStreamChunk
import com.example.autoscreenagent.accessibility.ScreenshotManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * AI Agent - 通用 Agent 框架
 *
 * 使用智谱 API 进行多轮对话和任务执行
 *
 * 使用示例：
 * ```kotlin
 * val agent = Agent(
 *     conversation = ZhipuConversation(systemPrompt = "你是一个手机操作助手..."),
 *     config = AgentConfig(maxIterations = 10),
 *     commandExecutor = commandExecutor,
 *     screenshotManager = screenshotManager
 * )
 *
 * // 监听状态
 * scope.launch {
 *     agent.state.collect { state ->
 *         when (state) {
 *             is AgentState.Running -> updateStatus(state.status)
 *             is AgentState.Completed -> showMessage(state.message)
 *             is AgentState.Failed -> showError(state.error)
 *         }
 *     }
 * }
 *
 * // 运行
 * val result = agent.run(
 *     goal = "打开微信发送消息给小明",
 *     onLog = { log -> appendLog(log) }
 * )
 * ```
 *
 * @param conversation 智谱对话会话（用于 LLM 通信）
 * @param config Agent 配置
 * @param commandExecutor 命令执行器（用于执行 AI 返回的动作）
 * @param screenshotManager 截屏管理器（用于自动截屏）
 * @param context Android 上下文（用于初始化 CommandExecutor）
 */
class Agent(
    private val conversation: ZhipuConversation,
    private val config: AgentConfig = AgentConfig(),
    private val commandExecutor: CommandExecutor? = null,
    private val screenshotManager: ScreenshotManager? = null,
    private val context: Context? = null
) {
    companion object {
        private const val TAG = "Agent"
    }

    // 状态流
    private val _state = MutableStateFlow<AgentState>(AgentState.Idle)
    val state: StateFlow<AgentState> = _state.asStateFlow()

    // 执行控制
    private var currentIteration = 0
    private var isCancelled = false
    private var isRunning = false
    private var currentJob: Job? = null

    // 上一轮执行结果
    private var lastActionResults = mutableListOf<String>()
    private var lastScreenshotBase64: String? = null

    // 工具执行器
    private var toolExecutor: ToolExecutor? = null

    // 日志回调
    private var logCallback: ((String) -> Unit)? = null
    private var actionCallback: ((String, Map<String, Any>) -> Unit)? = null

    /**
     * 运行 Agent
     *
     * @param goal 用户目标
     * @param onLog 日志回调
     * @param onAction 动作回调 (actionName, params)
     * @return 最终状态
     */
    suspend fun run(
        goal: String,
        onLog: (String) -> Unit = {},
        onAction: (String, Map<String, Any>) -> Unit = { _, _ -> }
    ): AgentState {
        // 防止重复运行
        if (isRunning) {
            Log.w(TAG, "Agent 已在运行中")
            return _state.value
        }

        // 重置状态并清除对话历史
        reset()
        conversation.clear()

        this.logCallback = onLog
        this.actionCallback = onAction

        // 初始化状态
        isCancelled = false
        currentIteration = 0
        lastActionResults.clear()
        lastScreenshotBase64 = null

        // 确保有 commandExecutor
        val executor = commandExecutor ?: context?.let { CommandExecutor(it) }
        if (executor == null) {
            Log.e(TAG, "CommandExecutor 未初始化")
            val failedState = AgentState.Failed("CommandExecutor 未初始化")
            _state.value = failedState
            return failedState
        }

        try {
            isRunning = true

            // 开始执行循环
            while (!isCancelled && currentIteration < config.maxIterations) {
                currentIteration++

                // 更新状态
                _state.value = AgentState.Running(
                    iteration = currentIteration,
                    status = "正在执行第 $currentIteration 轮"
                )

                onLog("=== 第 $currentIteration 轮 ===")

                // 检查是否需要截屏
                if (config.autoCaptureScreenshot && screenshotManager != null && context != null) {
                    val base64 = screenshotManager.captureToBase64(context)
                    if (base64 != null) {
                        lastScreenshotBase64 = base64
                        onLog("自动截屏已保存，将发送给 AI")
                    }
                }

                // 构建消息
                val message = buildMessage(goal)
                onLog("发送消息给 AI...")

                // 发送消息并收集响应
                val responseFlow = if (lastScreenshotBase64 != null) {
                    conversation.sendWithImage(
                        buildUserMessage(goal),
                        lastScreenshotBase64!!
                    )
                } else {
                    conversation.send(buildUserMessage(goal))
                }

                val response = collectResponse(responseFlow)

                if (response == null) {
                    onLog("错误：AI 响应解析失败")
                    val failedState = AgentState.Failed("AI 响应解析失败")
                    _state.value = failedState
                    return failedState
                }

                onLog("AI 响应: ${response.take(200)}...")

                // 解析 AI 响应
                val parsedResponse = AIResponseParser.parse(response)
                if (parsedResponse == null) {
                    onLog("错误：AI 响应解析失败")
                    val failedState = AgentState.Failed("AI 响应解析失败")
                    _state.value = failedState
                    return failedState
                }

                onLog("AI 思考: ${parsedResponse.thought}")
                onLog("解析到 ${parsedResponse.actions.size} 个动作")

                // 检查是否完成
                val hasFinishAction = parsedResponse.actions.any {
                    it.action.lowercase() == "finish"
                }

                // 执行动作
                val results = executor.execute(parsedResponse, screenshotManager)

                // 收集执行结果
                val currentResults = mutableListOf<String>()
                for (result in results) {
                    onLog(result.message)
                    currentResults.add(result.message)

                    // 触发动作回调
                    val actionName = parsedResponse.actions.firstOrNull()?.action ?: ""
                    actionCallback?.invoke(actionName, emptyMap())

                    // 如果是截屏操作，保存 Base64
                    if (result is CommandExecutor.ExecuteResult.ScreenshotResult) {
                        lastScreenshotBase64 = result.base64
                    }

                    // 如果是屏幕内容，包含在下一轮消息中
                    if (result is CommandExecutor.ExecuteResult.ScreenContent) {
                        currentResults.add(result.message)
                    }
                }

                lastActionResults = currentResults

                // 如果有完成动作，结束
                if (hasFinishAction) {
                    val completedState = AgentState.Completed("任务已完成")
                    _state.value = completedState
                    return completedState
                }

                // 迭代之间延迟
                delay(config.iterationDelay)
            }

            // 达到最大迭代次数
            if (currentIteration >= config.maxIterations) {
                val maxState = AgentState.Failed("达到最大迭代次数 (${config.maxIterations})")
                _state.value = maxState
                return maxState
            }

            // 被取消
            if (isCancelled) {
                val cancelledState = AgentState.Cancelled("任务已取消")
                _state.value = cancelledState
                return cancelledState
            }

            return _state.value

        } catch (e: Exception) {
            Log.e(TAG, "Agent 执行异常", e)
            onLog("异常: ${e.message}")
            val failedState = AgentState.Failed(e.message ?: "未知错误")
            _state.value = failedState
            return failedState
        } finally {
            isRunning = false
            logCallback = null
            actionCallback = null
        }
    }

    /**
     * 使用 Tool Calling 运行 Agent
     *
     * @param goal 用户目标
     * @param onLog 日志回调
     * @param onAction 动作回调
     * @return 最终状态
     */
    suspend fun runWithTools(
        goal: String,
        onLog: (String) -> Unit = {},
        onAction: (String, Map<String, Any>) -> Unit = { _, _ -> }
    ): AgentState {
        // 防止重复运行
        if (isRunning) {
            Log.w(TAG, "Agent 已在运行中")
            return _state.value
        }

        // 重置状态并清除对话历史
        reset()
        conversation.clear()

        this.logCallback = onLog
        this.actionCallback = onAction

        // 初始化状态
        isCancelled = false
        currentIteration = 0
        lastActionResults.clear()
        lastScreenshotBase64 = null

        // 确保有 context
        val androidContext = context ?: run {
            Log.e(TAG, "Context 未初始化")
            val failedState = AgentState.Failed("Context 未初始化")
            _state.value = failedState
            return failedState
        }

        // 初始化工具执行器
        toolExecutor = ToolExecutor(androidContext)

        try {
            isRunning = true

            // 开始执行循环
            while (!isCancelled && currentIteration < config.maxIterations) {
                currentIteration++

                // 更新状态
                _state.value = AgentState.Running(
                    iteration = currentIteration,
                    status = "正在执行第 $currentIteration 轮（工具调用模式）"
                )

                onLog("=== 第 $currentIteration 轮（工具调用）===")

                // 检查是否需要截屏
                if (config.autoCaptureScreenshot && screenshotManager != null) {
                    val base64 = screenshotManager.captureToBase64(androidContext)
                    if (base64 != null) {
                        lastScreenshotBase64 = base64
                        onLog("自动截屏已保存，将发送给 AI")
                    }
                }

                // 构建用户消息
                val userMessageText = buildUserMessageForTools(goal)
                val userMessage = if (lastScreenshotBase64 != null) {
                    // 创建多模态消息
                    val contents = listOf(
                        com.example.autoscreenagent.data.remote.zhipu.ZhipuContent.Text(userMessageText),
                        com.example.autoscreenagent.data.remote.zhipu.ZhipuContent.Image(lastScreenshotBase64!!, isBase64 = true)
                    )
                    ZhipuMessage.multimodal("user", contents)
                } else {
                    ZhipuMessage.user(userMessageText)
                }

                onLog("发送消息给 AI...")

                // 发送消息（带工具）并收集响应
                val responseFlow = conversation.sendWithTools(userMessage, AgentTools.tools)

                // 收集响应
                val responseBuilder = StringBuilder()
                val reasoningBuilder = StringBuilder()

                responseFlow.collect { chunk ->
                    // 调试：打印原始内容
                    chunk.getContent()?.let {
                        if (it.contains("tool_calls") || it.contains("function")) {
                            Log.d(TAG, "原始响应包含 function/tool_calls: $it")
                        }
                        responseBuilder.append(it)
                    }
                    chunk.getReasoningContent()?.let { reasoningBuilder.append(it) }
                }

                val response = responseBuilder.toString().ifEmpty { reasoningBuilder.toString() }
                Log.d(TAG, "AI 完整响应: ${response.take(500)}")

                if (response.isEmpty()) {
                    onLog("错误：AI 响应为空")
                    val failedState = AgentState.Failed("AI 响应为空")
                    _state.value = failedState
                    return failedState
                }

                // 检查是否有 tool_calls
                val toolCalls = conversation.getLastToolCalls()
                Log.d(TAG, "tool_calls 数量: ${toolCalls.size}")

                if (toolCalls.isNotEmpty()) {
                    onLog("检测到 ${toolCalls.size} 个工具调用")

                    // 处理每个 tool_call
                    for (toolCall in toolCalls) {
                        val functionName = toolCall.function.name
                        val arguments = toolCall.function.arguments
                        val toolCallId = toolCall.id

                        onLog("执行工具: $functionName")

                        // 执行工具
                        val result = toolExecutor!!.executeTool(functionName, arguments)
                        onLog("工具结果: $result")

                        // 添加工具结果消息到对话
                        conversation.addToolMessage(result, toolCallId)
                    }

                    // 获取模型对工具结果的最终响应
                    val finalResponseFlow = conversation.sendWithTools(
                        ZhipuMessage.assistant(""),
                        AgentTools.tools
                    )

                    val finalResponseBuilder = StringBuilder()
                    finalResponseFlow.collect { chunk ->
                        chunk.getContent()?.let { finalResponseBuilder.append(it) }
                    }

                    val finalResponse = finalResponseBuilder.toString()
                    onLog("AI 最终响应: ${finalResponse.take(200)}...")

                    // 保存响应作为反馈
                    lastActionResults = mutableListOf(finalResponse)
                } else {
                    // 没有 tool_calls，直接把 content 作为最终回答结束
                    onLog("无工具调用，任务结束")
                    onLog("AI 回复: ${response.take(200)}")

                    val completedState = AgentState.Completed(response)
                    _state.value = completedState
                    return completedState
                }

                // 迭代之间延迟
                delay(config.iterationDelay)
            }

            // 达到最大迭代次数
            if (currentIteration >= config.maxIterations) {
                val maxState = AgentState.Failed("达到最大迭代次数 (${config.maxIterations})")
                _state.value = maxState
                return maxState
            }

            // 被取消
            if (isCancelled) {
                val cancelledState = AgentState.Cancelled("任务已取消")
                _state.value = cancelledState
                return cancelledState
            }

            return _state.value

        } catch (e: Exception) {
            Log.e(TAG, "Agent 执行异常", e)
            onLog("异常: ${e.message}")
            val failedState = AgentState.Failed(e.message ?: "未知错误")
            _state.value = failedState
            return failedState
        } finally {
            isRunning = false
            logCallback = null
            actionCallback = null
            toolExecutor = null
        }
    }

    /**
     * 构建用户消息（Tool Calling 模式）
     */
    private fun buildUserMessageForTools(goal: String): String {
        val feedback = if (lastActionResults.isNotEmpty()) {
            """
            |执行结果反馈：
            |${lastActionResults.joinToString("\n")}
            |
            |请根据以上执行结果，使用工具完成用户目标。
            """.trimMargin()
        } else {
            "请分析并使用工具完成用户目标。"
        }

        return """
            |用户目标：$goal
            |
            |$feedback
            |
            |重要：
            |1. 使用提供的工具来操作手机
            |2. 每次操作后截屏确认结果
            |3. 任务完成后无需使用工具
            |
        """.trimMargin()
    }

    /**
     * 在协程作用域中异步运行 Agent
     */
    fun runInScope(
        scope: kotlinx.coroutines.CoroutineScope,
        goal: String,
        onLog: (String) -> Unit = {},
        onAction: (String, Map<String, Any>) -> Unit = { _, _ -> }
    ): Job {
        return scope.launch {
            run(goal, onLog, onAction)
        }
    }

    /**
     * 取消执行
     */
    fun cancel() {
        if (!isRunning) {
            Log.d(TAG, "Agent 未在运行，无法取消")
            return
        }

        Log.i(TAG, "取消 Agent 执行")
        isCancelled = true
        currentJob?.cancel()

        _state.value = AgentState.Cancelled("用户取消")
    }

    /**
     * 重置状态
     */
    fun reset() {
        currentIteration = 0
        isCancelled = false
        isRunning = false
        lastActionResults.clear()
        lastScreenshotBase64 = null

        _state.value = AgentState.Idle
        Log.d(TAG, "Agent 已重置")
    }

    /**
     * 获取当前迭代次数
     */
    fun getCurrentIteration(): Int = currentIteration

    /**
     * 是否正在运行
     */
 fun isCurrentlyRunning(): Boolean = isRunning

    /**
     * 获取对话历史
     */
    fun getConversationHistory(): List<Any> = conversation.getHistory()

    /**
     * 清空对话历史
     */
    fun clearHistory() {
        conversation.clear()
    }

    // ==================== 内部方法 ====================

    /**
     * 构建发送给 AI 的消息
     */
    private fun buildMessage(goal: String): String {
        return buildUserMessage(goal)
    }

    /**
     * 构建用户消息
     */
    private fun buildUserMessage(goal: String): String {
        val feedback = if (lastActionResults.isNotEmpty()) {
            """
            |执行结果反馈：
            |${lastActionResults.joinToString("\n")}
            |
            |请根据以上执行结果，分析当前状态并返回下一步操作指令。
            |如果需要获取屏幕内容，请使用 get_screen_content 动作。
            |如果需要截屏，请使用 capture_screenshot 动作。
            """.trimMargin()
        } else {
            "请分析并返回下一步操作指令。"
        }

        return """
            |用户目标：$goal
            |
            |$feedback
            |
            |重要：
            |1. 每次只返回一个操作
            |2. 使用 JSON 格式返回动作：{"action": "操作名称", "参数": "参数值", "_metadata": "do"}
            |3. 可用动作：tap, type_text, swipe, system_back, system_home, launch_app, capture_screenshot, get_screen_content, finish
            |4. 任务完成后必须返回 finish 动作
        """.trimMargin()
    }

    /**
     * 收集 AI 响应（suspend 函数）
     */
    private suspend fun collectResponse(responseFlow: kotlinx.coroutines.flow.Flow<ZhipuStreamChunk>): String? {
        val responseBuilder = StringBuilder()
        val reasoningBuilder = StringBuilder()

        responseFlow.collect { chunk ->
            chunk.getContent()?.let { responseBuilder.append(it) }
            chunk.getReasoningContent()?.let { reasoningBuilder.append(it) }
        }

        // 清理响应：移除 markdown 代码块标记
        val rawResponse = if (responseBuilder.isNotEmpty()) {
            responseBuilder.toString()
        } else {
            reasoningBuilder.toString()
        }

        return cleanJsonResponse(rawResponse)
    }

    /**
     * 清理 JSON 响应，移除 markdown 代码块标记
     */
    private fun cleanJsonResponse(response: String): String {
        var cleaned = response.trim()

        // 移除 ```json ... ``` 或 ``` ... ``` 标记
        if (cleaned.startsWith("```")) {
            val firstNewline = cleaned.indexOf('\n')
            val lastTripleBackticks = cleaned.lastIndexOf("```")
            if (firstNewline > 0 && lastTripleBackticks > firstNewline) {
                cleaned = cleaned.substring(firstNewline + 1, lastTripleBackticks).trim()
            }
        }

        return cleaned
    }

    /**
     * 尝试从响应中解析 message
     */
    private fun tryParseMessage(response: String): String? {
        val cleaned = cleanJsonResponse(response)
        return try {
            val json = JSONObject(cleaned)
            json.optString("message").takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            // 如果不是 JSON，直接返回响应内容
            response.takeIf { it.isNotBlank() }
        }
    }
}