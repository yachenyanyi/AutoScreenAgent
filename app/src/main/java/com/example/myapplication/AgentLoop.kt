package com.example.autoscreenagent

import android.util.Log
import com.example.autoscreenagent.ai.AIResponseParser
import com.example.autoscreenagent.ai.CommandExecutor
import com.example.autoscreenagent.accessibility.AccessibilityManager
import com.example.autoscreenagent.accessibility.ScreenshotManager
import com.example.autoscreenagent.data.remote.LangGraphClient
import kotlinx.coroutines.delay

/**
 * AI Agent 循环执行器
 *
 * 负责与 LangGraph Server 通信并执行 AI 指令
 */
object AgentLoop {

    private const val TAG = "AgentLoop"
    private const val MAX_ITERATIONS = 10

    /**
     * 运行 Agent 循环
     *
     * @param goal 用户目标
     * @param langGraphClient LangGraph 客户端
     * @param screenshotManager 截屏管理器
     * @param commandExecutor 命令执行器
     * @param onLog 日志回调
     * @param onStatus 状态回调
     * @param onComplete 完成回调
     */
    suspend fun run(
        goal: String,
        langGraphClient: LangGraphClient,
        screenshotManager: ScreenshotManager,
        commandExecutor: CommandExecutor,
        onLog: (String) -> Unit,
        onStatus: (String) -> Unit,
        onComplete: () -> Unit
    ) {
        var iteration = 0
        var taskCompleted = false
        var lastActionResults = mutableListOf<String>()
        var lastScreenshotBase64: String? = null

        try {
            while (!taskCompleted && iteration < MAX_ITERATIONS) {
                iteration++
                onStatus("第 $iteration 次循环")

                val service = AccessibilityManager.getService()
                if (service == null) {
                    onLog("错误：无障碍服务未连接")
                    onStatus("服务未连接")
                    return
                }

                onStatus("发送 AI 分析...")

                // 构建消息：只发送增量信息
                val feedbackContent = if (lastActionResults.isNotEmpty()) {
                    lastActionResults.joinToString("\n")
                } else {
                    "请分析并返回下一步操作指令。"
                }

                val message = """用户目标：$goal

执行结果反馈：
$feedbackContent

请根据以上执行结果，分析当前状态并返回下一步操作指令。

"""

                onLog("发送消息给 AI...")

                // 收集 AI 响应
                val responseFlow = langGraphClient.sendMessage(message, lastScreenshotBase64)
                var lastValidResponse = ""
                var fullContent = StringBuilder()
                var lastAdditionalKwargs: String? = null

                responseFlow.collect { chunk ->
                    Log.d("LangGraph", "收到响应 chunk: ${chunk.take(300)}...")
                    if (chunk.trim().startsWith("{")) {
                        lastValidResponse = chunk
                        try {
                            val json = org.json.JSONObject(chunk)
                            val messagesArray = json.optJSONArray("messages")
                            if (messagesArray != null && messagesArray.length() > 0) {
                                val msg = messagesArray.getJSONObject(0)
                                val content = msg.optString("content", "")
                                val addKwargs = msg.optJSONObject("additional_kwargs")

                                if (content.isNotEmpty()) {
                                    fullContent.append(content)
                                }
                                if (addKwargs != null && (addKwargs.has("action") || addKwargs.has("actions"))) {
                                    lastAdditionalKwargs = addKwargs.toString()
                                    Log.d("LangGraph", "找到 additional_kwargs: $lastAdditionalKwargs")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("LangGraph", "解析 chunk 失败", e)
                        }
                    }
                }

                // 构建最终响应
                val finalResponse = if (lastAdditionalKwargs != null) {
                    """{"messages": [{"role": "assistant", "content": "", "additional_kwargs": $lastAdditionalKwargs}]}"""
                } else if (fullContent.isNotEmpty()) {
                    """{"messages": [{"role": "assistant", "content": "${fullContent.toString().replace("\"", "\\\"")}"}]}"""
                } else {
                    lastValidResponse
                }

                Log.d("LangGraph", "最终响应：${finalResponse.take(300)}...")

                // 清空上一次的截屏数据
                lastScreenshotBase64 = null

                onStatus("解析 AI 响应...")

                // 解析 AI 响应
                val response = AIResponseParser.parse(finalResponse)
                if (response == null) {
                    onLog("错误：AI 响应解析失败")
                    onStatus("解析失败")
                    return
                }

                // 检查是否完成
                val hasFinishAction = response.actions.any {
                    it.action.lowercase() == "finish"
                }
                if (hasFinishAction) {
                    onLog("✅ 任务完成")
                    taskCompleted = true
                }

                // 执行行动
                onStatus("执行行动...")
                val results = commandExecutor.execute(response, screenshotManager)

                // 收集执行结果
                val currentActionResults = mutableListOf<String>()
                for (result in results) {
                    onLog(result.message)
                    currentActionResults.add(result.message)

                    if (result.message.contains("🎉") || result.message.contains("任务完成")) {
                        taskCompleted = true
                    }

                    // 如果是截屏操作，保存 Base64
                    if (result is CommandExecutor.ExecuteResult.ScreenshotResult) {
                        lastScreenshotBase64 = result.base64
                        onLog("截屏图像已保存，将发送给 AI")
                    }
                }

                lastActionResults = currentActionResults

                if (taskCompleted) {
                    onStatus("任务已完成")
                    break
                }

                delay(1000)
            }

            if (iteration >= MAX_ITERATIONS && !taskCompleted) {
                onLog("达到最大循环次数 ($MAX_ITERATIONS)，停止执行")
            }

        } catch (e: Exception) {
            onLog("异常：${e.message}")
        } finally {
            onComplete()
        }
    }
}