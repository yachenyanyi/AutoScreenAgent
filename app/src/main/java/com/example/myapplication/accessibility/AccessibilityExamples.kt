package com.example.myapplication.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log

/**
 * 无障碍服务使用示例
 *
 * 这些示例展示了如何使用 ActionExecutor 执行各种操作
 */
class AccessibilityExamples {

    companion object {
        private const val TAG = "AccessibilityExamples"

        /**
         * 示例 1: 打开微信并发送消息的完整流程
         */
        fun exampleWechatSendExample() {
            val executor = AccessibilityManager.createExecutor() ?: return

            // 1. 启动微信
            executor.execute(Action(
                actionId = "1",
                action = ActionType.LAUNCH.value,
                params = mapOf("app" to "com.tencent.mm"),
                description = "打开微信"
            ))

            // 2. 点击搜索框 (按文本)
            executor.execute(Action(
                actionId = "2",
                action = ActionType.TAP.value,
                params = mapOf("text" to "搜索"),
                description = "点击搜索"
            ))

            // 3. 输入联系人名字
            executor.execute(Action(
                actionId = "3",
                action = ActionType.TYPE.value,
                params = mapOf("text" to "张三"),
                description = "输入张三"
            ))

            // 4. 点击联系人
            executor.execute(Action(
                actionId = "4",
                action = ActionType.TAP.value,
                params = mapOf("text" to "张三"),
                description = "点击联系人"
            ))

            // 5. 点击输入框
            executor.execute(Action(
                actionId = "5",
                action = ActionType.TAP.value,
                params = mapOf("id" to "com.tencent.mm:id/b5z"),
                description = "点击输入框"
            ))

            // 6. 输入消息
            executor.execute(Action(
                actionId = "6",
                action = ActionType.TYPE.value,
                params = mapOf("text" to "晚上一起吃饭吗？"),
                description = "输入消息"
            ))

            // 7. 点击发送
            executor.execute(Action(
                actionId = "7",
                action = ActionType.TAP.value,
                params = mapOf("text" to "发送"),
                description = "点击发送"
            ))
        }

        /**
         * 示例 2: 滑动操作
         */
        fun exampleSwipe() {
            val executor = AccessibilityManager.createExecutor() ?: return

            // 使用封装好的方向滑动
            executor.swipeUp()
            Thread.sleep(500)

            executor.swipeDown()
            Thread.sleep(500)

            executor.swipeLeft()
            Thread.sleep(500)

            executor.swipeRight()
        }

        /**
         * 示例 3: 自定义坐标滑动
         */
        fun exampleCustomSwipe() {
            val executor = AccessibilityManager.createExecutor() ?: return

            // 从屏幕底部滑到顶部
            executor.swipe(
                startX = 500f,
                startY = 1500f,
                endX = 500f,
                endY = 500f,
                duration = 300
            )
        }

        /**
         * 示例 4: 检查执行结果
         */
        fun exampleWithResultCheck() {
            val executor = AccessibilityManager.createExecutor() ?: return

            val result = executor.execute(Action(
                actionId = "1",
                action = ActionType.TAP.value,
                params = mapOf("text" to "确定"),
                description = "点击确定"
            ))

            when (result) {
                is ActionResult.Success -> {
                    Log.d(TAG, "操作成功")
                }
                is ActionResult.Failure -> {
                    Log.e(TAG, "操作失败：${result.reason}")
                }
                is ActionResult.NotFound -> {
                    Log.e(TAG, "未找到元素：${result.targetType}='${result.targetValue}'")
                }
            }
        }

        /**
         * 示例 5: 使用便捷方法
         */
        fun exampleConvenienceMethods(context: Context) {
            // 检查服务是否启用 (需要在 Activity 中调用)
            // val isEnabled = AccessibilityManager.isEnabled(context)

            // 使用便捷方法
            AccessibilityManager.back()
            AccessibilityManager.home()
            AccessibilityManager.launchAppDirect(context, "com.tencent.mm")
            AccessibilityManager.tapByText("确定")
            AccessibilityManager.typeText("Hello World")
        }

        /**
         * 示例 6: 组合操作 - 打开设置
         */
        fun exampleOpenSettings() {
            val executor = AccessibilityManager.createExecutor() ?: return

            // 启动设置应用
            executor.execute(Action(
                actionId = "1",
                action = ActionType.LAUNCH.value,
                params = mapOf("app" to "com.android.settings"),
                description = "打开设置"
            ))

            // 向下滑动查找
            Thread.sleep(1000)
            executor.swipeDown()
        }
    }
}
