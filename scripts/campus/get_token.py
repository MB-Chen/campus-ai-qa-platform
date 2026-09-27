# -*- coding: utf-8 -*-
import json, subprocess, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
import urllib.request
BASE = 'http://localhost:7026'
resp = json.load(urllib.request.urlopen(BASE + '/login/captcha'))
key = resp['data']['key']
out = subprocess.run(['docker', 'exec', 'sparkx-redis', 'redis-cli', 'GET', 'captcha:' + key],
                     capture_output=True, text=True, encoding='utf-8', errors='replace').stdout.strip()
pts = json.loads(json.loads(out))
t = [p for p in pts if p['order'] >= 0]
t.sort(key=lambda p: p['order'])
pts_str = json.dumps([{'x': p['x'], 'y': p['y']} for p in t], ensure_ascii=False)
body = json.dumps({'username': 'admin', 'password': '123456', 'key': key, 'captcha': pts_str}).encode()
r = json.load(urllib.request.urlopen(
    urllib.request.Request(BASE + '/login/doLogin', data=body, headers={'Content-Type': 'application/json'})))
if r.get('code') != 0:
    raise SystemExit('login failed: ' + str(r))
print(r['data']['token'])
