# -*- coding: utf-8 -*-
# 三角色权限矩阵契约测试：admin(1)/developer(2)/user(3) 接口可达性、
# 角色分配/变更/保护、旧 token 语义。用法（后端角色版已部署）：
#   python scripts/campus/admin_role_cases_test.py
import json, shutil, subprocess, sys, time, urllib.request, urllib.error, io, http.client
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

BASE = 'http://localhost:7026'
DOCKER = shutil.which('docker') or r'C:\Users\shitBro\AppData\Local\Programs\DockerDesktop\resources\bin\docker.exe'
TS = str(int(time.time()))[-6:]

def raw_call(path, token):
    """绕过客户端 URI 归一化，用 http.client 原样发送畸形 request line（矩阵参数/点号遍历/空段）。
    正常返回 JSON dict；容器层直接拒绝时返回 {'code': 'http-<status>'}。"""
    conn = http.client.HTTPConnection('localhost', 7026, timeout=30)
    try:
        conn.request('GET', path, headers=({'token': token} if token else {}))
        resp = conn.getresponse()
        body = resp.read()
        try:
            return json.loads(body.decode('utf-8'))
        except Exception:
            return {'code': 'http-%d' % resp.status, 'message': body[:80].decode('utf-8', 'replace')}
    finally:
        conn.close()

def full(path, token, body=None):
    """返回完整响应 dict（含 data）。"""
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method='POST' if data else 'GET')
    if token: req.add_header('token', token)
    if data: req.add_header('Content-Type', 'application/json')
    try:
        return json.loads(urllib.request.urlopen(req, timeout=30).read().decode())
    except urllib.error.HTTPError as e:
        try: return json.loads(e.read().decode())
        except Exception: return {'code': 'http-' + str(e.code), 'message': str(e)}

def call(path, token, body=None):
    d = full(path, token, body)
    return d.get('code'), d.get('message'), d.get('data')

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
    data = d.get('data') or {}
    return d.get('code'), data.get('token'), data.get('userInfo')

def find_account(token, account):
    c, m, d = call('/admin/index?page=1&limit=10&account=' + account, token)
    rows = (d or {}).get('data', [])
    return rows[0] if rows else None

RESULTS = []
def case(name, cond, detail=''):
    RESULTS.append((name, bool(cond)))
    print('[%s] %s%s' % ('PASS' if cond else 'FAIL', name, ('  <- ' + str(detail)) if not cond else ''))

def main():
    ac, AT, admin_info = login('admin', '123456')
    assert ac == 0, 'admin 登录失败'
    ids = []
    try:
        # R1 建账号：显式 role=2 / 缺省
        case('R1a 建开发者', call('/admin/add', AT, {'account': 'zz_dev_' + TS, 'nickname': '测发', 'password': '123456', 'role': 2})[0] == 0)
        case('R1b 建账号(缺省角色)', call('/admin/add', AT, {'account': 'zz_usr_' + TS, 'nickname': '测用', 'password': '123456'})[0] == 0)
        du = find_account(AT, 'zz_dev_' + TS); uu = find_account(AT, 'zz_usr_' + TS)
        assert du and uu
        ids = [du['id'], uu['id']]
        case('R1c 缺省 role=3', du.get('role') == 2 and uu.get('role') == 3, '%s/%s' % (du, uu))
        c, DT, dev_info = login('zz_dev_' + TS, '123456')
        c, UT, usr_info = login('zz_usr_' + TS, '123456')
        # R2 登录签发
        case('R2a userInfo.roleId', dev_info.get('roleId') == 2 and usr_info.get('roleId') == 3, '%s/%s' % (dev_info, usr_info))
        case('R2b roleName 中文', dev_info.get('roleName') == '开发者' and usr_info.get('roleName') == '终端用户')
        case('R2c 管理员 userInfo', admin_info.get('roleId') == 1 and admin_info.get('roleName') == '管理员')
        # M 权限矩阵
        case('M1 admin 全通', call('/admin/index?page=1&limit=1', AT)[0] == 0)
        case('M2 dev 可知识库', call('/knowledge/index?page=1&limit=1', DT)[0] == 0)
        case('M3 dev 不可 /admin', call('/admin/index?page=1&limit=1', DT)[0] == 403)
        case('M4 user 可 sessions', call('/api/v1/sessions?page=1&size=1', UT)[0] == 0)
        case('M5 user 可 agent/list', call('/knowledge/agent/list', UT)[0] == 0)
        case('M6 user 不可 agent 管理', call('/knowledge/agent/index?page=1&limit=1', UT)[0] == 403)
        case('M7 user 不可 /admin', call('/admin/index?page=1&limit=1', UT)[0] == 403)
        case('M8 user 不可 ai/model', call('/ai/model/index?page=1&limit=1', UT)[0] == 403)
        case('M9 user 校园对话不被 403', call('/api/campus/chat', UT, {'question': '你好'})[0] != 403)
        # R4 变更与保护
        case('R4a 改角色生效', call('/admin/edit', AT, {'id': uu['id'], 'account': 'zz_usr_' + TS, 'nickname': '测用', 'role': 2})[0] == 0)
        c, UT2, ui2 = login('zz_usr_' + TS, '123456')
        case('R4b 重登新角色生效', ui2.get('roleId') == 2 and call('/knowledge/index?page=1&limit=1', UT2)[0] == 0, str(ui2))
        case('R4c 旧 token 旧角色仍有效', call('/api/v1/sessions?page=1&size=1', UT)[0] == 0)
        case('R4d 超管角色不可变更', call('/admin/edit', AT, {'id': 1, 'account': 'admin', 'nickname': '管理员', 'role': 3})[0] == -1)
        case('R4e 非法 role 拒绝', call('/admin/add', AT, {'account': 'zz_bad_' + TS, 'nickname': 'x', 'password': '123456', 'role': 5})[0] == -1)
        bad = find_account(AT, 'zz_bad_' + TS)
        if bad: ids.append(bad['id'])
        case('R4f 编辑不带 role 保留原值', call('/admin/edit', AT, {'id': uu['id'], 'account': 'zz_usr_' + TS, 'nickname': '测用改名'})[0] == 0
             and (find_account(AT, 'zz_usr_' + TS) or {}).get('role') == 2)
        # R5 超管禁用保护（R任务2 步骤2.0 的产物）
        case('R5 超管不可禁用', call('/admin/edit', AT, {'id': 1, 'account': 'admin', 'nickname': '管理员', 'status': 2})[0] == -1)
        # B 畸形 URI 绕过回归（C1 矩阵参数 / C2 点号遍历 / 空段）：http.client 原样发出 request line，
        # 期望被拦截器畸形 deny（403）或越权（403）或容器归一化拒绝（http-4xx），绝不能 code==0 泄露管理员数据
        case('B1 dev 矩阵参数 /admin;x=1/index 不放行', raw_call('/admin;x=1/index', DT).get('code') != 0)
        case('B2 dev 点号遍历 ../../admin/index 不放行', raw_call('/api/campus/../../admin/index', DT).get('code') != 0)
        case('B3 user 编码点号 %2e%2e/admin/index 不放行', raw_call('/api/campus/%2e%2e/admin/index', UT).get('code') != 0)
        case('B4 user 空段 //admin/index 不放行', raw_call('//admin/index', UT).get('code') != 0)
    finally:
        for i in ids: call('/admin/del', AT, {'id': i})
        print('清理 %d 个测试账号' % len(ids))
    bad = [n for n, ok in RESULTS if not ok]
    print('=' * 40)
    print('共 %d：PASS %d / FAIL %d' % (len(RESULTS), len(RESULTS) - len(bad), len(bad)))
    if bad:
        print('失败用例：', ', '.join(bad)); sys.exit(1)
    print('ALL ROLE CASES PASS')

if __name__ == '__main__':
    main()
