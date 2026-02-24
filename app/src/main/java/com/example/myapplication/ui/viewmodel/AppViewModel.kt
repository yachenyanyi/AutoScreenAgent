package com.example.autoscreenagent.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.autoscreenagent.data.remote.AgentConfig
import com.example.autoscreenagent.data.remote.LangGraphClient
import com.example.autoscreenagent.ui.screens.loadConfig
import com.example.autoscreenagent.ui.screens.saveConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 应用 ViewModel
 * 管理配置和 LangGraph 客户端
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val _config = MutableStateFlow(AgentConfig())
    val config: StateFlow<AgentConfig> = _config.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

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
}
