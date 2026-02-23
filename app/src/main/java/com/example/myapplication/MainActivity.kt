package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.myapplication.accessibility.*
import com.example.myapplication.accessibility.ScreenContentUtils
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.service.ScreenshotForegroundService
import com.example.myapplication.ai.AIResponseParser
import com.example.myapplication.ai.CommandExecutor
import com.example.myapplication.util.AppInfoUtils

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AccessibilityTestScreen()
            }
        }
    }
}

@Composable
fun AccessibilityTestScreen() {
    var logText by remember { mutableStateOf("日志输出：\n") }
    val context = LocalContext.current
    var isServiceEnabled by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var isFetching by remember { mutableStateOf(false) }
    var isScreenshotAuthorized by remember { mutableStateOf(false) }

    // 操作模式选择
    var operationMode by remember { mutableStateOf("无障碍模式") }
    val modes = listOf("无障碍模式", "截图坐标模式")

    // 检查服务状态
    isServiceEnabled = AccessibilityManager.isEnabled(context)

    // 初始化截屏管理器
    val screenshotManager = remember { ScreenshotManager.getInstance() }
    screenshotManager.setContext(context)

    // 初始化 AI 命令执行器
    val commandExecutor = remember { CommandExecutor(context) }

    // 监听授权结果广播
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.example.myapplication.MEDIA_PROJECTION_AUTH_RESULT") {
                    val success = intent.getBooleanExtra("success", false)
                    Log.d("MainActivity", "Received auth result: success=$success")
                    isScreenshotAuthorized = screenshotManager.isAuthorized()
                    if (success) {
                        Toast.makeText(context, "截屏授权成功！", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "截屏授权失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val filter = IntentFilter("com.example.myapplication.MEDIA_PROJECTION_AUTH_RESULT")
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // 更新授权状态
    LaunchedEffect(screenshotManager.isAuthorized()) {
        isScreenshotAuthorized = screenshotManager.isAuthorized()
    }

    // ActivityResultLauncher for MediaProjection
    val screenshotActivityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("MainActivity", "ActivityResult: resultCode=${result.resultCode}, data=${result.data}")

        if (result.resultCode == -1) { // RESULT_OK
            val data = result.data
            if (data != null) {
                Log.d("MainActivity", "User authorized, starting foreground service...")
                // 启动前台服务，在 Service 内部获取 MediaProjection
                ScreenshotForegroundService.startWithAuthorization(context, result.resultCode, data)
            } else {
                Log.e("MainActivity", "Intent data is null")
                Toast.makeText(context, "授权失败：Intent data 为 null", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.e("MainActivity", "Authorization cancelled, resultCode=${result.resultCode}")
            Toast.makeText(context, "授权被取消", Toast.LENGTH_SHORT).show()
        }
    }

    fun appendLog(msg: String) {
        logText = "${logText}${msg}\n"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // 服务状态
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isServiceEnabled)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isServiceEnabled) "服务已启用" else "服务未启用",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isServiceEnabled)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (!isServiceEnabled) {
                    Button(onClick = {
                        AccessibilityManager.openAccessibilitySettings(context)
                    }) {
                        Text("去开启")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 截屏服务状态
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isScreenshotAuthorized)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isScreenshotAuthorized) "截屏服务已授权" else "截屏服务未授权",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isScreenshotAuthorized)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 操作模式选择
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "操作模式",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    modes.forEach { mode ->
                        Button(
                            onClick = { operationMode = mode },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (operationMode == mode)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Text(mode)
                        }
                    }
                }
                Text(
                    text = when (operationMode) {
                        "无障碍模式" -> "使用无障碍 API 点击元素（精准，但可能被检测）"
                        "截图坐标模式" -> "使用坐标点击（更像真人，绕过检测）"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 测试按钮
        Text("测试操作", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    appendLog(">>> 测试 Home")
                    val result = AccessibilityManager.home()
                    appendLog("Home: ${result::class.simpleName}")
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Home")
            }

            Button(
                onClick = {
                    appendLog(">>> 测试 Back")
                    val result = AccessibilityManager.back()
                    appendLog("Back: ${result::class.simpleName}")
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Back")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    appendLog(">>> 启动 Alook")
                    val result = AccessibilityManager.launchAppDirect(context, "alook.browser")
                    appendLog("Alook: ${result::class.simpleName}")
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Alook")
            }

            Button(
                onClick = {
                    appendLog(">>> 启动设置")
                    val result = AccessibilityManager.launchAppDirect(context, "com.android.settings")
                    appendLog("设置：${result::class.simpleName}")
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("设置")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 获取应用包名
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "获取应用包名",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "获取系统中已安装的应用包名列表",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            appendLog(">>> 获取系统应用包名")
                            val systemApps = AppInfoUtils.getSystemAppPackages(context)
                            appendLog("系统应用数量：${systemApps.size}")
                            appendLog("----------------------------")
                            systemApps.forEach { pkg ->
                                appendLog(pkg)
                            }
                            appendLog("----------------------------")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("系统应用")
                    }

                    Button(
                        onClick = {
                            appendLog(">>> 获取用户安装应用包名")
                            val userApps = AppInfoUtils.getUserAppPackages(context)
                            appendLog("用户应用数量：${userApps.size}")
                            appendLog("----------------------------")
                            userApps.forEach { pkg ->
                                appendLog(pkg)
                            }
                            appendLog("----------------------------")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("用户应用")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                appendLog(">>> 测试滑动")
                val executor = AccessibilityManager.createExecutor()
                if (executor != null) {
                    executor.swipeUp()
                    appendLog("swipeUp: Success")
                } else {
                    appendLog("swipeUp: 服务未连接")
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("向上滑动")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 输入测试 - 使用传统 Android View 输入框
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "输入测试 (需要其他应用)",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "请在其他应用 (如短信、浏览器) 中测试输入功能",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "1. 打开短信或浏览器应用",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "2. 点击输入框聚焦",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "3. 回到此应用点击下方按钮",
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        scope.launch {
                            appendLog(">>> 测试输入 (3 秒后)")
                            appendLog("倒计时：3...")
                            delay(1000)
                            appendLog("倒计时：2...")
                            delay(1000)
                            appendLog("倒计时：1...")
                            delay(1000)

                            appendLog("正在输入文字...")
                            val result = AccessibilityManager.typeText("Hello World")
                            appendLog("typeText: ${result::class.simpleName}")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isServiceEnabled
                ) {
                    Text("测试输入 (聚焦其他应用输入框，3 秒延迟)")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        scope.launch {
                            appendLog(">>> 点击'发送' (3 秒后)")
                            appendLog("倒计时：3...")
                            delay(1000)
                            appendLog("倒计时：2...")
                            delay(1000)
                            appendLog("倒计时：1...")
                            delay(1000)

                            val result = AccessibilityManager.tapByText("发送")
                            appendLog("tapByText: ${result::class.simpleName}")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isServiceEnabled
                ) {
                    Text("点击文本'发送' (3 秒延迟)")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 点击测试
        Button(
            onClick = {
                appendLog(">>> 点击文本'测试输入'")
                val result = AccessibilityManager.tapByText("测试输入 (自动聚焦输入框)")
                appendLog("tapByText: ${result::class.simpleName}")
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = isServiceEnabled
        ) {
            Text("点击文本'测试'")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 获取屏幕内容
        Button(
            onClick = {
                scope.launch {
                    appendLog(">>> 获取屏幕内容 (3 秒后)")
                    appendLog("倒计时：3...")
                    delay(1000)
                    appendLog("倒计时：2...")
                    delay(1000)
                    appendLog("倒计时：1...")
                    delay(1000)

                    val screenInfo = AccessibilityManager.getScreenContent()
                    if (screenInfo != null) {
                        val app = AccessibilityManager.getCurrentApp()
                        appendLog("============================")
                        appendLog("当前应用：$app")
                        appendLog("节点数：${screenInfo.nodeCount}")
                        appendLog("----------------------------")
                        // 打印完整内容
                        val formatted = ScreenContentUtils.toFormattedString(screenInfo, maxDepth = 15)
                        appendLog(formatted)
                        appendLog("============================")
                    } else {
                        appendLog("获取屏幕内容失败")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = isServiceEnabled && !isFetching
        ) {
            Text("获取屏幕内容 (3 秒延迟)")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 系统截屏测试
        Button(
            onClick = {
                appendLog(">>> 触发系统截屏")
                val service = AccessibilityManager.getService()
                if (service != null) {
                    val success = service.takeSystemScreenshot()
                    if (success) {
                        appendLog("截屏成功！请查看相册")
                    } else {
                        appendLog("截屏失败：此机型不支持系统截屏 API")
                        appendLog("建议使用 MediaProjection 方案")
                    }
                } else {
                    appendLog("服务未连接")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = isServiceEnabled
        ) {
            Text("测试系统截屏 (Android 10+)")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // MediaProjection 截屏测试
        Button(
            onClick = {
                Log.d("MainActivity", "Button clicked, isAuthorized=${screenshotManager.isAuthorized()}")
                appendLog(">>> MediaProjection 截屏 (3 秒后)")
                if (screenshotManager.isAuthorized()) {
                    appendLog("已授权，3 秒后开始截屏...")
                    appendLog("倒计时：3...")
                    // 已授权，延迟 3 秒后截屏
                    scope.launch {
                        delay(1000)
                        appendLog("倒计时：2...")
                        delay(1000)
                        appendLog("倒计时：1...")
                        delay(1000)

                        try {
                            Log.d("MainActivity", "Calling captureToBase64...")
                            val base64 = screenshotManager.captureToBase64(context)
                            Log.d("MainActivity", "captureToBase64 returned: ${base64 != null}")
                            if (base64 != null) {
                                appendLog("截屏成功!")
                                appendLog("Base64 长度：${base64.length}")
                                appendLog("前 100 字符：${base64.take(100)}...")
                            } else {
                                appendLog("截屏失败：返回 null")
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "截屏异常", e)
                            appendLog("截屏异常：${e.message}")
                        }
                    }
                } else {
                    appendLog("未授权，请求授权...")
                    // 未授权，请求授权
                    val intent = ScreenshotManager.getMediaProjectionIntent(context)
                    Log.d("MainActivity", "Launching intent: $intent")
                    screenshotActivityLauncher.launch(intent)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = true
        ) {
            Text("MediaProjection 截屏 (获取 Base64, 3 秒延迟)")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // JSON 输出测试
        Button(
            onClick = {
                scope.launch {
                    appendLog(">>> 获取屏幕 JSON (3 秒后)")
                    appendLog("倒计时：3...")
                    delay(1000)
                    appendLog("倒计时：2...")
                    delay(1000)
                    appendLog("倒计时：1...")
                    delay(1000)

                    val screenInfo = AccessibilityManager.getScreenContent()
                    if (screenInfo != null) {
                        val json = ScreenContentUtils.toJson(screenInfo, maxDepth = 15)
                        appendLog("============================")
                        appendLog(json.toString())
                        appendLog("============================")
                    } else {
                        appendLog("获取 JSON 失败")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = isServiceEnabled && !isFetching
        ) {
            Text("获取完整屏幕 JSON(3 秒延迟)")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // AI 命令解析测试
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "AI 命令解析测试",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "模拟 AI 返回的命令，测试解析和执行功能",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            appendLog(">>> 测试无障碍模式命令解析")
                            val testJson = """
                            {
                                "thought": "用户想要点击'微信'文本，我需要使用无障碍 API 点击文本",
                                "mode": "accessibility",
                                "actions": [
                                    {
                                        "action": "tap_by_text",
                                        "params": { "text": "微信" },
                                        "_metadata": "do"
                                    }
                                ]
                            }
                            """.trimIndent()

                            val response = AIResponseParser.parse(testJson)
                            if (response != null) {
                                appendLog("解析成功!")
                                appendLog("模式：${response.mode}")
                                appendLog("思考：${response.thought}")
                                appendLog("行动数：${response.actions.size}")

                                val results = commandExecutor.execute(response)
                                results.forEach { appendLog(it) }
                            } else {
                                appendLog("解析失败!")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isServiceEnabled && operationMode == "无障碍模式"
                ) {
                    Text("测试无障碍模式 (tap_by_text)")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        scope.launch {
                            appendLog(">>> 测试坐标模式命令解析")
                            val testJson = """
                            {
                                "thought": "用户想要点击屏幕坐标 (200, 500)，我需要使用坐标点击",
                                "mode": "screenshot",
                                "actions": [
                                    {
                                        "action": "Tap",
                                        "element": [200, 500],
                                        "_metadata": "do"
                                    }
                                ]
                            }
                            """.trimIndent()

                            val response = AIResponseParser.parse(testJson)
                            if (response != null) {
                                appendLog("解析成功!")
                                appendLog("模式：${response.mode}")
                                appendLog("思考：${response.thought}")
                                appendLog("行动数：${response.actions.size}")

                                val results = commandExecutor.execute(response)
                                results.forEach { appendLog(it) }
                            } else {
                                appendLog("解析失败!")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isServiceEnabled && operationMode == "截图坐标模式"
                ) {
                    Text("测试坐标模式 (Tap)")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 日志输出
        Text("日志输出", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                Text(
                    text = logText,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.TopStart)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 复制和清空按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    // 复制日志到剪贴板
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("log", logText))
                    Toast.makeText(context, "已复制日志到剪贴板", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("复制日志")
            }

            Button(
                onClick = { logText = "日志输出：\n" },
                modifier = Modifier.weight(1f)
            ) {
                Text("清空日志")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 分割线按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    appendLog("-------------------")
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("分割线")
            }
        }
    }
}
