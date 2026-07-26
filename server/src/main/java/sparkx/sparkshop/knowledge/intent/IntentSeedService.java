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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import sparkx.sparkshop.knowledge.infra.LLMService;
import sparkx.sparkshop.knowledge.prompt.PromptTemplateLoader;
import sparkx.sparkshop.knowledge.validate.IntentSeedGenValidate;
import sparkx.sparkshop.knowledge.vo.IntentEvalCaseVo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 评估测试集自动生成服务。
 *
 * 一次性把全部意图节点（含 description/examples）喂给 LLM，让它为每个节点生成
 * countPerNode 条贴近真实用户、且不照抄 examples 的变形问法，直接作为评估用例。
 *
 * 之所以「一次性全局生成」而非逐节点并行：全局上下文能让模型感知所有意图边界，
 * 避免为不同节点生成出高度相似的问法，从而保证测试集的区分度。
 *
 * 解析走「正则抠 JSON 数组 + Jackson readTree」的容错套路（对齐 MultiQuestionRewriteService），
 * 只回填本地真实存在的节点 id，丢弃 LLM 编造的 id。
 */
@Service
public class IntentSeedService {

    private static final Logger log = LoggerFactory.getLogger(IntentSeedService.class);
    private static final Pattern JSON_ARRAY = Pattern.compile("\\[.*\\]", Pattern.DOTALL);
    private static final int DEFAULT_COUNT_PER_NODE = 4;

    private final LLMService llmService;
    private final PromptTemplateLoader templateLoader;
    private final IntentTreeCacheManager cacheManager;
    private final ObjectMapper mapper = new ObjectMapper();

    public IntentSeedService(LLMService llmService,
                             PromptTemplateLoader templateLoader,
                             IntentTreeCacheManager cacheManager) {
        this.llmService = llmService;
        this.templateLoader = templateLoader;
        this.cacheManager = cacheManager;
    }

    /**
     * 生成测试集。countPerNode 为空或 ≤0 时取默认值 4。
     *
     * @param req 入参（countPerNode）
     * @return 测试用例列表（可直接喂给 /eval）
     */
    public List<IntentEvalCaseVo> generate(IntentSeedGenValidate req) {
        int countPerNode = req.getCountPerNode() == null ? 0 : req.getCountPerNode();
        int n = countPerNode <= 0 ? DEFAULT_COUNT_PER_NODE : countPerNode;

        IntentNode root = cacheManager.loadTree();
        List<IntentNode> leaves = IntentTreeCacheManager.flattenLeaves(root);
        if (leaves.isEmpty()) return List.of();

        // 1. 渲染意图列表 + prompt
        String intentList = buildIntentList(leaves);
        String prompt = templateLoader.render("intent-eval-seed.st", Map.of(
                "intent_list", intentList,
                "count_per_node", String.valueOf(n)));

        // 2. 低温 LLM 调用
        String resp;
        try {
            resp = llmService.chat(prompt, 0.7, 0.9, false);
        } catch (Exception e) {
            log.warn("[IntentSeed] 生成失败: {}", e.getMessage());
            return List.of();
        }

        // 3. 解析（只认本地存在的节点 id）
        return parseCases(resp, leaves, n);
    }

    /** 拼接意图节点列表（id/path/type/desc/examples），喂给 LLM 理解意图边界 */
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

    /** 解析 LLM 返回的 JSON 数组，按节点回填，过滤编造 id 与超量条目 */
    private List<IntentEvalCaseVo> parseCases(String resp, List<IntentNode> leaves, int limitPerNode) {
        List<IntentEvalCaseVo> out = new ArrayList<>();
        if (resp == null || resp.isBlank()) return out;

        Map<String, IntentNode> byId = new HashMap<>();
        Map<String, Integer> countById = new HashMap<>();
        for (IntentNode leaf : leaves) {
            byId.put(leaf.getId(), leaf);
        }

        Matcher m = JSON_ARRAY.matcher(resp);
        if (!m.find()) return out;
        String json = m.group();

        try {
            JsonNode arr = mapper.readTree(json);
            if (!arr.isArray()) return out;
            for (JsonNode item : arr) {
                String query = item.path("query").asText("").trim();
                String nodeId = item.path("expectNodeId").asText("").trim();
                if (query.isEmpty() || nodeId.isEmpty()) continue;

                IntentNode node = byId.get(nodeId);
                if (node == null) continue;   // 丢弃 LLM 编造的 id

                // 单节点超量截断
                int cnt = countById.getOrDefault(nodeId, 0);
                if (cnt >= limitPerNode) continue;
                countById.put(nodeId, cnt + 1);

                IntentEvalCaseVo c = new IntentEvalCaseVo();
                c.setQuery(query);
                c.setExpectNodeId(nodeId);
                // 以节点实际名为准（避免 LLM 给的名字与配置不一致）
                c.setExpectNodeName(node.getName());
                c.setNote("AI 生成");
                out.add(c);
            }
        } catch (Exception e) {
            log.warn("[IntentSeed] 解析生成结果失败: {}", e.getMessage());
        }
        return out;
    }
}
