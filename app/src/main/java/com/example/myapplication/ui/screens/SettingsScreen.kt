package com.example.autoscreenagent.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.ContextCompat
import com.example.autoscreenagent.accessibility.AccessibilityManager
import com.example.autoscreenagent.accessibility.ScreenshotManager
import com.example.autoscreenagent.data.remote.AgentConfig
import com.example.autoscreenagent.service.ScreenshotForegroundService
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 设置屏幕
 * 用于配置 LangGraph Server 地址和 Assistant ID
 */
@Composable
fun SettingsScreen(
    config: AgentConfig,
    onConfigChanged: (AgentConfig) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // 权限状态 - 使用 remember 和 LaunchedEffect 来正确管理状态
    var isAccessibilityEnabled by remember { mutableStateOf(AccessibilityManager.isEnabled(context)) }
    val screenshotManager = remember { ScreenshotManager.getInstance() }
    screenshotManager.setContext(context)
    var isScreenshotAuthorized by remember { mutableStateOf(false) }

    // 定期刷新无障碍服务状态
    LaunchedEffect(Unit) {
        while (true) {
            isAccessibilityEnabled = AccessibilityManager.isEnabled(context)
            kotlinx.coroutines.delay(1000)
        }
    }

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

    // 定期刷新截屏授权状态
    LaunchedEffect(Unit) {
        while (true) {
            isScreenshotAuthorized = screenshotManager.isAuthorized()
            kotlinx.coroutines.delay(1000)
        }
    }

    val screenshotActivityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == -1) {
            result.data?.let { data ->
                ScreenshotForegroundService.startWithAuthorization(context, result.resultCode, data)
            }
        }
    }

    var baseUrl by remember { mutableStateOf(config.baseUrl) }
    var assistantId by remember { mutableStateOf(config.assistantId) }
    var timeoutSeconds by remember { mutableStateOf(config.timeoutSeconds.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 标题
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // 权限管理卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "权限管理",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(12.dp))

                // 无障碍服务
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "无障碍服务",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (isAccessibilityEnabled) "已启用" else "未启用",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    if (!isAccessibilityEnabled) {
                        Button(onClick = { AccessibilityManager.openAccessibilitySettings(context) }) {
                            Text("去开启")
                        }
                    } else {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "已启用",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // 截屏服务
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "截屏服务",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (isScreenshotAuthorized) "已授权" else "未授权",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    if (!isScreenshotAuthorized) {
                        Button(onClick = {
                            val intent = ScreenshotManager.getMediaProjectionIntent(context)
                            screenshotActivityLauncher.launch(intent)
                        }) {
                            Text("授权")
                        }
                    } else {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "已授权",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // 悬浮窗
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "悬浮窗",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "在其他应用上层显示输入框",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    val floatingWindowManager = AccessibilityManager.getFloatingWindowManager()
                    var isFloatingWindowShowing by remember { mutableStateOf(floatingWindowManager?.isShowing() ?: false) }

                    // 定期检查悬浮窗状态
                    LaunchedEffect(Unit) {
                        while (true) {
                            isFloatingWindowShowing = AccessibilityManager.getFloatingWindowManager()?.isShowing() ?: false
                            kotlinx.coroutines.delay(1000)
                        }
                    }

                    if (!isFloatingWindowShowing) {
                        Button(onClick = {
                            AccessibilityManager.getFloatingWindowManager()?.show()
                            isFloatingWindowShowing = true
                            Toast.makeText(context, "悬浮窗已开启", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("开启")
                        }
                    } else {
                        Button(onClick = {
                            AccessibilityManager.getFloatingWindowManager()?.hide()
                            isFloatingWindowShowing = false
                            Toast.makeText(context, "悬浮窗已关闭", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("关闭")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 服务器配置标题
        Text(
            text = "服务器配置",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 服务器地址
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("服务器地址") },
            placeholder = { Text("http://192.168.1.100:2024") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Assistant ID
        OutlinedTextField(
            value = assistantId,
            onValueChange = { assistantId = it },
            label = { Text("Assistant ID") },
            placeholder = { Text("intelligent_deep_agent_mobile") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 超时时间
        OutlinedTextField(
            value = timeoutSeconds,
            onValueChange = { timeoutSeconds = it },
            label = { Text("超时时间（秒）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 保存按钮
        Button(
            onClick = {
                try {
                    val newConfig = AgentConfig(
                        baseUrl = baseUrl,
                        assistantId = assistantId,
                        timeoutSeconds = timeoutSeconds.toIntOrNull() ?: 60
                    )
                    onConfigChanged(newConfig)
                    saveConfig(context, newConfig)
                    Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                    onBack()
                } catch (e: Exception) {
                    Toast.makeText(context, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存设置")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 返回按钮
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("返回")
        }

        // 说明文字
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "说明：\n" +
                    "1. 服务器地址：LangGraph Server 的 URL\n" +
                    "2. Assistant ID：你的 Agent 名称，可从 /assistants 端点获取\n" +
                    "3. 超时时间：网络请求超时时间（秒）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 保存配置到 SharedPreferences
 */
fun saveConfig(context: Context, config: AgentConfig) {
    val prefs = context.getSharedPreferences("autoscreenagent_prefs", Context.MODE_PRIVATE)
    val json = Json.encodeToString(config)
    prefs.edit().putString("agent_config", json).apply()
}

/**
 * 从 SharedPreferences 加载配置
 */
fun loadConfig(context: Context): AgentConfig {
    val prefs = context.getSharedPreferences("autoscreenagent_prefs", Context.MODE_PRIVATE)
    val json = prefs.getString("agent_config", null)
    return if (json != null) {
        try {
            Json.decodeFromString<AgentConfig>(json)
        } catch (e: Exception) {
            AgentConfig()
        }
    } else {
        AgentConfig()
    }
}
