# AutoScreenAgent - AI 手机助手

一个基于 LangGraph 的 Android AI 助手应用，可以通过语音/文字指令自动控制手机屏幕操作。

## 功能特性

### 核心功能
- **AI 对话界面** - 自然语言交互，输入任务目标即可自动执行
- **智能屏幕操作** - 基于 AI 分析自动执行点击、滑动、输入等操作
- **实时状态反馈** - 执行过程和结果以聊天气泡形式展示
- **流式响应** - 支持 SSE 流式接收 AI 响应，实时显示 AI 思考过程

### 权限管理
- **无障碍服务** - 自动识别并操作屏幕元素（文本、ID、坐标等）
- **截屏授权** - MediaProjection 截屏，支持将屏幕图像发送给 AI 分析

### 设置与调试
- **服务器配置** - 自定义 LangGraph Server 地址和 Assistant ID
- **状态指示器** - 实时显示服务器连接、无障碍服务、截屏授权状态
- **调试菜单** - 提供快速测试和日志查看功能

## 技术架构

```
app/src/main/java/com/example/myapplication/
├── accessibility/          # 无障碍服务模块
│   ├── AccessibilityManager.kt
│   ├── MyAccessibilityService.kt
│   ├── ActionExecutor.kt
│   ├── CoordinateExecutor.kt
│   ├── ScreenshotManager.kt
│   └── ...
├── ai/                     # AI 命令处理
│   ├── AIResponseParser.kt
│   └── CommandExecutor.kt
├── data/remote/            # 数据层
│   ├── AgentConfig.kt
│   ├── LangGraphClient.kt
│   └── api/
├── ui/                     # UI 层
│   ├── screens/
│   ├── theme/
│   └── viewmodel/
├── service/                # 后台服务
└── util/                   # 工具类
```

## 快速开始

### 1. 构建与安装

```bash
# 克隆项目
git clone https://github.com/yachenyanyi/AutoScreenAgent.git

# 使用 Android Studio 打开项目
# 或命令行构建
./gradlew assembleDebug

# 安装到设备
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 2. 环境要求
- Android 8.0 (API 26) 及以上
- 需要开启无障碍服务
- 需要授权截屏权限

### 3. 配置 LangGraph Server

在设置页面配置：
- **服务器地址**: LangGraph Server 的 URL (如 `http://192.168.1.100:2024`)
- **Assistant ID**: 你的 Agent 名称 (如 `intelligent_deep_agent_mobile`)
- **超时时间**: 网络请求超时时间（秒）

### 4. 使用方法

1. 打开应用，进入设置页面配置服务器地址
2. 返回主页，开启无障碍服务和截屏授权
3. 在底部输入框输入任务目标（如"打开微信"）
4. 点击发送，AI 将自动分析并执行操作
5. 执行过程和结果会显示在聊天窗口中

## AI 命令格式

应用支持以下 AI 返回的命令格式：

| 命令类型 | 说明 | 示例 |
|---------|------|------|
| `tap_by_text` | 点击文本元素 | `{"action": "tap_by_text", "params": {"text": "发送"}}` |
| `tap_by_id` | 点击 ID 元素 | `{"action": "tap_by_id", "params": {"viewId": "com.example:id/btn_send"}}` |
| `type_text` | 输入文本 | `{"action": "type_text", "params": {"text": "你好"}}` |
| `tap` | 点击坐标 | `{"action": "tap", "element": [180, 600]}` |
| `swipe` | 滑动 | `{"action": "swipe", "params": {"direction": "up"}}` |
| `back` | 返回 | `{"action": "back"}` |
| `home` | 回到主页 | `{"action": "home"}` |
| `launch_app` | 启动应用 | `{"action": "launch_app", "text": "com.tencent.mm"}` |
| `finish` | 任务完成 | `{"action": "finish", "text": "任务已完成"}` |

## 项目说明

### 主要组件

- **MainActivity.kt** - 主界面，包含 AI 对话 UI
- **SettingsScreen.kt** - 设置页面，权限管理和服务器配置
- **LangGraphClient.kt** - LangGraph API 客户端，支持流式响应
- **AIResponseParser.kt** - AI 响应解析器，解析 JSON 格式的行动指令
- **CommandExecutor.kt** - 命令执行器，将 AI 指令转换为实际操作
- **AccessibilityManager.kt** - 无障碍服务管理器

### 通信协议

应用通过 HTTP 与 LangGraph Server 通信：
- 创建线程：`POST /threads`
- 流式发送：`POST /threads/{thread_id}/runs/stream`
- 健康检查：`GET /`

## 开发调试

### 测试 AI 响应

使用 `test_ai_response.py` 脚本测试 LangGraph Server 的响应：

```bash
python3 test_ai_response.py
```

### 调试菜单

点击顶部栏的 🔧 图标进入调试菜单，提供：
- 快速测试（Home、Back、启动应用）
- 屏幕内容获取
- 截屏测试
- 调试日志查看

## 许可证

本项目采用 MIT 许可证

## 鸣谢

- [LangGraph](https://github.com/langchain-ai/langgraph) - AI Agent 框架
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - 现代化 UI 框架
