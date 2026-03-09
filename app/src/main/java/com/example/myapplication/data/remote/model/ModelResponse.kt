package com.example.autoscreenagent.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 统一响应格式
 */
@Serializable
data class ModelResponse(
    val id: String? = null,
    val choices: List<ModelResponseChoice> = emptyList(),
    val usage: ModelUsage? = null,
    val created: Long? = null,
    val model: String? = null,
    val error: ModelError? = null
) {
    /**
     * 获取第一个选择的内容
     */
    fun getContent(): String? = choices.firstOrNull()?.message?.content

    /**
     * 获取第一个选择的推理内容
     */
    fun getReasoningContent(): String? = choices.firstOrNull()?.message?.reasoningContent

    /**
     * 是否有错误
     */
    fun hasError(): Boolean = error != null
}

/**
 * 响应的选择项
 */
@Serializable
data class ModelResponseChoice(
    val index: Int = 0,
    val message: ModelResponseMessage? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

/**
 * 响应的消息
 */
@Serializable
data class ModelResponseMessage(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<ModelToolCall>? = null
)

/**
 * 流式响应块
 */
@Serializable
data class ModelStreamChunk(
    val id: String? = null,
    val choices: List<ModelStreamChoice> = emptyList(),
    val usage: ModelUsage? = null,
    val created: Long? = null,
    val model: String? = null
) {
    /**
     * 获取第一个选择的增量内容
     */
    fun getDelta(): ModelDelta? = choices.firstOrNull()?.delta

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

    /**
     * 是否有工具调用
     */
    fun hasToolCalls(): Boolean = !getDelta()?.toolCalls.isNullOrEmpty()

    /**
     * 获取工具调用
     */
    fun getToolCalls(): List<ModelToolCall>? = getDelta()?.toolCalls
}

/**
 * 流式响应的选择项
 */
@Serializable
data class ModelStreamChoice(
    val index: Int = 0,
    val delta: ModelDelta? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

/**
 * 流式响应的增量内容
 */
@Serializable
data class ModelDelta(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<ModelToolCall>? = null
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
data class ModelUsage(
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
data class ModelError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
) {
    override fun toString(): String {
        return "ModelError(type=$type, code=$code, message=$message)"
    }
}

/**
 * 流式响应聚合结果
 *
 * 用于收集所有流式块并合并为完整响应
 */
data class ModelStreamResult(
    val id: String? = null,
    val reasoningContent: String = "",
    val content: String = "",
    val toolCalls: List<ModelToolCall> = emptyList(),
    val usage: ModelUsage? = null,
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
        fun fromChunks(chunks: List<ModelStreamChunk>): ModelStreamResult {
            if (chunks.isEmpty()) return ModelStreamResult()

            val id = chunks.firstOrNull()?.id
            val reasoningBuilder = StringBuilder()
            val contentBuilder = StringBuilder()
            val toolCallsList = mutableListOf<ModelToolCall>()
            var usage: ModelUsage? = null
            var finishReason: String? = null

            for (chunk in chunks) {
                chunk.getDelta()?.let { delta ->
                    delta.reasoningContent?.let { reasoningBuilder.append(it) }
                    delta.content?.let { contentBuilder.append(it) }
                    delta.toolCalls?.let { toolCallsList.addAll(it) }
                }
                chunk.usage?.let { usage = it }
                chunk.choices.firstOrNull()?.finishReason?.let { finishReason = it }
            }

            return ModelStreamResult(
                id = id,
                reasoningContent = reasoningBuilder.toString(),
                content = contentBuilder.toString(),
                toolCalls = toolCallsList,
                usage = usage,
                finishReason = finishReason
            )
        }
    }
}