# AutoScreenAgent - AI 手机自动化助手

一个基于 AI 大模型的 Android 自动化助手应用，通过自然语言指令自动控制手机屏幕操作。

## 功能特性

### 多厂商 AI 支持

- **智谱 GLM** - 支持 GLM-4-Flash、GLM-4V-Flash、GLM-4.6V-Flash、AutoGLM-Phone 等模型
- **阿里云百炼** - 支持 Qwen3-Plus、Qwen3-VL-Plus、Qwen-VL-Plus 等模型
- **OpenAI** - 支持 GPT-4o、GPT-4o-mini 等模型
- **自定义接口** - 支持兼容 OpenAI 格式的自定义服务

### 智能屏幕操作

- **自然语言控制** - 输入任务目标，AI 自动分析并执行
- **无障碍 UI 分析** - 通过无障碍服务获取屏幕节点信息
- **智能定位** - 支持文本、ID、坐标多种定位方式
- **丰富工具集** - 点击、滑动、输入、启动应用等

### 工具列表

| 工具 | 功能 | 参数 |
|------|------|------|
| `tap_by_text` | 点击包含指定文本的元素 | text |
| `tap_by_id` | 通过 View ID 点击元素 | viewId |
| `tap` | 点击坐标位置 | x, y |
| `type_text` | 在当前输入框输入文本 | text |
| `swipe` | 滑动屏幕 | direction (up/down/left/right) |
| `swipe_coords` | 精确滑动 | startX, startY, endX, endY, duration |
| `longpress` | 长按 | x, y, duration |
| `launch_app` | 启动应用 | package_name |
| `back` | 返回键 | - |
| `home` | 主页键 | - |
| `get_screen_content` | 获取屏幕 UI 节点树 | - |
| `get_installed_apps` | 获取已安装应用列表 | filter, include_system, limit |

## 快速开始

### 环境要求

- Android 8.0 (API 26) 及以上
- 需要开启无障碍服务

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
   - 选择 AI 厂商（智谱/阿里云/OpenAI/自定义）
   - 配置 API Key 和选择模型

2. **开启权限**
   - 开启无障碍服务

3. **开始使用**
   - 在主页输入任务目标，如"打开微信"
   - 点击发送，AI 自动执行

## 技术架构

```
app/src/main/java/com/example/autoscreenagent/
├── MainActivity.kt                 # 入口 Activity
├── accessibility/                  # 无障碍服务模块
│   ├── MyAccessibilityService.kt   # 无障碍服务核心
│   ├── AccessibilityManager.kt     # 便捷调用接口
│   ├── ActionExecutor.kt           # 无障碍动作执行
│   ├── CoordinateExecutor.kt       # 手势坐标执行
│   ├── ScreenshotManager.kt        # 截屏管理
│   └── FloatingWindowManager.kt    # 悬浮窗管理
├── agent/                          # AI Agent 核心
│   ├── Agent.kt                    # Agent 框架（多轮对话+工具调用）
│   ├── AgentTools.kt               # 工具定义
│   ├── ToolExecutor.kt             # 工具执行器
│   └── AgentConfig.kt              # Agent 配置和系统提示词
├── ai/                             # AI 命令处理
│   ├── CommandExecutor.kt          # 执行 AI 返回的指令
│   └── ActionModels.kt             # 动作数据模型
├── data/remote/model/              # LLM API 实现
│   ├── BaseChatModel.kt            # 抽象基类（参考 LangChain）
│   ├── ZhipuChatModel.kt           # 智谱 API
│   ├── QwenChatModel.kt            # 通义千问 API
│   ├── OpenAIChatModel.kt          # OpenAI API
│   ├── ChatMessage.kt              # 消息模型
│   └── Tool.kt                     # 工具调用模型
├── data/remote/
│   └── AgentConfig.kt              # 配置管理
├── ui/
│   ├── screens/                    # Compose 屏幕
│   │   ├── ChatScreen.kt           # 聊天主界面
│   │   ├── SettingsScreen.kt       # 设置页面
│   │   ├── ModelConfigScreen.kt    # 模型配置
│   │   └── DebugMenuScreen.kt      # 调试菜单
│   ├── viewmodel/
│   │   └── AppViewModel.kt         # 应用状态管理
│   └── theme/                      # Material3 主题
└── service/
    └── ScreenshotForegroundService.kt  # 截屏前台服务
```

### 核心流程

```
用户输入目标
    ↓
发送文本给 LLM
    ↓
LLM 返回工具调用 (Tool Calling)
    ↓
执行工具（点击/滑动/输入等）
    ↓
返回结果给 LLM
    ↓
循环直至任务完成
```

### Agent 设计

Agent 采用类似 LangChain 的设计模式：

- **BaseChatModel** - 抽象基类，支持同步/流式调用、工具绑定、历史管理
- **Tool Calling** - 使用 OpenAI 兼容的工具调用格式
- **多轮对话** - 自动管理对话历史，支持上下文理解

## 配置说明

### 厂商配置

| 厂商 | API 地址 | 默认模型 |
|------|----------|----------|
| 智谱 GLM | open.bigmodel.cn | glm-4.6v-flash |
| 阿里云百炼 | dashscope.aliyuncs.com | qwen-vl-plus |
| OpenAI | api.openai.com | gpt-4o-mini |
| 自定义 | 自定义 | 自定义 |

### Agent 配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| 最大迭代次数 | Agent 执行循环的最大次数 | 10 |
| 迭代间隔 | 每轮操作之间的等待时间 | 1000ms |
| 历史消息数 | 保留的对话历史条数 | 20 |

## 权限说明

| 权限 | 用途 |
|------|------|
| 无障碍服务 | 获取屏幕 UI 信息，模拟点击、滑动、输入等操作 |
| 网络权限 | 调用 AI API |
| 前台服务 | 支持截屏功能（需单独授权） |
| 查询所有包 | 获取已安装应用列表 |

## 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose + Material3
- **架构**: MVVM (ViewModel + StateFlow)
- **网络**: OkHttp + Retrofit + Kotlinx Serialization
- **异步**: Kotlin Coroutines + Flow

## 许可证

GNU Affero General Public License v3.0 (AGPL-3.0)