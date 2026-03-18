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
import com.example.autoscreenagent.accessibility.AccessibilityManager
import com.example.autoscreenagent.accessibility.ScreenshotManager
import com.example.autoscreenagent.agent.Agent
import com.example.autoscreenagent.agent.AgentConfig
import com.example.autoscreenagent.ai.CommandExecutor
import com.example.autoscreenagent.data.remote.model.ChatModelConfig
import com.example.autoscreenagent.data.remote.model.ChatModelFactory
import com.example.autoscreenagent.data.remote.model.ConversationConfig
import com.example.autoscreenagent.data.remote.model.ModelProvider
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

        // 智谱 API 测试（使用新系统）
        Text("智谱 API 测试（新系统）", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                scope.launch {
                    appendLog(">>> 测试智谱 API (文本)")
                    try {
                        val providerConfig = appViewModel.config.value.providerConfigs.zhipu
                        val chatModelConfig = ChatModelConfig.zhipu(
                            apiKey = providerConfig.apiKey,
                            model = providerConfig.defaultModel
                        )
                        val chatModel = ChatModelFactory.create(chatModelConfig)

                        val result = chatModel.invoke("你好，请用一句话介绍你自己")

                        if (result.hasError()) {
                            appendLog("❌ 错误: ${result.error?.message}")
                        } else {
                            if (result.reasoningContent.isNotEmpty()) {
                                appendLog("💭 思考: ${result.reasoningContent.take(100)}...")
                            }
                            appendLog("📝 回复: ${result.content}")
                            appendLog("✅ 智谱 API 测试成功！")
                        }
                    } catch (e: Exception) {
                        appendLog("❌ 错误: ${e.message}")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("测试智谱 API (文本)")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                scope.launch {
                    appendLog(">>> 测试智谱 API (图片)")
                    try {
                        val providerConfig = appViewModel.config.value.providerConfigs.zhipu
                        val chatModelConfig = ChatModelConfig.zhipu(
                            apiKey = providerConfig.apiKey,
                            model = providerConfig.defaultModel
                        )
                        val chatModel = ChatModelFactory.create(chatModelConfig)

                        val base64 = screenshotManager.captureToBase64(context)
                        if (base64 != null) {
                            appendLog("截屏成功，发送给智谱...")

                            val result = chatModel.invoke("描述这张截图的内容", base64)

                            if (result.hasError()) {
                                appendLog("❌ 错误: ${result.error?.message}")
                            } else {
                                appendLog("📝 描述: ${result.content}")
                                appendLog("✅ 图片理解测试成功！")
                            }
                        } else {
                            appendLog("❌ 截屏失败")
                        }
                    } catch (e: Exception) {
                        appendLog("❌ 错误: ${e.message}")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = screenshotManager.isAuthorized()
        ) {
            Text("测试智谱 API (截图)")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 对话历史测试
        Button(
            onClick = {
                scope.launch {
                    appendLog(">>> 测试对话历史（自动移除图片）")
                    try {
                        // 创建会话，2 轮后移除图片
                        val providerConfig = appViewModel.config.value.providerConfigs.zhipu
                        val chatModelConfig = ChatModelConfig.zhipu(
                            apiKey = providerConfig.apiKey,
                            model = providerConfig.defaultModel
                        )
                        val conversationConfig = ConversationConfig(
                            systemPrompt = "你是一个手机操作助手",
                            removeImagesAfterRounds = 2
                        )
                        val chatModel = ChatModelFactory.create(chatModelConfig, conversationConfig)

                        // 第 1 轮：文本
                        appendLog("第 1 轮: 我叫小明")
                        var result = chatModel.invoke("我叫小明，请记住我的名字")
                        appendLog("📝 回复: ${result.content}")

                        // 第 2 轮：测试记忆
                        appendLog("\n第 2 轮: 我叫什么？")
                        result = chatModel.invoke("我叫什么名字？")
                        appendLog("📝 回复: ${result.content}")

                        appendLog("\n📊 当前历史: ${chatModel.history.size} 条消息")
                        appendLog("✅ 对话历史测试成功！AI 应该记住了名字")
                    } catch (e: Exception) {
                        appendLog("❌ 错误: ${e.message}")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("测试对话历史")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 测试 Agent 模块
        Button(
            onClick = {
                appendLog("\n=== 测试 Agent ===")
                scope.launch {
                    try {
                        // 使用 AppViewModel 创建 Agent
                        val commandExecutor = CommandExecutor(context)
                        val agent = appViewModel.createAgent(
                            context = context,
                            screenshotManager = screenshotManager,
                            commandExecutor = commandExecutor
                        )

                        // 监听状态
                        launch {
                            agent.state.collect { state ->
                                appendLog("状态: $state")
                            }
                        }

                        // 运行（使用新的 runWithTools 方法）
                        val result = agent.runWithTools(
                            goal = "打开微信",
                            onLog = { log -> appendLog(log) }
                        )

                        appendLog("\n✅ Agent 测试完成，最终状态: $result")
                    } catch (e: Exception) {
                        appendLog("❌ Agent 测试失败: ${e.message}")
                        e.printStackTrace()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("测试 Agent 模块")
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