// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.workflow.engine.node;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.input.PromptTemplate;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sparkx.sparkshop.common.exception.BusinessException;
import sparkx.sparkshop.knowledge.infra.LLMService;
import sparkx.sparkshop.knowledge.infra.chat.LlmChatRequest;
import sparkx.sparkshop.knowledge.infra.chat.StreamCallback;
import sparkx.sparkshop.knowledge.memory.ConversationMemoryService;
import sparkx.sparkshop.workflow.engine.IWorkflowNode;
import sparkx.sparkshop.workflow.engine.WorkflowRuntimeHelper;
import sparkx.sparkshop.workflow.engine.WorkflowSseHelper;
import sparkx.sparkshop.workflow.entity.WorkflowRuntimeContext;
import sparkx.sparkshop.workflow.enums.NodeTypeEnum;
import sparkx.sparkshop.workflow.mapper.WorkflowRuntimeContextMapper;
import sparkx.sparkshop.workflow.vo.EdgeVo;
import sparkx.sparkshop.workflow.vo.NextAnswerNodeVo;
import sparkx.sparkshop.workflow.vo.NodeRuntimeVo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 大模型节点。流式生成内容，token 经 SSE 推前端（仅当下游是 Answer 且引用本节点输出时）。
 * <p>
 * 适配 spark-x：用 {@link LLMService#streamChat}（享熔断/首包探测/降级链）+ {@link ConversationMemoryService} 做记忆。
 * prompt 变量替换：用户自定义 userPrompt 时按 {{var}} 替换上游输出；否则按是否有关联知识库拼接上下文。
 */
@Slf4j
@Component
public class LlmNode implements IWorkflowNode {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{(.*?)\\}\\}");

    @Autowired
    private LLMService llmService;

    @Autowired
    private ConversationMemoryService memoryService;

    @Autowired
    private WorkflowRuntimeContextMapper runtimeContextMapper;

    @Autowired
    private WorkflowRuntimeHelper runtimeHelper;

    @Autowired
    private WorkflowSseHelper sseHelper;

    @Setter
    public SseEmitter emitter;

    @Setter
    public CountDownLatch latch;

    @Override
    public List<EdgeVo> handle(NodeRuntimeVo runtimeVo) {
        JSONObject nodeObject = runtimeVo.getNodeInfo().getData();
        JSONObject modelObject = nodeObject;

        // 取首个输入变量 {nodeId, field}（多输入取第 0 个作为主问题来源）
        List<Map<String, String>> inputs = runtimeHelper.readInputList(nodeObject);
        String inputSourceId = inputs.isEmpty() ? "" : inputs.get(0).get("nodeId");
        String inputField = inputs.isEmpty() ? "sys.question" : inputs.get(0).get("field");

        // 上游上下文（并行分支时按 inputSourceId 精确匹配上游）
        WorkflowRuntimeContext context = runtimeHelper.getRuntimeContext(
                runtimeVo.getRuntimeId(), runtimeVo.getSourceId(), inputSourceId);
        if (context == null) {
            return null;
        }
        // 当前问题值（按分区读取，兼容旧扁平字段）
        String question = runtimeHelper.readVar(context.getOutputData(), inputSourceId, inputField);
        if (question == null || question.isEmpty()) {
            question = runtimeHelper.readVar(context.getOutputData(), inputSourceId, "sys.question");
        }
        JSONObject preOutput = JSONUtil.parseObj(context.getOutputData());

        // 落库本节点运行时
        WorkflowRuntimeContext contextEntity = new WorkflowRuntimeContext();
        contextEntity.setStep(context.getStep() + 1);
        contextEntity.setNodeType(NodeTypeEnum.LLM.getCode());
        contextEntity.setRuntimeId(runtimeVo.getRuntimeId());
        contextEntity.setModelData(nodeObject.toString());
        contextEntity.setOutputData(preOutput.toString());
        contextEntity.setCell(runtimeVo.getNodeInfo().getId());
        contextEntity.setCreatedAt(LocalDateTime.now());
        runtimeContextMapper.insert(contextEntity);

        // 检测下游是否 Answer 且引用本节点输出（决定是否流推前端）
        NextAnswerNodeVo next = runtimeHelper.checkNextIsAnswerNode(runtimeVo);
        boolean needStream = next.isNodeIsAnswer() && next.getAnswerType() == 1;

        try {
            streamAnswer(contextEntity, runtimeVo, preOutput, modelObject, needStream);
        } catch (Exception e) {
            log.error("[LlmNode] LLM 节点异常: {}", e.getMessage(), e);
            throw new BusinessException("LLM 节点异常：" + e.getMessage());
        }

        latch.countDown();
        return runtimeVo.getEdges().get(runtimeVo.getNodeInfo().getId());
    }

    /**
     * 流式生成。组装 prompt + 历史 → LLMService.streamChat → onContent 推 SSE/累计 → onComplete 落库 + 记忆。
     */
    private void streamAnswer(WorkflowRuntimeContext contextEntity, NodeRuntimeVo runtimeVo,
                              JSONObject preOutput, JSONObject modelObject, boolean needStream) {
        JSONObject modelInfo = modelObject.getJSONObject("modelInfo");
        Integer modelId = parseModelId(modelInfo == null ? null : modelInfo.getStr("modelId"));
        double temperature = modelInfo != null && modelInfo.getDouble("temperature") != null
                ? modelInfo.getDouble("temperature") : 0.3;
        int memory = modelObject.getInt("memory") != null ? modelObject.getInt("memory") : 0;

        // 组装 user prompt
        String question = preOutput.getStr("sys.question");
        String userPrompt = modelObject.getStr("userPrompt");
        String finalUserPrompt = buildUserPrompt(userPrompt, question, preOutput);

        String systemMsg = modelObject.getStr("systemMsg");

        // 加载历史记忆（按 cell+sessionId+userId 隔离）
        String memoryKey = "wf:" + runtimeVo.getRuntimeId() + ":" + runtimeVo.getNodeInfo().getId();
        List<ChatMessage> messages = new ArrayList<>();
        if (systemMsg != null && !systemMsg.isBlank()) {
            messages.add(SystemMessage.from(systemMsg));
        }
        if (memory > 0) {
            try {
                messages.addAll(memoryService.load(memoryKey, runtimeVo.getUserId(), memory));
            } catch (Exception e) {
                log.warn("[LlmNode] 加载记忆失败，忽略: {}", e.getMessage());
            }
        }
        messages.add(UserMessage.from(finalUserPrompt));

        // 流式调用
        StringBuilder full = new StringBuilder();
        // 流完成同步闸门：onComplete/onError 时 countDown
        final CountDownLatch doneLatch = new CountDownLatch(1);

        LlmChatRequest req = new LlmChatRequest(messages, temperature, 1.0, -1, -1, false);

        StreamCallback callback = new StreamCallback() {
            @Override
            public void onContent(String token) {
                if (token == null || token.isEmpty()) {
                    return;
                }
                full.append(token);
                if (needStream) {
                    sseHelper.sendAnswer(emitter, runtimeVo.getRuntimeId(),
                            runtimeVo.getNodeInfo().getId(), token);
                }
            }

            @Override
            public void onComplete() {
                // 落库输出：sys.content 写入本节点分区 node.<cell>，避免并行 LLM 互相覆盖
                String updated = runtimeHelper.writeVar(contextEntity.getOutputData(),
                        runtimeVo.getNodeInfo().getId(), "sys.content", full.toString());
                contextEntity.setOutputData(updated);

                JSONObject md = JSONUtil.createObj();
                md.set("inputTokenCount", 0);
                md.set("outputTokenCount", 0);
                md.set("totalTokenCount", 0);
                contextEntity.setModelData(md.toString());
                runtimeContextMapper.updateById(contextEntity);

                // 写记忆（user + ai 一轮）
                if (memory > 0) {
                    try {
                        memoryService.append(memoryKey, runtimeVo.getUserId(),
                                UserMessage.from(finalUserPrompt));
                        memoryService.append(memoryKey, runtimeVo.getUserId(),
                                AiMessage.from(full.toString()));
                    } catch (Exception e) {
                        log.warn("[LlmNode] 写记忆失败，忽略: {}", e.getMessage());
                    }
                }
                doneLatch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                log.error("[LlmNode] 流式生成异常: {}", error.getMessage());
                sseHelper.sendError(emitter, "LLM 生成失败：" + error.getMessage());
                doneLatch.countDown();
            }
        };

        // streamChat 主调用阻塞到首包探测，剩余 token 异步推送，由 doneLatch 同步等整流结束
        try {
            llmService.streamChat(req, callback, false, modelId);
            // 上限 120s 防死等（流式 LLM 单次通常远低于此）
            if (!doneLatch.await(120, TimeUnit.SECONDS)) {
                log.warn("[LlmNode] 流式生成等待超时（120s），已产出 {} 字符", full.length());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[LlmNode] 等待流式生成被中断");
        } catch (Exception e) {
            log.error("[LlmNode] streamChat 调用失败: {}", e.getMessage());
            sseHelper.sendError(emitter, "LLM 调用失败：" + e.getMessage());
        }
    }

    /**
     * 构建用户提示词。
     * <ul>
     *   <li>用户配了 userPrompt：按 {{var}} 替换上游输出（变量跨上游分区解析）</li>
     *   <li>没配但上游有知识库结果（sys.result）：question + 检索结果</li>
     *   <li>都没：直接用原问题</li>
     * </ul>
     * 变量解析顺序：全局 sys.* → 各上游分区 node.<cell>.<field>，首个非空命中。
     */
    private String buildUserPrompt(String userPrompt, String question, JSONObject preOutput) {
        if (userPrompt != null && !userPrompt.isBlank()) {
            PromptTemplate tpl = PromptTemplate.from(userPrompt);
            Matcher m = VAR_PATTERN.matcher(userPrompt);
            Map<String, Object> vars = new HashMap<>();
            while (m.find()) {
                vars.put(m.group(1), resolveVar(preOutput, m.group(1)));
            }
            return tpl.apply(vars).text();
        }

        String kbResult = resolveVar(preOutput, "sys.result");
        if (kbResult != null && !kbResult.isBlank()) {
            PromptTemplate tpl = PromptTemplate.from(
                    "{{question}}\n\n Answer using the following information:\n\n {{sys.result}}");
            Map<String, Object> vars = new HashMap<>();
            vars.put("question", question == null ? "" : question);
            vars.put("sys.result", kbResult);
            return tpl.apply(vars).text();
        }
        return question == null ? "" : question;
    }

    /**
     * 跨分区解析变量：先取全局字段，再遍历 node.* 分区取首个命中。
     */
    private String resolveVar(JSONObject preOutput, String field) {
        if (preOutput == null) return "";
        // 全局扁平字段（sys.question 等）
        Object v = preOutput.get(field);
        if (v != null) return v.toString();
        // 遍历各上游分区
        for (String key : preOutput.keySet()) {
            if (key.startsWith("node.")) {
                Object part = preOutput.get(key);
                if (part instanceof JSONObject jo) {
                    Object pv = jo.get(field);
                    if (pv != null) return pv.toString();
                }
            }
        }
        return "";
    }

    private Integer parseModelId(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
