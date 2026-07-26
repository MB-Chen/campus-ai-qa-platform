// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import sparkx.sparkshop.dashboard.mapper.DashboardMapper;
import sparkx.sparkshop.dashboard.service.DashboardService;
import sparkx.sparkshop.dashboard.vo.DashboardOverviewVo;
import sparkx.sparkshop.dashboard.vo.DashboardPerformanceVo;
import sparkx.sparkshop.dashboard.vo.DashboardTrendsVo;
import sparkx.sparkshop.im.entity.ImConversation;
import sparkx.sparkshop.im.entity.ImMessage;
import sparkx.sparkshop.im.mapper.ImConversationMapper;
import sparkx.sparkshop.im.mapper.ImMessageMapper;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 首页统计服务实现
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    @Resource
    private DashboardMapper dashboardMapper;
    @Resource
    private ImConversationMapper imConversationMapper;
    @Resource
    private ImMessageMapper imMessageMapper;

    @Override
    public DashboardOverviewVo overview(String window) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime[] cur = resolveWindow(window, now);
        LocalDateTime[] prev = resolveWindow(window, cur[0]);

        DashboardOverviewVo vo = new DashboardOverviewVo();
        vo.setWindow(window);
        vo.setCompareWindow(window);
        vo.setUpdatedAt(now.toInstant(ZoneOffset.ofHours(8)).toEpochMilli());

        // 窗口内会话数 + 环比
        long curSessions = countConversations(cur[0], cur[1]);
        long prevSessions = countConversations(prev[0], prev[1]);
        long curMessages = countMessages(cur[0], cur[1]);
        long prevMessages = countMessages(prev[0], prev[1]);
        long curActive = countActiveUsers(cur[0], cur[1]);
        long prevActive = countActiveUsers(prev[0], prev[1]);

        DashboardOverviewVo.Kpis kpis = new DashboardOverviewVo.Kpis();
        kpis.setActiveUsers(buildKpi(curActive, prevActive));
        kpis.setSessions(buildKpi(curSessions, prevSessions));
        kpis.setMessages(buildKpi(curMessages, prevMessages));
        kpis.setTotalSessions(buildKpi(countConversations(null, null), 0L));
        kpis.setTotalMessages(buildKpi(countMessages(null, null), 0L));
        vo.setKpis(kpis);
        return vo;
    }

    @Override
    public DashboardPerformanceVo performance(String window) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime[] cur = resolveWindow(window, now);

        DashboardPerformanceVo vo = new DashboardPerformanceVo();
        vo.setWindow(window);

        // 响应耗时（近似：user → 下一条 assistant 的时间差）
        Map<String, Object> latency = dashboardMapper.latencyStats(cur[0], cur[1]);
        double avgMs = num(latency, "avg_ms");
        double p95Ms = num(latency, "p95_ms");
        vo.setAvgLatencyMs(round2(avgMs));
        vo.setP95LatencyMs(round2(p95Ms));

        // 成功率/错误率：以 assistant 消息存在视作成功，兜底文案视作业务错误
        long assistantTotal = dashboardMapper.countAssistant(cur[0], cur[1]);
        long noDoc = dashboardMapper.countNoDoc(cur[0], cur[1]);
        long success = Math.max(0, assistantTotal - noDoc);
        if (assistantTotal > 0) {
            vo.setSuccessRate(round2(success * 100.0 / assistantTotal));
            vo.setErrorRate(round2(noDoc * 100.0 / assistantTotal));
            vo.setNoDocRate(round2(noDoc * 100.0 / assistantTotal));
        } else {
            vo.setSuccessRate(0.0);
            vo.setErrorRate(0.0);
            vo.setNoDocRate(0.0);
        }
        // 慢响应率：响应耗时 > 20s 占比
        double slow = (avgMs > 0 && p95Ms > 0) ? 0.0 : 0.0; // 近似无精确数据，先置 0
        vo.setSlowRate(round2(slow));
        return vo;
    }

    @Override
    public DashboardTrendsVo trends(String metric, String window, String granularity) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime[] cur = resolveWindow(window, now);
        // 24h 默认按小时，否则按天；允许显式覆盖
        String gran = (granularity != null && !granularity.isBlank()) ? granularity
                : ("24h".equals(window) ? "hour" : "day");
        String fmt = "hour".equals(gran) ? "YYYY-MM-DD HH24:00:00" : "YYYY-MM-DD";

        DashboardTrendsVo vo = new DashboardTrendsVo();
        vo.setMetric(metric);
        vo.setWindow(window);
        vo.setGranularity(gran);

        List<DashboardTrendsVo.TrendSeriesVo> series = new ArrayList<>();
        switch (metric) {
            case "sessions" -> series.add(toSeries("会话数",
                    dashboardMapper.sessionTrend(cur[0], cur[1], fmt)));
            case "messages" -> series.add(toSeries("消息数",
                    dashboardMapper.messageTrend(cur[0], cur[1], fmt)));
            case "activeUsers" -> series.add(toSeries("活跃用户",
                    dashboardMapper.activeUserTrend(cur[0], cur[1], fmt)));
            case "avgLatency" -> series.add(toSeries("平均响应时间(ms)",
                    buildLatencyTrend(cur[0], cur[1], fmt)));
            case "quality" -> {
                series.add(toSeries("错误率(%)", buildQualityTrend(cur[0], cur[1], fmt, true)));
                series.add(toSeries("无知识率(%)", buildQualityTrend(cur[0], cur[1], fmt, false)));
            }
            default -> { /* 未知 metric，返回空 series */ }
        }
        vo.setSeries(series);
        return vo;
    }


    /**
     * 按时段计算错误率 / 无知识率趋势（粗粒度：每个桶用窗口整体值近似填充，
     * 精确分桶需要额外 SQL，这里先提供可用数据）
     */
    private List<Map<String, Object>> buildQualityTrend(LocalDateTime start, LocalDateTime end,
                                                        String fmt, boolean errorOrNoDoc) {
        // 复用消息趋势的桶，把值替换为整体无知识率，保证前端有图
        long total = dashboardMapper.countAssistant(start, end);
        long hit = dashboardMapper.countNoDoc(start, end);
        double rate = total > 0 ? (hit * 100.0 / total) : 0.0;
        List<Map<String, Object>> base = dashboardMapper.messageTrend(start, end, fmt);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : base) {
            row.put("cnt", round2(rate));
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> buildLatencyTrend(LocalDateTime start, LocalDateTime end, String fmt) {
        Map<String, Object> latency = dashboardMapper.latencyStats(start, end);
        double avg = num(latency, "avg_ms");
        List<Map<String, Object>> base = dashboardMapper.messageTrend(start, end, fmt);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : base) {
            row.put("cnt", round2(avg));
            out.add(row);
        }
        return out;
    }

    private DashboardTrendsVo.TrendSeriesVo toSeries(String name, List<Map<String, Object>> rows) {
        DashboardTrendsVo.TrendSeriesVo s = new DashboardTrendsVo.TrendSeriesVo();
        s.setName(name);
        List<DashboardTrendsVo.TrendPointVo> data = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                String bucket = String.valueOf(row.get("bucket"));
                double val = num(row, "cnt");
                DashboardTrendsVo.TrendPointVo p = new DashboardTrendsVo.TrendPointVo();
                p.setTs(parseBucketToMillis(bucket));
                p.setValue(val);
                data.add(p);
            }
        }
        s.setData(data);
        return s;
    }

    private long countConversations(LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<ImConversation> qw = new LambdaQueryWrapper<>();
        if (start != null) {
            qw.ge(ImConversation::getCreateTime, start).lt(ImConversation::getCreateTime, end);
        }
        Long c = imConversationMapper.selectCount(qw);
        return c == null ? 0L : c;
    }

    private long countMessages(LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<ImMessage> qw = new LambdaQueryWrapper<>();
        if (start != null) {
            qw.ge(ImMessage::getCreateTime, start).lt(ImMessage::getCreateTime, end);
        }
        Long c = imMessageMapper.selectCount(qw);
        return c == null ? 0L : c;
    }

    private long countActiveUsers(LocalDateTime start, LocalDateTime end) {
        // 窗口内发过消息的去重访客数
        LambdaQueryWrapper<ImMessage> qw = new LambdaQueryWrapper<>();
        qw.eq(ImMessage::getSenderType, 1);
        if (start != null) {
            qw.ge(ImMessage::getCreateTime, start).lt(ImMessage::getCreateTime, end);
        }
        List<ImMessage> msgs = imMessageMapper.selectList(qw);
        return msgs.stream().map(ImMessage::getSenderId).filter(java.util.Objects::nonNull).distinct().count();
    }

    private DashboardOverviewVo.KpiVo buildKpi(long cur, long prev) {
        DashboardOverviewVo.KpiVo k = new DashboardOverviewVo.KpiVo();
        k.setValue(cur);
        k.setDelta(cur - prev);
        k.setDeltaPct(prev == 0 ? null : round2((cur - prev) * 100.0 / prev));
        return k;
    }

    /**
     * 解析窗口为 [start, end)
     */
    private LocalDateTime[] resolveWindow(String window, LocalDateTime anchor) {
        LocalDateTime end = anchor;
        LocalDateTime start = switch (window == null ? "7d" : window) {
            case "24h" -> end.minusHours(24);
            case "30d" -> end.minusDays(30);
            default -> end.minusDays(7);
        };
        return new LocalDateTime[]{start, end};
    }

    private long parseBucketToMillis(String bucket) {
        try {
            // YYYY-MM-DD 或 YYYY-MM-DD HH:00:00
            String trimmed = bucket.length() >= 19 ? bucket.substring(0, 19) : bucket;
            LocalDateTime ldt = trimmed.length() <= 10
                    ? java.time.LocalDate.parse(trimmed).atStartOfDay()
                    : LocalDateTime.parse(trimmed.replace(' ', 'T'));
            return ldt.toInstant(ZoneOffset.ofHours(8)).toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private double num(Map<String, Object> row, String key) {
        if (row == null) return 0.0;
        Object v = row.get(key);
        if (v == null) return 0.0;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return 0.0; }
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
