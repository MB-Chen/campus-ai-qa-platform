# -*- coding: utf-8 -*-
"""20 并发会话同时问答实测（对应需求分析表 3-2 并发能力指标）。

用法（后端在线，docker 在 PATH 或自动兜底）：
    python scripts/campus/campus_concurrency_test.py
每题一次 /api/campus/chat 真实请求，20 线程同时发出，记录成功率与端到端耗时。
"""
import json, os, shutil, subprocess, sys, threading, time, urllib.request, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

BASE = 'http://localhost:7026'
DOCKER = shutil.which('docker') or r'C:\Users\shitBro\AppData\Local\Programs\DockerDesktop\resources\bin\docker.exe'
N = 20
QUESTIONS = [
    '学生请假需要办理什么手续', '国家奖学金的评审条件是什么', '食堂几点开门',
    '图书馆开放时间', '综合测评加分项有哪些', '期末考试作弊会怎么处理',
    '学生证丢失怎么补办', '奖助学金评定流程', '宿舍用电规定', '重修报名什么时候开始',
    '校园网怎么办理', '医保报销流程', '转专业需要什么条件', '毕设选题流程是什么',
    '图书馆借书数量上限', '挂科了能评奖学金吗', '晚归管理是怎么规定的', '校医院看病怎么报销',
    '自习室开放到几点', '你好呀',
]

def login(username, password):
    r = json.load(urllib.request.urlopen(BASE + '/login/captcha', timeout=30))
    key = r['data']['key']
    out = subprocess.run([DOCKER, 'exec', 'sparkx-redis', 'redis-cli', 'GET', 'captcha:' + key],
                         capture_output=True, text=True, encoding='utf-8', errors='replace').stdout.strip()
    pts = sorted([p for p in json.loads(json.loads(out)) if p['order'] >= 0], key=lambda p: p['order'])
    body = json.dumps({'username': username, 'password': password, 'key': key,
                       'captcha': json.dumps([{'x': q['x'], 'y': q['y']} for q in pts])}).encode()
    d = json.loads(urllib.request.urlopen(urllib.request.Request(
        BASE + '/login/doLogin', data=body, headers={'Content-Type': 'application/json'}), timeout=30).read().decode())
    return d['data']['token']

results = []
lock = threading.Lock()

def worker(i, token):
    q = QUESTIONS[i % len(QUESTIONS)]
    body = json.dumps({'question': q}).encode()
    req = urllib.request.Request(BASE + '/api/campus/chat', data=body, method='POST',
                                 headers={'Content-Type': 'application/json', 'token': token})
    t0 = time.time()
    try:
        d = json.loads(urllib.request.urlopen(req, timeout=120).read().decode())
        dt = time.time() - t0
        # /api/campus/chat 直接返回 {answer, status}（无 code/data 包装）
        ok = bool(d.get('answer')) and str(d.get('status', 'OK')).upper() == 'OK'
        with lock:
            results.append((i, q, ok, round(dt, 2), '' if ok else str(d)[:100]))
    except Exception as e:
        dt = time.time() - t0
        with lock:
            results.append((i, q, False, round(dt, 2), repr(e)[:120]))

def main():
    token = login('admin', '123456')
    print('登录成功，发起 %d 并发…' % N)
    ts = [threading.Thread(target=worker, args=(i, token)) for i in range(N)]
    wall0 = time.time()
    for t in ts: t.start()
    for t in ts: t.join()
    wall = time.time() - wall0
    results.sort(key=lambda r: r[0])
    ok_n = sum(1 for r in results if r[2])
    lat = [r[3] for r in results if r[2]]
    print('%-3s %-20s %-5s %6s' % ('#', '问题', '成败', '耗时s'))
    for i, q, ok, dt, err in results:
        print('%-3d %-20s %-5s %6.2f %s' % (i, q[:20], 'OK' if ok else 'FAIL', dt, err))
    print('=' * 46)
    print('并发规模 %d | 成功 %d/%d | 成功题平均 %.2fs | 最大 %.2fs | 整墙钟 %.2fs'
          % (N, ok_n, N, sum(lat) / len(lat) if lat else -1, max(lat) if lat else -1, wall))

if __name__ == '__main__':
    main()
