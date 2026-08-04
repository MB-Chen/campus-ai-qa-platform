package sparkx.sparkshop.campus.agent;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 校园智能问答平台 - 意图路由器（医院分诊台）
 *
 * 关键词匹配（不用 LLM 分类，理由：快/便宜/易调试）
 *
 * 匹配顺序（重要）：
 * 1. KNOWLEDGE 疑问句式优先 —— "什么是/怎么/流程"是比"课"更强的"问知识"信号，
 *    避免"选课怎么选"被"课"字截胡到数据查询
 * 2. TOOL 数据关键词
 * 3. 都没命中 → FALLBACK 兜底
 */
@Component
public class IntentRouter {

    // KNOWLEDGE：疑问句式类关键词（先匹配）
    private static final List<String> KNOWLEDGE_KEYWORDS = List.of(
            "什么是", "怎么", "流程", "材料", "要求", "怎么办",
            "注意事项", "事项", "介绍", "定义", "含义"
    );

    // TOOL：数据查询类关键词
    private static final List<String> TOOL_KEYWORDS = List.of(
            "课表", "成绩", "分数", "学分", "绩点", "校历", "放假",
            "考试周", "开学", "课", "考试", "排名"
    );

    /**
     * 关键词分类
     * @param question 用户原始问题
     * @return 意图分类结果
     */
    public Intent classify(String question) {
        if (question == null || question.isBlank()) {
            return Intent.FALLBACK;
        }

        // 先匹配 KNOWLEDGE 疑问句式（优先级更高）
        for (String keyword : KNOWLEDGE_KEYWORDS) {
            if (question.contains(keyword)) {
                return Intent.KNOWLEDGE_QA;
            }
        }

        // 再匹配 TOOL 数据关键词
        for (String keyword : TOOL_KEYWORDS) {
            if (question.contains(keyword)) {
                return Intent.TOOL_QUERY;
            }
        }

        // 默认兜底
        return Intent.FALLBACK;
    }
}
