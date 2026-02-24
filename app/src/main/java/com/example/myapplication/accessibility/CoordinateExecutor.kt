package com.example.autoscreenagent.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewConfiguration
import androidx.annotation.RequiresApi

/**
 * 坐标执行器
 *
 * 基于坐标执行点击、滑动、长按等操作
 * 使用 AccessibilityService 的 dispatchGesture API
 */
class CoordinateExecutor(private val service: AccessibilityService) {

    companion object {
        private const val TAG = "CoordinateExecutor"
        private val handler = Handler(Looper.getMainLooper())
    }

    /**
     * 点击坐标
     */
    fun tap(x: Int, y: Int, callback: ((Boolean) -> Unit)? = null) {
        Log.d(TAG, "tap: ($x, $y)")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val gestureBuilder = GestureDescription.Builder()
            val path = Path()
            path.moveTo(x.toFloat(), y.toFloat())

            gestureBuilder.addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0,
                    ViewConfiguration.getTapTimeout().toLong()
                )
            )

            service.dispatchGesture(gestureBuilder.build(), object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "tap completed: ($x, $y)")
                    callback?.invoke(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.e(TAG, "tap cancelled: ($x, $y)")
                    callback?.invoke(false)
                }
            }, null)
        } else {
            Log.e(TAG, "tap not supported on this Android version")
            callback?.invoke(false)
        }
    }

    /**
     * 长按坐标
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun longPress(x: Int, y: Int, duration: Int = 500, callback: ((Boolean) -> Unit)? = null) {
        Log.d(TAG, "longPress: ($x, $y) duration=$duration")

        val gestureBuilder = GestureDescription.Builder()
        val path = Path()
        path.moveTo(x.toFloat(), y.toFloat())

        gestureBuilder.addStroke(
            GestureDescription.StrokeDescription(
                path,
                0,
                duration.toLong()
            )
        )

        service.dispatchGesture(gestureBuilder.build(), object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "longPress completed: ($x, $y)")
                callback?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.e(TAG, "longPress cancelled: ($x, $y)")
                callback?.invoke(false)
            }
        }, null)
    }

    /**
     * 滑动
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, duration: Int = 300, callback: ((Boolean) -> Unit)? = null) {
        Log.d(TAG, "swipe: ($startX, $startY) -> ($endX, $endY) duration=$duration")

        val gestureBuilder = GestureDescription.Builder()
        val path = Path()
        path.moveTo(startX.toFloat(), startY.toFloat())
        path.lineTo(endX.toFloat(), endY.toFloat())

        gestureBuilder.addStroke(
            GestureDescription.StrokeDescription(
                path,
                0,
                duration.toLong()
            )
        )

        service.dispatchGesture(gestureBuilder.build(), object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "swipe completed")
                callback?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.e(TAG, "swipe cancelled")
                callback?.invoke(false)
            }
        }, null)
    }

    /**
     * 向上滑动
     */
    fun swipeUp(duration: Int = 300, callback: ((Boolean) -> Unit)? = null) {
        val rect = getScreenSize()
        swipe(
            startX = rect.width() / 2,
            startY = (rect.height() * 0.75).toInt(),
            endX = rect.width() / 2,
            endY = (rect.height() * 0.25).toInt(),
            duration = duration,
            callback = callback
        )
    }

    /**
     * 向下滑动
     */
    fun swipeDown(duration: Int = 300, callback: ((Boolean) -> Unit)? = null) {
        val rect = getScreenSize()
        swipe(
            startX = rect.width() / 2,
            startY = (rect.height() * 0.25).toInt(),
            endX = rect.width() / 2,
            endY = (rect.height() * 0.75).toInt(),
            duration = duration,
            callback = callback
        )
    }

    /**
     * 向左滑动
     */
    fun swipeLeft(duration: Int = 300, callback: ((Boolean) -> Unit)? = null) {
        val rect = getScreenSize()
        swipe(
            startX = (rect.width() * 0.75).toInt(),
            startY = rect.height() / 2,
            endX = (rect.width() * 0.25).toInt(),
            endY = rect.height() / 2,
            duration = duration,
            callback = callback
        )
    }

    /**
     * 向右滑动
     */
    fun swipeRight(duration: Int = 300, callback: ((Boolean) -> Unit)? = null) {
        val rect = getScreenSize()
        swipe(
            startX = (rect.width() * 0.25).toInt(),
            startY = rect.height() / 2,
            endX = (rect.width() * 0.75).toInt(),
            endY = rect.height() / 2,
            duration = duration,
            callback = callback
        )
    }

    /**
     * 获取屏幕尺寸
     */
    fun getScreenSize(): Rect {
        val rootNode = service.rootInActiveWindow
        return if (rootNode != null) {
            val rect = Rect()
            rootNode.getBoundsInScreen(rect)
            rect
        } else {
            // 默认尺寸
            Rect(0, 0, 1080, 2400)
        }
    }

    /**
     * 延迟执行
     */
    fun waitMillis(millis: Long, block: () -> Unit) {
        handler.postDelayed(block, millis)
    }

    /**
     * 主线程执行
     */
    fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            handler.post(block)
        }
    }
}
