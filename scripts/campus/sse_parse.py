# -*- coding: utf-8 -*-
# 解析 SSE 文件，提取 content 拼成完整回答，并统计是否含 references（知识库引用）
import json, sys
full, refs_seen = [], 0
for line in open(sys.argv[1], encoding='utf-8'):
    s = line.rstrip('\n')
    if not s.startswith('data:'):
        continue
    d = s[5:].strip()
    try:
        o = json.loads(d)
    except Exception:
        continue
    if not isinstance(o, dict):
        continue
    c = o.get('content') or o.get('answer') or o.get('text')
    if isinstance(c, dict):
        c = c.get('content')
    if c:
        full.append(c)
    if o.get('references') or o.get('stageData'):
        refs_seen += 1
print('=== 完整回答 ===')
print(''.join(full))
print('\n=== 含 references/stageData 的帧数: %d ===' % refs_seen)
