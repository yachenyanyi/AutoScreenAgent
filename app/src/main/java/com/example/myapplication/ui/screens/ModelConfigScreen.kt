package com.example.autoscreenagent.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.autoscreenagent.data.remote.model.ModelProvider
import com.example.autoscreenagent.data.remote.model.MultiProviderConfig
import com.example.autoscreenagent.data.remote.model.ProviderConfig

/**
 * 模型配置页面
 * 显示所有厂商列表，点击进入详细配置
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelConfigScreen(
    multiProviderConfig: MultiProviderConfig,
    onConfigChanged: (MultiProviderConfig) -> Unit,
    onBack: () -> Unit
) {
    var selectedProvider by remember { mutableStateOf<ModelProvider?>(null) }

    if (selectedProvider == null) {
        // 厂商列表页面
        ProviderListScreen(
            multiProviderConfig = multiProviderConfig,
            onProviderClick = { selectedProvider = it },
            onBack = onBack
        )
    } else {
        // 厂商详细配置页面
        ProviderDetailScreen(
            provider = selectedProvider!!,
            config = multiProviderConfig.getProviderConfig(selectedProvider!!),
            onConfigChanged = { newConfig ->
                onConfigChanged(multiProviderConfig.updateProviderConfig(selectedProvider!!, newConfig))
            },
            onBack = { selectedProvider = null }
        )
    }
}

/**
 * 厂商列表页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderListScreen(
    multiProviderConfig: MultiProviderConfig,
    onProviderClick: (ModelProvider) -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // 标题
        Text(
            text = "模型配置",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "为每个厂商配置独立的 API Key 和参数",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // 厂商列表
        ProviderCard(
            name = "智谱 GLM",
            description = "国内免费，支持视觉模型和思考模式",
            config = multiProviderConfig.zhipu,
            onClick = { onProviderClick(ModelProvider.ZHIPU) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        ProviderCard(
            name = "阿里云百炼",
            description = "Qwen 系列模型，支持 Qwen3 思考模式",
            config = multiProviderConfig.qwen,
            onClick = { onProviderClick(ModelProvider.QWEN) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        ProviderCard(
            name = "OpenAI",
            description = "GPT 系列模型",
            config = multiProviderConfig.openai,
            onClick = { onProviderClick(ModelProvider.OPENAI) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        ProviderCard(
            name = "自定义",
            description = "自定义 OpenAI 兼容接口",
            config = multiProviderConfig.custom,
            onClick = { onProviderClick(ModelProvider.CUSTOM) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 说明
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "已配置 API Key 的厂商会在列表中显示绿色勾号。点击卡片进入详细配置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 厂商卡片
 */
@Composable
private fun ProviderCard(
    name: String,
    description: String,
    config: ProviderConfig,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (config.hasApiKey()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "已配置",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (config.hasApiKey()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "模型: ${config.defaultModel.ifBlank { "未设置" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = "进入配置",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 厂商详细配置页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDetailScreen(
    provider: ModelProvider,
    config: ProviderConfig,
    onConfigChanged: (ProviderConfig) -> Unit,
    onBack: () -> Unit
) {
    val providerName = when (provider) {
        ModelProvider.ZHIPU -> "智谱 GLM"
        ModelProvider.QWEN -> "阿里云百炼"
        ModelProvider.OPENAI -> "OpenAI"
        ModelProvider.CUSTOM -> "自定义"
    }

    var apiKey by remember { mutableStateOf(config.apiKey) }
    var baseUrl by remember { mutableStateOf(config.baseUrl) }
    var defaultModel by remember { mutableStateOf(config.defaultModel) }
    var temperature by remember { mutableStateOf(config.temperature.toString()) }
    var topP by remember { mutableStateOf(config.topP.toString()) }
    var maxTokens by remember { mutableStateOf(config.maxTokens?.toString() ?: "") }
    var timeoutSeconds by remember { mutableStateOf(config.timeoutSeconds.toString()) }
    var enableThinking by remember { mutableStateOf(config.enableThinking) }
    var showApiKey by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // 标题
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = providerName,
                style = MaterialTheme.typography.headlineLarge
            )
        }

        // API 配置卡片
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "API 配置",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                // API Key
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    placeholder = { Text("输入 API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showApiKey = !showApiKey }) {
                            Text(if (showApiKey) "隐藏" else "显示")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Base URL
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    placeholder = { Text("API 基础地址") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 默认模型
                OutlinedTextField(
                    value = defaultModel,
                    onValueChange = { defaultModel = it },
                    label = { Text("默认模型") },
                    placeholder = { Text("模型名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 生成参数卡片
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "生成参数",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Temperature
                Text("Temperature: ${temperature.toFloatOrNull()?.let { String.format("%.2f", it) } ?: "-"}")
                Slider(
                    value = temperature.toFloatOrNull() ?: 0.7f,
                    onValueChange = { temperature = it.toString() },
                    valueRange = 0f..2f,
                    steps = 20,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "控制输出的随机性，值越高输出越随机",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Top-P
                Text("Top-P: ${topP.toFloatOrNull()?.let { String.format("%.2f", it) } ?: "-"}")
                Slider(
                    value = topP.toFloatOrNull() ?: 1.0f,
                    onValueChange = { topP = it.toString() },
                    valueRange = 0f..1f,
                    steps = 10,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "核采样参数，控制候选词的概率累计阈值",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Max Tokens
                OutlinedTextField(
                    value = maxTokens,
                    onValueChange = { maxTokens = it.filter { c -> c.isDigit() } },
                    label = { Text("最大输出 Tokens") },
                    placeholder = { Text("留空则不限制") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 高级设置卡片
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "高级设置",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Timeout
                OutlinedTextField(
                    value = timeoutSeconds,
                    onValueChange = { timeoutSeconds = it.filter { c -> c.isDigit() } },
                    label = { Text("请求超时（秒）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 思考模式开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("思考模式")
                        Text(
                            text = if (provider == ModelProvider.OPENAI) "OpenAI 不支持思考模式" else "启用推理思考过程",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enableThinking,
                        onCheckedChange = { enableThinking = it },
                        enabled = provider != ModelProvider.OPENAI
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 保存按钮
        Button(
            onClick = {
                val newConfig = ProviderConfig(
                    apiKey = apiKey.trim(),
                    baseUrl = baseUrl.trim(),
                    defaultModel = defaultModel.trim(),
                    temperature = temperature.toFloatOrNull() ?: 0.7f,
                    topP = topP.toFloatOrNull() ?: 1.0f,
                    maxTokens = maxTokens.toIntOrNull(),
                    timeoutSeconds = timeoutSeconds.toIntOrNull() ?: 120,
                    enableThinking = enableThinking
                )
                onConfigChanged(newConfig)
                onBack()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存配置")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 重置按钮
        OutlinedButton(
            onClick = {
                val defaultConfig = when (provider) {
                    ModelProvider.ZHIPU -> ProviderConfig.zhipuDefault()
                    ModelProvider.QWEN -> ProviderConfig.qwenDefault()
                    ModelProvider.OPENAI -> ProviderConfig.openaiDefault()
                    ModelProvider.CUSTOM -> ProviderConfig.customDefault()
                }
                apiKey = defaultConfig.apiKey
                baseUrl = defaultConfig.baseUrl
                defaultModel = defaultConfig.defaultModel
                temperature = defaultConfig.temperature.toString()
                topP = defaultConfig.topP.toString()
                maxTokens = defaultConfig.maxTokens?.toString() ?: ""
                timeoutSeconds = defaultConfig.timeoutSeconds.toString()
                enableThinking = defaultConfig.enableThinking
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("重置为默认值")
        }
    }
}