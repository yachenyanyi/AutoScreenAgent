package com.example.autoscreenagent.data.remote.zhipu

import okhttp3.ResponseBody
import retrofit2.http.*

/**
 * 智谱 GLM API 服务接口
 *
 * 基于 OpenAI 兼容格式
 * 文档：https://open.bigmodel.cn/dev/api#chatglm
 */
interface ZhipuApiService {

    /**
     * 非流式对话补全
     *
     * @param authorization Authorization Header (Bearer token)
     * @param request 请求体
     * @return 完整响应
     */
    @POST("chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: ZhipuRequest
    ): ZhipuResponse

    /**
     * 流式对话补全
     *
     * @param authorization Authorization Header (Bearer token)
     * @param request 请求体（stream 应为 true）
     * @return SSE 流式响应体
     */
    @Streaming
    @POST("chat/completions")
    suspend fun chatCompletionStream(
        @Header("Authorization") authorization: String,
        @Body request: ZhipuRequest
    ): ResponseBody

    companion object {
        /**
         * API 基础 URL
         */
        const val BASE_URL = "https://open.bigmodel.cn/api/paas/v4/"

        /**
         * 创建 Authorization Header 值
         */
        fun createAuthHeader(apiKey: String): String = "Bearer $apiKey"
    }
}