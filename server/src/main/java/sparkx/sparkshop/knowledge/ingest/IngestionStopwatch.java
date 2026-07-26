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

import sparkx.sparkshop.knowledge.vo.IngestionSummary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 文档入库阶段计时器（轻量、线程不安全 —— 单文件处理用）。
 *
 * <p>覆盖所有解析引擎：在每个处理阶段开始调 {@link #start(String)}，结束调
 * {@link #stop(String, String, String)}，{@link #build(String)} 出最终统计。
 *
 * <p>容错设计：某阶段忘了 stop、或 stop 了未 start，都不会抛异常，仅丢弃脏数据，
 * 不影响后续阶段计时 —— 因为各阶段是独立 key，互不依赖。
 *
 * <p>典型用法：
 * <pre>
 * IngestionStopwatch sw = new IngestionStopwatch();
 * sw.start("parse");
 * try {
 *     doc = parser.parse(is);
 *     sw.stop("parse", "success", null);
 * } catch (Exception e) {
 *     sw.stop("parse", "failed", e.getMessage());
 *     throw e;
 * }
 * IngestionSummary summary = sw.build("mineru");
 * </pre>
 */
public class IngestionStopwatch {

    /** 阶段标签（key → 中文 label），与前端柱状图阶段保持一致。 */
    private static final Map<String, String> STAGE_LABELS = new LinkedHashMap<>();
    static {
        // 保序：parse → chunk → persist → embed，前端按此顺序渲染
        STAGE_LABELS.put("parse", "解析");
        STAGE_LABELS.put("chunk", "分块");
        STAGE_LABELS.put("persist", "入库");
        STAGE_LABELS.put("embed", "向量化");
    }

    /** 每个阶段当前开始时刻（nanoTime），key=阶段；stop 后从此移除。 */
    private final Map<String, Long> startNanos = new LinkedHashMap<>();
    /** 已完成阶段的结果（保序）。 */
    private final Map<String, IngestionSummary.StageStat> finished = new LinkedHashMap<>();

    /** 开始某个阶段计时。重复 start 同一阶段会覆盖开始时刻（以最后一次为准）。 */
    public void start(String stage) {
        if (stage == null || stage.isBlank()) return;
        startNanos.put(stage, System.nanoTime());
    }

    /**
     * 结束某个阶段并记录耗时与状态。
     *
     * @param stage  阶段 key（需先 start 过）
     * @param status success/failed/skipped
     * @param detail 附加说明（可空）
     */
    public void stop(String stage, String status, String detail) {
        if (stage == null || stage.isBlank()) return;
        Long startNs = startNanos.remove(stage);
        if (startNs == null) {
            // 没 start 过直接 stop：忽略，避免脏数据
            return;
        }
        long durationMs = Math.max(0, (System.nanoTime() - startNs) / 1_000_000);
        String label = STAGE_LABELS.getOrDefault(stage, stage);
        finished.put(stage, new IngestionSummary.StageStat(stage, label, durationMs,
                status == null ? "success" : status, detail));
    }

    /** 把当前已完成阶段快照成 List（前端轮询进度用，顺序 = STAGE_LABELS 顺序）。 */
    public List<IngestionSummary.StageStat> snapshotStages() {
        List<IngestionSummary.StageStat> out = new ArrayList<>();
        for (String key : STAGE_LABELS.keySet()) {
            IngestionSummary.StageStat s = finished.get(key);
            if (s != null) out.add(s);
            else if (startNanos.containsKey(key)) {
                // 进行中：输出 running + 实时已耗时
                long live = Math.max(0, (System.nanoTime() - startNanos.get(key)) / 1_000_000);
                out.add(new IngestionSummary.StageStat(key, STAGE_LABELS.get(key), live, "running", null));
            }
        }
        return out;
    }

    /** 合并另一个 stopwatch 已完成的阶段（用于把预览阶段 parse/chunk 并入 save 阶段的统计）。 */
    public void mergeFinished(IngestionStopwatch other) {
        if (other == null) return;
        for (Map.Entry<String, IngestionSummary.StageStat> e : other.finished.entrySet()) {
            // 不覆盖自己已记录的（避免 save 路径下被预览阶段旧数据覆盖）
            finished.putIfAbsent(e.getKey(), e.getValue());
        }
    }

    /**
     * 直接记录一个已完成的阶段（绕过 start/stop，用于接收外部已计的耗时，如前端透传的 parse/chunk）。
     * 若该阶段已被本 stopwatch 计过（start/stop 过），保留本地计时，忽略外部值。
     */
    public void recordStage(String key, String label, long durationMs, String status, String detail) {
        recordStage(key, label, durationMs, status, detail, false);
    }

    /**
     * 同 {@link #recordStage(String, String, long, String, String)}，但 {@code force=true} 时
     * 强制覆盖已存在的阶段（用于 embed 阶段刚跑完，要覆盖历史 JSON 里的 skipped 占位）。
     */
    public void recordStage(String key, String label, long durationMs, String status, String detail, boolean force) {
        if (key == null || key.isBlank()) return;
        if (!force && finished.containsKey(key)) return; // 非强制模式：本地已计，不覆盖
        String lbl = label != null ? label : STAGE_LABELS.getOrDefault(key, key);
        finished.put(key, new IngestionSummary.StageStat(key, lbl, durationMs,
                status == null ? "success" : status, detail));
    }

    /** {@link #recordStage} 的便捷重载，直接接收前端透传的 StageStat 列表。 */
    public void recordStages(java.util.List<IngestionSummary.StageStat> stages) {
        recordStages(stages, false);
    }

    /**
     * 同 {@link #recordStages(java.util.List)}，但 {@code force=true} 时强制覆盖已存在的阶段。
     * 用于 embed 阶段刚跑完时，覆盖历史 JSON 里的 skipped 占位。
     */
    public void recordStages(java.util.List<IngestionSummary.StageStat> stages, boolean force) {
        if (stages == null) return;
        for (IngestionSummary.StageStat s : stages) {
            if (s == null || s.getKey() == null) continue;
            recordStage(s.getKey(), s.getLabel(), s.getDurationMs(), s.getStatus(), s.getDetail(), force);
        }
    }

    /** 生成最终统计（总耗时 = 各阶段耗时之和，简化模型：跨阶段无重叠）。 */
    public IngestionSummary build(String engine) {
        IngestionSummary s = new IngestionSummary();
        s.setEngine(engine);
        List<IngestionSummary.StageStat> stages = new ArrayList<>();
        long total = 0;
        for (String key : STAGE_LABELS.keySet()) {
            IngestionSummary.StageStat st = finished.get(key);
            if (st == null) {
                // 跳过的阶段（此路径未执行，如 embed 走另一入口）：标 skipped
                st = new IngestionSummary.StageStat(key, STAGE_LABELS.get(key), 0, "skipped", null);
            }
            stages.add(st);
            if (st.getDurationMs() > 0 && !"skipped".equals(st.getStatus())) {
                total += st.getDurationMs();
            }
        }
        s.setStages(stages);
        s.setTotalMs(total);
        return s;
    }

    /** 是否已记录过某阶段（用于判断是否需要 merge 预览阶段的 parse/chunk）。 */
    public boolean hasStage(String stage) {
        return finished.containsKey(stage);
    }

    /** ms → 人类可读（前端也可复用），与 WeKnora 格式Duration 对齐。 */
    public static String formatDuration(long ms) {
        if (ms < 1000) return Math.round(ms) + "ms";
        if (ms < 60000) return String.format(Locale.ROOT, "%.2fs", ms / 1000.0);
        long mins = ms / 60000;
        double rem = (ms % 60000) / 1000.0;
        return String.format(Locale.ROOT, "%dm%.1fs", mins, rem);
    }
}
