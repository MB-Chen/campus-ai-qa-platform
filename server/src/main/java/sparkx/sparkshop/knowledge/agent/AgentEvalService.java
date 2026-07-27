// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.agent;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sparkx.sparkshop.knowledge.entity.KnowledgeAgent;
import sparkx.sparkshop.knowledge.infra.LLMService;
import sparkx.sparkshop.knowledge.infra.chat.LlmChatRequest;
import sparkx.sparkshop.knowledge.service.IKnowledgeAgentService;
import sparkx.sparkshop.knowledge.validate.AgentEvalValidate;
import sparkx.sparkshop.knowledge.vo.AgentEvalReportVo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 智能体评估服务（LLM-as-judge）。
 * <p>
 * 对每个测试问题：① 跑智能体管线取 answer + references；② 用 LLM 按 5 维打分
 * （relevance/accuracy/completeness/groundedness/conciseness，0-10）；③ 聚合报告。
 * <p>
 * 报告骨架移植自 {@code IntentEvalService}（并行 + 降级 + 分桶 + 总评），
 * 把"意图命中"换成"回答质量多维评分"。
 */
@Slf4j
@Service
public class AgentEvalService {

    /** 5 个评分维度（key → 中文 label） */
    private static final LinkedHashMap<String, String> DIMENSIONS = new LinkedHashMap<>();
    static {
        DIMENSIONS.put("relevance", "相关性");
        DIMENSIONS.put("accuracy", "准确性");
        DIMENSIONS.put("completeness", "完整性");
        DIMENSIONS.put("groundedness", "事实依据");
        DIMENSIONS.put("conciseness", "简洁性");
    }

    /** 综合分分桶（0~10 划 4 桶） */
    private static final double[] BIN_EDGES = {0, 4, 6, 8, 10};
    private static final String[] BIN_LABELS = {"[0,4)", "[4,6)", "[6,8)", "[8,10]"};

    /** JSON 提取正则（容错：模型可能把 JSON 包在 markdown ``` 里或带前后文案） */
    private static final Pattern JSON_PATTERN = Pattern.compile("\\{[\\s\\S]*\\}");

    @Resource
    private IKnowledgeAgentService agentService;

    @Resource
    private AgentChatService agentChatService;

    @Resource
    private LLMService llmService;

    @Resource(name = "intentClassifyExecutor")
    private ExecutorService executor;

    /**
     * 批量评估并产出报告。
     */
    public AgentEvalReportVo runEval(AgentEvalValidate req) {
        long start = System.currentTimeMillis();
        AgentEvalReportVo report = new AgentEvalReportVo();
        List<AgentEvalValidate.AgentEvalCase> cases = req.getCases();

        if (cases == null || cases.isEmpty()) {
            report.setTotal(0);
            report.setCostMs(System.currentTimeMillis() - start);
            report.setDimensions(emptyDimensions());
            report.setScoreBins(emptyBins());
            report.setDetails(List.of());
            report.setGrade("poor");
            report.setGradeLabel("无数据");
            report.setSummary("尚无用例，点击「AI 生成测试集」或手动添加后评估。");
            report.setTips(List.of());
            return report;
        }

        KnowledgeAgent agent = agentService.getById(req.getAgentId());
        if (agent == null) {
            throw new sparkx.sparkshop.common.exception.BusinessException("智能体不存在");
        }

        report.setTotal(cases.size());

        // 并行跑每条 case
        List<CompletableFuture<AgentEvalReportVo.CaseDetail>> futures = cases.stream()
                .map(c -> CompletableFuture.supplyAsync(() -> runOne(agent, c), executor))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<AgentEvalReportVo.CaseDetail> details = futures.stream()
                .map(f -> {
                    try {
                        return f.get();
                    } catch (Exception e) {
                        AgentEvalReportVo.CaseDetail d = new AgentEvalReportVo.CaseDetail();
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

    /**
     * AI 生成测试集：按智能体关联的知识库文档，用 LLM 生成问题。
     */
    public List<String> genSeedQuestions(AgentEvalValidate req) {
        int count = req.getSampleCount() == null || req.getSampleCount() <= 0 ? 3 : req.getSampleCount();
        // 借助智能体自身能力：构造一个「请生成问题」的 prompt，跑智能体取答案
        KnowledgeAgent agent = agentService.getById(req.getAgentId());
        if (agent == null) {
            throw new sparkx.sparkshop.common.exception.BusinessException("智能体不存在");
        }
        String prompt = String.format(
                "请基于你关联的知识库内容，生成 %d 个用户可能会问的典型问题，每行一个，" +
                        "只输出问题本身，不要编号、不要解释、不要前后缀。\n要求：问题具体、覆盖不同主题、口语化。", count);
        AgentChatService.ChatResult result = agentChatService.chatSync(agent, prompt);
        String text = result.answer == null ? "" : result.answer;
        return Arrays.stream(text.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                // 去掉可能的行首序号 "1." "1、" 等
                .map(s -> s.replaceFirst("^\\d+[.、)）]\\s*", ""))
                .filter(s -> !s.isEmpty())
                .limit(count * 2L)
                .collect(Collectors.toList());
    }


    /** 跑单条：问答 + LLM 打分（异常降级为 error） */
    private AgentEvalReportVo.CaseDetail runOne(KnowledgeAgent agent, AgentEvalValidate.AgentEvalCase c) {
        AgentEvalReportVo.CaseDetail d = new AgentEvalReportVo.CaseDetail();
        d.setQuery(c.getQuery());
        d.setExpectedAnswer(c.getExpectedAnswer());
        d.setNote(c.getNote());
        try {
            // 1. 跑智能体取回答 + 引用
            AgentChatService.ChatResult result = agentChatService.chatSync(agent, c.getQuery());
            d.setAnswer(result.answer);
            d.setReferenceCount(result.references != null ? result.references.size() : 0);
            if (result.error) {
                d.setError(true);
                d.setErrorMsg(result.errorMsg);
                return d;
            }
            // 2. LLM-as-judge 打分
            JudgeResult jr = judge(c.getQuery(), result.answer, c.getExpectedAnswer(),
                    result.references != null && !result.references.isEmpty());
            d.setRelevance(jr.relevance);
            d.setAccuracy(jr.accuracy);
            d.setCompleteness(jr.completeness);
            d.setGroundedness(jr.groundedness);
            d.setConciseness(jr.conciseness);
            d.setOverall(jr.overall());
            d.setComment(jr.comment);
        } catch (Exception e) {
            log.warn("[AgentEval] 单条评估失败 query=\"{}\": {}", c.getQuery(), e.getMessage());
            d.setError(true);
            d.setErrorMsg(e.getMessage());
        }
        return d;
    }

    /** LLM-as-judge：对 (query, answer) 按 5 维打分，返回 JSON */
    private JudgeResult judge(String query, String answer, String expectedAnswer, boolean hasReferences) {
        String sys = "你是一个严格的知识库问答评估员。请根据用户问题、参考答案（若有）和实际回答，" +
                "从 5 个维度打分（0-10，整数或一位小数）：\n" +
                "- relevance 相关性：回答是否切题\n" +
                "- accuracy 准确性：回答内容是否正确\n" +
                "- completeness 完整性：是否完整回答了问题\n" +
                "- groundedness 事实依据：回答是否基于检索到的知识（有引用时才判，无引用则该项给 0）\n" +
                "- conciseness 简洁性：回答是否简洁无冗余\n" +
                "请严格只输出一个 JSON，格式：{\"relevance\":8.0,\"accuracy\":7.5,\"completeness\":8.0,\"groundedness\":7.0,\"conciseness\":8.0,\"comment\":\"一句话评价\"}";
        StringBuilder user = new StringBuilder();
        user.append("【用户问题】").append(query).append("\n\n");
        if (StrUtil.isNotBlank(expectedAnswer)) {
            user.append("【参考答案】").append(expectedAnswer).append("\n\n");
        }
        user.append("【检索到知识引用】").append(hasReferences ? "有" : "无").append("\n\n");
        user.append("【实际回答】").append(StrUtil.isBlank(answer) ? "(空回答)" : answer).append("\n\n");
        user.append("请输出打分 JSON。");

        String resp = llmService.chat(LlmChatRequest.of(sys, user.toString(), 0.0));
        return parseJudge(resp, hasReferences);
    }

    /** 解析 LLM 返回的 JSON（容错） */
    private JudgeResult parseJudge(String resp, boolean hasReferences) {
        JudgeResult jr = new JudgeResult();
        if (StrUtil.isBlank(resp)) {
            jr.comment = "评估无返回";
            return jr;
        }
        Matcher m = JSON_PATTERN.matcher(resp);
        String json = m.find() ? m.group() : resp;
        // 简易提取（避免引入 JSON 库依赖，评估对精度要求不高）
        jr.relevance = extractDouble(json, "relevance");
        jr.accuracy = extractDouble(json, "accuracy");
        jr.completeness = extractDouble(json, "completeness");
        jr.groundedness = hasReferences ? extractDouble(json, "groundedness") : 0.0;
        jr.conciseness = extractDouble(json, "conciseness");
        jr.comment = extractString(json, "comment");
        return jr;
    }

    private double extractDouble(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*([0-9.]+)");
        Matcher m = p.matcher(json);
        if (m.find()) {
            try {
                double v = Double.parseDouble(m.group(1));
                return Math.max(0, Math.min(10, v));
            } catch (NumberFormatException ignore) {
            }
        }
        return 0.0;
    }

    private String extractString(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : "";
    }


    /** 汇总指标 + 维度均分 + 分桶 + 总评 */
    private void aggregate(List<AgentEvalReportVo.CaseDetail> details, AgentEvalReportVo report) {
        int total = details.size();
        int error = 0;
        int valid = 0;
        double overallSum = 0;
        Map<String, Double> dimSum = new LinkedHashMap<>();
        for (String k : DIMENSIONS.keySet()) dimSum.put(k, 0.0);

        int[] binCount = new int[BIN_LABELS.length];

        for (AgentEvalReportVo.CaseDetail d : details) {
            if (d.isError()) {
                error++;
                continue;
            }
            valid++;
            overallSum += d.getOverall();
            dimSum.merge("relevance", d.getRelevance(), Double::sum);
            dimSum.merge("accuracy", d.getAccuracy(), Double::sum);
            dimSum.merge("completeness", d.getCompleteness(), Double::sum);
            dimSum.merge("groundedness", d.getGroundedness(), Double::sum);
            dimSum.merge("conciseness", d.getConciseness(), Double::sum);
            int bin = binOf(d.getOverall());
            binCount[bin]++;
        }

        report.setErrorRate(safeDiv(error, total));
        report.setAvgScore(valid > 0 ? overallSum / valid : 0);

        // 维度均分
        List<AgentEvalReportVo.DimensionScore> dims = new ArrayList<>();
        for (Map.Entry<String, String> e : DIMENSIONS.entrySet()) {
            AgentEvalReportVo.DimensionScore ds = new AgentEvalReportVo.DimensionScore();
            ds.setKey(e.getKey());
            ds.setLabel(e.getValue());
            double sum = dimSum.getOrDefault(e.getKey(), 0.0);
            ds.setScore(valid > 0 ? sum / valid : 0);
            dims.add(ds);
        }
        report.setDimensions(dims);

        // 分桶
        List<AgentEvalReportVo.ScoreBin> bins = new ArrayList<>();
        for (int i = 0; i < BIN_LABELS.length; i++) {
            AgentEvalReportVo.ScoreBin b = new AgentEvalReportVo.ScoreBin();
            b.setBin(BIN_LABELS[i]);
            b.setCount(binCount[i]);
            bins.add(b);
        }
        report.setScoreBins(bins);

        // 典型案例按综合分降序（高分在前，便于看最佳/最差）
        details.sort(Comparator.comparingDouble(
                (AgentEvalReportVo.CaseDetail x) -> x.isError() ? -1 : x.getOverall()).reversed());
        report.setDetails(details);

        buildVerdict(report, dims, valid, error, total);
    }

    /** 基于指标生成评级 + 总评 + 建议 */
    private void buildVerdict(AgentEvalReportVo report, List<AgentEvalReportVo.DimensionScore> dims,
                              int valid, int error, int total) {
        double avg = report.getAvgScore();
        double errorRate = report.getErrorRate();

        String grade;
        String gradeLabel;
        if (errorRate > 0.3) {
            grade = "poor";
            gradeLabel = "需排查";
        } else if (avg >= 8.0) {
            grade = "excellent";
            gradeLabel = "优秀";
        } else if (avg >= 6.5) {
            grade = "good";
            gradeLabel = "良好";
        } else if (avg >= 5.0) {
            grade = "fair";
            gradeLabel = "一般";
        } else {
            grade = "poor";
            gradeLabel = "较差";
        }
        report.setGrade(grade);
        report.setGradeLabel(gradeLabel);

        report.setSummary(String.format("共 %d 条用例，平均综合分 %.1f/10（有效 %d 条，异常 %d 条）。",
                total, avg, valid, error));

        List<String> tips = new ArrayList<>();
        if (errorRate > 0.1) {
            tips.add(String.format("异常率 %.0f%% 偏高，部分用例生成失败，请检查模型可用性。", errorRate * 100));
        }
        // 找最弱维度
        dims.stream()
                .min(Comparator.comparingDouble(AgentEvalReportVo.DimensionScore::getScore))
                .ifPresent(weak -> {
                    if (weak.getScore() < 6.0) {
                        tips.add(String.format("「%s」维度较弱（%.1f 分），建议针对性优化。", weak.getLabel(), weak.getScore()));
                    }
                });
        // 事实依据低 → 幻觉风险
        dims.stream().filter(d -> "groundedness".equals(d.getKey())).findFirst()
                .ifPresent(g -> {
                    if (g.getScore() < 5.0 && valid > 0) {
                        tips.add("事实依据分偏低，回答可能存在幻觉（编造知识库外的内容），建议调高相似度阈值或补充知识库。");
                    }
                });
        if (tips.isEmpty()) {
            tips.add("各项指标良好，可继续扩充测试集覆盖更多边界场景。");
        }
        report.setTips(tips);
    }

    private int binOf(double score) {
        for (int i = 0; i < BIN_EDGES.length - 1; i++) {
            double lo = BIN_EDGES[i];
            double hi = BIN_EDGES[i + 1];
            boolean ok = i == BIN_EDGES.length - 2 ? (score >= lo && score <= hi) : (score >= lo && score < hi);
            if (ok) return i;
        }
        return 0;
    }

    private List<AgentEvalReportVo.DimensionScore> emptyDimensions() {
        return DIMENSIONS.entrySet().stream().map(e -> {
            AgentEvalReportVo.DimensionScore ds = new AgentEvalReportVo.DimensionScore();
            ds.setKey(e.getKey());
            ds.setLabel(e.getValue());
            ds.setScore(0);
            return ds;
        }).collect(Collectors.toList());
    }

    private List<AgentEvalReportVo.ScoreBin> emptyBins() {
        List<AgentEvalReportVo.ScoreBin> bins = new ArrayList<>();
        for (String label : BIN_LABELS) {
            AgentEvalReportVo.ScoreBin b = new AgentEvalReportVo.ScoreBin();
            b.setBin(label);
            b.setCount(0);
            bins.add(b);
        }
        return bins;
    }

    private static double safeDiv(int a, int b) {
        return b == 0 ? 0 : (double) a / b;
    }

    /** LLM 打分中间结果 */
    private static class JudgeResult {
        double relevance;
        double accuracy;
        double completeness;
        double groundedness;
        double conciseness;
        String comment;

        double overall() {
            return (relevance + accuracy + completeness + groundedness + conciseness) / 5.0;
        }
    }
}
