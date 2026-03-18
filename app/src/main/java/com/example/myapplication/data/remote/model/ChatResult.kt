package com.example.autoscreenagent.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 完整响应结果
 */
@Serializable
data class ChatResult(
    val id: String? = null,
    val content: String = "",
    val reasoningContent: String = "",
    val toolCalls: List<ToolCall>? = null,
    val usage: ChatUsage? = null,
    val finishReason: String? = null,
    val error: ChatError? = null
) {
    /**
     * 是否有错误
     */
    fun hasError(): Boolean = error != null

    /**
     * 是否有工具调用
     */
    fun hasToolCalls(): Boolean = !toolCalls.isNullOrEmpty()

    /**
     * 是否有思考内容
     */
    fun hasReasoning(): Boolean = reasoningContent.isNotBlank()

    /**
     * 获取完整文本（包含思考内容）
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
         * 创建错误结果
         */
        fun error(message: String, code: String? = null): ChatResult = ChatResult(
            error = ChatError(message = message, code = code)
        )

        /**
         * 从流式块聚合结果
         */
        fun fromChunks(chunks: List<ChatChunk>): ChatResult {
            if (chunks.isEmpty()) return ChatResult()

            val id = chunks.firstOrNull()?.id
            val contentBuilder = StringBuilder()
            val reasoningBuilder = StringBuilder()
            val toolCallsList = mutableListOf<ToolCall>()
            var usage: ChatUsage? = null
            var finishReason: String? = null

            for (chunk in chunks) {
                chunk.content?.let { contentBuilder.append(it) }
                chunk.reasoningContent?.let { reasoningBuilder.append(it) }
                chunk.toolCalls?.let { toolCallsList.addAll(it) }
                chunk.usage?.let { usage = it }
                chunk.finishReason?.let { finishReason = it }
            }

            return ChatResult(
                id = id,
                content = contentBuilder.toString(),
                reasoningContent = reasoningBuilder.toString(),
                toolCalls = toolCallsList.takeIf { it.isNotEmpty() },
                usage = usage,
                finishReason = finishReason
            )
        }
    }
}

/**
 * 流式响应块
 */
@Serializable
data class ChatChunk(
    val id: String? = null,
    val content: String? = null,
    val reasoningContent: String? = null,
    val toolCalls: List<ToolCall>? = null,
    val usage: ChatUsage? = null,
    val finishReason: String? = null,
    val model: String? = null,
    /**
     * 错误信息（用于流式响应中的错误传递）
     */
    val error: ChatError? = null
) {
    /**
     * 是否为结束块
     */
    fun isFinished(): Boolean = finishReason != null

    /**
     * 是否有内容
     */
    fun hasContent(): Boolean = !content.isNullOrEmpty()

    /**
     * 是否有思考内容
     */
    fun hasReasoning(): Boolean = !reasoningContent.isNullOrEmpty()

    /**
     * 是否有工具调用
     */
    fun hasToolCalls(): Boolean = !toolCalls.isNullOrEmpty()

    /**
     * 是否有错误
     */
    fun hasError(): Boolean = error != null
}

/**
 * Token 使用统计
 */
@Serializable
data class ChatUsage(
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
data class ChatError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
) {
    override fun toString(): String {
        return "ChatError(type=$type, code=$code, message=$message)"
    }
}