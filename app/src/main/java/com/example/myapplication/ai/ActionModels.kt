package com.example.autoscreenagent.ai

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
    data class GetInstalledApps(
        val filter: String? = null,
        val includeSystem: Boolean = false,
        val limit: Int = 50
    ) : ExecutableAction()
    data class Finish(val message: String) : ExecutableAction()
}

data class AIResponse(
    val thought: String,
    val mode: ActionMode,
    val actions: List<ActionCommand>
)