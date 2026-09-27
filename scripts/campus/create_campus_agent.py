# -*- coding: utf-8 -*-
import json, subprocess, urllib.request, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
BASE = 'http://localhost:7026'

def get_token():
    resp = json.load(urllib.request.urlopen(BASE+'/login/captcha'))
    key = resp['data']['key']
    out = subprocess.run(['docker','exec','sparkx-redis','redis-cli','GET','captcha:'+key],
        capture_output=True, text=True, encoding='utf-8', errors='replace').stdout.strip()
    pts = json.loads(json.loads(out))
    targets = [p for p in pts if p['order']>=0]; targets.sort(key=lambda p:p['order'])
    points = json.dumps([{'x':p['x'],'y':p['y']} for p in targets], ensure_ascii=False)
    body = json.dumps({'username':'admin','password':'123456','key':key,'captcha':points}).encode()
    return json.load(urllib.request.urlopen(
        urllib.request.Request(BASE+'/login/doLogin', data=body, headers={'Content-Type':'application/json'}))
    )['data']['token']

tok = get_token()

SYSTEM_PROMPT = """你是"校园智能问答平台"的智能助手，负责回答大学生的校园生活问题。

你会先判断用户问题的意图，然后按对应风格回答：

## 意图判断规则

1. 数据查询意图（含"课表/成绩/分数/学分/绩点/校历/放假/考试周/开学/课/考试/排名"）：
   - 你是工具查询助手，专长是查询学生个人数据
   - 回答要简洁、友好，可以加 emoji
   - 如果知识库有相关信息就引用，没有就说"暂未接入教务系统，建议登录教务系统查询"

2. 知识问答意图（含"什么是/怎么/流程/材料/要求/怎么办/注意事项/事项/介绍/定义/含义"）：
   - 你是知识问答助手，专长是基于知识库回答校园生活问题
   - 回答时必须引用信息来源（"根据校园知识库..."）
   - 如果知识库没有覆盖，如实说明"知识库暂无此信息"

3. 闲聊兜底（其他问题）：
   - 你是闲聊助手，负责通用对话
   - 回复要友好、年轻化、贴近大学生口吻

## 重要规则
- 优先基于知识库检索结果回答，不要编造信息
- 每次回答开头简要说明你判断的意图类型（如"[数据查询]"或"[知识问答]"或"[闲聊]"）"""

agent_data = {
    'name': '校园智能助手',
    'description': '校园智能问答平台 - 3 Agent 协同（数据查询/知识问答/闲聊兜底）',
    'avatar': '🎓',
    'kbMode': 'selected',
    'knowledgeBaseIds': [
        'e282cb9764a24b9d971ecac8c3d94cc6',
        '084084dd4725408e8b866cebe7e2d6c2',
        '8e3284018850435597c2aec0031a2aa1',
        '359fc277426f498ababc15824cf15bce',
        '92059eecf159402eb35229c45521adbf',
    ],
    'chatModelId': 1,
    'systemPrompt': SYSTEM_PROMPT,
    'temperature': 0.7,
    'maxTokens': 2048,
    'historyTurns': 4,
    'embeddingTopK': 5,
    'vectorThreshold': 0.5,
    'keywordThreshold': 0.3,
    'retrievalMode': 'mix',
    'rerankEnabled': 2,
    'fallbackStrategy': 'model',
    'sampleQueryEnabled': 2,
    'status': 1,
    'welcome': '你好！我是校园智能助手 🎓\n我可以帮你查询课表、成绩、校历，回答校园生活问题，或者随便聊聊。\n试试问我：选课怎么选？请假流程是什么？',
    'suggestedQuestions': ['选课怎么选', '请假流程是什么', '食堂几点开门', '奖学金怎么申请', '我下学期有什么课'],
}

body = json.dumps(agent_data, ensure_ascii=False).encode()
req = urllib.request.Request(BASE+'/knowledge/agent/add', data=body, headers={'Content-Type':'application/json','token':tok})
r = json.load(urllib.request.urlopen(req))
print(f'code={r.get("code")} msg={r.get("message","")}')
if r.get('code') == 0:
    d = r.get('data',{})
    print(f'agentId={d.get("id")} name={d.get("name")}')
else:
    print(json.dumps(r, ensure_ascii=False)[:300])
