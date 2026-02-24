package com.example.autoscreenagent.data.remote

import kotlinx.serialization.Serializable

/**
 * Agent 配置
 *
 * @param baseUrl LangGraph Server 地址，如 http://192.168.1.100:2024
 * @param assistantId 助手 ID，如 11530794-69e6-563f-a576-6b3bf2d3a667 或 intelligent_deep_agent_mobile
 * @param apiKey 可选的 API 密钥（如果需要认证）
 * @param timeoutSeconds 请求超时时间（秒）
 * @param maxRetries 最大重试次数
 */
@Serializable
data class AgentConfig(
    val baseUrl: String = "http://192.168.10.12:2024",
    val assistantId: String = "11530794-69e6-563f-a576-6b3bf2d3a667",
    val apiKey: String? = null,
    val timeoutSeconds: Int = 60,
    val maxRetries: Int = 3
) {
    /**
     * 验证配置是否有效
     */
    fun isValid(): Boolean {
        return baseUrl.isNotBlank() &&
                assistantId.isNotBlank() &&
                timeoutSeconds > 0 &&
                maxRetries >= 0
    }

    /**
     * 获取不带协议的主机地址
     */
    fun getHost(): String {
        return baseUrl
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
    }
}
