package com.example.autoscreenagent.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 统一消息格式
 *
 * 参考 LangChain 的 BaseMessage 设计，支持：
 * - 纯文本和多模态内容
 * - additional_kwargs 存储厂商特定信息
 * - response_metadata 存储响应元数据
 * - 各厂商 API 兼容
 */
@Serializable
data class ChatMessage(
    val role: ChatRole,
    val content: ChatContent,
    @SerialName("tool_calls")
    val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null,
    /**
     * 额外参数（厂商特定信息）
     *
     * 参考 LangChain: additional_kwargs: dict = Field(default_factory=dict)
     * 用于存储如 reasoning_content、name 等非标准字段
     */
    @SerialName("additional_kwargs")
    val additionalKwargs: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    /**
     * 响应元数据
     *
     * 参考 LangChain: response_metadata: dict = Field(default_factory=dict)
     * 用于存储响应头、token 统计详细信息等
     */
    @SerialName("response_metadata")
    val responseMetadata: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    /**
     * 消息名称（用于多角色对话）
     *
     * 参考 LangChain: name: str | None = None
     */
    val name: String? = null,
    /**
     * 消息 ID
     *
     * 参考 LangChain: id: str | None = None
     */
    val id: String? = null
) {
    companion object {
        /**
         * 创建系统消息
         */
        fun system(text: String): ChatMessage = ChatMessage(
            role = ChatRole.SYSTEM,
            content = ChatContent.Text(text)
        )

        /**
         * 创建用户文本消息
         */
        fun user(text: String): ChatMessage = ChatMessage(
            role = ChatRole.USER,
            content = ChatContent.Text(text)
        )

        /**
         * 创建用户多模态消息（文本 + 图片）
         */
        fun userWithImage(text: String, imageBase64: String): ChatMessage = ChatMessage(
            role = ChatRole.USER,
            content = ChatContent.MultiModal(listOf(
                ContentPart.Text(text),
                ContentPart.Image(imageBase64, isBase64 = true)
            ))
        )

        /**
         * 创建用户多模态消息（文本 + 多张图片）
         */
        fun userWithImages(text: String, images: List<String>, isBase64: Boolean = true): ChatMessage {
            val parts = mutableListOf<ContentPart>()
            parts.add(ContentPart.Text(text))
            images.forEach { parts.add(ContentPart.Image(it, isBase64)) }
            return ChatMessage(
                role = ChatRole.USER,
                content = ChatContent.MultiModal(parts)
            )
        }

        /**
         * 创建助手消息
         */
        fun assistant(text: String): ChatMessage = ChatMessage(
            role = ChatRole.ASSISTANT,
            content = ChatContent.Text(text)
        )

        /**
         * 创建助手消息（带工具调用）
         */
        fun assistantWithToolCalls(toolCalls: List<ToolCall>, text: String = ""): ChatMessage = ChatMessage(
            role = ChatRole.ASSISTANT,
            content = ChatContent.Text(text),
            toolCalls = toolCalls
        )

        /**
         * 创建工具结果消息
         */
        fun toolResult(content: String, toolCallId: String): ChatMessage = ChatMessage(
            role = ChatRole.TOOL,
            content = ChatContent.Text(content),
            toolCallId = toolCallId
        )
    }
}

/**
 * 消息角色
 */
@Serializable
enum class ChatRole {
    @SerialName("system")
    SYSTEM,

    @SerialName("user")
    USER,

    @SerialName("assistant")
    ASSISTANT,

    @SerialName("tool")
    TOOL
}

/**
 * 消息内容（支持纯文本和多模态）
 */
@Serializable
sealed class ChatContent {
    /**
     * 纯文本内容
     */
    @Serializable
    data class Text(val text: String) : ChatContent()

    /**
     * 多模态内容（文本 + 图片/视频/文件）
     */
    @Serializable
    data class MultiModal(val parts: List<ContentPart>) : ChatContent()
}

/**
 * 多模态内容部分
 *
 * 参考 LangChain 的 ContentBlock 设计，支持：
 * - TextContentBlock: 文本内容
 * - ImageContentBlock: 图片内容
 * - AudioContentBlock: 音频内容
 * - VideoContentBlock: 视频内容
 * - FileContentBlock: 文件内容
 * - ReasoningContentBlock: 思考内容（支持思考模式）
 */
@Serializable
sealed class ContentPart {
    /**
     * 文本部分
     */
    @Serializable
    data class Text(val text: String) : ContentPart()

    /**
     * 图片部分
     *
     * @param url 图片 URL 或 Base64 编码
     * @param isBase64 是否为 Base64 编码
     */
    @Serializable
    data class Image(
        val url: String,
        val isBase64: Boolean = false
    ) : ContentPart()

    /**
     * 音频部分
     *
     * 参考 LangChain: AudioContentBlock
     * @param url 音频 URL 或 Base64 编码
     * @param isBase64 是否为 Base64 编码
     * @param format 音频格式（如 "wav", "mp3"）
     */
    @Serializable
    data class Audio(
        val url: String,
        val isBase64: Boolean = false,
        val format: String? = null
    ) : ContentPart()

    /**
     * 视频部分
     */
    @Serializable
    data class Video(val url: String) : ContentPart()

    /**
     * 文件部分
     */
    @Serializable
    data class File(val url: String) : ContentPart()

    /**
     * 思考内容部分
     *
     * 参考 LangChain: ReasoningContentBlock = {"type": "reasoning", "reasoning": str}
     * 用于支持 DeepSeek、智谱等厂商的思考模式
     */
    @Serializable
    data class Reasoning(val reasoning: String) : ContentPart()
}

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
 * 扩展函数：获取文本内容
 */
fun ChatMessage.getText(): String? {
    return when (content) {
        is ChatContent.Text -> (content as ChatContent.Text).text
        is ChatContent.MultiModal -> {
            (content as ChatContent.MultiModal).parts
                .filterIsInstance<ContentPart.Text>()
                .firstOrNull()?.text
        }
    }
}

/**
 * 扩展函数：检查是否包含图片
 */
fun ChatMessage.hasImages(): Boolean {
    return when (content) {
        is ChatContent.MultiModal -> {
            (content as ChatContent.MultiModal).parts.any { it is ContentPart.Image }
        }
        else -> false
    }
}

/**
 * 扩展函数：移除图片，只保留文本
 */
fun ChatMessage.removeImages(): ChatMessage {
    return when (content) {
        is ChatContent.MultiModal -> {
            val textParts = (content as ChatContent.MultiModal).parts
                .filterIsInstance<ContentPart.Text>()

            if (textParts.isEmpty()) {
                copy(content = ChatContent.Text("[图片已过期]"))
            } else if (textParts.size == 1) {
                copy(content = ChatContent.Text(textParts[0].text))
            } else {
                copy(content = ChatContent.Text(textParts.joinToString("\n") { it.text }))
            }
        }
        else -> this
    }
}