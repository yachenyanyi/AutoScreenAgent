package com.example.autoscreenagent.data.remote.model

import android.util.Log

/**
 * 模型厂商管理器
 *
 * 统一管理所有模型厂商，提供切换和配置功能
 *
 * 使用示例：
 * ```kotlin
 * val manager = ModelManager.getInstance()
 *
 * // 切换到阿里云
 * manager.switchProvider(ModelProviderType.ALIBABA, "qwen-vl-plus")
 *
 * // 或者直接配置
 * manager.setConfig(ModelConfig.forAlibaba(apiKey = "your-key"))
 *
 * // 获取当前 Provider
 * val provider = manager.getProvider()
 *
 * // 发送消息
 * provider.sendMessage("你好").collect { chunk ->
 *     println(chunk.getContent())
 * }
 * ```
 */
class ModelManager private constructor() {

    companion object {
        private const val TAG = "ModelManager"

        @Volatile
        private var instance: ModelManager? = null

        fun getInstance(): ModelManager {
            return instance ?: synchronized(this) {
                instance ?: ModelManager().also { instance = it }
            }
        }
    }

    /**
     * 当前使用的 Provider
     */
    @Volatile
    private var currentProvider: ModelProvider? = null

    /**
     * 当前配置
     */
    @Volatile
    private var currentConfig: ModelConfig? = null

    /**
     * 切换模型厂商
     *
     * @param providerType 厂商类型
     * @param model 模型名称（可选，为空时使用厂商默认模型）
     * @param apiKey API Key（可选，为空时使用当前配置）
     * @param enableThinking 是否启用思考模式
     */
    fun switchProvider(
        providerType: ModelProviderType,
        model: String? = null,
        apiKey: String? = null,
        enableThinking: Boolean = true
    ) {
        val baseConfig = when (providerType) {
            ModelProviderType.ZHIPU -> ModelConfig.forZhipu(
                apiKey = apiKey ?: currentConfig?.apiKey ?: "",
                enableThinking = enableThinking
            )
            ModelProviderType.ALIBABA -> ModelConfig.forAlibaba(
                apiKey = apiKey ?: currentConfig?.apiKey ?: "",
                enableThinking = enableThinking
            )
            ModelProviderType.OPENAI -> ModelConfig.forOpenAI(
                apiKey = apiKey ?: currentConfig?.apiKey ?: ""
            )
            ModelProviderType.CUSTOM -> currentConfig?.copy(provider = providerType)
                ?: throw IllegalArgumentException("Custom Provider 需要提供完整配置")
        }

        val config = if (model != null) {
            baseConfig.copy(model = model)
        } else {
            baseConfig
        }

        setConfig(config)
    }

    /**
     * 设置完整配置
     */
    fun setConfig(config: ModelConfig) {
        // 关闭旧的 Provider
        currentProvider?.close()

        // 创建新的 Provider
        currentProvider = ModelProvider.create(config)
        currentConfig = config

        Log.d(TAG, "已切换到 ${config.provider}，模型: ${config.model}")
    }

    /**
     * 获取当前 Provider
     *
     * @throws IllegalStateException 如果未初始化
     */
    fun getProvider(): ModelProvider {
        return currentProvider ?: throw IllegalStateException("请先调用 setConfig 或 switchProvider 初始化")
    }

    /**
     * 获取当前配置
     */
    fun getConfig(): ModelConfig? = currentConfig

    /**
     * 获取当前厂商类型
     */
    fun getCurrentProviderType(): ModelProviderType? = currentConfig?.provider

    /**
     * 获取当前模型名称
     */
    fun getCurrentModel(): String? = currentConfig?.model

    /**
     * 更新 API Key
     */
    fun setApiKey(apiKey: String) {
        currentConfig?.let { config ->
            setConfig(config.withApiKey(apiKey))
        }
    }

    /**
     * 更新模型
     */
    fun setModel(model: String) {
        currentConfig?.let { config ->
            setConfig(config.withModel(model))
        }
    }

    /**
     * 切换思考模式
     */
    fun setThinkingEnabled(enabled: Boolean) {
        currentConfig?.let { config ->
            if (enabled) {
                setConfig(config.copy(enableThinking = true))
            } else {
                setConfig(config.withoutThinking())
            }
        }
    }

    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean = currentProvider != null

    /**
     * 验证当前配置
     */
    fun validate(): Boolean {
        return currentProvider?.isValid() == true
    }

    /**
     * 关闭并清理资源
     */
    fun close() {
        currentProvider?.close()
        currentProvider = null
        currentConfig = null
        Log.d(TAG, "ModelManager 已关闭")
    }

    // ========================================
    // 快捷方法
    // ========================================

    /**
     * 初始化为智谱
     */
    fun initWithZhipu(apiKey: String, model: String = "glm-4.6v-flash", enableThinking: Boolean = true) {
        setConfig(ModelConfig.forZhipu(apiKey, model, enableThinking))
    }

    /**
     * 初始化为阿里云
     */
    fun initWithAlibaba(apiKey: String, model: String = "qwen-vl-plus", enableThinking: Boolean = true) {
        setConfig(ModelConfig.forAlibaba(apiKey, model, enableThinking))
    }

    /**
     * 切换到智谱
     */
    fun useZhipu(model: String = "glm-4.6v-flash", enableThinking: Boolean = true) {
        switchProvider(ModelProviderType.ZHIPU, model, enableThinking = enableThinking)
    }

    /**
     * 切换到阿里云
     */
    fun useAlibaba(model: String = "qwen-vl-plus", enableThinking: Boolean = true) {
        switchProvider(ModelProviderType.ALIBABA, model, enableThinking = enableThinking)
    }

    /**
     * 切换到 OpenAI
     */
    fun useOpenAI(model: String = "gpt-4o-mini") {
        switchProvider(ModelProviderType.OPENAI, model)
    }

    /**
     * 切换到自定义厂商
     */
    fun useCustom(baseUrl: String, model: String, apiKey: String) {
        setConfig(ModelConfig(
            provider = ModelProviderType.CUSTOM,
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model
        ))
    }

    /**
     * 获取支持的模型列表
     */
    fun getSupportedModels(providerType: ModelProviderType): List<String> {
        return when (providerType) {
            ModelProviderType.ZHIPU -> listOf(
                "glm-4.6v-flash",
                "glm-4.6v",
                "glm-4v-flash",
                "glm-4v",
                "glm-3.5v-flash",
                "glm-3.5v"
            )
            ModelProviderType.ALIBABA -> listOf(
                // 思考模式模型
                "qwen3-plus",
                "qwen3-turbo",
                "qwen3-vl-plus",
                "qwen3-vl-max",
                "qwen3-32b",
                "qwen3-7b",
                // 商用模型
                "qwen-plus",
                "qwen-turbo",
                "qwen-max",
                "qwen-max-longcontext",
                // 多模态模型
                "qwen-vl-plus",
                "qwen-vl-max",
                "qwen-vl-plus-2025-01-25",
                "qwen-audio-turbo",
                "qwen-audio-chat"
            )
            ModelProviderType.OPENAI -> listOf(
                "gpt-4o",
                "gpt-4o-mini",
                "gpt-4-turbo",
                "gpt-4",
                "gpt-3.5-turbo"
            )
            ModelProviderType.CUSTOM -> emptyList()
        }
    }
}