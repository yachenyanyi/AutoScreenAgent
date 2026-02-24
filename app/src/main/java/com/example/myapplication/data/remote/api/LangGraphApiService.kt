package com.example.autoscreenagent.data.remote.api

import com.example.autoscreenagent.data.remote.model.AssistantInfo
import com.example.autoscreenagent.data.remote.model.Input
import com.example.autoscreenagent.data.remote.model.Message
import com.example.autoscreenagent.data.remote.model.OkResponse
import com.example.autoscreenagent.data.remote.model.SearchRequest
import com.example.autoscreenagent.data.remote.model.StreamRunRequest
import com.example.autoscreenagent.data.remote.model.ThreadResponse
import kotlinx.serialization.json.JsonElement
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Streaming

/**
 * LangGraph API 服务接口
 *
 * 主要端点：
 * - POST /threads - 创建新线程（会话）
 * - POST /threads/{threadId}/runs/stream - 在螺纹上流式运行
 * - POST /runs/stream - 无状态流式运行
 * - GET /assistants - 获取助手列表
 * - GET /health - 健康检查
 */
interface LangGraphApiService {

    /**
     * 创建新线程（会话）
     */
    @POST("threads")
    suspend fun createThread(
        @Body metadata: Map<String, String> = emptyMap()
    ): ThreadResponse

    /**
     * 在螺纹上流式运行
     */
    @POST("threads/{threadId}/runs/stream")
    @Streaming
    fun streamRun(
        @Path("threadId") threadId: String,
        @Body request: StreamRunRequest
    ): okhttp3.Call

    /**
     * 无状态流式运行（不保存会话）
     */
    @POST("runs/stream")
    @Streaming
    fun streamRunStateless(
        @Body request: StreamRunRequest
    ): okhttp3.Call

    /**
     * 获取助手列表
     */
    @POST("assistants/search")
    suspend fun listAssistants(
        @Body request: SearchRequest = SearchRequest()
    ): List<AssistantInfo>

    /**
     * 健康检查
     */
    @GET("ok")
    suspend fun healthCheck(): OkResponse

    /**
     * 等待运行完成（一次性返回）
     */
    @POST("runs/wait")
    @Headers("Content-Type: application/json")
    suspend fun runWait(
        @Body request: StreamRunRequest
    ): Response<JsonElement>
}
