package com.example.autoscreenagent.util

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shell 命令执行器
 *
 * 用于执行需要 root 权限的命令，如 ADB 操作
 */
object ShellCommandExecutor {

    private const val TAG = "ShellCommandExecutor"

    /**
     * 执行 Shell 命令
     *
     * @param command 要执行的命令
     * @return 命令输出结果
     */
    fun execute(command: String): ShellResult {
        return try {
            Log.d(TAG, "执行命令：$command")

            // 尝试使用 su 执行（需要 root）
            val process = Runtime.getRuntime().exec("su")
            val outputStream = process.outputStream
            val inputStream = process.inputStream
            val errorStream = process.errorStream

            // 写入命令
            outputStream.write("$command\n".toByteArray())
            outputStream.write("exit\n".toByteArray())
            outputStream.flush()

            // 读取输出
            val output = readStream(inputStream.bufferedReader())
            val error = readStream(errorStream.bufferedReader())

            // 等待进程结束
            val exitCode = process.waitFor()

            Log.d(TAG, "命令执行完成，退出码：$exitCode")
            Log.d(TAG, "输出：$output")
            if (error.isNotEmpty()) {
                Log.e(TAG, "错误：$error")
            }

            ShellResult(
                success = exitCode == 0 && error.isEmpty(),
                output = output,
                error = error,
                exitCode = exitCode
            )
        } catch (e: Exception) {
            Log.e(TAG, "执行命令失败：${e.message}", e)
            ShellResult(
                success = false,
                output = "",
                error = e.message ?: "未知错误",
                exitCode = -1
            )
        }
    }

    /**
     * 执行 ADB 命令（通过 am 服务）
     */
    fun executeAmCommand(command: String): ShellResult {
        return execute("am $command")
    }

    /**
     * 启动应用
     */
    fun launchApp(packageName: String): ShellResult {
        return executeAmCommand("start -n $packageName/.LauncherActivity")
    }

    /**
     * 点击坐标
     */
    fun tap(x: Int, y: Int): ShellResult {
        return execute("input tap $x $y")
    }

    /**
     * 滑动
     */
    fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, duration: Int = 300): ShellResult {
        return execute("input swipe $startX $startY $endX $endY $duration")
    }

    /**
     * 输入文本
     */
    fun typeText(text: String): ShellResult {
        // 转义特殊字符
        val escapedText = text.replace("\"", "\\\"").replace(" ", "%s")
        return execute("input text \"$escapedText\"")
    }

    /**
     * 按返回键
     */
    fun pressBack(): ShellResult {
        return execute("input keyevent KEYCODE_BACK")
    }

    /**
     * 按 Home 键
     */
    fun pressHome(): ShellResult {
        return execute("input keyevent KEYCODE_HOME")
    }

    /**
     * 获取当前活动界面
     */
    fun getCurrentActivity(): ShellResult {
        return execute("dumpsys activity activities | grep mResumedActivity")
    }

    /**
     * 获取已安装应用列表
     */
    fun getInstalledPackages(): ShellResult {
        return execute("pm list packages")
    }

    /**
     * 截屏
     */
    fun screenshot(outputPath: String = "/sdcard/screenshot.png"): ShellResult {
        return execute("screencap -p $outputPath")
    }

    /**
     * 读取文件内容
     */
    fun readFile(path: String): ShellResult {
        return execute("cat $path")
    }

    private fun readStream(stream: BufferedReader): String {
        val output = StringBuilder()
        stream.use { reader ->
            reader.lineSequence().forEach { line ->
                output.appendLine(line)
            }
        }
        return output.toString().trim()
    }

    /**
     * Shell 执行结果
     */
    data class ShellResult(
        val success: Boolean,
        val output: String,
        val error: String,
        val exitCode: Int
    )
}
