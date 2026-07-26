// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.pipeline.stages;

import sparkx.sparkshop.knowledge.pipeline.PipelineContext;
import sparkx.sparkshop.knowledge.pipeline.PipelineStage;
import dev.langchain4j.rag.content.Content;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合并阶段 —— 文档未给代码，按时序图逻辑自研。
 *
 * 职责：
 * 1. 对重排结果按文本去重（多查询变体可能召回相同内容）
 * 2. 若启用父子分块，此处已是父块内容（由 ParentChildRetriever 展开）；
 *    若使用普通检索，则做截断到合理上下文长度
 * 3. 结果写入 ctx.mergeResult，供生成阶段使用
 */
@Component
@Order(70)
public class MergeStage implements PipelineStage {

    private static final Logger log = LoggerFactory.getLogger(MergeStage.class);

    /** 单次注入上下文的字符上限，避免超长 */
    private static final int MAX_CONTEXT_CHARS = 12000;

    @Override
    public String name() { return "merge"; }

    @Override
    public boolean shouldRun(PipelineContext ctx) {
        return ctx.needsRetrieval()
                && ctx.getRerankResult() != null
                && !ctx.getRerankResult().isEmpty();
    }

    @Override
    public StageResult execute(PipelineContext ctx) throws Exception {
        List<Content> reranked = ctx.getRerankResult();

        // 1. 按文本去重（保留顺序）
        Map<String, Content> dedup = new LinkedHashMap<>();
        int totalChars = 0;
        for (Content c : reranked) {
            String text = c.textSegment().text();
            if (dedup.containsKey(text)) continue;
            if (totalChars + text.length() > MAX_CONTEXT_CHARS) {
                log.info("[Merge] 达到上下文上限 {}，截断", MAX_CONTEXT_CHARS);
                break;
            }
            dedup.put(text, c);
            totalChars += text.length();
        }

        List<Content> merged = List.copyOf(dedup.values());
        ctx.setMergeResult(merged);
        log.info("[Merge] reranked={} merged={} chars={}", reranked.size(), merged.size(), totalChars);

        if (merged.isEmpty()) {
            return StageResult.FALLBACK;
        }
        return StageResult.CONTINUE;
    }
}
