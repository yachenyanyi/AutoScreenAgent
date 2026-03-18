package com.example.autoscreenagent.data.remote.model

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 智谱 GLM ChatModel 实现
 *
 * 特性：
 * - 支持 GLM-4.6V-Flash 等视觉模型
 * - 支持思考模式（reasoning_content）
 * - 支持工具调用（tool_calls）
 * - 支持流式输出（SSE）
 */
class ZhipuChatModel(
    config: ChatModelConfig,
    conversationConfig: ConversationConfig = ConversationConfig.DEFAULT
) : BaseChatModel(config, conversationConfig) {

    companion object {
        private const val TAG = "ZhipuChatModel"
    }

    override val provider: ModelProvider = ModelProvider.ZHIPU

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * 使用共享的 OkHttpClient
     *
     * 修复审查报告指出的资源管理问题：
     * - 避免每个实例创建独立客户端
     * - 共享连接池
     */
    private val okHttpClient: OkHttpClient = OkHttpClientProvider.sharedClient

    // ========================================
    // 核心方法实现
    // ========================================

    override suspend fun _generate(
        messages: List<ChatMessage>,
        tools: List<Tool>?,
        stop: List<String>?,
        toolChoice: ToolChoice?
    ): ChatResult {
        val request = buildZhipuRequest(messages, stream = false, tools = tools, stop = stop, toolChoice = toolChoice)
        val requestJson = json.encodeToString(request)
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
                parseZhipuResponse(body)
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "API 错误: ${response.code}, $errorBody")
                ChatResult.error("API 错误: ${response.code}", response.code.toString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "请求失败: ${e.message}", e)
            ChatResult.error(e.message ?: "Unknown error")
        }
    }

    override fun _stream(
        messages: List<ChatMessage>,
        tools: List<Tool>?,
        stop: List<String>?,
        toolChoice: ToolChoice?
    ): Flow<ChatChunk> = callbackFlow {
        val request = buildZhipuRequest(messages, stream = true, tools = tools, stop = stop, toolChoice = toolChoice)
        val requestJson = json.encodeToString(request)
        val requestBody = requestJson.toRequestBody("application/json".toMediaType())

        val httpRequest = okhttp3.Request.Builder()
            .url("${config.baseUrl}/chat/completions")
            .header("Authorization", config.getAuthorizationHeader())
            .post(requestBody)
            .build()

        Log.d(TAG, "发送流式请求: ${requestJson.take(500)}...")

        okHttpClient.newCall(httpRequest).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e(TAG, "请求失败: ${e.message}", e)
                trySend(ChatChunk(content = "请求失败: ${e.message}"))
                close()
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    Log.e(TAG, "API 错误: ${response.code}, $errorBody")
                    trySend(ChatChunk(content = "API 错误: ${response.code}"))
                    close()
                    return
                }

                Log.d(TAG, "开始接收 SSE 流")
                response.body?.let { body ->
                    try {
                        // 使用 use {} 确保资源正确关闭（参考审查报告建议）
                        java.io.InputStreamReader(body.byteStream()).use { inputStreamReader ->
                            java.io.BufferedReader(inputStreamReader).use { reader ->
                                var line: String?
                                while (reader.readLine().also { line = it } != null) {
                                    val trimmedLine = line?.trim() ?: continue
                                    if (trimmedLine.isEmpty()) continue
                                    if (trimmedLine.startsWith("data:")) {
                                        val dataContent = trimmedLine.substringAfter("data:").trim()
                                        if (dataContent == "[DONE]") break
                                        parseZhipuChunk(dataContent)?.let { trySend(it) }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "读取 SSE 流失败: ${e.message}")
                    }
                }
                close()
            }
        })

        awaitClose { }
    }.flowOn(Dispatchers.IO)

    // ========================================
    // 消息格式转换
    // ========================================

    private fun buildZhipuRequest(
        messages: List<ChatMessage>,
        stream: Boolean,
        tools: List<Tool>?,
        stop: List<String>? = null,
        toolChoice: ToolChoice? = null
    ): ZhipuRequest {
        // 确定 toolChoice 参数
        val toolChoiceStr = when (toolChoice) {
            ToolChoice.AUTO -> "auto"
            ToolChoice.ANY -> "any"
            ToolChoice.NONE -> "none"
            null -> if (tools != null && tools.isNotEmpty()) "auto" else null
        }

        return ZhipuRequest(
            model = config.model,
            messages = messages.map { formatMessage(it) },
            stream = stream,
            temperature = config.temperature,
            topP = config.topP,
            maxTokens = config.maxTokens,
            thinking = if (config.enableThinking) ThinkingConfig() else null,
            tools = tools?.takeIf { it.isNotEmpty() },
            toolChoice = toolChoiceStr,
            stop = stop
        )
    }

    /**
     * 格式化消息为智谱格式
     */
    private fun formatMessage(message: ChatMessage): ZhipuMessage {
        return ZhipuMessage(
            role = when (message.role) {
                ChatRole.SYSTEM -> "system"
                ChatRole.USER -> "user"
                ChatRole.ASSISTANT -> "assistant"
                ChatRole.TOOL -> "tool"
            },
            content = formatContent(message.content),
            toolCalls = message.toolCalls,
            toolCallId = message.toolCallId
        )
    }

    /**
     * 格式化内容
     */
    private fun formatContent(content: ChatContent): JsonElement {
        return when (content) {
            is ChatContent.Text -> JsonPrimitive(content.text)
            is ChatContent.MultiModal -> {
                buildJsonArray {
                    content.parts.forEach { part ->
                        when (part) {
                            is ContentPart.Text -> add(buildJsonObject {
                                put("type", "text")
                                put("text", part.text)
                            })
                            is ContentPart.Image -> add(buildJsonObject {
                                put("type", "image_url")
                                put("image_url", buildJsonObject {
                                    val url = if (part.isBase64) {
                                        "data:image/png;base64,${part.url}"
                                    } else {
                                        part.url
                                    }
                                    put("url", url)
                                })
                            })
                            is ContentPart.Audio -> add(buildJsonObject {
                                put("type", "audio_url")
                                put("audio_url", buildJsonObject {
                                    val url = if (part.isBase64) {
                                        "data:audio/${part.format ?: "wav"};base64,${part.url}"
                                    } else {
                                        part.url
                                    }
                                    put("url", url)
                                })
                            })
                            is ContentPart.Video -> add(buildJsonObject {
                                put("type", "video_url")
                                put("video_url", buildJsonObject {
                                    put("url", part.url)
                                })
                            })
                            is ContentPart.File -> add(buildJsonObject {
                                put("type", "file_url")
                                put("file_url", buildJsonObject {
                                    put("url", part.url)
                                })
                            })
                            is ContentPart.Reasoning -> add(buildJsonObject {
                                put("type", "reasoning")
                                put("reasoning", part.reasoning)
                            })
                        }
                    }
                }
            }
        }
    }

    // ========================================
    // 响应解析
    // ========================================

    private fun parseZhipuResponse(responseJson: String): ChatResult {
        return try {
            val response = json.decodeFromString<ZhipuResponse>(responseJson)
            val choice = response.choices.firstOrNull()

            ChatResult(
                id = response.id,
                content = choice?.message?.content ?: "",
                reasoningContent = choice?.message?.reasoningContent ?: "",
                toolCalls = choice?.message?.toolCalls,
                usage = response.usage?.let {
                    ChatUsage(
                        promptTokens = it.promptTokens,
                        completionTokens = it.completionTokens,
                        totalTokens = it.totalTokens
                    )
                },
                finishReason = choice?.finishReason,
                error = response.error?.let {
                    ChatError(message = it.message, type = it.type, code = it.code)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "解析响应失败: ${e.message}")
            ChatResult.error("解析响应失败: ${e.message}")
        }
    }

    private fun parseZhipuChunk(dataContent: String): ChatChunk? {
        if (dataContent.isBlank()) return null

        return try {
            val chunk = json.decodeFromString<ZhipuStreamChunk>(dataContent)
            val choice = chunk.choices.firstOrNull()

            ChatChunk(
                id = chunk.id,
                content = choice?.delta?.content,
                reasoningContent = choice?.delta?.reasoningContent,
                toolCalls = choice?.delta?.toolCalls,
                usage = chunk.usage?.let {
                    ChatUsage(
                        promptTokens = it.promptTokens,
                        completionTokens = it.completionTokens,
                        totalTokens = it.totalTokens
                    )
                },
                finishReason = choice?.finishReason,
                model = chunk.model
            )
        } catch (e: Exception) {
            Log.w(TAG, "解析流式块失败: ${e.message}")
            null
        }
    }

    // ========================================
    // 智谱专用数据模型
    // ========================================

    @Serializable
    data class ZhipuRequest(
        val model: String,
        val messages: List<ZhipuMessage>,
        val stream: Boolean = true,
        val temperature: Float? = null,
        @SerialName("top_p")
        val topP: Float? = null,
        @SerialName("max_tokens")
        val maxTokens: Int? = null,
        val thinking: ThinkingConfig? = null,
        val tools: List<Tool>? = null,
        @SerialName("tool_choice")
        val toolChoice: String? = null,
        val stop: List<String>? = null
    )

    @Serializable
    data class ZhipuMessage(
        val role: String,
        val content: JsonElement,
        @SerialName("tool_calls")
        val toolCalls: List<ToolCall>? = null,
        @SerialName("tool_call_id")
        val toolCallId: String? = null
    )

    @Serializable
    data class ThinkingConfig(
        val type: String = "enabled"
    )

    @Serializable
    data class ZhipuResponse(
        val id: String? = null,
        val choices: List<ZhipuResponseChoice> = emptyList(),
        val usage: ZhipuUsage? = null,
        val error: ZhipuError? = null
    )

    @Serializable
    data class ZhipuResponseChoice(
        val index: Int = 0,
        val message: ZhipuResponseMessage? = null,
        @SerialName("finish_reason")
        val finishReason: String? = null
    )

    @Serializable
    data class ZhipuResponseMessage(
        val role: String? = null,
        val content: String? = null,
        @SerialName("reasoning_content")
        val reasoningContent: String? = null,
        @SerialName("tool_calls")
        val toolCalls: List<ToolCall>? = null
    )

    @Serializable
    data class ZhipuStreamChunk(
        val id: String? = null,
        val choices: List<ZhipuStreamChoice> = emptyList(),
        val usage: ZhipuUsage? = null,
        val model: String? = null
    )

    @Serializable
    data class ZhipuStreamChoice(
        val index: Int = 0,
        val delta: ZhipuDelta? = null,
        @SerialName("finish_reason")
        val finishReason: String? = null
    )

    @Serializable
    data class ZhipuDelta(
        val role: String? = null,
        val content: String? = null,
        @SerialName("reasoning_content")
        val reasoningContent: String? = null,
        @SerialName("tool_calls")
        val toolCalls: List<ToolCall>? = null
    )

    @Serializable
    data class ZhipuUsage(
        @SerialName("prompt_tokens")
        val promptTokens: Int = 0,
        @SerialName("completion_tokens")
        val completionTokens: Int = 0,
        @SerialName("total_tokens")
        val totalTokens: Int = 0
    )

    @Serializable
    data class ZhipuError(
        val message: String? = null,
        val type: String? = null,
        val code: String? = null
    )
}