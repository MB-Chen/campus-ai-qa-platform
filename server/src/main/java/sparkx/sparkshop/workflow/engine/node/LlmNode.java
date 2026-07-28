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

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.input.PromptTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import sparkx.sparkshop.common.exception.BusinessException;
import sparkx.sparkshop.knowledge.infra.LLMService;
import sparkx.sparkshop.knowledge.infra.chat.LlmChatRequest;
import sparkx.sparkshop.knowledge.infra.chat.StreamCallback;
import sparkx.sparkshop.knowledge.memory.ConversationMemoryService;
import sparkx.sparkshop.workflow.engine.IWorkflowNode;
import sparkx.sparkshop.workflow.engine.WorkflowRuntimeHelper;
import sparkx.sparkshop.workflow.engine.WorkflowSseHelper;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sparkx.sparkshop.workflow.entity.WorkflowRuntimeContext;
import sparkx.sparkshop.workflow.enums.NodeTypeEnum;
import sparkx.sparkshop.workflow.mapper.WorkflowRuntimeContextMapper;
import sparkx.sparkshop.workflow.vo.EdgeVo;
import sparkx.sparkshop.workflow.vo.NextAnswerNodeVo;
import sparkx.sparkshop.workflow.vo.NodeRuntimeVo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /**
     * ★ 默认知识库问答提示词（用户未配置 userPrompt/systemMsg 时的兜底）。
     * 强约束 LLM 只依据召回片段回答、不得编造，避免被召回噪声带偏或凭训练常识自由发挥。
     * 仅在「上游有召回上下文」时启用，纯生成（无召回）场景不套用。
     */
    private static final String DEFAULT_KB_SYSTEM_PROMPT =
            "你是企业知识库助手。请严格根据下方提供的【知识库信息】回答用户问题，"
            + "禁止使用训练数据中的常识或自行编造。若知识库信息不足以回答，请直接回复"
            + "「知识库中未找到相关内容」，不要臆测或补充。回答应简洁、准确、紧扣知识库原文。";

    private static final String DEFAULT_KB_USER_TEMPLATE =
            "【知识库信息】\n{{kbContext}}\n\n【用户问题】\n{{question}}\n\n"
            + "请仅依据上方【知识库信息】回答，不要编造。";

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

    @Override
    public List<EdgeVo> handle(NodeRuntimeVo runtimeVo) {
        JSONObject nodeObject = runtimeVo.getNodeInfo().getData();
        JSONObject modelObject = nodeObject;
        long startTime = System.currentTimeMillis();

        // ★ 多入边冗余执行守卫：当多个检索节点（知识检索/知识图谱）同时连到本 LLM 时，
        //   执行器会按每条入边各执行一次本节点（见 FlowNodeParser.execute 的 nextNeedVoMap.forEach）。
        //   这里只真正跑一次，其余冗余调用直接跳过，避免重复流式输出 + 写入互相覆盖。
        //   真正的那次执行会自行汇聚所有上游分支的召回结果做 RRF 融合。
        String llmCell = runtimeVo.getNodeInfo().getId();
        if (isAlreadyExecuted(runtimeVo.getRuntimeId(), llmCell)) {
            runtimeVo.getLatch().countDown();
            return runtimeVo.getEdges().get(llmCell);
        }

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
        runtimeHelper.upsertContext(contextEntity);

        // 检测下游是否 Answer 且引用本节点输出（决定是否流推前端）
        NextAnswerNodeVo next = runtimeHelper.checkNextIsAnswerNode(runtimeVo);
        boolean needStream = next.isNodeIsAnswer() && next.getAnswerType() == 1;

        try {
            streamAnswer(contextEntity, runtimeVo, preOutput, modelObject, needStream, startTime);
        } catch (Exception e) {
            log.error("[LlmNode] LLM 节点异常: {}", e.getMessage(), e);
            throw new BusinessException("LLM 节点异常：" + e.getMessage());
        }

        runtimeVo.getLatch().countDown();
        return runtimeVo.getEdges().get(runtimeVo.getNodeInfo().getId());
    }

    /**
     * 流式生成。组装 prompt + 历史 → LLMService.streamChat → onContent 推 SSE/累计 → onComplete 落库 + 记忆。
     * <p>调试增强：把变量替换后的 renderedSystemMsg/renderedUserPrompt 存进 modelData，
     * onComplete 时基于已有 modelData 合并 token + costMs（而非整体覆盖，避免丢节点配置）。
     */
    private void streamAnswer(WorkflowRuntimeContext contextEntity, NodeRuntimeVo runtimeVo,
                              JSONObject preOutput, JSONObject modelObject, boolean needStream,
                              long startTime) {
        JSONObject modelInfo = modelObject.getJSONObject("modelInfo");
        Integer modelId = parseModelId(modelInfo == null ? null : modelInfo.getStr("modelId"));
        double temperature = modelInfo != null && modelInfo.getDouble("temperature") != null
                ? modelInfo.getDouble("temperature") : 0.3;
        int memory = modelObject.getInt("memory") != null ? modelObject.getInt("memory") : 0;

        // 组装 user prompt
        String question = preOutput.getStr("sys.question");
        String userPrompt = modelObject.getStr("userPrompt");
        // ★ 多源召回融合：当多个上游检索节点（知识检索/知识图谱）连到本 LLM 时，
        //   优先做 RRF 融合，否则退回旧逻辑（直接取首个 sys.result）。
        String kbContext = resolveKbContext(preOutput, runtimeVo);
        String finalUserPrompt = buildUserPrompt(userPrompt, question, preOutput, kbContext);

        String systemMsg = modelObject.getStr("systemMsg");
        // ★ 默认系统提示兜底：用户未配 systemMsg，且本轮有召回上下文（走知识库问答路径）时，
        //   注入 DEFAULT_KB_SYSTEM_PROMPT 强约束"只依据知识库、不得编造"，避免 LLM 自由发挥。
        //   纯生成（无召回）场景不套用，保持原行为。
        boolean hasKbContext = kbContext != null && !kbContext.isBlank();
        String effectiveSystemMsg = (systemMsg != null && !systemMsg.isBlank())
                ? systemMsg
                : (hasKbContext ? DEFAULT_KB_SYSTEM_PROMPT : null);

        // 先把渲染后的 prompt 落进 modelData（保留节点原配置，新增 renderedSystemMsg/renderedUserPrompt）
        java.util.Map<String, Object> renderExtra = new HashMap<>();
        renderExtra.put("renderedUserPrompt", finalUserPrompt);
        if (effectiveSystemMsg != null) {
            renderExtra.put("renderedSystemMsg", effectiveSystemMsg);
        }
        contextEntity.setModelData(runtimeHelper.mergeModelData(contextEntity.getModelData(), renderExtra));
        runtimeContextMapper.updateById(contextEntity);

        // 加载历史记忆：按 sessionId+cell 隔离。
        // ★ Bug A 修复：原用 runtimeId（每次对话都变，导致记忆永远读不到），改用 sessionId（前端固定传
        //   conversationId，跨轮稳定），多轮记忆才能累积。
        String memoryKey = "wf:" + runtimeVo.getSessionId() + ":" + runtimeVo.getNodeInfo().getId();
        List<ChatMessage> messages = new ArrayList<>();
        if (effectiveSystemMsg != null) {
            messages.add(SystemMessage.from(effectiveSystemMsg));
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
                    sseHelper.sendAnswer(runtimeVo.getEmitter(), runtimeVo.getRuntimeId(),
                            runtimeVo.getNodeInfo().getId(), token);
                }
            }

            @Override
            public void onComplete() {
                // 落库输出：sys.content 写入本节点分区 node.<cell>，避免并行 LLM 互相覆盖
                String updated = runtimeHelper.writeVar(contextEntity.getOutputData(),
                        runtimeVo.getNodeInfo().getId(), "sys.content", full.toString());
                contextEntity.setOutputData(updated);

                // ★ 修复：基于已有 modelData（含节点配置 + 渲染后 prompt）合并 token + costMs + note，
                //   不再整体替换（原实现会丢节点配置）。
                // 流式 SSE 未解析 usage（见 AbstractOpenAIChatClient.doStream），token 计数不可得，
                // 标注 note 避免展示误导性的 0。
                long costMs = System.currentTimeMillis() - startTime;
                java.util.Map<String, Object> doneExtra = new HashMap<>();
                doneExtra.put("inputTokenCount", 0);
                doneExtra.put("outputTokenCount", 0);
                doneExtra.put("totalTokenCount", 0);
                doneExtra.put("costMs", costMs);
                doneExtra.put("tokenNote", "流式模式未返回 token 计数");
                contextEntity.setModelData(
                        runtimeHelper.mergeModelData(contextEntity.getModelData(), doneExtra));
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
                // 异常也补 costMs，便于排查卡住的节点
                long costMs = System.currentTimeMillis() - startTime;
                contextEntity.setModelData(
                        runtimeHelper.withCostMs(contextEntity.getModelData(), costMs));
                runtimeContextMapper.updateById(contextEntity);
                sseHelper.sendError(runtimeVo.getEmitter(), "LLM 生成失败：" + error.getMessage());
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

            // ★ Bug E 诊断：流式结束后立即检测 emitter 状态
            //   若此处 emitter 已死，说明它在 LLM 推送阶段被容器/客户端关闭，
            //   后续 Answer 节点的 SSE 推送必然全部失败。
            try {
                runtimeVo.getEmitter().send(SseEmitter.event().name("_heartbeat").data("ok"));
            } catch (Exception ex) {
                log.warn("[LlmNode] ⚠️ LLM 流式结束后 emitter 已不可用（{}）。" +
                        "后续 Answer 节点推送可能失败，前端聊天可能空白。" +
                        "建议检查网络/代理/容器超时设置。", ex.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[LlmNode] 等待流式生成被中断");
        } catch (Exception e) {
            log.error("[LlmNode] streamChat 调用失败: {}", e.getMessage());
            sseHelper.sendError(runtimeVo.getEmitter(), "LLM 调用失败：" + e.getMessage());
        }
    }

    /**
     * 构建用户提示词。
     * <ul>
     *   <li>用户配了 userPrompt：按 {{var}} 替换上游输出（变量跨上游分区解析）</li>
     *   <li>没配但上游有召回上下文（kbContext，可能已是多源 RRF 融合结果）：question + 上下文</li>
     *   <li>都没：直接用原问题</li>
     * </ul>
     * 变量解析顺序：全局 sys.* → 各上游分区 node.<cell>.<field>，首个非空命中。
     */
    private String buildUserPrompt(String userPrompt, String question, JSONObject preOutput, String kbContext) {
        if (userPrompt != null && !userPrompt.isBlank()) {
            PromptTemplate tpl = PromptTemplate.from(userPrompt);
            Matcher m = VAR_PATTERN.matcher(userPrompt);
            Map<String, Object> vars = new HashMap<>();
            while (m.find()) {
                String varName = m.group(1);
                // ★ 召回类变量（sys.result）走 kbContext：它已做多源融合 + rerank，
                //   且能跨上游分区拿到全部召回（resolveVar 只看 preOutput 单分区，
                //   多入边时另一路 dataset 的召回拿不到，会被空 sys.result 命中导致上下文丢失）。
                if ("sys.result".equals(varName)) {
                    vars.put(varName, kbContext == null ? "" : kbContext);
                } else {
                    vars.put(varName, resolveVar(preOutput, varName));
                }
            }
            return tpl.apply(vars).text();
        }

        if (kbContext != null && !kbContext.isBlank()) {
            // ★ 默认知识库问答模板：结构化【知识库信息】+【用户问题】，并附"不要编造"约束
            PromptTemplate tpl = PromptTemplate.from(DEFAULT_KB_USER_TEMPLATE);
            Map<String, Object> vars = new HashMap<>();
            vars.put("question", question == null ? "" : question);
            vars.put("kbContext", kbContext);
            return tpl.apply(vars).text();
        }
        return question == null ? "" : question;
    }

    /**
     * 解析 LLM 的召回上下文：
     * <ul>
     *   <li>存在 ≥2 个上游检索源（datasets.fragments / graph.fragments）→ RRF 多源融合</li>
     *   <li>否则沿用旧逻辑（取首个上游分区的 sys.result）</li>
     * </ul>
     * <p>★ 融合后统一 rerank：当本节点配置了 rerankModelId 时，对融合（或单源）后的片段列表
     * 再做一次重排，按分数降序取 topK。这能修正「上游各检索节点 rerank 粒度/阈值不一致、
     * 或 graph 节点未 rerank」导致的噪声混入——在喂给 LLM 之前统一把关。
     */
    private String resolveKbContext(JSONObject preOutput, NodeRuntimeVo runtimeVo) {
        List<RetrieverSource> sources = collectRetrieverSources(preOutput, runtimeVo);
        List<String> passages;
        if (sources.size() >= 2) {
            log.info("[LlmNode] 检测到 {} 个上游召回源，执行 RRF 多源融合", sources.size());
            passages = rrfFuse(sources);
        } else if (sources.size() == 1) {
            // 单源：直接从该源的 fragments 取文本（collectRetrieverSources 已通过 edges
            // 扫描拿到所有上游分区，含 preOutput 里没有的另一路检索节点产出）。
            passages = new ArrayList<>();
            JSONArray frags = sources.get(0).fragments;
            for (int i = 0; i < frags.size(); i++) {
                JSONObject f = frags.getJSONObject(i);
                if (f != null) {
                    String t = f.getStr("text");
                    if (t != null && !t.isBlank()) passages.add(t);
                }
            }
        } else {
            // 兜底：没有结构化 fragments，尝试读 sys.result（兼容老数据/无 fragments 的节点）
            String single = resolveVar(preOutput, "sys.result");
            if (single == null || single.isBlank()) {
                return "";
            }
            passages = splitPassages(single);
        }
        return joinWithRerank(passages, runtimeVo);
    }

    /**
     * 收集所有上游检索源的分区（datasets.fragments / graph.fragments）。
     * 除当前 preOutput 外，还扫描所有入边对应的上游上下文，补齐并行分支（另一路检索节点）的产出。
     */
    private List<RetrieverSource> collectRetrieverSources(JSONObject preOutput, NodeRuntimeVo runtimeVo) {
        List<RetrieverSource> sources = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        collectFromPartition(preOutput, sources, seen);

        Map<String, List<EdgeVo>> edges = runtimeVo.getEdges();
        if (edges != null) {
            String llmCell = runtimeVo.getNodeInfo().getId();
            for (Map.Entry<String, List<EdgeVo>> e : edges.entrySet()) {
                String srcCell = e.getKey();
                if (e.getValue() == null) continue;
                for (EdgeVo edge : e.getValue()) {
                    if (edge.getTarget() != null && edge.getTarget().contains(llmCell)) {
                        WorkflowRuntimeContext ctx = runtimeHelper.getRuntimeContext(
                                runtimeVo.getRuntimeId(), srcCell, srcCell);
                        if (ctx != null && ctx.getOutputData() != null) {
                            collectFromPartition(JSONUtil.parseObj(ctx.getOutputData()), sources, seen);
                        }
                        break;
                    }
                }
            }
        }
        return sources;
    }

    /** 从某个 outputData 的分区里提取检索源（datasets→权重1.0，graph→权重0.5，对齐 RAG 通道权重） */
    private void collectFromPartition(JSONObject output, List<RetrieverSource> sources, Set<String> seen) {
        if (output == null) return;
        for (String key : output.keySet()) {
            if (!key.startsWith("node.")) continue;
            String cell = key.substring(5);
            if (seen.contains(cell)) continue;
            Object part = output.get(key);
            if (!(part instanceof JSONObject jo)) continue;
            JSONArray dsFrags = jo.getJSONArray("datasets.fragments");
            if (dsFrags != null && !dsFrags.isEmpty()) {
                sources.add(new RetrieverSource("dataset", dsFrags, 1.0));
                seen.add(cell);
                continue;
            }
            JSONArray gFrags = jo.getJSONArray("graph.fragments");
            if (gFrags != null && !gFrags.isEmpty()) {
                sources.add(new RetrieverSource("graph", gFrags, 0.5));
                seen.add(cell);
            }
        }
    }

    /**
     * RRF（Reciprocal Rank Fusion）多源融合：score(d) = Σ_source w_s / (k + rank_s(d))。
     * <ul>
     *   <li>rank = 片段在所属源返回列表中的名次（0-based）</li>
     *   <li>k = 20（对齐 RagProperties.Fusion.rrfK 默认，针对小候选池）</li>
     *   <li>同文本跨源累加；按总分降序截断候选池（≤40）</li>
     * </ul>
     * 返回融合后的片段列表（未 join），供上层在喂给 LLM 前做统一 rerank。
     */
    private List<String> rrfFuse(List<RetrieverSource> sources) {
        final double k = 20.0;
        Map<String, Double> scoreByText = new LinkedHashMap<>();
        for (RetrieverSource src : sources) {
            JSONArray frags = src.fragments;
            for (int i = 0; i < frags.size(); i++) {
                JSONObject f = frags.getJSONObject(i);
                if (f == null) continue;
                String text = f.getStr("text");
                if (text == null || text.isBlank()) continue;
                double contribution = src.weight / (k + i);
                scoreByText.merge(text, contribution, Double::sum);
            }
        }
        List<String> sorted = new ArrayList<>(scoreByText.keySet());
        sorted.sort(Comparator.comparingDouble((String t) -> scoreByText.getOrDefault(t, 0.0)).reversed());
        if (sorted.size() > 40) {
            sorted = sorted.subList(0, 40);
        }
        return sorted;
    }

    /**
     * 把片段列表喂给 LLM 前的最后把关：配置了 rerankModelId 时按相关度重排取 topK，
     * 否则原序拼接。query 取真实问题（上游 sys.question）。
     */
    private String joinWithRerank(List<String> passages, NodeRuntimeVo runtimeVo) {
        if (passages == null || passages.isEmpty()) {
            return "";
        }
        JSONObject nodeData = runtimeVo.getNodeInfo().getData();
        String rerankModelId = nodeData == null ? null : nodeData.getStr("rerankModelId");
        Integer rerankModelIdInt = parseModelId(rerankModelId);
        int topK = nodeData != null && nodeData.getInt("topRank") != null
                ? nodeData.getInt("topRank") : 3;
        List<String> finalPassages = passages;
        if (rerankModelIdInt != null && passages.size() > 1) {
            try {
                String question = resolveQuestionForRerank(runtimeVo);
                List<Float> scores = llmService.rerank(question, passages, rerankModelIdInt);
                if (scores != null && scores.size() == passages.size()) {
                    finalPassages = java.util.stream.IntStream.range(0, passages.size())
                            .mapToObj(i -> new java.util.AbstractMap.SimpleEntry<>(passages.get(i), scores.get(i)))
                            .sorted((a, b) -> Float.compare(b.getValue(), a.getValue()))
                            .limit(topK)
                            .map(java.util.AbstractMap.SimpleEntry::getKey)
                            .collect(java.util.stream.Collectors.toList());
                    log.info("[LlmNode] 融合后统一 rerank 完成，rerankModelId={} topK={} 命中={}",
                            rerankModelIdInt, topK, finalPassages.size());
                }
            } catch (Exception e) {
                log.warn("[LlmNode] 融合后统一 rerank 失败，回退融合原序: {}", e.getMessage());
            }
        }
        return String.join("\n", finalPassages);
    }

    /** 取 rerank 用的 query：优先上游 sys.question */
    private String resolveQuestionForRerank(NodeRuntimeVo runtimeVo) {
        try {
            JSONObject data = runtimeVo.getNodeInfo().getData();
            List<Map<String, String>> inputs = runtimeHelper.readInputList(data);
            if (!inputs.isEmpty()) {
                String srcId = inputs.get(0).get("nodeId");
                String field = inputs.get(0).get("field");
                WorkflowRuntimeContext ctx = runtimeHelper.getRuntimeContext(
                        runtimeVo.getRuntimeId(), srcId, srcId);
                if (ctx != null) {
                    String q = runtimeHelper.readVar(ctx.getOutputData(), srcId, field);
                    if (q != null && !q.isBlank()) return q;
                    q = runtimeHelper.readVar(ctx.getOutputData(), srcId, "sys.question");
                    if (q != null && !q.isBlank()) return q;
                }
            }
        } catch (Exception ignore) {
            // ignore
        }
        return "";
    }

    /** 单源 sys.result 按换行拆成片段列表（供统一 rerank） */
    private List<String> splitPassages(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String p : text.split("\n")) {
            if (p != null && !p.isBlank()) out.add(p);
        }
        return out;
    }

    /** 判断本 LLM 节点是否已执行过（(runtimeId, cell) 上下文已写入 sys.content） */
    private boolean isAlreadyExecuted(long runtimeId, String cell) {
        WorkflowRuntimeContext prior = runtimeContextMapper.selectOne(
                new LambdaQueryWrapper<WorkflowRuntimeContext>()
                        .eq(WorkflowRuntimeContext::getRuntimeId, runtimeId)
                        .eq(WorkflowRuntimeContext::getCell, cell)
                        .last("limit 1"));
        if (prior == null || prior.getOutputData() == null) return false;
        return hasSysContent(prior.getOutputData(), cell);
    }

    private boolean hasSysContent(String outputData, String cell) {
        JSONObject obj = JSONUtil.parseObj(outputData);
        JSONObject part = obj.getJSONObject("node." + cell);
        if (part == null) return false;
        String c = part.getStr("sys.content");
        return c != null && !c.isBlank();
    }

    /** 上游检索源：类型（dataset/graph）、片段列表（按返回顺序排列即名次）、融合权重 */
    private record RetrieverSource(String type, JSONArray fragments, double weight) {
    }

    /**
     * 跨分区解析变量：先取全局字段，再遍历 node.* 分区取首个命中。
     * ★ 空串/纯空白不视为命中（继续找下一个分区）——避免某个上游分区写了空 sys.result
     *   却抢先命中，导致有内容的分区被忽略（多入边召回丢失的根因之一）。
     *   召回类变量 sys.result 的解析另走 kbContext（见 buildUserPrompt）。
     */
    private String resolveVar(JSONObject preOutput, String field) {
        if (preOutput == null) return "";
        // 全局扁平字段（sys.question 等）
        Object v = preOutput.get(field);
        if (v != null && !v.toString().isBlank()) return v.toString();
        // 遍历各上游分区
        for (String key : preOutput.keySet()) {
            if (key.startsWith("node.")) {
                Object part = preOutput.get(key);
                if (part instanceof JSONObject jo) {
                    Object pv = jo.get(field);
                    if (pv != null && !pv.toString().isBlank()) return pv.toString();
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
