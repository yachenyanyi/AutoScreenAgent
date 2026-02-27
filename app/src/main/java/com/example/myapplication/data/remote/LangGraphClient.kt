package com.example.autoscreenagent.data.remote

import android.util.Log
import com.example.autoscreenagent.data.remote.api.LangGraphApiService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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

    companion object {
        private const val TAG = "LangGraphClient"
    }

    /**
     * 初始化 Retrofit 和 API 服务
     */
    private fun initRetrofit() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
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
     *
     * @param message 文本消息
     * @param screenshotBase64 可选的截屏 Base64 图像数据
     */
    fun sendMessage(
        message: String,
        screenshotBase64: String? = null
    ): Flow<String> = callbackFlow {
        try {
            if (apiService == null) {
                initRetrofit()
            }

            ensureThreadId()

            // 直接构造 JSON 请求体，避免序列化问题
            val jsonBody = if (screenshotBase64 != null) {
                // 多模态格式：图像 + 文本
                val escapedMessage = message.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")
                """{
                    "assistant_id": "${config.assistantId}",
                    "input": {
                        "messages": [{
                            "role": "human",
                            "content": [
                                {"type": "image_url", "image_url": {"url": "data:image/png;base64,$screenshotBase64"}},
                                {"type": "text", "text": "$escapedMessage"}
                            ]
                        }]
                    },
                    "stream_mode": ["updates", "values"]
                }"""
            } else {
                // 纯文本格式
                val escapedMessage = message.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")
                """{
                    "assistant_id": "${config.assistantId}",
                    "input": {
                        "messages": [{
                            "role": "human",
                            "content": "$escapedMessage"
                        }]
                    },
                    "stream_mode": ["updates", "values"]
                }"""
            }

            Log.d(TAG, "请求 JSON: $jsonBody")

            // 使用 OkHttp 直接调用
            val url = "${ensureTrailingSlash(config.baseUrl)}threads/$currentThreadId/runs/stream"
            val requestBody = RequestBody.create(
                "application/json".toMediaType(),
                jsonBody
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
                    Log.d(TAG, "HTTP 响应状态码：${response.code}")
                    response.body?.source()?.use { source ->
                        var lineCount = 0
                        var eventCount = 0
                        var dataBuffer = StringBuilder()

                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            lineCount++

                            val trimmed = line.trim()

                            // 空行表示一个 SSE 事件结束，解析并发送
                            if (trimmed.isEmpty()) {
                                if (dataBuffer.isNotEmpty()) {
                                    eventCount++
                                    val eventData = dataBuffer.toString()
                                    Log.d(TAG, "SSE 事件 $eventCount: ${eventData.take(200)}...")

                                    val event = parseSseEvent(eventData)
                                    if (event != null) {
                                        Log.d(TAG, "SSE 事件解析成功：${event.take(100)}")
                                        trySend(event)
                                    } else {
                                        Log.w(TAG, "SSE 事件解析失败")
                                    }
                                    dataBuffer.clear()
                                }
                            }
                            // 处理 data: 行
                            else if (trimmed.startsWith("data:")) {
                                val dataContent = trimmed.substringAfter("data:", "").trim()

                                // 跳过 [DONE] 标记
                                if (dataContent == "[DONE]") {
                                    break
                                }

                                // 累加 data 内容（处理跨行数据）
                                if (dataBuffer.isNotEmpty()) {
                                    dataBuffer.append(dataContent)
                                } else {
                                    dataBuffer.append(dataContent)
                                }
                            }
                        }

                        // 处理最后一个事件（可能没有结尾的空行）
                        if (dataBuffer.isNotEmpty()) {
                            eventCount++
                            val eventData = dataBuffer.toString()
                            Log.d(TAG, "SSE 事件 $eventCount (末尾): ${eventData.take(200)}...")

                            val event = parseSseEvent(eventData)
                            if (event != null) {
                                Log.d(TAG, "SSE 事件解析成功：${event.take(100)}")
                                trySend(event)
                            } else {
                                Log.w(TAG, "SSE 事件解析失败（末尾）")
                            }
                        }

                        Log.d(TAG, "SSE 读取完成，总行数：$lineCount, 事件数：$eventCount")
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
            currentThreadId = apiService!!.createThread(emptyMap()).threadId
            Log.d(TAG, "创建新线程：$currentThreadId")
        }
    }

    /**
     * 解析 SSE 事件
     *
     * 支持的格式：
     * 1. 标准 SSE 格式：event: xxx \n data: {...}
     * 2. 直接 JSON 格式：{"model": {...}} 或 {"values": {...}}
     *
     * updates 格式：{"model": {"messages": [...]}}
     * values 格式：{"values": {"messages": [...]}}
     * 中间件格式：{"MobileActionMiddleware.after_agent": {"messages": [...]}}
     *
     * 注意：action 在 content 字段中（JSON 字符串格式）或 additional_kwargs 中
     */
    private fun parseSseEvent(eventText: String): String? {
        // 首先检查是否是标准 SSE 格式（包含 event: 或 data: 行）
        val lines = eventText.split("\n")
        var eventData = ""
        var hasSsePrefix = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("event:")) {
                hasSsePrefix = true
            } else if (trimmed.startsWith("data:")) {
                hasSsePrefix = true
                eventData = trimmed.substringAfter("data:", "").trim()
            }
        }

        // 如果是标准 SSE 格式，使用 eventData；否则直接将整个 eventText 作为 JSON
        val jsonText = if (eventData.isNotEmpty()) eventData else eventText

        // 尝试解析 JSON 对象格式
        try {
            val json = org.json.JSONObject(jsonText)

            // ========== 处理 MobileActionMiddleware.after_agent 格式 ==========
            // 这是中间件处理后的最终格式，包含完整的 additional_kwargs.actions
            val middlewareObj = json.optJSONObject("MobileActionMiddleware.after_agent")
            if (middlewareObj != null) {
                val middlewareMessages = middlewareObj.optJSONArray("messages")
                if (middlewareMessages != null && middlewareMessages.length() > 0) {
                    val lastMessage = middlewareMessages.getJSONObject(middlewareMessages.length() - 1)
                    Log.d(TAG, "找到 MobileActionMiddleware.after_agent 消息")
                    return extractActionFromMessage(lastMessage)
                }
            }

            // ========== 处理 updates 格式：{"model": {"messages": [...]}} ==========
            val modelObj = json.optJSONObject("model")
            if (modelObj != null) {
                val modelMessages = modelObj.optJSONArray("messages")
                if (modelMessages != null && modelMessages.length() > 0) {
                    val lastMessage = modelMessages.getJSONObject(modelMessages.length() - 1)
                    return extractActionFromMessage(lastMessage)
                }
            }

            // ========== 处理 values 格式：{"values": {"messages": [...]}} ==========
            val valuesObj = json.optJSONObject("values")
            if (valuesObj != null) {
                val messagesArray = valuesObj.optJSONArray("messages")
                if (messagesArray != null && messagesArray.length() > 0) {
                    val lastMessage = messagesArray.getJSONObject(messagesArray.length() - 1)
                    return extractActionFromMessage(lastMessage)
                }
            }

            // ========== 处理直接包含 action 的格式 ==========
            if (json.has("action")) {
                return """{"messages": [{"role": "assistant", "content": "", "additional_kwargs": $jsonText}]}"""
            }
        } catch (e: Exception) {
            Log.d(TAG, "解析 JSON 对象失败：${e.message}")
        }

        return null
    }

    /**
     * 从消息对象中提取 action
     *
     * action 可能在：
     * 1. content 字段中（JSON 字符串，可能是数组格式 [...] 或对象格式 {...}）
     * 2. additional_kwargs.actions 数组中
     * 3. additional_kwargs.action 对象中
     */
    private fun extractActionFromMessage(message: org.json.JSONObject): String? {
        // LangGraph 使用 "type" 字段，而不是 "role"
        val role = message.optString("role", "")
        val type = message.optString("type", "")

        // 放宽类型检查：只要有 content 或 additional_kwargs，就尝试解析
        // 不再严格要求 role/type 字段，因为 LangGraph 返回的消息格式可能不一致
        val hasContent = message.has("content")
        val hasAdditionalKwargs = message.has("additional_kwargs")
        val isAssistantMessage = (role == "assistant" || type == "ai" || type == "AIMessage" || type == "AIMessageChunk")

        // 如果是 assistant/ai 类型，或者有 content/additional_kwargs 字段，就尝试解析
        if (!isAssistantMessage && !hasContent && !hasAdditionalKwargs) {
            Log.d(TAG, "跳过非 assistant 消息：role=$role, type=$type")
            return null
        }

        val content = message.optString("content", "")
        val additionalKwargs = message.optJSONObject("additional_kwargs")

        Log.d(TAG, "AI 消息 - role: $role, type: $type")
        Log.d(TAG, "AI 消息 - content: ${content.take(200)}...")
        Log.d(TAG, "AI 消息 - additional_kwargs: $additionalKwargs")

        // 1. 尝试从 additional_kwargs 提取 actions 数组
        val actions = additionalKwargs?.optJSONArray("actions")
        if (actions != null && actions.length() > 0) {
            Log.d(TAG, "找到 actions 数组，长度：${actions.length()}")
            return """{"messages": [{"role": "assistant", "content": "", "additional_kwargs": $additionalKwargs}]}"""
        }

        // 2. 尝试直接从 additional_kwargs 提取 action 对象
        if (additionalKwargs != null && additionalKwargs.has("action")) {
            Log.d(TAG, "找到 action 对象")
            return """{"messages": [{"role": "assistant", "content": "", "additional_kwargs": $additionalKwargs}]}"""
        }

        // 3. 从 content 字段提取（数组格式 [...]）
        if (content.isNotEmpty() && content.trim().startsWith("[")) {
            try {
                val contentArray = org.json.JSONArray(content)
                // 将数组转换为 additional_kwargs.actions 格式
                val actionsJson = org.json.JSONObject()
                actionsJson.put("actions", contentArray)
                Log.d(TAG, "从 content 解析到 actions 数组，长度：${contentArray.length()}")
                return """{"messages": [{"role": "assistant", "content": "", "additional_kwargs": $actionsJson}]}"""
            } catch (e: Exception) {
                Log.d(TAG, "content 数组解析失败：${e.message}")
            }
        }

        // 4. 从 content 字段提取（对象格式 {...}）
        if (content.isNotEmpty() && content.trim().startsWith("{")) {
            try {
                val contentJson = org.json.JSONObject(content)
                if (contentJson.has("action")) {
                    Log.d(TAG, "从 content 找到 action: ${contentJson.optString("action")}")
                    return """{"messages": [{"role": "assistant", "content": "", "additional_kwargs": $contentJson}]}"""
                }
                // 也可能是数组格式嵌套在对象中
                val nestedActions = contentJson.optJSONArray("actions")
                if (nestedActions != null && nestedActions.length() > 0) {
                    Log.d(TAG, "从 content 找到嵌套 actions 数组")
                    return """{"messages": [{"role": "assistant", "content": "", "additional_kwargs": $contentJson}]}"""
                }
            } catch (e: Exception) {
                Log.d(TAG, "content 不是有效的 JSON")
            }
        }

        // 5. 从 content 字段提取（```json 代码块）
        if (content.isNotEmpty()) {
            val jsonMatch = Regex("```json\\s*\\{([^}]+)\\}").find(content)
            if (jsonMatch != null) {
                try {
                    val jsonStr = "{" + jsonMatch.groupValues[1] + "}"
                    val contentJson = org.json.JSONObject(jsonStr)
                    if (contentJson.has("action")) {
                        Log.d(TAG, "从 ```json 代码块找到 action")
                        return """{"messages": [{"role": "assistant", "content": "", "additional_kwargs": $contentJson}]}"""
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "```json 代码块解析失败")
                }
            }
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
