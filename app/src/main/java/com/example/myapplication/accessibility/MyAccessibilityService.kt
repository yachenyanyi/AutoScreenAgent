package com.example.autoscreenagent.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 无障碍服务实现
 */
class MyAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MyAccessibilityService"

        @Volatile
        private var instance: MyAccessibilityService? = null

        fun getInstance(): MyAccessibilityService? = instance
    }

    // 悬浮窗管理器
    private var floatingWindowManager: FloatingWindowManager? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "AccessibilityService connected")

        // 初始化悬浮窗管理器
        floatingWindowManager = FloatingWindowManager(this, this)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        // 清理悬浮窗
        floatingWindowManager?.hide()
        floatingWindowManager = null
        Log.d(TAG, "AccessibilityService destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 事件处理（可选）
    }

    override fun onInterrupt() {
        Log.d(TAG, "AccessibilityService interrupted")
    }

    /**
     * 获取悬浮窗管理器
     */
    fun getFloatingWindowManager(): FloatingWindowManager? = floatingWindowManager

    /**
     * 获取当前根节点
     */
    fun getRootNode(): AccessibilityNodeInfo? {
        return rootInActiveWindow
    }

    /**
     * 获取当前应用包名
     */
    fun getCurrentApp(): String? {
        return rootInActiveWindow?.packageName?.toString()
    }

    /**
     * 触发系统截屏（Android 10+，部分机型支持）
     * 注意：这个 API 在某些机型上可能不可用
     */
    fun takeSystemScreenshot(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
        } else {
            false
        }
    }
}

/**
 * 屏幕节点信息
 */
data class ScreenNodeInfo(
    val text: String?,
    val className: String?,
    val packageName: String?,
    val viewId: String?,
    val contentDesc: String?,
    val bounds: Rect,
    val isClickable: Boolean,
    val isEnabled: Boolean,
    val isVisible: Boolean = true,  // 是否可见
    val childCount: Int,
    val depth: Int,
    val children: List<ScreenNodeInfo> = emptyList()
) {
    /**
     * 转为中心坐标
     */
    fun getCenterX(): Int = bounds.centerX()

    fun getCenterY(): Int = bounds.centerY()
}

/**
 * 屏幕信息
 */
data class ScreenInfo(
    val packageName: String?,
    val rootNodes: List<ScreenNodeInfo>,
    val nodeCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 获取屏幕内容的扩展方法
 */
fun MyAccessibilityService.getScreenContent(): ScreenInfo {
    val rootNode = rootInActiveWindow
    val packageName = rootNode?.packageName?.toString()

    val rootNodes = if (rootNode != null) {
        buildNodeTree(rootNode, 0)
    } else {
        emptyList()
    }

    val nodeCount = countNodes(rootNodes)

    return ScreenInfo(
        packageName = packageName,
        rootNodes = rootNodes,
        nodeCount = nodeCount
    )
}

/**
 * 检查节点是否有用（应包含在输出中）
 */
private fun isNodeUseful(node: AccessibilityNodeInfo): Boolean {
    // 必须可见
    if (!node.isVisibleToUser) return false

    // 至少有一个有用属性
    val hasText = !node.text.isNullOrBlank()
    val hasDesc = !node.contentDescription.isNullOrBlank()
    val hasId = !node.viewIdResourceName.isNullOrBlank()
    val isClickable = node.isClickable && node.isEnabled

    return hasText || hasDesc || hasId || isClickable
}

/**
 * 检查节点是否在屏幕内
 */
private fun isNodeOnScreen(node: AccessibilityNodeInfo): Boolean {
    val bounds = Rect()
    node.getBoundsInScreen(bounds)

    // 检查是否在屏幕外
    if (bounds.isEmpty) return false
    if (bounds.right < 0 || bounds.bottom < 0) return false

    // 假设屏幕最大尺寸为 4000（足够覆盖所有设备）
    if (bounds.left > 4000 || bounds.top > 4000) return false

    return true
}

private fun buildNodeTree(node: AccessibilityNodeInfo, depth: Int): List<ScreenNodeInfo> {
    val result = mutableListOf<ScreenNodeInfo>()

    for (i in 0 until node.childCount) {
        val child = node.getChild(i) ?: continue

        try {
            // 检查节点是否有用且在屏幕内
            if (!isNodeOnScreen(child)) {
                continue
            }

            val bounds = Rect()
            child.getBoundsInScreen(bounds)

            // 检查是否有用（可见且有内容或可点击）
            val isUseful = isNodeUseful(child)
            val hasUsefulChildren = child.childCount > 0

            // 只保留有用的节点或包含有用子节点的节点
            if (isUseful || hasUsefulChildren) {
                val nodeInfo = ScreenNodeInfo(
                    text = child.text?.toString(),
                    className = child.className?.toString(),
                    packageName = child.packageName?.toString(),
                    viewId = child.viewIdResourceName,
                    contentDesc = child.contentDescription?.toString(),
                    bounds = bounds,
                    isClickable = child.isClickable,
                    isEnabled = child.isEnabled,
                    childCount = child.childCount,
                    depth = depth,
                    isVisible = child.isVisibleToUser,  // 添加可见性属性
                    children = buildNodeTree(child, depth + 1)
                )

                result.add(nodeInfo)
            }
        } finally {
            // 必须回收节点，避免内存泄漏
            child.recycle()
        }
    }

    return result
}

private fun countNodes(nodes: List<ScreenNodeInfo>): Int {
    var count = nodes.size
    for (node in nodes) {
        count += countNodes(node.children)
    }
    return count
}
