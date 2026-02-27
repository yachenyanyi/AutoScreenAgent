package com.example.autoscreenagent.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.autoscreenagent.AggregatedMessage
import com.example.autoscreenagent.ChatMessage
import com.example.autoscreenagent.MessageStatus
import com.example.autoscreenagent.data.remote.AgentConfig
import com.example.autoscreenagent.data.remote.LangGraphClient
import com.example.autoscreenagent.ui.screens.loadConfig
import com.example.autoscreenagent.ui.screens.saveConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 消息类型封装
 */
sealed class MessageItem {
    data class Simple(val message: ChatMessage) : MessageItem()
    data class Aggregated(val message: AggregatedMessage) : MessageItem()
}

/**
 * 应用 ViewModel
 * 管理配置、LangGraph 客户端和消息状态
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val _config = MutableStateFlow(AgentConfig())
    val config: StateFlow<AgentConfig> = _config.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

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

    private lateinit var langGraphClient: LangGraphClient
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
        initLangGraphClient(config)
    }

    /**
     * 保存配置
     */
    fun saveConfig(newConfig: AgentConfig) {
        _config.value = newConfig
        saveConfig(getApplication(), newConfig)
        initLangGraphClient(newConfig)
    }

    /**
     * 初始化 LangGraph 客户端
     */
    private fun initLangGraphClient(config: AgentConfig) {
        langGraphClient = LangGraphClient(config)
        viewModelScope.launch {
            _isConnected.value = langGraphClient.healthCheck()
        }
    }

    /**
     * 获取 LangGraph 客户端
     */
    fun getLangGraphClient(): LangGraphClient {
        return langGraphClient
    }

    /**
     * 测试连接
     */
    suspend fun testConnection(): Boolean {
        return langGraphClient.healthCheck()
    }

    /**
     * 获取助手列表
     */
    suspend fun listAssistants(): List<String> {
        return langGraphClient.listAssistants()
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
