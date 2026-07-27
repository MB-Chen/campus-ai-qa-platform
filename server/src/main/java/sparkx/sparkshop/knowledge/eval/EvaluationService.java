// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.eval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG 回归评估服务（移植自 sparkxV2，文档 9.2 第14项）—— 占位骨架。
 *
 * 规划接入 LangChain4j evaluation + RAGAS 指标（faithfulness/answer_relevancy/context_precision）。
 * 本阶段提供测试集管理与评估入口的骨架，具体评估指标计算后续接入。
 */
@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

    /**
     * 评估单个问答样本。
     *
     * @param question   问题
     * @param answer     模型回答
     * @param contexts   检索上下文
     * @param groundTruth 标准答案（可空）
     * @return 评估结果（占位：后续接 RAGAS）
     */
    public EvalResult evaluate(String question, String answer,
                               List<String> contexts, String groundTruth) {
        log.info("[Eval] questionLen={} answerLen={} contexts={}",
                question.length(), answer.length(), contexts.size());
        // 占位：后续接入 LangChain4j evaluation / RAGAS
        return new EvalResult(question, answer, 0.0, 0.0, 0.0, new ArrayList<>());
    }

    /** 评估结果 */
    public record EvalResult(String question, String answer,
                             double faithfulness, double answerRelevancy,
                             double contextPrecision, List<String> issues) { }
}
