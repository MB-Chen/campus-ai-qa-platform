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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sparkx.sparkshop.knowledge.infra.LLMService;
import sparkx.sparkshop.knowledge.prompt.PromptTemplateLoader;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模糊查询澄清器 —— 补齐 {@link IntentGuidanceService} 的盲区。
 *
 * <p>{@code IntentGuidanceService} 仅在「候选≥2 且分数接近」时反问澄清，无法覆盖：
 * <ul>
 *   <li>0 候选：问题太笼统，意图分类一个都没匹配上</li>
 *   <li>1 个低分候选：勉强命中但置信度不足</li>
 * </ul>
 * 本澄清器对这类「候选不足」的场景，调 LLM 判定：
 * <ul>
 *   <li>chitchat（闲聊）→ 放行，让大模型自由回答</li>
 *   <li>clear（明确）→ 放行，走正常检索</li>
 *   <li>vague（模糊）→ 反问澄清，列出最接近的意图方向</li>
 * </ul>
 *
 * <p>降级倾向：LLM 异常时默认 CLEAR（放行），避免因判定服务故障导致所有问题都被拦截反问。
 */
@Component
public class VagueQueryClarifier {

    private static final Logger log = LoggerFactory.getLogger(VagueQueryClarifier.class);

    /** 列入澄清选项的最大意图数 */
    private static final int MAX_HINT_NODES = 5;

    private static final Pattern ACTION_PATTERN =
            Pattern.compile("\"action\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern HINT_PATTERN =
            Pattern.compile("\"hint\"\\s*:\\s*\"([^\"]*)\"");

    private final LLMService llmService;
    private final PromptTemplateLoader templateLoader;
    private final IntentTreeCacheManager cacheManager;

    public VagueQueryClarifier(LLMService llmService,
                               PromptTemplateLoader templateLoader,
                               IntentTreeCacheManager cacheManager) {
        this.llmService = llmService;
        this.templateLoader = templateLoader;
        this.cacheManager = cacheManager;
    }

    /**
     * 判定模糊查询是否需要反问澄清。
     *
     * @param query      用户原始问题
     * @param subIntents 意图分类候选（可能为空或不足 2 个）
     * @return 澄清决策（action + 话术）
     */
    public ClarifyDecision clarify(String query, List<NodeScore> subIntents) {
        try {
            String system = templateLoader.render("vague-query-clarify.st", Map.of());
            String resp = llmService.chat(system + "\n\n用户问题：" + query, 0.1, 0.3, false);
            String action = extractAction(resp);
            String hint = extractHint(resp);

            if ("vague".equalsIgnoreCase(action)) {
                String prompt = buildClarifyPrompt(query, subIntents, hint);
                return ClarifyDecision.vague(prompt);
            }
            if ("chitchat".equalsIgnoreCase(action)) {
                return ClarifyDecision.chitchat();
            }
            // clear 或无法识别 → 放行
            return ClarifyDecision.clear();
        } catch (Exception e) {
            log.debug("[VagueClarify] 判定失败，默认放行: {}", e.getMessage());
            // 降级放行，避免判定服务故障导致全局拦截
            return ClarifyDecision.clear();
        }
    }

    /** 从 LLM 响应里提取 action 字段 */
    private String extractAction(String resp) {
        if (resp == null) return "clear";
        Matcher m = ACTION_PATTERN.matcher(resp);
        return m.find() ? m.group(1) : "clear";
    }

    /** 从 LLM 响应里提取 hint 字段（vague 时的引导语） */
    private String extractHint(String resp) {
        if (resp == null) return "";
        Matcher m = HINT_PATTERN.matcher(resp);
        return m.find() ? m.group(1) : "";
    }

    /**
     * 构造澄清话术：引导语 + 最接近的意图方向列表。
     *
     * <p>意图来源优先级：
     * <ol>
     *   <li>subIntents 里已有的候选（即使低分，也是模型认为最接近的）</li>
     *   <li>subIntents 为空时，从意图树取前 N 个节点作为「我能帮你查这些」</li>
     * </ol>
     */
    private String buildClarifyPrompt(String query, List<NodeScore> subIntents, String hint) {
        StringBuilder sb = new StringBuilder();
        // 引导语：优先用 LLM 生成的 hint，否则用默认
        if (hint != null && !hint.isBlank()) {
            sb.append(hint).append("\n\n");
        } else {
            sb.append("您的问题我还不太确定具体想了解什么，可以补充一下吗？\n\n");
        }

        // 列出意图方向
        List<String> options = collectHintOptions(subIntents);
        if (!options.isEmpty()) {
            sb.append("我目前可以帮您处理以下方向：\n");
            for (int i = 0; i < options.size(); i++) {
                sb.append(i + 1).append(". ").append(options.get(i)).append("\n");
            }
            sb.append("\n您可以直接回复数字，或用一句话描述您的需求。");
        } else {
            // 意图树也为空（未配置任何意图）
            sb.append("请尽量具体地描述您的问题，例如「怎么请假」「报销流程」等。");
        }
        return sb.toString();
    }

    /** 收集澄清选项：优先用已有候选，否则从意图树取前 N 个 */
    private List<String> collectHintOptions(List<NodeScore> subIntents) {
        // 1. 已有候选（即使低分），按分数降序取前 N
        if (subIntents != null && !subIntents.isEmpty()) {
            return subIntents.stream()
                    .sorted(NodeScore.descending())
                    .limit(MAX_HINT_NODES)
                    .map(s -> s.node().getName())
                    .filter(n -> n != null && !n.isBlank())
                    .toList();
        }
        // 2. 无候选 → 从意图树取所有叶子节点的前 N 个
        try {
            IntentNode root = cacheManager.loadTree();
            return IntentTreeCacheManager.flattenLeaves(root).stream()
                    .limit(MAX_HINT_NODES)
                    .map(IntentNode::getName)
                    .filter(n -> n != null && !n.isBlank())
                    .toList();
        } catch (Exception e) {
            log.debug("[VagueClarify] 加载意图树失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 澄清决策。
     *
     * <p>action 含义：
     * <ul>
     *   <li>{@link #CLEAR}：放行，走正常检索流程</li>
     *   <li>{@link #CHITCHAT}：闲聊，放行让大模型自由回答</li>
     *   <li>{@link #VAGUE}：模糊，用 prompt 反问澄清（需短路）</li>
     * </ul>
     */
    public record ClarifyDecision(Action action, String prompt) {
        public enum Action { CLEAR, CHITCHAT, VAGUE }

        public static ClarifyDecision clear() { return new ClarifyDecision(Action.CLEAR, null); }
        public static ClarifyDecision chitchat() { return new ClarifyDecision(Action.CHITCHAT, null); }
        public static ClarifyDecision vague(String prompt) { return new ClarifyDecision(Action.VAGUE, prompt); }

        public boolean isVague() { return action == Action.VAGUE; }
        public boolean isChitchat() { return action == Action.CHITCHAT; }
    }
}
