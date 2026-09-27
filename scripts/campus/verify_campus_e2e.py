# -*- coding: utf-8 -*-
import json, subprocess, urllib.request, sys, io, re
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

BASE = 'http://localhost:7026'
AGENT_ID = '9af9bb79230848d39b0323eda8220b47'
CURL = r'C:\Windows\System32\curl.exe'
OUT = r'C:\A_study\NewEndWork\spark-x\sse_e2e.txt'

def get_token():
    resp = json.load(urllib.request.urlopen(BASE + '/login/captcha'))
    key = resp['data']['key']
    out = subprocess.run(['docker', 'exec', 'sparkx-redis', 'redis-cli', 'GET', 'captcha:' + key],
                         capture_output=True, text=True, encoding='utf-8', errors='replace').stdout.strip()
    pts = json.loads(json.loads(out))
    targets = [p for p in pts if p['order'] >= 0]
    targets.sort(key=lambda p: p['order'])
    points = json.dumps([{'x': p['x'], 'y': p['y']} for p in targets], ensure_ascii=False)
    body = json.dumps({'username': 'admin', 'password': '123456', 'key': key, 'captcha': points}).encode()
    r = json.load(urllib.request.urlopen(
        urllib.request.Request(BASE + '/login/doLogin', data=body, headers={'Content-Type': 'application/json'})))
    return r['data']['token']

def run_chat(token, query, conv):
    out_file = OUT
    body = json.dumps({'agentId': AGENT_ID, 'query': query, 'conversationId': conv}).encode()
    cmd = [CURL, '-N', '-s', '-X', 'POST', BASE + '/knowledge/agent/chat',
           '-H', 'Content-Type: application/json', '-H', 'token:' + token,
           '--max-time', '290', '-o', out_file, '-d', body.decode()]
    res = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8', errors='replace')
    if res.returncode != 0:
        print('[curl rc=%d] %s' % (res.returncode, res.stderr[:300]))
    # parse
    with open(out_file, encoding='utf-8', errors='replace') as f:
        raw = f.read()
    answer_parts = []
    complete_answer = None
    references = None
    for m in re.finditer(r'event:(\w+)\s*\ndata:(.*?)(?=\nevent:|\Z)', raw, re.S):
        evt, data = m.group(1), m.group(2).strip()
        try:
            obj = json.loads(data)
        except Exception:
            continue
        if evt == 'answer':
            answer_parts.append(obj.get('content', ''))
        elif evt == 'complete':
            complete_answer = obj.get('answer', '')
            references = obj.get('references', [])
    return ''.join(answer_parts), complete_answer, references, len(raw)

def main():
    token = get_token()
    print('token_len=%d' % len(token))
    questions = ['请假流程是什么']
    for i, q in enumerate(questions):
        conv = 'e2e-%d-%d' % (i, len(q))
        stream_ans, complete_ans, refs, raw_len = run_chat(token, q, conv)
        final = complete_ans if complete_ans else stream_ans
        fallback = '未来自企业知识库' in final or '未检索到' in final or '暂未找到与您问题相关' in final
        print('\n==== Q%d: %s ====' % (i + 1, q))
        print('raw_sse_bytes=%d' % raw_len)
        print('FALLBACK=%s' % fallback)
        print('references_count=%s' % (len(refs) if refs else 0))
        if refs:
            for r in refs[:5]:
                title = r.get('title') or r.get('fileName') or r.get('name') or ''
                print('   ref: %s' % str(title)[:60])
        print('ANSWER>>')
        print(final[:900])

if __name__ == '__main__':
    main()
