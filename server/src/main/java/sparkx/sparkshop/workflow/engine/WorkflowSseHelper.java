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
 */
@Slf4j
@Component
public class WorkflowSseHelper {

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

    /** 底层：发一个 SSE 事件 */
    public void sendEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException | IllegalStateException e) {
            // 客户端可能已断开，仅记录，不再抛
            log.warn("[WorkflowSse] 推送 {} 失败: {}", eventName, e.getMessage());
        }
    }
}
