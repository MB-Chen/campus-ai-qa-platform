// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.evaluation.service;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import sparkx.sparkshop.evaluation.validate.RagEvalValidate;
import sparkx.sparkshop.evaluation.vo.EvalProbeVo;
import sparkx.sparkshop.evaluation.vo.RagEvalReportVo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * RAG 检索评估服务（纯 Java 标准库，无 LLM，秒级出结果）。
 * <p>
 * 对每个用例调 {@link EvalProbeService#evalProbe} 跑真实 RAG 链路取检索证据，
 * 然后算 Hit@K / Recall@5 / MRR@10 / 误拒率 / 过召回率 / 首字延迟。
 * <p>
 * 与 Python 侧（sparkx-ragas）的分工：Java 做实时单智能体可视化评估（前端面板），
 * Python 做离线批量深度评测（含 RAGAS LLM-judge + 报告产出 + A/B 对比）。
 * 两者指标口径一致（Hit@K/Recall/MRR），可交叉验证。
 */
@Slf4j
@Service
public class EvaluationService {

    @Resource
    private EvalProbeService evalProbeService;

    @Resource
    @Qualifier("intentClassifyExecutor")
    private ExecutorService executor;

    /**
     * 跑检索评估并产出报告。
     *
     * @param req 入参（agentId + cases）
     * @return 评估报告
     */
    public RagEvalReportVo evalRetrieval(RagEvalValidate req) {
        long start = System.currentTimeMillis();
        RagEvalReportVo report = new RagEvalReportVo();
        List<RagEvalValidate.RagEvalCase> cases = req.getCases();
        String agentId = req.getAgentId();

        if (cases == null || cases.isEmpty()) {
            report.setTotal(0);
            report.setCostMs(System.currentTimeMillis() - start);
            report.setDetails(Collections.emptyList());
            report.setGrade("poor");
            report.setGradeLabel("无数据");
            report.setSummary("尚无用例，请添加评估用例后评估。");
            report.setTips(List.of());
            return report;
        }

        report.setTotal(cases.size());

        // 并行跑每条 case（复用意图分类线程池）
        List<CompletableFuture<RagEvalReportVo.CaseDetail>> futures = cases.stream()
                .map(c -> CompletableFuture.supplyAsync(() -> runOne(agentId, c), executor))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<RagEvalReportVo.CaseDetail> details = futures.stream()
                .map(f -> {
                    try {
                        return f.get();
                    } catch (Exception e) {
                        RagEvalReportVo.CaseDetail d = new RagEvalReportVo.CaseDetail();
                        d.setError(true);
                        d.setErrorMsg(e.getMessage());
                        return d;
                    }
                })
                .collect(Collectors.toCollection(ArrayList::new));

        aggregate(details, report);
        report.setCostMs(System.currentTimeMillis() - start);
        return report;
    }

    /** 跑单条用例（异常降级） */
    private RagEvalReportVo.CaseDetail runOne(String agentId, RagEvalValidate.RagEvalCase c) {
        RagEvalReportVo.CaseDetail d = new RagEvalReportVo.CaseDetail();
        d.setQuery(c.getQuery());
        d.setDifficulty(c.getDifficulty() == null ? "medium" : c.getDifficulty());
        d.setRequiresRag(c.isRequiresRag());
        d.setExpectedDocIds(c.getExpectedDocIds() == null ? Collections.emptyList() : c.getExpectedDocIds());
        try {
            EvalProbeVo probe = evalProbeService.evalProbe(agentId, c.getQuery());
            List<String> retrieved = probe.getRetrievedDocIds() == null
                    ? Collections.emptyList() : probe.getRetrievedDocIds();
            d.setRetrievedDocIds(retrieved);
            d.setDocCount(retrieved.size());
            d.setFirstTokenMs(probe.getFirstTokenMs() == null ? 0 : probe.getFirstTokenMs());
            d.setTotalCost(probe.getTotalCost() == null ? 0 : probe.getTotalCost());
            d.setResponsePreview(StrUtil.isBlank(probe.getResponse()) ? ""
                    : (probe.getResponse().length() > 200 ? probe.getResponse().substring(0, 200) + "..." : probe.getResponse()));
            if ("error".equals(probe.getFinalStatus())) {
                d.setError(true);
                d.setErrorMsg(probe.getError());
            }
            // 算 Hit@5 / Recall@5 / MRR
            Set<String> ref = new HashSet<>(d.getExpectedDocIds());
            if (c.isRequiresRag() && !ref.isEmpty()) {
                d.setHit5(hitAtK(retrieved, ref, 5));
                d.setRecall5(recallAtK(retrieved, ref, 5));
                d.setMrr(mrrAtK(retrieved, ref, 10));
            }
        } catch (Exception e) {
            log.warn("[RagEval] 单条评估失败 query=\"{}\": {}", c.getQuery(), e.getMessage());
            d.setError(true);
            d.setErrorMsg(e.getMessage());
            d.setRetrievedDocIds(Collections.emptyList());
        }
        return d;
    }

    /** 汇总指标 + 评级 + 建议 */
    private void aggregate(List<RagEvalReportVo.CaseDetail> details, RagEvalReportVo report) {
        // 检索 eligible：requires_rag=true 且 expectedDocIds 非空
        List<RagEvalReportVo.CaseDetail> retrievalEligible = details.stream()
                .filter(d -> d.isRequiresRag() && d.getExpectedDocIds() != null && !d.getExpectedDocIds().isEmpty())
                .toList();
        // 应走兜底的样本（requires_rag=false）
        List<RagEvalReportVo.CaseDetail> nonRag = details.stream()
                .filter(d -> !d.isRequiresRag())
                .toList();

        report.setHit1(meanHit(retrievalEligible, 1));
        report.setHit3(meanHit(retrievalEligible, 3));
        report.setHit5(meanHit(retrievalEligible, 5));
        report.setRecall5(retrievalEligible.isEmpty() ? 0
                : retrievalEligible.stream().mapToDouble(RagEvalReportVo.CaseDetail::getRecall5).average().orElse(0));
        report.setMrr10(retrievalEligible.isEmpty() ? 0
                : retrievalEligible.stream().mapToDouble(RagEvalReportVo.CaseDetail::getMrr).average().orElse(0));

        // 误拒率：requires_rag=true 但召回为空
        long refusalCount = retrievalEligible.stream().filter(d -> d.getDocCount() == 0).count();
        report.setRefusalRate(retrievalEligible.isEmpty() ? 0 : (double) refusalCount / retrievalEligible.size());

        // 过召回率：requires_rag=false 却走了召回
        long overCount = nonRag.stream().filter(d -> d.getDocCount() > 0).count();
        report.setOverRetrievalRate(nonRag.isEmpty() ? 0 : (double) overCount / nonRag.size());

        // 延迟均值
        List<RagEvalReportVo.CaseDetail> withTiming = details.stream()
                .filter(d -> d.getFirstTokenMs() > 0).toList();
        report.setTtftMeanMs(withTiming.isEmpty() ? 0
                : withTiming.stream().mapToLong(RagEvalReportVo.CaseDetail::getFirstTokenMs).average().orElse(0));
        List<RagEvalReportVo.CaseDetail> withTotal = details.stream()
                .filter(d -> d.getTotalCost() > 0).toList();
        report.setTotalMeanMs(withTotal.isEmpty() ? 0
                : withTotal.stream().mapToLong(RagEvalReportVo.CaseDetail::getTotalCost).average().orElse(0));

        // 异常样本排到后面
        details.sort(Comparator.comparing(RagEvalReportVo.CaseDetail::isError));
        report.setDetails(details);

        buildVerdict(report);
    }

    /** 评级 + 总评 + 建议 */
    private void buildVerdict(RagEvalReportVo report) {
        double hit5 = report.getHit5();
        double refusal = report.getRefusalRate();

        String grade;
        String gradeLabel;
        if (hit5 >= 0.85 && refusal <= 0.05) {
            grade = "excellent";
            gradeLabel = "优秀";
        } else if (hit5 >= 0.7) {
            grade = "good";
            gradeLabel = "良好";
        } else if (hit5 >= 0.5) {
            grade = "fair";
            gradeLabel = "一般";
        } else {
            grade = "poor";
            gradeLabel = "较差";
        }
        report.setGrade(grade);
        report.setGradeLabel(gradeLabel);

        report.setSummary(String.format(
                "%d 条用例，Hit@5 %.1f%%，Recall@5 %.1f%%，MRR@10 %.3f，误拒率 %.1f%%。",
                report.getTotal(), hit5 * 100, report.getRecall5() * 100,
                report.getMrr10(), refusal * 100));

        List<String> tips = new ArrayList<>();
        if (hit5 < 0.7) {
            tips.add(String.format("Hit@5 仅 %.1f%%，召回不足，建议检查 embedding 模型/相似度阈值/topK 或扩充知识库。", hit5 * 100));
        }
        if (refusal > 0.05) {
            tips.add(String.format("误拒率 %.1f%% 偏高，该召回的问题召回为空，检查知识库覆盖或调低检索阈值。", refusal * 100));
        }
        if (report.getOverRetrievalRate() > 0.1) {
            tips.add(String.format("过召回率 %.1f%% 偏高，不该走检索的问题走了检索，检查意图路由（闲聊/兜底是否正确短路）。",
                    report.getOverRetrievalRate() * 100));
        }
        if (tips.isEmpty()) {
            tips.add("各项指标良好，可继续扩充评估集覆盖更多边界场景。");
        }
        report.setTips(tips);
    }

    /** eligible 样本上 Hit@K 的均值 */
    private static double meanHit(List<RagEvalReportVo.CaseDetail> eligible, int k) {
        if (eligible.isEmpty()) {
            return 0;
        }
        return (double) eligible.stream()
                .mapToInt(d -> hitAtK(d.getRetrievedDocIds(), new HashSet<>(d.getExpectedDocIds()), k))
                .sum() / eligible.size();
    }

    /** Hit@K：topK 是否命中任一期望文档（0/1） */
    private static int hitAtK(List<String> retrieved, Set<String> ref, int k) {
        if (retrieved == null || retrieved.isEmpty() || ref == null || ref.isEmpty()) {
            return 0;
        }
        Set<String> topk = new LinkedHashSet<>(retrieved.subList(0, Math.min(k, retrieved.size())));
        topk.retainAll(ref);
        return topk.isEmpty() ? 0 : 1;
    }

    /** Recall@K：topK 命中期望文档的比例（0~1） */
    private static double recallAtK(List<String> retrieved, Set<String> ref, int k) {
        if (retrieved == null || retrieved.isEmpty() || ref == null || ref.isEmpty()) {
            return 0.0;
        }
        Set<String> topk = new LinkedHashSet<>(retrieved.subList(0, Math.min(k, retrieved.size())));
        topk.retainAll(ref);
        return (double) topk.size() / ref.size();
    }

    /** MRR@K：第一条命中文档排名的倒数（0~1） */
    private static double mrrAtK(List<String> retrieved, Set<String> ref, int k) {
        if (retrieved == null || retrieved.isEmpty() || ref == null || ref.isEmpty()) {
            return 0.0;
        }
        int limit = Math.min(k, retrieved.size());
        for (int i = 0; i < limit; i++) {
            if (ref.contains(retrieved.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }
}
