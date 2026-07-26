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

import sparkx.sparkshop.knowledge.prompt.PromptTemplateManager;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * LLM 意图分类 。
 * 注意 1.17.0：ChatModel.chat(String) 返回 String。
 */
@Component
public class LlmIntentClassifier {

    private static final Logger log = LoggerFactory.getLogger(LlmIntentClassifier.class);

    private final ChatModel chatModel;
    private final PromptTemplateManager promptManager;

    public LlmIntentClassifier(ChatModel chatModel, PromptTemplateManager promptManager) {
        this.chatModel = chatModel;
        this.promptManager = promptManager;
    }

    public QueryIntent classify(String query, String conversationHistory, String language) {
        String history = conversationHistory == null ? "" : conversationHistory;
        String system = promptManager.render("intent_classification.system",
                Map.of("language", language, "conversation", history));
        String user = promptManager.render("intent_classification.user",
                Map.of("query", query == null ? "" : query));

        String resp = chatModel.chat(system + "\n\n" + user).trim();
        // 容错：模型可能输出多余文字，提取意图码
        String code = resp.replaceAll(
                ".*?(greeting|summarize|web_search|kb_search|clarification"
                        + "|follow_up|image_only|doc_only|chitchat).*", "$1");
        log.info("[Intent] query=\"{}\" → {} (raw={})", query, code, resp);
        return QueryIntent.fromCode(code);
    }
}
