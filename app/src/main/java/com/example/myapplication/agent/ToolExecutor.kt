package com.example.autoscreenagent.agent

import android.content.Context
import android.util.Log
import com.example.autoscreenagent.ai.CommandExecutor
import com.example.autoscreenagent.ai.ExecutableAction

/**
 * 工具执行器
 *
 * 负责执行模型通过 Tool Calling 触发的工具调用
 */
class ToolExecutor(private val context: Context) {

    companion object {
        private const val TAG = "ToolExecutor"
    }

    private val commandExecutor = CommandExecutor(context)

    /**
     * 执行工具调用
     *
     * @param name 函数名
     * @param arguments JSON 字符串格式的参数
     * @return 执行结果（JSON 字符串）
     */
    suspend fun executeTool(name: String, arguments: String): String {
        Log.d(TAG, "执行工具: $name, 参数: $arguments")

        // 转换为可执行动作
        val action = AgentTools.toExecutableAction(name, arguments)
        if (action == null) {
            Log.e(TAG, "未知工具: $name")
            return """{"success": false, "error": "未知工具: $name"}"""
        }

        // 执行动作并收集结果
        return try {
            val result = commandExecutor.executeAction(action, null)
            formatResult(result)
        } catch (e: Exception) {
            Log.e(TAG, "执行工具失败: ${e.message}", e)
            """{"success": false, "error": "${e.message}"}"""
        }
    }

    /**
     * 格式化执行结果为 JSON 字符串
     */
    private fun formatResult(result: CommandExecutor.ExecuteResult): String {
        return if (result.success) {
            """{"success": true, "message": "${result.message.replace("\"", "\\\"")}"}"""
        } else {
            """{"success": false, "error": "${result.message.replace("\"", "\\\"")}"}"""
        }
    }
}