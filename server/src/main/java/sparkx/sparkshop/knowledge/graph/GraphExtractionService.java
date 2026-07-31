// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import sparkx.sparkshop.knowledge.entity.KgConfig;
import sparkx.sparkshop.knowledge.entity.KgExtractionRecord;
import sparkx.sparkshop.knowledge.entity.ParentChunkEntity;
import sparkx.sparkshop.knowledge.ingest.KgEntityIndexer;
import sparkx.sparkshop.knowledge.infra.LLMService;
import sparkx.sparkshop.knowledge.infra.chat.LlmChatRequest;
import sparkx.sparkshop.knowledge.mapper.ChunkMapper;
import sparkx.sparkshop.knowledge.mapper.KgConfigMapper;
import sparkx.sparkshop.knowledge.mapper.KgEntityMapper;
import sparkx.sparkshop.knowledge.mapper.KgExtractionRecordMapper;
import sparkx.sparkshop.knowledge.mapper.ParentChunkMapper;
import sparkx.sparkshop.knowledge.vo.KgExtractionProgressVo;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 知识图谱文档级抽取服务（自研 prompt + LLMService.chat + Neo4jGraphRepository 写图）。
 *
 * <p>★ 动态装配：本类始终注册（{@code @Component}），{@link #graphRepository} 由 {@code @Resource} 注入，
 * 始终非 null（已配置 Neo4j 为真实实现，未配置为 {@code NoopGraphRepository} 兜底）。
 * {@code isKgAvailable()} 通过 {@code getSchema()==null} 识别未配置状态并短路，避免空跑 LLM。
 *
 * <p>★ Self 代理模式：{@code triggerExtraction()} 同步入口通过 {@code self.extractFromDocumentAsync()} 触发
 * {@code @Async("ragTaskExecutor")}，避免同类内 {@code this.xxx()} 绕过 Spring AOP 导致异步失效
 * （对齐 {@code KnowledgeDocumentServiceImpl.triggerGenerateKbQuestions} 范式）。
 *
 * <p>★ 抽取粒度：父块（parent_chunks，~4096 tokens）。
 * 每个父块调一次 LLM（temperature=0.2，JSON 输出），解析后消歧（canonical_name 合并），
 * MERGE 写 Neo4j + INSERT kg_entity（实体向量在 pgvector，对齐 WeKnora 架构）。
 *
 * <p>★ LLM JSON 输出协议（system prompt）：
 * <pre>{@code
 * {"entities":[{"name":"","type":"","description":"","canonical_name":"","aliases":[]}],
 *  "relations":[{"head":"","type":"","tail":""}]}
 * }</pre>
 * head/tail 均为 canonical_name，写 Neo4j 关系时直接 MERGE。
 *
 * <p>依赖来源：{@link GraphRepository}（Neo4j 写图）、{@link KgEntityMapper}/{@link KgEntityIndexer}（pgvector 实体），
 * 均由 {@code KnowledgeGraphConfig} 装配，{@code @ConditionalOnProperty} 门控。
 */
@Service
public class GraphExtractionService {

    private static final Logger log = LoggerFactory.getLogger(GraphExtractionService.class);

    private static final String PROGRESS_KEY_PREFIX = "kg:extraction:";
    private static final Duration PROGRESS_TTL = Duration.ofHours(2);

    /** 抽取 LLM 的 system prompt（few-shot + JSON schema 协议） */
    private static final String EXTRACTION_SYSTEM_PROMPT = """
            你是知识图谱抽取助手。从文本中抽取实体和关系。

            ## 输出要求
            严格按 JSON 输出，不要 markdown 代码块，不要多余解释：
            {"entities":[...],"relations":[...]}

            实体格式：{"name":"原始名","type":"类型","description":"一句话描述","canonical_name":"规范名","aliases":["别名1","别名2"]}
            - canonical_name：同一实体用统一规范名（如"北京市"和"北京"合并为"北京"），这是合并键
            - aliases：该实体的其他称呼（可为空数组）
            - type：实体类型，如 人/组织/产品/技术/概念/地点/事件/...（自由识别，不强制枚举）

            关系格式：{"head":"头实体canonical_name","type":"关系类型","tail":"尾实体canonical_name"}
            - head/tail 必须引用上面 entities 里的某个 canonical_name
            - type：关系类型，如 包含/位于/属于/使用/创建/依赖/...（自由识别，简短英文或中文）

            ## 参考示例
            输入："LangChain4j 是一个 Java 库，由 LangChain 团队开发，支持 OpenAI 和 Ollama 等模型。"
            输出：
            {"entities":[
              {"name":"LangChain4j","type":"技术","description":"Java LLM 应用框架","canonical_name":"LangChain4j","aliases":[]},
              {"name":"LangChain","type":"组织","description":"LLM 工具链开发团队","canonical_name":"LangChain","aliases":[]},
              {"name":"OpenAI","type":"组织","description":"AI 模型提供商","canonical_name":"OpenAI","aliases":[]},
              {"name":"Ollama","type":"技术","description":"本地 LLM 运行工具","canonical_name":"Ollama","aliases":[]}
            ],"relations":[
              {"head":"LangChain4j","type":"属于","tail":"LangChain"},
              {"head":"LangChain4j","type":"使用","tail":"OpenAI"},
              {"head":"LangChain4j","type":"使用","tail":"Ollama"}
            ]}
            """;

    /** JSON 解析（项目约定：不声明 Bean，用字段实例） */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 父块抽取并发度：对齐 embedding 的 EMBED_PARALLELISM=2 限流策略。
     * 抽取模型往往是 chat 接口，QPS 限制比 embedding 更严，保守取 2 最不易触发 429。
     * 父块间相互独立（跨块合并靠 Neo4j MERGE + kg_entity 唯一约束兜底），可安全并发。
     */
    private static final int KG_EXTRACT_PARALLELISM = 2;

    /** 进度上报锁：保护从 AtomicInteger 计数器构建 VO 快照写 Redis 的临界区，避免整对象覆盖 */
    private final Object progressLock = new Object();

    /** ★ 并发扇出用线程池（AsyncConfig 的 ragTaskExecutor，pool=CPU*8，足够） */
    @Qualifier("ragTaskExecutor")
    @Resource
    private ExecutorService ragTaskExecutor;

    /** 图谱仓库（始终非 null：已配置 Neo4j 为真实实现，未配置为 NoopGraphRepository 兜底） */
    @Resource
    private GraphRepository graphRepository;

    /** ★ 第三期：实体 embedding 消歧器（KG 关闭时仍装配，但不会被调用） */
    @Autowired(required = false)
    private EntityDisambiguator entityDisambiguator;

    @Lazy
    @Resource
    private GraphExtractionService self;

    @Resource
    private LLMService llmService;

    @Resource
    private ParentChunkMapper parentChunkMapper;

    @Resource
    private ChunkMapper chunkMapper;

    @Resource
    private KgConfigMapper kgConfigMapper;

    @Resource
    private KgEntityMapper kgEntityMapper;

    @Resource
    private KgExtractionRecordMapper kgExtractionRecordMapper;

    @Resource
    private KgEntityIndexer kgEntityIndexer;

    @Resource
    private RedissonClient redisson;


    /**
     * 触发抽取（同步入口，可从 Controller 或 DocumentIngestService 调用）。
     * 创建/复用进度桶 → 通过 self 代理触发异步抽取，保证 @Async 生效。
     *
     * @return taskId（Redis 进度桶 key 的后缀，前端轮询用）
     */
    public String triggerExtraction(String docId, String kbId) {
        if (!isKgAvailable()) return null;

        // 创建进度桶
        String taskId = UUID.randomUUID().toString().replace("-", "");
        RBucket<KgExtractionProgressVo> bucket = progressBucket(taskId);
        bucket.set(KgExtractionProgressVo.processing(0), PROGRESS_TTL);  // total 先写 0，异步里再更新

        // 同步创建/复用抽取记录（status=extracting）
        upsertRecord(kbId, docId, "extracting");

        // 通过代理触发异步（保证 @Async 生效）
        self.extractFromDocumentAsync(docId, kbId, taskId);

        return taskId;
    }

    /**
     * 触发批量补抽取（同步入口，多个文档）。
     *
     * @return taskId（进度桶 key 后缀）
     */
    public String triggerBatchExtraction(List<String> docIds, String kbId) {
        if (!isKgAvailable() || docIds == null || docIds.isEmpty()) return null;

        String taskId = UUID.randomUUID().toString().replace("-", "");
        RBucket<KgExtractionProgressVo> bucket = progressBucket(taskId);
        bucket.set(KgExtractionProgressVo.processing(0), PROGRESS_TTL);

        self.extractBatchAsync(docIds, kbId, taskId);

        return taskId;
    }

    /** 读取抽取进度（Controller 调用） */
    public KgExtractionProgressVo getProgress(String taskId) {
        if (taskId == null || taskId.isBlank()) return null;
        return progressBucket(taskId).get();
    }


    /**
     * 单文档异步抽取（整体兜底 try/catch，对齐 generateKbQuestions 范式）。
     */
    @Async("ragTaskExecutor")
    public void extractFromDocumentAsync(String docId, String kbId, String taskId) {
        RBucket<KgExtractionProgressVo> bucket = progressBucket(taskId);
        KgExtractionProgressVo progress = bucket.get();
        if (progress == null) {
            log.warn("[KgExtract] 进度桶不存在 taskId={}", taskId);
            return;
        }
        try {
            doExtractDocument(docId, kbId, progress, bucket);
            progress.setStatus("done");
            bucket.set(progress, PROGRESS_TTL);
        } catch (Throwable t) {
            log.error("[KgExtract] 异步任务整体失败 docId={} kbId={}: {}", docId, kbId, t.getMessage(), t);
            progress.setStatus("failed");
            progress.setMessage(t.getMessage());
            bucket.set(progress, PROGRESS_TTL);  // 任务级兜底：必须写桶，否则前端无限轮询
            updateRecordStatus(kbId, docId, "failed", t.getMessage());
        }
    }

    /**
     * 批量补抽取（多个文档串行执行，共用一个进度桶）。
     */
    @Async("ragTaskExecutor")
    public void extractBatchAsync(List<String> docIds, String kbId, String taskId) {
        RBucket<KgExtractionProgressVo> bucket = progressBucket(taskId);
        KgExtractionProgressVo progress = bucket.get();
        if (progress == null) {
            log.warn("[KgExtract] 进度桶不存在 taskId={}", taskId);
            return;
        }
        int totalSuccess = 0, totalFailed = 0;
        for (String docId : docIds) {
            try {
                // ★ 同步建/复用抽取记录（status=extracting），保证前端表格能看到进度。
                // 单文档路径 triggerExtraction() 会 upsertRecord，批量补抽取路径原先漏了这步，
                // 导致 record 表为空、抽取管理表格无数据。
                upsertRecord(kbId, docId, "extracting");
                doExtractDocument(docId, kbId, progress, bucket);
                totalSuccess++;
            } catch (Exception e) {
                log.warn("[KgExtract] 文档抽取失败 docId={}: {}", docId, e.getMessage());
                totalFailed++;
                updateRecordStatus(kbId, docId, "failed", e.getMessage());
            }
        }
        progress.setStatus("done");
        progress.setMessage("文档级汇总: 成功=" + totalSuccess + " 失败=" + totalFailed);
        bucket.set(progress, PROGRESS_TTL);
    }


    /**
     * 单文档抽取：遍历父块 → LLM 抽取 → 消歧 → 写 Neo4j + kg_entity。
     * 异常向上抛（由调用方 catch 整体兜底）。
     */
    private void doExtractDocument(String docId, String kbId,
                                    KgExtractionProgressVo progress, RBucket<KgExtractionProgressVo> bucket) {
        KgConfig config = kgConfigMapper.selectById(1);
        if (config == null || config.getEnabled() == null || config.getEnabled() != 1) {
            log.info("[KgExtract] kg_config 未启用，跳过 docId={}", docId);
            return;
        }

        List<ParentChunkEntity> parents = parentChunkMapper.selectByDocumentId(docId);
        if (parents.isEmpty()) {
            log.info("[KgExtract] 无父块可抽取 docId={}", docId);
            updateRecordStatus(kbId, docId, "done", null);
            return;
        }

        // 更新进度桶 total
        progress.setTotal(parents.size());
        bucket.set(progress, PROGRESS_TTL);

        // 更新记录 parent_total
        updateRecordParentTotal(kbId, docId, parents.size());

        // ★ 并发扇出：父块间相互独立，KG_EXTRACT_PARALLELISM 路并发抽取。
        // 计数器用 AtomicInteger（多线程累加安全），进度上报用 progressLock 保护快照构建。
        AtomicInteger parentDone = new AtomicInteger(0);
        AtomicInteger parentSuccess = new AtomicInteger(0);
        AtomicInteger parentFailed = new AtomicInteger(0);
        AtomicInteger totalEntities = new AtomicInteger(0);
        AtomicInteger totalRelations = new AtomicInteger(0);
        Semaphore sem = new Semaphore(KG_EXTRACT_PARALLELISM);

        List<CompletableFuture<Void>> futures = new ArrayList<>(parents.size());
        for (ParentChunkEntity parent : parents) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    sem.acquire();
                    try {
                        int[] counts = extractOneParent(parent, kbId, docId, config);
                        totalEntities.addAndGet(counts[0]);
                        totalRelations.addAndGet(counts[1]);
                        parentSuccess.incrementAndGet();
                    } finally {
                        sem.release();
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    parentFailed.incrementAndGet();
                    log.warn("[KgExtract] 父块抽取被中断 parentId={}", parent.getId());
                } catch (Exception e) {
                    parentFailed.incrementAndGet();
                    log.warn("[KgExtract] 父块抽取失败 parentId={}: {}", parent.getId(), e.getMessage());
                } finally {
                    // 进度上报：从原子计数器构建快照写 Redis（加锁防整对象覆盖）
                    int done = parentDone.incrementAndGet();
                    synchronized (progressLock) {
                        progress.setDone(done);
                        progress.setSuccess(parentSuccess.get());
                        progress.setFailed(parentFailed.get());
                        progress.setEntityCount(totalEntities.get());
                        progress.setRelationCount(totalRelations.get());
                        bucket.set(progress, PROGRESS_TTL);
                    }
                }
            }, ragTaskExecutor));
        }
        // 阻塞等全部完成（本方法在 @Async 线程跑，阻塞不影响主线程/HTTP 请求）
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 更新记录状态
        updateRecordResult(kbId, docId, parentDone.get(), totalEntities.get(), totalRelations.get(),
                parentFailed.get() > 0 ? "部分失败(" + parentFailed.get() + "/" + parents.size() + ")" : null);
    }

    /**
     * 单个父块的抽取执行体（从原 for 循环体抽出，便于并发扇出复用）。
     *
     * <p>线程安全：依赖的 EntityDisambiguator / Neo4jGraphRepository / KgEntityIndexer /
     * RoutingLLMService 均为无共享状态单例，可被多父块并发调用；upsertKgEntities 已改为
     * ON CONFLICT 原子 upsert，并发安全。
     *
     * @return [实体数, 关系数]；空结果返回 [0,0]
     */
    private int[] extractOneParent(ParentChunkEntity parent, String kbId, String docId, KgConfig config) {
        // 查该父块下子块 id（Entity.chunk_ids 用于回溯原文）
        List<String> chunkIds = chunkMapper.selectIdsByParentId(parent.getId());

        // LLM 抽取
        String llmOutput = callLlmForExtraction(parent.getContent(), config, kbId);
        GraphExtractionResult result = parseExtractionJson(llmOutput);

        if (result.entities().isEmpty() && result.relations().isEmpty()) {
            log.debug("[KgExtract] 父块无实体/关系 parentId={}", parent.getId());
            return new int[]{0, 0};
        }
        // ★ 消歧第一步：字符串 canonical_name 合并（同 key 取并集 aliases、最长 description）
        List<GraphExtractionResult.Entity> disambiguated = disambiguate(result.entities());

        // ★ 第三期：消歧第二步——embedding 余弦相似度合并。
        // 解决 LLM 在同一父块内对同一实体输出不同 canonical_name（"北京"vs"北京市"）的问题。
        // 跨父块的合并靠 Neo4j MERGE + kg_entity 唯一约束兜底。
        // 失败（模型不可达/阈值异常）降级为仅字符串消歧，不阻断抽取。
        List<GraphExtractionResult.Relation> relations = result.relations();
        if (entityDisambiguator != null) {
            try {
                var mergeResult = entityDisambiguator.mergeByEmbedding(kbId, disambiguated, config);
                if (!mergeResult.nameRemap().isEmpty()) {
                    relations = entityDisambiguator.remapRelations(relations, mergeResult.nameRemap());
                }
                disambiguated = mergeResult.entities();
            } catch (Exception embEx) {
                log.debug("[KgExtract] embedding 消歧失败，降级为字符串消歧: {}", embEx.getMessage());
            }
        }

        // 写 Neo4j
        int eCount = graphRepository.mergeEntities(kbId, disambiguated, docId, parent.getId(), chunkIds);
        int rCount = graphRepository.mergeRelations(kbId, docId, relations);

        // 写 kg_entity（ON CONFLICT 原子 upsert，并发安全）
        int newEntities = upsertKgEntities(kbId, disambiguated, docId, parent.getId(), config);

        log.debug("[KgExtract] parentId={} entities={} relations={} newKgEntities={}",
                parent.getId(), eCount, rCount, newEntities);
        return new int[]{eCount, rCount};
    }


    /** 调 LLM 抽取（同步，指定 modelId） */
    private String callLlmForExtraction(String content, KgConfig config, String kbId) {
        // content 过长时截断（父块 ~4096 chars，LLM 上下文一般够用，这里做安全截断）
        if (content.length() > 8000) {
            content = content.substring(0, 8000);
        }

        LlmChatRequest req = LlmChatRequest.of(EXTRACTION_SYSTEM_PROMPT,
                "请从以下文本中抽取实体和关系：\n\n" + content, 0.2);
        return llmService.chat(req, config.getExtractModelId(), config.getExtractModelName());
    }


    /** 解析 LLM 返回的 JSON（strip markdown fence 后用 Jackson） */
    private GraphExtractionResult parseExtractionJson(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) return GraphExtractionResult.empty();

        // strip ```json ... ``` 围栏
        String json = stripMarkdownFence(llmOutput.trim());

        try {
            JsonNode root = mapper.readTree(json);
            JsonNode entitiesNode = root.path("entities");
            JsonNode relationsNode = root.path("relations");

            List<GraphExtractionResult.Entity> entities = new ArrayList<>();
            if (entitiesNode.isArray()) {
                for (JsonNode e : entitiesNode) {
                    String name = textOrNull(e, "name");
                    String canonical = textOrNull(e, "canonical_name");
                    if (canonical == null || canonical.isBlank()) canonical = name;  // 兜底
                    if (canonical == null || canonical.isBlank()) continue;

                    List<String> aliases = new ArrayList<>();
                    JsonNode aliasesNode = e.path("aliases");
                    if (aliasesNode.isArray()) {
                        for (JsonNode a : aliasesNode) {
                            String alias = a.asText(null);
                            if (alias != null && !alias.isBlank()) aliases.add(alias);
                        }
                    }
                    entities.add(new GraphExtractionResult.Entity(
                            name, textOrNull(e, "type"), textOrNull(e, "description"),
                            canonical, aliases));
                }
            }

            List<GraphExtractionResult.Relation> relations = new ArrayList<>();
            if (relationsNode.isArray()) {
                for (JsonNode r : relationsNode) {
                    String head = textOrNull(r, "head");
                    String tail = textOrNull(r, "tail");
                    String type = textOrNull(r, "type");
                    if (head != null && tail != null && type != null) {
                        relations.add(new GraphExtractionResult.Relation(head, type, tail));
                    }
                }
            }

            return new GraphExtractionResult(entities, relations);
        } catch (Exception e) {
            log.warn("[KgExtract] JSON 解析失败（前 200 字符）: {}", json.substring(0, Math.min(200, json.length())));
            return GraphExtractionResult.empty();
        }
    }

    /** 去掉 LLM 常见的 markdown 代码块围栏 */
    private static String stripMarkdownFence(String s) {
        // 处理 ```json ... ``` 或 ``` ... ```
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline > 0) {
                s = s.substring(firstNewline + 1);
            }
        }
        if (s.endsWith("```")) {
            s = s.substring(0, s.length() - 3);
        }
        return s.trim();
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) return null;
        String s = v.asText(null);
        return (s == null || s.isBlank()) ? null : s;
    }


    /**
     * 消歧：同 canonical_name 的实体合并（aliases 取并集，description 取最长）。
     * 返回去重后的实体列表。
     */
    private List<GraphExtractionResult.Entity> disambiguate(List<GraphExtractionResult.Entity> entities) {
        if (entities == null || entities.isEmpty()) return List.of();

        // canonical_name → 合并后的实体
        Map<String, GraphExtractionResult.Entity> merged = new LinkedHashMap<>();
        for (GraphExtractionResult.Entity e : entities) {
            String key = (e.canonicalName() == null || e.canonicalName().isBlank()) ? e.name() : e.canonicalName();
            if (key == null || key.isBlank()) continue;

            GraphExtractionResult.Entity existing = merged.get(key);
            if (existing == null) {
                merged.put(key, e);
            } else {
                // 合并 aliases
                Set<String> allAliases = new HashSet<>();
                if (existing.aliases() != null) allAliases.addAll(existing.aliases());
                if (e.aliases() != null) allAliases.addAll(e.aliases());
                // 也把原始 name 当 alias
                if (e.name() != null && !e.name().equals(key)) allAliases.add(e.name());
                if (existing.name() != null && !existing.name().equals(key)) allAliases.add(existing.name());

                // description 取最长
                String desc = longest(existing.description(), e.description());
                // type 取非空的
                String type = existing.type() != null ? existing.type() : e.type();

                merged.put(key, new GraphExtractionResult.Entity(
                        existing.name(), type, desc, key, new ArrayList<>(allAliases)));
            }
        }
        return new ArrayList<>(merged.values());
    }

    private static String longest(String a, String b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.length() >= b.length() ? a : b;
    }


    /**
     * 写 kg_entity 表（实体向量索引）。
     * 只对"本次新增的 canonical_name"做 INSERT + 向量化；已存在（同 kb_id + canonical_name）则跳过。
     *
     * @return 新增实体数
     */
    private int upsertKgEntities(String kbId, List<GraphExtractionResult.Entity> entities,
                                  String docId, String parentId, KgConfig config) {
        int count = 0;
        for (GraphExtractionResult.Entity e : entities) {
            String canonical = (e.canonicalName() == null || e.canonicalName().isBlank()) ? e.name() : e.canonicalName();
            if (canonical == null || canonical.isBlank()) continue;

            // ★ 并发安全 upsert：单条 INSERT ... ON CONFLICT 原子完成「新增 or 追加来源」，
            // 依赖唯一约束 uk_kg_entity_kb_doc_canonical 兜底，杜绝并发下重复插入与 lost-update。
            // inserted=true 表示本次是真正新增（需向量化）；false 表示冲突命中已有行（跳过向量化，
            // 与原 SELECT-then-INSERT 路径下「已存在则 continue」语义一致）。
            String aliasesJson = toJson(e.aliases());
            String docIdsJson = toJson(List.of(docId));
            String parentIdsJson = toJson(List.of(parentId));

            Map<String, Object> row = kgEntityMapper.upsertOnConflict(
                    kbId, docId, e.name(), canonical, e.type(), e.description(),
                    aliasesJson, docIdsJson, parentIdsJson);

            boolean inserted = row != null && Boolean.TRUE.equals(row.get("inserted"));
            if (inserted) {
                Long newId = row != null ? ((Number) row.get("id")).longValue() : null;
                if (newId != null) {
                    // 新增实体向量化（失败不阻断，与原逻辑一致）
                    try {
                        kgEntityIndexer.index(newId, canonical, e.description(),
                                config.getEmbeddingModelId(), config.getEmbeddingModelName());
                    } catch (Exception ex) {
                        log.warn("[KgExtract] 实体向量化失败 id={} name={}: {}", newId, canonical, ex.getMessage());
                    }
                }
                count++;
            }
        }
        return count;
    }

    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }


    private void upsertRecord(String kbId, String docId, String status) {
        KgExtractionRecord existing = kgExtractionRecordMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KgExtractionRecord>()
                        .eq(KgExtractionRecord::getKbId, kbId)
                        .eq(KgExtractionRecord::getDocumentId, docId)
                        .last("LIMIT 1"));
        if (existing != null) {
            existing.setStatus(status);
            existing.setErrorMsg(null);
            existing.setStartedAt(LocalDateTime.now());
            existing.setUpdatedAt(LocalDateTime.now());
            kgExtractionRecordMapper.updateById(existing);
        } else {
            KgExtractionRecord record = new KgExtractionRecord();
            record.setKbId(kbId);
            record.setDocumentId(docId);
            record.setStatus(status);
            record.setParentTotal(0);
            record.setParentDone(0);
            record.setEntityCount(0);
            record.setRelationCount(0);
            record.setStartedAt(LocalDateTime.now());
            record.setCreatedAt(LocalDateTime.now());
            record.setUpdatedAt(LocalDateTime.now());
            kgExtractionRecordMapper.insert(record);
        }
    }

    private void updateRecordStatus(String kbId, String docId, String status, String errorMsg) {
        KgExtractionRecord record = findRecord(kbId, docId);
        if (record == null) return;
        record.setStatus(status);
        record.setErrorMsg(errorMsg);
        record.setUpdatedAt(LocalDateTime.now());
        if ("done".equals(status) || "failed".equals(status)) {
            record.setFinishedAt(LocalDateTime.now());
        }
        kgExtractionRecordMapper.updateById(record);
    }

    private void updateRecordParentTotal(String kbId, String docId, int total) {
        KgExtractionRecord record = findRecord(kbId, docId);
        if (record == null) return;
        record.setParentTotal(total);
        record.setUpdatedAt(LocalDateTime.now());
        kgExtractionRecordMapper.updateById(record);
    }

    private void updateRecordResult(String kbId, String docId, int parentDone,
                                     int entityCount, int relationCount, String errorMsg) {
        KgExtractionRecord record = findRecord(kbId, docId);
        if (record == null) return;
        record.setParentDone(parentDone);
        record.setEntityCount(entityCount);
        record.setRelationCount(relationCount);
        record.setStatus(errorMsg == null ? "done" : "done");  // 部分失败也算 done（前端看 failed 数）
        record.setErrorMsg(errorMsg);
        record.setFinishedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        kgExtractionRecordMapper.updateById(record);
    }

    private KgExtractionRecord findRecord(String kbId, String docId) {
        return kgExtractionRecordMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KgExtractionRecord>()
                        .eq(KgExtractionRecord::getKbId, kbId)
                        .eq(KgExtractionRecord::getDocumentId, docId)
                        .last("LIMIT 1"));
    }


    private boolean isKgAvailable() {
        // Neo4j 未配置（NoopGraphRepository）时返回 false，抽取流程短路避免空跑 LLM
        return graphRepository != null && graphRepository.getSchema() != null;
    }

    private RBucket<KgExtractionProgressVo> progressBucket(String taskId) {
        return redisson.getBucket(PROGRESS_KEY_PREFIX + taskId);
    }
}
