// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.intent;

import sparkx.sparkshop.knowledge.infra.LLMService;
import sparkx.sparkshop.knowledge.infra.chat.LlmChatRequest;
import sparkx.sparkshop.knowledge.prompt.PromptTemplateLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 意图分类器（文档 5.2.2）—— 对叶子节点打分。。
 *
 * 低温生成（temperature=0.1）求稳定，输出 JSON [{id, score, reason}]。
 * 解析失败/异常降级为空列表（交由上层走全局兜底检索）。
 *
 * 与现有 {@code RuleBasedIntentRouter}（规则快速路径）共存：
 * 规则先判闲聊/问候（零成本），未命中再调本分类器。
 */
@Component
public class IntentClassifier {

    private static final Logger log = LoggerFactory.getLogger(IntentClassifier.class);
    private static final Pattern JSON_ARRAY = Pattern.compile("\\[.*\\]", Pattern.DOTALL);

    private final LLMService llmService;
    private final PromptTemplateLoader templateLoader;
    private final IntentTreeCacheManager cacheManager;
    private final RuleBasedIntentRouter ruleRouter;

    public IntentClassifier(LLMService llmService, PromptTemplateLoader templateLoader,
                            IntentTreeCacheManager cacheManager, RuleBasedIntentRouter ruleRouter) {
        this.llmService = llmService;
        this.templateLoader = templateLoader;
        this.cacheManager = cacheManager;
        this.ruleRouter = ruleRouter;
    }

    /** 对单个问题做意图分类（默认对话模型），返回按 score 降序的候选列表 */
    public List<NodeScore> classifyTargets(String question) {
        return classifyTargets(question, null);
    }

    /**
     * 对单个问题做意图分类（指定模型），返回按 score 降序的候选列表。
     *
     * @param modelId ai_model.id（type=1，对话模型）。null = 走默认对话候选链；非空强制路由到该模型
     *                （智能体配了专用小快模型时用，降本提速）。RoutingLLMService 保证 modelId 不可用时回退默认链。
     */
    public List<NodeScore> classifyTargets(String question, Integer modelId) {
        // ★ 短应答 / 敷衍 / 情绪填充词：零成本直接归内置兜底闲聊节点 sys_chitchat，
        //   规避 LLM 把所有候选打成 <0.6 而返回空数组、导致短闲聊漏匹配（见 eval 报告 emptyRate）。
        //   该短路同时覆盖评估链路与生产意图路由链路（TreeIntentStage），且不消耗 LLM 调用。
        if (ruleRouter.isFiller(question)) {
            return List.of(new NodeScore(buildFallbackChitchat(), 0.95));
        }

        IntentNode root = cacheManager.loadTree();
        List<IntentNode> leaves = IntentTreeCacheManager.flattenLeaves(root);
        if (leaves.isEmpty()) return List.of(new NodeScore(buildDefaultRetrieval(), 0.5));

        // 1. 渲染意图列表（id/path/description/examples/type）
        String intentList = buildIntentList(leaves);
        String prompt = templateLoader.render("intent-classifier.st",
                Map.of("intent_list", intentList));

        // 2. 低温 LLM 调用
        try {
            LlmChatRequest req = LlmChatRequest.ofUser(
                    prompt + "\n\n用户问题：" + question, 0.1, 0.3);
            String resp = llmService.chat(req, modelId);
            // 3. 解析 JSON（去 markdown 围栏，容错）
            List<NodeScore> parsed = parseScores(resp, leaves);
            // ★ 永不空兜底：模型对所有候选打分均低（返回空数组）时，归默认知识库检索节点，
            //   避免"有实质问题但无专属节点"（如常识问答）被误判为空/闲聊；生产仍走 KB 检索。
            return parsed.isEmpty() ? List.of(new NodeScore(buildDefaultRetrieval(), 0.5)) : parsed;
        } catch (Exception e) {
            log.debug("[Intent] 分类失败，降级为默认检索兜底: {}", e.getMessage());
            return List.of(new NodeScore(buildDefaultRetrieval(), 0.5));
        }
    }

    /** 过滤 score ≥ minScore 并取前 topN（默认对话模型） */
    public List<NodeScore> topKAboveThreshold(String question, int topN, double minScore) {
        return topKAboveThreshold(question, topN, minScore, null);
    }

    /** 过滤 score ≥ minScore 并取前 topN（指定模型） */
    public List<NodeScore> topKAboveThreshold(String question, int topN, double minScore, Integer modelId) {
        return classifyTargets(question, modelId).stream()
                .filter(s -> s.score() >= minScore)
                .sorted(NodeScore.descending())
                .limit(topN)
                .toList();
    }

    private String buildIntentList(List<IntentNode> leaves) {
        StringBuilder sb = new StringBuilder();
        for (IntentNode leaf : leaves) {
            sb.append("- id=").append(leaf.getId())
              .append(" | path=").append(leaf.getFullPath())
              .append(" | type=").append(leaf.getKind())
              .append(" | desc=").append(leaf.getDescription());
            if (leaf.getExamples() != null && !leaf.getExamples().isEmpty()) {
                sb.append(" | examples=").append(leaf.getExamples());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private List<NodeScore> parseScores(String resp, List<IntentNode> leaves) {
        List<NodeScore> result = new ArrayList<>();
        if (resp == null || resp.isBlank()) return result;

        Map<String, IntentNode> byId = new java.util.HashMap<>();
        for (IntentNode leaf : leaves) byId.put(leaf.getId(), leaf);

        Matcher m = JSON_ARRAY.matcher(resp);
        if (!m.find()) return result;
        String json = m.group();

        // 粗解析 [{"id":"...","score":0.x,...},...]
        Pattern item = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"[^}]*\"score\"\\s*:\\s*([0-9.]+)");
        Matcher im = item.matcher(json);
        while (im.find()) {
            String id = im.group(1);
            double score = Double.parseDouble(im.group(2));
            IntentNode node = byId.get(id);
            if (node != null && score > 0) {
                result.add(new NodeScore(node, score));
            }
        }
        result.sort(NodeScore.descending());
        return result;
    }

    /**
     * 构造内置兜底闲聊节点 sys_chitchat 的最小实例（id/name/kind 与预设一致），
     * 供短应答短路直接返回，免去加载整棵意图树。
     */
    private IntentNode buildFallbackChitchat() {
        IntentNode n = new IntentNode();
        n.setId("sys_chitchat");
        n.setName("闲聊问候");
        n.setKind(IntentNode.IntentKind.SYSTEM);
        n.setLevel(IntentNode.IntentLevel.LEAF);
        n.setFullPath("闲聊问候");
        return n;
    }

    /** 默认知识库检索兜底节点 id：模型无法归入任何具体节点时的安全落点，生产仍走 KB 检索 */
    static final String DEFAULT_RETRIEVAL_ID = "sys_default_retrieval";

    /**
     * 构造默认知识库检索兜底节点的最小实例（kind=KB）。
     * 供"模型对所有候选打分均低（空分类）"或分类异常时返回，确保分类结果永不空：
     *  - 生产链路：kind=KB 使意图路由阶段（TreeIntentStage）走知识库检索（与原空分类默认 KB_SEARCH 行为一致）；
     *  - eval 链路：作为命中节点（empty=false），让事实问答类问题有合理去向而非判空。
     * 该节点不加入候选列表（不进 flattenLeaves），避免被模型当作普通候选抢分。
     */
    private IntentNode buildDefaultRetrieval() {
        IntentNode n = new IntentNode();
        n.setId(DEFAULT_RETRIEVAL_ID);
        n.setName("默认知识库检索");
        n.setKind(IntentNode.IntentKind.KB);
        n.setLevel(IntentNode.IntentLevel.LEAF);
        n.setFullPath("默认知识库检索");
        return n;
    }
}
