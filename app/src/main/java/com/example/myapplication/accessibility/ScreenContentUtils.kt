package com.example.myapplication.accessibility

import android.graphics.Rect
import org.json.JSONArray
import org.json.JSONObject

/**
 * 屏幕内容工具类
 *
 * 用于将屏幕节点树转换为 JSON 格式，方便传给 AI 分析
 */
object ScreenContentUtils {

    /**
     * 将屏幕信息转换为精简 JSON（适合传给 AI）
     * 只包含关键信息：text、className、viewId、bounds、isClickable
     */
    fun toJson(screenInfo: ScreenInfo, maxDepth: Int = 10): JSONObject {
        return JSONObject().apply {
            put("packageName", screenInfo.packageName)
            put("nodeCount", screenInfo.nodeCount)
            put("timestamp", screenInfo.timestamp)
            put("nodes", nodesToJson(screenInfo.rootNodes, 0, maxDepth))
        }
    }

    /**
     * 转换为格式化字符串（用于日志）
     */
    fun toFormattedString(screenInfo: ScreenInfo, maxDepth: Int = 10): String {
        val sb = StringBuilder()
        sb.appendLine("=== 屏幕内容 ===")
        sb.appendLine("包名：${screenInfo.packageName}")
        sb.appendLine("节点数：${screenInfo.nodeCount}")
        sb.appendLine("-------------------")
        for (node in screenInfo.rootNodes) {
            appendNodeToString(sb, node, 0, maxDepth)
        }
        return sb.toString()
    }

    private fun nodesToJson(
        nodes: List<ScreenNodeInfo>,
        depth: Int,
        maxDepth: Int
    ): JSONArray {
        val jsonArray = JSONArray()
        if (depth > maxDepth) return jsonArray

        for (node in nodes) {
            jsonArray.put(nodeToJson(node, depth, maxDepth))
        }
        return jsonArray
    }

    private fun nodeToJson(
        node: ScreenNodeInfo,
        depth: Int,
        maxDepth: Int
    ): JSONObject {
        return JSONObject().apply {
            put("text", node.text)
            put("className", node.className?.substringAfterLast('.')) // 只保留类名
            put("viewId", node.viewId?.substringAfterLast('/')) // 只保留 ID 名
            put("contentDesc", node.contentDesc)
            put("bounds", rectToJson(node.bounds))
            put("clickable", node.isClickable)
            put("enabled", node.isEnabled)
            put("childCount", node.childCount)
            if (depth < maxDepth && node.children.isNotEmpty()) {
                put("children", nodesToJson(node.children, depth + 1, maxDepth))
            }
        }
    }

    private fun rectToJson(rect: Rect): JSONObject {
        return JSONObject().apply {
            put("left", rect.left)
            put("top", rect.top)
            put("right", rect.right)
            put("bottom", rect.bottom)
            put("centerX", rect.centerX())
            put("centerY", rect.centerY())
        }
    }

    private fun appendNodeToString(
        sb: StringBuilder,
        node: ScreenNodeInfo,
        depth: Int,
        maxDepth: Int
    ) {
        if (depth > maxDepth) return

        val indent = "  ".repeat(depth)
        val clickable = if (node.isClickable) "[可点击]" else ""
        val textInfo = node.text?.takeIf { it.isNotEmpty() }
            ?: node.contentDesc?.takeIf { it.isNotEmpty() }
            ?: node.className?.substringAfterLast('.') ?: "节点"

        sb.appendLine("${indent}${textInfo} ${clickable}")
        sb.appendLine("${indent}  边界：${node.bounds}")
        sb.appendLine("${indent}  ID: ${node.viewId ?: "无"}")

        for (child in node.children) {
            appendNodeToString(sb, child, depth + 1, maxDepth)
        }
    }

    /**
     * 查找可点击的节点列表
     */
    fun findClickableNodes(screenInfo: ScreenInfo): List<ScreenNodeInfo> {
        val result = mutableListOf<ScreenNodeInfo>()
        for (node in screenInfo.rootNodes) {
            findClickableNodesRecursive(node, result)
        }
        return result
    }

    private fun findClickableNodesRecursive(
        node: ScreenNodeInfo,
        result: MutableList<ScreenNodeInfo>
    ) {
        if (node.isClickable && node.isEnabled) {
            result.add(node)
        }
        for (child in node.children) {
            findClickableNodesRecursive(child, result)
        }
    }

    /**
     * 根据文本查找节点
     */
    fun findNodeByText(screenInfo: ScreenInfo, targetText: String): ScreenNodeInfo? {
        for (node in screenInfo.rootNodes) {
            val found = findNodeByTextRecursive(node, targetText)
            if (found != null) return found
        }
        return null
    }

    private fun findNodeByTextRecursive(
        node: ScreenNodeInfo,
        targetText: String
    ): ScreenNodeInfo? {
        if (node.text.equals(targetText, ignoreCase = true) ||
            node.contentDesc.equals(targetText, ignoreCase = true)
        ) {
            return node
        }
        for (child in node.children) {
            val found = findNodeByTextRecursive(child, targetText)
            if (found != null) return found
        }
        return null
    }
}
