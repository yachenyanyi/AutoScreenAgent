package com.example.autoscreenagent.data.remote

import com.example.autoscreenagent.data.remote.model.ModelProvider
import com.example.autoscreenagent.data.remote.model.ModelProviderType
import com.example.autoscreenagent.data.remote.model.MultiProviderConfig
import kotlinx.serialization.Serializable

/**
 * Agent 配置（支持多厂商模型）
 *
 * @param modelProvider  模型厂商类型
 * @param model 模型选择（为空时使用厂商默认模型）
 * @param customModelId  用户自定义模型 ID
 * @param maxIterations 最大迭代次数
 * @param iterationDelayMs 迭代延迟（毫秒）
 * @param autoCaptureScreenshot 自动截屏
 * @param maxHistoryMessages 最大历史消息数
 * @param removeImagesAfterRounds N 轮后移除图片
 * @param providerConfigs 多厂商配置（每个厂商有独立的 enableThinking 设置）
 */
@Serializable
data class AgentConfig(
    val modelProvider: String = ModelProviderType.ZHIPU.name,  // 厂商类型
    val model: String = "",  // 当前使用的模型（为空时使用厂商默认）
    val customModelId: String = "",  // 用户自定义模型 ID
    val maxIterations: Int = 10,
    val iterationDelayMs: Long = 1000,
    val autoCaptureScreenshot: Boolean = true,
    val maxHistoryMessages: Int = 20,
    val removeImagesAfterRounds: Int = 3,
    // 多厂商配置
    val providerConfigs: MultiProviderConfig = MultiProviderConfig()
) {
    /**
     * 获取厂商类型
     */
    fun getProviderType(): ModelProviderType {
        return try {
            ModelProviderType.valueOf(modelProvider)
        } catch (e: Exception) {
            ModelProviderType.ZHIPU
        }
    }

    /**
     * 获取当前厂商的配置
     */
    fun getCurrentProviderConfig() = providerConfigs.getProviderConfig(getProviderType().toModelProvider())

    /**
     * 获取当前使用的模型（优先使用指定的模型，否则使用厂商默认）
     */
    fun getCurrentModel(): String {
        return model.ifBlank { getCurrentProviderConfig().defaultModel }
    }

    /**
     * 检查当前厂商是否已配置
     */
    fun isCurrentProviderConfigured(): Boolean {
        return providerConfigs.isConfigured(getProviderType().toModelProvider())
    }

    companion object {
        /**
         * 获取所有可用厂商
         */
        fun getAvailableProviders(): List<ModelOption> = listOf(
            ModelOption(ModelProviderType.ZHIPU.name, "智谱 GLM", "国内免费，快速"),
            ModelOption(ModelProviderType.ALIBABA.name, "阿里云百炼", "支持 Qwen3 思考模式"),
            ModelOption(ModelProviderType.OPENAI.name, "OpenAI", "GPT 系列模型"),
            ModelOption(ModelProviderType.CUSTOM.name, "自定义", "OpenAI 兼容接口")
        )

        /**
         * 获取所有可用模型（包括自定义模型）
         */
        fun getAvailableModels(provider: ModelProviderType, customModelId: String): List<ModelOption> {
            val baseModels = when (provider) {
                ModelProviderType.ZHIPU -> listOf(
                    ModelOption("glm-4-flash", "GLM-4-Flash", "免费，速度快"),
                    ModelOption("glm-4v-flash", "GLM-4V-Flash", "支持图片理解"),
                    ModelOption("glm-4.6v-flash", "GLM-4.6V-Flash", "最新版本，支持视频"),
                    ModelOption("glm-4.6", "GLM-4.6", "最新标准版，不支持图片"),
                    ModelOption("glm-4.6v", "GLM-4.6V", "最新视觉版，支持图片"),
                    ModelOption("autoglm-phone", "AutoGLM-Phone", "专为手机操作设计")
                )
                ModelProviderType.ALIBABA -> listOf(
                    ModelOption("qwen3-plus", "Qwen3-Plus", "最新思考模式"),
                    ModelOption("qwen3-vl-plus", "Qwen3-VL-Plus", "思考模式+多模态"),
                    ModelOption("qwen-vl-plus", "Qwen-VL-Plus", "多模态理解"),
                    ModelOption("qwen-plus", "Qwen-Plus", "商用文本模型"),
                    ModelOption("qwen-turbo", "Qwen-Turbo", "快速响应")
                )
                ModelProviderType.OPENAI -> listOf(
                    ModelOption("gpt-4o-mini", "GPT-4o Mini", "性价比高"),
                    ModelOption("gpt-4o", "GPT-4o", "最新旗舰"),
                    ModelOption("gpt-4-turbo", "GPT-4 Turbo", "强大推理")
                )
                ModelProviderType.CUSTOM -> emptyList()
            }
            // 如果有自定义模型 ID，添加到列表
            return if (customModelId.isNotBlank()) {
                baseModels + ModelOption(customModelId, "自定义: $customModelId", "用户自定义")
            } else {
                baseModels
            }
        }

        /**
         * 获取默认模型
         */
        fun getDefaultModel(provider: ModelProviderType): String {
            return when (provider) {
                ModelProviderType.ZHIPU -> "glm-4.6v-flash"      // 与 ProviderConfig.zhipuDefault() 一致
                ModelProviderType.ALIBABA -> "qwen-vl-plus"      // 与 ProviderConfig.qwenDefault() 一致
                ModelProviderType.OPENAI -> "gpt-4o-mini"        // 与 ProviderConfig.openaiDefault() 一致
                ModelProviderType.CUSTOM -> ""
            }
        }

        /**
         * 默认配置
         */
        fun default() = AgentConfig()
    }

    /**
     * 验证配置是否有效
     */
    fun isValid(): Boolean {
        return model.isNotBlank() &&
                maxIterations > 0 &&
                iterationDelayMs >= 0 &&
                maxHistoryMessages > 0
    }
}

/**
 * 模型选项
 */
data class ModelOption(
    val id: String,
    val name: String,
    val description: String
)
