package com.example.autoscreenagent.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 统一消息格式
 *
 * 支持纯文本和多模态内容
 */
data class ModelMessage(
    val role: String,
    val content: JsonElement,
    val toolCalls: List<ModelToolCall>? = null,
    val toolCallId: String? = null
) {
    companion object {
        /**
         * 创建文本消息
         */
        fun text(role: String, text: String): ModelMessage {
            return ModelMessage(
                role = role,
                content = JsonPrimitive(text)
            )
        }

        /**
         * 创建多模态消息（包含文本和图片）
         */
        fun multimodal(role: String, contents: List<ModelContent>): ModelMessage {
            val contentArray = JsonArray(contents.map { it.toJsonElement() })
            return ModelMessage(role = role, content = contentArray)
        }

        /**
         * 工具结果消息
         */
        fun tool(content: String, toolCallId: String): ModelMessage {
            return ModelMessage(
                role = "tool",
                content = JsonPrimitive(content),
                toolCallId = toolCallId
            )
        }

        /**
         * 用户文本消息
         */
        fun user(text: String): ModelMessage = text("user", text)

        /**
         * 助手文本消息
         */
        fun assistant(text: String): ModelMessage = text("assistant", text)

        /**
         * 系统消息
         */
        fun system(text: String): ModelMessage = text("system", text)

        /**
         * 从智谱消息转换
         */
        fun fromZhipu(zhipuMessage: com.example.autoscreenagent.data.remote.zhipu.ZhipuMessage): ModelMessage {
            return ModelMessage(
                role = zhipuMessage.role,
                content = zhipuMessage.content,
                toolCalls = zhipuMessage.toolCalls?.map { ModelToolCall.fromZhipu(it) },
                toolCallId = zhipuMessage.toolCallId
            )
        }
    }

    /**
     * 转换为智谱消息
     */
    fun toZhipu(): com.example.autoscreenagent.data.remote.zhipu.ZhipuMessage {
        return com.example.autoscreenagent.data.remote.zhipu.ZhipuMessage(
            role = role,
            content = content,
            toolCalls = toolCalls?.map { it.toZhipu() },
            toolCallId = toolCallId
        )
    }
}

/**
 * 多模态内容（sealed class）
 */
sealed class ModelContent {
    /**
     * 文本内容
     */
    data class Text(val text: String) : ModelContent() {
        override fun toJsonElement(): JsonElement = JsonObject(mapOf(
            "type" to JsonPrimitive("text"),
            "text" to JsonPrimitive(text)
        ))
    }

    /**
     * 图片内容
     *
     * @param urlOrBase64 图片 URL 或 Base64 编码
     * @param isBase64 是否为 Base64 编码
     */
    data class Image(val urlOrBase64: String, val isBase64: Boolean = false) : ModelContent() {
        override fun toJsonElement(): JsonElement {
            val imageUrl = if (isBase64) {
                "data:image/png;base64,$urlOrBase64"
            } else {
                urlOrBase64
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
    data class Video(val url: String) : ModelContent() {
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
    data class File(val url: String) : ModelContent() {
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
 * 工具调用
 */
@Serializable
data class ModelToolCall(
    val id: String,
    val type: String = "function",
    val function: ModelFunctionCall
) {
    companion object {
        /**
         * 从智谱 ToolCall 转换
         */
        fun fromZhipu(zhipuCall: com.example.autoscreenagent.data.remote.zhipu.ToolCall): ModelToolCall {
            return ModelToolCall(
                id = zhipuCall.id,
                type = zhipuCall.type,
                function = ModelFunctionCall(
                    name = zhipuCall.function.name,
                    arguments = zhipuCall.function.arguments
                )
            )
        }
    }

    /**
     * 转换为智谱 ToolCall
     */
    fun toZhipu(): com.example.autoscreenagent.data.remote.zhipu.ToolCall {
        return com.example.autoscreenagent.data.remote.zhipu.ToolCall(
            id = id,
            type = type,
            function = com.example.autoscreenagent.data.remote.zhipu.FunctionCall(
                name = function.name,
                arguments = function.arguments
            )
        )
    }
}

/**
 * 函数调用信息
 */
@Serializable
data class ModelFunctionCall(
    val name: String,
    val arguments: String
)

/**
 * 工具函数定义
 */
@Serializable
data class ModelToolFunction(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

/**
 * 工具定义
 */
@Serializable
data class ModelTool(
    val type: String = "function",
    val function: ModelToolFunction
) {
    companion object {
        /**
         * 从智谱 Tool 转换
         */
        fun fromZhipu(tool: com.example.autoscreenagent.data.remote.zhipu.Tool): ModelTool {
            return ModelTool(
                type = tool.type,
                function = ModelToolFunction(
                    name = tool.function.name,
                    description = tool.function.description,
                    parameters = tool.function.parameters
                )
            )
        }
    }

    /**
     * 转换为智谱 Tool
     */
    fun toZhipu(): com.example.autoscreenagent.data.remote.zhipu.Tool {
        return com.example.autoscreenagent.data.remote.zhipu.Tool(
            type = type,
            function = com.example.autoscreenagent.data.remote.zhipu.ToolFunction(
                name = function.name,
                description = function.description,
                parameters = function.parameters
            )
        )
    }
}