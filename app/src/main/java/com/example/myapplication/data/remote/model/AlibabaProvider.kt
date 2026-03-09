package com.example.autoscreenagent.data.remote.model

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 阿里云百炼模型 Provider 实现
 *
 * 使用 OpenAI 兼容接口调用阿里云千问模型
 */
class AlibabaProvider(config: ModelConfig) : ModelProvider(config) {

    companion object {
        private const val TAG = "AlibabaProvider"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private var okHttpClient: OkHttpClient = createOkHttpClient()

    override val providerType: ModelProviderType = ModelProviderType.ALIBABA

    private fun createOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .writeTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .build()
    }

    override fun sendMessage(text: String): Flow<ModelStreamChunk> {
        return sendMessageWithImagesBase64(text, emptyList())
    }

    override fun sendMessageWithImageBase64(text: String, imageBase64: String): Flow<ModelStreamChunk> {
        return sendMessageWithImagesBase64(text, listOf(imageBase64))
    }

    override fun sendMessageWithImagesBase64(text: String, images: List<String>): Flow<ModelStreamChunk> {
        val contents = mutableListOf<ModelContent>()
        contents.add(ModelContent.Text(text))
        images.forEach { base64 ->
            contents.add(ModelContent.Image(base64, isBase64 = true))
        }
        val messages = listOf(ModelMessage.multimodal("user", contents))
        return sendMessages(messages)
    }

    override fun sendMessageWithImageUrls(text: String, imageUrls: List<String>): Flow<ModelStreamChunk> {
        val contents = mutableListOf<ModelContent>()
        contents.add(ModelContent.Text(text))
        imageUrls.forEach { url ->
            contents.add(ModelContent.Image(url, isBase64 = false))
        }
        val messages = listOf(ModelMessage.multimodal("user", contents))
        return sendMessages(messages)
    }

    override fun sendMessages(messages: List<ModelMessage>): Flow<ModelStreamChunk> {
        return sendMessagesInternal(messages, tools = null).flowOn(Dispatchers.IO)
    }

    override fun sendMessagesWithTools(
        messages: List<ModelMessage>,
        tools: List<ModelTool>?
    ): Flow<ModelStreamChunk> {
        return sendMessagesInternal(messages, tools).flowOn(Dispatchers.IO)
    }

    private fun sendMessagesInternal(
        messages: List<ModelMessage>,
        tools: List<ModelTool>?
    ): Flow<ModelStreamChunk> = callbackFlow {
        try {
            val requestJson = buildRequestJson(messages, stream = true, tools = tools)
            val requestBody = requestJson.toRequestBody("application/json".toMediaType())

            val httpRequest = okhttp3.Request.Builder()
                .url("${config.baseUrl}/chat/completions")
                .header("Authorization", config.getAuthorizationHeader())
                .post(requestBody)
                .build()

            Log.d(TAG, "发送阿里云请求: ${requestJson.take(500)}...")

            okHttpClient.newCall(httpRequest).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    Log.e(TAG, "请求失败: ${e.message}", e)
                    trySend(ModelStreamChunk(
                        choices = listOf(ModelStreamChoice(delta = ModelDelta(content = "请求失败: ${e.message}")))
                    ))
                    close()
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "Unknown error"
                        Log.e(TAG, "响应错误: ${response.code}, $errorBody")
                        trySend(ModelStreamChunk(
                            choices = listOf(ModelStreamChoice(delta = ModelDelta(content = "API 错误: ${response.code}")))
                        ))
                        close()
                        return
                    }

                    Log.d(TAG, "开始接收 SSE 流")
                    response.body?.let { body ->
                        try {
                            val reader = java.io.BufferedReader(java.io.InputStreamReader(body.byteStream()))
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                val trimmedLine = line?.trim() ?: continue
                                if (trimmedLine.isEmpty()) continue
                                if (trimmedLine.startsWith("data:")) {
                                    val dataContent = trimmedLine.substringAfter("data:").trim()
                                    if (dataContent == "[DONE]") break
                                    parseChunk(dataContent)?.let { chunk -> trySend(chunk) }
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
            trySend(ModelStreamChunk(
                choices = listOf(ModelStreamChoice(delta = ModelDelta(content = "发送失败: ${e.message}")))
            ))
        }
        awaitClose { }
    }

    /**
     * 构建请求 JSON 字符串
     */
    private fun buildRequestJson(
        messages: List<ModelMessage>,
        stream: Boolean,
        tools: List<ModelTool>?
    ): String {
        val sb = StringBuilder()
        sb.append("{")

        // model
        sb.append("\"model\":\"${config.model}\",")

        // messages
        sb.append("\"messages\":[")
        sb.append(messages.joinToString(",") { msg ->
            val content = convertContentToJson(msg.content)
            "{\"role\":\"${msg.role}\",\"content\":$content}"
        })
        sb.append("],")

        // parameters
        sb.append("\"parameters\":{")
        sb.append("\"temperature\":${config.temperature},")
        sb.append("\"top_p\":${config.topP}")
        config.maxTokens?.let { sb.append(",\"max_tokens\":$it") }
        if (config.enableThinking) sb.append(",\"enable_thinking\":true")
        config.extraParams.forEach { (k, v) ->
            sb.append(",\"$k\":${serializeValue(v)}")
        }
        sb.append("},")

        // stream
        sb.append("\"stream\":$stream")

        // tools
        if (tools != null) {
            sb.append(",\"tools\":[")
            sb.append(tools.joinToString(",") { tool ->
                """{"type":"function","function":{"name":"${tool.function.name}","description":"${tool.function.description}","parameters":${tool.function.parameters}}}"""
            })
            sb.append("]")
        }

        sb.append("}")
        return sb.toString()
    }

    private fun convertContentToJson(content: JsonElement): String {
        return when (content) {
            is JsonPrimitive -> "\"${content.content}\""
            is kotlinx.serialization.json.JsonArray -> {
                content.joinToString(",", "[", "]") { item ->
                    when (item) {
                        is JsonObject -> {
                            val type = item["type"]?.let { (it as? JsonPrimitive)?.content }
                            when (type) {
                                "text" -> {
                                    val text = item["text"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                                    """{"type":"text","text":"$text"}"""
                                }
                                "image_url" -> {
                                    val urlObj = item["image_url"] as? JsonObject
                                    val url = urlObj?.get("url")?.let { (it as? JsonPrimitive)?.content } ?: ""
                                    """{"type":"image_url","image_url":{"url":"$url"}}"""
                                }
                                else -> item.toString()
                            }
                        }
                        else -> item.toString()
                    }
                }
            }
            else -> content.toString()
        }
    }

    private fun serializeValue(value: Any): String {
        return when (value) {
            is String -> "\"$value\""
            is Number -> value.toString()
            is Boolean -> value.toString()
            is List<*> -> value.joinToString(",", "[", "]") { serializeValue(it ?: "null") }
            else -> "\"$value\""
        }
    }

    private fun parseChunk(dataContent: String): ModelStreamChunk? {
        return try {
            val map = json.decodeFromString<Map<String, Any?>>(dataContent)
            val output = map["output"] as? Map<String, Any?>
            val choices = output?.get("choices") as? List<Map<String, Any?>>

            ModelStreamChunk(
                id = map["request_id"] as? String,
                choices = choices?.mapIndexed { index, choice ->
                    val delta = choice["delta"] as? Map<String, Any?>
                    ModelStreamChoice(
                        index = index,
                        delta = delta?.let {
                            ModelDelta(
                                role = it["role"] as? String,
                                content = it["content"] as? String,
                                reasoningContent = it["reasoning_content"] as? String
                            )
                        },
                        finishReason = choice["finish_reason"] as? String
                    )
                } ?: emptyList(),
                usage = (map["usage"] as? Map<String, Any?>)?.let {
                    ModelUsage(
                        promptTokens = (it["input_tokens"] as? Number)?.toInt() ?: 0,
                        completionTokens = (it["output_tokens"] as? Number)?.toInt() ?: 0,
                        totalTokens = (it["total_tokens"] as? Number)?.toInt() ?: 0
                    )
                },
                model = config.model
            )
        } catch (e: Exception) {
            Log.w(TAG, "解析响应块失败: ${e.message}")
            null
        }
    }

    override suspend fun chatCompletion(messages: List<ModelMessage>): ModelResponse {
        val requestJson = buildRequestJson(messages, stream = false, tools = null)
        val requestBody = requestJson.toRequestBody("application/json".toMediaType())

        val httpRequest = okhttp3.Request.Builder()
            .url("${config.baseUrl}/chat/completions")
            .header("Authorization", config.getAuthorizationHeader())
            .post(requestBody)
            .build()

        return try {
            val response = okHttpClient.newCall(httpRequest).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val map = json.decodeFromString<Map<String, Any?>>(body)
                mapToModelResponse(map)
            } else {
                ModelResponse(error = ModelError(message = response.body?.string() ?: "Unknown error", code = response.code.toString()))
            }
        } catch (e: Exception) {
            ModelResponse(error = ModelError(message = e.message))
        }
    }

    private fun mapToModelResponse(map: Map<String, Any?>): ModelResponse {
        val output = map["output"] as? Map<String, Any?>
        val choices = output?.get("choices") as? List<Map<String, Any?>>

        return ModelResponse(
            id = map["request_id"] as? String,
            choices = choices?.map { choice ->
                val msg = choice["message"] as? Map<String, Any?>
                ModelResponseChoice(
                    index = (choice["index"] as? Number)?.toInt() ?: 0,
                    message = msg?.let {
                        ModelResponseMessage(
                            role = it["role"] as? String,
                            content = it["content"] as? String,
                            reasoningContent = it["reasoning_content"] as? String
                        )
                    },
                    finishReason = choice["finish_reason"] as? String
                )
            } ?: emptyList(),
            usage = (map["usage"] as? Map<String, Any?>)?.let {
                ModelUsage(
                    promptTokens = (it["input_tokens"] as? Number)?.toInt() ?: 0,
                    completionTokens = (it["output_tokens"] as? Number)?.toInt() ?: 0,
                    totalTokens = (it["total_tokens"] as? Number)?.toInt() ?: 0
                )
            },
            model = map["model"] as? String,
            error = (map["code"] as? String)?.let { ModelError(code = it, message = map["message"] as? String) }
        )
    }

    override fun updateConfig(newConfig: ModelConfig) {
        okHttpClient = createOkHttpClient()
    }

    override fun setApiKey(apiKey: String) {
        updateConfig(config.withApiKey(apiKey))
    }

    override fun setModel(model: String) {
        updateConfig(config.withModel(model))
    }

    override fun isValid(): Boolean = config.isValid()

    override fun close() {
        okHttpClient.dispatcher.executorService.shutdown()
        okHttpClient.connectionPool.evictAll()
        Log.d(TAG, "阿里云 Provider 已关闭")
    }
}

/**
 * OpenAI Provider（占位实现）
 */
class OpenAIProvider(config: ModelConfig) : ModelProvider(config) {
    override val providerType: ModelProviderType = ModelProviderType.OPENAI
    override fun sendMessage(text: String): Flow<ModelStreamChunk> = throw UnsupportedOperationException("OpenAI Provider 待实现")
    override fun sendMessageWithImageBase64(text: String, imageBase64: String): Flow<ModelStreamChunk> = throw UnsupportedOperationException("OpenAI Provider 待实现")
    override fun sendMessageWithImagesBase64(text: String, images: List<String>): Flow<ModelStreamChunk> = throw UnsupportedOperationException("OpenAI Provider 待实现")
    override fun sendMessageWithImageUrls(text: String, imageUrls: List<String>): Flow<ModelStreamChunk> = throw UnsupportedOperationException("OpenAI Provider 待实现")
    override fun sendMessages(messages: List<ModelMessage>): Flow<ModelStreamChunk> = throw UnsupportedOperationException("OpenAI Provider 待实现")
    override fun sendMessagesWithTools(messages: List<ModelMessage>, tools: List<ModelTool>?): Flow<ModelStreamChunk> = throw UnsupportedOperationException("OpenAI Provider 待实现")
    override suspend fun chatCompletion(messages: List<ModelMessage>): ModelResponse = throw UnsupportedOperationException("OpenAI Provider 待实现")
    override fun updateConfig(newConfig: ModelConfig) {}
    override fun setApiKey(apiKey: String) {}
    override fun setModel(model: String) {}
    override fun isValid(): Boolean = config.isValid()
    override fun close() {}
}

/**
 * 自定义 Provider（占位实现）
 */
class CustomProvider(config: ModelConfig) : ModelProvider(config) {
    override val providerType: ModelProviderType = ModelProviderType.CUSTOM
    override fun sendMessage(text: String): Flow<ModelStreamChunk> = throw UnsupportedOperationException("Custom Provider 待实现")
    override fun sendMessageWithImageBase64(text: String, imageBase64: String): Flow<ModelStreamChunk> = throw UnsupportedOperationException("Custom Provider 待实现")
    override fun sendMessageWithImagesBase64(text: String, images: List<String>): Flow<ModelStreamChunk> = throw UnsupportedOperationException("Custom Provider 待实现")
    override fun sendMessageWithImageUrls(text: String, imageUrls: List<String>): Flow<ModelStreamChunk> = throw UnsupportedOperationException("Custom Provider 待实现")
    override fun sendMessages(messages: List<ModelMessage>): Flow<ModelStreamChunk> = throw UnsupportedOperationException("Custom Provider 待实现")
    override fun sendMessagesWithTools(messages: List<ModelMessage>, tools: List<ModelTool>?): Flow<ModelStreamChunk> = throw UnsupportedOperationException("Custom Provider 待实现")
    override suspend fun chatCompletion(messages: List<ModelMessage>): ModelResponse = throw UnsupportedOperationException("Custom Provider 待实现")
    override fun updateConfig(newConfig: ModelConfig) {}
    override fun setApiKey(apiKey: String) {}
    override fun setModel(model: String) {}
    override fun isValid(): Boolean = config.isValid() && config.baseUrl.isNotBlank()
    override fun close() {}
}