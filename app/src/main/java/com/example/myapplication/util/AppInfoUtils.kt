package com.example.autoscreenagent.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log

/**
 * 应用信息工具类
 */
object AppInfoUtils {

    private const val TAG = "AppInfoUtils"

    data class AppInfo(
        val packageName: String,
        val appName: String,
        val isSystemApp: Boolean
    )

    /**
     * 获取所有已安装的应用
     * @param systemApps 是否包含系统应用
     * @param userApps 是否包含用户安装的应用
     */
    fun getAllApps(context: Context, systemApps: Boolean = true, userApps: Boolean = true): List<AppInfo> {
        val packageManager = context.packageManager
        val apps = mutableListOf<AppInfo>()

        val allApps = try {
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            Log.e(TAG, "获取应用列表失败", e)
            emptyList()
        }

        for (appInfo in allApps) {
            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            when {
                isSystemApp && systemApps -> {
                    apps.add(
                        AppInfo(
                            packageName = appInfo.packageName,
                            appName = appInfo.loadLabel(packageManager).toString(),
                            isSystemApp = isSystemApp
                        )
                    )
                }
                !isSystemApp && userApps -> {
                    apps.add(
                        AppInfo(
                            packageName = appInfo.packageName,
                            appName = appInfo.loadLabel(packageManager).toString(),
                            isSystemApp = isSystemApp
                        )
                    )
                }
            }
        }

        // 按应用名称排序
        return apps.sortedBy { it.appName.lowercase() }
    }

    /**
     * 获取系统应用包名列表
     */
    fun getSystemAppPackages(context: Context): List<String> {
        return getAllApps(context, systemApps = true, userApps = false)
            .map { it.packageName }
    }

    /**
     * 获取用户安装的应用包名列表
     */
    fun getUserAppPackages(context: Context): List<String> {
        return getAllApps(context, systemApps = false, userApps = true)
            .map { it.packageName }
    }

    /**
     * 判断是否是系统应用
     */
    fun isSystemApp(context: Context, packageName: String): Boolean {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取应用名称
     */
    fun getAppName(context: Context, packageName: String): String {
        return try {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}
