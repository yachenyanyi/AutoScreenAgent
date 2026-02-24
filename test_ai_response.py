#!/usr/bin/env python3
"""
测试 LangGraph AI 响应
模拟 Android App 的请求逻辑，检查 AI 返回的 Finish 动作是否正确

使用方法：
1. 修改 ASSISTANT_ID 为你的助手 ID
2. 运行：python3 test_ai_response.py
"""

import requests
import json
import sys

# ==================== 配置 ====================
LANGGRAPH_BASE_URL = "http://192.168.10.12:2024"  # LangGraph Server 地址
ASSISTANT_ID = "intelligent_deep_agent_mobile"  # 助手 ID（从错误信息获取）
# =============================================

def health_check():
    """健康检查"""
    url = f"{LANGGRAPH_BASE_URL}/"
    try:
        response = requests.get(url, timeout=5)
        if response.status_code == 200:
            data = response.json()
            if data.get("ok") == True:
                print(f"✅ 服务器连接成功")
                return True
    except Exception as e:
        print(f"❌ 服务器连接失败：{e}")
    return False

def create_thread():
    """创建新线程"""
    url = f"{LANGGRAPH_BASE_URL}/threads"
    response = requests.post(url, json={})
    if response.status_code == 200:
        data = response.json()
        thread_id = data.get("thread_id")
        print(f"✅ 创建线程成功：{thread_id}")
        return thread_id
    else:
        print(f"❌ 创建线程失败：{response.status_code}")
        print(response.text)
        return None

def send_message(thread_id, message):
    """发送消息并获取流式响应"""
    url = f"{LANGGRAPH_BASE_URL}/threads/{thread_id}/runs/stream"

    payload = {
        "assistant_id": ASSISTANT_ID,
        "input": {
            "messages": [
                {"role": "human", "content": message}
            ]
        },
        "stream_mode": ["updates", "values"]
    }

    print(f"\n📤 发送消息：{message}")
    print(f"📍 URL: {url}")
    print(f"📦 请求体：{json.dumps(payload, ensure_ascii=False, indent=2)}\n")

    try:
        response = requests.post(url, json=payload, stream=True, timeout=30)
    except Exception as e:
        print(f"❌ 请求失败：{e}")
        return []

    all_events = []
    full_response_text = ""

    if response.status_code == 200:
        print("📥 接收到 SSE 流：")
        print("-" * 60)

        buffer = ""
        for line in response.iter_lines():
            if line:
                line_str = line.decode('utf-8')
                buffer += line_str + "\n"
                full_response_text += line_str

                # 空行表示一个完整的事件，或者检测到新的事件开始
                if line_str.strip() == "" or line_str.strip().startswith("event:"):
                    if buffer.strip():
                        # 如果是新的事件开始，先处理之前的 buffer
                        if line_str.strip().startswith("event:") and not buffer.strip().startswith("event:"):
                            events = parse_sse_events(buffer)
                            for event in events:
                                all_events.append(event)
                            buffer = line_str + "\n"
                        else:
                            events = parse_sse_events(buffer)
                            for event in events:
                                all_events.append(event)
                            buffer = ""

        # 处理剩余的 buffer
        if buffer.strip():
            events = parse_sse_events(buffer)
            for event in events:
                all_events.append(event)

        print("-" * 60)
        print(f"\n✅ 共收到 {len(all_events)} 个事件")

    else:
        print(f"❌ 请求失败：{response.status_code}")
        print(response.text)

    print(f"\n📝 原始响应文本（前 2000 字符）:\n{full_response_text[:2000]}...")

    return all_events

def parse_sse_events(event_text):
    """解析 SSE 事件（支持多个事件）"""
    events = []

    # 按空行分割多个事件
    raw_events = event_text.strip().split("\n\n")

    for raw_event in raw_events:
        if not raw_event.strip():
            continue

        event_type = ""
        event_data = ""

        for line in raw_event.strip().split("\n"):
            line = line.strip()
            if line.startswith("event:"):
                event_type = line.replace("event:", "").strip()
            elif line.startswith("data:"):
                event_data = line.replace("data:", "").strip()

        if event_data:
            try:
                data_json = json.loads(event_data)
                events.append({"event": event_type, "data": data_json})
            except json.JSONDecodeError as e:
                events.append({"event": event_type, "data": event_data})

    return events

def extract_actions_from_events(events):
    """从事件中提取 actions"""
    print("\n🔍 分析事件中的 actions...")

    all_actions = []

    for idx, event in enumerate(events):
        data = event.get("data", {})
        event_type = event.get("event", "unknown")

        print(f"\n  事件 {idx + 1} ({event_type}):")

        # 检查是否有 messages 数组（顶层）
        messages = data.get("messages", [])
        if messages:
            last_message = messages[-1] if messages else {}
            process_message(last_message, all_actions, "data.messages")

        # 检查 model 对象（LangGraph updates 格式）
        model = data.get("model", {})
        if model:
            model_messages = model.get("messages", [])
            if model_messages:
                process_message(model_messages[-1], all_actions, "model.messages")

        # 直接检查 data 是否有 action 或 actions
        if "action" in data:
            all_actions.append(data)
            print(f"    ✅ 从 data 找到 action: {json.dumps(data, ensure_ascii=False)}")
        if "actions" in data and isinstance(data.get("actions"), list):
            for a in data.get("actions", []):
                all_actions.append(a)
            print(f"    ✅ 从 data 找到 actions: {len(data.get('actions', []))} 个")

    # 去重
    unique_actions = []
    seen = set()
    for action in all_actions:
        key = json.dumps(action, sort_keys=True)
        if key not in seen:
            seen.add(key)
            unique_actions.append(action)

    print(f"\n📊 去重后共 {len(unique_actions)} 个 actions")
    return unique_actions

def process_message(message, all_actions, source_name):
    """处理单个消息对象"""
    if not message or not isinstance(message, dict):
        return

    # 从 additional_kwargs 中提取
    additional_kwargs = message.get("additional_kwargs", {})
    actions = additional_kwargs.get("actions", [])
    thought = additional_kwargs.get("thought", "")

    if thought:
        print(f"    Thought ({source_name}): {thought[:100]}...")
    if actions:
        all_actions.extend(actions)
        print(f"    ✅ 从 {source_name}.additional_kwargs 找到 actions: {len(actions)} 个")
        for a in actions:
            print(f"      - {json.dumps(a, ensure_ascii=False)}")

    # 从 content 中提取（可能是 JSON 字符串）
    content = message.get("content", "")
    if isinstance(content, str):
        # 尝试解析为 JSON
        try:
            content_json = json.loads(content.strip())
            if isinstance(content_json, dict):
                if "action" in content_json:
                    all_actions.append(content_json)
                    print(f"    ✅ 从 {source_name}.content 找到 action: {json.dumps(content_json, ensure_ascii=False)}")
                elif "actions" in content_json:
                    for a in content_json.get("actions", []):
                        all_actions.append(a)
                    print(f"    ✅ 从 {source_name}.content 找到 actions: {len(content_json.get('actions', []))} 个")
        except json.JSONDecodeError:
            pass

    # 直接检查 message 是否有 action
    if "action" in message:
        all_actions.append(message)
        print(f"    ✅ 从 {source_name} 找到 action: {json.dumps(message, ensure_ascii=False)}")

def check_finish_action(actions):
    """检查是否有 Finish 动作"""
    print("\n🎯 检查 Finish 动作...")

    finish_actions = []
    for action in actions:
        action_name = action.get("action", "").lower()
        metadata = action.get("_metadata", "")
        text = action.get("text") or action.get("message", "N/A")

        print(f"  动作：{action_name}, _metadata={metadata}, text/message={text}")

        if action_name == "finish":
            finish_actions.append(action)
            print(f"    ✅ FINISH 动作!")
        else:
            print(f"    ⚪ 普通动作")

    return finish_actions

def test_message(message):
    """测试单条消息"""
    print(f"\n{'='*60}")
    print(f"测试：{message}")
    print("="*60)

    thread_id = create_thread()
    if not thread_id:
        print("❌ 无法创建线程，跳过测试")
        return False

    events = send_message(thread_id, message)
    actions = extract_actions_from_events(events)
    finish_actions = check_finish_action(actions)

    if finish_actions:
        print(f"\n✅ 测试通过：AI 正确返回了 Finish 动作")
        return True
    else:
        print(f"\n❌ 测试失败：没有找到 Finish 动作")
        print(f"   所有 actions: {json.dumps(actions, ensure_ascii=False, indent=2)}")
        return False

def main():
    print("=" * 60)
    print("LangGraph AI 响应测试工具")
    print("=" * 60)
    print(f"服务器：{LANGGRAPH_BASE_URL}")
    print(f"API 文档：{LANGGRAPH_BASE_URL}/docs")
    print(f"Assistant ID: {ASSISTANT_ID}")
    print()

    # 健康检查
    if not health_check():
        print("\n❌ 服务器不可用，请检查网络连接")
        sys.exit(1)

    # 测试消息
    test_messages = [
        "你好",
        "打开微信",
    ]

    results = []
    for message in test_messages:
        result = test_message(message)
        results.append((message, result))

    # 总结
    print("\n" + "=" * 60)
    print("测试总结")
    print("=" * 60)
    for msg, passed in results:
        status = "✅ 通过" if passed else "❌ 失败"
        print(f"  {status}: {msg}")

if __name__ == "__main__":
    main()
