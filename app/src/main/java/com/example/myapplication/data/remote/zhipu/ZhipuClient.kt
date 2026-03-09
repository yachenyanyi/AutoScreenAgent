package com.example.autoscreenagent.data.remote.zhipu

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 智谱 GLM API 客户端
 *
 * 提供与智谱 GLM-4.6V-Flash API 交互的完整功能：
 * - 纯文本对话
 * - 图片理解（URL 或 Base64）
 * - 视频理解
 * - 文件理解
 * - 流式/非流式输出
 * - 思考模式
 * - 多轮对话
 *
 * 使用示例：
 * ```kotlin
 * val client = ZhipuClient()
 *
 * // 纯文本对话
 * client.sendMessage("你好").collect { chunk ->
 *     println(chunk.getContent())
 * }
 *
 * // 图片理解
 * client.sendMessageWithImageBase64("描述图片", base64String).collect { ... }
 * ```
 */
class ZhipuClient(private var config: ZhipuConfig = ZhipuConfig.DEFAULT) {

    companion object {
        private const val TAG = "ZhipuClient"
        private const val BASE_URL = "https://open.bigmodel.cn/api/paas/v4/"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private var okHttpClient: OkHttpClient = createOkHttpClient()
    private var apiService: ZhipuApiService = createApiService()

    // ========================================
    // 公共 API
    // ========================================

    /**
     * 发送纯文本消息（流式）
     *
     * @param text 文本内容
     * @return 流式响应
     */
    fun sendMessage(text: String): Flow<ZhipuStreamChunk> {
        val messages = listOf(ZhipuMessage.user(text))
        return sendMessages(messages)
    }

    /**
     * 发送消息 + 单张图片（URL）
     *
     * @param text 文本内容
     * @param imageUrl 图片 URL
     * @return 流式响应
     */
    fun sendMessageWithImage(text: String, imageUrl: String): Flow<ZhipuStreamChunk> {
        return sendMessageWithImages(text, listOf(imageUrl))
    }

    /**
     * 发送消息 + 单张图片（Base64）
     *
     * @param text 文本内容
     * @param base64 图片 Base64 编码
     * @return 流式响应
     */
    fun sendMessageWithImageBase64(text: String, base64: String): Flow<ZhipuStreamChunk> {
        val contents = listOf(
            ZhipuContent.Text(text),
            ZhipuContent.Image(base64, isBase64 = true)
        )
        val messages = listOf(ZhipuMessage.multimodal("user", contents))
        return sendMessages(messages)
    }

    /**
     * 发送消息 + 多张图片
     *
     * @param text 文本内容
     * @param imageUrls 图片 URL 列表
     * @return 流式响应
     */
    fun sendMessageWithImages(text: String, imageUrls: List<String>): Flow<ZhipuStreamChunk> {
        val contents = mutableListOf<ZhipuContent>()
        contents.add(ZhipuContent.Text(text))
        imageUrls.forEach { url ->
            contents.add(ZhipuContent.Image(url, isBase64 = false))
        }
        val messages = listOf(ZhipuMessage.multimodal("user", contents))
        return sendMessages(messages)
    }

    /**
     * 发送消息 + 多张图片（Base64）
     *
     * @param text 文本内容
     * @param base64Images 图片 Base64 列表
     * @return 流式响应
     */
    fun sendMessageWithImagesBase64(text: String, base64Images: List<String>): Flow<ZhipuStreamChunk> {
        val contents = mutableListOf<ZhipuContent>()
        contents.add(ZhipuContent.Text(text))
        base64Images.forEach { base64 ->
            contents.add(ZhipuContent.Image(base64, isBase64 = true))
        }
        val messages = listOf(ZhipuMessage.multimodal("user", contents))
        return sendMessages(messages)
    }

    /**
     * 发送消息 + 视频
     *
     * @param text 文本内容
     * @param videoUrl 视频 URL
     * @return 流式响应
     */
    fun sendMessageWithVideo(text: String, videoUrl: String): Flow<ZhipuStreamChunk> {
        val contents = listOf(
            ZhipuContent.Text(text),
            ZhipuContent.Video(videoUrl)
        )
        val messages = listOf(ZhipuMessage.multimodal("user", contents))
        return sendMessages(messages)
    }

    /**
     * 发送消息 + 文件
     *
     * @param text 文本内容
     * @param fileUrl 文件 URL（PDF、TXT 等）
     * @return 流式响应
     */
    fun sendMessageWithFile(text: String, fileUrl: String): Flow<ZhipuStreamChunk> {
        val contents = listOf(
            ZhipuContent.Text(text),
            ZhipuContent.File(fileUrl)
        )
        val messages = listOf(ZhipuMessage.multimodal("user", contents))
        return sendMessages(messages)
    }

    /**
     * 发送消息 + 多个文件
     *
     * @param text 文本内容
     * @param fileUrls 文件 URL 列表
     * @return 流式响应
     */
    fun sendMessageWithFiles(text: String, fileUrls: List<String>): Flow<ZhipuStreamChunk> {
        val contents = mutableListOf<ZhipuContent>()
        contents.add(ZhipuContent.Text(text))
        fileUrls.forEach { url ->
            contents.add(ZhipuContent.File(url))
        }
        val messages = listOf(ZhipuMessage.multimodal("user", contents))
        return sendMessages(messages)
    }

    /**
     * 发送消息 + 多种模态混合
     *
     * @param text 文本内容
     * @param images 图片 URL 列表
     * @param videos 视频 URL 列表
     * @param files 文件 URL 列表
     * @param isImageBase64 图片是否为 Base64 编码
     * @return 流式响应
     */
    fun sendMessage(
        text: String,
        images: List<String> = emptyList(),
        videos: List<String> = emptyList(),
        files: List<String> = emptyList(),
        isImageBase64: Boolean = false
    ): Flow<ZhipuStreamChunk> {
        val contents = mutableListOf<ZhipuContent>()
        contents.add(ZhipuContent.Text(text))

        images.forEach { img ->
            contents.add(ZhipuContent.Image(img, isBase64 = isImageBase64))
        }
        videos.forEach { video ->
            contents.add(ZhipuContent.Video(video))
        }
        files.forEach { file ->
            contents.add(ZhipuContent.File(file))
        }

        val messages = listOf(ZhipuMessage.multimodal("user", contents))
        return sendMessages(messages)
    }

    /**
     * 发送完整消息列表（多轮对话）
     *
     * @param messages 消息列表
     * @return 流式响应
     */
    fun sendMessages(messages: List<ZhipuMessage>): Flow<ZhipuStreamChunk> = callbackFlow {
        try {
            val request = buildRequest(messages, stream = true)
            val requestJson = json.encodeToString(request)
            val requestBody = requestJson
                .toRequestBody("application/json".toMediaType())

            val httpRequest = okhttp3.Request.Builder()
                .url("${config.baseUrl}/chat/completions")
                .header("Authorization", config.getAuthorizationHeader())
                .post(requestBody)
                .build()

            Log.d(TAG, "发送请求: ${requestJson.take(500)}...")

            okHttpClient.newCall(httpRequest).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    Log.e(TAG, "请求失败: ${e.message}", e)
                    trySend(ZhipuStreamChunk(
                        choices = listOf(
                            ZhipuStreamChoice(
                                delta = ZhipuDelta(content = "请求失败: ${e.message}")
                            )
                        )
                    ))
                    close()
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "Unknown error"
                        Log.e(TAG, "响应错误: ${response.code}, $errorBody")
                        trySend(ZhipuStreamChunk(
                            choices = listOf(
                                ZhipuStreamChoice(
                                    delta = ZhipuDelta(content = "API 错误: ${response.code}")
                                )
                            )
                        ))
                        close()
                        return
                    }

                    Log.d(TAG, "开始接收 SSE 流")
                    response.body?.let { body ->
                        // 使用阻塞方式读取 SSE 流
                        try {
                            val reader = java.io.BufferedReader(java.io.InputStreamReader(body.byteStream()))
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                val trimmedLine = line?.trim() ?: continue
                                if (trimmedLine.isEmpty()) continue
                                if (trimmedLine.startsWith("data:")) {
                                    val dataContent = trimmedLine.substringAfter("data:").trim()
                                    if (dataContent == "[DONE]") break
                                    ZhipuStreamingParser.parseLine(dataContent)?.let { chunk ->
                                        trySend(chunk)
                                    }
                                }
                            }
                            reader.close()
                        } catch (e: Exception) {
                            Log.e(TAG, "读取 SSE 流失败: ${e.message}")
                        }
                    }
                    close()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "发送消息失败: ${e.message}", e)
            trySend(ZhipuStreamChunk(
                choices = listOf(
                    ZhipuStreamChoice(
                        delta = ZhipuDelta(content = "发送失败: ${e.message}")
                    )
                )
            ))
        }

        awaitClose { }
    }.flowOn(Dispatchers.IO)

    /**
     * 非流式调用
     *
     * @param messages 消息列表
     * @return 完整响应
     */
    suspend fun chatCompletion(messages: List<ZhipuMessage>): ZhipuResponse = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(messages, stream = false)
            apiService.chatCompletion(config.getAuthorizationHeader(), request)
        } catch (e: Exception) {
            Log.e(TAG, "非流式调用失败: ${e.message}", e)
            ZhipuResponse(
                error = ZhipuError(message = e.message)
            )
        }
    }

    /**
     * 非流式纯文本调用
     */
    suspend fun chatCompletion(text: String): ZhipuResponse {
        return chatCompletion(listOf(ZhipuMessage.user(text)))
    }

    // ========================================
    // 配置管理
    // ========================================

    /**
     * 更新配置
     */
    fun updateConfig(newConfig: ZhipuConfig) {
        config = newConfig
        okHttpClient = createOkHttpClient()
        apiService = createApiService()
        Log.d(TAG, "配置已更新: model=${config.model}, thinking=${config.enableThinking}")
    }

    /**
     * 获取当前配置
     */
    fun getConfig(): ZhipuConfig = config

    /**
     * 设置 API Key
     */
    fun setApiKey(apiKey: String) {
        updateConfig(config.withApiKey(apiKey))
    }

    /**
     * 设置模型
     */
    fun setModel(model: String) {
        updateConfig(config.withModel(model))
    }

    /**
     * 启用/禁用思考模式
     */
    fun setThinkingEnabled(enabled: Boolean) {
        if (enabled != config.enableThinking) {
            updateConfig(config.copy(enableThinking = enabled))
        }
    }

    // ========================================
    // 私有方法
    // ========================================

    private fun createOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .writeTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .build()
    }

    private fun createApiService(): ZhipuApiService {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return retrofit.create(ZhipuApiService::class.java)
    }

    private fun buildRequest(
        messages: List<ZhipuMessage>,
        stream: Boolean,
        tools: List<Tool>? = null,
        toolChoice: String? = null
    ): ZhipuRequest {
        return ZhipuRequest(
            model = config.model,
            messages = messages,
            stream = stream,
            temperature = config.temperature,
            topP = config.topP,
            maxTokens = config.maxTokens,
            thinking = if (config.enableThinking) ThinkingConfig() else null,
            tools = tools,
            toolChoice = toolChoice
        )
    }

    /**
     * 发送消息（支持 Tool Calling）
     *
     * @param messages 消息列表
     * @param tools 工具定义列表
     * @param toolChoice 工具选择策略
     * @return 流式响应
     */
    fun sendMessagesWithTools(
        messages: List<ZhipuMessage>,
        tools: List<Tool>? = null,
        toolChoice: String? = null
    ): Flow<ZhipuStreamChunk> = callbackFlow {
        try {
            val request = buildRequest(messages, stream = true, tools = tools, toolChoice = toolChoice)
            val requestJson = json.encodeToString(request)
            val requestBody = requestJson
                .toRequestBody("application/json".toMediaType())

            val httpRequest = okhttp3.Request.Builder()
                .url("${config.baseUrl}/chat/completions")
                .header("Authorization", config.getAuthorizationHeader())
                .post(requestBody)
                .build()

            Log.d(TAG, "发送请求(带工具): ${requestJson.take(500)}...")

            okHttpClient.newCall(httpRequest).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    Log.e(TAG, "请求失败: ${e.message}", e)
                    trySend(ZhipuStreamChunk(
                        choices = listOf(
                            ZhipuStreamChoice(
                                delta = ZhipuDelta(content = "请求失败: ${e.message}")
                            )
                        )
                    ))
                    close()
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "Unknown error"
                        Log.e(TAG, "响应错误: ${response.code}, $errorBody")
                        trySend(ZhipuStreamChunk(
                            choices = listOf(
                                ZhipuStreamChoice(
                                    delta = ZhipuDelta(content = "API 错误: ${response.code}")
                                )
                            )
                        ))
                        close()
                        return
                    }

                    Log.d(TAG, "开始接收 SSE 流")
                    response.body?.let { body ->
                        // 使用阻塞方式读取 SSE 流
                        try {
                            val reader = java.io.BufferedReader(java.io.InputStreamReader(body.byteStream()))
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                val trimmedLine = line?.trim() ?: continue
                                if (trimmedLine.isEmpty()) continue
                                if (trimmedLine.startsWith("data:")) {
                                    val dataContent = trimmedLine.substringAfter("data:").trim()
                                    if (dataContent == "[DONE]") break
                                    ZhipuStreamingParser.parseLine(dataContent)?.let { chunk ->
                                        trySend(chunk)
                                    }
                                }
                            }
                            reader.close()
                        } catch (e: Exception) {
                            Log.e(TAG, "读取 SSE 流失败: ${e.message}")
                        }
                    }
                    close()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "发送消息失败: ${e.message}", e)
            trySend(ZhipuStreamChunk(
                choices = listOf(
                    ZhipuStreamChoice(
                        delta = ZhipuDelta(content = "发送失败: ${e.message}")
                    )
                )
            ))
        }

        awaitClose { }
    }.flowOn(Dispatchers.IO)
}