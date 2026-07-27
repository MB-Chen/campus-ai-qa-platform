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
import sparkx.sparkshop.knowledge.prompt.PromptTemplateLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 歧义 LLM 二次确认（文档 5.2.4）—— 边界 case 调 LLM 判定品类歧义。
 *
 * 仅在分数比处于 [skipThreshold, threshold) 边界区间时调用。
 * ★ 降级倾向：解析失败默认判歧义（宁可多问一句澄清，避免答错）。
 */
@Component
public class AmbiguityChecker {

    private static final Logger log = LoggerFactory.getLogger(AmbiguityChecker.class);

    private final LLMService llmService;
    private final PromptTemplateLoader templateLoader;

    public AmbiguityChecker(LLMService llmService, PromptTemplateLoader templateLoader) {
        this.llmService = llmService;
        this.templateLoader = templateLoader;
    }

    /**
     * @param question 用户问题
     * @param ranked   候选意图（按分降序）
     * @return 是否歧义；解析失败默认 true（降级倾向澄清）
     */
    public boolean checkAmbiguity(String question, List<NodeScore> ranked) {
        try {
            String system = templateLoader.render("guidance-ambiguity-check.st", Map.of());
            StringBuilder candidates = new StringBuilder();
            for (NodeScore s : ranked) {
                candidates.append("- ").append(s.node().getFullPath())
                          .append("（score=").append(s.score()).append("）\n");
            }
            String user = "用户问题：" + question + "\n\n候选意图：\n" + candidates;
            String resp = llmService.chat(system + "\n\n" + user, 0.1, 0.3, false);
            // 解析 {"ambiguous": true/false}，失败默认 true
            return resp.contains("\"ambiguous\": true") || resp.contains("\"ambiguous\":true");
        } catch (Exception e) {
            log.debug("[Ambiguity] LLM 判定失败，默认判歧义: {}", e.getMessage());
            return true;   // ★ 降级倾向：宁可澄清
        }
    }
}
