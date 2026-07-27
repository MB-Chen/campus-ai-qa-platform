// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.query;

import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.transformer.QueryTransformer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 本地查询扩写。
 * 实现 LangChain4j QueryTransformer，生成多个查询变体提升关键词召回。
 * 纯规则，不调 LLM，零延迟零成本。
 */
public class QueryExpansionTransformer implements QueryTransformer {

    private static final Set<String> STOPWORDS = Set.of(
            "的", "是", "在", "了", "和", "与", "或", "a", "an", "the",
            "is", "are", "was", "were", "what", "how", "why", "which");

    private static final Pattern QUESTION_WORDS = Pattern.compile(
            "^(什么是|什么|如何|怎么|怎样|为什么|为何|哪个|哪些|谁|何时|何地|请问|请告诉我|帮我|我想知道|我想了解)");

    private static final Pattern DELIMITERS = Pattern.compile("[,，;；、。！？!?\\s]+");

    @Override
    public Collection<Query> transform(Query query) {
        String q = query.text().trim();
        List<String> variants = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        seen.add(q.toLowerCase());

        // 1. 去停用词的关键词变体
        List<String> keywords = extractKeywords(q);
        if (keywords.size() >= 2) {
            addIfNew(variants, seen, String.join(" ", keywords));
        }
        // 2. 按分隔符切分取最长段
        for (String seg : DELIMITERS.split(q)) {
            if (seg.length() > 5) addIfNew(variants, seen, seg);
        }
        // 3. 去疑问词
        String cleaned = QUESTION_WORDS.matcher(q).replaceFirst("").trim();
        if (!cleaned.equals(q) && cleaned.length() >= 3) {
            addIfNew(variants, seen, cleaned);
        }

        if (variants.isEmpty()) return List.of(query);

        List<Query> result = new ArrayList<>();
        result.add(query);  // 原始 query 始终保留
        for (String v : variants) {
            result.add(Query.from(v, query.metadata()));
        }
        return result;   // DefaultRetrievalAugmentor 会为每个 query 并发检索，再 RRF 融合
    }

    private List<String> extractKeywords(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isLetterOrDigit(c) || isChinese(c)) {
                cur.append(c);
            } else if (cur.length() > 0) {
                tokens.add(cur.toString());
                cur.setLength(0);
            }
        }
        if (cur.length() > 0) tokens.add(cur.toString());
        return tokens.stream()
                .filter(t -> t.length() > 1 && !STOPWORDS.contains(t.toLowerCase()))
                .toList();
    }

    private boolean isChinese(char c) {
        return c >= '\u4e00' && c <= '\u9fff';
    }

    private void addIfNew(List<String> variants, Set<String> seen, String s) {
        if (s == null || s.length() < 3) return;
        if (seen.add(s.toLowerCase())) variants.add(s);
    }
}
