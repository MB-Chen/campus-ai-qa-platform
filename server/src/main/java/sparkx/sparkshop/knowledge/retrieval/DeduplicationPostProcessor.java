// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.retrieval;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 去重后处理器（文档 5.4.4）—— order=1，按文本去重，保留顺序。
 *
 * 多通道结果合并时去重：同一文本片段只保留首次出现（按通道 priority 优先）。
 * 调用方应在合并时已按 priority 排序，本处理器仅做文本级去重。
 */
@Component
public class DeduplicationPostProcessor implements SearchResultPostProcessor {

    @Override
    public int getOrder() { return 1; }

    @Override
    public boolean isEnabled(RetrievalContext ctx) { return true; }

    @Override
    public List<Content> process(List<Content> chunks, Query query, RetrievalContext ctx) {
        if (chunks == null || chunks.isEmpty()) return List.of();
        // LinkedHashMap 保留首次出现顺序
        Map<String, Content> dedup = new LinkedHashMap<>();
        for (Content c : chunks) {
            String text = c.textSegment().text();
            dedup.putIfAbsent(text, c);
        }
        return List.copyOf(dedup.values());
    }
}
