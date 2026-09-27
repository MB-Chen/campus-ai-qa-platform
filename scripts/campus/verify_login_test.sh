#!/usr/bin/env bash
BASE=http://localhost:7026
AGENT=9af9bb79230848d39b0323eda8220b47
PY=/c/Users/shitBro/.workbuddy/binaries/python/versions/3.13.12/python.exe
TOKEN=$("$PY" get_token.py)
echo "TOKEN_LEN=${#TOKEN}"
echo "=== SSE (curl -N, max 90s) ==="
curl -N -X POST "$BASE/knowledge/agent/chat" \
  -H 'Content-Type: application/json' \
  -H "token: $TOKEN" \
  -d "{\"agentId\":\"$AGENT\",\"query\":\"请假流程是什么\"}" \
  --max-time 90
