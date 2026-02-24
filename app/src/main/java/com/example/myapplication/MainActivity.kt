package com.example.autoscreenagent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.example.autoscreenagent.accessibility.*
import com.example.autoscreenagent.ui.theme.MyApplicationTheme
import com.example.autoscreenagent.ai.CommandExecutor
import com.example.autoscreenagent.ui.screens.SettingsScreen
import com.example.autoscreenagent.data.remote.LangGraphClient
import com.example.autoscreenagent.ui.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val appViewModel: AppViewModel = viewModel()
                MainScreen(appViewModel = appViewModel)
            }
        }
    }
}

@Composable
fun MainScreen(
    appViewModel: AppViewModel
) {
    var showSettings by remember { mutableStateOf(false) }
    var showDebugMenu by remember { mutableStateOf(false) }

    // 收集配置状态
    val config by appViewModel.config.collectAsState()

    if (showSettings) {
        SettingsScreen(
            config = config,
            onConfigChanged = { newConfig ->
                appViewModel.saveConfig(newConfig)
            },
            onBack = { showSettings = false }
        )
    } else if (showDebugMenu) {
        DebugMenuScreen(
            appViewModel = appViewModel,
            onBack = { showDebugMenu = false }
        )
    } else {
        ChatScreen(
            appViewModel = appViewModel,
            onNavigateToSettings = { showSettings = true },
            onOpenDebugMenu = { showDebugMenu = true }
        )
    }
}

/**
 * 消息数据类
 */
data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * AI 对话主屏幕
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    appViewModel: AppViewModel,
    onNavigateToSettings: () -> Unit,
    onOpenDebugMenu: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 聊天消息列表
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    val listState = rememberLazyListState()

    // 输入框
    var inputText by remember { mutableStateOf("") }

    // Agent 状态
    var isAgentRunning by remember { mutableStateOf(false) }
    var agentStatus by remember { mutableStateOf("就绪") }

    // 无障碍服务状态 - 使用 remember 和 LaunchedEffect 来正确管理状态
    var isAccessibilityEnabled by remember { mutableStateOf(AccessibilityManager.isEnabled(context)) }

    // 定期刷新无障碍服务状态
    LaunchedEffect(Unit) {
        while (true) {
            isAccessibilityEnabled = AccessibilityManager.isEnabled(context)
            kotlinx.coroutines.delay(1000)
        }
    }

    // 截屏授权状态
    val screenshotManager = remember { ScreenshotManager.getInstance() }
    screenshotManager.setContext(context)
    var isScreenshotAuthorized by remember { mutableStateOf(false) }

    // 监听截屏授权广播
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.example.autoscreenagent.MEDIA_PROJECTION_AUTH_RESULT") {
                    isScreenshotAuthorized = screenshotManager.isAuthorized()
                }
            }
        }
        val filter = IntentFilter("com.example.autoscreenagent.MEDIA_PROJECTION_AUTH_RESULT")
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        isScreenshotAuthorized = screenshotManager.isAuthorized()
        onDispose { context.unregisterReceiver(receiver) }
    }

    // 监听截屏服务状态变化
    LaunchedEffect(Unit) {
        while (true) {
            isScreenshotAuthorized = screenshotManager.isAuthorized()
            kotlinx.coroutines.delay(1000)
        }
    }

    val langGraphClient = remember { appViewModel.getLangGraphClient() }
    val commandExecutor = remember { CommandExecutor(context) }

    // 收集服务器连接状态
    val connected by appViewModel.isConnected.collectAsState()

    // 滚动到最新消息
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun addMessage(content: String, isUser: Boolean) {
        messages = messages + ChatMessage(content = content, isUser = isUser)
    }

    fun appendLog(msg: String) {
        messages = messages + ChatMessage(content = msg, isUser = false)
    }

    // 使用 Scaffold 包裹整个页面
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AI 手机助手", fontWeight = FontWeight.Bold)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .background(
                                            color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        )
                                ) {
                                    if (!connected) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = null,
                                            modifier = Modifier.size(10.dp),
                                            tint = MaterialTheme.colorScheme.onError
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (connected) "已连接" else "未连接",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isAccessibilityEnabled) Icons.Default.CheckCircle else Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (isAccessibilityEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "无障碍",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isAccessibilityEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isScreenshotAuthorized) Icons.Default.CheckCircle else Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (isScreenshotAuthorized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "截屏",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isScreenshotAuthorized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenDebugMenu,
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "调试",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        // 主内容
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 16.dp)
        ) {
        // 聊天消息列表
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "AI",
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "你好！我是 AI 手机助手 👋",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "请告诉我你想要完成什么任务，\n例如：「打开微信」、「发送消息给张三」",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.5
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(messages, key = { it.timestamp }) { message ->
                        ChatMessageBubble(message = message)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 状态显示
        if (isAgentRunning) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = agentStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 输入区域
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp, max = 120.dp),
                placeholder = { Text("告诉我你想要完成什么任务...") },
                shape = RoundedCornerShape(28.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column {
                // 发送按钮
                FloatingActionButton(
                    onClick = {
                        if (inputText.isNotBlank() && !isAgentRunning) {
                            // 添加用户消息
                            addMessage(inputText, isUser = true)
                            val userGoal = inputText
                            inputText = ""

                            // 启动 Agent
                            isAgentRunning = true
                            agentStatus = "分析中..."

                            scope.launch {
                                runAgentLoop(
                                    goal = userGoal,
                                    langGraphClient = langGraphClient,
                                    screenshotManager = screenshotManager,
                                    commandExecutor = commandExecutor,
                                    onLog = { msg ->
                                        // 将日志拆分成多行显示
                                        msg.lines().forEach { line ->
                                            if (line.isNotBlank()) {
                                                addMessage(line, isUser = false)
                                            }
                                        }
                                    },
                                    onStatus = { status ->
                                        agentStatus = status
                                    },
                                    onComplete = {
                                        isAgentRunning = false
                                        agentStatus = "就绪"
                                        addMessage("任务完成！还有什么可以帮你的吗？", isUser = false)
                                    }
                                )
                            }
                        }
                    },
                    containerColor = if (inputText.isNotBlank() && !isAgentRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送"
                    )
                }

                // 停止按钮（运行时显示）
                if (isAgentRunning) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FloatingActionButton(
                        onClick = {
                            isAgentRunning = false
                            agentStatus = "已停止"
                            addMessage("已停止执行", isUser = false)
                        },
                        containerColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "停止",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
    }
}

/**
 * 聊天消息气泡
 */
@Composable
fun ChatMessageBubble(
    message: ChatMessage
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (message.isUser) 16.dp else 4.dp,
                        bottomEnd = if (message.isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (message.isUser)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.secondaryContainer
                )
                .padding(12.dp)
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.isUser)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * 隐藏调试菜单
 */
@Composable
fun DebugMenuScreen(
    appViewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var logText by remember { mutableStateOf("调试日志：\n") }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var isServiceEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        snapshotFlow { AccessibilityManager.isEnabled(context) }.collect { enabled ->
            isServiceEnabled = enabled
        }
    }

    val screenshotManager = remember { ScreenshotManager.getInstance() }
    screenshotManager.setContext(context)

    fun appendLog(msg: String) {
        logText = "${logText}${msg}\n"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text("🔧 调试菜单", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("返回主页")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("快速测试", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    appendLog(">>> Home")
                    AccessibilityManager.home()
                },
                modifier = Modifier.weight(1f)
            ) { Text("Home") }

            Button(
                onClick = {
                    appendLog(">>> Back")
                    AccessibilityManager.back()
                },
                modifier = Modifier.weight(1f)
            ) { Text("Back") }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    appendLog(">>> 启动微信")
                    AccessibilityManager.launchAppDirect(context, "com.tencent.mm")
                },
                modifier = Modifier.weight(1f)
            ) { Text("微信") }

            Button(
                onClick = {
                    appendLog(">>> 启动设置")
                    AccessibilityManager.launchAppDirect(context, "com.android.settings")
                },
                modifier = Modifier.weight(1f)
            ) { Text("设置") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("屏幕内容", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                scope.launch {
                    appendLog(">>> 获取屏幕内容")
                    val screenInfo = AccessibilityManager.getScreenContent()
                    if (screenInfo != null) {
                        appendLog("节点数：${screenInfo.nodeCount}")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = isServiceEnabled
        ) {
            Text("获取屏幕内容")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                scope.launch {
                    appendLog(">>> 截屏")
                    val base64 = screenshotManager.captureToBase64(context)
                    if (base64 != null) {
                        appendLog("截屏成功，长度：${base64.length}")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = screenshotManager.isAuthorized()
        ) {
            Text("截屏 (Base64)")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("调试日志", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                Text(
                    text = logText,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * 运行 AI Agent 循环
 */
suspend fun runAgentLoop(
    goal: String,
    langGraphClient: LangGraphClient,
    screenshotManager: ScreenshotManager,
    commandExecutor: CommandExecutor,
    onLog: (String) -> Unit,
    onStatus: (String) -> Unit,
    onComplete: () -> Unit
) {
    val maxIterations = 10
    var iteration = 0
    var taskCompleted = false
    var lastActionResults = mutableListOf<String>()

    try {
        while (!taskCompleted && iteration < maxIterations) {
            iteration++
            onStatus("第 $iteration 次循环")

            val service = AccessibilityManager.getService()
            if (service == null) {
                onLog("错误：无障碍服务未连接")
                onStatus("服务未连接")
                return
            }

            onStatus("发送 AI 分析...")

            // 构建消息：只发送增量信息
            val feedbackContent = if (lastActionResults.isNotEmpty()) {
                lastActionResults.joinToString("\n")
            } else {
                "请分析并返回下一步操作指令。"
            }

            val message = """用户目标：$goal

执行结果反馈：
$feedbackContent

请根据以上执行结果，分析当前状态并返回下一步操作指令。
如果需要获取屏幕内容（无障碍 UI 树），请使用 get_screen_content 动作。
如果需要截屏，请使用 capture_screenshot 动作。"""

            onLog("发送消息给 AI...")

            // 收集 AI 响应 - 只保留最后一个有效的 updates/values 事件
            val responseFlow = langGraphClient.sendMessage(message, null)
            var lastValidResponse = ""

            responseFlow.collect { chunk ->
                onLog("收到：${chunk.take(80)}...")
                if (chunk.trim().startsWith("{")) {
                    lastValidResponse = chunk
                }
            }

            onStatus("解析 AI 响应...")

            // 解析 AI 响应
            val response = com.example.autoscreenagent.ai.AIResponseParser.parse(lastValidResponse)
            if (response == null) {
                onLog("错误：AI 响应解析失败")
                onStatus("解析失败")
                return
            }

            // 检查 AI 是否返回了 Finish 动作
            val hasFinishAction = response.actions.any {
                it.action.lowercase() == "finish"
            }
            if (hasFinishAction) {
                onLog("✅ 任务完成")
                taskCompleted = true
            }

            // 执行行动
            onStatus("执行行动...")
            val results = commandExecutor.execute(response)

            // 收集所有执行结果反馈给 AI
            val currentActionResults = mutableListOf<String>()
            for (result in results) {
                onLog(result.message)
                currentActionResults.add(result.message)

                if (result.message.contains("🎉") || result.message.contains("任务完成")) {
                    taskCompleted = true
                }
            }

            lastActionResults = currentActionResults

            if (taskCompleted) {
                onStatus("任务已完成")
                break
            }

            kotlinx.coroutines.delay(1000)
        }

        if (iteration >= maxIterations && !taskCompleted) {
            onLog("达到最大循环次数 ($maxIterations)，停止执行")
        }

    } catch (e: Exception) {
        onLog("异常：${e.message}")
    } finally {
        onComplete()
    }
}
