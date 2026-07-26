// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.ingest;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自适应文档分块器
 * 实现 LangChain4j DocumentSplitter，按文档结构自动选择切分策略。
 *
 * <ul>
 *   <li>三段式递归切分：{@code protectedSpans}（保护区域原子化）→ {@code buildUnitsWithProtection}
 *       （切原子 unit）→ {@code mergeUnits}（贪心填满 + overlap）。</li>
 *   <li>{@code computeOverlapAtoms}：overlap &le; 0 时返回空（不再返回整个文本，否则下一块会复制上一块全文）。</li>
 *   <li>{@code isSeparatorOnly}：纯分隔符 unit（{@code ---}、空行）不进入 overlap，避免下一块以孤立分隔符开头。</li>
 *   <li>{@link ChunkValidator}：碎屑率/超大块校验，失败自动降级到下一 tier（对齐 validator.go）。</li>
 *   <li>{@code ensureDefaults}：overlap 超过 chunkSize/2 时 clamp（对齐 strategy.go）。</li>
 *   <li>{@code dominantHeadingLevel}：用「最低且出现 &ge; 3 次的标题层级」决定是否走 heading tier（对齐 profiler.go）。</li>
 * </ul>
 *
 * <p>三层策略（验证不通过自动降级）：
 * <ul>
 *   <li>Tier 1: Heading —— Markdown 标题感知（保留 SparkX 的「软边界累积」语义，对密集小标题文档更优）。</li>
 *   <li>Tier 2: Heuristic —— 编号/章节等启发式边界（当前并入 heading 判定）。</li>
 *   <li>Tier 3: Recursive —— 递归分隔符（兜底）。</li>
 * </ul>
 */
public class AdaptiveDocumentSplitter implements DocumentSplitter {

    private final int chunkSize;
    private final int chunkOverlap;
    private final String strategy;   // auto | heading | heuristic | recursive
    /** 自定义递归分隔符列表（仅 recursive 分支消费；为 null/空走 DEFAULT_SEPARATORS） */
    private final List<String> separators;

    /** 默认递归分隔符：段落（双换行）→ 单换行 → 中文句号 */
    private static final List<String> DEFAULT_SEPARATORS = List.of("\n\n", "\n", "。");

    /** 保护模式：以下内容不切开 */
    private static final List<Pattern> PROTECTED = List.of(
            Pattern.compile("(?s)\\$\\$.*?\\$\\$"),                    // LaTeX 块公式
            Pattern.compile("!\\[[^\\]]*\\]\\([^)]+\\)"),              // Markdown 图片
            Pattern.compile("\\[[^\\]]*\\]\\([^)]+\\)"),               // Markdown 链接
            Pattern.compile("(?s)```(?:\\w+)?[\\r\\n].*?```"),         // 围栏代码块
            // 表格：表头行 + 分隔行（对齐 splitter.go:122）
            Pattern.compile("(?m)[ ]*(?:\\|[^|\\n]*)+\\|[\\r\\n]+\\s*(?:\\|\\s*:?-{3,}:?\\s*)+\\|[\\r\\n]+"),
            // 表格：普通数据行（对齐 splitter.go:123）
            Pattern.compile("(?m)[ ]*(?:\\|[^|\\n]*)+\\|[\\r\\n]+")
    );

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+.+$");

    /** 保护区域 unit 超过该字符数才强制硬切（对齐 WeKnora maxProtectedSize） */
    private static final int MAX_PROTECTED_SIZE = 7500;

    public AdaptiveDocumentSplitter(int chunkSize, int chunkOverlap, String strategy) {
        this(chunkSize, chunkOverlap, strategy, null);
    }

    /**
     * @param separators 自定义递归分隔符列表（仅 recursive 分支消费）。
     *                   为 null/空/元素全空白时走 {@link #DEFAULT_SEPARATORS}。
     *                   列表顺序即降级顺序：先用第 0 个切，仍超长再用第 1 个，依此类推。
     */
    public AdaptiveDocumentSplitter(int chunkSize, int chunkOverlap, String strategy, List<String> separators) {
        // ensureDefaults（对齐 strategy.go:248-276）：overlap 不应超过 chunkSize/2，否则每块都是上一块的克隆
        if (chunkSize > 0 && chunkOverlap > chunkSize / 2) {
            chunkOverlap = chunkSize / 2;
        }
        if (chunkOverlap < 0) chunkOverlap = 0;
        this.chunkSize = chunkSize > 0 ? chunkSize : 512;
        this.chunkOverlap = chunkOverlap;
        this.strategy = strategy;
        this.separators = separators;
    }

    @Override
    public List<TextSegment> split(Document document) {
        String text = document.text();
        if (text == null || text.isBlank()) return List.of();

        // 1. 文档画像
        DocProfile profile = profileDocument(text);

        // 2. 选择策略链
        List<String> chain = selectChain(profile);
        int totalChars = text.length();
        for (String tier : chain) {
            List<TextSegment> chunks = splitByTier(tier, text, document);
            if (ChunkValidator.validate(chunks, totalChars, chunkSize)) return chunks;
        }
        // 兜底：递归切分（不再校验，保证一定有输出）
        return recursiveSplit(text, document);
    }

    /** 文档画像（对齐 profiler.go） */
    private DocProfile profileDocument(String text) {
        int[] headingByLevel = new int[7]; // index 1..6
        int headingTotal = 0;
        for (String line : text.split("\n")) {
            Matcher m = HEADING.matcher(line.trim());
            if (m.matches()) {
                int level = m.group(1).length();
                if (level >= 1 && level <= 6) {
                    headingByLevel[level]++;
                    headingTotal++;
                }
            }
        }
        return new DocProfile(headingByLevel, headingTotal, text.length());
    }

    /**
     * 主导标题层级（对齐 profiler.go DominantHeadingLevel）：
     * 返回「最低且出现 &ge; 3 次的层级」；都没有则返回「最深的非零层级」；无标题返回 0。
     */
    private int dominantHeadingLevel(DocProfile p) {
        if (p.headingTotal == 0) return 0;
        for (int level = 1; level <= 6; level++) {
            if (p.headingByLevel[level] >= 3) return level;
        }
        for (int level = 6; level >= 1; level--) {
            if (p.headingByLevel[level] > 0) return level;
        }
        return 0;
    }

    private List<String> selectChain(DocProfile p) {
        if (!"auto".equals(strategy)) {
            return switch (strategy) {
                case "heading" -> List.of("heading", "recursive");
                case "heuristic" -> List.of("heuristic", "recursive");
                default -> List.of("recursive");
            };
        }
        // auto：主导标题层级 > 0 才上 heading（比原来「headingTotal>=3」更准确，对齐 WeKnora SelectStrategy）
        List<String> chain = new ArrayList<>();
        if (dominantHeadingLevel(p) > 0) chain.add("heading");
        chain.add("recursive");
        return chain;
    }

    private List<TextSegment> splitByTier(String tier, String text, Document doc) {
        return switch (tier) {
            case "heading" -> splitByHeadings(text, doc);
            default -> recursiveSplit(text, doc);
        };
    }

    /**
     * Tier 1: 按标题切分，携带标题面包屑上下文；超长块强制切分时保留 overlap 重叠。
     *
     * <p>★ 标题边界处理（标题是「软边界」，不是「硬边界」）：
     * <ul>
     *   <li>遇到标题行：仅当「当前块继续累积该标题行后会超过 chunkSize」时，
     *       才在标题前关闭当前块（<b>不带 overlap 尾巴</b>，因为标题是天然边界），
     *       新块从标题行干净开始；否则标题并入当前块继续累积。</li>
     *   <li>这样能让密集小标题文档（如多个 {@code ##} 章节）真正按 chunkSize 聚合，
     *       避免父块被切成一堆几百字的小块、永远攒不到 parentSize。</li>
     *   <li>同一章节内因 chunkSize 超长被强制切分时，才保留 overlap 尾巴（基于 atom 列表），
     *       保证段内语义连续性（避免「连续缺勤 1 天」和「以上视为旷工」被切开）。</li>
     * </ul>
     */
    private List<TextSegment> splitByHeadings(String text, Document doc) {
        List<TextSegment> result = new ArrayList<>();
        List<String> current = new ArrayList<>();   // atom 列表（对齐 WeKnora mergeUnits 的 current []splitUnit）
        int curLen = 0;
        String currentBreadcrumb = "";

        for (String line : text.split("(?<=\n)", -1)) {
            Matcher m = HEADING.matcher(line.trim());
            if (m.matches()) {
                // 标题作为软边界：仅当继续累积会超过 chunkSize 时，才在标题前切断；
                // 否则标题并入当前块继续累积，避免密集标题把文档切得过碎（父块攒不满 parentSize）。
                if (!current.isEmpty() && curLen + line.length() > chunkSize) {
                    addIfNotBlank(result, joinAtoms(current), currentBreadcrumb, doc);
                    current = new ArrayList<>();
                    curLen = 0;
                }
                currentBreadcrumb = line.trim();
                current.add(line);
                curLen += line.length();
                continue;
            }
            current.add(line);
            curLen += line.length();
            // 同一章节内超长：强制切分，这里保留 overlap 尾巴保证段内连续
            if (curLen >= chunkSize) {
                addIfNotBlank(result, joinAtoms(current), currentBreadcrumb, doc);
                // overlap 尾巴按 atom 边界取（不再用 overlapTail 字符串硬截）。
                // nextLen 传 line.length()：flush 后这一行会重新加入新块，需约束 overlap+line 不超 chunkSize。
                current = computeOverlapAtoms(current, chunkOverlap, chunkSize, line.length());
                curLen = totalLen(current);
            }
        }
        if (!current.isEmpty()) {
            addIfNotBlank(result, joinAtoms(current), currentBreadcrumb, doc);
        }
        return result;
    }

    /**
     * Tier 3: 递归分隔符切分 + overlap。
     *
     * <p>三阶段流水线（对齐 splitter.go:271-297）：
     * <ol>
     *   <li>{@link #protectedSpans} 找出保护区域（表格/代码块/图片/LaTeX/链接）。</li>
     *   <li>{@link #buildUnitsWithProtection} 把文本切成「原子 unit」：非保护区域走
     *       {@link #recursiveCut}，保护区域作为整体 unit（超 {@link #MAX_PROTECTED_SIZE} 才硬切）。</li>
     *   <li>{@link #mergeUnits} 贪心填满 + overlap 合并 unit 为最终 chunk。</li>
     * </ol>
     */
    private List<TextSegment> recursiveSplit(String text, Document doc) {
        List<String> seps = effectiveSeparators();
        List<String> units = buildUnitsWithProtection(text, seps);
        List<String> mergedTexts = mergeUnits(units);
        List<TextSegment> result = new ArrayList<>();
        for (String t : mergedTexts) {
            addIfNotBlank(result, t, "", doc);
        }
        return result;
    }

    /** 归一化 separators：用户传空/全空白时回落到默认列表 */
    private List<String> effectiveSeparators() {
        if (separators != null) {
            List<String> cleaned = new ArrayList<>();
            for (String s : separators) {
                if (s != null && !s.isEmpty()) cleaned.add(s);
            }
            if (!cleaned.isEmpty()) return cleaned;
        }
        return DEFAULT_SEPARATORS;
    }


    /** 保护区域 [start, end) 区间（字符偏移） */
    private record Span(int start, int end) {}

    /**
     * 找出所有保护区域，按 start 排序并去除重叠。
     */
    private List<Span> protectedSpans(String text) {
        List<Span> all = new ArrayList<>();
        for (Pattern pat : PROTECTED) {
            Matcher m = pat.matcher(text);
            while (m.find()) {
                if (m.end() - m.start() > 0) all.add(new Span(m.start(), m.end()));
            }
        }
        if (all.isEmpty()) return Collections.emptyList();
        // 按 start 升序，start 相同则长的优先（对齐 splitter.go:178-188）
        all.sort((a, b) -> a.start != b.start
                ? Integer.compare(a.start, b.start)
                : Integer.compare(b.end - b.start, a.end - a.start));
        // 去重叠（对齐 splitter.go:190-198）
        List<Span> result = new ArrayList<>();
        int lastEnd = 0;
        for (Span s : all) {
            if (s.start >= lastEnd) {
                result.add(s);
                lastEnd = s.end;
            }
        }
        return result;
    }

    /**
     * 把文本切成 unit：非保护区域走 {@link #recursiveCut}，保护区域作为整体 unit
     * （超 {@link #MAX_PROTECTED_SIZE} 才硬切，对齐 splitter.go:306 buildUnitsWithProtection）。
     */
    private List<String> buildUnitsWithProtection(String text, List<String> seps) {
        List<Span> protected_ = protectedSpans(text);
        List<String> units = new ArrayList<>();
        int bytePos = 0;
        for (Span p : protected_) {
            if (p.start > bytePos) {
                String pre = text.substring(bytePos, p.start);
                units.addAll(recursiveCut(pre, seps, 0));
            }
            String protText = text.substring(p.start, p.end);
            if (protText.length() > MAX_PROTECTED_SIZE) {
                // 保护内容过大，按行/空格强制切（对齐 splitter.go:334-360）
                units.addAll(forceSplitProtected(protText));
            } else {
                units.add(protText);
            }
            bytePos = p.end;
        }
        if (bytePos < text.length()) {
            units.addAll(recursiveCut(text.substring(bytePos), seps, 0));
        }
        return units;
    }

    /** 保护区域过大时按 MAX_PROTECTED_SIZE 硬切，尽量在换行/空格处断开（对齐 splitter.go:336-360） */
    private List<String> forceSplitProtected(String text) {
        List<String> out = new ArrayList<>();
        int offset = 0;
        while (offset < text.length()) {
            int chunkEnd = Math.min(text.length(), offset + MAX_PROTECTED_SIZE);
            if (chunkEnd < text.length()) {
                for (int i = chunkEnd - 1; i > offset && i > chunkEnd - 200; i--) {
                    char c = text.charAt(i);
                    if (c == '\n' || c == ' ') { chunkEnd = i + 1; break; }
                }
            }
            out.add(text.substring(offset, chunkEnd));
            offset = chunkEnd;
        }
        return out;
    }


    /**
     * 把原子 unit 合并成最终 chunk：贪心填满到 chunkSize，flush 时保留尾部 overlap。
     * flush 条件用严格 {@code >}（对齐 splitter.go:469 {@code curLen+uLen > chunkSize}），
     * 让每个 chunk 尽量接近 chunkSize 而不是中途切断。
     */
    private List<String> mergeUnits(List<String> units) {
        if (units.isEmpty()) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int curLen = 0;
        for (int idx = 0; idx < units.size(); idx++) {
            String u = units.get(idx);
            int uLen = u.length();
            // 单个 unit 超过绝对上限（MAX_PROTECTED_SIZE）时单独成块（保护内容已在 buildUnits 阶段限长，这里是兜底）
            if (uLen > MAX_PROTECTED_SIZE) {
                if (!current.isEmpty()) {
                    result.add(joinAtoms(current));
                    current = new ArrayList<>();
                    curLen = 0;
                }
                result.add(u);
                continue;
            }
            // 加上这个 unit 会超 chunkSize → flush 当前块（对齐 splitter.go:469）
            if (curLen + uLen > chunkSize && !current.isEmpty()) {
                result.add(joinAtoms(current));
                // overlap 取尾部 unit，传入「下一个 unit 长度」约束 overlap+next 不超 chunkSize
                // （对齐 splitter.go:473 + 598-600，否则 overlap 偏大会让子块数偏少）
                current = computeOverlapAtoms(current, chunkOverlap, chunkSize, uLen);
                curLen = totalLen(current);
            }
            current.add(u);
            curLen += uLen;
        }
        if (!current.isEmpty()) {
            result.add(joinAtoms(current));
        }
        return result;
    }

    /**
     * 从 current 尾部取 overlap unit 子列表（对齐 splitter.go:584 computeOverlap）。
     * <p><b>关键：overlap &le; 0 时返回空</b>——这是修复「下一块复制上一块全文」bug 的核心。
     *
     * <p>从尾部往前累积 unit，直到长度超过 chunkOverlap；同时受 nextLen 约束：
     * overlap + 下一个 unit 不能超过 chunkSize（对齐 splitter.go:598-600）。
     * 否则 overlap 偏大时，下一块开头会被 overlap 撑满，导致子块数偏少、与 WeKnora 不一致。
     *
     * <p>跳过开头是 {@link #isSeparatorOnly} 的 unit（避免 overlap 以孤立分隔符开头，对齐 splitter.go:605-616）。
     */
    private List<String> computeOverlapAtoms(List<String> current, int overlap, int chunkSize, int nextLen) {
        if (overlap <= 0 || current.isEmpty()) return new ArrayList<>();
        int overlapLen = 0;
        int startIdx = current.size();
        for (int i = current.size() - 1; i >= 0; i--) {
            int uLen = current.get(i).length();
            if (overlapLen + uLen > overlap) break;
            // overlap + 下一个 unit 不能超过 chunkSize（对齐 splitter.go:598-600）
            if (overlapLen + uLen + nextLen > chunkSize) break;
            overlapLen += uLen;
            startIdx = i;
        }
        // 跳过开头的纯分隔符 unit（对齐 splitter.go:605-616）
        while (startIdx < current.size() && isSeparatorOnly(current.get(startIdx))) {
            startIdx++;
        }
        if (startIdx >= current.size()) return new ArrayList<>();
        return new ArrayList<>(current.subList(startIdx, current.size()));
    }

    /**
     * 只含 {@code \n \r 空格 \t 。} 的字符串视为纯分隔符（对齐 splitter.go:627 isSeparatorOnly）。
     * 典型：空行、{@code ---}（其实 {@code -} 不在里面，但独立的 {@code ---\n} 会被判定为非纯分隔；
     * 真正目的是过滤空行/换行/句号构成的噪声 unit）。
     */
    private boolean isSeparatorOnly(String s) {
        if (s == null || s.isEmpty()) return true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\n' && c != '\r' && c != ' ' && c != '\t' && c != '。') return false;
        }
        return true;
    }


    /**
     * 递归细切：用 seps[level] 把 text 切成多段；对仍超过 chunkSize 的段，
     * 用 seps[level+1] 继续切；seps 用尽仍超长则 {@link #hardSplit} 硬切。
     * 返回的原子块拼接后等于原文（保留分隔符在前一块），不丢字符。
     *
     * <p>★ 用显式 {@code indexOf} 扫描替代 {@code String.split("(?<=...)")}：
     * 后者在不同输入下的零宽断言行为难以预测，曾导致 atom 粒度与参考实现（WeKnora）不一致，
     * 子块数偏差。显式扫描保证「分隔符粘在前一块尾、不丢字符」的可控语义。
     */
    private List<String> recursiveCut(String text, List<String> seps, int level) {
        if (text.isEmpty()) return Collections.emptyList();
        // 已足够小，直接作为原子返回（仍可能 > chunkSize，交由上层 merge 处理边界）
        if (text.length() <= chunkSize) return new ArrayList<>(List.of(text));
        // 分隔符用尽，硬切
        if (level >= seps.size()) return hardSplit(text);

        String sep = seps.get(level);
        // 空分隔符跳过用下一级
        if (sep.isEmpty()) return recursiveCut(text, seps, level + 1);

        // 显式扫描：找到每个 sep 的结束位置，分隔符（含）并入前一段
        List<String> parts = splitKeepSepBack(text, sep);
        if (parts.size() <= 1) {
            // 当前分隔符没切出多段，用下一级
            return recursiveCut(text, seps, level + 1);
        }

        List<String> out = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (part.length() > chunkSize) {
                // 仍超长，用下一级分隔符继续切
                out.addAll(recursiveCut(part, seps, level + 1));
            } else {
                out.add(part);
            }
        }
        return out;
    }

    /**
     * 按 sep 切分 text，分隔符（含）并入前一段尾。例如 {@code "A\n\nB\n\nC"} 用 {@code "\n\n"} 切
     * 得 {@code ["A\n\n", "B\n\n", "C"]}。找不到 sep 时返回 {@code [text]}（单元素）。
     */
    private List<String> splitKeepSepBack(String text, String sep) {
        List<String> out = new ArrayList<>();
        int from = 0;
        int idx;
        while ((idx = text.indexOf(sep, from)) >= 0) {
            int cutEnd = idx + sep.length();   // 分隔符并入前段
            out.add(text.substring(from, cutEnd));
            from = cutEnd;
        }
        if (from < text.length()) {
            out.add(text.substring(from));
        }
        return out;
    }

    /** 分隔符用尽后的纯字符硬切：按 chunkSize 等分，保留全部字符 */
    private List<String> hardSplit(String text) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < text.length(); i += chunkSize) {
            out.add(text.substring(i, Math.min(text.length(), i + chunkSize)));
        }
        return out;
    }


    private String joinAtoms(List<String> atoms) {
        if (atoms.isEmpty()) return "";
        if (atoms.size() == 1) return atoms.get(0);
        StringBuilder sb = new StringBuilder();
        for (String a : atoms) sb.append(a);
        return sb.toString();
    }

    private int totalLen(List<String> atoms) {
        int n = 0;
        for (String a : atoms) n += a.length();
        return n;
    }

    /** 仅当文本 strip 后非空时才生成 TextSegment，避免 LangChain4j 校验抛异常 */
    private void addIfNotBlank(List<TextSegment> result, String text, String breadcrumb, Document doc) {
        if (text == null || text.strip().isEmpty()) return;
        result.add(toSegment(text, breadcrumb, doc));
    }

    private TextSegment toSegment(String text, String breadcrumb, Document doc) {
        Metadata meta = doc.metadata() != null ? Metadata.from(doc.metadata().toMap()) : new Metadata();
        meta.put("breadcrumb", breadcrumb == null ? "" : breadcrumb);
        return TextSegment.from(text.strip(), meta);
    }

    private record DocProfile(int[] headingByLevel, int headingTotal, int totalChars) {}
}
