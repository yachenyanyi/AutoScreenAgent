package com.example.autoscreenagent.agent

/**
 * 工具接口
 *
 * 定义 Agent 可以使用的工具
 */
interface Tool {
    /**
     * 工具名称
     */
    val name: String

    /**
     * 工具描述（用于 AI 理解工具用途）
     */
    val description: String

    /**
     * 执行工具
     *
     * @param params 工具参数
     * @return 执行结果
     */
    suspend fun execute(params: Map<String, Any>): ToolResult
}

/**
 * 工具执行结果
 */
sealed class ToolResult {
    /**
     * 成功结果
     * @param output 输出内容
     */
    data class Success(val resultOutput: String) : ToolResult()

    /**
     * 失败结果
     * @param error 错误信息
     */
    data class Failure(val error: String) : ToolResult()

    /**
     * 是否成功
     */
    fun isSuccess(): Boolean = this is Success

    /**
     * 获取输出内容（失败时返回错误信息）
     */
    fun getResultText(): String = when (this) {
        is Success -> resultOutput
        is Failure -> "错误: $error"
    }
}