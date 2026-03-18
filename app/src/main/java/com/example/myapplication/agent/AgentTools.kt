package com.example.autoscreenagent.agent

import com.example.autoscreenagent.ai.ExecutableAction
import com.example.autoscreenagent.data.remote.model.Tool
import com.example.autoscreenagent.data.remote.model.ToolFunction
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONObject

/**
 * Agent 工具定义
 *
 * 定义所有可用的工具，每个工具包含：
 * - name: 函数名
 * - description: 描述（帮助模型理解工具用途）
 * - parameters: JSON Schema 参数定义
 */
object AgentTools {

    /**
     * 获取所有工具定义（用于发送给 API）
     */
    val tools: List<Tool> = listOf(
        // 点击文本元素
        createTool(
            name = "tap_by_text",
            description = "点击屏幕上包含指定文本的元素。使用此工具来点击按钮、链接或其他带有文本的元素。",
            parameters = JsonObject(mapOf(
                "type" to JsonPrimitive("object"),
                "properties" to JsonObject(mapOf(
                    "text" to JsonObject(mapOf(
                        "type" to JsonPrimitive("string"),
                        "description" to JsonPrimitive("要点击的文本内容，精确匹配")
                    ))
                )),
                "required" to JsonArray(listOf(JsonPrimitive("text")))
            ))
        ),

        // 通过 ID 点击元素
        createTool(
            name = "tap_by_id",
            description = "通过 View ID 点击元素。用于点击已知资源 ID 的元素。",
            parameters = JsonObject(mapOf(
                "type" to JsonPrimitive("object"),
                "properties" to JsonObject(mapOf(
                    "viewId" to JsonObject(mapOf(
                        "type" to JsonPrimitive("string"),
                        "description" to JsonPrimitive("元素的资源 ID，如 com.example:id/button")
                    ))
                )),
                "required" to JsonArray(listOf(JsonPrimitive("viewId")))
            ))
        ),

        // 输入文本
        createTool(
            name = "type_text",
            description = "在当前聚焦的输入框中输入文本。如果需要先点击输入框，请使用 tap_by_text 或 tap_by_id 工具。",
            parameters = JsonObject(mapOf(
                "type" to JsonPrimitive("object"),
                "properties" to JsonObject(mapOf(
                    "text" to JsonObject(mapOf(
                        "type" to JsonPrimitive("string"),
                        "description" to JsonPrimitive("要输入的文本内容")
                    ))
                )),
                "required" to JsonArray(listOf(JsonPrimitive("text")))
            ))
        ),

        // 坐标点击（备选方案，仅在 tap_by_text 和 tap_by_id 都无法使用时使用）
        createTool(
            name = "tap",
            description = "点击指定坐标位置。这是备选点击方式，仅当 tap_by_text 和 tap_by_id 都找不到目标时使用。",
            parameters = JsonObject(mapOf(
                "type" to JsonPrimitive("object"),
                "properties" to JsonObject(mapOf(
                    "x" to JsonObject(mapOf(
                        "type" to JsonPrimitive("integer"),
                        "description" to JsonPrimitive("X 坐标")
                    )),
                    "y" to JsonObject(mapOf(
                        "type" to JsonPrimitive("integer"),
                        "description" to JsonPrimitive("Y 坐标")
                    ))
                )),
                "required" to JsonArray(listOf(JsonPrimitive("x"), JsonPrimitive("y")))
            ))
        ),

        // 滑动
        createTool(
            name = "swipe",
            description = "在屏幕上滑动。可用于滚动页面或执行滑动手势。",
            parameters = JsonObject(mapOf(
                "type" to JsonPrimitive("object"),
                "properties" to JsonObject(mapOf(
                    "direction" to JsonObject(mapOf(
                        "type" to JsonPrimitive("string"),
                        "description" to JsonPrimitive("滑动方向：up, down, left, right"),
                        "enum" to JsonArray(listOf(
                            JsonPrimitive("up"),
                            JsonPrimitive("down"),
                            JsonPrimitive("left"),
                            JsonPrimitive("right")
                        ))
                    ))
                )),
                "required" to JsonArray(listOf(JsonPrimitive("direction")))
            ))
        ),

        // 坐标滑动
        createTool(
            name = "swipe_coords",
            description = "从指定起点滑动到终点。用于精确的滑动手势。",
            parameters = JsonObject(mapOf(
                "type" to JsonPrimitive("object"),
                "properties" to JsonObject(mapOf(
                    "startX" to JsonObject(mapOf(
                        "type" to JsonPrimitive("integer"),
                        "description" to JsonPrimitive("起点 X 坐标")
                    )),
                    "startY" to JsonObject(mapOf(
                        "type" to JsonPrimitive("integer"),
                        "description" to JsonPrimitive("起点 Y 坐标")
                    )),
                    "endX" to JsonObject(mapOf(
                        "type" to JsonPrimitive("integer"),
                        "description" to JsonPrimitive("终点 X 坐标")
                    )),
                    "endY" to JsonObject(mapOf(
                        "type" to JsonPrimitive("integer"),
                        "description" to JsonPrimitive("终点 Y 坐标")
                    )),
                    "duration" to JsonObject(mapOf(
                        "type" to JsonPrimitive("integer"),
                        "description" to JsonPrimitive("滑动持续时间（毫秒），默认 300")
                    ))
                )),
                "required" to JsonArray(listOf(
                    JsonPrimitive("startX"),
                    JsonPrimitive("startY"),
                    JsonPrimitive("endX"),
                    JsonPrimitive("endY")
                ))
            ))
        ),

        // 长按
        createTool(
            name = "longpress",
            description = "长按指定坐标位置。用于触发上下文菜单或其他长按操作。",
            parameters = JsonObject(mapOf(
                "type" to JsonPrimitive("object"),
                "properties" to JsonObject(mapOf(
                    "x" to JsonObject(mapOf(
                        "type" to JsonPrimitive("integer"),
                        "description" to JsonPrimitive("X 坐标")
                    )),
                    "y" to JsonObject(mapOf(
                        "type" to JsonPrimitive("integer"),
                        "description" to JsonPrimitive("Y 坐标")
                    )),
                    "duration" to JsonObject(mapOf(
                        "type" to JsonPrimitive("integer"),
                        "description" to JsonPrimitive("长按持续时间（毫秒），默认 500")
                    ))
                )),
                "required" to JsonArray(listOf(JsonPrimitive("x"), JsonPrimitive("y")))
            ))
        ),

        // 启动应用
        createTool(
            name = "launch_app",
            description = "启动指定的应用程序。需要知道应用的包名。",
            parameters = JsonObject(mapOf(
                "type" to JsonPrimitive("object"),
                "properties" to JsonObject(mapOf(
                    "package_name" to JsonObject(mapOf(
                        "type" to JsonPrimitive("string"),
                        "description" to JsonPrimitive("应用的包名，如 com.tencent.mm（微信）")
                    ))
                )),
                "required" to JsonArray(listOf(JsonPrimitive("package_name")))
            ))
        ),

        // 系统返回
        createTool(
            name = "back",
            description = "模拟系统返回键。用于返回上一页或关闭当前页面。",
            parameters = JsonObject(mapOf(
                "type" to JsonPrimitive("object"),
                "properties" to JsonObject(mapOf()),
                "required" to JsonArray(listOf())
            ))
        ),

        // 回到主页
        createTool(
            name = "home",
            description = "返回系统主屏幕。",
            parameters = JsonObject(mapOf(
                "type" to JsonPrimitive("object"),
                "properties" to JsonObject(mapOf()),
                "required" to JsonArray(listOf())
            ))
        ),

        // 获取屏幕内容
        createTool(
            name = "get_screen_content",
            description = "获取当前屏幕的无障碍 UI 树信息，包含所有可交互元素的文本、ID 和位置。用于分析屏幕结构。",
            parameters = JsonObject(mapOf(
                "type" to JsonPrimitive("object"),
                "properties" to JsonObject(mapOf()),
                "required" to JsonArray(listOf())
            ))
        )
    )

    /**
     * 创建工具定义
     */
    private fun createTool(
        name: String,
        description: String,
        parameters: JsonObject
    ): Tool {
        return Tool(
            type = "function",
            function = ToolFunction(
                name = name,
                description = description,
                parameters = parameters
            )
        )
    }

    /**
     * 将函数调用转换为可执行动作
     *
     * @param name 函数名
     * @param arguments JSON 字符串格式的参数
     * @return 可执行的动作，如果未知则返回 null
     */
    fun toExecutableAction(name: String, arguments: String): ExecutableAction? {
        val args = try {
            JSONObject(arguments)
        } catch (e: Exception) {
            return null
        }

        return when (name) {
            "tap_by_text" -> {
                val text = args.optString("text", "")
                if (text.isNotEmpty()) ExecutableAction.TapByElement(text) else null
            }

            "tap_by_id" -> {
                val viewId = args.optString("viewId", "")
                if (viewId.isNotEmpty()) ExecutableAction.TapById(viewId) else null
            }

            "type_text" -> {
                val text = args.optString("text", "")
                if (text.isNotEmpty()) ExecutableAction.TypeText(text) else null
            }

            "tap" -> {
                val x = args.optInt("x", -1)
                val y = args.optInt("y", -1)
                if (x >= 0 && y >= 0) ExecutableAction.TapCoords(x, y) else null
            }

            "swipe" -> {
                val direction = args.optString("direction", "")
                if (direction.isNotEmpty()) ExecutableAction.Swipe(direction) else null
            }

            "swipe_coords" -> {
                val startX = args.optInt("startX", -1)
                val startY = args.optInt("startY", -1)
                val endX = args.optInt("endX", -1)
                val endY = args.optInt("endY", -1)
                val duration = args.optInt("duration", 300)
                if (startX >= 0 && startY >= 0 && endX >= 0 && endY >= 0) {
                    ExecutableAction.SwipeCoords(startX, startY, endX, endY, duration)
                } else null
            }

            "longpress" -> {
                val x = args.optInt("x", -1)
                val y = args.optInt("y", -1)
                val duration = args.optInt("duration", 500)
                if (x >= 0 && y >= 0) ExecutableAction.LongPress(x, y, duration) else null
            }

            "launch_app" -> {
                val packageName = args.optString("package_name", "")
                if (packageName.isNotEmpty()) ExecutableAction.LaunchApp(packageName) else null
            }

            "back" -> ExecutableAction.Back

            "home" -> ExecutableAction.Home

            "capture_screenshot" -> ExecutableAction.CaptureScreenshot

            "get_screen_content" -> ExecutableAction.GetScreenContent

            else -> null
        }
    }
}
