# -*- coding: utf-8 -*-
# 端到端验证：复用 create_campus_agent.py 的登录绕过逻辑（captcha 必须是 JSON 字符串），
# 走 2.0 标准智能体对话链路 POST /knowledge/agent/chat（SSE），
# 确认「校园智能助手」能基于 5 个校园知识库检索回答。
import json, subprocess, urllib.request, sys, io, argparse
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

BASE = 'http://localhost:7026'
AGENT = '9af9bb79230848d39b0323eda8220b47'  # knowledge_agent 表「校园智能助手」

def get_token():
    resp = json.load(urllib.request.urlopen(BASE + '/login/captcha'))
    key = resp['data']['key']
    out = subprocess.run(['docker', 'exec', 'sparkx-redis', 'redis-cli', 'GET', 'captcha:' + key],
                         capture_output=True, text=True, encoding='utf-8', errors='replace').stdout.strip()
    pts = json.loads(json.loads(out))
    targets = [p for p in pts if p['order'] >= 0]
    targets.sort(key=lambda p: p['order'])
    # captcha 必须是 JSON 字符串（后端 LoginValidate.captcha 是 String）
    points = json.dumps([{'x': p['x'], 'y': p['y']} for p in targets], ensure_ascii=False)
    body = json.dumps({'username': 'admin', 'password': '123456', 'key': key, 'captcha': points}).encode()
    r = json.load(urllib.request.urlopen(
        urllib.request.Request(BASE + '/login/doLogin', data=body,
                               headers={'Content-Type': 'application/json'})))
    if r.get('code') != 0:
        raise RuntimeError('login failed: %s' % json.dumps(r, ensure_ascii=False))
    tok = r['data']['token']
    print('[get_token] ok, len=%d' % len(tok), file=sys.stderr)
    return tok

def chat(query, conv='', max_frames=40):
    token = get_token()
    body = json.dumps({'agentId': AGENT, 'query': query, 'conversationId': conv}).encode('utf-8')
    req = urllib.request.Request(BASE + '/knowledge/agent/chat', data=body,
                                 headers={'Content-Type': 'application/json', 'token': token})
    resp = urllib.request.urlopen(req, timeout=90)
    print('[chat] content-type=%s' % resp.headers.get('Content-Type'), file=sys.stderr)
    full, n = [], 0
    for line in resp:
        s = line.decode('utf-8', 'replace').rstrip('\n')
        if n < max_frames:
            print('[frame %d] %s' % (n, s[:160]), file=sys.stderr); n += 1
        if not s.startswith('data:'):
            continue
        data = s[5:].strip()
        try:
            obj = json.loads(data)
        except Exception:
            continue
        if not isinstance(obj, dict):
            continue
        c = obj.get('content') or obj.get('answer') or obj.get('text')
        if isinstance(c, dict):
            c = c.get('content')
        if c:
            full.append(c)
    return ''.join(full)

if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('query')
    ap.add_argument('--conv', default='')
    a = ap.parse_args()
    print('Q:', a.query)
    print('A:', chat(a.query, a.conv))
