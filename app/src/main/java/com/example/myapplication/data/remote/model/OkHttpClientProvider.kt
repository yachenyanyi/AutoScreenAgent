package com.example.autoscreenagent.data.remote.model

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * OkHttpClient 共享提供者
 *
 * 解决审查报告中指出的资源管理问题：
 * - 避免每个 ChatModel 创建独立 OkHttpClient
 * - 共享连接池，提高资源利用率
 * - 统一配置超时和拦截器
 */
object OkHttpClientProvider {

    /**
     * 默认超时时间（秒）
     */
    const val DEFAULT_TIMEOUT_SECONDS = 120L

    /**
     * 共享的 OkHttpClient 实例
     *
     * 使用 lazy 初始化，确保线程安全
     */
    val sharedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS) // 保持连接活跃
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * 创建自定义配置的 OkHttpClient
     *
     * 用于需要特殊配置的场景（如不同的超时时间）
     *
     * @param timeoutSeconds 超时时间（秒）
     * @return 新的 OkHttpClient 实例
     */
    fun createClient(timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * 创建用于流式响应的 OkHttpClient
     *
     * 流式响应需要更长的读取超时
     *
     * @param timeoutSeconds 超时时间（秒）
     * @return 配置用于流式响应的 OkHttpClient
     */
    fun createStreamingClient(timeoutSeconds: Long = 300L): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS) // 流式响应需要更长读取超时
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}