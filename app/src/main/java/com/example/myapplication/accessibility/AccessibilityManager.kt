package com.example.autoscreenagent.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * 无障碍服务管理器
 *
 * 提供便捷的静态方法来检查和使用无障碍服务
 */
object AccessibilityManager {

    private const val TAG = "AccessibilityManager"

    /**
     * 检查无障碍服务是否已启用
     */
    fun isEnabled(context: Context): Boolean {
        val serviceName = context.packageName + "/.accessibility.MyAccessibilityService"
        val fullServiceName = context.packageName + "/" + context.packageName + ".accessibility.MyAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""

        val isDisabled = enabledServices.isEmpty() || enabledServices == "(null)"
        if (isDisabled) {
            Log.d(TAG, "无障碍服务未启用：enabledServices is empty")
            return false
        }

        Log.d(TAG, "检查服务：$serviceName")
        Log.d(TAG, "完整服务名：$fullServiceName")
        Log.d(TAG, "已启用的服务：$enabledServices")

        // 检查是否包含我们的服务（两种格式都检查）
        val isEnabled = enabledServices.contains(serviceName, ignoreCase = true)
            || enabledServices.contains(fullServiceName, ignoreCase = true)
            || enabledServices.contains("MyAccessibilityService", ignoreCase = true)
        Log.d(TAG, "服务是否启用：$isEnabled")
        return isEnabled
    }

    /**
     * 跳转到无障碍设置页面
     */
    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开设置失败", e)
        }
    }

    /**
     * 获取服务实例
     */
    fun getService(): MyAccessibilityService? {
        return MyAccessibilityService.getInstance()
    }

    /**
     * 获取悬浮窗管理器
     */
    fun getFloatingWindowManager(): com.example.autoscreenagent.accessibility.FloatingWindowManager? {
        return getService()?.getFloatingWindowManager()
    }

    /**
     * 创建执行器
     */
    fun createExecutor(): ActionExecutor? {
        return try {
            ActionExecutor()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "创建执行器失败：服务未连接", e)
            null
        }
    }

    /**
     * 执行动作
     */
    fun execute(action: Action): ActionResult? {
        val executor = createExecutor() ?: return null
        return executor.execute(action)
    }

    /**
     * 便捷方法 - 点击文本
     */
    fun tapByText(text: String): ActionResult {
        val executor = createExecutor() ?: return ActionResult.Failure("服务未连接")
        return executor.tapByText(text)
    }

    /**
     * 便捷方法 - 点击 ID
     */
    fun tapById(viewId: String): ActionResult {
        val executor = createExecutor() ?: return ActionResult.Failure("服务未连接")
        return executor.tapById(viewId)
    }

    /**
     * 便捷方法 - 输入文字
     */
    fun typeText(text: String): ActionResult {
        val executor = createExecutor() ?: return ActionResult.Failure("服务未连接")
        return executor.typeText(text)
    }

    /**
     * 便捷方法 - 返回
     */
    fun back(): ActionResult {
        val executor = createExecutor() ?: return ActionResult.Failure("服务未连接")
        return executor.executeBack(Action("back", "back", emptyMap()))
    }

    /**
     * 便捷方法 - Home
     */
    fun home(): ActionResult {
        val executor = createExecutor() ?: return ActionResult.Failure("服务未连接")
        return executor.executeHome(Action("home", "home", emptyMap()))
    }

    /**
     * 便捷方法 - 启动应用
     */
    fun launchApp(context: Context, packageName: String): ActionResult {
        // 先尝试使用 Service 启动（如果有）
        val executor = createExecutor()
        if (executor != null) {
            return executor.execute(Action("launch", "launch", mapOf("app" to packageName)))
        }

        // Service 未连接，直接使用 Intent 启动
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return ActionResult.Failure("应用未安装：$packageName")

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "启动应用：$packageName")
            ActionResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "启动应用失败", e)
            ActionResult.Failure("启动失败：${e.message}")
        }
    }

    /**
     * 便捷方法 - 启动应用 (不依赖 Service)
     */
    fun launchAppDirect(context: Context, packageName: String): ActionResult {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return ActionResult.Failure("应用未安装：$packageName")

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "启动应用：$packageName")
            ActionResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "启动应用失败", e)
            ActionResult.Failure("启动失败：${e.message}")
        }
    }

    /**
     * 获取当前屏幕内容（节点树）
     */
    fun getScreenContent(): ScreenInfo? {
        val service = getService() ?: return null
        return service.getScreenContent()
    }

    /**
     * 获取当前应用包名
     */
    fun getCurrentApp(): String? {
        return getService()?.getCurrentApp()
    }
}
