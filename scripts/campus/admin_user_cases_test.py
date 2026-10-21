# -*- coding: utf-8 -*-
# /admin 用户管理接口测试用例集（后端契约 + 校验边界 + 鉴权行为）。
# 与 admin_user_api_test.py（冒烟：主链路通不通）互补，本脚本专测边界与负向用例。
# 用法：cd spark-x && python scripts/campus/admin_user_cases_test.py
# 依赖：sparkx-backend 运行中；DOCKER 路径按本机环境（不在 PATH）。
# 用例自管理测试账号（zz_t_<ts> 前缀 + finally 清理），可重复运行。
import json, shutil, subprocess, sys, time, urllib.error, urllib.parse, urllib.request, io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

BASE = 'http://localhost:7026'
DOCKER = shutil.which('docker') or r'C:\Users\shitBro\AppData\Local\Programs\DockerDesktop\resources\bin\docker.exe'
TS = str(int(time.time()))[-6:]  # 账号名后缀，保证可重入不撞号


def _solr_captcha_login(username, password):
    """登录并返回 (code, message, token)——验证码点位从 redis 直接读。"""
    resp = json.load(urllib.request.urlopen(BASE + '/login/captcha', timeout=30))
    key = resp['data']['key']
    out = subprocess.run([DOCKER, 'exec', 'sparkx-redis', 'redis-cli', 'GET', 'captcha:' + key],
                         capture_output=True, text=True, encoding='utf-8', errors='replace').stdout.strip()
    pts = sorted([p for p in json.loads(json.loads(out)) if p['order'] >= 0], key=lambda p: p['order'])
    body = json.dumps({'username': username, 'password': password, 'key': key,
                       'captcha': json.dumps([{'x': p['x'], 'y': p['y']} for p in pts])}).encode()
    r = json.load(urllib.request.urlopen(urllib.request.Request(
        BASE + '/login/doLogin', data=body, headers={'Content-Type': 'application/json'}), timeout=30))
    return r.get('code'), r.get('message'), (r.get('data') or {}).get('token')


def admin_token():
    code, msg, tok = _solr_captcha_login('admin', '123456')
    assert code == 0, 'admin 登录失败: %s' % msg
    return tok


def call(path, token=None, body=None):
    """返回 (code, message, data)；HTTP 层异常抛给调用方。"""
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method='POST' if data else 'GET')
    if token:
        req.add_header('token', token)
    if data:
        req.add_header('Content-Type', 'application/json')
    try:
        r = urllib.request.urlopen(req, timeout=30)
        d = json.loads(r.read().decode())
        return d.get('code'), d.get('message'), d.get('data')
    except urllib.error.HTTPError as e:
        try:
            d = json.loads(e.read().decode())
            return d.get('code', 'http-' + str(e.code)), d.get('message', str(e)), None
        except Exception:
            return 'http-' + str(e.code), str(e), None


RESULTS = []


def case(name, cond, detail=''):
    RESULTS.append((name, bool(cond), detail))
    mark = 'PASS' if cond else 'FAIL'
    print('[%s] %s%s' % (mark, name, ('  <- ' + detail) if (detail and not cond) else ''))


def main():
    T = admin_token()
    acct = 'zz_t_' + TS
    uid = None
    try:
        # ── A 组：参数校验（后端 @Valid）──────────────────────────────
        c, m, _ = call('/admin/add', T, {'account': acct, 'nickname': '', 'password': '123456', 'status': 1})
        case('A1 昵称为空被拒(400)', c == 400 and '昵称' in m, '%s/%s' % (c, m))
        c, m, _ = call('/admin/add', T, {'account': 'a', 'nickname': 'x', 'password': '123456', 'status': 1})
        case('A2 账号短于2位被拒(400)', c == 400 and '长度' in m, '%s/%s' % (c, m))
        c, m, _ = call('/admin/add', T, {'account': acct + 'x' * 40, 'nickname': 'x', 'password': '123456', 'status': 1})
        case('A3 账号长于30位被拒(400)', c == 400 and '长度' in m, '%s/%s' % (c, m))
        c, m, _ = call('/admin/add', T, {'nickname': '无账号', 'password': '123456', 'status': 1})
        case('A4 缺账号字段被拒(400)', c == 400, '%s/%s' % (c, m))

        # ── B 组：业务规则 ────────────────────────────────────────────
        c, m, _ = call('/admin/add', T, {'account': 'admin', 'nickname': 'x', 'password': '123456', 'status': 1})
        case('B1 重复账号被拒(-1 账号已存在)', c == -1 and '已存在' in m, '%s/%s' % (c, m))
        c, m, _ = call('/admin/edit', T, {'id': 999999, 'account': 'ghost', 'nickname': '幽灵', 'status': 1})
        case('B2 编辑不存在账号被拒(-1)', c == -1 and '不存在' in m, '%s/%s' % (c, m))
        c, m, _ = call('/admin/del', T, {'id': 1})
        case('B3 删除超管被拒(非0)', c != 0, '%s/%s' % (c, m))

        # ── C 组：主链路（新增→查询→禁用→启用→删除）──────────────────
        c, m, _ = call('/admin/add', T, {'account': acct, 'nickname': '用例号', 'password': 'abc12345', 'status': 1})
        case('C1 新增成功', c == 0, '%s/%s' % (c, m))
        c, m, d = call('/admin/index?page=1&limit=10&account=' + acct, T)
        rows = (d or {}).get('data', [])
        found = [r for r in rows if r['account'] == acct]
        uid = found[0]['id'] if found else None
        case('C2 列表按账号精确搜索命中', bool(found), '%s' % rows)
        case('C3 新用户初始状态正常且无登录时间', bool(found) and found[0]['status'] == 1 and not found[0].get('lastLoginTime'))

        c, m, _ = call('/admin/edit', T, {'id': uid, 'account': acct, 'nickname': '用例号改名', 'status': 1})
        case('C4 编辑改昵称成功', c == 0, '%s/%s' % (c, m))
        c, m, d = call('/admin/index?page=1&limit=10&account=' + acct, T)
        case('C5 编辑后列表反映新昵称', (d.get('data') or [{}])[0].get('nickname') == '用例号改名', '%s' % d)

        c, m, _ = call('/admin/edit', T, {'id': uid, 'account': acct, 'nickname': '用例号改名', 'status': 2})
        case('C6 禁用成功', c == 0, '%s/%s' % (c, m))

        # ── D 组：登录鉴权行为（此时账号已被 C6 置为禁用）──────────────
        c, m, _ = _solr_captcha_login(acct, 'abc12345')
        case('D1 禁用状态登录被拒(该账号已被禁用)', c == -1 and '禁用' in m, '%s/%s' % (c, m))
        c, m, _ = call('/admin/edit', T, {'id': uid, 'account': acct, 'nickname': '用例号改名', 'status': 1})
        case('D2 重新启用成功', c == 0, '%s/%s' % (c, m))
        c, m, _ = _solr_captcha_login(acct, 'abc12345')
        case('D3 启用后可用初始密码登录', c == 0 and m == 'success', '%s/%s' % (c, m))
        c, m, _ = _solr_captcha_login(acct, 'wrong-pass')
        case('D4 密码错误被拒', c != 0, '%s/%s' % (c, m))
        c, m, d = call('/admin/index?page=1&limit=10&account=' + acct, T)
        row = (d.get('data') or [{}])[0]
        case('D5 登录成功后 lastLoginTime 写入', bool(row.get('lastLoginTime')), '%s' % row)

        # ── E 组：鉴权拦截 ────────────────────────────────────────────
        c, m, _ = call('/admin/index?page=1&limit=10')
        case('E1 无 token 访问被拒(912)', c == 912, '%s/%s' % (c, m))
        c, m, _ = call('/admin/index?page=1&limit=10', 'bad.token.xxx')
        case('E2 伪造 token 被拒(912)', c == 912, '%s/%s' % (c, m))

        # ── F 组：列表完整性 ──────────────────────────────────────────
        c, m, d = call('/admin/index?page=1&limit=10&nickname=' + urllib.parse.quote('不存在xyz'), T)
        case('F1 无匹配搜索返回空列表', c == 0 and (d.get('data') == [] or d.get('data') is None), '%s' % d)
        c, m, d = call('/admin/index?page=1&limit=1', T)
        case('F2 分页 limit=1 生效且 total 真实', c == 0 and len(d.get('data', [])) == 1 and d.get('total', 0) >= 1, '%s' % d)
    finally:
        if uid:
            call('/admin/del', T, {'id': uid})
        print('\n清理测试账号 %s%s' % (acct, '（已删）' if uid else '（未创建）'))

    failed = [n for n, ok, _ in RESULTS if not ok]
    print('=' * 46)
    print('共 %d 个用例：PASS %d / FAIL %d' % (len(RESULTS), len(RESULTS) - len(failed), len(failed)))
    if failed:
        print('失败用例：', ', '.join(failed))
        sys.exit(1)
    print('ALL CASES PASS')


if __name__ == '__main__':
    main()
