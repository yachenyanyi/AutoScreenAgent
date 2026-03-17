package com.example.autoscreenagent.util

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.autoscreenagent.MainActivity
import com.example.autoscreenagent.R

/**
 * 任务完成通知工具类
 *
 * 用于在 Agent 任务完成时发送通知
 */
object NotificationHelper {

    private const val TAG = "NotificationHelper"
    private const val CHANNEL_ID = "task_completion_channel"
    private const val NOTIFICATION_ID = 2001

    /**
     * 创建通知渠道
     * 应在 Application 或 MainActivity 的 onCreate 中调用
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "任务完成通知",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Agent 任务完成时发送通知"
                enableLights(true)
                enableVibration(true)
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "通知渠道已创建")
        }
    }

    /**
     * 显示任务完成通知
     *
     * @param context 上下文
     * @param message 任务完成消息
     */
    fun showTaskCompletedNotification(context: Context, message: String) {
        // 检查通知权限
        if (!hasNotificationPermission(context)) {
            Log.w(TAG, "没有通知权限，跳过通知")
            return
        }

        // 创建点击通知后的 Intent
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 构建通知
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("任务完成")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // 发送通知
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "任务完成通知已发送: $message")
        } catch (e: SecurityException) {
            Log.e(TAG, "发送通知失败: ${e.message}")
        }
    }

    /**
     * 检查是否有通知权限
     * Android 13+ 需要动态申请 POST_NOTIFICATIONS 权限
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * 检查应用是否在前台
     */
    fun isAppInForeground(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false

        val packageName = context.packageName
        for (appProcess in appProcesses) {
            if (appProcess.processName == packageName) {
                return appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            }
        }
        return false
    }

    /**
     * 检查应用是否在后台
     */
    fun isAppInBackground(context: Context): Boolean {
        return !isAppInForeground(context)
    }
}