// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.infra;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.dictionary.CustomDictionary;
import com.hankcs.hanlp.seg.common.Term;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * tsv 全文检索分词器（移植自 sparkxV2）。
 *
 * <p>设计思路：PG 端 {@code to_tsvector('simple', content)} 不做中文分词，中文 query
 * 命中率极低。这里在 Java 端用 HanLP 切词，把结果手工拼成 PG tsvector 文本格式
 * {@code word:pos1,pos2 word2:pos3}，通过 {@link TsVectorTypeHandler} 以
 * {@code Types.OTHER} 写入 tsv 列。查询端 query 同样先 {@link #toTsQuery} 预分词，
 * 再交 {@code websearch_to_tsquery('simple', ?)}。
 *
 * <p>用 {@code 'simple'} config 是因为词已在 Java 切好，PG 只需做整词等值匹配，
 * 不需要语干还原。
 *
 * <p>注意：DB 端原 {@code trg_tsv} 触发器必须禁用，否则 trigger 会用 simple 覆盖掉
 * 这里写入的分词结果。
 */
public final class TsVectorGenerator {

    /** 占位符候选字符池（用原文中不会出现的 ASCII 控制区字符包成 #c# 占位） */
    private static final String[] MARKER_CACHE = buildMarkerCache();

    /** 特殊词汇保护：版本号、邮箱（防止被 HanLP 切碎） */
    private static final List<Pattern> WORD_PATTERNS = Arrays.asList(
            Pattern.compile("v\\d+\\.\\d+\\.\\d+"),
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}")
    );

    private TsVectorGenerator() {}

    private static String[] buildMarkerCache() {
        List<String> chars = new ArrayList<>();
        // 38..83 为 ASCII '&'..'S'，原文中通常已大量出现，可改用其它区段；
        // 这里保持与 sparkxV2 一致，命中即可用。
        for (int i = 38; i < 84; i++) {
            chars.add(Character.toString((char) i));
        }
        return chars.toArray(new String[0]);
    }

    /**
     * 生成 PG tsvector 文本格式：{@code word:pos1,pos2 word2:pos3}。
     * 供入库写入 tsv 列使用。
     */
    public static String toTsVector(String text) {
        List<WordPosition> positions = buildWordPositions(text);
        return buildTsVector(positions);
    }

    /**
     * 生成 OR 连接的 query 词串：{@code word1 OR word2 OR word3}。
     * 供 {@code websearch_to_tsquery('simple', ?)} 使用。
     *
     * <p>★ 必须用 OR 而非空格：{@code websearch_to_tsquery} 把空格分隔的多词解释成 AND，
     * 中文场景下 HanLP 切出的词只要有一个没在文档里出现就全盘不命中（句子越长越难命中）。
     * 改成 OR 后任一词命中即召回，由 {@code ts_rank_cd} 排序把命中文档多/词频高的排前，
     * 再配合 {@code topRank} 截断与混合检索的 RRF 融合控制噪声。
     */
    public static String toTsQuery(String text) {
        List<WordPosition> positions = buildWordPositions(text);
        return positions.stream()
                .map(wp -> wp.word)
                .distinct()
                .collect(Collectors.joining(" OR "));
    }

    private static List<WordPosition> buildWordPositions(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        // 1. 抽取特殊词汇（版本号 / 邮箱），用占位符替换避免被切碎
        List<String> specialWords = extractSpecialWords(text);
        Map<String, String> wordMap = createWordMap(specialWords, text);
        String processedText = replaceSpecialWords(text, wordMap);

        // 2. HanLP 分词
        List<Term> terms = HanLP.segment(processedText);

        // 3. 过滤标点 / 废话词，还原占位符
        List<WordPosition> positions = new ArrayList<>();
        int index = 1;
        for (Term term : terms) {
            String word = wordMap.getOrDefault(term.word, term.word);
            if (shouldKeepWord(word, term.nature.toString())) {
                positions.add(new WordPosition(word.toLowerCase(), index));
            }
            index++;
        }
        return positions;
    }

    private static List<String> extractSpecialWords(String text) {
        List<String> words = new ArrayList<>();
        for (Pattern pattern : WORD_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                String[] split = matcher.group().split(":");
                Collections.addAll(words, split);
            }
        }
        return words;
    }

    private static Map<String, String> createWordMap(List<String> specialWords, String originalText) {
        Map<String, String> map = new HashMap<>();
        Set<String> usedMarkers = new HashSet<>();
        for (String word : specialWords) {
            String marker = findAvailableMarker(originalText, usedMarkers);
            String placeholder = "#" + marker + "#";
            map.put(placeholder, word);
            // 占位符本身也注册为 HanLP 自定义词，避免被二次切分
            CustomDictionary.add(placeholder);
        }
        return map;
    }

    private static String findAvailableMarker(String text, Set<String> used) {
        for (String c : MARKER_CACHE) {
            if (!text.contains(c) && !used.contains(c)) {
                used.add(c);
                return c;
            }
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String replaceSpecialWords(String text, Map<String, String> wordMap) {
        String result = text;
        for (Map.Entry<String, String> entry : wordMap.entrySet()) {
            String pattern = "(?<!#)" + Pattern.quote(entry.getValue()) + "(?!#)";
            result = result.replaceAll(pattern, entry.getKey());
        }
        return result;
    }

    private static boolean shouldKeepWord(String word, String pos) {
        return !pos.matches("[xw]") &&
                word.length() < 10 &&
                !word.matches("[\n\\s,:\\'<>!@#$%^&*（）：；，./\"]");
    }

    private static String buildTsVector(List<WordPosition> positions) {
        // 同一词出现多次的位置聚合到一起：word -> [pos1, pos2, ...]
        Map<String, List<Integer>> positionMap = new LinkedHashMap<>();
        for (WordPosition wp : positions) {
            positionMap.computeIfAbsent(wp.word, k -> new ArrayList<>()).add(wp.position);
        }
        return positionMap.entrySet().stream()
                .map(entry -> {
                    String positionsStr = entry.getValue().stream()
                            .limit(20)
                            .map(Object::toString)
                            .collect(Collectors.joining(","));
                    return entry.getKey() + ":" + positionsStr;
                })
                .collect(Collectors.joining(" "));
    }

    private static final class WordPosition {
        final String word;
        final int position;

        WordPosition(String word, int position) {
            this.word = word;
            this.position = position;
        }
    }
}
