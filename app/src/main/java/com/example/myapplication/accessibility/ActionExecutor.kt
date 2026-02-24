package com.example.autoscreenagent.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi

/**
 * 动作执行器
 *
 * 执行优先级：
 * 1. 按 ID 查找 (最精准)
 * 2. 按文本查找 (最常用)
 * 3. 按内容描述查找
 * 4. 按坐标查找 (备选)
 */
class ActionExecutor {

    companion object {
        private const val TAG = "ActionExecutor"
        private const val DEFAULT_MAX_DEPTH = 20
    }

    private val service: MyAccessibilityService

    constructor() {
        this.service = MyAccessibilityService.getInstance()
            ?: throw IllegalStateException("AccessibilityService not connected")
    }

    constructor(service: MyAccessibilityService) {
        this.service = service
    }

    // ==================== 主执行方法 ====================

    /**
     * 执行动作
     */
    fun execute(action: Action): ActionResult {
        return when (action.action) {
            ActionType.LAUNCH.value -> executeLaunch(action)
            ActionType.TAP.value -> executeTap(action)
            ActionType.TYPE.value -> executeType(action)
            ActionType.SWIPE.value -> executeSwipe(action)
            ActionType.BACK.value -> executeBack(action)
            ActionType.HOME.value -> executeHome(action)
            ActionType.FINISH.value -> executeFinish(action)
            ActionType.CONFIRM.value -> executeConfirm(action)
            else -> ActionResult.Failure("未知动作类型：${action.action}")
        }
    }

    // ==================== Launch ====================

    private fun executeLaunch(action: Action): ActionResult {
        val packageName = action.params["app"] as? String
            ?: return ActionResult.Failure("缺少参数：app")

        return try {
            val intent = service.packageManager.getLaunchIntentForPackage(packageName)
                ?: return ActionResult.Failure("应用未安装：$packageName")

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            service.startActivity(intent)

            Log.d(TAG, "启动应用：$packageName")
            ActionResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "启动应用失败", e)
            ActionResult.Failure("启动失败：${e.message}")
        }
    }

    // ==================== Tap ====================

    private fun executeTap(action: Action): ActionResult {
        // 优先按 ID 查找
        val viewId = action.params["id"] as? String
        if (viewId != null) {
            return tapById(viewId)
        }

        // 其次按文本查找
        val text = action.params["text"] as? String
        if (text != null) {
            return tapByText(text)
        }

        // 最后按坐标查找
        val x = (action.params["x"] as? Number)?.toInt()
        val y = (action.params["y"] as? Number)?.toInt()
        if (x != null && y != null) {
            return tapAtCoord(x, y)
        }

        return ActionResult.Failure("缺少点击参数：需要 id、text 或坐标 x,y")
    }

    /**
     * 按 ID 点击
     */
    fun tapById(viewId: String): ActionResult {
        val root = service.rootInActiveWindow
            ?: return ActionResult.Failure("无法获取根节点")

        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        if (nodes.isEmpty()) {
            return ActionResult.NotFound("id", viewId)
        }

        val node = nodes.firstOrNull { it.isClickable && it.isEnabled }
            ?: nodes.firstOrNull()
            ?: return ActionResult.Failure("节点不可点击")

        val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        Log.d(TAG, "点击 ID: $viewId, 成功：$success")
        return if (success) ActionResult.Success
               else ActionResult.Failure("点击失败")
    }

    /**
     * 按文本点击
     */
    fun tapByText(text: String, maxDepth: Int = DEFAULT_MAX_DEPTH): ActionResult {
        val root = service.rootInActiveWindow
            ?: return ActionResult.Failure("无法获取根节点")

        val node = findNodeByText(root, text, 0, maxDepth)
            ?: return ActionResult.NotFound("text", text)

        val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        Log.d(TAG, "点击文本：'$text', 成功：$success")
        return if (success) ActionResult.Success
               else ActionResult.Failure("点击失败")
    }

    /**
     * 按坐标点击（使用手势）
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun tapAtCoord(x: Int, y: Int): ActionResult {
        return performGesture(
            startX = x.toFloat(),
            startY = y.toFloat(),
            endX = x.toFloat(),
            endY = y.toFloat(),
            duration = 100
        )
    }

    // ==================== Type ====================

    /**
     * 执行输入操作
     */
    private fun executeType(action: Action): ActionResult {
        val text = action.params["text"] as? String
            ?: return ActionResult.Failure("缺少参数：text")
        return typeText(text)
    }

    /**
     * 输入文字
     */
    fun typeText(text: String): ActionResult {
        val root = service.rootInActiveWindow
            ?: return ActionResult.Failure("无法获取根节点")

        Log.d(TAG, "typeText: 开始输入 '$text'")

        // 先查找当前聚焦的输入框
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null) {
            Log.d(TAG, "typeText: 找到已聚焦的输入框")
            val result = setText(focused, text)
            if (result is ActionResult.Success) return result
        }

        // 查找可编辑的 EditText（优先查找 class 名）
        val editText = findEditText(root)
        if (editText != null) {
            Log.d(TAG, "typeText: 找到 EditText，尝试聚焦")
            // 先尝试聚焦
            val focusSuccess = editText.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            Log.d(TAG, "typeText: 聚焦结果：$focusSuccess")
            Thread.sleep(100) // 等待聚焦

            // 尝试点击
            val clickSuccess = editText.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "typeText: 点击结果：$clickSuccess")
            Thread.sleep(100)

            val result = setText(editText, text)
            if (result is ActionResult.Success) return result
        }

        Log.d(TAG, "typeText: 未找到输入框或输入失败")
        return ActionResult.Failure("未找到输入框或不支持此输入方式")
    }

    private fun setText(node: AccessibilityNodeInfo, text: String): ActionResult {
        Log.d(TAG, "setText: 设置文本 '$text'")

        // 方法 1: 使用 ACTION_SET_TEXT
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }

        var success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        Log.d(TAG, "setText: ACTION_SET_TEXT 结果：$success")

        if (success) {
            return ActionResult.Success
        }

        // 方法 2: 使用 ACTION_SET_TEXT + ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE
        success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT)
        Log.d(TAG, "setText: ACTION_SET_TEXT(无参数) 结果：$success")

        if (success) {
            return ActionResult.Success
        }

        // 方法 3: 尝试逐字符输入（使用 ACTION_INSERT）
        for (char in text) {
            val charArgs = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, char.toString())
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, charArgs)
        }
        Log.d(TAG, "setText: 尝试逐字符输入完成")

        return ActionResult.Failure("输入框不支持标准输入 API")
    }

    private fun findEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 检查节点是否是可编辑的
        if (node.isEditable) {
            Log.d(TAG, "findEditText: 找到可编辑节点")
            return node
        }

        // 检查类名是否包含 EditText
        val className = node.className?.toString() ?: ""
        if (className.contains("EditText", ignoreCase = true) ||
            className.contains("Editable", ignoreCase = true)) {
            Log.d(TAG, "findEditText: 找到 EditText 节点，className=$className")
            return node
        }

        // 递归查找子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditText(child)
            if (found != null) return found
        }
        return null
    }

    // ==================== Swipe ====================

    /**
     * 执行滑动操作
     */
    private fun executeSwipe(action: Action): ActionResult {
        val x1 = (action.params["x1"] as? Number)?.toFloat()
        val y1 = (action.params["y1"] as? Number)?.toFloat()
        val x2 = (action.params["x2"] as? Number)?.toFloat()
        val y2 = (action.params["y2"] as? Number)?.toFloat()
        val duration = (action.params["duration"] as? Number)?.toLong() ?: 300L

        if (x1 == null || y1 == null || x2 == null || y2 == null) {
            return ActionResult.Failure("缺少滑动参数：需要 x1, y1, x2, y2")
        }

        return swipe(x1, y1, x2, y2, duration)
    }

    /**
     * 滑动手势
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun swipe(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        duration: Long = 300
    ): ActionResult {
        return performGesture(startX, startY, endX, endY, duration)
    }

    /**
     * 向上滑动
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun swipeUp(): ActionResult {
        val root = service.rootInActiveWindow ?: return ActionResult.Failure("无法获取根节点")
        val rect = Rect()
        root.getBoundsInScreen(rect)

        val centerX = rect.centerX().toFloat()
        val startY = rect.bottom.toFloat() * 0.7f
        val endY = rect.top.toFloat() * 1.3f

        return swipe(centerX, startY, centerX, endY, 300)
    }

    /**
     * 向下滑动
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun swipeDown(): ActionResult {
        val root = service.rootInActiveWindow ?: return ActionResult.Failure("无法获取根节点")
        val rect = Rect()
        root.getBoundsInScreen(rect)

        val centerX = rect.centerX().toFloat()
        val startY = rect.top.toFloat() * 0.3f
        val endY = rect.bottom.toFloat() * 0.7f

        return swipe(centerX, startY, centerX, endY, 300)
    }

    /**
     * 向左滑动
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun swipeLeft(): ActionResult {
        val root = service.rootInActiveWindow ?: return ActionResult.Failure("无法获取根节点")
        val rect = Rect()
        root.getBoundsInScreen(rect)

        val startX = rect.right.toFloat() * 0.7f
        val centerY = rect.centerY().toFloat()
        val endX = rect.left.toFloat() * 1.3f

        return swipe(startX, centerY, endX, centerY, 300)
    }

    /**
     * 向右滑动
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun swipeRight(): ActionResult {
        val root = service.rootInActiveWindow ?: return ActionResult.Failure("无法获取根节点")
        val rect = Rect()
        root.getBoundsInScreen(rect)

        val startX = rect.left.toFloat() * 0.3f
        val centerY = rect.centerY().toFloat()
        val endX = rect.right.toFloat() * 0.7f

        return swipe(startX, centerY, endX, centerY, 300)
    }

    /**
     * 长按坐标
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun longPress(x: Int, y: Int, duration: Long = 500): ActionResult {
        return performGesture(
            startX = x.toFloat(),
            startY = y.toFloat(),
            endX = x.toFloat(),
            endY = y.toFloat(),
            duration = duration
        )
    }

    /**
     * 长按文本
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun longPressByText(text: String, duration: Long = 500): ActionResult {
        val root = service.rootInActiveWindow
            ?: return ActionResult.Failure("无法获取根节点")

        val node = findNodeByText(root, text, 0, DEFAULT_MAX_DEPTH)
            ?: return ActionResult.NotFound("text", text)

        val rect = Rect()
        node.getBoundsInScreen(rect)
        return longPress(rect.centerX(), rect.centerY(), duration)
    }

    /**
     * 长按 ID
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun longPressById(viewId: String, duration: Long = 500): ActionResult {
        val root = service.rootInActiveWindow
            ?: return ActionResult.Failure("无法获取根节点")

        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        if (nodes.isEmpty()) {
            return ActionResult.NotFound("id", viewId)
        }

        val node = nodes.firstOrNull() ?: return ActionResult.Failure("节点不存在")
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return longPress(rect.centerX(), rect.centerY(), duration)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun performGesture(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        duration: Long
    ): ActionResult {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val result = service.dispatchGesture(gesture, null, null)

        Log.d(TAG, "执行手势：($startX,$startY)->($endX,$endY), 成功：$result")
        return if (result) ActionResult.Success
               else ActionResult.Failure("手势执行失败")
    }

    // ==================== Back/Home ====================

    /**
     * 返回操作
     */
    fun executeBack(action: Action): ActionResult {
        val success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        Log.d(TAG, "返回操作，成功：$success")
        return if (success) ActionResult.Success
               else ActionResult.Failure("返回失败")
    }

    /**
     * Home 操作
     */
    fun executeHome(action: Action): ActionResult {
        val success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        Log.d(TAG, "Home 操作，成功：$success")
        return if (success) ActionResult.Success
               else ActionResult.Failure("Home 失败")
    }

    // ==================== Finish/Confirm ====================

    /**
     * 完成操作
     */
    fun executeFinish(action: Action): ActionResult {
        val message = action.params["message"] as? String ?: ""
        Log.d(TAG, "任务完成：$message")
        // 这里可以添加回调通知
        return ActionResult.Success
    }

    /**
     * 确认操作（需要前端弹窗）
     */
    fun executeConfirm(action: Action): ActionResult {
        val message = action.params["message"] as? String ?: "确认执行此操作？"
        Log.d(TAG, "请求确认：$message")
        // 需要前端弹窗让用户确认
        return ActionResult.Success
    }

    // ==================== 辅助方法 ====================

    /**
     * 按文本查找节点（递归深度优先）
     */
    private fun findNodeByText(
        node: AccessibilityNodeInfo,
        targetText: String,
        depth: Int,
        maxDepth: Int
    ): AccessibilityNodeInfo? {
        if (depth > maxDepth) return null

        // 检查当前节点
        val text = node.text?.toString()
        if (text.equals(targetText, ignoreCase = true)) {
            return node
        }

        // 检查内容描述
        val contentDesc = node.contentDescription?.toString()
        if (contentDesc.equals(targetText, ignoreCase = true)) {
            return node
        }

        // 递归查找子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByText(child, targetText, depth + 1, maxDepth)
            if (found != null) return found
        }

        return null
    }

    /**
     * 按内容描述查找节点
     */
    fun findNodeByContentDesc(contentDesc: String): ActionResult {
        val root = service.rootInActiveWindow
            ?: return ActionResult.Failure("无法获取根节点")

        val node = findNodeByContentDescRecursive(root, contentDesc, 0, DEFAULT_MAX_DEPTH)
            ?: return ActionResult.NotFound("contentDescription", contentDesc)

        val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return if (success) ActionResult.Success
               else ActionResult.Failure("点击失败")
    }

    private fun findNodeByContentDescRecursive(
        node: AccessibilityNodeInfo,
        targetDesc: String,
        depth: Int,
        maxDepth: Int
    ): AccessibilityNodeInfo? {
        if (depth > maxDepth) return null

        val desc = node.contentDescription?.toString()
        if (desc.equals(targetDesc, ignoreCase = true)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByContentDescRecursive(child, targetDesc, depth + 1, maxDepth)
            if (found != null) return found
        }

        return null
    }

    /**
     * 获取节点的中心坐标
     */
    private fun getNodeCenter(node: AccessibilityNodeInfo): Pair<Int, Int> {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return Pair(rect.centerX(), rect.centerY())
    }
}
