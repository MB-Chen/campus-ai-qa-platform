# -*- coding: utf-8 -*-
# 校园平台意图分类测试：POST /api/campus/chat（CampusChatController -> CampusAgentService -> IntentRouter）
# 登录绕过逻辑与 verify_campus_chat.py 一致（captcha 必须是 JSON 字符串）。
# 每题完成后立即从 docker logs 抓取最新一条「路由分类」日志，保证问答与 intent 一一对应。
#
# 用法：
#   python campus_intent_test.py <题目JSON文件> <结果输出JSONL>
#   python campus_intent_test.py --single "问题文本"
import json, subprocess, urllib.request, sys, io, argparse, time, re

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

BASE = 'http://localhost:7026'


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
        urllib.request.Request(BASE + '/login/doLogin', data=body,
                               headers={'Content-Type': 'application/json'})))
    if r.get('code') != 0:
        raise RuntimeError('login failed: %s' % json.dumps(r, ensure_ascii=False))
    return r['data']['token']


def chat_once(token, question):
    """同步 POST /api/campus/chat，返回 (answer, elapsed_seconds)。计时口径：请求发出 -> 完整响应收到。"""
    body = json.dumps({'question': question}).encode('utf-8')
    req = urllib.request.Request(BASE + '/api/campus/chat', data=body,
                                 headers={'Content-Type': 'application/json', 'token': token})
    t0 = time.time()
    resp = urllib.request.urlopen(req, timeout=300)
    data = json.load(resp)
    elapsed = time.time() - t0
    answer = data.get('answer') if isinstance(data, dict) else str(data)
    return answer, elapsed


def last_route_log(question=''):
    """抓 docker logs 里该题最新一条「路由分类」日志，返回日志原文与解析出的 intent。
    按 question='...' 精确匹配，避免 TOOL/KNOWLEDGE 题的海量检索日志把目标行挤出 tail 窗口。"""
    out = subprocess.run(['docker', 'logs', 'sparkx-backend', '--tail', '30000'],
                         capture_output=True, text=True, encoding='utf-8', errors='replace')
    text = (out.stdout or '') + (out.stderr or '')
    needle = "question='%s'" % question
    lines = [l for l in text.splitlines() if '路由分类' in l and needle in l]
    if not lines:
        return '', ''
    line = lines[-1].strip()
    m = re.search(r"intent='([A-Z_]+)'", line)
    return line, (m.group(1) if m else '')


def run_one(question, token=None):
    token = token or get_token()
    answer, elapsed = chat_once(token, question)
    log_line, intent = last_route_log(question)
    return answer, elapsed, log_line, intent


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--single', dest='single_q', default=None)
    ap.add_argument('args', nargs='*')
    a = ap.parse_args()
    results = []
    token = get_token()
    if a.single_q:
        q = a.single_q
        print('Q:', q)
        try:
            ans, dt, log_line, intent = run_one(q, token)
            print('INTENT:', intent)
            print('T: %.2fs' % dt)
            print('A:', ans)
        except Exception as e:
            print('ERROR: %r' % e)
        return

    test_file, out_file = a.args[0], a.args[1]
    with open(test_file, encoding='utf-8') as f:
        cases = json.load(f)
    with open(out_file, 'w', encoding='utf-8') as w:
        for i, c in enumerate(cases, 1):
            q, expect = c['q'], c['expect']
            print('=== [%d/15] Q: %s (expect=%s)' % (i, q, expect), flush=True)
            rec = {'n': i, 'q': q, 'expect': expect, 'ok': False}
            try:
                ans, dt, log_line, intent = run_one(q, token)
                rec.update({'ok': True, 'intent': intent, 'elapsed': round(dt, 2),
                            'answer': ans, 'log': log_line})
                print('INTENT=%s T=%.2fs' % (intent, dt))
                print('A: %s' % (ans[:120] if ans else ans))
            except Exception as e:
                rec['error'] = repr(e)
                print('ERROR: %r' % e, flush=True)
            w.write(json.dumps(rec, ensure_ascii=False) + '\n')
            w.flush()
            results.append(rec)
            time.sleep(2)
    ok_n = sum(1 for r in results if r.get('ok'))
    print('=== done: %d/%d requests ok' % (ok_n, len(results)))


if __name__ == '__main__':
    main()
