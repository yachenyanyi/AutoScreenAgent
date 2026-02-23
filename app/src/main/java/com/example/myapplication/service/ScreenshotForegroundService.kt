package com.example.myapplication.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.myapplication.MainActivity
import com.example.myapplication.R

/**
 * 截屏前台服务
 *
 * Android 10+ 要求 MediaProjection 必须在前台服务中运行
 * Android 14 强制要求：必须先让用户授权，然后才能启动 mediaProjection 类型的前台服务
 *
 * 正确的调用顺序：
 * 1. Activity 请求授权
 * 2. 用户点击同意
 * 3. Activity 将 resultCode 和 data 暂存到 companion object
 * 4. Activity 启动 Service（不带 data）
 * 5. Service 调用 startForeground(..., FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
 * 6. Service 从 companion object 获取数据并调用 getMediaProjection
 * 7. Service 通过广播通知 Activity 授权成功
 */
class ScreenshotForegroundService : Service() {

    companion object {
        private const val TAG = "ScreenshotForegroundService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "screenshot_service_channel"

        // 暂存授权数据（在 Activity 和 Service 之间传递）
        // 使用特殊的初始值表示"未设置"
        private var pendingResultCode: Int = 0  // 0 表示未设置，-1 才是 RESULT_OK
        private var pendingData: Intent? = null
        private var hasPendingData: Boolean = false  // 标记是否有待处理的数据

        @Volatile
        private var instance: ScreenshotForegroundService? = null

        fun getInstance(): ScreenshotForegroundService? = instance

        fun isRunning(): Boolean = instance != null

        /**
         * 启动服务并获取 MediaProjection（在用户授权后调用）
         */
        fun startWithAuthorization(context: Context, resultCode: Int, data: Intent) {
            // 先暂存数据
            pendingResultCode = resultCode
            pendingData = data
            hasPendingData = true
            Log.d(TAG, "Stored pending data: resultCode=$resultCode, data=$data, hasPendingData=$hasPendingData")

            val intent = Intent(context, ScreenshotForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenshotForegroundService::class.java)
            context.stopService(intent)
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called")
        Log.d(TAG, "hasPendingData=$hasPendingData, pendingResultCode=$pendingResultCode, pendingData=$pendingData")

        // 1. 先启动前台服务（带类型参数）- 必须在 5 秒内调用
        Log.d(TAG, "Before startForeground: hasPendingData=$hasPendingData")
        startForegroundWithMinimalNotification()
        Log.d(TAG, "After startForeground: hasPendingData=$hasPendingData")

        // 2. 检查数据
        if (!hasPendingData || pendingData == null) {
            Log.e(TAG, "No pending authorization data, stopping service")
            Log.e(TAG, "DEBUG: hasPendingData=$hasPendingData, pendingData=$pendingData")
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = pendingResultCode
        val data = pendingData
        Log.d(TAG, "Got data, clearing static vars")
        // 清除暂存数据
        hasPendingData = false
        pendingResultCode = 0
        pendingData = null

        // 3. 然后在主线程中获取 MediaProjection
        handler.post {
            acquireMediaProjection(resultCode, data!!)
        }

        return START_STICKY
    }

    /**
     * 启动前台服务，使用最小化通知（避免触发类加载）
     */
    private fun startForegroundWithMinimalNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("截屏服务")
            .setContentText("正在启动...")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
            Log.d(TAG, "startForeground called with FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION")
        } else {
            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "startForeground called (pre-Q)")
        }
    }

    /**
     * 获取 MediaProjection 实例
     */
    private fun acquireMediaProjection(resultCode: Int, data: Intent) {
        try {
            val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            if (mpManager == null) {
                Log.e(TAG, "MediaProjectionManager is null")
                sendAuthorizationResult(false)
                return
            }

            Log.d(TAG, "Calling getMediaProjection with resultCode=$resultCode")
            val projection = mpManager.getMediaProjection(resultCode, data)

            if (projection != null) {
                Log.d(TAG, "MediaProjection acquired successfully!")
                // 将 projection 传递给 ScreenshotManager
                com.example.myapplication.accessibility.ScreenshotManager.setMediaProjectionFromService(projection)
                sendAuthorizationResult(true)
            } else {
                Log.e(TAG, "getMediaProjection returned null")
                sendAuthorizationResult(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire MediaProjection: ${e.message}", e)
            sendAuthorizationResult(false)
        }
    }

    /**
     * 发送授权结果广播
     */
    private fun sendAuthorizationResult(success: Boolean) {
        val broadcastIntent = Intent("com.example.myapplication.MEDIA_PROJECTION_AUTH_RESULT").apply {
            putExtra("success", success)
            setPackage(packageName)
        }
        sendBroadcast(broadcastIntent)
        Log.d(TAG, "Authorization result broadcast: success=$success")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "截屏服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "用于 MediaProjection 截屏"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("截屏服务运行中")
            .setContentText("MediaProjection 服务正在运行")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
