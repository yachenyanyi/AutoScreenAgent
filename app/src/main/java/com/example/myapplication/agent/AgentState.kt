package com.example.autoscreenagent.agent

/**
 * Agent 执行状态
 *
 * 用于表示 Agent 的当前执行状态，支持 UI 观察
 */
sealed class AgentState {
    /**
     * 空闲状态 - Agent 未在执行
     */
    object Idle : AgentState() {
        override fun toString() = "空闲"
    }

    /**
     * 运行中状态 - Agent 正在执行
     * @param iteration 当前迭代次数
     * @param status 当前状态描述
     */
    data class Running(
        val iteration: Int,
        val status: String
    ) : AgentState() {
        override fun toString() = "运行中 (第 $iteration 轮): $status"
    }

    /**
     * 完成状态 - Agent 已成功完成任务
     * @param message 完成消息
     */
    data class Completed(
        val message: String
    ) : AgentState() {
        override fun toString() = "✅ 完成: $message"
    }

    /**
     * 失败状态 - Agent 执行过程中出错
     * @param error 错误信息
     */
    data class Failed(
        val error: String
    ) : AgentState() {
        override fun toString() = "❌ 失败: $error"
    }

    /**
     * 取消状态 - Agent 被用户取消
     * @param reason 取消原因
     */
    data class Cancelled(
        val reason: String
    ) : AgentState() {
        override fun toString() = "⏹️ 已取消: $reason"
    }

    /**
     * 是否正在运行
     */
    fun isRunning(): Boolean = this is Running

    /**
     * 是否已结束（完成/失败/取消）
     */
    fun isFinished(): Boolean = this is Completed || this is Failed || this is Cancelled
}