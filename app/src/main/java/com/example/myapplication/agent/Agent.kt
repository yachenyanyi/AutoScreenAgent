package com.example.autoscreenagent.agent

import android.content.Context
import android.util.Log
import com.example.autoscreenagent.accessibility.ScreenshotManager
import com.example.autoscreenagent.ai.CommandExecutor
import com.example.autoscreenagent.data.remote.model.BaseChatModel
import com.example.autoscreenagent.data.remote.model.ChatMessage
import com.example.autoscreenagent.data.remote.model.ChatModelRunnable
import com.example.autoscreenagent.data.remote.model.Tool
import com.example.autoscreenagent.data.remote.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AI Agent - 通用 Agent 框架
 *
 * 使用 BaseChatModel 进行多轮对话和任务执行
 *
 * 使用示例：
 * ```kotlin
 * val model = ZhipuChatModel(ChatModelConfig.zhipu("your-key"))
 * val agent = Agent(
 *     chatModel = model,
 *     config = AgentConfig(),
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
 * val result = agent.runWithTools(goal = "打开微信发送消息给小明")
 * ```
 */
class Agent(
    private val chatModel: BaseChatModel,
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

    // 工具绑定的模型（在 init 中初始化，避免重复绑定）
    private var modelWithTools: ChatModelRunnable? = null

    // 使用的工具列表
    private var currentTools: List<Tool>? = null

    init {
        // 设置系统提示词
        chatModel.systemPrompt = config.systemPrompt
    }

    /**
     * 获取或创建工具绑定的模型（延迟初始化，按需绑定）
     *
     * 修复审查报告指出的问题：避免每次运行都重新绑定工具
     */
    private fun getOrCreateModelWithTools(tools: List<Tool>): ChatModelRunnable {
        // 如果工具列表变化，需要重新绑定
        if (modelWithTools == null || currentTools != tools) {
            modelWithTools = chatModel.bindTools(tools)
            currentTools = tools
            Log.d(TAG, "工具已绑定: ${tools.map { it.function.name }}")
        }
        return modelWithTools!!
    }

    /**
     * 使用工具调用运行 Agent
     */
    suspend fun runWithTools(
        goal: String,
        tools: List<Tool> = AgentTools.tools,
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
        chatModel.clearHistory()

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

        // 获取工具绑定的模型（延迟初始化，避免重复绑定）
        val modelWithTools = getOrCreateModelWithTools(tools)

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

                onLog("发送消息给 AI...")

                // 发送消息并收集响应（在 IO 线程执行网络请求）
                val result = withContext(Dispatchers.IO) {
                    if (lastScreenshotBase64 != null) {
                        modelWithTools!!.invoke(userMessageText, lastScreenshotBase64!!)
                    } else {
                        modelWithTools!!.invoke(userMessageText)
                    }
                }

                if (result.hasError()) {
                    onLog("错误：${result.error?.message}")
                    val failedState = AgentState.Failed(result.error?.message ?: "Unknown error")
                    _state.value = failedState
                    return failedState
                }

                // 检查是否有工具调用
                if (result.hasToolCalls()) {
                    onLog("检测到 ${result.toolCalls!!.size} 个工具调用")

                    // 执行每个工具
                    for (toolCall in result.toolCalls!!) {
                        val functionName = toolCall.function.name
                        val arguments = toolCall.function.arguments

                        onLog("执行工具: $functionName")

                        // 执行工具
                        val toolResult = toolExecutor!!.executeTool(functionName, arguments)
                        onLog("工具结果: $toolResult")

                        // 添加工具结果消息到对话
                        chatModel.addToolResult(
                            ToolResult.success(toolCall.id, functionName, toolResult)
                        )
                    }

                    // 继续下一轮
                    lastScreenshotBase64 = null
                    delay(config.iterationDelay)
                    continue
                }

                // 没有工具调用，任务完成
                onLog("无工具调用，任务结束")
                onLog("AI 回复: ${result.content.take(200)}")

                val completedState = AgentState.Completed(result.content)
                _state.value = completedState
                return completedState
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
     * 构建用户消息（工具调用模式）
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
            |重要提示：
            |1. 使用提供的工具来操作手机
            |2. 点击操作优先使用 tap_by_text 或 tap_by_id
            |3. get_screen_content 仅在无法看到截图时使用
            |4. 任务完成后无需使用工具，直接回复即可
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
            runWithTools(goal, onLog = onLog, onAction = onAction)
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
    fun getConversationHistory(): List<ChatMessage> = chatModel.history

    /**
     * 清空对话历史
     */
    fun clearHistory() {
        chatModel.clearHistory()
    }
}