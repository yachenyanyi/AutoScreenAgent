package com.example.autoscreenagent.data.remote

import android.util.Log
import com.example.autoscreenagent.data.remote.api.LangGraphApiService
import com.example.autoscreenagent.data.remote.model.Input
import com.example.autoscreenagent.data.remote.model.Message
import com.example.autoscreenagent.data.remote.model.StreamRunRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * LangGraph 客户端
 *
 * 用于与 LangGraph Server 进行 HTTP 通信
 * 支持流式响应和会话管理
 */
class LangGraphClient(
    private var config: AgentConfig
) {
    private var apiService: LangGraphApiService? = null
    private var currentThreadId: String? = null
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val TAG = "LangGraphClient"
    }

    /**
     * 初始化 Retrofit 和 API 服务
     */
    private fun initRetrofit() {
        val client = OkHttpClient.Builder()
            .connectTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .writeTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(ensureTrailingSlash(config.baseUrl))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        apiService = retrofit.create(LangGraphApiService::class.java)
    }

    /**
     * 更新配置
     */
    fun updateConfig(newConfig: AgentConfig) {
        config = newConfig
        apiService = null
        currentThreadId = null
    }

    /**
     * 获取当前配置
     */
    fun getConfig(): AgentConfig = config

    /**
     * 获取当前线程 ID
     */
    fun getCurrentThreadId(): String? = currentThreadId

    /**
     * 开始新会话
     */
    suspend fun startNewSession() {
        currentThreadId = null
    }

    /**
     * 结束当前会话
     */
    suspend fun endSession() {
        currentThreadId = null
    }

    /**
     * 发送消息给 Agent（流式响应）
     *
     * 利用 LangGraph 线程记忆功能，每次只发送增量信息：
     * - 第 1 次：用户目标（如："用户目标：打开微信"）
     * - 后续：执行结果（如："✅ 点击坐标 (180, 600)"）
     *
     * LangGraph 服务器端会自动维护消息历史，无需重复发送
     */
    fun sendMessage(
        message: String,
        screenContent: String? = null
    ): Flow<String> = callbackFlow {
        try {
            if (apiService == null) {
                initRetrofit()
            }

            ensureThreadId()

            val content = if (screenContent != null) {
                "$message\n\n屏幕内容：\n$screenContent"
            } else {
                message
            }

            val request = StreamRunRequest(
                assistantId = config.assistantId,
                input = Input(messages = listOf(Message(role = "human", content = content))),
                streamMode = listOf("updates")
            )

            // 使用 OkHttp 直接调用
            val url = "${ensureTrailingSlash(config.baseUrl)}threads/$currentThreadId/runs/stream"
            val requestBody = RequestBody.create(
                "application/json".toMediaType(),
                json.encodeToString(request)
            )

            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .readTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .build()

            val requestOkhttp = okhttp3.Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            okHttpClient.newCall(requestOkhttp).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    trySend("""{"error": "${e.message}"}""")
                    close()
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.body?.source()?.use { source ->
                        var buffer = ""
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            buffer += line + "\n"

                            if (line.isEmpty() || line == "\n") {
                                val event = parseSseEvent(buffer)
                                if (event != null) {
                                    trySend(event)
                                }
                                buffer = ""
                            }
                        }
                    }
                    close()
                }
            })

        } catch (e: Exception) {
            Log.e(TAG, "发送消息失败", e)
            trySend("""{"error": "${e.message}"}""")
        }

        awaitClose { }
    }

    /**
     * 获取助手列表
     */
    suspend fun listAssistants(): List<String> {
        try {
            if (apiService == null) {
                initRetrofit()
            }
            return apiService!!.listAssistants().map { it.assistantId }
        } catch (e: Exception) {
            Log.e(TAG, "获取助手列表失败", e)
            return emptyList()
        }
    }

    /**
     * 健康检查
     */
    suspend fun healthCheck(): Boolean {
        try {
            if (apiService == null) {
                initRetrofit()
            }
            val response = apiService!!.healthCheck()
            return response.ok == true
        } catch (e: Exception) {
            Log.e(TAG, "健康检查失败", e)
            return false
        }
    }

    /**
     * 确保有线程 ID
     */
    private suspend fun ensureThreadId() {
        if (currentThreadId == null) {
            if (apiService == null) {
                initRetrofit()
            }
            currentThreadId = apiService!!.createThread().threadId
            Log.d(TAG, "创建新线程：$currentThreadId")
        }
    }

    /**
     * 解析 SSE 事件
     */
    private fun parseSseEvent(eventText: String): String? {
        val lines = eventText.split("\n")
        var eventType = ""
        var eventData = ""

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("event:")) {
                eventType = trimmed.substringAfter("event:").trim()
            } else if (trimmed.startsWith("data:")) {
                eventData = trimmed.substringAfter("data:").trim()
            }
        }

        if ((eventType == "updates" || eventType == "values") && eventData.isNotEmpty()) {
            return eventData
        }

        return null
    }

    /**
     * 确保 URL 以斜杠结尾
     */
    private fun ensureTrailingSlash(url: String): String {
        return if (url.endsWith("/")) url else "$url/"
    }
}
