// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.config;

import com.alibaba.ttl.threadpool.TtlExecutors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 异步任务 + 专用线程池配置（移植自 sparkxV2）。
 *
 * 两类执行器：
 *  - RAG 任务执行器（{@code ragTaskExecutor}）：I/O 密集型异步任务（OCR/入库/摘要触发）。
 *  - ★ TTL 透传线程池：并行检索/意图分类/记忆加载/流式读取，
 *    用 TransmittableThreadLocal 包装，跨线程透传 {@code UserContext}。
 *    满载用 CallerRunsPolicy 降级同步执行，不丢任务。
 *
 * 注意：当前 JDK 17 不支持虚拟线程（需 JDK 21+），所有执行器均使用平台线程线程池。
 *
 * 说明：@EnableAsync 已在 SparkxApplication 上开启，本类不再重复声明。
 */
@Configuration
public class AsyncConfig {

    private static final int CPU = Math.max(2, Runtime.getRuntime().availableProcessors());

    /**
     * RAG 任务执行器：I/O 密集型（LLM 调用、OCR）。
     * 使用 TTL 透传固定线程池；虚拟线程需 JDK 21+，当前 JDK 17 暂不启用。
     */
    @Bean("ragTaskExecutor")
    public Executor ragTaskExecutor() {
        return ttlFixed("rag-task-", CPU * 8, 500);
    }

    // 工作负载特征各异，独立线程池避免相互拖累；均用 TtlExecutors 包装以透传上下文。

    /** 通道级并行检索（多个检索通道并发） */
    @Bean("ragRetrievalExecutor")
    public ExecutorService ragRetrievalExecutor() {
        return ttlFixed("rag-retrieve-", CPU * 4, 200);
    }

    /** 子问题级并行（每个子问题独立线程） */
    @Bean("ragContextExecutor")
    public ExecutorService ragContextExecutor() {
        return ttlFixed("rag-context-", CPU * 4, 200);
    }

    /** 意图并行分类（每个子问题独立调 LLM） */
    @Bean("intentClassifyExecutor")
    public ExecutorService intentClassifyExecutor() {
        return ttlFixed("intent-classify-", CPU * 2, 100);
    }

    /** MCP 工具并行调用（本阶段未启用，预留） */
    @Bean("mcpBatchExecutor")
    public ExecutorService mcpBatchExecutor() {
        return ttlFixed("mcp-batch-", CPU * 2, 100);
    }

    /**
     * 试切预览文件级并发解析（多文件扇出）。
     * 独立池避免与 RAG 异步任务/检索抢资源；用 TTL 透传保证 UserContext 跨线程传递。
     * 对齐 WeKnora 批内并发思路（errgroup.SetLimit + 保序结果聚合）。
     */
    @Bean("previewParseExecutor")
    public ExecutorService previewParseExecutor() {
        return ttlFixed("preview-parse-", CPU * 4, 200);
    }

    /** 会话摘要异步压缩 */
    @Bean("memorySummaryExecutor")
    public ExecutorService memorySummaryExecutor() {
        return ttlFixed("memory-summary-", 2, 50);
    }

    /** 记忆并行加载（摘要 + 历史） */
    @Bean("memoryLoadExecutor")
    public ExecutorService memoryLoadExecutor() {
        return ttlFixed("memory-load-", 4, 50);
    }

    /**
     * 流式 SSE 读取专用线程池。
     * 刻意用平台线程（非虚拟线程）：OkHttp SSE 阻塞读取在虚拟线程上偶有兼容性问题，
     * 且流式任务长尾，独立线程池避免占用并行检索线程。
     */
    @Bean("streamingExecutor")
    public ExecutorService streamingExecutor() {
        return ttlCached("stream-read-", CPU * 2);
    }


    /** 固定大小 + 有界队列 + TTL 透传 + CallerRunsPolicy（满则降级同步执行） */
    private ExecutorService ttlFixed(String prefix, int size, int queueSize) {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                size, size, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueSize),
                namedFactory(prefix),
                new ThreadPoolExecutor.CallerRunsPolicy());
        pool.allowCoreThreadTimeOut(true);
        return TtlExecutors.getTtlExecutorService(pool);
    }

    /** 缓存线程池（适合长尾、稀疏的流式任务） + TTL 透传 */
    private ExecutorService ttlCached(String prefix, int max) {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, max, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                namedFactory(prefix),
                new ThreadPoolExecutor.CallerRunsPolicy());
        return TtlExecutors.getTtlExecutorService(pool);
    }

    private static ThreadFactory namedFactory(String prefix) {
        AtomicInteger seq = new AtomicInteger(0);
        return r -> {
            Thread t = new Thread(r, prefix + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
    }
}
