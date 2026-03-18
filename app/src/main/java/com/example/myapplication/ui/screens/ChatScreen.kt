package com.example.autoscreenagent.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.autoscreenagent.*
import com.example.autoscreenagent.accessibility.AccessibilityManager
import com.example.autoscreenagent.accessibility.ScreenshotManager
import com.example.autoscreenagent.ai.CommandExecutor
import com.example.autoscreenagent.agent.Agent
import com.example.autoscreenagent.agent.AgentState
import com.example.autoscreenagent.ui.viewmodel.AppViewModel
import com.example.autoscreenagent.ui.viewmodel.MessageItem
import kotlinx.coroutines.launch
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import com.example.autoscreenagent.util.NotificationHelper

/**
 * 简单消息气泡组件
 * 支持长按复制
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatMessageBubble(message: ChatMessage) {
    val context = LocalContext.current

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
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        copyToClipboard(context, message.content)
                    }
                )
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

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("AI 回复", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
}

/**
 * 聚合消息气泡组件
 * 支持长按复制完整日志
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AggregatedMessageBubble(
    message: AggregatedMessage,
    onToggleExpand: () -> Unit
) {
    val context = LocalContext.current
    // 合并所有详情为可复制文本
    val fullContent = buildString {
        append("状态: ${message.status.name}\n")
        append("摘要: ${message.summary}\n")
        if (message.details.isNotEmpty()) {
            append("\n--- 详细日志 ---\n")
            message.details.forEach { append("$it\n") }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 32.dp) // 留出左侧空间，模拟 AI 消息靠左
            .combinedClickable(
                onClick = { onToggleExpand() },
                onLongClick = {
                    copyToClipboard(context, fullContent)
                }
            )
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = 4.dp,
                    bottomEnd = 16.dp
                )),
            colors = CardDefaults.cardColors(
                containerColor = when (message.status) {
                    MessageStatus.RUNNING -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    MessageStatus.SUCCESS -> MaterialTheme.colorScheme.secondaryContainer
                    MessageStatus.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    MessageStatus.STOPPED -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // 状态指示器 + 摘要
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 状态图标
                    when (message.status) {
                        MessageStatus.RUNNING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        MessageStatus.SUCCESS -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "成功",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        MessageStatus.ERROR -> {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "错误",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        MessageStatus.STOPPED -> {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "已停止",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 摘要文本
                    Text(
                        text = message.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        color = when (message.status) {
                            MessageStatus.ERROR -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )

                    // 展开/收起图标（有详情时显示）
                    if (message.details.isNotEmpty()) {
                        Icon(
                            imageVector = if (message.isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (message.isExpanded) "收起" else "展开",
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // 展开显示详细日志
                AnimatedVisibility(
                    visible = message.isExpanded && message.details.isNotEmpty(),
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        message.details.forEachIndexed { index, detail ->
                            // 根据日志内容选择颜色
                            val logColor = when {
                                detail.contains("✅") || detail.contains("成功") -> MaterialTheme.colorScheme.primary
                                detail.contains("❌") || detail.contains("失败") || detail.contains("错误") -> MaterialTheme.colorScheme.error
                                detail.startsWith("执行工具:") -> MaterialTheme.colorScheme.tertiary
                                detail.startsWith("工具结果:") -> MaterialTheme.colorScheme.onSurfaceVariant
                                detail.startsWith("AI") -> MaterialTheme.colorScheme.secondary
                                detail.contains("=== 第") -> MaterialTheme.colorScheme.outline
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }

                            Text(
                                text = detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = logColor,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                            if (index < message.details.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 消息项渲染组件
 */
@Composable
fun MessageItemView(
    item: MessageItem,
    onToggleExpand: (String) -> Unit
) {
    when (item) {
        is MessageItem.Simple -> ChatMessageBubble(item.message)
        is MessageItem.Aggregated -> AggregatedMessageBubble(
            message = item.message,
            onToggleExpand = { onToggleExpand(item.message.id) }
        )
    }
}

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

    // 从 ViewModel 获取消息列表和状态
    val messages by appViewModel.messages.collectAsState()
    val isAgentRunning by appViewModel.isAgentRunning.collectAsState()
    val agentStatus by appViewModel.agentStatus.collectAsState()

    val listState = rememberLazyListState()

    // 输入框
    var inputText by remember { mutableStateOf("") }

    // 无障碍服务状态
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

    val commandExecutor = remember { CommandExecutor(context) }

    // API 配置状态
    val config by appViewModel.config.collectAsState()
    val isApiConfigured = config.isCurrentProviderConfigured()

    // 滚动到最新消息
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // 启动 Agent 的函数（使用智谱 API）
    fun launchAgent(goal: String) {
        appViewModel.setAgentRunning(true)
        appViewModel.setAgentStatus("初始化 Agent...")
        appViewModel.startAggregatedMessage()

        // 获取新的 Agent 实例（每次都创建新的）
        val agent = appViewModel.createAgent(
            context = context,
            screenshotManager = screenshotManager,
            commandExecutor = commandExecutor
        )

        // 监听 Agent 状态
        scope.launch {
            agent.state.collect { state ->
                when (state) {
                    is AgentState.Running -> {
                        appViewModel.setAgentStatus(state.status)
                    }
                    is AgentState.Completed -> {
                        appViewModel.completeAggregatedMessage(
                            status = MessageStatus.SUCCESS,
                            finalMessage = state.message
                        )
                        appViewModel.setAgentRunning(false)
                        appViewModel.setAgentStatus("就绪")

                        // 如果应用在后台，发送通知
                        if (NotificationHelper.isAppInBackground(context)) {
                            NotificationHelper.showTaskCompletedNotification(context, state.message)
                        }
                    }
                    is AgentState.Failed -> {
                        appViewModel.completeAggregatedMessage(
                            status = MessageStatus.ERROR,
                            finalMessage = state.error
                        )
                        appViewModel.setAgentRunning(false)
                        appViewModel.setAgentStatus("失败")
                    }
                    is AgentState.Cancelled -> {
                        appViewModel.completeAggregatedMessage(
                            status = MessageStatus.ERROR,
                            finalMessage = "任务已取消"
                        )
                        appViewModel.setAgentRunning(false)
                        appViewModel.setAgentStatus("已取消")
                    }
                    is AgentState.Idle -> {
                        // 空闲状态，不做处理
                    }
                }
            }
        }

        // 运行 Agent（Tool Calling 模式）
        scope.launch {
            agent.runWithTools(
                goal = goal,
                onLog = { msg ->
                    msg.lines().forEach { line ->
                        if (line.isNotBlank()) {
                            appViewModel.appendLog(line)
                        }
                    }
                }
            )
        }
    }

    // 监听悬浮窗任务广播
    DisposableEffect(Unit) {
        val taskReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    "com.example.autoscreenagent.FLOATING_WINDOW_TASK" -> {
                        val taskText = intent.getStringExtra("task_text") ?: ""
                        if (taskText.isNotBlank() && !isAgentRunning) {
                            appViewModel.addUserMessage(taskText)
                            launchAgent(goal = taskText)
                        }
                    }
                    "com.example.autoscreenagent.FLOATING_WINDOW_STOP" -> {
                        appViewModel.completeAggregatedMessage(
                            status = MessageStatus.STOPPED,
                            finalMessage = "任务已停止"
                        )
                        appViewModel.setAgentRunning(false)
                        appViewModel.setAgentStatus("已停止")
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction("com.example.autoscreenagent.FLOATING_WINDOW_TASK")
            addAction("com.example.autoscreenagent.FLOATING_WINDOW_STOP")
        }
        ContextCompat.registerReceiver(context, taskReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(taskReceiver) }
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
                            StatusIndicator(
                                isConnected = isApiConfigured,
                                label = if (isApiConfigured) "API 已配置" else "未配置 API"
                            )
                            StatusIndicator(
                                isConnected = isAccessibilityEnabled,
                                label = "无障碍"
                            )
                            StatusIndicator(
                                isConnected = isScreenshotAuthorized,
                                label = "截屏"
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenDebugMenu) {
                        Icon(Icons.Default.Build, contentDescription = "调试")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 16.dp)
        ) {
            // 聊天消息列表
            ChatMessagesList(
                messages = messages,
                listState = listState,
                onToggleExpand = { messageId -> appViewModel.toggleMessageExpanded(messageId) },
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 状态显示
            if (isAgentRunning) {
                AgentStatusBar(status = agentStatus)
            }

            // 输入区域
            ChatInputArea(
                inputText = inputText,
                onInputTextChange = { inputText = it },
                isAgentRunning = isAgentRunning,
                onSend = {
                    if (inputText.isNotBlank() && !isAgentRunning) {
                        appViewModel.addUserMessage(inputText)
                        val userGoal = inputText
                        inputText = ""
                        launchAgent(goal = userGoal)
                    }
                },
                onStop = {
                    appViewModel.completeAggregatedMessage(
                        status = MessageStatus.STOPPED,
                        finalMessage = "已停止执行"
                    )
                    appViewModel.setAgentRunning(false)
                    appViewModel.setAgentStatus("已停止")
                }
            )
        }
    }
}

@Composable
private fun StatusIndicator(isConnected: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(
                    color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    shape = androidx.compose.foundation.shape.CircleShape
                )
        ) {
            if (!isConnected) {
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
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun ChatMessagesList(
    messages: List<MessageItem>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onToggleExpand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        if (messages.isEmpty()) {
            EmptyChatPlaceholder()
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(
                    items = messages,
                    key = { when (it) {
                        is MessageItem.Simple -> it.message.id
                        is MessageItem.Aggregated -> it.message.id
                    }}
                ) { item ->
                    MessageItemView(
                        item = item,
                        onToggleExpand = onToggleExpand
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyChatPlaceholder() {
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
                text = "你好！我是 AI 手机助手",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "请告诉我你想要完成什么任务，\n例如：「打开微信」、「发送消息给张三」",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun AgentStatusBar(status: String) {
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
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ChatInputArea(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    isAgentRunning: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputTextChange,
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
                onClick = onSend,
                containerColor = if (inputText.isNotBlank() && !isAgentRunning)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outline,
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
                    onClick = onStop,
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