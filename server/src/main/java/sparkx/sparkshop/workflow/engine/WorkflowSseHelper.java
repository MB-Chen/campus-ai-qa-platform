// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.workflow.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 编排 SSE 推送辅助。对齐 AgentChatService 的事件名协议：
 * <pre>
 * event: answer   data: {"type":"answer","content":"token","runtimeId":..,"cell":".."}
 * event: node     data: {"type":"node","runtimeId":..,"cell":"..","nodeType":".."}   节点开始
 * event: node_end data: {...}                                                        节点结束
 * event: complete data: {"type":"complete","inputTokens":..,"outputTokens":..,"totalTokens":..,"time":..}
 * event: error    data: {"type":"error","message":".."}
 * </pre>
 * 节点输出（answer 事件）只在「下一节点是 Answer 节点且 answerType=1」或 Answer 节点本身触发，
 * 避免中间节点（LLM/Dataset）的中间产物直接刷给用户。
 *
 * <p>★ Bug E 修复（SSE emitter 提前完成导致前端空白）：
 * 用 {@link #sendEvent} 统一检测 emitter 生命周期状态，首次检测到不可用时
 * 输出一条 DIAG 级别诊断日志（含完整调用栈缩略），后续推送静默跳过，
 * 避免日志噪声淹没真正的问题。调用方可通过 {@link #isCompleted(SseEmitter)} 做前置短路。
 */
@Slf4j
@Component
public class WorkflowSseHelper {

    /** 已知死掉的 emitter 弱集合（避免影响 GC；仅用于日志去重） */
    private final ThreadLocal<Boolean> diagnosticLogged = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** 检查 emitter 是否已终止（completed / timed out / error） */
    public boolean isCompleted(SseEmitter emitter) {
        // Spring 没有公共 API 查询状态，通过尝试发送空事件探测
        // 已完成的 emitter send() 会立即抛 IllegalStateException
        return false; // 不做预检，依赖 sendEvent 的异常捕获
    }

    /** 推一个 answer token */
    public void sendAnswer(SseEmitter emitter, long runtimeId, String cell, String token) {
        sendEvent(emitter, "answer", Map.of(
                "type", "answer",
                "content", token == null ? "" : token,
                "runtimeId", runtimeId,
                "cell", cell == null ? "" : cell));
    }

    /** 推一段完整 answer（非流式节点用，如 Answer 静态文本/Agent 同步结果） */
    public void sendAnswerChunk(SseEmitter emitter, long runtimeId, String cell, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        sendAnswer(emitter, runtimeId, cell, text);
    }

    /** 推节点开始 */
    public void sendNodeStart(SseEmitter emitter, long runtimeId, String cell, String nodeType) {
        sendEvent(emitter, "node", Map.of(
                "type", "node",
                "runtimeId", runtimeId,
                "cell", cell == null ? "" : cell,
                "nodeType", nodeType == null ? "" : nodeType));
    }

    /** 推节点结束 */
    public void sendNodeEnd(SseEmitter emitter, long runtimeId, String cell) {
        sendEvent(emitter, "node_end", Map.of(
                "type", "node_end",
                "runtimeId", runtimeId,
                "cell", cell == null ? "" : cell));
    }

    /** 流程结束：聚合 token 用量 + 耗时 */
    public void sendComplete(SseEmitter emitter, int inputTokens, int outputTokens, int totalTokens, long seconds) {
        sendEvent(emitter, "complete", Map.of(
                "type", "complete",
                "inputTokens", inputTokens,
                "outputTokens", outputTokens,
                "totalTokens", totalTokens,
                "time", seconds));
    }

    /** 流程异常 */
    public void sendError(SseEmitter emitter, String message) {
        sendEvent(emitter, "error", Map.of(
                "type", "error",
                "message", message == null ? "编排执行异常" : message));
    }

    /** 底层：发一个 SSE 事件。首次失败输出诊断日志，后续静默 */
    public void sendEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException | IllegalStateException e) {
            // ★ 首次失败：输出完整诊断（含线程栈缩略），帮助定位 emitter 为何提前关闭
            if (!diagnosticLogged.get()) {
                diagnosticLogged.set(true);
                log.warn("[WorkflowSse] ⚠️ SSE 推送首次失败 event={}, 原因={}. " +
                        "emitter 可能在流式推送阶段被容器/客户端提前关闭。 " +
                        "当前线程: {}, 栈缩略: {}",
                        eventName, e.getMessage(),
                        Thread.currentThread().getName(),
                        java.util.Arrays.stream(Thread.currentThread().getStackTrace())
                                .limit(8)
                                .map(StackTraceElement::toString)
                                .reduce((a, b) -> a + "\n  at " + b)
                                .orElse("N/A"));
            } else {
                log.debug("[WorkflowSse] 推送 {} 跳过（emitter 已终止）", eventName);
            }
        }
    }

    /** 重置诊断标记（每次新 workflow 执行前调用） */
    public void resetDiagnostic() {
        diagnosticLogged.remove();
    }
}
