package com.example.autoscreenagent.data.remote.zhipu

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 智谱 API 仪器测试（需要连接设备或模拟器）
 *
 * 运行方式：连接设备后，右键点击 -> Run
 */
@RunWith(AndroidJUnit4::class)
class

ZhipuClientInstrumentedTest {

    private val testApiKey = "2201bc125a3730d960ea65480eb0ed83.CqJExogi5MPRIsuy"

    @Test
    fun testConfigValidation() {
        val config = ZhipuConfig(apiKey = testApiKey)
        Assert.assertTrue("配置应该有效", config.isValid())
        println("✅ 配置验证通过")
    }

    @Test
    fun testTextMessage(): Unit = runBlocking {
        val config = ZhipuConfig(
            apiKey = testApiKey,
            enableThinking = true
        )
        val client = ZhipuClient(config)

        println("\n📡 发送测试消息: 你好\n")

        val result = StringBuilder()
        val reasoning = StringBuilder()

        client.sendMessage("你好，请用一句话介绍你自己")
            .collect { chunk ->
                chunk.getReasoningContent()?.let { reasoning.append(it) }
                chunk.getContent()?.let {
                    result.append(it)
                    print(it)
                }
            }

        println("\n")
        if (reasoning.isNotEmpty()) {
            println("💭 思考: ${reasoning.take(200)}...")
        }
        println("📝 回复: $result")

        Assert.assertTrue("应该有回复内容", result.isNotEmpty())
        println("\n✅ 文本消息测试通过")
    }

    @Test
    fun testImageUnderstanding(): Unit = runBlocking {
        val config = ZhipuConfig(apiKey = testApiKey)
        val client = ZhipuClient(config)

        // 使用一个公开的测试图片 URL
        val testImageUrl = "https://cdn.bigmodel.cn/static/logo/register.png"

        println("\n📡 发送图片理解请求\n")

        val result = StringBuilder()

        client.sendMessageWithImage("描述这张图片", testImageUrl)
            .collect { chunk ->
                chunk.getContent()?.let {
                    result.append(it)
                    print(it)
                }
            }

        println("\n📝 描述: $result")
        Assert.assertTrue("应该有图片描述", result.isNotEmpty())
        println("\n✅ 图片理解测试通过")
    }
}