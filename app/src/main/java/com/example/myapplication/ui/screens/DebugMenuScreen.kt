package com.example.autoscreenagent.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.autoscreenagent.accessibility.AccessibilityManager
import com.example.autoscreenagent.accessibility.ScreenshotManager
import com.example.autoscreenagent.ui.viewmodel.AppViewModel
import kotlinx.coroutines.launch

/**
 * 调试菜单屏幕
 */
@Composable
fun DebugMenuScreen(
    appViewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var logText by remember { mutableStateOf("调试日志：\n") }
    val scrollState = rememberScrollState()

    val screenshotManager = remember { ScreenshotManager.getInstance() }
    screenshotManager.setContext(context)

    var isServiceEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isServiceEnabled = AccessibilityManager.isEnabled(context)
    }

    fun appendLog(msg: String) {
        logText = "$logText$msg\n"
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

        // 快速测试
        Text("快速测试", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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

        // 屏幕内容
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

        // 调试日志
        Text("调试日志", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                Text(
                    text = logText,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}