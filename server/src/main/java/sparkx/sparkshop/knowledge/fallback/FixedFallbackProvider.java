// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.fallback;

import sparkx.sparkshop.knowledge.prompt.PromptTemplateManager;

import java.util.Map;

/**
 * 固定兜底 —— 零成本，不调模型，直接返回预设话术。
 */
public class FixedFallbackProvider implements FallbackProvider {

    private final PromptTemplateManager promptManager;

    /** 无 PromptManager 时使用内置话术 */
    public FixedFallbackProvider() {
        this.promptManager = null;
    }

    public FixedFallbackProvider(PromptTemplateManager promptManager) {
        this.promptManager = promptManager;
    }

    @Override
    public String fallback(String query, String rewriteQuery, String language) {
        if (promptManager != null) {
            try {
                return promptManager.render("fallback.fixed", Map.of());
            } catch (Exception ignored) {
                // 模板缺失走内置话术
            }
        }
        return "抱歉，我在当前知识库中暂未找到与您问题相关的内容。"
                + "您可以：1) 换个关键词重新提问；2) 上传相关文档到知识库后再试。";
    }
}
