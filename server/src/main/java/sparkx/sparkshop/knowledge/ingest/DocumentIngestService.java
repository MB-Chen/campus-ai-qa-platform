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

import sparkx.sparkshop.knowledge.entity.KnowledgeDocument;
import sparkx.sparkshop.knowledge.mapper.ChunkMapper;
import sparkx.sparkshop.knowledge.mapper.KnowledgeDocumentMapper;
import sparkx.sparkshop.knowledge.mapper.ParentChunkMapper;
import sparkx.sparkshop.knowledge.infra.EmbeddingModelProvider;
import sparkx.sparkshop.knowledge.infra.TsVectorGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 文档入库服务
 *
 * 流程：解析 → 图片提取 → 分块（父子）→ 父块落表 → 子块向量化入库 → (异步)图片OCR
 *
 * 注意 1.17.0：
 *  - DocumentParser.parse(InputStream) 返回 List<Document>（取第一个）
 *  - EmbeddingModel.embed(TextSegment).content() 取 Embedding
 *
 * 适配点
 *  - JPA Repository → MyBatis-Plus Mapper（documentRepo.save → documentMapper.insert）
 *  - HybridContentRetriever.toPgVector → 内联为本类私有静态方法（retrieval 模块未移植）
 */
@Service
public class DocumentIngestService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestService.class);

    private final DocumentTypeRouter parserRouter;
    private final ParentChildSplitter splitter;
    private final EmbeddingModelProvider embeddingModelProvider;
    private final ImageOcrService imageOcrService;
    private final MultimodalDocumentParser multimodalParser;
    private final ChunkMapper chunkMapper;
    private final ParentChunkMapper parentChunkMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final ObjectMapper mapper = new ObjectMapper();

    public DocumentIngestService(DocumentTypeRouter parserRouter,
                                  ParentChildSplitter splitter,
                                  EmbeddingModelProvider embeddingModelProvider,
                                  ImageOcrService imageOcrService,
                                  MultimodalDocumentParser multimodalParser,
                                  ChunkMapper chunkMapper,
                                  ParentChunkMapper parentChunkMapper,
                                  KnowledgeDocumentMapper documentMapper) {
        this.parserRouter = parserRouter;
        this.splitter = splitter;
        this.embeddingModelProvider = embeddingModelProvider;
        this.imageOcrService = imageOcrService;
        this.multimodalParser = multimodalParser;
        this.chunkMapper = chunkMapper;
        this.parentChunkMapper = parentChunkMapper;
        this.documentMapper = documentMapper;
    }

    // Spring 注入所有 IngestPostHook Bean；KG 关闭时 GraphExtractionPostHook 不装配，列表为空。
    // 每个 hook 自己判 shouldRun + 自己容错，ingest 主流程只负责遍历触发。
    @Autowired(required = false)
    private List<IngestPostHook> postHooks;

    /**
     * 入库一个文件（新建文档记录）。
     * @return 子块数量
     */
    public int ingest(MultipartFile file, String kbId, String engine) throws Exception {
        return ingest(file, kbId, engine, null);
    }

    /**
     * 入库一个文件。
     *
     * @param docId 可选：已存在的文档 id。传入则复用该记录（update），不新建；
     *              为 null 则新建一条文档记录（用于首次上传）。
     *              供「重新向量化」复用原 doc 记录，避免重复插入。
     * @return 子块数量
     */
    public int ingest(MultipartFile file, String kbId, String engine, String docId) throws Exception {
        String fileName = file.getOriginalFilename();
        log.info("[Ingest] start file={} kb={} engine={} docId={}", fileName, kbId, engine, docId);

        // 1. 解析文档（1.17.0 parse 返回 Document）
        DocumentParser parser = parserRouter.route(fileName, engine);
        Document doc;
        try (InputStream is = file.getInputStream()) {
            doc = parser.parse(is);
        }

        // 2. 文档记录：传了 docId 则复用现有记录（update），否则新建
        KnowledgeDocument docEntity;
        if (docId != null && !docId.isBlank()) {
            docEntity = documentMapper.selectById(docId);
            if (docEntity == null) {
                // 极端情况：doc 记录已被删，退回新建一条同 id 的记录兜底
                docEntity = new KnowledgeDocument();
                docEntity.setId(docId);
                docEntity.setKbId(kbId);
                docEntity.setFileName(fileName);
                docEntity.setFileSize(file.getSize());
                docEntity.setStatus("processing");
                documentMapper.insert(docEntity);
            } else {
                docEntity.setStatus("processing");
                documentMapper.updateById(docEntity);
            }
        } else {
            docEntity = new KnowledgeDocument();
            docEntity.setId("doc_" + UUID.randomUUID().toString().replace("-", ""));
            docEntity.setKbId(kbId);
            docEntity.setFileName(fileName);
            docEntity.setFileSize(file.getSize());
            docEntity.setStatus("processing");
            documentMapper.insert(docEntity);
        }

        // 3. 分块（父子模式）
        List<TextSegment> children = splitter.split(doc);
        Map<String, String> parentContents = splitter.getParentContents();

        // 父子块 metadata 都带 document_id，便于按文档维度的切片列表/重新向量化清理
        Map<String, Object> baseMeta = new HashMap<>();
        baseMeta.put("file_name", fileName == null ? "" : fileName);
        baseMeta.put("document_id", docEntity.getId());

        // 4. 父块落表（metadata 为 jsonb，走 CAST，不走 BaseMapper.insert）
        for (var entry : parentContents.entrySet()) {
            parentChunkMapper.insertJsonb(
                    entry.getKey(), kbId, entry.getValue(),
                    mapper.writeValueAsString(baseMeta));
        }

        // 5. 子块注入元数据 + 向量化 + 入库（按 kbId 解析对应 embedding 模型）
        EmbeddingModel embeddingModel = embeddingModelProvider.resolve(kbId);
        int success = 0;
        for (TextSegment seg : children) {
            seg.metadata().put("kb_id", kbId);
            seg.metadata().put("file_name", fileName == null ? "" : fileName);
            seg.metadata().put("document_id", docEntity.getId());

            try {
                Embedding emb = embeddingModel.embed(seg).content();
                String pgVec = toPgVector(emb);

                String meta = metadataToJson(seg.metadata());
                chunkMapper.insertWithEmbedding(
                        "c_" + UUID.randomUUID().toString().replace("-", ""),
                        kbId, seg.text(), pgVec, TsVectorGenerator.toTsVector(seg.text()), meta);
                success++;
            } catch (Exception e) {
                log.warn("[Ingest] 子块向量化/入库失败: {}", e.getMessage());
            }
        }

        // 6. 更新文档状态
        docEntity.setStatus("done");
        docEntity.setChunkCount(success);
        documentMapper.updateById(docEntity);
        log.info("[Ingest] done file={} children={} parents={} success={}",
                fileName, children.size(), parentContents.size(), success);

        // ★ 6.5 入库后增强钩子（可插拔：图谱抽取 / 未来问答对预生成等）。
        // 每个 hook 自己判 shouldRun（含三闸校验）+ 自己容错，这里只负责按 order 排序遍历触发。
        // 与旧版行为等价：图谱抽取 best-effort，失败只 warn 不阻断主流程（主流程已状态=done）。
        if (postHooks != null && !postHooks.isEmpty()) {
            IngestPostContext hookCtx = IngestPostContext.of(
                    docEntity.getId(), kbId, engine, fileName, success);
            List<IngestPostHook> sorted = postHooks.stream()
                    .sorted(Comparator.comparingInt(IngestPostHook::getOrder))
                    .toList();
            for (IngestPostHook hook : sorted) {
                try {
                    if (hook.shouldRun(hookCtx)) {
                        hook.afterIngest(hookCtx);
                    }
                } catch (Exception ex) {
                    log.warn("[Ingest] 后置钩子 {} 执行失败（不影响文档入库）: {}",
                            hook.getClass().getSimpleName(), ex.getMessage());
                }
            }
        }

        // 7. 异步图片 OCR/Caption
        // 仅 image-mode=keep 时 doc.text() 里才残留图片 URL（describe/drop 模式解析阶段已处理），
        // 此时 extractImageUrls 返回空列表，循环天然跳过，不产生额外开销。
        for (String url : multimodalParser.extractImageUrls(doc.text())) {
            imageOcrService.processImage(url, "", kbId);
        }

        return success;
    }

    /** 把 LangChain4j Metadata 序列化为 JSON 字符串 */
    private String metadataToJson(Metadata meta) {
        try {
            Map<String, Object> map = new HashMap<>(meta.toMap());
            return mapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 把 LangChain4j Embedding 转为 pgvector 文本格式 {@code [0.123456,...]}。
     *
     * 内联自 sparkxV2 {@code HybridContentRetriever.toPgVector}（retrieval 模块未移植到 SparkX）。
     * Locale.ROOT 避免欧洲语系小数点变成逗号导致 SQL 解析失败。
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
}
