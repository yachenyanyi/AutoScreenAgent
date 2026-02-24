package com.example.autoscreenagent.accessibility

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.WindowManager
import com.example.autoscreenagent.service.ScreenshotForegroundService

/**
 * 截屏管理器
 *
 * 使用 MediaProjection API 获取屏幕截图
 * 需要用户首次授权（系统弹窗）
 *
 * Android 10+ 要求 MediaProjection 必须在前台服务中运行
 * Android 14 强制要求：必须先让用户授权，然后才能启动 mediaProjection 类型的前台服务
 */
class ScreenshotManager {

    companion object {
        private const val TAG = "ScreenshotManager"

        @Volatile
        private var instance: ScreenshotManager? = null

        fun getInstance(): ScreenshotManager = instance ?: synchronized(this) {
            instance ?: ScreenshotManager().also { instance = it }
        }

        /**
         * 获取请求授权用的 Intent
         */
        fun getMediaProjectionIntent(context: Context): Intent {
            val mediaProjectionManager = context.getSystemService(
                Context.MEDIA_PROJECTION_SERVICE
            ) as android.media.projection.MediaProjectionManager
            return mediaProjectionManager.createScreenCaptureIntent()
        }

        /**
         * 从 Service 设置 MediaProjection（内部使用）
         */
        fun setMediaProjectionFromService(projection: MediaProjection) {
            instance?.mediaProjection = projection
            Log.d(TAG, "MediaProjection set from service: $projection")
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var contextRef: Context? = null

    /**
     * 设置 Context（在应用启动时调用）
     */
    fun setContext(context: Context) {
        contextRef = context.applicationContext
    }

    /**
     * 是否已授权
     */
    fun isAuthorized(): Boolean = mediaProjection != null

    /**
     * 释放授权
     */
    fun stop() {
        mediaProjection?.stop()
        mediaProjection = null
        cleanup()
        Log.d(TAG, "MediaProjection 已停止")
    }

    /**
     * 截取屏幕并返回 Bitmap
     */
    fun captureScreenshot(context: Context): Bitmap? {
        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection 未授权")
            return null
        }

        Log.d(TAG, "开始截屏...")

        return try {
            // 获取屏幕尺寸
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = wm.defaultDisplay
            val metrics = android.util.DisplayMetrics()
            display.getRealMetrics(metrics)
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            Log.d(TAG, "屏幕尺寸：${width}x${height}, density: $density")

            // 创建 ImageReader
            imageReader = ImageReader.newInstance(
                width, height,
                PixelFormat.RGBA_8888, 2
            )
            Log.d(TAG, "ImageReader 创建成功")

            // 创建虚拟显示
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "screenshot",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null
            )
            Log.d(TAG, "VirtualDisplay 创建成功")

            // 等待一帧
            Log.d(TAG, "等待图像帧...")
            Thread.sleep(500)

            // 获取图像
            val image = imageReader?.acquireLatestImage()
            Log.d(TAG, "acquireLatestImage 返回：${image != null}")

            image?.let {
                Log.d(TAG, "开始转换 Bitmap...")
                val bitmap = imageToBitmap(it)
                Log.d(TAG, "Bitmap 创建成功：${bitmap.width}x${bitmap.height}")
                it.close()
                return bitmap
            } ?: run {
                Log.e(TAG, "未能获取图像帧")
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "截屏失败：${e.message}", e)
            null
        } finally {
            Log.d(TAG, "开始清理资源")
            cleanup()
        }
    }

    /**
     * 截取屏幕并返回 Base64
     */
    fun captureToBase64(context: Context, quality: Int = 90): String? {
        val bitmap = captureScreenshot(context) ?: return null

        return try {
            val byteArrayOutputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, quality, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()
            Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * 将 Image 转换为 Bitmap
     */
    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        // 裁剪掉填充部分
        return Bitmap.createBitmap(
            bitmap, 0, 0, image.width, image.height
        )
    }

    /**
     * 清理资源
     */
    private fun cleanup() {
        virtualDisplay?.release()
        imageReader?.close()
        virtualDisplay = null
        imageReader = null
    }
}
