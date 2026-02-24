package com.example.autoscreenagent.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * LangGraph 助手信息
 */
@Serializable
data class AssistantInfo(
    @SerialName("assistant_id")
    val assistantId: String,
    @SerialName("graph_id")
    val graphId: String,
    @SerialName("created_at")
    val createdAt: String
)

/**
 * 线程响应
 */
@Serializable
data class ThreadResponse(
    @SerialName("thread_id")
    val threadId: String
)

/**
 * 助手搜索请求
 */
@Serializable
data class SearchRequest(
    val offset: Int = 0,
    val limit: Int = 10
)

/**
 * 流式运行请求
 */
@Serializable
data class StreamRunRequest(
    @SerialName("assistant_id")
    val assistantId: String,
    val input: Input,
    @SerialName("stream_mode")
    val streamMode: List<String> = listOf("updates")
)

/**
 * 输入消息
 */
@Serializable
data class Input(
    val messages: List<Message>
)

/**
 * 消息
 */
@Serializable
data class Message(
    val role: String,
    val content: String
)

/**
 * SSE 事件块
 */
@Serializable
data class SseChunk(
    val event: String,
    val data: String
)

/**
 * 健康检查响应
 */
@Serializable
data class OkResponse(
    val ok: Boolean
)
