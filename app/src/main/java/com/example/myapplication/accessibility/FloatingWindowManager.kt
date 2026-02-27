package com.example.autoscreenagent.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner

// Material Design 3 颜色
private val PrimaryColor = Color(0xFF6650a4)
private val PrimaryContainerColor = Color(0xFFEADDFF)
private val OnPrimaryColor = Color.White
private val SurfaceColor = Color.White
private val OnSurfaceColor = Color(0xFF1C1B1F)
private val OutlineColor = Color(0xFF79747E)

/**
 * 悬浮窗管理器 - Compose 版本
 *
 * 提供在所有应用上层的悬浮窗，用于与 AI 助手交互
 * 样式：Material Design 3 风格
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
            // 创建悬浮窗视图 (使用 ComposeView)
            floatingView = createComposeView()

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
     * 创建 Compose 视图
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createComposeView(): androidx.compose.ui.platform.ComposeView {
        return androidx.compose.ui.platform.ComposeView(context).apply {
            // 设置 ViewCompositionStrategy
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnDetachedFromWindow)

            // 设置 LifecycleOwner - 关键！否则 Compose 不会渲染
            // AccessibilityService 实现了 LifecycleOwner
            setViewTreeLifecycleOwner(accessibilityService as LifecycleOwner)

            setContent {
                MaterialTheme {
                    FloatingWindowContent(
                        onSendTask = { text -> sendTaskIntent(text) },
                        onStop = { sendStopIntent() },
                        onDrag = { deltaX, deltaY -> updatePosition(deltaX, deltaY) }
                    )
                }
            }
        }
    }

    /**
     * 更新悬浮窗位置
     */
    private fun updatePosition(deltaX: Float, deltaY: Float) {
        try {
            val params = floatingView?.layoutParams as? WindowManager.LayoutParams
            if (params != null) {
                params.x += deltaX.toInt()
                params.y += deltaY.toInt()
                windowManager?.updateViewLayout(floatingView, params)
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新位置失败", e)
        }
    }

    /**
     * 创建布局参数
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 200
            width = WindowManager.LayoutParams.MATCH_PARENT
            horizontalMargin = 0.08f
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
    private fun hideKeyboard() {
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(floatingView?.windowToken, 0)
        } catch (e: Exception) {
            Log.e(TAG, "隐藏键盘失败", e)
        }
    }
}

/**
 * 悬浮窗内容 - Compose UI
 */
@Composable
private fun FloatingWindowContent(
    onSendTask: (String) -> Unit,
    onStop: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    // 拖动状态
    var dragOffsetX by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetX += dragAmount.x
                        dragOffsetY += dragAmount.y
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                )
            },
        shape = RoundedCornerShape(28.dp),
        color = SurfaceColor,
        shadowElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 拖动手柄 (三条横线)
            DragHandle()

            // 输入框
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "告诉 AI 你想做什么...",
                        color = OutlineColor
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryColor,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputText.isNotBlank()) {
                            onSendTask(inputText.trim())
                            inputText = ""
                            focusManager.clearFocus()
                        }
                    }
                )
            )

            // 发送按钮 (填充式)
            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        onSendTask(inputText.trim())
                        inputText = ""
                        focusManager.clearFocus()
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(PrimaryColor)
                    .semantics { contentDescription = "发送" }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    tint = OnPrimaryColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 停止按钮 (tonal 样式)
            IconButton(
                onClick = {
                    onStop()
                    focusManager.clearFocus()
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(PrimaryContainerColor)
                    .semantics { contentDescription = "停止" }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "停止",
                    tint = PrimaryColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 拖动手柄 (三条横线)
 */
@Composable
private fun DragHandle() {
    Column(
        modifier = Modifier
            .width(24.dp)
            .fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .height(2.dp)
                    .background(OutlineColor, RoundedCornerShape(1.dp))
            )
        }
    }
}