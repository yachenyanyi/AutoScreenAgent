package com.example.myapplication.ai

import org.json.JSONObject

/**
 * AI 返回的行动命令
 *
 * 支持两种模式：
 * 1. accessibility - 使用无障碍 API（tap_by_text, tap_by_id 等）
 * 2. screenshot - 使用坐标点击（Tap [x, y]）
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
    fun shouldExecute(): Boolean = _metadata == "do"

    /**
     * 获取操作模式
     */
    fun getMode(): ActionMode = when (action.lowercase()) {
        "tap_by_text", "tap_by_id", "type_text", "system_back", "system_home", "swipe" -> ActionMode.ACCESSIBILITY
        "tap", "swipe", "type", "longpress", "back", "home", "scroll", "inputtext" -> ActionMode.SCREENSHOT
        else -> ActionMode.UNKNOWN
    }

    /**
     * 转换为可执行的 Action 对象
     */
    fun toExecutableAction(): ExecutableAction? {
        if (!shouldExecute()) return null

        return when (action.lowercase()) {
            // 无障碍模式
            "tap_by_text" -> ExecutableAction.TapByElement(text = params?.text ?: return null)
            "tap_by_id" -> ExecutableAction.TapById(viewId = params?.viewId ?: return null)
            "type_text" -> ExecutableAction.TypeText(text = params?.text ?: return null)
            "system_back" -> ExecutableAction.Back
            "system_home" -> ExecutableAction.Home
            "swipe" -> ExecutableAction.Swipe(direction = params?.direction ?: "up")

            // 截图坐标模式
            "tap" -> {
                val coords = element ?: return null
                if (coords.size >= 2) ExecutableAction.TapCoords(x = coords[0], y = coords[1])
                else null
            }
            "type", "inputtext" -> {
                val coords = element
                val inputText = text ?: params?.text ?: return null
                if (coords?.size == 2) {
                    ExecutableAction.TapAndType(x = coords[0], y = coords[1], text = inputText)
                } else {
                    ExecutableAction.TypeText(text = inputText)
                }
            }
            "swipe" -> {
                val coords = element ?: return null
                if (coords.size >= 4) {
                    ExecutableAction.SwipeCoords(
                        startX = coords[0],
                        startY = coords[1],
                        endX = coords[2],
                        endY = coords[3],
                        duration = duration ?: 300
                    )
                } else if (coords.size == 1 && coords[0] in -1..4) {
                    // direction 数组
                    val directionMap = listOf("up", "down", "left", "right")
                    val direction = if (coords[0] >= 0 && coords[0] < directionMap.size) {
                        directionMap[coords[0]]
                    } else "up"
                    ExecutableAction.Swipe(direction = direction)
                } else {
                    null
                }
            }
            "longpress" -> {
                val coords = element ?: return null
                if (coords.size >= 2) {
                    ExecutableAction.LongPress(
                        x = coords[0],
                        y = coords[1],
                        duration = duration ?: 500
                    )
                } else null
            }
            "back" -> ExecutableAction.Back
            "home" -> ExecutableAction.Home
            "scroll" -> {
                val direction = params?.direction ?: element?.firstOrNull()?.let {
                    listOf("up", "down", "left", "right").getOrNull(it) ?: "up"
                } ?: "up"
                ExecutableAction.Swipe(direction = direction)
            }

            else -> null
        }
    }
}

/**
 * 行动参数
 */
data class ActionParams(
    val text: String?,
    val viewId: String?,
    val direction: String?,
    val coords: List<Int>?
)

/**
 * 操作模式
 */
enum class ActionMode {
    ACCESSIBILITY,
    SCREENSHOT,
    UNKNOWN
}

/**
 * 可执行的行动
 */
sealed class ExecutableAction {
    // 无障碍模式
    data class TapByElement(val text: String) : ExecutableAction()
    data class TapById(val viewId: String) : ExecutableAction()
    data class TypeText(val text: String) : ExecutableAction()
    data class Swipe(val direction: String) : ExecutableAction()

    // 坐标模式
    data class TapCoords(val x: Int, val y: Int) : ExecutableAction()
    data class TapAndType(val x: Int, val y: Int, val text: String) : ExecutableAction()
    data class SwipeCoords(val startX: Int, val startY: Int, val endX: Int, val endY: Int, val duration: Int) : ExecutableAction()
    data class LongPress(val x: Int, val y: Int, val duration: Int) : ExecutableAction()

    // 通用
    object Back : ExecutableAction()
    object Home : ExecutableAction()
}

/**
 * AI 响应解析器
 */
object AIResponseParser {

    /**
     * 解析 AI 返回的 JSON
     */
    fun parse(jsonString: String): AIResponse? {
        return try {
            val json = JSONObject(jsonString)
            val thought = json.optString("thought", "")
            val mode = json.optString("mode", "unknown")
            val actionsArray = json.optJSONArray("actions")

            val actions = mutableListOf<ActionCommand>()
            if (actionsArray != null) {
                for (i in 0 until actionsArray.length()) {
                    val actionJson = actionsArray.getJSONObject(i)
                    actions.add(parseAction(actionJson))
                }
            }

            AIResponse(
                thought = thought,
                mode = when (mode) {
                    "accessibility" -> ActionMode.ACCESSIBILITY
                    "screenshot" -> ActionMode.SCREENSHOT
                    else -> ActionMode.UNKNOWN
                },
                actions = actions
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解析单个行动命令
     */
    private fun parseAction(json: JSONObject): ActionCommand {
        val action = json.optString("action", "")
        val _metadata = json.optString("_metadata", "")

        // 解析 params 对象
        val params = json.optJSONObject("params")?.let { paramsJson ->
            ActionParams(
                text = paramsJson.optString("text", null),
                viewId = paramsJson.optString("viewId", null),
                direction = paramsJson.optString("direction", null),
                coords = null
            )
        }

        // 解析 element 数组
        val element = json.optJSONArray("element")?.let { arr ->
            mutableListOf<Int>().apply {
                for (i in 0 until arr.length()) {
                    add(arr.getInt(i))
                }
            }
        }

        // 解析 text 字段
        val text = json.optString("text", null).takeIf { it != null }

        // 解析 duration 字段
        val duration = json.optInt("duration", 0).takeIf { it > 0 }

        return ActionCommand(
            action = action,
            params = params,
            element = element,
            text = text,
            duration = duration,
            _metadata = _metadata
        )
    }

    /**
     * 从混合内容中提取 JSON（处理 AI 返回的思考过程 + JSON）
     */
    fun extractJsonFromContent(content: String): String? {
        // 尝试查找 ```json ... ``` 代码块
        val codeBlockRegex = """```json\s*(.+?)\s*```""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val codeBlockMatch = codeBlockRegex.find(content)
        if (codeBlockMatch != null) {
            return codeBlockMatch.groupValues[1]
        }

        // 尝试查找 ``` ... ``` 代码块（没有 json 标记）
        val genericCodeBlockRegex = """```\s*(\{.+?\})\s*```""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val genericMatch = genericCodeBlockRegex.find(content)
        if (genericMatch != null) {
            return genericMatch.groupValues[1]
        }

        // 尝试直接解析整个内容为 JSON
        return try {
            JSONObject(content.trim())
            content.trim()
        } catch (e: Exception) {
            // 尝试查找最后一个 JSON 对象
            val lastJsonStart = content.lastIndexOf('{')
            if (lastJsonStart >= 0) {
                content.substring(lastJsonStart).trim()
            } else {
                null
            }
        }
    }
}

/**
 * AI 响应
 */
data class AIResponse(
    val thought: String,
    val mode: ActionMode,
    val actions: List<ActionCommand>
)
