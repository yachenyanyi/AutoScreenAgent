package com.example.autoscreenagent.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.annotation.RequiresApi

/**
 * 悬浮窗管理器
 *
 * 提供在所有应用上层的悬浮窗，用于与 AI 助手交互
 */
class FloatingWindowManager(
    private val context: Context,
    private val accessibilityService: AccessibilityService
) {
    companion object {
        private const val TAG = "FloatingWindowManager"
    }

    private var floatingView: View? = null
    private var windowManager: WindowManager? = null
    private var isShowing = false

    /**
     * 显示悬浮窗
     */
    fun show() {
        if (isShowing) {
            Log.d(TAG, "悬浮窗已显示，跳过")
            return
        }

        try {
            // 创建悬浮窗视图
            floatingView = createFloatingView()

            // 设置布局参数
            val params = createLayoutParams()

            // 添加到窗口
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager?.addView(floatingView, params)

            isShowing = true
            Log.d(TAG, "悬浮窗已显示")
        } catch (e: Exception) {
            Log.e(TAG, "显示悬浮窗失败", e)
        }
    }

    /**
     * 隐藏悬浮窗
     */
    fun hide() {
        if (!isShowing) {
            return
        }

        try {
            floatingView?.let { view ->
                windowManager?.removeView(view)
            }
            floatingView = null
            windowManager = null
            isShowing = false
            Log.d(TAG, "悬浮窗已隐藏")
        } catch (e: Exception) {
            Log.e(TAG, "隐藏悬浮窗失败", e)
        }
    }

    /**
     * 切换悬浮窗显示状态
     */
    fun toggle() {
        if (isShowing) {
            hide()
        } else {
            show()
        }
    }

    /**
     * 是否正在显示
     */
    fun isShowing(): Boolean = isShowing

    /**
     * 创建悬浮窗视图
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createFloatingView(): View {
        // 创建根布局
        val rootLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 8, 16, 8)
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            clipChildren = false
        }

        // 创建输入框
        val editText = EditText(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(8, 0, 8, 0)
            }
            hint = "输入任务..."
            maxLines = 1
            imeOptions = EditorInfo.IME_ACTION_SEND
            setBackgroundResource(android.R.drawable.edit_text)
            setPadding(12, 8, 12, 8)
        }

        // 创建发送按钮
        val sendButton = ImageButton(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setImageResource(android.R.drawable.ic_menu_send)
            contentDescription = "发送"
            setBackgroundResource(android.R.drawable.btn_default)
            setPadding(8, 8, 8, 8)
        }

        // 创建停止按钮
        val stopButton = ImageButton(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(8, 0, 0, 0)
            }
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            contentDescription = "停止"
            setBackgroundResource(android.R.drawable.btn_default)
            setPadding(8, 8, 8, 8)
        }

        // 添加视图到布局
        rootLayout.addView(editText)
        rootLayout.addView(sendButton)
        rootLayout.addView(stopButton)

        // 设置发送按钮点击监听
        sendButton.setOnClickListener {
            val text = editText.text.toString().trim()
            if (text.isNotEmpty()) {
                // 发送广播通知 MainActivity 处理
                sendTaskIntent(text)
                editText.text?.clear()
                hideKeyboard(editText)
            }
        }

        // 设置输入框回车发送
        editText.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                val text = editText.text.toString().trim()
                if (text.isNotEmpty()) {
                    sendTaskIntent(text)
                    editText.text?.clear()
                    hideKeyboard(editText)
                }
                true
            } else {
                false
            }
        }

        // 设置停止按钮点击监听
        stopButton.setOnClickListener {
            sendStopIntent()
            hideKeyboard(editText)
        }

        // 设置拖动功能
        setupDraggableWindow(rootLayout, editText)

        return rootLayout
    }

    /**
     * 创建布局参数
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            // 使用无障碍悬浮窗类型，不需要权限
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // 不获取焦点，允许触摸事件传递到下层
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 200  // 初始位置距离顶部 200dp
            width = WindowManager.LayoutParams.MATCH_PARENT
            horizontalMargin = 0.1f  // 左右留白 10%
        }
    }

    /**
     * 设置窗口可拖动
     */
    private fun setupDraggableWindow(rootLayout: LinearLayout, editText: EditText) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        rootLayout.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    val params = floatingView?.layoutParams as? WindowManager.LayoutParams
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val params = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return@setOnTouchListener false
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 发送任务广播
     */
    private fun sendTaskIntent(text: String) {
        try {
            val intent = Intent("com.example.autoscreenagent.FLOATING_WINDOW_TASK").apply {
                putExtra("task_text", text)
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
            Log.d(TAG, "发送任务：$text")
        } catch (e: Exception) {
            Log.e(TAG, "发送任务广播失败", e)
        }
    }

    /**
     * 发送停止广播
     */
    private fun sendStopIntent() {
        try {
            val intent = Intent("com.example.autoscreenagent.FLOATING_WINDOW_STOP").apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
            Log.d(TAG, "发送停止命令")
        } catch (e: Exception) {
            Log.e(TAG, "发送停止广播失败", e)
        }
    }

    /**
     * 隐藏键盘
     */
    private fun hideKeyboard(view: View) {
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        } catch (e: Exception) {
            Log.e(TAG, "隐藏键盘失败", e)
        }
    }
}
