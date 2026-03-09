package com.example.autoscreenagent.data.remote.zhipu

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// ============================================
// 请求模型
// ============================================

/**
 * 智谱 API 请求体
 */
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
    val toolChoice: String? = null
)

/**
 * 消息
 */
@Serializable
data class ZhipuMessage(
    val role: String,
    val content: JsonElement,
    @SerialName("tool_calls")
    val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null
) {
    companion object {
        /**
         * 创建文本消息
         */
        fun text(role: String, text: String): ZhipuMessage {
            return ZhipuMessage(
                role = role,
                content = JsonPrimitive(text)
            )
        }

        /**
         * 创建多模态消息（包含文本和图片/视频/文件）
         */
        fun multimodal(role: String, contents: List<ZhipuContent>): ZhipuMessage {
            val contentArray = JsonArray(contents.map { it.toJsonElement() })
            return ZhipuMessage(role = role, content = contentArray)
        }

        /**
         * 工具结果消息
         */
        fun tool(content: String, toolCallId: String): ZhipuMessage {
            return ZhipuMessage(
                role = "tool",
                content = JsonPrimitive(content),
                toolCallId = toolCallId
            )
        }

        /**
         * 用户文本消息
         */
        fun user(text: String): ZhipuMessage = text("user", text)

        /**
         * 助手文本消息
         */
        fun assistant(text: String): ZhipuMessage = text("assistant", text)

        /**
         * 系统消息
         */
        fun system(text: String): ZhipuMessage = text("system", text)
    }
}

/**
 * 多模态内容（sealed class）
 */
sealed class ZhipuContent {
    /**
     * 文本内容
     */
    @Serializable
    data class Text(val text: String) : ZhipuContent() {
        override fun toJsonElement(): JsonElement = JsonObject(mapOf(
            "type" to JsonPrimitive("text"),
            "text" to JsonPrimitive(text)
        ))
    }

    /**
     * 图片内容
     * @param url 图片 URL 或 Base64 编码
     * @param isBase64 是否为 Base64 编码
     */
    @Serializable
    data class Image(val url: String, val isBase64: Boolean = false) : ZhipuContent() {
        override fun toJsonElement(): JsonElement {
            val imageUrl = if (isBase64) {
                // Base64 编码的图片需要添加 data URI 前缀
                "data:image/png;base64,$url"
            } else {
                url
            }
            return JsonObject(mapOf(
                "type" to JsonPrimitive("image_url"),
                "image_url" to JsonObject(mapOf(
                    "url" to JsonPrimitive(imageUrl)
                ))
            ))
        }
    }

    /**
     * 视频内容
     */
    @Serializable
    data class Video(val url: String) : ZhipuContent() {
        override fun toJsonElement(): JsonElement = JsonObject(mapOf(
            "type" to JsonPrimitive("video_url"),
            "video_url" to JsonObject(mapOf(
                "url" to JsonPrimitive(url)
            ))
        ))
    }

    /**
     * 文件内容
     */
    @Serializable
    data class File(val url: String) : ZhipuContent() {
        override fun toJsonElement(): JsonElement = JsonObject(mapOf(
            "type" to JsonPrimitive("file_url"),
            "file_url" to JsonObject(mapOf(
                "url" to JsonPrimitive(url)
            ))
        ))
    }

    /**
     * 转换为 JsonElement
     */
    abstract fun toJsonElement(): JsonElement
}

/**
 * 思考模式配置
 */
@Serializable
data class ThinkingConfig(
    val type: String = "enabled"
)

// ============================================
// Tool Calling 模型
// ============================================

/**
 * 工具函数定义
 */
@Serializable
data class ToolFunction(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

/**
 * 工具定义
 */
@Serializable
data class Tool(
    val type: String = "function",
    val function: ToolFunction
)

/**
 * 工具调用
 */
@Serializable
data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: FunctionCall
)

/**
 * 函数调用信息
 */
@Serializable
data class FunctionCall(
    val name: String,
    val arguments: String
)

/**
 * 工具调用结果消息
 */
@Serializable
data class ToolMessage(
    val role: String = "tool",
    val content: String,
    @SerialName("tool_call_id")
    val toolCallId: String
)

// ============================================
// 响应模型
// ============================================

/**
 * 非流式响应
 */
@Serializable
data class ZhipuResponse(
    val id: String? = null,
    val choices: List<ZhipuResponseChoice> = emptyList(),
    val usage: ZhipuUsage? = null,
    val created: Long? = null,
    val model: String? = null,
    val error: ZhipuError? = null
) {
    /**
     * 获取第一个选择的内容
     */
    fun getContent(): String? = choices.firstOrNull()?.message?.content

    /**
     * 是否有错误
     */
    fun hasError(): Boolean = error != null
}

/**
 * 非流式响应的选择项
 */
@Serializable
data class ZhipuResponseChoice(
    val index: Int = 0,
    val message: ZhipuResponseMessage? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

/**
 * 非流式响应的消息
 */
@Serializable
data class ZhipuResponseMessage(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<ToolCall>? = null
)

/**
 * 流式响应块
 */
@Serializable
data class ZhipuStreamChunk(
    val id: String? = null,
    val choices: List<ZhipuStreamChoice> = emptyList(),
    val usage: ZhipuUsage? = null,
    val created: Long? = null,
    val model: String? = null
) {
    /**
     * 获取第一个选择的增量内容
     */
    fun getDelta(): ZhipuDelta? = choices.firstOrNull()?.delta

    /**
     * 获取思考内容
     */
    fun getReasoningContent(): String? = getDelta()?.reasoningContent

    /**
     * 获取正式内容
     */
    fun getContent(): String? = getDelta()?.content

    /**
     * 是否为结束块
     */
    fun isFinished(): Boolean = choices.firstOrNull()?.finishReason != null
}

/**
 * 流式响应的选择项
 */
@Serializable
data class ZhipuStreamChoice(
    val index: Int = 0,
    val delta: ZhipuDelta? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

/**
 * 流式响应的增量内容
 */
@Serializable
data class ZhipuDelta(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<ToolCall>? = null
) {
    /**
     * 是否有思考内容
     */
    fun hasReasoning(): Boolean = !reasoningContent.isNullOrEmpty()

    /**
     * 是否有正式内容
     */
    fun hasContent(): Boolean = !content.isNullOrEmpty()

    /**
     * 是否有工具调用
     */
    fun hasToolCalls(): Boolean = !toolCalls.isNullOrEmpty()

    /**
     * 是否为角色设置块（第一个块）
     */
    fun isRoleBlock(): Boolean = role != null && content == null && reasoningContent == null && toolCalls == null
}

/**
 * Token 使用统计
 */
@Serializable
data class ZhipuUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int = 0,
    @SerialName("completion_tokens")
    val completionTokens: Int = 0,
    @SerialName("total_tokens")
    val totalTokens: Int = 0
)

/**
 * 错误响应
 */
@Serializable
data class ZhipuError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
) {
    override fun toString(): String {
        return "ZhipuError(type=$type, code=$code, message=$message)"
    }
}

// ============================================
// 聚合结果
// ============================================

/**
 * 流式响应聚合结果
 *
 * 用于收集所有流式块并合并为完整响应
 */
data class ZhipuStreamResult(
    val id: String? = null,
    val reasoningContent: String = "",
    val content: String = "",
    val usage: ZhipuUsage? = null,
    val finishReason: String? = null
) {
    /**
     * 是否有思考内容
     */
    fun hasReasoning(): Boolean = reasoningContent.isNotBlank()

    /**
     * 获取完整响应文本（包含思考和正式内容）
     */
    fun getFullText(): String {
        val parts = mutableListOf<String>()
        if (reasoningContent.isNotBlank()) {
            parts.add("[思考]\n$reasoningContent")
        }
        if (content.isNotBlank()) {
            parts.add(content)
        }
        return parts.joinToString("\n\n")
    }

    companion object {
        /**
         * 从流式块列表构建结果
         */
        fun fromChunks(chunks: List<ZhipuStreamChunk>): ZhipuStreamResult {
            if (chunks.isEmpty()) return ZhipuStreamResult()

            val id = chunks.firstOrNull()?.id
            val reasoningBuilder = StringBuilder()
            val contentBuilder = StringBuilder()
            var usage: ZhipuUsage? = null
            var finishReason: String? = null

            for (chunk in chunks) {
                chunk.getDelta()?.let { delta ->
                    delta.reasoningContent?.let { reasoningBuilder.append(it) }
                    delta.content?.let { contentBuilder.append(it) }
                }
                chunk.usage?.let { usage = it }
                chunk.choices.firstOrNull()?.finishReason?.let { finishReason = it }
            }

            return ZhipuStreamResult(
                id = id,
                reasoningContent = reasoningBuilder.toString(),
                content = contentBuilder.toString(),
                usage = usage,
                finishReason = finishReason
            )
        }
    }
}