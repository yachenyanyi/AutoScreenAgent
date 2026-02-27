package com.example.autoscreenagent

/**
 * 聊天消息数据类
 */
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 聚合消息数据类
 *
 * 用于将多次 Agent 执行的日志聚合为一条消息
 */
data class AggregatedMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val summary: String,              // 摘要（最后一条重要日志）
    val details: List<String>,         // 详细日志列表
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isExpanded: Boolean = false,   // 是否展开显示详情
    val status: MessageStatus = MessageStatus.RUNNING  // 消息状态
)

/**
 * 消息状态
 */
enum class MessageStatus {
    RUNNING,    // 执行中
    SUCCESS,    // 成功完成
    ERROR,      // 出错
    STOPPED     // 用户停止
}