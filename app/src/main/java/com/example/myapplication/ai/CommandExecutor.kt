package com.example.autoscreenagent.ai

import android.content.Context
import android.util.Log
import com.example.autoscreenagent.accessibility.AccessibilityManager
import com.example.autoscreenagent.accessibility.ActionExecutor
import com.example.autoscreenagent.accessibility.CoordinateExecutor
import com.example.autoscreenagent.accessibility.MyAccessibilityService
import com.example.autoscreenagent.accessibility.ScreenshotManager
import com.example.autoscreenagent.accessibility.ScreenContentUtils
import kotlinx.coroutines.delay

/**
 * AI 命令执行引擎
 *
 * 负责解析 AI 返回的行动指令并执行
 */
class CommandExecutor(private val context: Context) {

    companion object {
        private const val TAG = "CommandExecutor"
        private const val DEFAULT_ACTION_DELAY = 500L // 行动间隔延迟（毫秒）
        private const val LONG_PRESS_DURATION = 500L
    }

    private var actionExecutor: ActionExecutor? = null
    private var coordinateExecutor: CoordinateExecutor? = null

    private val listener: CommandExecuteListener? = null

    interface CommandExecuteListener {
        fun onActionStart(action: ExecutableAction)
        fun onActionComplete(action: ExecutableAction, success: Boolean, result: String?)
        fun onAllComplete(success: Boolean, results: List<String>)
    }

    /**
     * 执行 AI 响应
     */
    suspend fun execute(response: AIResponse, screenshotManager: ScreenshotManager? = null): List<ExecuteResult> {
        val results = mutableListOf<ExecuteResult>()

        Log.d(TAG, "开始执行 AI 响应，mode=${response.mode}, actions=${response.actions.size}")
        Log.d(TAG, "思考过程：${response.thought}")

        // 输出思考过程
        results.add(ExecuteResult.success("【AI 思考】${response.thought}"))

        // 初始化执行器
        initExecutors()

        // 依次执行每个行动
        for ((index, actionCommand) in response.actions.withIndex()) {
            Log.d(TAG, "执行行动 $index: ${actionCommand.action}")

            val executableAction = actionCommand.toExecutableAction()
            if (executableAction == null) {
                Log.w(TAG, "行动 ${actionCommand.action} 无法执行，跳过")
                results.add(ExecuteResult.failure("❌ 行动 ${actionCommand.action}: 无法解析"))
                continue
            }

            listener?.onActionStart(executableAction)

            val result = executeAction(executableAction, screenshotManager)
            listener?.onActionComplete(executableAction, result.success, result.message)

            results.add(result)

            // 行动间隔延迟
            if (index < response.actions.size - 1) {
                delay(DEFAULT_ACTION_DELAY)
            }
        }

        listener?.onAllComplete(results.all { it.success }, results.map { it.message })
        return results
    }

    /**
     * 执行单个行动
     */
    private suspend fun executeAction(action: ExecutableAction, screenshotManager: ScreenshotManager? = null): ExecuteResult {
        return when (action) {
            // 无障碍模式
            is ExecutableAction.TapByElement -> {
                val success = AccessibilityManager.tapByText(action.text)
                if (success is com.example.autoscreenagent.accessibility.ActionResult.Success) {
                    ExecuteResult.success("✅ 点击文本 '${action.text}'")
                } else {
                    ExecuteResult.failure("❌ 点击文本 '${action.text}' 失败")
                }
            }

            is ExecutableAction.TapById -> {
                val success = AccessibilityManager.tapById(action.viewId)
                if (success is com.example.autoscreenagent.accessibility.ActionResult.Success) {
                    ExecuteResult.success("✅ 点击 ID '${action.viewId}'")
                } else {
                    ExecuteResult.failure("❌ 点击 ID '${action.viewId}' 失败")
                }
            }

            is ExecutableAction.TypeText -> {
                val success = AccessibilityManager.typeText(action.text)
                if (success is com.example.autoscreenagent.accessibility.ActionResult.Success) {
                    ExecuteResult.success("✅ 输入文本 '${action.text}'")
                } else {
                    ExecuteResult.failure("❌ 输入文本 '${action.text}' 失败")
                }
            }

            is ExecutableAction.Swipe -> {
                val result = executeSwipe(action.direction)
                ExecuteResult.success(result)
            }

            is ExecutableAction.Back -> {
                val success = AccessibilityManager.back()
                if (success is com.example.autoscreenagent.accessibility.ActionResult.Success) {
                    ExecuteResult.success("✅ 返回")
                } else {
                    ExecuteResult.failure("❌ 返回失败")
                }
            }

            is ExecutableAction.Home -> {
                val success = AccessibilityManager.home()
                if (success is com.example.autoscreenagent.accessibility.ActionResult.Success) {
                    ExecuteResult.success("✅ 回到主屏幕")
                } else {
                    ExecuteResult.failure("❌ 回到主屏幕失败")
                }
            }

            is ExecutableAction.LaunchApp -> {
                val success = AccessibilityManager.launchAppDirect(context, action.packageName)
                if (success is com.example.autoscreenagent.accessibility.ActionResult.Success) {
                    ExecuteResult.success("✅ 启动应用 '${action.packageName}'")
                } else {
                    ExecuteResult.failure("❌ 启动应用 '${action.packageName}' 失败")
                }
            }

            is ExecutableAction.Finish -> {
                ExecuteResult.success("🎉 ${action.message}")
            }

            // 截屏操作
            is ExecutableAction.CaptureScreenshot -> {
                if (screenshotManager == null) {
                    ExecuteResult.failure("❌ 截屏失败：截屏管理器未初始化")
                } else {
                    val base64 = screenshotManager.captureToBase64(context)
                    if (base64 != null) {
                        Log.d(TAG, "截屏成功，Base64 长度：${base64.length}")
                        // 返回特殊结果，包含 Base64 数据
                        ExecuteResult.ScreenshotResult("✅ 截屏成功，图像已保存为 Base64（${base64.length} 字符）", base64)
                    } else {
                        Log.e(TAG, "截屏失败：未授权或错误")
                        ExecuteResult.failure("❌ 截屏失败：请确保已授权截屏服务")
                    }
                }
            }

            // 获取屏幕内容（无障碍 UI 树）
            is ExecutableAction.GetScreenContent -> {
                val screenInfo = AccessibilityManager.getScreenContent()
                if (screenInfo != null) {
                    val screenDescription = ScreenContentUtils.toFormattedString(screenInfo, maxDepth = 15)
                    Log.d(TAG, "获取屏幕内容成功，节点数：${screenInfo.nodeCount}")
                    // 返回特殊格式，包含实际内容
                    ExecuteResult.screenContent("✅ 已获取屏幕内容:\n$screenDescription")
                } else {
                    Log.e(TAG, "获取屏幕内容失败：无障碍服务未启用")
                    ExecuteResult.failure("❌ 获取屏幕内容失败：请确保已启用无障碍服务")
                }
            }

            // 坐标模式
            is ExecutableAction.TapCoords -> {
                coordinateExecutor?.tap(action.x, action.y) { success ->
                    if (success) {
                        Log.d(TAG, "坐标点击成功：(${action.x}, ${action.y})")
                    } else {
                        Log.e(TAG, "坐标点击失败：(${action.x}, ${action.y})")
                    }
                }
                delay(100) // 等待手势完成
                ExecuteResult.success("✅ 点击坐标 (${action.x}, ${action.y})")
            }

            is ExecutableAction.TapAndType -> {
                // 先点击坐标聚焦输入框
                coordinateExecutor?.tap(action.x, action.y) { success ->
                    Log.d(TAG, "点击坐标聚焦：(${action.x}, ${action.y}) 成功=$success")
                }
                delay(200)
                // 然后输入文本
                val success = AccessibilityManager.typeText(action.text)
                if (success is com.example.autoscreenagent.accessibility.ActionResult.Success) {
                    ExecuteResult.success("✅ 输入 '${action.text}'")
                } else {
                    ExecuteResult.failure("❌ 输入 '${action.text}' 失败")
                }
            }

            is ExecutableAction.SwipeCoords -> {
                coordinateExecutor?.swipe(
                    action.startX, action.startY,
                    action.endX, action.endY,
                    action.duration
                ) { success ->
                    Log.d(TAG, "滑动成功=$success")
                }
                delay(300)
                ExecuteResult.success("✅ 滑动 (${action.startX},${action.startY}) -> (${action.endX},${action.endY})")
            }

            is ExecutableAction.LongPress -> {
                coordinateExecutor?.longPress(action.x, action.y, action.duration) { success ->
                    Log.d(TAG, "长按成功=$success")
                }
                kotlinx.coroutines.delay(action.duration + 100L)
                ExecuteResult.success("✅ 长按 (${action.x}, ${action.y}) ${action.duration}ms")
            }
        }
    }

    /**
     * 执行滑动方向
     */
    private fun executeSwipe(direction: String): String {
        val executor = actionExecutor ?: return "❌ 滑动失败：执行器未初始化"

        val result = when (direction.lowercase()) {
            "up" -> executor.swipeUp()
            "down" -> executor.swipeDown()
            "left" -> executor.swipeLeft()
            "right" -> executor.swipeRight()
            else -> com.example.autoscreenagent.accessibility.ActionResult.Failure("未知方向：$direction")
        }

        return if (result is com.example.autoscreenagent.accessibility.ActionResult.Success) {
            "✅ 向$direction 滑动"
        } else {
            "❌ 向$direction 滑动失败"
        }
    }

    /**
     * 初始化执行器
     */
    private fun initExecutors() {
        if (actionExecutor == null) {
            val service = MyAccessibilityService.getInstance()
            if (service != null) {
                actionExecutor = ActionExecutor(service)
                coordinateExecutor = CoordinateExecutor(service)
                Log.d(TAG, "执行器初始化成功")
            } else {
                Log.e(TAG, "无障碍服务未连接")
            }
        }
    }

    /**
     * 执行结果
     */
    sealed class ExecuteResult(
        val success: Boolean,
        open val message: String,
        val isScreenContent: Boolean = false  // 标记是否包含屏幕内容
    ) {
        data class Success(override val message: String) : ExecuteResult(true, message)
        data class Failure(override val message: String) : ExecuteResult(false, message)
        data class ScreenContent(override val message: String) : ExecuteResult(true, message, isScreenContent = true)

        // 截屏结果，包含 Base64 数据
        data class ScreenshotResult(override val message: String, val base64: String) : ExecuteResult(true, message)

        companion object {
            fun success(message: String) = Success(message)
            fun failure(message: String) = Failure(message)
            fun screenContent(message: String) = ScreenContent(message)
        }
    }
}
