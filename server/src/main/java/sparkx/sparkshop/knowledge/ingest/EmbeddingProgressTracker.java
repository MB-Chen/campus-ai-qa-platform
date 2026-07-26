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

import org.springframework.stereotype.Component;
import sparkx.sparkshop.knowledge.vo.IngestionSummary;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 向量化进度追踪器（内存态，进程内可见）。
 *
 * <p>背景：{@code embedding} 是异步任务（{@code @Async}），前端文档列表轮询时
 * 只能看到 {@code status=processing}，看不到「已经跑了多久 / 进行到哪一步」。
 * 本追踪器在 embedding 开始/结束时写入每个文档的当前阶段状态，
 * {@code page()} 查询时回填到 DocumentVo，前端轮询自然就能看到实时进度。
 *
 * <p>线程安全：用 {@link ConcurrentHashMap}，embedding 异步线程写、HTTP 请求线程读，
 * 读写不冲突。不持久化（进程重启丢失，但 embedding 也会重跑，可接受）。
 *
 * <p>清理：embed 结束后保留记录一段时间（{@link #RETAIN_MS}），让前端最后一次轮询
 * 能看到终态（success/failed），超时后由 {@link #cleanupStale()} 清除避免内存泄漏。
 */
@Component
public class EmbeddingProgressTracker {

    /** 终态记录保留时长（5 分钟）：让前端轮询能看到 done/failed，超时清除。 */
    private static final long RETAIN_MS = 5 * 60 * 1000L;

    /** 单条进度记录：StageStat + 记录写入时刻（用于过期清理）。 */
    private static final class Entry {
        final IngestionSummary.StageStat stat;
        final long updatedAt;
        Entry(IngestionSummary.StageStat stat, long updatedAt) {
            this.stat = stat;
            this.updatedAt = updatedAt;
        }
    }

    /** docId → 当前 embed 阶段状态（running/success/failed）。 */
    private final Map<String, Entry> progress = new ConcurrentHashMap<>();

    /** embed 阶段开始：记录 running 状态 + 起始时刻（durationMs 实时算）。 */
    public void markRunning(String docId) {
        if (docId == null || docId.isBlank()) return;
        progress.put(docId, new Entry(
                new IngestionSummary.StageStat("embed", "向量化", 0, "running", null),
                System.currentTimeMillis()));
    }

    /**
     * embed 阶段结束：记录终态（success/failed）+ 真实耗时。
     * 调用方负责传入 stopwatch 计出的 durationMs。
     */
    public void markFinished(String docId, long durationMs, String status, String detail) {
        if (docId == null || docId.isBlank()) return;
        progress.put(docId, new Entry(
                new IngestionSummary.StageStat("embed", "向量化", durationMs, status, detail),
                System.currentTimeMillis()));
    }

    /**
     * 取某文档的当前 embed 进度（供 DocumentVo 回填用）。
     * running 状态实时算已耗时（now - 起始时刻）；终态原样返回。
     * 无记录返回 null。
     */
    public IngestionSummary.StageStat get(String docId) {
        if (docId == null || docId.isBlank()) return null;
        Entry e = progress.get(docId);
        if (e == null) return null;
        if ("running".equals(e.stat.getStatus())) {
            long live = System.currentTimeMillis() - e.updatedAt;
            return new IngestionSummary.StageStat("embed", "向量化",
                    Math.max(0, live), "running", null);
        }
        return e.stat;
    }

    /** 清除过期记录（终态超过 RETAIN_MS 的）。由 page() 顺带调用，不另起线程。 */
    public void cleanupStale() {
        long now = System.currentTimeMillis();
        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, Entry> e : progress.entrySet()) {
            if (!"running".equals(e.getValue().stat.getStatus())
                    && now - e.getValue().updatedAt > RETAIN_MS) {
                stale.add(e.getKey());
            }
        }
        for (String k : stale) progress.remove(k);
    }
}
