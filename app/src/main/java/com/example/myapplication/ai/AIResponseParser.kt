package com.example.autoscreenagent.ai

import android.util.Log
import org.json.JSONObject

/**
 * AI 返回的行动命令
 */
data class ActionCommand(
    val action: String,
    val params: ActionParams?,
    val element: List<Int>?,
    val text: String?,
    val duration: Int?,
    val _metadata: String?
) {
    /**
     * 判断是否应该执行此命令
     */
    fun shouldExecute(): Boolean = _metadata == "do" || _metadata == "finish"

    /**
     * 转换为可执行的 Action 对象
     */
    fun toExecutableAction(): ExecutableAction? {
        if (!shouldExecute()) return null

        return when (action.lowercase()) {
            "finish" -> ExecutableAction.Finish(message = text ?: params?.text ?: "任务完成")
            "tap_by_text" -> ExecutableAction.TapByElement(text = params?.text ?: return null)
            "tap_by_id" -> ExecutableAction.TapById(viewId = params?.viewId ?: return null)
            "type_text" -> ExecutableAction.TypeText(text = params?.text ?: return null)
            "system_back" -> ExecutableAction.Back
            "system_home" -> ExecutableAction.Home
            "launch_app", "launch" -> {
                val packageName = text ?: params?.text ?: return null
                ExecutableAction.LaunchApp(packageName = packageName)
            }
            "swipe" -> {
                if (params?.direction != null) {
                    ExecutableAction.Swipe(direction = params.direction)
                } else if (element != null && element.size >= 4) {
                    ExecutableAction.SwipeCoords(
                        startX = element[0], startY = element[1],
                        endX = element[2], endY = element[3],
                        duration = duration ?: 300
                    )
                } else {
                    ExecutableAction.Swipe(direction = params?.direction ?: "up")
                }
            }
            "tap" -> {
                val coords = element ?: return null
                if (coords.size >= 2) ExecutableAction.TapCoords(x = coords[0], y = coords[1])
                else null
            }
            "type", "inputtext" -> {
                val inputText = text ?: params?.text ?: return null
                if (element?.size == 2) {
                    ExecutableAction.TapAndType(x = element[0], y = element[1], text = inputText)
                } else {
                    ExecutableAction.TypeText(text = inputText)
                }
            }
            "longpress" -> {
                val coords = element ?: return null
                if (coords.size >= 2) {
                    ExecutableAction.LongPress(x = coords[0], y = coords[1], duration = duration ?: 500)
                } else null
            }
            "scroll" -> {
                val direction = params?.direction ?: element?.firstOrNull()?.let {
                    listOf("up", "down", "left", "right").getOrNull(it) ?: "up"
                } ?: "up"
                ExecutableAction.Swipe(direction = direction)
            }
            "back" -> ExecutableAction.Back
            "home" -> ExecutableAction.Home
            "capture_screenshot", "screenshot", "take_screenshot" -> ExecutableAction.CaptureScreenshot
            "get_screen_content", "get_ui_tree", "get_screen_tree" -> ExecutableAction.GetScreenContent
            else -> null
        }
    }
}

data class ActionParams(
    val text: String?,
    val viewId: String?,
    val direction: String?,
    val coords: List<Int>?
)

enum class ActionMode { ACCESSIBILITY, SCREENSHOT, UNKNOWN }

sealed class ExecutableAction {
    data class TapByElement(val text: String) : ExecutableAction()
    data class TapById(val viewId: String) : ExecutableAction()
    data class TypeText(val text: String) : ExecutableAction()
    data class Swipe(val direction: String) : ExecutableAction()
    data class LaunchApp(val packageName: String) : ExecutableAction()
    data class TapCoords(val x: Int, val y: Int) : ExecutableAction()
    data class TapAndType(val x: Int, val y: Int, val text: String) : ExecutableAction()
    data class SwipeCoords(val startX: Int, val startY: Int, val endX: Int, val endY: Int, val duration: Int) : ExecutableAction()
    data class LongPress(val x: Int, val y: Int, val duration: Int) : ExecutableAction()
    object CaptureScreenshot : ExecutableAction()
    object GetScreenContent : ExecutableAction()
    object Back : ExecutableAction()
    object Home : ExecutableAction()
    data class Finish(val message: String) : ExecutableAction()
}

data class AIResponse(
    val thought: String,
    val mode: ActionMode,
    val actions: List<ActionCommand>
)

/**
 * AI 响应解析器
 */
object AIResponseParser {
    private const val TAG = "AIResponseParser"

    /**
     * 解析 AI 返回的 JSON
     *
     * 支持的 LangGraph 格式：
     * - values 事件：{"messages": [{"content": "{\"action\": \"...\"}"}]}
     * - updates 事件：{"model": {"messages": [{"content": "{\"action\": \"...\"}"}]}}
     */
    fun parse(jsonString: String): AIResponse? {
        return try {
            val json = JSONObject(jsonString)

            // 1. 查找 messages 数组（可能在根级别或 model 对象中）
            var messagesArray = json.optJSONArray("messages")
            if (messagesArray == null) {
                messagesArray = json.optJSONObject("model")?.optJSONArray("messages")
            }

            // 2. 从消息中提取 actions
            val actions = mutableListOf<ActionCommand>()
            var thought = ""

            if (messagesArray != null) {
                // 从最后一个消息提取（包含最新 AI 响应）
                val lastMessage = messagesArray.getJSONObject(messagesArray.length() - 1)
                extractActionsFromMessage(lastMessage, actions)
                thought = lastMessage.optJSONObject("additional_kwargs")?.optString("thought", "") ?: ""
            }

            // 3. 如果没有找到 actions，尝试从根级别解析
            if (actions.isEmpty()) {
                extractActionsFromMessage(json, actions)
            }

            Log.d(TAG, "解析到 ${actions.size} 个 actions")
            actions.forEachIndexed { i, action ->
                Log.d(TAG, "  [$i] ${action.action}, metadata=${action._metadata}")
            }

            AIResponse(thought = thought, mode = ActionMode.UNKNOWN, actions = actions)
        } catch (e: Exception) {
            Log.e(TAG, "解析失败：${e.message}", e)
            null
        }
    }

    /**
     * 从消息对象中提取 actions
     */
    private fun extractActionsFromMessage(msg: JSONObject, actions: MutableList<ActionCommand>) {
        // 尝试从 additional_kwargs.actions 数组提取
        val additionalKwargs = msg.optJSONObject("additional_kwargs")
        val actionsArray = additionalKwargs?.optJSONArray("actions")
        if (actionsArray != null) {
            for (i in 0 until actionsArray.length()) {
                actions.add(parseAction(actionsArray.getJSONObject(i)))
            }
            return
        }

        // 尝试从 content 字段提取（JSON 字符串）
        val contentStr = msg.optString("content", null)
        if (contentStr != null) {
            try {
                val contentJson = JSONObject(contentStr)
                val action = contentJson.optString("action", "")
                if (action.isNotEmpty()) {
                    actions.add(parseAction(contentJson))
                    return
                }
            } catch (e: Exception) {
                // content 不是 JSON
            }
        }

        // 尝试直接从消息对象提取
        if (msg.optString("action", "").isNotEmpty()) {
            actions.add(parseAction(msg))
        }
    }

    /**
     * 解析单个行动命令
     */
    fun parseAction(json: JSONObject): ActionCommand {
        val action = json.optString("action", "")
        val _metadata = json.optString("_metadata", "")

        val params = json.optJSONObject("params")?.let { paramsJson ->
            ActionParams(
                text = paramsJson.optString("text").takeIf { it.isNotEmpty() },
                viewId = paramsJson.optString("viewId").takeIf { it.isNotEmpty() },
                direction = paramsJson.optString("direction").takeIf { it.isNotEmpty() },
                coords = null
            )
        }

        val element = json.optJSONArray("element")?.let { arr ->
            mutableListOf<Int>().apply {
                for (i in 0 until arr.length()) add(arr.getInt(i))
            }
        }

        val text = json.optString("text").takeIf { it.isNotEmpty() }
            ?: json.optString("message").takeIf { it.isNotEmpty() }

        val duration = json.optInt("duration", 0).takeIf { it > 0 }

        return ActionCommand(action, params, element, text, duration, _metadata)
    }
}
