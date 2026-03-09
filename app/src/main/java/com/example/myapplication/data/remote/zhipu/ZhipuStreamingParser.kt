package com.example.autoscreenagent.data.remote.zhipu

import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 智谱 SSE 流解析器
 *
 * 解析智谱 GLM API 的 SSE 流式响应
 *
 * SSE 格式示例：
 * ```
 * data: {"id":"xxx","choices":[{"delta":{"role":"assistant"}}]}
 * data: {"choices":[{"delta":{"reasoning_content":"思考中..."}}]}
 * data: {"choices":[{"delta":{"content":"正式回复..."}}]}
 * data: [DONE]
 * ```
 */
object ZhipuStreamingParser {

    private const val TAG = "ZhipuStreamingParser"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * 解析 SSE 流，返回流式响应块
     *
     * @param responseBody OkHttp ResponseBody
     * @return Flow<ZhipuStreamChunk>
     */
    fun parseStream(responseBody: ResponseBody): Flow<ZhipuStreamChunk> = callbackFlow {
        val reader = BufferedReader(InputStreamReader(responseBody.byteStream()))

        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val trimmedLine = line?.trim() ?: continue

                // 跳过空行
                if (trimmedLine.isEmpty()) continue

                // 处理 data: 开头的行
                if (trimmedLine.startsWith("data:")) {
                    val dataContent = trimmedLine.substringAfter("data:").trim()

                    // 检查是否是结束标记
                    if (dataContent == "[DONE]") {
                        Log.d(TAG, "SSE 流结束")
                        break
                    }

                    // 解析 JSON
                    val chunk = parseLine(dataContent)
                    if (chunk != null) {
                        trySend(chunk)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析 SSE 流失败: ${e.message}", e)
        } finally {
            reader.close()
            responseBody.close()
        }

        close()
        awaitClose { }
    }

    /**
     * 解析单个 data: 行
     *
     * @param line JSON 字符串
     * @return ZhipuStreamChunk 或 null
     */
    fun parseLine(line: String): ZhipuStreamChunk? {
        if (line.isBlank()) return null

        // 调试：打印原始行
        if (line.contains("tool_calls") || line.contains("function")) {
            Log.d(TAG, "原始响应包含 tool_calls: ${line.take(500)}")
        }

        return try {
            json.decodeFromString<ZhipuStreamChunk>(line)
        } catch (e: Exception) {
            Log.w(TAG, "解析 JSON 失败: ${e.message}, line: ${line.take(100)}")
            null
        }
    }

    /**
     * 手动解析 SSE 行（不使用 kotlinx.serialization）
     *
     * 用于调试或处理特殊格式
     */
    fun parseLineManually(line: String): ZhipuStreamChunk? {
        if (line.isBlank()) return null

        return try {
            // 简单解析：提取 content 和 reasoning_content
            var content: String? = null
            var reasoningContent: String? = null
            var role: String? = null
            var finishReason: String? = null

            // 提取 role
            val roleMatch = """"role"\s*:\s*"([^"]+)"""".toRegex().find(line)
            if (roleMatch != null) {
                role = roleMatch.groupValues[1]
            }

            // 提取 content
            val contentMatch = """"content"\s*:\s*"((?:[^"\\]|\\.)*)"""".toRegex().find(line)
            if (contentMatch != null) {
                content = contentMatch.groupValues[1]
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\r", "\r")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            }

            // 提取 reasoning_content
            val reasoningMatch = """"reasoning_content"\s*:\s*"((?:[^"\\]|\\.)*)"""".toRegex().find(line)
            if (reasoningMatch != null) {
                reasoningContent = reasoningMatch.groupValues[1]
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\r", "\r")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            }

            // 提取 finish_reason
            val finishMatch = """"finish_reason"\s*:\s*"([^"]+)"""".toRegex().find(line)
            if (finishMatch != null) {
                finishReason = finishMatch.groupValues[1]
            }

            // 构建 ZhipuStreamChunk
            val delta = ZhipuDelta(
                role = role,
                content = content,
                reasoningContent = reasoningContent
            )
            val choice = ZhipuStreamChoice(
                index = 0,
                delta = delta,
                finishReason = finishReason
            )

            ZhipuStreamChunk(
                choices = listOf(choice)
            )
        } catch (e: Exception) {
            Log.e(TAG, "手动解析失败: ${e.message}")
            null
        }
    }
}