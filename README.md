# AutoScreenAgent - AI 手机自动化助手

一个基于 AI 大模型的 Android 自动化助手应用，通过自然语言指令自动控制手机屏幕操作。

## ✨ 功能特性

### 🤖 多厂商 AI 支持
- **智谱 GLM** - 支持 GLM-4.6v-flash、GLM-4-Plus 等模型
- **通义千问** - 支持 Qwen-VL-Max 等视觉模型
- **OpenAI** - 支持 GPT-4o 等模型
- **自定义接口** - 支持兼容 OpenAI 格式的自定义服务

### 🎯 智能屏幕操作
- **自然语言控制** - 输入任务目标，AI 自动分析并执行
- **视觉理解** - 通过截屏分析界面内容
- **智能定位** - 支持文本、ID、坐标多种定位方式
- **丰富工具集** - 点击、滑动、输入、启动应用等

### 🔧 工具列表

| 工具 | 功能 | 参数 |
|------|------|------|
| `tap_by_text` | 点击文本 | text, exact |
| `tap_by_id` | 点击元素ID | viewId |
| `tap` | 点击坐标 | element |
| `type_text` | 输入文本 | text |
| `swipe` | 滑动 | direction |
| `longpress` | 长按 | element |
| `launch_app` | 启动应用 | packageName |
| `back` | 返回键 | - |
| `home` | 主页键 | - |
| `get_screen_content` | 获取屏幕内容 | - |
| `get_installed_apps` | 获取已安装应用 | keyword, includeSystem, limit |

## 📱 快速开始

### 环境要求
- Android 8.0 (API 26) 及以上
- 需要开启无障碍服务
- 需要授权截屏权限

### 构建与安装

```bash
# 克隆项目
git clone https://github.com/yachenyanyi/AutoScreenAgent.git

# 构建
./gradlew assembleDebug

# 安装
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 使用方法

1. **配置 AI 模型**
   - 进入设置页面
   - 选择 AI 厂商（智谱/通义/OpenAI/自定义）
   - 配置 API Key 和模型

2. **开启权限**
   - 开启无障碍服务
   - 授权截屏权限

3. **开始使用**
   - 在主页输入任务目标
   - 点击发送，AI 自动执行

## 🏗️ 技术架构

```
app/src/main/java/com/example/myapplication/
├── accessibility/          # 无障碍服务
│   ├── AccessibilityManager.kt
│   ├── MyAccessibilityService.kt
│   ├── ActionExecutor.kt
│   └── ScreenshotManager.kt
├── agent/                  # Agent 核心
│   ├── Agent.kt            # Agent 主循环
│   └── AgentTools.kt       # 工具定义
├── ai/                     # AI 模型层
│   ├── ActionModels.kt     # 模型接口
│   ├── ChatModelFactory.kt # 模型工厂
│   ├── CommandExecutor.kt  # 命令执行
│   └── model/              # 各厂商实现
│       ├── ZhipuChatModel.kt
│       ├── QwenChatModel.kt
│       └── OpenAIChatModel.kt
├── data/remote/            # 数据配置
│   └── AgentConfig.kt      # 配置管理
└── ui/                     # UI 层
    ├── screens/
    └── viewmodel/
```

### 核心流程

```
用户输入 → AI 分析 → 工具调用 → 执行操作 → 获取屏幕反馈 → 循环直至完成
```

## ⚙️ 配置说明

### 厂商配置

| 厂商 | API 地址 | 默认模型 |
|------|----------|----------|
| 智谱 GLM | open.bigmodel.cn | glm-4.6v-flash |
| 通义千问 | dashscope.aliyuncs.com | qwen-vl-max |
| OpenAI | api.openai.com | gpt-4o |
| 自定义 | 自定义 | 自定义 |

### Agent 配置

- **最大迭代次数**: Agent 执行循环的最大次数
- **迭代间隔**: 每轮操作之间的等待时间
- **历史消息数**: 保留的对话历史条数

## 🔐 权限说明

| 权限 | 用途 |
|------|------|
| 无障碍服务 | 模拟点击、滑动、输入等操作 |
| 截屏权限 | 获取屏幕图像供 AI 分析 |
| 网络权限 | 调用 AI API |

## 📄 许可证

GNU Affero General Public License v3.0 (AGPL-3.0)

## 🙏 鸣谢

- [Jetpack Compose](https://developer.android.com/jetpack/compose) - 现代化 UI 框架
- [OkHttp](https://square.github.io/okhttp/) - 网络请求库
- [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization) - JSON 序列化