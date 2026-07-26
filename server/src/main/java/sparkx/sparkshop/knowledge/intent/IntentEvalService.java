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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import sparkx.sparkshop.knowledge.validate.IntentEvalSingleValidate;
import sparkx.sparkshop.knowledge.validate.IntentEvalValidate;
import sparkx.sparkshop.knowledge.vo.IntentEvalCaseVo;
import sparkx.sparkshop.knowledge.vo.IntentEvalReportVo;
import sparkx.sparkshop.knowledge.vo.IntentEvalResultVo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * 意图分类评估服务。
 *
 * 注入生产 {@link IntentClassifier}（跑真实 LLM 链路），对一组标注用例并行分类，
 * 统计 Top1/Top3 准确率、各节点 P/R/F1、置信度校准、误判清单。
 *
 * 单条异常降级为 empty/error，不中断整体评估。
 */
@Service
public class IntentEvalService {

    private static final Logger log = LoggerFactory.getLogger(IntentEvalService.class);

    /** 校准分桶边界 */
    private static final double[] BIN_EDGES = {0.0, 0.4, 0.6, 0.8, 1.0};
    private static final String[] BIN_LABELS = {"[0,0.4)", "[0.4,0.6)", "[0.6,0.8)", "[0.8,1.0]"};

    /** 默认 TopN（对齐前端兜底 3） */
    private static final int DEFAULT_TOP_N = 3;
    /** 默认最小分数阈值（对齐生产 INTENT_MIN_SCORE=0.35） */
    private static final double DEFAULT_MIN_SCORE = 0.35;

    private final IntentClassifier classifier;
    private final ExecutorService executor;
    private final IntentTreeCacheManager cacheManager;
    private final RuleBasedIntentRouter ruleRouter;

    public IntentEvalService(IntentClassifier classifier,
                             @Qualifier("intentClassifyExecutor") ExecutorService executor,
                             IntentTreeCacheManager cacheManager,
                             RuleBasedIntentRouter ruleRouter) {
        this.classifier = classifier;
        this.executor = executor;
        this.cacheManager = cacheManager;
        this.ruleRouter = ruleRouter;
    }

    /** 构 id→节点 索引（用于回填期望节点的 name/kind） */
    private Map<String, IntentNode> indexLeaves() {
        Map<String, IntentNode> map = new HashMap<>();
        try {
            IntentNode root = cacheManager.loadTree();
            for (IntentNode leaf : IntentTreeCacheManager.flattenLeaves(root)) {
                map.put(leaf.getId(), leaf);
            }
        } catch (Exception e) {
            log.warn("[IntentEval] 加载意图树失败: {}", e.getMessage());
        }
        return map;
    }

    /**
     * 跑单条 query，返回 TopK 候选（实时调试用）。
     *
     * 对齐生产链路：GREETING/CHITCHAT 规则命中后不再短路，统一走 LLM 意图树路由，
     * 由 classifier 精确分类到具体 SYSTEM 节点。topN/minScore 为空时取默认值。
     */
    public List<IntentEvalResultVo.HitCandidate> evalSingle(IntentEvalSingleValidate req) {
        int topN = req.getTopN() == null ? DEFAULT_TOP_N : req.getTopN();
        double minScore = req.getMinScore() == null ? DEFAULT_MIN_SCORE : req.getMinScore();
        String query = req.getQuery() == null ? "" : req.getQuery();
        List<NodeScore> hits = classifier.topKAboveThreshold(query, topN, minScore);
        return toCandidates(hits);
    }

    /**
     * 批量评估并产出报告。topN/minScore 为空时取默认值（与前端兜底一致）。
     *
     * @param req 入参（cases + 可选 topN + 可选 minScore）
     */
    public IntentEvalReportVo runEval(IntentEvalValidate req) {
        List<IntentEvalCaseVo> cases = req.getCases();
        int topN = req.getTopN() == null ? DEFAULT_TOP_N : req.getTopN();
        double minScore = req.getMinScore() == null ? DEFAULT_MIN_SCORE : req.getMinScore();
        long start = System.currentTimeMillis();
        IntentEvalReportVo report = new IntentEvalReportVo();
        if (cases == null || cases.isEmpty()) {
            report.setTotal(0);
            report.setCostMs(System.currentTimeMillis() - start);
            report.setPerNode(List.of());
            report.setCalibration(List.of());
            report.setMisclassified(List.of());
            report.setDetails(List.of());
            report.setGrade("poor");
            report.setGradeLabel("无数据");
            report.setSummary("尚无用例，点击「加载种子测试集」或手动添加后评估。");
            report.setTips(List.of());
            return report;
        }
        report.setTotal(cases.size());

        // 并行跑每条 case
        List<CompletableFuture<IntentEvalResultVo>> futures = cases.stream()
                .map(c -> CompletableFuture.supplyAsync(() -> runOne(c, topN, minScore), executor))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<IntentEvalResultVo> results = futures.stream()
                .map(f -> {
                    try {
                        return f.get();
                    } catch (Exception e) {
                        // 兜底（runOne 已 catch，正常不会到这）
                        IntentEvalResultVo r = new IntentEvalResultVo();
                        r.setError(true);
                        r.setErrorMsg(e.getMessage());
                        return r;
                    }
                })
                .collect(Collectors.toCollection(ArrayList::new));

        Map<String, IntentNode> leafIndex = indexLeaves();
        aggregate(results, report, leafIndex);
        report.setCostMs(System.currentTimeMillis() - start);
        return report;
    }

    /** 跑单条用例（异常降级） */
    private IntentEvalResultVo runOne(IntentEvalCaseVo c, int topN, double minScore) {
        IntentEvalResultVo r = new IntentEvalResultVo();
        r.setQuery(c.getQuery());
        r.setExpectNodeId(c.getExpectNodeId());
        r.setExpectNodeName(c.getExpectNodeName());
        r.setNote(c.getNote());
        r.setRuleShortCircuit(ruleRouter.isFiller(c.getQuery()));
        try {
            // 对齐生产链路：GREETING/CHITCHAT 规则命中后仍走意图树路由（不再短路），
            // 统一由 classifier 精确分类到具体 SYSTEM 节点。
            List<NodeScore> hits = classifier.topKAboveThreshold(c.getQuery(), topN, minScore);
            List<IntentEvalResultVo.HitCandidate> cands = toCandidates(hits);
            r.setTopKHits(cands);
            if (cands.isEmpty()) {
                r.setEmpty(true);
            } else {
                IntentEvalResultVo.HitCandidate top1 = cands.get(0);
                r.setHitNodeId(top1.getId());
                r.setHitNodeName(top1.getName());
                r.setHitKind(top1.getKind());
                r.setScore(top1.getScore());
            }
        } catch (Exception e) {
            log.warn("[IntentEval] 单条评估失败 query={}: {}", c.getQuery(), e.getMessage());
            r.setError(true);
            r.setErrorMsg(e.getMessage());
            r.setTopKHits(List.of());
            r.setEmpty(true);
        }
        return r;
    }

    /** NodeScore → 轻量候选 */
    private List<IntentEvalResultVo.HitCandidate> toCandidates(List<NodeScore> hits) {
        if (hits == null || hits.isEmpty()) return List.of();
        List<IntentEvalResultVo.HitCandidate> out = new ArrayList<>(hits.size());
        for (NodeScore s : hits) {
            IntentEvalResultVo.HitCandidate hc = new IntentEvalResultVo.HitCandidate();
            hc.setId(s.node().getId());
            hc.setName(s.node().getName());
            hc.setKind(s.node().getKind() == null ? null : s.node().getKind().name());
            hc.setScore(s.score());
            out.add(hc);
        }
        return out;
    }

    /** 汇总指标 */
    private void aggregate(List<IntentEvalResultVo> results, IntentEvalReportVo report,
                           Map<String, IntentNode> leafIndex) {
        int total = results.size();
        int top1Hit = 0, top3Hit = 0, empty = 0, error = 0, scored = 0;
        double scoreSum = 0;

        // 校准桶：命中数 / 总数
        int[] binTotal = new int[BIN_LABELS.length];
        int[] binCorrect = new int[BIN_LABELS.length];
        // P/R/F1 累计：按 expectNodeId 分组
        Map<String, Agg> byExpect = new LinkedHashMap<>();
        // 预测计数（按 top1 命中 id，含空预测记作 null）
        Map<String, Integer> predCount = new HashMap<>();

        List<IntentEvalResultVo> misclassified = new ArrayList<>();

        for (IntentEvalResultVo r : results) {
            if (r.isError()) error++;
            String expect = r.getExpectNodeId();
            Agg agg = byExpect.computeIfAbsent(expect == null ? "" : expect, k -> {
                Agg a = new Agg();
                IntentNode n = k.isEmpty() ? null : leafIndex.get(k);
                if (n != null) {
                    a.name = n.getName();
                    a.kind = n.getKind() == null ? null : n.getKind().name();
                }
                return a;
            });
            agg.caseCount++;

            if (r.getTopKHits() == null || r.getTopKHits().isEmpty()) {
                empty++;
                // ★ 空预测匹配空期望（如无意义输入"asdfghjkl"）应判为正确，
                //   计入准确率、不进误判清单；仅当期望非空时空预测才算误判。
                boolean expectEmpty = expect == null || expect.isEmpty();
                if (expectEmpty) {
                    top1Hit++;
                    top3Hit++;
                    agg.correct++;
                    predCount.merge("", 1, Integer::sum);
                    r.setTop1Hit(true);
                    r.setTop3Hit(true);
                } else {
                    predCount.merge(null, 1, Integer::sum);
                    misclassified.add(r);
                }
                continue;
            }

            // Top1 / Top3 命中判定（期望 id 命中）
            List<String> ids = r.getTopKHits().stream()
                    .map(IntentEvalResultVo.HitCandidate::getId).toList();
            boolean h1 = !ids.isEmpty() && ids.get(0).equals(expect);
            boolean h3 = ids.stream().limit(3).anyMatch(x -> x != null && x.equals(expect));
            r.setTop1Hit(h1);
            r.setTop3Hit(h3);
            if (h1) top1Hit++;
            if (h3) top3Hit++;

            // Top1 分数统计
            scored++;
            scoreSum += r.getScore();
            predCount.merge(r.getHitNodeId(), 1, Integer::sum);

            // 校准桶（按 Top1 分数）
            int bin = binOf(r.getScore());
            binTotal[bin]++;
            if (h1) binCorrect[bin]++;

            // P/R/F1：命中归 correct
            if (h1) {
                agg.correct++;
            } else {
                misclassified.add(r);
            }
        }

        // 误判按分数降序（高分却判错最值得关注）
        misclassified.sort(Comparator.comparingDouble(
                (IntentEvalResultVo x) -> x.getScore() == null ? -1 : x.getScore()).reversed());

        report.setAccuracy1(safeDiv(top1Hit, total));
        report.setAccuracy3(safeDiv(top3Hit, total));
        report.setEmptyRate(safeDiv(empty, total));
        report.setErrorRate(safeDiv(error, total));
        report.setAvgTop1Score(scored > 0 ? scoreSum / scored : 0);
        report.setMisclassified(misclassified);
        report.setDetails(results);

        // perNode：precision = correct / 该类被预测为 top1 的总次数（含被其他类误判到这里）
        // recall = correct / caseCount
        List<IntentEvalReportVo.NodeMetric> nodes = new ArrayList<>();
        for (Map.Entry<String, Agg> e : byExpect.entrySet()) {
            Agg a = e.getValue();
            String id = e.getKey();
            int predictedAs = predCount.getOrDefault(id, 0);
            double p = safeDiv(a.correct, predictedAs);
            double rc = safeDiv(a.correct, a.caseCount);
            double f1 = (p + rc) > 0 ? 2 * p * rc / (p + rc) : 0;
            IntentEvalReportVo.NodeMetric m = new IntentEvalReportVo.NodeMetric();
            m.setNodeId(id);
            // 名称/类型从首条结果取
            m.setName(a.name);
            m.setKind(a.kind);
            m.setCaseCount(a.caseCount);
            m.setCorrect(a.correct);
            m.setWrong(a.caseCount - a.correct);
            m.setPrecision(p);
            m.setRecall(rc);
            m.setF1(f1);
            nodes.add(m);
        }
        nodes.sort(Comparator.comparingDouble(IntentEvalReportVo.NodeMetric::getF1).reversed());
        report.setPerNode(nodes);

        // 校准桶
        List<IntentEvalReportVo.CalibrationBin> bins = new ArrayList<>();
        for (int i = 0; i < BIN_LABELS.length; i++) {
            IntentEvalReportVo.CalibrationBin b = new IntentEvalReportVo.CalibrationBin();
            b.setBin(BIN_LABELS[i]);
            b.setAcc(binTotal[i] > 0 ? (double) binCorrect[i] / binTotal[i] : 0);
            b.setCount(binTotal[i]);
            bins.add(b);
        }
        report.setCalibration(bins);

        // ★ 总评：把指标翻译成人话，直观判断效果好坏
        buildVerdict(report, nodes, bins);
    }

    /**
     * 基于本次评估指标生成总体评级 + 一句话总评 + 针对性改进建议。
     *
     * 评级阈值与前端 rateClass（0.8/0.6）保持一致：
     *  - excellent：acc1 ≥ 0.85 且无异常且空预测率 ≤ 0.1
     *  - good     ：acc1 ≥ 0.7
     *  - fair     ：acc1 ≥ 0.5
     *  - poor     ：其余
     */
    private void buildVerdict(IntentEvalReportVo report,
                              List<IntentEvalReportVo.NodeMetric> nodes,
                              List<IntentEvalReportVo.CalibrationBin> bins) {
        double acc1 = report.getAccuracy1();
        double emptyRate = report.getEmptyRate();
        double errorRate = report.getErrorRate();

        // 评级
        String grade;
        String gradeLabel;
        if (errorRate > 0.1) {
            grade = "poor"; gradeLabel = "需排查";
        } else if (acc1 >= 0.85 && emptyRate <= 0.1) {
            grade = "excellent"; gradeLabel = "优秀";
        } else if (acc1 >= 0.7) {
            grade = "good"; gradeLabel = "良好";
        } else if (acc1 >= 0.5) {
            grade = "fair"; gradeLabel = "一般";
        } else {
            grade = "poor"; gradeLabel = "较差";
        }
        report.setGrade(grade);
        report.setGradeLabel(gradeLabel);

        // 一句话总评
        report.setSummary(String.format(
                "%d 条用例，Top1 准确率 %s，Top3 准确率 %s，平均置信度 %s。",
                report.getTotal(), pct(acc1), pct(report.getAccuracy3()), pct(report.getAvgTop1Score())));

        // 改进建议：只列出本次实际暴露的问题
        List<String> tips = new ArrayList<>();

        if (errorRate > 0.1) {
            tips.add(String.format("异常率 %s 偏高，部分用例调用 LLM 失败，优先检查模型可用性与限流配置。",
                    pct(errorRate)));
        }

        if (emptyRate > 0.2) {
            tips.add(String.format("空预测率 %s 偏高，较多问题无法命中任何意图，可补充意图配置或适当调低 minScore。",
                    pct(emptyRate)));
        }

        if (acc1 < 0.7 && errorRate <= 0.1) {
            tips.add(String.format("Top1 准确率仅 %s，误判较多，建议对照「误判案例」逐条排查路由问题。", pct(acc1)));
        }

        // 低 F1 节点（召回不足，最该补 examples）。
        // ★ 排除「期望空预测」的虚拟类（nodeId 为空 / name 为 null），它们不是真实意图，
        //   给它们提"补 examples"的建议没有意义。
        List<String> weakNodes = nodes.stream()
                .filter(n -> n.getCaseCount() > 0 && n.getF1() < 0.7
                        && n.getNodeId() != null && !n.getNodeId().isEmpty()
                        && n.getName() != null && !n.getName().isEmpty())
                .map(n -> String.format("「%s」(F1=%s)", n.getName(), pct(n.getF1())))
                .toList();
        if (!weakNodes.isEmpty()) {
            tips.add("以下意图分类较弱，建议补充/修正其 examples 与描述：" + String.join("、", weakNodes) + "。");
        }

        // 置信度校准：高分桶命中率低 = 置信度不可信
        for (IntentEvalReportVo.CalibrationBin b : bins) {
            if (b.getCount() > 0 && b.getAcc() < 0.7 && b.getBin().contains("0.8")) {
                tips.add(String.format("高分区间(%s)命中率仅 %s，模型置信度虚高，建议上调 minScore 阈值。",
                        b.getBin(), pct(b.getAcc())));
                break;
            }
        }

        if (tips.isEmpty()) {
            tips.add("各项指标良好，无明显短板，可继续扩充测试集覆盖更多边界场景。");
        }
        report.setTips(tips);
    }

    /** 百分比文案（与前端 pct 对齐） */
    private static String pct(double v) {
        return String.format("%.1f%%", v * 100);
    }

    /** 把 Top1 score 映射到校准桶下标 */
    private int binOf(Double score) {
        double s = score == null ? 0 : score;
        for (int i = 0; i < BIN_EDGES.length - 1; i++) {
            double lo = BIN_EDGES[i];
            double hi = BIN_EDGES[i + 1];
            boolean ok = i == BIN_EDGES.length - 2 ? (s >= lo && s <= hi) : (s >= lo && s < hi);
            if (ok) return i;
        }
        return 0;
    }

    private static double safeDiv(int a, int b) {
        return b == 0 ? 0 : (double) a / b;
    }

    /** perNode 聚合中间态 */
    private static class Agg {
        int caseCount;
        int correct;
        String name;
        String kind;
    }
}
