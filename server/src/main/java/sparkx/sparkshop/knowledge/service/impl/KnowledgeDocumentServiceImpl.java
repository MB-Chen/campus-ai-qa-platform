// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import sparkx.sparkshop.knowledge.service.MinioService;
import sparkx.sparkshop.common.exception.BusinessException;
import sparkx.sparkshop.knowledge.config.RagProperties;
import sparkx.sparkshop.knowledge.entity.ChunkEntity;
import sparkx.sparkshop.knowledge.entity.KgExtractionRecord;
import sparkx.sparkshop.knowledge.entity.KnowledgeBase;
import sparkx.sparkshop.knowledge.entity.KnowledgeDocument;
import sparkx.sparkshop.knowledge.entity.KnowledgeQuestion;
import sparkx.sparkshop.knowledge.ingest.AdaptiveDocumentSplitter;
import sparkx.sparkshop.knowledge.ingest.DocumentIngestService;
import sparkx.sparkshop.knowledge.ingest.DocumentTypeRouter;
import sparkx.sparkshop.knowledge.ingest.IngestionStopwatch;
import sparkx.sparkshop.knowledge.ingest.ParentChildSplitter;
import sparkx.sparkshop.knowledge.ingest.SpreadsheetRowSplitter;
import sparkx.sparkshop.knowledge.infra.EmbeddingModelProvider;
import sparkx.sparkshop.knowledge.infra.LLMService;
import sparkx.sparkshop.knowledge.infra.TsVectorGenerator;
import sparkx.sparkshop.knowledge.infra.chat.LlmChatRequest;
import sparkx.sparkshop.knowledge.mapper.ChunkMapper;
import sparkx.sparkshop.knowledge.mapper.KnowledgeBaseMapper;
import sparkx.sparkshop.knowledge.mapper.KnowledgeDocumentMapper;
import sparkx.sparkshop.knowledge.mapper.KnowledgeQuestionMapper;
import sparkx.sparkshop.knowledge.mapper.ParentChunkMapper;
import sparkx.sparkshop.knowledge.graph.GraphRepository;
import sparkx.sparkshop.knowledge.service.IKnowledgeDocumentService;
import sparkx.sparkshop.knowledge.validate.DocumentListValidate;
import sparkx.sparkshop.knowledge.validate.DocumentSaveValidate;
import sparkx.sparkshop.knowledge.validate.KbQuestionGenValidate;
import sparkx.sparkshop.knowledge.validate.PreviewValidate;
import sparkx.sparkshop.knowledge.vo.ChunkVo;
import sparkx.sparkshop.knowledge.vo.DocumentDetailVo;
import sparkx.sparkshop.knowledge.vo.DocumentPreviewVo;
import sparkx.sparkshop.knowledge.vo.DocumentSaveProgressVo;
import sparkx.sparkshop.knowledge.vo.DocumentVo;
import sparkx.sparkshop.knowledge.vo.IngestionSummary;
import sparkx.sparkshop.knowledge.vo.PreviewChunkVo;
import sparkx.sparkshop.knowledge.vo.PreviewProgressVo;
import sparkx.sparkshop.knowledge.vo.TaskIdVo;
import sparkx.sparkshop.system.vo.PageResult;
import dev.langchain4j.model.embedding.EmbeddingModel;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 知识库文档业务实现。
 *
 * 上传流程：落 MinIO（knowledge/<uuid>.<ext>）→ 建文档记录（pending）
 *          → 调 DocumentIngestService.ingest 解析+分块+向量化（同步）。
 *
 * 注意：DocumentIngestService.ingest 内部会再建一条 document 记录，
 * 为避免重复，上传侧先建 pending 占位记录并传给引擎做后续处理；
 * 若引擎内部重复插入则捕获并容忍（DB 主键冲突走更新）。
 */
@Slf4j
@Service
public class KnowledgeDocumentServiceImpl implements IKnowledgeDocumentService {

    private static final Pattern QUESTION_PATTERN =
            Pattern.compile("<question>(.*?)</question>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** 入库进度桶 Redis key 前缀（与问题导入范式一致，独立命名空间避免冲突） */
    private static final String SAVE_PROGRESS_KEY_PREFIX = "knowledge:document:save:";

    /** 入库进度桶 TTL（1 小时，与问题导入一致，超时自动清理无残留） */
    private static final Duration SAVE_PROGRESS_TTL = Duration.ofHours(1);

    /** 预览进度桶 Redis key 前缀（mineru 异步预览专用，与入库桶独立命名空间） */
    private static final String PREVIEW_PROGRESS_KEY_PREFIX = "knowledge:document:preview:";

    /** 预览进度桶 TTL（1 小时，含切片结果，超时自动清理） */
    private static final Duration PREVIEW_PROGRESS_TTL = Duration.ofHours(1);

    /** CPU 核数（用于自适应推导预览并发度） */
    private static final int CPU = Math.max(2, Runtime.getRuntime().availableProcessors());

    /**
     * 文件级并发上限（对齐 WeKnora IngestMapParallel≈10 量级，按 CPU 自适应）。
     * 限流避免打爆下游（LLM/MinIO/MinerU）。
     */
    private static final int PREVIEW_PARALLELISM = Math.max(4, Math.min(12, CPU * 2));

    /** 并发预览整体超时（分钟，兜底；超时后未完成的填空 VO，不阻塞前端轮询） */
    private static final long PREVIEW_TIMEOUT_MIN = 30L;

    /**
     * 批量向量化文档级并发上限（写死 2，防止打爆 embedding 服务器 QPS 上限）。
     * 注意：切片级向量化仍是串行（{@link #embedExistingChunks} 内的 for 循环），
     * 真正容易瞬时打爆 QPS 的是切片并发，文档级 2 路并发是吞吐与限流的平衡点。
     */
    private static final int EMBED_PARALLELISM = 2;

    /** 局部 ObjectMapper（不暴露为 Bean，避免破坏全局自动配置） */
    private final ObjectMapper mapper = new ObjectMapper();

    @Resource
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Resource
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Resource
    private ChunkMapper chunkMapper;

    @Resource
    private GraphRepository graphRepository;

    @Resource
    private ParentChunkMapper parentChunkMapper;

    @Resource
    private KnowledgeQuestionMapper knowledgeQuestionMapper;

    @Resource
    private MinioService minioService;

    @Resource
    private DocumentIngestService documentIngestService;

    @Resource
    private LLMService llmService;

    @Resource
    private DocumentTypeRouter documentTypeRouter;

    @Resource
    private EmbeddingModelProvider embeddingModelProvider;

    @Resource
    private RagProperties ragProperties;

    /**
     * Redisson 客户端：用于异步入库的进度桶（与问题导入范式一致）。
     * 项目已通过 RedissonConfig 注册 Bean，这里直接注入。
     */
    @Resource
    private RedissonClient redisson;

    /** 向量化进度追踪器（内存态，记录每个文档 embed 阶段的实时状态，供文档列表回填） */
    @Resource
    private sparkx.sparkshop.knowledge.ingest.EmbeddingProgressTracker embeddingProgressTracker;

    /**
     * 知识图谱服务：删除文档 / 关闭文档级 KG 开关时，调 deleteByDocument 做完整清理
     * （Neo4j + kg_entity + 抽取记录）。用 @Lazy 防御潜在的循环依赖。
     */
    @Lazy
    @Resource
    private sparkx.sparkshop.knowledge.service.KnowledgeGraphService knowledgeGraphService;

    /** 图谱抽取记录 mapper：文档列表回填抽取状态用 */
    @Resource
    private sparkx.sparkshop.knowledge.mapper.KgExtractionRecordMapper kgExtractionRecordMapper;

    /**
     * 自注入代理对象：用于在 {@link #triggerGenerateKbQuestions} 中调 {@link #generateKbQuestions}（{@code @Async}），
     * 以及在 {@link #save} 中调 {@link #doSaveAsync}（{@code @Async}）。
     * 同类内直接 this 调用会绕过 Spring AOP 代理导致 {@code @Async} 失效，必须走代理。
     * 用 {@code @Lazy} 打破「自己依赖自己」的启动期循环依赖。
     */
    @Lazy
    @Resource
    private IKnowledgeDocumentService self;

    /**
     * 试切预览文件级并发解析专用线程池（{@code previewParseExecutor}）。
     * 独立池避免与 RAG 异步任务/检索抢资源；TTL 透传保证 UserContext 跨线程传递。
     * 用于 {@link #runPreviewParallel} / {@link #doPreviewAsync} 的多文件并发扇出。
     */
    @Resource(name = "previewParseExecutor")
    private ExecutorService previewParseExecutor;

    /**
     * RAG 异步任务执行器（{@code ragTaskExecutor}）。
     * 这里显式注入为 handle，用于 {@link #embedding} 方法内部文档级并发扇出
     * （{@code CompletableFuture.supplyAsync(..., ragTaskExecutor)} + Semaphore 限流）。
     * 本身也是 {@code @Async("ragTaskExecutor")} 标注的执行器，复用同一池不新增资源。
     */
    @Resource(name = "ragTaskExecutor")
    private ExecutorService ragTaskExecutor;

    /** 分页查询文档列表（支持按知识库 + 关键词过滤）。 */
    @Override
    public PageResult<DocumentVo> page(DocumentListValidate query) {
        LambdaQueryWrapper<KnowledgeDocument> wrapper = new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(query.getKbId() != null && !query.getKbId().isBlank(), KnowledgeDocument::getKbId, query.getKbId())
                .orderByDesc(KnowledgeDocument::getCreatedAt);
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.like(KnowledgeDocument::getFileName, query.getKeyword());
        }
        IPage<KnowledgeDocument> mpPage = new Page<>(query.safePage(), query.safeSize());
        IPage<KnowledgeDocument> result = knowledgeDocumentMapper.selectPage(mpPage, wrapper);
        // 顺带清理过期的 embed 进度记录（终态超过保留时长的），避免内存泄漏
        embeddingProgressTracker.cleanupStale();
        List<DocumentVo> vos = result.getRecords().stream().map(d -> {
            DocumentVo vo = toVo(d);
            // processing 文档回填实时 embed 进度（含已耗时），其他状态不回填
            if ("processing".equals(d.getStatus())) {
                vo.setEmbedProgress(embeddingProgressTracker.get(d.getId()));
            }
            return vo;
        }).collect(Collectors.toList());

        // 批量回填图谱抽取状态（kg_extraction_record 按 document_id 关联，避免 N+1）
        if (!vos.isEmpty()) {
            java.util.List<String> docIds = vos.stream()
                    .map(DocumentVo::getId).filter(java.util.Objects::nonNull).toList();
            if (!docIds.isEmpty()) {
                // 原生 SQL 按 document_id 批量查最新记录，取每个文档最新一条
                java.util.List<KgExtractionRecord> records =
                        kgExtractionRecordMapper.selectLatestByDocumentIds(docIds);
                java.util.Map<String, KgExtractionRecord> recordMap = new java.util.HashMap<>();
                for (KgExtractionRecord r : records) {
                    recordMap.putIfAbsent(r.getDocumentId(), r); // 已按 updated_at DESC 排序，首条即最新
                }
                for (DocumentVo vo : vos) {
                    KgExtractionRecord r = recordMap.get(vo.getId());
                    if (r != null) {
                        vo.setKgExtractStatus(r.getStatus());
                        vo.setKgEntityCount(r.getEntityCount());
                    }
                }
            }
        }

        return new PageResult<>(vos, result.getTotal());
    }

    /** 上传文档：落 MinIO → 建 pending 文档记录 → 同步调引擎入库（解析 + 分块 + 向量化）。 */
    @Override
    public DocumentVo upload(MultipartFile file, String kbId, String engine) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null) {
            throw new BusinessException("知识库不存在");
        }
        String originalName = file.getOriginalFilename();
        String ext = extractExt(originalName);
        String objectName = "knowledge/" + UUID.randomUUID().toString().replace("-", "") + "." + ext;

        try {
            minioService.upload(objectName, file);
        } catch (Exception e) {
            log.error("[DocUpload] MinIO 上传失败: {}", e.getMessage(), e);
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }

        // 建文档记录（pending）
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId("doc_" + UUID.randomUUID().toString().replace("-", ""));
        doc.setKbId(kbId);
        doc.setFileName(originalName);
        doc.setFileSize(file.getSize());
        doc.setStorageUrl(objectName);
        doc.setStatus("pending");
        doc.setQuestionStatus(1);
        doc.setActive(1);
        LocalDateTime now = LocalDateTime.now();
        doc.setCreatedAt(now);
        doc.setUpdatedAt(now);
        knowledgeDocumentMapper.insert(doc);

        // 同步入库：调引擎（它会建自己的 document 记录，这里用单独 id 避免冲突）
        String ingestEngine = engine == null || engine.isBlank() ? "tika" : engine;
        IngestionStopwatch sw = new IngestionStopwatch();
        sw.start("parse");
        String parseStatus = "success";
        String parseDetail = null;
        try {
            documentIngestService.ingest(file, kbId, ingestEngine);
            sw.stop("parse", "success", null);
            // 引擎跑完后刷新本记录状态
            doc.setStatus("done");
            doc.setUpdatedAt(LocalDateTime.now());
            knowledgeDocumentMapper.updateById(doc);
        } catch (Exception e) {
            sw.stop("parse", "failed", e.getMessage());
            log.error("[DocUpload] 入库失败: {}", e.getMessage(), e);
            doc.setStatus("failed");
            doc.setUpdatedAt(LocalDateTime.now());
            knowledgeDocumentMapper.updateById(doc);
            // 失败也落一份耗时统计，方便排查
            saveIngestionSummarySafely(doc.getId(), sw, ingestEngine);
            throw new BusinessException("文档入库失败: " + e.getMessage());
        }
        // 落入库耗时统计（此路径 ingest 内部已分块+向量化，统一记到 parse 阶段）
        saveIngestionSummarySafely(doc.getId(), sw, ingestEngine);
        return toVo(doc);
    }

    /** 同步将指定文档置为 processing 状态（供异步 embedding 触发前调用，让前端立即看到"向量化中"）。 */
    @Override
    public void markProcessing(List<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        for (String docId : documentIds) {
            KnowledgeDocument doc = knowledgeDocumentMapper.selectById(docId);
            if (doc == null) continue;
            doc.setStatus("processing");
            doc.setUpdatedAt(now);
            knowledgeDocumentMapper.updateById(doc);
        }
    }

    /**
     * 异步向量化指定文档（由 {@code ragTaskExecutor} 执行）。
     *
     * <p>状态机：Controller 先同步 {@link #markProcessing} 置 processing（前端立即看到），
     * 再触发本方法；本方法跑完置 done / failed。
     *
     * <p>★ 文档级并发：批量向量化时按 {@link #EMBED_PARALLELISM}（=2）路并发扇出，
     * 用 {@link Semaphore} 限流防止打爆 embedding 服务器 QPS 上限。
     * 切片级向量化仍是串行（{@link #embedExistingChunks} 内的 for 循环），
     * 因为切片并发才是真正容易瞬时打爆 QPS 的地方。
     *
     * <p>★ 按 {@code doc.kbId} 动态解析 embedding 模型（修复「ai_model 表配置不生效」问题）：
     * 走 {@link EmbeddingModelProvider#resolve(String)} 链式查
     * {@code knowledge_base.embedding_model_id → ai_model} 构造对应模型，
     * 解析失败回退 yml 兜底。
     *
     * <p>★ 「重新向量化」语义：先清空该文档切片的旧向量（embedding 置 NULL），
     * 再用当前 KB 绑定的模型重新生成向量，保留切片文本/用户编辑。彻底避免上一次
     * 失败/陈旧的向量残留污染检索，也避免新旧维度混杂。
     */
    @Override
    @Async("ragTaskExecutor")
    public void embedding(List<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return;
        }
        int n = documentIds.size();
        // 单文档：直接同步执行，省一次线程切换（与 runPreviewParallel 同款快捷分支）
        if (n == 1) {
            embedOneDocument(documentIds.get(0));
            return;
        }
        // 多文档：Semaphore 限流并发扇出（对齐 runPreviewParallel 思路）
        // 每个 future 只写自己索引位，靠 CompletableFuture 的 happens-before 保证可见性，无需加锁
        Semaphore sem = new Semaphore(EMBED_PARALLELISM);
        List<CompletableFuture<Void>> futures = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            final String docId = documentIds.get(i);
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    sem.acquire();
                    try {
                        embedOneDocument(docId);
                    } finally {
                        sem.release();
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("[ReEmbed] 文档向量化被中断 docId={}", docId);
                }
            }, ragTaskExecutor));
        }
        // 阻塞等全部完成（本方法在 @Async 线程跑，阻塞不影响主线程/HTTP 请求）
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    /**
     * 单文档向量化执行体（从原 {@code embedding} for 循环体抽出，便于并发扇出复用）。
     *
     * <p>单文档失败 try-catch 不中断其他文档（与原语义一致）：失败仅记 warn/error +
     * 置 doc.status=failed + 进度追踪器 markFinished(failed)，不抛异常外泄。
     *
     * <p>线程安全保证：依赖的 {@link EmbeddingProgressTracker}（ConcurrentHashMap）、
     * {@link EmbeddingModelProvider#resolve}（ConcurrentHashMap.computeIfAbsent 缓存）、
     * MyBatis mapper 均为线程安全单例，可被多文档并发调用。
     */
    private void embedOneDocument(String docId) {
        KnowledgeDocument doc = knowledgeDocumentMapper.selectById(docId);
        if (doc == null) return;

        // 标记该文档 embed 进行中（前端文档列表轮询能看到实时耗时）
        embeddingProgressTracker.markRunning(docId);
        // 向量化阶段计时：merge 进文档已有 ingestion_summary（保留 parse/chunk/persist 历史）
        IngestionStopwatch sw = new IngestionStopwatch();
        sw.start("embed");
        String embedStatus = "success";
        String embedDetail = null;
        try {
            // 维度一致性校验：DB 列已改为无维度 vector，应用层需保证同 KB 维度一致，
            // 避免同库混入不同维度向量导致查询时 <=> 比较报错。
            KnowledgeBase kb = knowledgeBaseMapper.selectById(doc.getKbId());
            assertDimensionConsistent(kb);

            int chunks;
            long hasDocChunks = chunkMapper.countByDocumentId(docId);
            if (hasDocChunks > 0) {
                // 优先走「对已存在切片重向量」：先清旧向量，再重新 embedding
                chunks = embedExistingChunks(docId, doc.getKbId());
            } else {
                // 老数据（无 document_id，仅 file_name 关联）：重读 MinIO 原文件重 ingest
                chunks = reEmbedByFileName(doc);
            }
            doc.setStatus("done");
            doc.setChunkCount(chunks);
        } catch (BusinessException be) {
            // 维度不一致等业务错误，直接标记失败并提示
            log.warn("[ReEmbed] 文档向量化被拒绝 docId={}: {}", docId, be.getMessage());
            doc.setStatus("failed");
            embedStatus = "failed";
            embedDetail = be.getMessage();
        } catch (Exception e) {
            log.error("[ReEmbed] 文档向量化失败 docId={}: {}", docId, e.getMessage(), e);
            doc.setStatus("failed");
            embedStatus = "failed";
            embedDetail = e.getMessage();
        }
        sw.stop("embed", embedStatus, embedDetail);
        doc.setUpdatedAt(LocalDateTime.now());
        knowledgeDocumentMapper.updateById(doc);
        // 把 embed 阶段 merge 进已有 ingestion_summary（不覆盖 parse/chunk/persist）
        mergeEmbedStageSafely(docId, sw);
        // 更新进度追踪器为终态（保留几分钟让前端看到最终耗时，之后自动清理）
        long embedDurationMs = 0;
        for (IngestionSummary.StageStat st : sw.snapshotStages()) {
            if ("embed".equals(st.getKey())) {
                embedDurationMs = st.getDurationMs();
                break;
            }
        }
        embeddingProgressTracker.markFinished(docId, embedDurationMs, embedStatus, embedDetail);
    }

    /**
     * 把 embed 阶段耗时合并进文档已有的 ingestion_summary（保留 parse/chunk/persist）。
     * 读出已有 JSON → 反序列化 → 把 embed 阶段替换/追加 → 写回。失败不阻断（仅 warn）。
     */
    private void mergeEmbedStageSafely(String docId, IngestionStopwatch embedSw) {
        try {
            KnowledgeDocument doc = knowledgeDocumentMapper.selectById(docId);
            if (doc == null) return;
            IngestionStopwatch merged = new IngestionStopwatch();
            String existing = doc.getIngestionSummary();
            String engine = "tika";
            if (existing != null && !existing.isBlank()) {
                try {
                    IngestionSummary prev = mapper.readValue(existing, IngestionSummary.class);
                    if (prev != null) {
                        merged.recordStages(prev.getStages());
                        if (prev.getEngine() != null) engine = prev.getEngine();
                    }
                } catch (Exception parseEx) {
                    // 旧 JSON 解析失败：从空白开始，仅记录 embed
                    log.debug("[ReEmbed] 解析旧 ingestion_summary 失败，仅记录 embed：{}", parseEx.getMessage());
                }
            }
            // embed 阶段强制覆盖：历史 JSON 里 embed 可能是 build() 占位的 skipped，
            // 必须用本次真实计时覆盖它（其他阶段保留历史，不覆盖）。
            merged.recordStages(embedSw.snapshotStages(), true);
            saveIngestionSummarySafely(docId, merged, engine);
        } catch (Exception e) {
            log.warn("[ReEmbed] 合并 embed 阶段耗时失败 doc={} : {}", docId, e.getMessage());
        }
    }

    /**
     * 重新向量化整个知识库。
     *
     * <p>查出该 KB 下所有文档 id → 同步置 processing（前端轮询立即看到「向量化中」）
     * → 触发 {@link #embedding(List)} 异步执行。复用文档级向量化逻辑，
     * 不重复实现清向量 + embedding + 维度校验。
     */
    @Override
    public void embeddingByKb(String kbId) {
        if (kbId == null || kbId.isBlank()) {
            return;
        }
        List<String> docIds = knowledgeDocumentMapper.selectList(
                        new LambdaQueryWrapper<KnowledgeDocument>().eq(KnowledgeDocument::getKbId, kbId))
                .stream().map(KnowledgeDocument::getId).collect(Collectors.toList());
        if (docIds.isEmpty()) {
            return;
        }
        // 同步置 processing，再异步触发（与文档级 embedding 端点同款编排）
        // ★ 必须走 self 代理，直接 this.embedding() 会绕过 Spring AOP 导致 @Async 失效（退化为同步）
        markProcessing(docIds);
        self.embedding(docIds);
    }

    /**
     * 维度一致性校验：探测 KB 当前绑定 embedding 模型的实际输出维度，
     * 与 {@code kb.dimension}（创建时记录）比对，不一致直接抛业务异常。
     *
     * <p>背景：chunks.embedding 已改为无维度 vector 列，允许不同 KB 用不同维度，
     * 但同一 KB 内必须维度一致，否则该 KB 的向量检索 {@code <=>} 会报维度不匹配。
     * 这里在向量化前做一道防护，避免坏数据落库后被检索时才发现。
     */
    private void assertDimensionConsistent(KnowledgeBase kb) {
        if (kb == null || kb.getEmbeddingModelId() == null) {
            return; // 无绑定模型，走默认兜底，不做校验
        }
        int actual = embeddingModelProvider.probeDimension(
                kb.getEmbeddingModelId(), kb.getEmbeddingModelName());
        if (actual <= 0) {
            // 探测失败（模型不可达）不阻断，让 embed 阶段自然报错
            return;
        }
        Integer recorded = kb.getDimension();
        if (recorded != null && recorded > 0 && actual != recorded) {
            throw new BusinessException("向量化模型维度(" + actual
                    + ")与知识库维度(" + recorded + ")不一致，请检查嵌入模型配置");
        }
    }

    /**
     * 对已存在切片（按 document_id）重向量：先清空旧向量，再逐条 embedding 回填。
     * 不重新解析、不删切片内容，保留用户编辑过的文本。
     *
     * @param docId 文档 id
     * @param kbId  知识库 id（用于按 kb.embedding_model_id 解析对应 embedding 模型）
     * @return 成功补向量的切片数
     */
    private int embedExistingChunks(String docId, String kbId) {
        // 先清空该文档切片的旧向量，保证「先清后建」，避免残留坏向量
        chunkMapper.clearEmbeddingByDocumentId(docId);

        EmbeddingModel model = embeddingModelProvider.resolve(kbId);
        List<ChunkEntity> chunks = chunkMapper.selectByDocumentId(docId, 0, Integer.MAX_VALUE);
        int success = 0;
        for (ChunkEntity c : chunks) {
            String content = c.getContent();
            if (content == null || content.isBlank()) continue;
            try {
                Embedding emb = model.embed(TextSegment.from(content)).content();
                chunkMapper.updateEmbedding(c.getId(), toPgVector(emb));
                success++;
            } catch (Exception e) {
                log.warn("[ReEmbed] 切片补向量失败 chunk={} : {}", c.getId(), e.getMessage());
            }
        }
        return success;
    }

    /**
     * 老数据向量化兜底：切片无 document_id，按 file_name 清除后从 MinIO 重读原文件 ingest。
     */
    private int reEmbedByFileName(KnowledgeDocument doc) throws Exception {
        String fileName = doc.getFileName();
        // metadata 为 jsonb 列，需用 ->>'file_name' 提取后 LIKE，不能对 jsonb 直接 LIKE varchar
        String pattern = "%" + (fileName == null ? "" : fileName) + "%";
        // 覆盖重传兜底：删旧 PG chunk 前先清理图谱该文档贡献，避免 union 累加产生幽灵 chunk id
        try {
            List<String> docChunkIds = chunkMapper.selectIdsByDocumentId(doc.getId());
            graphRepository.deleteByDocument(doc.getKbId(), doc.getId(), docChunkIds);
        } catch (Exception e) {
            log.warn("[ReEmbed] 清理图谱失败 docId={}: {}", doc.getId(), e.getMessage());
        }
        chunkMapper.deleteByKbAndFileName(doc.getKbId(), pattern);
        parentChunkMapper.deleteByKbAndFileName(doc.getKbId(), pattern);
        MultipartFile restored = readBack(doc);
        return documentIngestService.ingest(restored, doc.getKbId(), "tika");
    }

    /**
     * 同步将指定文档置为 questionStatus=2（生成中）。
     * 供异步 {@link #generateKbQuestions} 触发前调用，让前端轮询立即看到状态变化。
     */
    @Override
    public void markQuestionGenerating(List<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        for (String docId : documentIds) {
            KnowledgeDocument doc = knowledgeDocumentMapper.selectById(docId);
            if (doc == null) continue;
            doc.setQuestionStatus(2);
            doc.setUpdatedAt(now);
            knowledgeDocumentMapper.updateById(doc);
        }
    }

    /**
     * 生成问题编排入口：同步置 questionStatus=2 → 通过 self 代理触发异步 {@link #generateKbQuestions}。
     *
     * <p>★ 必须通过 {@code self}（自注入的代理对象）调异步方法，直接 {@code this.generateKbQuestions()}
     * 会绕过 Spring AOP 代理，导致 {@code @Async} 不生效（同步执行 + 无 TTL 上下文）。
     */
    @Override
    public void triggerGenerateKbQuestions(KbQuestionGenValidate validate) {
        if (validate == null || validate.getDocumentIds() == null || validate.getDocumentIds().isEmpty()) {
            return;
        }
        // 1. 同步置 questionStatus=2，前端轮询立即看到「生成中」
        markQuestionGenerating(validate.getDocumentIds());
        // 2. 通过代理触发异步生成（保证 @Async 生效）
        self.generateKbQuestions(validate);
    }

    /**
     * 按文档列表生成问题（异步）。
     *
     * <p>遍历所选文档的每个原文分块，用 LLM 生成 N 个问题：
     * <ul>
     *   <li>每个问题作为独立 chunk 入库（metadata.type=question、source_chunk_id=来源原文 chunk、
     *       document_id、kb_id、file_name），并用文档所属 KB 绑定的 embedding 模型生成向量。</li>
     *   <li>同步写一条 knowledge_question 记录，chunk_id 指向新生成的问题 chunk。</li>
     * </ul>
     *
     * <p>★ embedding 按 {@code doc.kbId} 动态解析（同 KB 缓存复用），保证问题向量与原文向量
     * 同维度、同模型，避免 pgvector 跨维度比较报错。每个 KB 首次解析时做维度一致性校验。
     * ★ 异常不中断整体：单个文档/分块失败仅记 warn，继续处理后续；文档级失败回退 questionStatus=1。
     */
    @Override
    @Async("ragTaskExecutor")
    public void generateKbQuestions(KbQuestionGenValidate validate) {
        List<String> documentIds = validate.getDocumentIds();
        if (documentIds == null || documentIds.isEmpty()) return;

        try {
            doGenerateKbQuestions(validate);
        } catch (Throwable t) {
            // 整体兜底：异步线程未捕获异常会导致任务静默失败，前端 questionStatus 永远停在 2(生成中)。
            // 这里把所有仍处于「生成中」的文档回退为 1，保证前端轮询能停。
            log.error("[GenKbQuestion] 异步任务整体失败，回退生成中状态: {}", t.getMessage(), t);
            rollbackQuestionGenerating(documentIds);
        }
    }

    /** generateKbQuestions 的实际执行体，由外层兜底包裹。 */
    private void doGenerateKbQuestions(KbQuestionGenValidate validate) {
        List<String> documentIds = validate.getDocumentIds();
        int qCount = validate.getQuestionCount() != null && validate.getQuestionCount() > 0
                ? Math.min(validate.getQuestionCount(), 10) : 3;
        Integer modelId = validate.getModelId();

        // kbId -> EmbeddingModel 缓存（同批文档多属同一 KB，避免重复解析）
        Map<String, EmbeddingModel> embModelCache = new HashMap<>();

        for (String docId : documentIds) {
            KnowledgeDocument doc = knowledgeDocumentMapper.selectById(docId);
            if (doc == null) continue;
            String kbId = doc.getKbId();
            int qTotal = 0;
            try {
                // 解析该文档所属 KB 的 embedding 模型（带维度校验，失败回退该文档状态）
                EmbeddingModel embModel = embModelCache.get(kbId);
                if (embModel == null) {
                    KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
                    if (kb == null) {
                        throw new BusinessException("知识库不存在: " + kbId);
                    }
                    assertDimensionConsistent(kb);
                    embModel = embeddingModelProvider.resolve(kbId);
                    embModelCache.put(kbId, embModel);
                }
                // 该文档的所有原文切片（已排除问题切片）
                List<ChunkEntity> contentChunks = chunkMapper.selectContentByDocument(doc.getId());
                for (ChunkEntity src : contentChunks) {
                    String srcContent = src.getContent();
                    if (srcContent == null || srcContent.isBlank()) continue;
                    // 按原文分块逐个生成 N 个问题
                    List<String> questions = generateQuestionsForChunk(srcContent, qCount, modelId);
                    for (String q : questions) {
                        if (q == null || q.isBlank()) continue;
                        String qChunkId = "c_" + UUID.randomUUID().toString().replace("-", "");
                        // 问题向量：用 KB 绑定模型，维度与原文一致
                        String embPg = null;
                        try {
                            Embedding emb = embModel.embed(TextSegment.from(q)).content();
                            embPg = toPgVector(emb);
                        } catch (Exception ee) {
                            log.warn("[GenKbQuestion] 问题向量生成失败 chunk={} : {}", qChunkId, ee.getMessage());
                        }
                        Map<String, Object> metaMap = new LinkedHashMap<>();
                        metaMap.put("type", "question");
                        metaMap.put("source_chunk_id", src.getId());
                        metaMap.put("document_id", doc.getId());
                        metaMap.put("kb_id", kbId);
                        metaMap.put("file_name", doc.getFileName() == null ? "" : doc.getFileName());
                        String meta = metadataToJson(metaMap);
                        // 入库问题切片（含向量 + tsv，保证向量和关键词检索都能命中）
                        chunkMapper.insertWithEmbedding(qChunkId, kbId, q, embPg,
                                TsVectorGenerator.toTsVector(q), meta);
                        // 同步写 knowledge_question 记录，chunk_id 指向问题 chunk（修复历史 bug）
                        KnowledgeQuestion kq = new KnowledgeQuestion();
                        kq.setKbId(kbId);
                        kq.setDocumentId(doc.getId());
                        kq.setChunkId(qChunkId);
                        kq.setContent(q);
                        kq.setSource("ai");
                        kq.setStatus(1);
                        LocalDateTime now = LocalDateTime.now();
                        kq.setCreatedAt(now);
                        kq.setUpdatedAt(now);
                        knowledgeQuestionMapper.insert(kq);
                        qTotal++;
                    }
                }
                doc.setQuestionStatus(3);  // 走完即视为已生成（无原文切片/无问题也归入已生成）
                log.info("[GenKbQuestion] 文档 {} 生成问题 {} 条", doc.getId(), qTotal);
            } catch (Exception e) {
                log.error("[GenKbQuestion] 文档 {} 问题生成失败: {}", doc.getId(), e.getMessage(), e);
                doc.setQuestionStatus(1);
            }
            doc.setUpdatedAt(LocalDateTime.now());
            knowledgeDocumentMapper.updateById(doc);
        }
    }

    /**
     * 异步失败兜底：把指定文档中仍处于「生成中」(questionStatus=2) 的回退为 1(待生成)，
     * 避免前端轮询永远停在「生成中」。已走到 3(已生成) 的不动。
     */
    private void rollbackQuestionGenerating(List<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        for (String docId : documentIds) {
            try {
                KnowledgeDocument doc = knowledgeDocumentMapper.selectById(docId);
                if (doc == null) continue;
                if (doc.getQuestionStatus() != null && doc.getQuestionStatus() == 2) {
                    doc.setQuestionStatus(1);
                    doc.setUpdatedAt(now);
                    knowledgeDocumentMapper.updateById(doc);
                }
            } catch (Exception e) {
                log.warn("[GenKbQuestion] 回退文档状态失败 doc={} : {}", docId, e.getMessage());
            }
        }
    }

    /**
     * 删除文档：级联删除 MinIO 对象 + chunks + parent_chunks + questions + 文档记录。
     *
     * <p>★ 状态守卫：正在向量化（status=processing）或问题生成中（questionStatus=2）的文档
     * 直接拒绝删除并抛业务异常，避免异步任务插回孤儿 chunk/向量化白跑/状态写不回等竞态问题。
     * 由全局异常处理器转成 {code,message} 返回前端，提示用户稍后再删。
     */
    @Override
    public void delete(List<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) return;
        for (String docId : documentIds) {
            KnowledgeDocument doc = knowledgeDocumentMapper.selectById(docId);
            if (doc == null) continue;
            // 守卫：处理中的文档拒绝删除
            if ("processing".equals(doc.getStatus())) {
                throw new BusinessException("文档「" + doc.getFileName() + "」正在向量化，请稍后再删除");
            }
            if (doc.getQuestionStatus() != null && doc.getQuestionStatus() == 2) {
                throw new BusinessException("文档「" + doc.getFileName() + "」正在生成问题，请稍后再删除");
            }
            // 删 MinIO 对象
            if (doc.getStorageUrl() != null && !doc.getStorageUrl().isBlank()) {
                minioService.remove(doc.getStorageUrl());
            }
            // 删图谱（文档级隔离：按 doc_id 直接删 Neo4j 节点 + kg_entity 行 + 抽取记录，干净利落）。
            // 失败不阻断删除主流程（图谱清理是增强项，文档本身删除必须成功）。
            try {
                knowledgeGraphService.deleteByDocument(doc.getKbId(), docId);
            } catch (Exception e) {
                log.warn("[DocService] 清理文档图谱失败 kbId={} docId={}: {}", doc.getKbId(), docId, e.getMessage());
            }
            // 删 chunks（按 metadata.file_name，jsonb 列用 ->> 提取，避免 LIKE varchar 报错）
            String fileName = doc.getFileName();
            String pattern = "%" + (fileName == null ? "" : fileName) + "%";
            chunkMapper.deleteByKbAndFileName(doc.getKbId(), pattern);
            // 删父块
            parentChunkMapper.deleteByKbAndFileName(doc.getKbId(), pattern);
            // 删问题
            knowledgeQuestionMapper.delete(new LambdaQueryWrapper<KnowledgeQuestion>()
                    .eq(KnowledgeQuestion::getDocumentId, docId));
            // 删文档
            knowledgeDocumentMapper.deleteById(docId);
        }
    }

    /**
     * 切换文档级知识图谱开关。
     * 关闭时若该文档已有抽取数据，级联清理其图谱（与删除文档一致的清理逻辑），
     * 避免关闭后图谱残留该文档实体污染召回。
     */
    @Override
    public void toggleKg(String documentId, Integer kgEnabled) {
        if (documentId == null || documentId.isBlank()) {
            throw new BusinessException("documentId 不能为空");
        }
        if (kgEnabled == null || (kgEnabled != 1 && kgEnabled != 2)) {
            throw new BusinessException("kgEnabled 取值非法（1=启用 2=禁用）");
        }
        KnowledgeDocument doc = knowledgeDocumentMapper.selectById(documentId);
        if (doc == null) {
            throw new BusinessException("文档不存在");
        }
        // 值未变化直接返回
        Integer current = doc.getKgEnabled() != null ? doc.getKgEnabled() : 2;
        if (current.equals(kgEnabled)) return;

        doc.setKgEnabled(kgEnabled);
        doc.setUpdatedAt(LocalDateTime.now());
        knowledgeDocumentMapper.updateById(doc);

        // 关闭：自然清理该文档已有的图谱数据（Neo4j + kg_entity + 抽取记录）
        if (kgEnabled == 2) {
            try {
                knowledgeGraphService.deleteByDocument(doc.getKbId(), documentId);
                log.info("[DocService] 关闭文档 KG 开关，已清理图谱数据 docId={}", documentId);
            } catch (Exception e) {
                log.warn("[DocService] 关闭文档 KG 开关时清理图谱失败 docId={}: {}", documentId, e.getMessage());
            }
        }
    }

    /** 文档详情：返回文档基本信息 + 关联切片列表。 */
    @Override
    public DocumentDetailVo detail(String documentId) {
        KnowledgeDocument doc = knowledgeDocumentMapper.selectById(documentId);
        if (doc == null) {
            throw new BusinessException("文档不存在");
        }
        DocumentDetailVo detail = new DocumentDetailVo();
        detail.setDocument(toVo(doc));

        // 切片列表（metadata 为 jsonb，用 ->>'file_name' 提取，避免对 jsonb 列 LIKE varchar）
        String fileName = doc.getFileName();
        String pattern = "%" + (fileName == null ? "" : fileName) + "%";
        List<ChunkEntity> chunks = chunkMapper.selectByKbAndFileName(doc.getKbId(), pattern);
        List<ChunkVo> chunkVos = chunks.stream().map(c -> {
            ChunkVo v = new ChunkVo();
            v.setId(c.getId());
            v.setKbId(c.getKbId());
            v.setContent(c.getContent());
            v.setMetadata(c.getMetadata());
            v.setCreatedAt(c.getCreatedAt());
            return v;
        }).collect(Collectors.toList());
        detail.setChunks(chunkVos);
        return detail;
    }

    /**
     * 查询单文档的入库耗时统计（前端「统计」图标弹窗用）。
     * 老数据/未走新流程 → 返回 null，前端兜底显示「暂无耗时数据」。
     */
    @Override
    public IngestionSummary getIngestionSummary(String documentId) {
        if (documentId == null || documentId.isBlank()) return null;
        KnowledgeDocument doc = knowledgeDocumentMapper.selectById(documentId);
        if (doc == null) return null;
        return parseIngestionSummary(doc.getIngestionSummary());
    }

    /**
     * 下载文档原文件：从 MinIO 读 storageUrl 指向的对象，流式写入 response。
     * 文档不存在或无 storageUrl 抛业务异常（前端弹「无原文件可下载」）。
     */
    @Override
    public void downloadDocument(String documentId, HttpServletResponse response) {
        KnowledgeDocument doc = knowledgeDocumentMapper.selectById(documentId);
        if (doc == null) {
            throw new BusinessException("文档不存在");
        }
        String storageUrl = doc.getStorageUrl();
        if (storageUrl == null || storageUrl.isBlank()) {
            throw new BusinessException("该文档无原文件可下载（可能由旧版本上传，未保存原文件）");
        }
        String fileName = doc.getFileName() == null ? "document" : doc.getFileName();
        try {
            minioService.download(storageUrl, fileName, response);
        } catch (Exception e) {
            log.error("[Download] 文档下载失败 doc={} storage={} : {}", documentId, storageUrl, e.getMessage(), e);
            throw new BusinessException("文件下载失败: " + e.getMessage());
        }
    }

    /** 把逗号分隔的 id 字符串解析为 List（去空白、去空串）。空输入返回空 List。 */
    @Override
    public List<String> parseIds(String documentIds) {
        if (documentIds == null || documentIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(documentIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * 重新向量化编排入口：解析 id → 空列表直接返回 → 同步置 processing（前端立即看到「向量化中」）
     * → 异步触发 {@link #embedding}。
     */
    @Override
    public void triggerEmbedding(String documentIds) {
        List<String> ids = parseIds(documentIds);
        if (ids.isEmpty()) {
            return;
        }
        // 同步置 processing（让前端轮询立即看到「向量化中」），再异步触发
        markProcessing(ids);
        // ★ 必须走 self 代理，直接 this.embedding() 会绕过 Spring AOP 导致 @Async 失效（退化为同步）
        self.embedding(ids);
    }

    /**
     * 删除文档编排入口：解析 id 后级联删除（子块 + MinIO 对象 + 图谱数据）。
     */
    @Override
    public void deleteDocuments(String documentIds) {
        List<String> ids = parseIds(documentIds);
        delete(ids);
    }

    /**
     * 试切预览：对每个文件解析 + 按运行时分块参数切分，返回切片预览。
     * 不落库、不向量化、不建文档记录。支持父子分块、问题生成和 QA 模式。
     *
     * <p>★ 异步分支：当解析慢（多文件或 mineru 系列引擎）时走异步——
     * 同步读字节 + 初始化进度桶 + {@code self.doPreviewAsync()} 派发，返回 taskId(String)；
     * 单文件非 mineru 解析毫秒级，走原同步路径，返回 {@code List<DocumentPreviewVo>}。
     *
     * <p>★ 为什么多文件也走异步：非 mineru 多文件解析虽单文件快，但串行/并发总耗时会累积到秒级，
     * 走异步并发（{@link #doPreviewAsync}）后前端能轮询到「每个文件完成的实时进度」，
     * 让并发优化可见，避免「干转圈、卡着不动」的体感。
     */
    @Override
    public Object preview(PreviewValidate req) {
        if (req == null || req.getFiles() == null || req.getFiles().isEmpty()) {
            throw new BusinessException("未选择文件");
        }
        // 解析参数 + 构建 extEngineMap（同步预处理，两条路径共用）
        PreviewPayload payload = buildPreviewPayload(req);

        // 判断是否用了 mineru 系列引擎（解析慢）
        boolean useMineru = payload.getExtEngineMap().values().stream()
                .anyMatch(e -> e != null && e.startsWith("mineru"));

        // 单文件 + 非 mineru：解析毫秒级，走同步路径，省异步开销
        boolean singleFile = req.getFiles().size() == 1;
        if (singleFile && !useMineru) {
            return previewSync(payload, req.getFiles());
        }

        // 多文件 或 mineru：走异步并发路径（前端轮询可见文件级实时进度）
        // 注意：MultipartFile 请求结束后失效，必须在此阶段读完字节
        List<PreviewFileItem> fileItems = new ArrayList<>();
        for (MultipartFile file : req.getFiles()) {
            String fileName = file.getOriginalFilename();
            try {
                fileItems.add(new PreviewFileItem(fileName, file.getSize(), file.getBytes()));
            } catch (IOException e) {
                throw new BusinessException("读取文件字节失败: " + fileName + " - " + e.getMessage());
            }
        }
        payload.setFiles(fileItems);

        String taskId = UUID.randomUUID().toString().replace("-", "");
        RBucket<PreviewProgressVo> bucket = previewProgressBucket(taskId);
        bucket.set(PreviewProgressVo.processing(fileItems.size()), PREVIEW_PROGRESS_TTL);

        self.doPreviewAsync(payload, taskId);
        // 返回 TaskIdVo（C 层无需再 instanceof/包装），同步分支返回 List<DocumentPreviewVo> 原样透传
        return new TaskIdVo(taskId);
    }

    /**
     * 同步预览执行体（非 mineru 场景，原 preview 逻辑）。
     * 直接用原始 {@link MultipartFile}（请求内有效，无需预读字节），逐文件解析+分块。
     *
     * <p>★ 多文件并发：单文件直接同步（省线程切换）；多文件走 {@link #runPreviewParallel} 扇出，
     * 对齐 WeKnora ParallelMap 保序 + 信号量限流思路。
     */
    private List<DocumentPreviewVo> previewSync(PreviewPayload payload, List<MultipartFile> files) {
        if (files.size() <= 1) {
            // 单文件直接同步，省一次线程切换
            List<DocumentPreviewVo> result = new ArrayList<>(files.size());
            for (MultipartFile f : files) {
                result.add(splitOneFile(payload, f.getOriginalFilename(), f.getSize(), f));
            }
            return result;
        }
        // 多文件：并发扇出（每个任务只读自己的 MultipartFile，线程安全）
        List<Supplier<DocumentPreviewVo>> tasks = new ArrayList<>(files.size());
        for (MultipartFile f : files) {
            final String name = f.getOriginalFilename();
            final long size = f.getSize();
            tasks.add(() -> {
                try {
                    return splitOneFile(payload, name, size, f);
                } catch (Exception e) {
                    // 兜底（splitOneFile 内部已 catch，这里防御中断/取消等未传播异常）
                    log.warn("[Preview-Sync] 文件解析失败 {}: {}", name, e.getMessage());
                    return new DocumentPreviewVo(name, size, new ArrayList<>());
                }
            });
        }
        return runPreviewParallel(tasks, "Sync");
    }

    /**
     * 保序并发解析一批文件（对齐 WeKnora ParallelMap 思路）。
     *
     * <p>核心要点：
     * <ul>
     *   <li>预分配数组 {@code results[i]}，每个任务只写自己索引位，
     *       靠 {@code CompletableFuture} 的 happens-before 保证可见性，无需加锁即可保序。</li>
     *   <li>{@link Semaphore} 限流并发度，避免打爆下游（LLM/MinIO/MinerU）。</li>
     *   <li>单任务异常不中断其他（continue-on-failure），失败位填空 VO（由调用方兜底）。</li>
     *   <li>整体超时兜底，超时后 {@code cancel(true)} 未完成任务，已完成的保留。</li>
     * </ul>
     *
     * @param tasks 每个文件的处理函数（必须自包含、无共享可变状态）
     * @param tag   日志标签（Sync/Async），便于区分两条调用路径
     */
    private List<DocumentPreviewVo> runPreviewParallel(List<Supplier<DocumentPreviewVo>> tasks, String tag) {
        int n = tasks.size();
        if (n == 0) return new ArrayList<>();
        if (n == 1) {
            // 单任务快捷分支：直接同步执行，省线程切换
            return new ArrayList<>(List.of(tasks.get(0).get()));
        }
        DocumentPreviewVo[] results = new DocumentPreviewVo[n];
        Semaphore sem = new Semaphore(PREVIEW_PARALLELISM);
        // 注意：supplyAsync + whenComplete 返回 CompletableFuture<DocumentPreviewVo>（whenComplete 保留上游泛型），
        // 这里用 CompletableFuture<DocumentPreviewVo> 而非 <Void>；allOf 接受 CompletableFuture<?> 不受影响。
        List<CompletableFuture<DocumentPreviewVo>> futures = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            final int idx = i;
            Supplier<DocumentPreviewVo> task = tasks.get(i);
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    sem.acquire();
                    try {
                        return task.get();
                    } finally {
                        sem.release();
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }, previewParseExecutor).whenComplete((vo, ex) -> {
                if (ex != null) {
                    log.warn("[Preview-{}] idx={} 失败: {}", tag, idx, ex.getMessage());
                } else {
                    results[idx] = vo;
                }
            }));
        }
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(PREVIEW_TIMEOUT_MIN, TimeUnit.MINUTES);
        } catch (TimeoutException te) {
            futures.forEach(f -> f.cancel(true));
            log.error("[Preview-{}] 并发解析超时（{}分钟），未完成的将填空结果", tag, PREVIEW_TIMEOUT_MIN);
        } catch (InterruptedException ie) {
            futures.forEach(f -> f.cancel(true));
            Thread.currentThread().interrupt();
            log.error("[Preview-{}] 并发解析被中断", tag);
        } catch (Exception e) {
            log.error("[Preview-{}] 并发解析异常: {}", tag, e.getMessage(), e);
        }
        List<DocumentPreviewVo> ret = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            DocumentPreviewVo vo = results[i];
            if (vo == null) {
                // 未完成（超时/失败/中断）的位填空 VO，保持与原同步路径「单文件失败不阻断」语义一致
                vo = new DocumentPreviewVo("解析失败", 0L, new ArrayList<>());
            }
            ret.add(vo);
        }
        return ret;
    }

    /**
     * 异步预览执行体（由 {@code ragTaskExecutor} 执行，仅 mineru 场景）。
     *
     * <p>复刻原 preview 循环逻辑，但文件来源是预读的 byte[]（{@link PreviewFileItem}），
     * 每完成一个文件把结果 add 进进度桶的 result 并刷新，前端轮询可见实时进度。
     * 单文件失败 try-catch 不中断（仍 append 空 chunks VO），任务级异常兜底置 failed。
     *
     * <p>★ 多文件并发：原本是单线程串行 for 循环，现在改为 {@link CompletableFuture} 扇出，
     * 由 {@code previewParseExecutor} 执行，{@link Semaphore} 限流并发度。
     * 对齐 WeKnora 批内并发思路（errgroup.SetLimit + mutex 串行化进度更新）。
     * 关键：进度桶（Redis 整对象写）和 result/success/failed 计数都在 {@code progressLock} 下更新，
     * 避免并发覆盖；results[i] 预分配保序，前端轮询拿到的 tab 顺序稳定。
     */
    @Override
    @Async("ragTaskExecutor")
    public void doPreviewAsync(Object payloadObj, String taskId) {
        RBucket<PreviewProgressVo> bucket = previewProgressBucket(taskId);
        PreviewProgressVo progress = bucket.get();
        if (progress == null) {
            log.warn("[Preview] 进度桶不存在 taskId={}，放弃处理", taskId);
            return;
        }
        PreviewPayload payload = (PreviewPayload) payloadObj;
        List<PreviewFileItem> items = payload.getFiles();
        if (items == null || items.isEmpty()) {
            progress.setStatus("done");
            bucket.set(progress, PREVIEW_PROGRESS_TTL);
            return;
        }
        int n = items.size();
        // 预分配结果数组：每个 future 只写自己索引位，靠 progressLock 串行化保证可见与保序
        DocumentPreviewVo[] results = new DocumentPreviewVo[n];
        // 串行化 Redis 进度桶写（对齐 WeKnora mapMu.Lock 模式），避免并发覆盖
        Object progressLock = new Object();
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        Semaphore sem = new Semaphore(PREVIEW_PARALLELISM);
        // 注意：supplyAsync + whenComplete 返回 CompletableFuture<DocumentPreviewVo>（whenComplete 保留上游泛型），
        // 这里用 CompletableFuture<DocumentPreviewVo> 而非 <Void>；allOf 接受 CompletableFuture<?> 不受影响。
        List<CompletableFuture<DocumentPreviewVo>> futures = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            final int idx = i;
            PreviewFileItem item = items.get(i);
            MultipartFile file = new InMemoryMultipartFile(item.getFileName(), item.getBytes());
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    sem.acquire();
                    try {
                        return splitOneFile(payload, item.getFileName(), item.getSize(), file);
                    } finally {
                        sem.release();
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }, previewParseExecutor).whenComplete((vo, ex) -> {
                DocumentPreviewVo r = vo;
                boolean isFail = false;
                if (ex != null || r == null) {
                    // 单文件失败不阻断：append 空 chunks VO（与原同步语义一致）
                    r = new DocumentPreviewVo(item.getFileName(), item.getSize(), new ArrayList<>());
                    isFail = true;
                    log.warn("[Preview-Async] 文件解析/分块失败 {}: {}", item.getFileName(),
                            ex == null ? "中断/取消" : ex.getMessage());
                }
                synchronized (progressLock) {
                    results[idx] = r;
                    if (isFail) {
                        progress.setFailed(failed.incrementAndGet());
                    } else {
                        progress.setSuccess(success.incrementAndGet());
                    }
                    progress.setDone(progress.getSuccess() + progress.getFailed());
                    // 透传最近完成文件的阶段耗时，让前端轮询实时看到 parse/chunk 进度
                    progress.setStages(r.getStages());
                    // 保序快照：只把已完成位按原顺序塞进 result（前端轮询按 tab 顺序展示）
                    List<DocumentPreviewVo> snap = new ArrayList<>(n);
                    for (DocumentPreviewVo x : results) {
                        if (x != null) snap.add(x);
                    }
                    progress.setResult(snap);
                    // 每完成一个文件即刷新进度桶，前端轮询可见实时进度增长
                    bucket.set(progress, PREVIEW_PROGRESS_TTL);
                }
            }));
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(PREVIEW_TIMEOUT_MIN, TimeUnit.MINUTES);
            synchronized (progressLock) {
                progress.setStatus("done");
            }
        } catch (TimeoutException te) {
            futures.forEach(f -> f.cancel(true));
            synchronized (progressLock) {
                progress.setStatus("failed");
                progress.setMessage("预览超时（" + PREVIEW_TIMEOUT_MIN + " 分钟）");
            }
            log.error("[Preview] 预览任务超时 taskId={}", taskId);
        } catch (InterruptedException ie) {
            futures.forEach(f -> f.cancel(true));
            Thread.currentThread().interrupt();
            synchronized (progressLock) {
                progress.setStatus("failed");
                progress.setMessage("预览任务被中断");
            }
            log.error("[Preview] 预览任务被中断 taskId={}", taskId);
        } catch (Throwable t) {
            // 任务级兜底：@Async 异常不会到 Controller，必须在此写桶，否则前端无限轮询
            log.error("[Preview] 预览任务异常 taskId={} : {}", taskId, t.getMessage(), t);
            synchronized (progressLock) {
                progress.setStatus("failed");
                progress.setMessage(t.getMessage() == null ? "预览任务异常" : t.getMessage());
            }
        }
        synchronized (progressLock) {
            bucket.set(progress, PREVIEW_PROGRESS_TTL);
        }
        log.info("[Preview] 任务 {} 完成 total={} success={} failed={}",
                taskId, progress.getTotal(), progress.getSuccess(), progress.getFailed());
    }

    /** 查询预览进度（从 Redis 进度桶读取，含已完成文件的切片结果）。 */
    @Override
    public PreviewProgressVo getPreviewProgress(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        return previewProgressBucket(taskId).get();
    }

    private RBucket<PreviewProgressVo> previewProgressBucket(String taskId) {
        return redisson.getBucket(PREVIEW_PROGRESS_KEY_PREFIX + taskId);
    }

    /**
     * 把 {@link PreviewValidate} 的所有参数预处理成一个 {@link PreviewPayload}：
     * 生效分块参数 + extEngineMap + 文件清单（同步路径用 MultipartFile 临时包装）。
     * 同步/异步两条路径共用，避免参数解析重复。
     */
    private PreviewPayload buildPreviewPayload(PreviewValidate req) {
        // 未传参时回落到全局配置（app.rag.chunking.*）
        RagProperties.Chunking ck = ragProperties.getChunking();
        int effSize = req.getChunkSize() != null && req.getChunkSize() > 0 ? req.getChunkSize() : ck.getChunkSize();
        int effOverlap = req.getOverlap() != null && req.getOverlap() >= 0 ? req.getOverlap() : ck.getChunkOverlap();
        String effStrategy = req.getStrategy() != null && !req.getStrategy().isBlank() ? req.getStrategy() : ck.getStrategy();
        String eng = req.getEngine() == null || req.getEngine().isBlank() ? "tika" : req.getEngine();

        boolean enableParent = req.getEnableParentChild() != null && req.getEnableParentChild();
        int effParentSize = req.getParentChunkSize() != null && req.getParentChunkSize() > 0
                ? req.getParentChunkSize() : ck.getParentSize();
        int effChildSize = req.getChildChunkSize() != null && req.getChildChunkSize() > 0
                ? req.getChildChunkSize() : ck.getChildSize();

        boolean enableQ = req.getEnableQuestionGen() != null && req.getEnableQuestionGen();
        int qCount = req.getQuestionCount() != null && req.getQuestionCount() > 0
                ? Math.min(req.getQuestionCount(), 10) : 3;
        boolean qaMode = req.getQaMode() != null && req.getQaMode();

        // 自定义分隔符（仅 strategy=legacy 生效）
        List<String> effSeparators = null;
        if (req.getSeparatorsJson() != null && !req.getSeparatorsJson().isBlank()) {
            try {
                List<String> parsed = mapper.readValue(req.getSeparatorsJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
                if (parsed != null && !parsed.isEmpty()) effSeparators = parsed;
            } catch (Exception e) {
                log.warn("[Preview] 解析 separatorsJson 失败: {}", e.getMessage());
            }
        }

        // parserEngineRules 优先取对象，没有则从 JSON 字符串解析（multipart 场景）
        List<PreviewValidate.ParserEngineRule> rules = req.getParserEngineRules();
        if (rules == null && req.getParserEngineRulesJson() != null
                && !req.getParserEngineRulesJson().isBlank()) {
            try {
                rules = mapper.readValue(req.getParserEngineRulesJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<PreviewValidate.ParserEngineRule>>() {});
            } catch (Exception e) {
                log.warn("[Preview] 解析 parserEngineRulesJson 失败: {}", e.getMessage());
            }
        }
        Map<String, String> extEngineMap = new HashMap<>();
        if (rules != null) {
            for (PreviewValidate.ParserEngineRule rule : rules) {
                if (rule.getFileTypes() != null && rule.getEngine() != null) {
                    for (String ext : rule.getFileTypes()) {
                        extEngineMap.put(ext.toLowerCase(), rule.getEngine());
                    }
                }
            }
        }

        PreviewPayload payload = new PreviewPayload();
        payload.setEng(eng);
        payload.setExtEngineMap(extEngineMap);
        payload.setEffSize(effSize);
        payload.setEffOverlap(effOverlap);
        payload.setEffStrategy(effStrategy);
        payload.setEffSeparators(effSeparators);
        payload.setEnableParent(enableParent);
        payload.setEffParentSize(effParentSize);
        payload.setEffChildSize(effChildSize);
        payload.setEnableQ(enableQ);
        payload.setQCount(qCount);
        payload.setQaMode(qaMode);
        // files 由 preview() 按路径填充：异步路径预读字节，同步路径不设（直接用 req.getFiles()）
        return payload;
    }

    /**
     * 单文件解析+分块（同步/异步两路径共用核心逻辑）。
     * 入参用 {@link MultipartFile}（同步路径用原文件，异步路径用 {@link InMemoryMultipartFile} 包装 byte[]），
     * 内部 parser.parse(is) 调用方式零改动。
     *
     * <p>★ 耗时统计：在 parse / chunk 两个阶段插入 {@link IngestionStopwatch} 计时，
     * 通过返回 VO 的 {@code stages} 字段透传给前端，再由 save 请求带回，最终落入
     * {@code document.ingestion_summary}。覆盖所有引擎（tika/pdfbox/poi/mineru/...）。
     *
     * <p>★ 原文件存储：预览阶段是唯一确定持有原始字节的时机（multipart 请求结束后失效），
     * 此处把原文件存 MinIO 并把 objectName 挂到 VO，前端透传给 save，避免后续无法下载原文件。
     */
    private DocumentPreviewVo splitOneFile(PreviewPayload payload, String fileName, long fileSize,
                                           MultipartFile file) {
        List<PreviewChunkVo> chunks = new ArrayList<>();
        // 阶段计时器（覆盖所有引擎）
        IngestionStopwatch sw = new IngestionStopwatch();
        // 原文件 MinIO 对象 key（透传给 save 持久化到 document.storage_url）
        String storageUrl = storeRawFileSafely(file, fileName);
        // 实际解析引擎：按扩展名 + parserEngineRules 推导（提至 try 外，便于末尾 setEngine 透传给 save）
        String fileExt = extractExt(fileName);
        String fileEngine = payload.getExtEngineMap().getOrDefault(fileExt, payload.getEng());
        try {
            boolean isSpreadsheet = "xls".equals(fileExt) || "xlsx".equals(fileExt) || "csv".equals(fileExt);
            if (isSpreadsheet) {
                // 表格类：parse + chunk 合并为一步（SpreadsheetRowSplitter 内部即解析即分块）
                sw.start("parse");
                try (InputStream is = file.getInputStream()) {
                    if (payload.isQaMode()) {
                        List<SpreadsheetRowSplitter.QaItem> items =
                                SpreadsheetRowSplitter.splitQa(is, fileExt);
                        for (SpreadsheetRowSplitter.QaItem item : items) {
                            chunks.add(new PreviewChunkVo(item.title(), item.content()));
                        }
                    } else {
                        Document doc = Document.from("spreadsheet");
                        List<TextSegment> segments = SpreadsheetRowSplitter.split(is, fileExt, payload.getEffSize(), doc);
                        for (TextSegment seg : segments) {
                            String t = seg.text();
                            if (t == null || t.isBlank()) continue;
                            PreviewChunkVo c = new PreviewChunkVo("", t.trim());
                            if (payload.isEnableQ()) c.setQuestions(generateQuestionsForChunk(t.trim(), payload.getQCount(), null));
                            chunks.add(c);
                        }
                    }
                }
                sw.stop("parse", "success", null);
                DocumentPreviewVo vo = new DocumentPreviewVo(fileName, fileSize, storageUrl, chunks);
                vo.setStages(sw.snapshotStages());
                // 表格类走 SpreadsheetRowSplitter（内部基于 POI），引擎记 poi
                vo.setEngine("poi");
                return vo;
            }
            DocumentParser parser = documentTypeRouter.route(fileName, fileEngine);
            String text;
            sw.start("parse");
            try (InputStream is = file.getInputStream()) {
                Object parsed = parser.parse(is);
                text = extractParsedText(parsed);
                sw.stop("parse", "success", null);
            } catch (Exception pe) {
                sw.stop("parse", "failed", pe.getMessage());
                throw pe;
            }
            if (text != null && !text.isBlank()) {
                sw.start("chunk");
                Document doc = Document.from(text);
                if (payload.isEnableParent()) {
                    // 父子 splitter 的 overlap 都用配置值（对齐 WeKnora buildParentChildConfigs）。
                    // 子块 overlap 上限 childSize/5（≈20%），避免 overlap 占满子块。
                    // ⚠️ 父子 splitter 强制用 "recursive" 策略（对齐 WeKnora：buildParentChildConfigs 不设
                    // Strategy 字段，空 = legacy = SplitText/recursive）。
                    // 不能用 auto→heading：SparkX 的 splitByHeadings 是「逐行软边界累积」，
                    // 与 WeKnora 的「按 section 硬切 + 内部 SplitText」语义不同，会让密集标题文档
                    // （如 OA 规范，16+ 个 ##）切出的子块数与 WeKnora 偏差很大。
                    int parentOverlap = payload.getEffOverlap();
                    int childOverlap = Math.min(payload.getEffOverlap(), payload.getEffChildSize() / 5);
                    AdaptiveDocumentSplitter parentSp = new AdaptiveDocumentSplitter(payload.getEffParentSize(), parentOverlap, "recursive", payload.getEffSeparators());
                    AdaptiveDocumentSplitter childSp = new AdaptiveDocumentSplitter(payload.getEffChildSize(), childOverlap, "recursive", payload.getEffSeparators());
                    ParentChildSplitter pc = new ParentChildSplitter(parentSp, childSp);
                    List<TextSegment> children = pc.split(doc);
                    Map<String, String> parentContents = pc.getParentContents();
                    for (TextSegment seg : children) {
                        String t = seg.text();
                        if (t == null || t.isBlank()) continue;
                        PreviewChunkVo c = new PreviewChunkVo("", t.trim());
                        String parentId = seg.metadata().getString("parentId");
                        if (parentId != null && parentContents.containsKey(parentId)) {
                            c.setParentContext(parentContents.get(parentId));
                        }
                        String pi = seg.metadata().getString("parentIndex");
                        if (pi != null) {
                            try { c.setParentIndex(Integer.parseInt(pi)); } catch (Exception ignore) {}
                        }
                        if (payload.isEnableQ()) c.setQuestions(generateQuestionsForChunk(t.trim(), payload.getQCount(), null));
                        chunks.add(c);
                    }
                } else {
                    AdaptiveDocumentSplitter splitter = new AdaptiveDocumentSplitter(payload.getEffSize(), payload.getEffOverlap(), payload.getEffStrategy(), payload.getEffSeparators());
                    List<TextSegment> segments = splitter.split(doc);
                    for (TextSegment seg : segments) {
                        String t = seg.text();
                        if (t == null || t.isBlank()) continue;
                        PreviewChunkVo c = new PreviewChunkVo("", t.trim());
                        if (payload.isEnableQ()) c.setQuestions(generateQuestionsForChunk(t.trim(), payload.getQCount(), null));
                        chunks.add(c);
                    }
                }
                sw.stop("chunk", "success", null);
            }
        } catch (Exception e) {
            log.warn("[Preview] 文件解析/分块失败 {}: {}", fileName, e.getMessage());
            // 单文件失败不阻断，返回空切片列表（前端可提示该文件无结果）
        }
        DocumentPreviewVo vo = new DocumentPreviewVo(fileName, fileSize, storageUrl, chunks);
        vo.setStages(sw.snapshotStages());
        // 记录实际解析引擎（按扩展名 + parserEngineRules 推导），透传给 save 落库
        vo.setEngine(fileEngine != null && !fileEngine.isBlank() ? fileEngine : payload.getEng());
        return vo;
    }

    /**
     * 把原文件存到 MinIO（供后续下载）。失败不阻断主流程（仅 warn + 返回 null）——
     * 切片仍可正常入库，只是该文档不可下载原文件。
     *
     * @return objectName；失败返回 null
     */
    private String storeRawFileSafely(MultipartFile file, String fileName) {
        if (file == null || file.isEmpty()) return null;
        try {
            String ext = extractExt(fileName);
            String objectName = "knowledge/" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
            minioService.upload(objectName, file);
            return objectName;
        } catch (Exception e) {
            log.warn("[Preview] 原文件存储 MinIO 失败 fileName={} : {}", fileName, e.getMessage());
            return null;
        }
    }

    /**
     * 为单个切片调用 LLM 生成问题（试切预览用，不入库）。
     *
     * @param modelId 指定 chat 模型 id；为 null 走默认候选链
     */
    private List<String> generateQuestionsForChunk(String content, int count, Integer modelId) {
        if (content == null || content.isBlank()) return List.of();
        try {
            String prompt = "请根据以下参考资料生成用户可能提出的 " + count + " 个问题。"
                    + "每个问题用 <question>问题</question> 标签包裹，只输出问题，不要其他内容。";
            String answer = llmService.chat(LlmChatRequest.of("你是问题生成助手",
                    prompt + "\n\n参考资料：\n" + content, 0.3), modelId);
            return extractQuestions(answer);
        } catch (Exception e) {
            log.warn("[Preview] 问题生成失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 保存切片结果（异步入口）：同步校验 → 初始化 Redis 进度桶 → 通过 {@code self} 代理派发
     * {@link #doSaveAsync}（@Async）后台入库，立即返回 TaskIdVo 供前端轮询。
     *
     * <p>异步执行体 {@link #doSaveAsync} 保留原同步实现逻辑（建文档记录 + 逐块写文本/tsv +
     * 可选问题生成），每个文档处理完后刷新进度桶；任务级异常兜底置 failed，
     * 保证前端轮询一定能看到终态（{@code @Async} 异常不会到 Controller，必须自兜底）。
     */
    @Override
    public TaskIdVo save(DocumentSaveValidate validate) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(validate.getKbId());
        if (kb == null) {
            throw new BusinessException("知识库不存在");
        }

        // 1) 初始化 Redis 进度桶（total = 文档数）
        String taskId = UUID.randomUUID().toString().replace("-", "");
        RBucket<DocumentSaveProgressVo> bucket = saveProgressBucket(taskId);
        bucket.set(DocumentSaveProgressVo.processing(validate.getDocumentList().size()), SAVE_PROGRESS_TTL);

        // 2) 通过 self 代理派发异步任务（保证 @Async 生效）
        self.doSaveAsync(validate, taskId);
        return new TaskIdVo(taskId);
    }

    /**
     * 异步入库执行体（由 {@code ragTaskExecutor} 执行）。
     *
     * <p>保留原 {@code save()} 同步实现的逐文档/逐块处理逻辑，增加：
     * <ul>
     *   <li>每个文档处理完后刷新进度桶（success/failed/done）</li>
     *   <li>单文档失败 try-catch 不中断整批（沿用原切片级容错）</li>
     *   <li>外层 try-catch 兜底：任务级异常置 status=failed + message，前端能看到</li>
     * </ul>
     */
    @Override
    @Async("ragTaskExecutor")
    public void doSaveAsync(DocumentSaveValidate validate, String taskId) {
        RBucket<DocumentSaveProgressVo> bucket = saveProgressBucket(taskId);
        DocumentSaveProgressVo progress = bucket.get();
        if (progress == null) {
            // 进度桶已过期（Redis 重启 / 超过 1 小时），不再处理
            log.warn("[Save] 进度桶不存在 taskId={}，放弃处理", taskId);
            return;
        }

        boolean enableQ = validate.getEnableQuestionGen() != null && validate.getEnableQuestionGen();
        int qCount = validate.getQuestionCount() != null && validate.getQuestionCount() > 0
                ? Math.min(validate.getQuestionCount(), 10) : 3;

        int success = 0;
        int failed = 0;
        try {
            for (DocumentSaveValidate.DocItem item : validate.getDocumentList()) {
                try {
                    List<IngestionSummary.StageStat> stages = persistOneDocument(validate, item, enableQ, qCount);
                    success++;
                    // 把已完成文档的阶段耗时（parse/chunk/persist）透传到进度桶，供前端实时展示
                    progress.setStages(stages);
                } catch (Exception e) {
                    // 单文档失败：切片级已有 try-catch 兜底，这里捕获的是文档级异常（如 doc insert 失败）
                    failed++;
                    log.error("[Save] 文档入库失败 fileName={} : {}", item.getFileName(), e.getMessage(), e);
                }
                // 刷新进度
                progress.setSuccess(success);
                progress.setFailed(failed);
                progress.setDone(success + failed);
                bucket.set(progress, SAVE_PROGRESS_TTL);
            }
            progress.setStatus("done");
        } catch (Throwable t) {
            // 任务级兜底：@Async 异常不会到 Controller，必须在此写桶，否则前端会无限轮询
            log.error("[Save] 入库任务异常 taskId={} : {}", taskId, t.getMessage(), t);
            progress.setStatus("failed");
            progress.setMessage(t.getMessage() == null ? "入库任务异常" : t.getMessage());
        }
        bucket.set(progress, SAVE_PROGRESS_TTL);
        log.info("[Save] 任务 {} 完成 total={} success={} failed={}",
                taskId, progress.getTotal(), success, failed);
    }

    /** 查询入库存度（从 Redis 进度桶读取）。 */
    @Override
    public DocumentSaveProgressVo getSaveProgress(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        return saveProgressBucket(taskId).get();
    }

    private RBucket<DocumentSaveProgressVo> saveProgressBucket(String taskId) {
        return redisson.getBucket(SAVE_PROGRESS_KEY_PREFIX + taskId);
    }

    /**
     * 单文档落库：建文档记录（pending）→ 逐块写文本+tsv（不向量化）→ 可选问题生成。
     * 抽出以便 {@link #doSaveAsync} 用 try-catch 包裹做文档级容错。
     * 逻辑与原同步 {@code save()} 完全一致，仅做结构化拆分。
     *
     * @return 该文档本次处理的阶段耗时快照（parse/chunk/persist），供 doSaveAsync 写进度桶
     */
    private List<IngestionSummary.StageStat> persistOneDocument(DocumentSaveValidate validate, DocumentSaveValidate.DocItem item,
                                    boolean enableQ, int qCount) {
        LocalDateTime now = LocalDateTime.now();
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId("doc_" + UUID.randomUUID().toString().replace("-", ""));
        doc.setKbId(validate.getKbId());
        doc.setFileName(item.getFileName());
        doc.setFileSize(item.getFileSize());
        // 原文件 MinIO 对象 key：预览阶段已存，透传过来直接挂到文档记录（供下载）
        doc.setStorageUrl(item.getStorageUrl());
        // 只存切片文本，未向量化 → pending（待用户主动向量化）
        doc.setStatus("pending");
        doc.setQuestionStatus(enableQ ? 2 : 1);
        doc.setActive(1);
        doc.setCreatedAt(now);
        doc.setUpdatedAt(now);
        knowledgeDocumentMapper.insert(doc);

        // 入库阶段计时器：先灌入预览阶段透传的 parse/chunk，再追加本次 persist
        IngestionStopwatch sw = new IngestionStopwatch();
        sw.recordStages(item.getStages());

        int count = 0;
        int qTotal = 0;
        // 父块去重：同一 parentContext 只落一次 parent_chunks，并记录 parentId 供子块回写
        Map<String, String> parentIdMap = new HashMap<>();
        sw.start("persist");
        try {
        for (DocumentSaveValidate.ChunkItem chunk : item.getChunks()) {
            String content = chunk.getContent();
            if (content == null || content.isBlank()) continue;
            String chunkId = "c_" + UUID.randomUUID().toString().replace("-", "");
            try {
                // metadata 同时写 file_name 与 document_id，修复按文档查询切片的关联
                // ★ 关键修复：父子分块必须把 parentId 写回子块 metadata，
                //   否则图谱抽取时 selectIdsByParentId 查不到子块 → Entity.chunk_ids 永远为空
                //   → hitTest / 图谱检索永远召回不到原文（可视化不受影响，因为它只依赖节点+关系）
                Map<String, Object> metaMap = new LinkedHashMap<>();
                metaMap.put("kb_id", validate.getKbId());
                metaMap.put("file_name", item.getFileName() == null ? "" : item.getFileName());
                metaMap.put("document_id", doc.getId());
                metaMap.put("index", count);

                // 父子分块：若携带 parentContext，落 parent_chunks（同父块去重），并回写 parentId 到子块
                String parentId = null;
                String parentContext = chunk.getParentContext();
                if (parentContext != null && !parentContext.isBlank()) {
                    parentId = parentIdMap.get(parentContext);
                    if (parentId == null) {
                        parentId = "p_" + UUID.randomUUID().toString().replace("-", "");
                        parentIdMap.put(parentContext, parentId);
                        String parentMeta = metadataToJson(Map.of(
                                "kb_id", validate.getKbId(),
                                "file_name", item.getFileName() == null ? "" : item.getFileName(),
                                "document_id", doc.getId()));
                        parentChunkMapper.insertJsonb(parentId, validate.getKbId(),
                                parentContext, parentMeta);
                    }
                    metaMap.put("parentId", parentId);
                }

                String meta = metadataToJson(metaMap);
                // 只插文本，不向量化；向量留给「向量化」按钮主动触发。
                // tsv 在此处一并写入（Java 端 HanLP 预分词），保证未向量化的切片也能被关键词检索命中。
                chunkMapper.insertTextOnly(chunkId, validate.getKbId(), content,
                        TsVectorGenerator.toTsVector(content), meta);

                // 问题生成：优先用预览阶段已生成的问题（避免重复调 LLM），否则按需现生成
                List<String> questions = chunk.getQuestions();
                if (enableQ) {
                    if (questions == null || questions.isEmpty()) {
                        questions = generateQuestionsForChunk(content, qCount, null);
                    }
                    for (String q : questions) {
                        if (q == null || q.isBlank()) continue;
                        KnowledgeQuestion kq = new KnowledgeQuestion();
                        kq.setKbId(validate.getKbId());
                        kq.setDocumentId(doc.getId());
                        kq.setContent(q);
                        kq.setSource("ai");
                        kq.setStatus(1);
                        kq.setCreatedAt(LocalDateTime.now());
                        kq.setUpdatedAt(LocalDateTime.now());
                        knowledgeQuestionMapper.insert(kq);
                        qTotal++;
                    }
                }
                count++;
            } catch (Exception e) {
                log.warn("[Save] 切片入库失败 doc={} : {}", doc.getId(), e.getMessage());
            }
        }
        sw.stop("persist", "success", null);
        } catch (Exception e) {
            sw.stop("persist", "failed", e.getMessage());
            throw e;
        }

        doc.setChunkCount(count);
        if (enableQ) {
            doc.setQuestionStatus(qTotal > 0 ? 3 : 1);
        }
        doc.setUpdatedAt(LocalDateTime.now());
        knowledgeDocumentMapper.updateById(doc);

        // 落入库耗时统计（jsonb 列走 CAST，不能用 updateById）
        // engine 优先用 item 上的（前端透传预览阶段实际推导的引擎），其次 validate.engine，兜底 tika
        String engine = (item.getEngine() != null && !item.getEngine().isBlank())
                ? item.getEngine()
                : (validate.getEngine() != null && !validate.getEngine().isBlank()
                        ? validate.getEngine() : "tika");
        saveIngestionSummarySafely(doc.getId(), sw, engine);
        // 返回阶段快照（含 parse/chunk/persist），供 doSaveAsync 写进度桶
        return sw.snapshotStages();
    }

    /**
     * 把 {@link IngestionStopwatch} 当前快照落库到 document.ingestion_summary（jsonb）。
     * 失败不阻断主流程（仅 warn）—— 入库结果已落，统计缺失不影响业务。
     */
    private void saveIngestionSummarySafely(String docId, IngestionStopwatch sw, String engine) {
        try {
            IngestionSummary summary = sw.build(engine);
            String json = mapper.writeValueAsString(summary);
            knowledgeDocumentMapper.updateIngestionSummary(docId, json);
        } catch (Exception e) {
            log.warn("[Save] 落入库耗时统计失败 doc={} : {}", docId, e.getMessage());
        }
    }

    /** 1.17.0 DocumentParser.parse 可能返回 List&lt;Document&gt; 或 Document，兼容提取文本 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private String extractParsedText(Object parsed) {
        if (parsed == null) return "";
        if (parsed instanceof List list) {
            if (list.isEmpty()) return "";
            Object first = list.get(0);
            return first instanceof Document d ? d.text() : String.valueOf(first);
        }
        if (parsed instanceof Document d) return d.text();
        return String.valueOf(parsed);
    }

    /** 把 metadata Map 序列化为 JSON 文本（复用字段级 mapper，不暴露为 Bean） */
    private String metadataToJson(Map<String, Object> metaMap) {
        try {
            return mapper.writeValueAsString(metaMap);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * LangChain4j Embedding → pgvector 文本格式 {@code [0.123456,...]}。
     * Locale.ROOT 避免小数点变逗号导致 SQL 解析失败。
     */
    private static String toPgVector(Embedding embedding) {
        float[] vec = embedding.vector();
        StringBuilder sb = new StringBuilder(vec.length * 9);
        sb.append('[');
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format(Locale.ROOT, "%.6f", vec[i]));
        }
        sb.append(']');
        return sb.toString();
    }


    /** 文档实体 → 列表 VO 转换。 */
    private DocumentVo toVo(KnowledgeDocument doc) {
        DocumentVo vo = new DocumentVo();
        vo.setId(doc.getId());
        vo.setKbId(doc.getKbId());
        vo.setFileName(doc.getFileName());
        vo.setFileSize(doc.getFileSize());
        vo.setStorageUrl(doc.getStorageUrl());
        vo.setStatus(doc.getStatus());
        vo.setChunkCount(doc.getChunkCount());
        vo.setQuestionStatus(doc.getQuestionStatus());
        vo.setActive(doc.getActive());
        vo.setKgEnabled(doc.getKgEnabled() != null ? doc.getKgEnabled() : 2);
        vo.setIngestionSummary(parseIngestionSummary(doc.getIngestionSummary()));
        vo.setCreatedAt(doc.getCreatedAt());
        vo.setUpdatedAt(doc.getUpdatedAt());
        return vo;
    }

    /** 把 document.ingestion_summary（jsonb 字符串）反序列化为 {@link IngestionSummary}，失败/空返回 null。 */
    private IngestionSummary parseIngestionSummary(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return mapper.readValue(json, IngestionSummary.class);
        } catch (Exception e) {
            log.debug("[Doc] 解析 ingestion_summary 失败：{}", e.getMessage());
            return null;
        }
    }

    /** 提取文件扩展名（小写），无扩展名时返回 "bin"。 */
    private String extractExt(String fileName) {
        if (fileName == null) return "bin";
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) return "bin";
        return fileName.substring(idx + 1).toLowerCase();
    }

    /** 从 LLM 回复中提取 {@code <question>...</question>} 标签包裹的问题列表。 */
    private List<String> extractQuestions(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        Matcher m = QUESTION_PATTERN.matcher(text);
        while (m.find()) {
            String q = m.group(1).trim();
            if (!q.isBlank()) out.add(q);
        }
        return out;
    }

    /** 从 MinIO 读回文档，包装为 MultipartFile 以便复用 ingest */
    private MultipartFile readBack(KnowledgeDocument doc) throws Exception {
        try (InputStream is = minioService.getObject(doc.getStorageUrl())) {
            java.nio.file.Path temp = Files.createTempFile("kb-redl-", "-" + (doc.getFileName() == null ? "doc" : doc.getFileName()));
            try (OutputStream os = Files.newOutputStream(temp)) {
                is.transferTo(os);
            }
            return new InMemoryMultipartFile(doc.getFileName(), Files.readAllBytes(temp));
        }
    }

    /** 轻量内存型 MultipartFile 实现（避免依赖 test-only 的 MockMultipartFile） */
    private static class InMemoryMultipartFile implements MultipartFile {
        private final String name;
        private final byte[] content;

        InMemoryMultipartFile(String name, byte[] content) {
            this.name = name;
            this.content = content;
        }

        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return name; }
        @Override public String getContentType() { return "application/octet-stream"; }
        @Override public boolean isEmpty() { return content == null || content.length == 0; }
        @Override public long getSize() { return content == null ? 0 : content.length; }
        @Override public byte[] getBytes() { return content; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }
        @Override public void transferTo(java.io.File dest) throws java.io.IOException, IllegalStateException {
            try {
                Files.write(dest.toPath(), content);
            } catch (java.io.IOException e) {
                throw e;
            } catch (Exception e) {
                throw new java.io.IOException(e);
            }
        }
    }

    /** 预览文件载体：fileName/size/bytes（异步路径用，MultipartFile 请求结束后失效故预读字节） */
    private static class PreviewFileItem {
        private final String fileName;
        private final long size;
        private final byte[] bytes;

        PreviewFileItem(String fileName, long size, byte[] bytes) {
            this.fileName = fileName;
            this.size = size;
            this.bytes = bytes;
        }
        public String getFileName() { return fileName; }
        public long getSize() { return size; }
        public byte[] getBytes() { return bytes; }
    }

    /**
     * 预览参数载体：把 {@link PreviewValidate} 预处理后的生效参数 + 文件清单打包，
     * 传给 {@link #doPreviewAsync}（@Async 方法参数，不依赖请求上下文 / MultipartFile）。
     * 同步路径 {@link #previewSync} 也复用它。
     */
    private static class PreviewPayload {
        private String eng;
        private Map<String, String> extEngineMap;
        private int effSize;
        private int effOverlap;
        private String effStrategy;
        private List<String> effSeparators;
        private boolean enableParent;
        private int effParentSize;
        private int effChildSize;
        private boolean enableQ;
        private int qCount;
        private boolean qaMode;
        private List<PreviewFileItem> files;

        public String getEng() { return eng; }
        public void setEng(String eng) { this.eng = eng; }
        public Map<String, String> getExtEngineMap() { return extEngineMap; }
        public void setExtEngineMap(Map<String, String> extEngineMap) { this.extEngineMap = extEngineMap; }
        public int getEffSize() { return effSize; }
        public void setEffSize(int effSize) { this.effSize = effSize; }
        public int getEffOverlap() { return effOverlap; }
        public void setEffOverlap(int effOverlap) { this.effOverlap = effOverlap; }
        public String getEffStrategy() { return effStrategy; }
        public void setEffStrategy(String effStrategy) { this.effStrategy = effStrategy; }
        public List<String> getEffSeparators() { return effSeparators; }
        public void setEffSeparators(List<String> effSeparators) { this.effSeparators = effSeparators; }
        public boolean isEnableParent() { return enableParent; }
        public void setEnableParent(boolean enableParent) { this.enableParent = enableParent; }
        public int getEffParentSize() { return effParentSize; }
        public void setEffParentSize(int effParentSize) { this.effParentSize = effParentSize; }
        public int getEffChildSize() { return effChildSize; }
        public void setEffChildSize(int effChildSize) { this.effChildSize = effChildSize; }
        public boolean isEnableQ() { return enableQ; }
        public void setEnableQ(boolean enableQ) { this.enableQ = enableQ; }
        public int getQCount() { return qCount; }
        public void setQCount(int qCount) { this.qCount = qCount; }
        public boolean isQaMode() { return qaMode; }
        public void setQaMode(boolean qaMode) { this.qaMode = qaMode; }
        public List<PreviewFileItem> getFiles() { return files; }
        public void setFiles(List<PreviewFileItem> files) { this.files = files; }
    }
}
