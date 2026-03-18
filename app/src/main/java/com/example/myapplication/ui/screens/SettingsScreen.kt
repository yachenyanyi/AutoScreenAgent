package com.example.autoscreenagent.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ArrowDropDown
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
import com.example.autoscreenagent.data.remote.ModelOption
import com.example.autoscreenagent.data.remote.model.ModelProviderType
import com.example.autoscreenagent.service.ScreenshotForegroundService
import com.example.autoscreenagent.util.NotificationHelper
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)

/**
 * 设置屏幕
 * 用于配置模型 API 和 Agent 参数
 */
@Composable
fun SettingsScreen(
    config: AgentConfig,
    onConfigChanged: (AgentConfig) -> Unit,
    onModelConfigClick: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // 权限状态
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

    // 通知权限 (Android 13+)
    var hasNotificationPermission by remember { mutableStateOf(NotificationHelper.hasNotificationPermission(context)) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    // 配置状态
    var selectedProvider by remember { mutableStateOf(config.getProviderType()) }
    var selectedModel by remember { mutableStateOf(config.model) }
    var customModelId by remember { mutableStateOf(config.customModelId) }
    var maxIterations by remember { mutableStateOf(config.maxIterations.toString()) }
    var iterationDelay by remember { mutableStateOf(config.iterationDelayMs.toString()) }
    var autoCaptureScreenshot by remember { mutableStateOf(config.autoCaptureScreenshot) }
    var enableThinking by remember { mutableStateOf(config.enableThinking) }
    var maxHistoryMessages by remember { mutableStateOf(config.maxHistoryMessages.toString()) }
    var removeImagesAfterRounds by remember { mutableStateOf(config.removeImagesAfterRounds.toString()) }

    // 厂商选择下拉菜单
    var providerExpanded by remember { mutableStateOf(false) }
    val availableProviders = AgentConfig.getAvailableProviders()
    val selectedProviderInfo = availableProviders.find { it.id == selectedProvider.name }

    // 模型选择下拉菜单
    var modelExpanded by remember { mutableStateOf(false) }
    val availableModels = AgentConfig.getAvailableModels(selectedProvider, customModelId)
    val selectedModelInfo = availableModels.find { it.id == selectedModel }

    // 当厂商变化时，重置模型选择
    LaunchedEffect(selectedProvider) {
        val defaultModel = AgentConfig.getDefaultModel(selectedProvider)
        val hasModel = availableModels.any { it.id == selectedModel }
        if (!hasModel) {
            selectedModel = defaultModel
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
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
                PermissionRow(
                    title = "无障碍服务",
                    status = if (isAccessibilityEnabled) "已启用" else "未启用",
                    isEnabled = isAccessibilityEnabled,
                    buttonText = if (isAccessibilityEnabled) null else "去开启",
                    onButtonClick = { AccessibilityManager.openAccessibilitySettings(context) }
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // 截屏服务
                PermissionRow(
                    title = "截屏服务",
                    status = if (isScreenshotAuthorized) "已授权" else "未授权",
                    isEnabled = isScreenshotAuthorized,
                    buttonText = if (isScreenshotAuthorized) null else "授权",
                    onButtonClick = {
                        val intent = ScreenshotManager.getMediaProjectionIntent(context)
                        screenshotActivityLauncher.launch(intent)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // 悬浮窗
                PermissionRow(
                    title = "悬浮窗",
                    subtitle = "在其他应用上层显示输入框",
                    status = null,
                    isEnabled = false,
                    buttonText = null,
                    onButtonClick = {}
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // 通知权限 (Android 13+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    PermissionRow(
                        title = "通知权限",
                        subtitle = "任务完成时发送通知",
                        status = if (hasNotificationPermission) "已授权" else "未授权",
                        isEnabled = hasNotificationPermission,
                        buttonText = if (hasNotificationPermission) null else "授权",
                        onButtonClick = {
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 模型配置入口卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onModelConfigClick
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "模型配置",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "配置各厂商 API Key 和参数",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // 显示已配置的厂商数量
                    val configuredCount = config.providerConfigs.getConfiguredProviders().size
                    if (configuredCount > 0) {
                        Text(
                            text = "已配置 $configuredCount 个厂商",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "进入配置"
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Agent 配置标题
        Text(
            text = "Agent 配置",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 厂商选择
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "选择厂商",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = providerExpanded,
                    onExpandedChange = { providerExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedProviderInfo?.name ?: selectedProvider.name,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = providerExpanded,
                        onDismissRequest = { providerExpanded = false }
                    ) {
                        availableProviders.forEach { provider ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(provider.name)
                                        Text(
                                            text = provider.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedProvider = try {
                                        ModelProviderType.valueOf(provider.id)
                                    } catch (e: Exception) {
                                        ModelProviderType.ZHIPU
                                    }
                                    providerExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 模型选择
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "选择模型",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = modelExpanded,
                    onExpandedChange = { modelExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedModelInfo?.name ?: selectedModel,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = modelExpanded,
                        onDismissRequest = { modelExpanded = false }
                    ) {
                        availableModels.forEach { model ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(model.name)
                                        Text(
                                            text = model.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedModel = model.id
                                    modelExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 自定义模型 ID 输入框
                OutlinedTextField(
                    value = customModelId,
                    onValueChange = { customModelId = it },
                    label = { Text("自定义模型 ID（可选）") },
                    placeholder = { Text("例如: glm-4-custom") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // 当前厂商配置状态提示
                val currentProviderConfig = config.getCurrentProviderConfig()
                if (currentProviderConfig.hasApiKey()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "✓ 当前厂商已配置 API Key",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚠ 当前厂商未配置 API Key，请前往模型配置",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 执行参数卡片
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "执行参数",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(12.dp))

                // 最大迭代次数
                OutlinedTextField(
                    value = maxIterations,
                    onValueChange = { maxIterations = it },
                    label = { Text("最大迭代次数") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 迭代延迟
                OutlinedTextField(
                    value = iterationDelay,
                    onValueChange = { iterationDelay = it },
                    label = { Text("迭代延迟（毫秒）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 最大历史消息数
                OutlinedTextField(
                    value = maxHistoryMessages,
                    onValueChange = { maxHistoryMessages = it },
                    label = { Text("最大历史消息数") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 移除图片轮次
                OutlinedTextField(
                    value = removeImagesAfterRounds,
                    onValueChange = { removeImagesAfterRounds = it },
                    label = { Text("N 轮后移除图片（节省 token）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 功能开关卡片
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "功能开关",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))

                // 自动截屏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("自动截屏")
                        Text(
                            text = "每次操作后自动截屏发送给 AI",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoCaptureScreenshot,
                        onCheckedChange = { autoCaptureScreenshot = it }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // 思考模式
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("思考模式")
                        Text(
                            text = "启用智谱的推理思考过程",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enableThinking,
                        onCheckedChange = { enableThinking = it }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 保存按钮
        Button(
            onClick = {
                try {
                    val newConfig = AgentConfig(
                        modelProvider = selectedProvider.name,
                        model = selectedModel,
                        customModelId = customModelId.trim(),
                        maxIterations = maxIterations.toIntOrNull() ?: 10,
                        iterationDelayMs = iterationDelay.toLongOrNull() ?: 1000,
                        autoCaptureScreenshot = autoCaptureScreenshot,
                        enableThinking = enableThinking,
                        maxHistoryMessages = maxHistoryMessages.toIntOrNull() ?: 20,
                        removeImagesAfterRounds = removeImagesAfterRounds.toIntOrNull() ?: 3,
                        providerConfigs = config.providerConfigs  // 保持原有的多厂商配置
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
                    "1. 模型：推荐 glm-4-flash（免费），需要图片理解用 glm-4v-flash\n" +
                    "2. 最大迭代次数：任务执行的最大轮次，防止无限循环\n" +
                    "3. N 轮后移除图片：节省 token 用量，图片过几轮后会被移除\n" +
                    "4. 自动截屏：关闭后可减少 API 调用次数",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String? = null,
    status: String?,
    isEnabled: Boolean,
    buttonText: String?,
    onButtonClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            if (status != null) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }

        if (buttonText != null) {
            Button(onClick = onButtonClick) {
                Text(buttonText)
            }
        } else if (isEnabled) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "已启用",
                tint = MaterialTheme.colorScheme.primary
            )
        }
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
