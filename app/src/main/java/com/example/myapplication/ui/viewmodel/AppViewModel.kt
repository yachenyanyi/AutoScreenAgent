package com.example.autoscreenagent.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import com.example.autoscreenagent.AggregatedMessage
import com.example.autoscreenagent.ChatMessage
import com.example.autoscreenagent.MessageStatus
import com.example.autoscreenagent.accessibility.ScreenshotManager
import com.example.autoscreenagent.ai.CommandExecutor
import com.example.autoscreenagent.agent.Agent
import com.example.autoscreenagent.agent.AgentConfig
import com.example.autoscreenagent.data.remote.AgentConfig as RemoteAgentConfig
import com.example.autoscreenagent.data.remote.model.ChatModelConfig
import com.example.autoscreenagent.data.remote.model.ChatModelFactory
import com.example.autoscreenagent.data.remote.model.ConversationConfig
import com.example.autoscreenagent.data.remote.model.ModelProvider
import com.example.autoscreenagent.data.remote.model.MultiProviderConfig
import com.example.autoscreenagent.ui.screens.loadConfig
import com.example.autoscreenagent.ui.screens.saveConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 消息类型封装
 */
sealed class MessageItem {
    data class Simple(val message: ChatMessage) : MessageItem()
    data class Aggregated(val message: AggregatedMessage) : MessageItem()
}

/**
 * 应用 ViewModel
 * 管理配置、Agent 和消息状态
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val _config = MutableStateFlow(RemoteAgentConfig())
    val config: StateFlow<RemoteAgentConfig> = _config.asStateFlow()

    // 消息列表状态
    private val _messages = MutableStateFlow<List<MessageItem>>(emptyList())
    val messages: StateFlow<List<MessageItem>> = _messages.asStateFlow()

    // Agent 运行状态
    private val _isAgentRunning = MutableStateFlow(false)
    val isAgentRunning: StateFlow<Boolean> = _isAgentRunning.asStateFlow()

    private val _agentStatus = MutableStateFlow("就绪")
    val agentStatus: StateFlow<String> = _agentStatus.asStateFlow()

    // 当前正在收集的消息
    private var currentAggregatedMessage: AggregatedMessage? = null

    // 新 Agent 实例（使用智谱 API）
    private var agent: Agent? = null

    private val prefs: SharedPreferences

    init {
        prefs = application.getSharedPreferences("autoscreenagent_prefs", Context.MODE_PRIVATE)
        loadConfig()
    }

    /**
     * 加载配置
     */
    fun loadConfig() {
        val config = loadConfig(getApplication())
        _config.value = config
    }

    /**
     * 保存配置
     */
    fun saveConfig(newConfig: RemoteAgentConfig) {
        _config.value = newConfig
        saveConfig(getApplication(), newConfig)
        // 清除旧 Agent，使用新配置重新创建
        clearAgent()
    }

    /**
     * 更新多厂商配置
     */
    fun updateProviderConfigs(newProviderConfigs: MultiProviderConfig) {
        val newConfig = _config.value.copy(providerConfigs = newProviderConfigs)
        _config.value = newConfig
        saveConfig(getApplication(), newConfig)
        clearAgent()
    }

    // ==================== 新 Agent 管理（使用 BaseChatModel） ====================

    /**
     * 创建新的 Agent（每次对话都是全新的实例）
     */
    fun createAgent(
        context: Context,
        screenshotManager: ScreenshotManager,
        commandExecutor: CommandExecutor
    ): Agent {
        val config = _config.value

        // 将 ModelProviderType 转换为 ModelProvider
        val provider = config.getProviderType().toModelProvider()

        // 获取当前厂商的配置
        val providerConfig = config.getCurrentProviderConfig()

        // 创建 ChatModel 配置
        val chatModelConfig = ChatModelConfig(
            provider = provider,
            baseUrl = providerConfig.baseUrl,
            apiKey = providerConfig.apiKey,
            model = config.getCurrentModel(),
            temperature = providerConfig.temperature,
            topP = providerConfig.topP,
            maxTokens = providerConfig.maxTokens,
            timeoutSeconds = providerConfig.timeoutSeconds,
            enableThinking = providerConfig.enableThinking
        )

        // 创建对话配置
        val conversationConfig = ConversationConfig(
            systemPrompt = AgentConfig.DEFAULT_SYSTEM_PROMPT,
            maxHistoryMessages = config.maxHistoryMessages,
            removeImagesAfterRounds = config.removeImagesAfterRounds
        )

        // 使用工厂创建 ChatModel
        val chatModel = ChatModelFactory.create(chatModelConfig, conversationConfig)

        // 创建 Agent
        return Agent(
            chatModel = chatModel,
            config = AgentConfig(
                maxIterations = config.maxIterations,
                iterationDelay = config.iterationDelayMs,
                autoCaptureScreenshot = config.autoCaptureScreenshot,
                enableThinking = providerConfig.enableThinking,
                removeImagesAfterRounds = config.removeImagesAfterRounds
            ),
            screenshotManager = screenshotManager,
            commandExecutor = commandExecutor,
            context = context
        )
    }

    /**
     * 获取或创建 Agent（兼容旧代码，已废弃，请使用 createAgent）
     */
    @Deprecated("请使用 createAgent 方法")
    fun getOrCreateAgent(
        context: Context,
        screenshotManager: ScreenshotManager,
        commandExecutor: CommandExecutor
    ): Agent {
        if (agent == null) {
            agent = createAgent(context, screenshotManager, commandExecutor)
        }
        return agent!!
    }

    /**
     * 清除 Agent（用于重新开始对话）
     */
    fun clearAgent() {
        agent?.reset()
        agent = null
    }

    // ==================== 消息管理 ====================

    /**
     * 添加用户消息
     */
    fun addUserMessage(content: String) {
        val newMessage = ChatMessage(content = content, isUser = true)
        _messages.value = _messages.value + MessageItem.Simple(newMessage)
    }

    /**
     * 开始聚合消息（Agent 开始执行时调用）
     */
    fun startAggregatedMessage() {
        currentAggregatedMessage = AggregatedMessage(
            summary = "分析中...",
            details = emptyList(),
            isUser = false,
            status = MessageStatus.RUNNING
        )
        // 先添加一个占位消息
        currentAggregatedMessage?.let {
            _messages.value = _messages.value + MessageItem.Aggregated(it)
        }
    }

    /**
     * 添加日志到当前聚合消息
     */
    fun appendLog(log: String) {
        currentAggregatedMessage?.let { current ->
            val newDetails = current.details + log
            // 提取摘要：优先使用有 ✅ 或 🎉 的日志
            val summary = newDetails.lastOrNull { it.contains("✅") || it.contains("🎉") }
                ?: newDetails.lastOrNull()
                ?: current.summary

            val updated = current.copy(
                details = newDetails,
                summary = summary
            )
            currentAggregatedMessage = updated

            // 更新列表中的消息
            _messages.value = _messages.value.map { item ->
                if (item is MessageItem.Aggregated && item.message.id == current.id) {
                    MessageItem.Aggregated(updated)
                } else {
                    item
                }
            }
        }
    }

    /**
     * 完成聚合消息（Agent 执行完成时调用）
     */
    fun completeAggregatedMessage(status: MessageStatus = MessageStatus.SUCCESS, finalMessage: String? = null) {
        currentAggregatedMessage?.let { current ->
            val updated = current.copy(
                status = status,
                summary = finalMessage ?: current.summary
            )
            currentAggregatedMessage = null

            // 更新列表中的消息
            _messages.value = _messages.value.map { item ->
                if (item is MessageItem.Aggregated && item.message.id == current.id) {
                    MessageItem.Aggregated(updated)
                } else {
                    item
                }
            }
        }
    }

    /**
     * 切换消息展开状态
     */
    fun toggleMessageExpanded(messageId: String) {
        _messages.value = _messages.value.map { item ->
            if (item is MessageItem.Aggregated && item.message.id == messageId) {
                MessageItem.Aggregated(item.message.copy(isExpanded = !item.message.isExpanded))
            } else {
                item
            }
        }
    }

    /**
     * 设置 Agent 运行状态
     */
    fun setAgentRunning(running: Boolean) {
        _isAgentRunning.value = running
    }

    /**
     * 设置 Agent 状态文本
     */
    fun setAgentStatus(status: String) {
        _agentStatus.value = status
    }

    /**
     * 清空所有消息
     */
    fun clearMessages() {
        _messages.value = emptyList()
    }
}
