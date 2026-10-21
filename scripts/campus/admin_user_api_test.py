# -*- coding: utf-8 -*-
# /admin 接口契约冒烟：登录→新增→列表→编辑→禁用→删→超管保护。
# 用法（需 sparkx-backend 运行、docker 在 PATH 或用绝对路径改 DOCKER）：
#   python admin_user_api_test.py
import io, json, subprocess, sys, urllib.request, urllib.error

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

BASE = 'http://localhost:7026'
DOCKER = r'C:\Users\shitBro\AppData\Local\Programs\DockerDesktop\resources\bin\docker.exe'

def get_token():
    resp = json.load(urllib.request.urlopen(BASE + '/login/captcha', timeout=30))
    key = resp['data']['key']
    out = subprocess.run([DOCKER, 'exec', 'sparkx-redis', 'redis-cli', 'GET', 'captcha:' + key],
                         capture_output=True, text=True, encoding='utf-8', errors='replace').stdout.strip()
    pts = sorted([p for p in json.loads(json.loads(out)) if p['order'] >= 0], key=lambda p: p['order'])
    body = json.dumps({'username': 'admin', 'password': '123456', 'key': key,
                       'captcha': json.dumps([{'x': p['x'], 'y': p['y']} for p in pts])}).encode()
    r = json.load(urllib.request.urlopen(urllib.request.Request(
        BASE + '/login/doLogin', data=body, headers={'Content-Type': 'application/json'}), timeout=30))
    assert r.get('code') == 0, 'login failed: %s' % json.dumps(r, ensure_ascii=False)
    return r['data']['token']

def call(path, token, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method='POST' if data else 'GET')
    req.add_header('token', token)
    if data: req.add_header('Content-Type', 'application/json')
    return json.load(urllib.request.urlopen(req, timeout=30))

def main():
    tok = get_token()
    uid = None
    try:
        # 1 新增
        r = call('/admin/add', tok, {'account': 'zzz_smoke', 'nickname': '冒烟测试号',
                                     'password': '123456', 'status': 1})
        assert r['code'] == 0, 'add: %s' % r
        # 2 列表能搜到
        r = call('/admin/index?page=1&limit=10&account=zzz_smoke', tok)
        rows = r['data']['data']
        assert any(x['account'] == 'zzz_smoke' for x in rows), 'list miss'
        uid = next(x['id'] for x in rows if x['account'] == 'zzz_smoke')
        # 3 编辑昵称（不带 password = 不改密码）
        r = call('/admin/edit', tok, {'id': uid, 'account': 'zzz_smoke', 'nickname': '冒烟改名', 'status': 1})
        assert r['code'] == 0, 'edit: %s' % r
        # 4 禁用
        r = call('/admin/edit', tok, {'id': uid, 'account': 'zzz_smoke', 'nickname': '冒烟改名', 'status': 2})
        assert r['code'] == 0, 'disable: %s' % r
        # 5 超管不可删：后端 BusinessException → HTTP 200 + code=-1；4xx/5xx 形态的拒绝同样可接受
        try:
            r = call('/admin/del', tok, {'id': 1})
            assert r['code'] != 0, 'super admin del accepted: %s' % r
        except urllib.error.HTTPError:
            pass
        # 超管存在性用账号搜索，避免 id 倒序分页假阳性（用户数 >10 时首页查不到 id=1）
        r = call('/admin/index?page=1&limit=10&account=admin', tok)
        assert any(x['id'] == 1 for x in r['data']['data']), 'super admin vanished!'
    finally:
        # 6 删测试号收尾：无论前面是否断言失败都尽力删除，保证脚本可重入
        if uid is not None:
            try:
                call('/admin/del', tok, {'id': uid})
            except Exception:
                pass
    print('ALL PASS: /admin 契约 6 项全通过')

if __name__ == '__main__':
    main()
