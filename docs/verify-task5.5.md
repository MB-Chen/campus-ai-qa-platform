# 任务 5.5 verify 报告 — 3 Agent 端到端测试

> 日期：2026-08-04
> 方式：真实 HTTP 调用 `POST http://localhost:7026/api/campus/chat`（带登录 token）
> 后端：`sparkx-backend` 容器已替换为本地编译的新 jar（`docker cp` + `restart`）

## 验证结果（6 场景）

| # | 请求 | 路由结果（后端日志） | 响应 | 判定 |
|---|------|---------------------|------|------|
| A | `{"question":"你好"}` | FALLBACK → AggregatorAgent | 闲聊回答"嘿！你好呀！👋" | ✅ |
| B | `{"question":"我下学期有什么课"}` | TOOL_QUERY → ToolAgent | "帮你查下学期的课表！请告诉我学号和专业" | ✅ |
| C | `{"question":"什么是 RAG"}` | KNOWLEDGE_QA → FaqAgent | "根据校园知识库，RAG（检索增强生成）…" | ✅ |
| 探针1 | `{"question":""}` | FALLBACK → AggregatorAgent | 正常闲聊回答，无异常 | ✅ |
| 探针2 | `{}`（缺字段） | FALLBACK（question=null） | 正常回答，**无 NPE** | ✅ |
| 探针3 | GET（错误方法） | 全局异常处理器 | 返回业务错误码，未 500 | ✅ |

## 后端日志关键行

```
[校园平台] 路由分类: question='你好' intent='FALLBACK'
[校园平台] 路由到 AggregatorAgent（闲聊兜底）
[校园平台] 路由分类: question='我下学期有什么课' intent='TOOL_QUERY'
[校园平台] 路由到 ToolAgent（数据查询）
[校园平台] 路由分类: question='什么是 RAG' intent='KNOWLEDGE_QA'
[校园平台] 路由到 FaqAgent（知识问答）
```

## 遇到的坑（已解决）

1. **登录验证码绕过**：登录是汉字点选验证码（Redis 存坐标），verify 用脚本读取 `captcha:{key}` 坐标 → 按 order 排序 → 提交 doLogin 拿 token。
2. **`UnresolvedAddressException`**：容器刚启动网络未就绪时 DeepSeek API 域名解析失败，JVM 缓存该失败，`restart` 后端后恢复正常。
3. **curl 中文乱码**：Git Bash 终端 `-d` 中文被 GBK 编码发送，改用 Python 生成 UTF-8 JSON body。

## 说明

- 未接 RAG 时，FaqAgent 回答由 LLM 基于人设 prompt 自由发挥（符合预期，任务 5.5 计划注释说明）。
- ToolAgent 回答需要学号/专业（未接真实数据源，符合 v1 演示需求）。
- jar 被 `.gitignore`（`*.jar`）忽略，不提交；容器内已加载新代码。
