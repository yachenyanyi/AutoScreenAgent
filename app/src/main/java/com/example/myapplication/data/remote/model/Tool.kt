package com.example.autoscreenagent.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 工具定义
 */
@Serializable
data class Tool(
    val type: String = "function",
    val function: ToolFunction
) {
    companion object {
        /**
         * 创建工具
         */
        fun create(
            name: String,
            description: String,
            parameters: JsonObject
        ): Tool = Tool(
            type = "function",
            function = ToolFunction(
                name = name,
                description = description,
                parameters = parameters
            )
        )

        /**
         * 创建简单工具（无参数）
         */
        fun createNoArgs(
            name: String,
            description: String
        ): Tool = create(
            name = name,
            description = description,
            parameters = JsonObject(mapOf(
                "type" to JsonPrimitive("object"),
                "properties" to JsonObject(emptyMap()),
                "required" to kotlinx.serialization.json.JsonArray(emptyList())
            ))
        )

        /**
         * 创建带字符串参数的工具
         */
        fun createWithStringParam(
            name: String,
            description: String,
            paramName: String,
            paramDescription: String,
            required: Boolean = true
        ): Tool = create(
            name = name,
            description = description,
            parameters = JsonObject(mapOf(
                "type" to JsonPrimitive("object"),
                "properties" to JsonObject(mapOf(
                    paramName to JsonObject(mapOf(
                        "type" to JsonPrimitive("string"),
                        "description" to JsonPrimitive(paramDescription)
                    ))
                )),
                "required" to if (required) {
                    kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive(paramName)))
                } else {
                    kotlinx.serialization.json.JsonArray(emptyList())
                }
            ))
        )

        /**
         * 创建带整数参数的工具
         */
        fun createWithIntParams(
            name: String,
            description: String,
            params: Map<String, String>,
            required: List<String> = emptyList()
        ): Tool {
            val properties = params.map { (name, desc) ->
                name to JsonObject(mapOf(
                    "type" to JsonPrimitive("integer"),
                    "description" to JsonPrimitive(desc)
                ))
            }.toMap()

            return create(
                name = name,
                description = description,
                parameters = JsonObject(mapOf(
                    "type" to JsonPrimitive("object"),
                    "properties" to JsonObject(properties),
                    "required" to kotlinx.serialization.json.JsonArray(
                        required.map { JsonPrimitive(it) }
                    )
                ))
            )
        }

        /**
         * 创建枚举参数工具
         */
        fun createWithEnumParam(
            name: String,
            description: String,
            paramName: String,
            paramDescription: String,
            enumValues: List<String>,
            required: Boolean = true
        ): Tool = create(
            name = name,
            description = description,
            parameters = JsonObject(mapOf(
                "type" to JsonPrimitive("object"),
                "properties" to JsonObject(mapOf(
                    paramName to JsonObject(mapOf(
                        "type" to JsonPrimitive("string"),
                        "description" to JsonPrimitive(paramDescription),
                        "enum" to kotlinx.serialization.json.JsonArray(
                            enumValues.map { JsonPrimitive(it) }
                        )
                    ))
                )),
                "required" to if (required) {
                    kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive(paramName)))
                } else {
                    kotlinx.serialization.json.JsonArray(emptyList())
                }
            ))
        )
    }
}

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
 * 工具选择模式
 */
@Serializable
enum class ToolChoice {
    @SerialName("auto")
    AUTO,       // 自动选择

    @SerialName("any")
    ANY,        // 必须调用至少一个工具

    @SerialName("none")
    NONE        // 不调用工具
}

/**
 * 工具调用结果
 */
data class ToolResult(
    val toolCallId: String,
    val functionName: String,
    val result: String,
    val isError: Boolean = false
) {
    companion object {
        fun success(toolCallId: String, functionName: String, result: String): ToolResult =
            ToolResult(toolCallId, functionName, result, isError = false)

        fun error(toolCallId: String, functionName: String, error: String): ToolResult =
            ToolResult(toolCallId, functionName, error, isError = true)
    }
}