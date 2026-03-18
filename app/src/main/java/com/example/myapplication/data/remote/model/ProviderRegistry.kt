package com.example.autoscreenagent.data.remote.model

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * ChatModel 注册表
 *
 * 支持多 Provider 并存，替代之前的 ModelManager 单例模式
 *
 * 使用示例：
 * ```kotlin
 * val registry = ProviderRegistry.getInstance()
 *
 * // 注册多个模型
 * registry.register("zhipu", ZhipuChatModel(ChatModelConfig.zhipu("your-key")))
 * registry.register("qwen", QwenChatModel(ChatModelConfig.qwen("your-key")))
 *
 * // 获取模型
 * val zhipu = registry.get("zhipu")
 * val qwen = registry.get("qwen")
 *
 * // 使用默认模型
 * registry.setDefault("zhipu")
 * val default = registry.getDefault()
 * ```
 */
class ProviderRegistry private constructor() {

    companion object {
        private const val TAG = "ProviderRegistry"

        @Volatile
        private var instance: ProviderRegistry? = null

        fun getInstance(): ProviderRegistry {
            return instance ?: synchronized(this) {
                instance ?: ProviderRegistry().also { instance = it }
            }
        }
    }

    // 存储所有注册的模型
    private val models = ConcurrentHashMap<String, BaseChatModel>()

    // 默认模型名称
    @Volatile
    private var defaultModelName: String? = null

    // ========================================
    // 注册/注销
    // ========================================

    /**
     * 注册模型
     *
     * @param name 模型名称
     * @param model ChatModel 实例
     */
    fun register(name: String, model: BaseChatModel) {
        models[name] = model
        Log.d(TAG, "注册模型: $name (${model.provider})")

        // 如果是第一个模型，设为默认
        if (models.size == 1) {
            defaultModelName = name
        }
    }

    /**
     * 注册并设为默认
     */
    fun registerAsDefault(name: String, model: BaseChatModel) {
        register(name, model)
        setDefault(name)
    }

    /**
     * 注销模型
     */
    fun unregister(name: String): BaseChatModel? {
        val removed = models.remove(name)
        if (removed != null) {
            Log.d(TAG, "注销模型: $name")

            // 如果移除的是默认模型，重新选择
            if (defaultModelName == name) {
                defaultModelName = models.keys.firstOrNull()
            }
        }
        return removed
    }

    /**
     * 清空所有模型
     */
    fun clear() {
        models.clear()
        defaultModelName = null
        Log.d(TAG, "清空所有模型")
    }

    // ========================================
    // 获取模型
    // ========================================

    /**
     * 获取模型
     */
    fun get(name: String): BaseChatModel? {
        return models[name]
    }

    /**
     * 获取模型（如果不存在则抛出异常）
     */
    fun getOrThrow(name: String): BaseChatModel {
        return models[name] ?: throw IllegalArgumentException("模型 '$name' 未注册")
    }

    /**
     * 获取默认模型
     */
    fun getDefault(): BaseChatModel? {
        return defaultModelName?.let { models[it] }
    }

    /**
     * 获取默认模型（如果不存在则抛出异常）
     */
    fun getDefaultOrThrow(): BaseChatModel {
        val name = defaultModelName ?: throw IllegalStateException("没有设置默认模型")
        return getOrThrow(name)
    }

    /**
     * 获取默认模型名称
     */
    fun getDefaultName(): String? = defaultModelName

    /**
     * 获取所有已注册的模型
     */
    fun getAll(): Map<String, BaseChatModel> = models.toMap()

    /**
     * 获取所有模型名称
     */
    fun getNames(): Set<String> = models.keys.toSet()

    /**
     * 检查模型是否存在
     */
    fun contains(name: String): Boolean = models.containsKey(name)

    /**
     * 获取模型数量
     */
    fun size(): Int = models.size

    // ========================================
    // 默认模型管理
    // ========================================

    /**
     * 设置默认模型
     */
    fun setDefault(name: String) {
        if (!models.containsKey(name)) {
            throw IllegalArgumentException("模型 '$name' 未注册，无法设为默认")
        }
        defaultModelName = name
        Log.d(TAG, "设置默认模型: $name")
    }

    // ========================================
    // 快捷工厂方法
    // ========================================

    /**
     * 快速创建并注册智谱模型
     */
    fun registerZhipu(
        apiKey: String,
        model: String = "glm-4.6v-flash",
        name: String = "zhipu",
        setAsDefault: Boolean = false
    ): ZhipuChatModel {
        val config = ChatModelConfig.zhipu(apiKey, model)
        val chatModel = ZhipuChatModel(config)
        if (setAsDefault) {
            registerAsDefault(name, chatModel)
        } else {
            register(name, chatModel)
        }
        return chatModel
    }

    /**
     * 快速创建并注册阿里云模型
     */
    fun registerQwen(
        apiKey: String,
        model: String = "qwen-vl-plus",
        name: String = "qwen",
        setAsDefault: Boolean = false
    ): QwenChatModel {
        val config = ChatModelConfig.qwen(apiKey, model)
        val chatModel = QwenChatModel(config)
        if (setAsDefault) {
            registerAsDefault(name, chatModel)
        } else {
            register(name, chatModel)
        }
        return chatModel
    }

    /**
     * 快速创建并注册 OpenAI 模型
     */
    fun registerOpenAI(
        apiKey: String,
        model: String = "gpt-4o-mini",
        name: String = "openai",
        setAsDefault: Boolean = false
    ): OpenAIChatModel {
        val config = ChatModelConfig.openai(apiKey, model)
        val chatModel = OpenAIChatModel(config)
        if (setAsDefault) {
            registerAsDefault(name, chatModel)
        } else {
            register(name, chatModel)
        }
        return chatModel
    }

    // ========================================
    // 工具方法
    // ========================================

    /**
     * 获取模型信息
     */
    fun getModelInfo(name: String): ModelInfo? {
        val model = models[name] ?: return null
        return ModelInfo(
            name = name,
            provider = model.provider,
            isDefault = name == defaultModelName
        )
    }

    /**
     * 获取所有模型信息
     */
    fun getAllModelInfo(): List<ModelInfo> {
        return models.map { (name, model) ->
            ModelInfo(
                name = name,
                provider = model.provider,
                isDefault = name == defaultModelName
            )
        }
    }
}

/**
 * 模型信息
 */
data class ModelInfo(
    val name: String,
    val provider: ModelProvider,
    val isDefault: Boolean
) {
    override fun toString(): String {
        val defaultTag = if (isDefault) " [默认]" else ""
        return "$name (${provider.name})$defaultTag"
    }
}

/**
 * ChatModel 工厂
 */
object ChatModelFactory {
    /**
     * 根据配置创建 ChatModel
     */
    fun create(config: ChatModelConfig, conversationConfig: ConversationConfig = ConversationConfig.DEFAULT): BaseChatModel {
        return when (config.provider) {
            ModelProvider.ZHIPU -> ZhipuChatModel(config, conversationConfig)
            ModelProvider.QWEN -> QwenChatModel(config, conversationConfig)
            ModelProvider.OPENAI -> OpenAIChatModel(config, conversationConfig)
            ModelProvider.CUSTOM -> {
                // 默认使用 OpenAI 兼容格式
                OpenAIChatModel(config, conversationConfig)
            }
        }
    }

    /**
     * 创建并注册到 Registry
     */
    fun createAndRegister(
        registry: ProviderRegistry,
        name: String,
        config: ChatModelConfig,
        conversationConfig: ConversationConfig = ConversationConfig.DEFAULT,
        setAsDefault: Boolean = false
    ): BaseChatModel {
        val model = create(config, conversationConfig)
        if (setAsDefault) {
            registry.registerAsDefault(name, model)
        } else {
            registry.register(name, model)
        }
        return model
    }
}