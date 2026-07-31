// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.retrieval;

import sparkx.sparkshop.knowledge.entity.ParentChunkEntity;
import sparkx.sparkshop.knowledge.mapper.ParentChunkMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 父块展开后处理器 —— order=2（去重之后、融合/重排之前）。
 *
 * <p>解决父子分块「检索时父块从未注入大模型」的断裂：入库侧 {@code ParentChildSplitter}
 * 已正确切分并落 {@code parent_chunks} 表、子块 metadata 带 {@code parentId}，但检索链路
 * 原先只返回子块文本（历史上有个未装配的 ParentChildRetriever，从未接入管道）。本处理器把
 * 展开逻辑接入既有后处理器责任链，使父块上下文真正喂给 LLM。
 *
 * <p>展开逻辑：
 * <ol>
 *   <li>从每个 Content 的 metadata 取 {@code parentId} / {@code chunkRole}</li>
 *   <li>仅对 {@code chunkRole=child} 且有 {@code parentId} 的块处理</li>
 *   <li>收集去重 parentId → {@link ParentChunkMapper#findByIds} 批量查父块全文 → 用父块内容替换子块内容</li>
 *   <li>{@code findByIds} 失败时容错降级为返回原子块（不阻断检索）</li>
 * </ol>
 *
 * <p>数据驱动：非父子分块的知识库（子块没有 {@code parentId}）自动跳过，无需 KB 级开关，
 * 与现有「父子关系由 metadata 承载，存储无关」设计一致。多个子块命中同一父块时去重，
 * 避免重复上下文灌爆 LLM。
 *
 * <p>order=2 处于接口预留的 2~9 间隙（去重=1，重排=10）：先去重再展开，避免对重复子块
 * 做无谓的父块查询；展开在融合/重排前，让父块（更长的上下文）参与后续重排打分。
 */
@Component
public class ParentExpansionPostProcessor implements SearchResultPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(ParentExpansionPostProcessor.class);

    private final ParentChunkMapper parentChunkMapper;

    public ParentExpansionPostProcessor(ParentChunkMapper parentChunkMapper) {
        this.parentChunkMapper = parentChunkMapper;
    }

    @Override
    public int getOrder() { return 2; }

    /** 始终启用：内部靠 chunkRole=child 判定自然跳过非父子库，无需开关 */
    @Override
    public boolean isEnabled(RetrievalContext ctx) { return true; }

    @Override
    public List<Content> process(List<Content> chunks, Query query, RetrievalContext ctx) {
        if (chunks == null || chunks.isEmpty()) return List.of();

        // 1. 收集命中子块的去重 parentId
        Set<String> parentIds = new LinkedHashSet<>();
        for (Content c : chunks) {
            String pid = c.textSegment().metadata().getString("parentId");
            if (pid != null && !pid.isBlank()
                    && "child".equals(c.textSegment().metadata().getString("chunkRole"))) {
                parentIds.add(pid);
            }
        }
        // 无父子分块的库（子块无 parentId）→ 原样返回，零成本跳过
        if (parentIds.isEmpty()) {
            return chunks;
        }

        // 2. 批量查父块全文（查询失败容错降级，见下方 catch）
        Map<String, String> parentContent = new HashMap<>();
        try {
            List<ParentChunkEntity> parents = parentChunkMapper.findByIds(List.copyOf(parentIds));
            if (parents != null) {
                for (ParentChunkEntity p : parents) {
                    if (p.getContent() != null) {
                        parentContent.put(p.getId(), p.getContent());
                    }
                }
            }
        } catch (Exception e) {
            // 父块查询失败：降级返回原子块（不阻断检索）
            log.warn("[ParentExpand] 父块查询失败，降级返回子块: {}", e.getMessage());
            return chunks;
        }
        if (parentContent.isEmpty()) {
            log.debug("[ParentExpand] 命中 parentId={} 但无对应父块记录，返回子块", parentIds);
            return chunks;
        }

        // 3. 用父块内容替换子块；多个子块命中同一父块去重（按父块文本去重）
        Set<String> seenParentText = new LinkedHashSet<>();
        List<Content> result = new ArrayList<>(chunks.size());
        int expanded = 0;
        for (Content c : chunks) {
            String pid = c.textSegment().metadata().getString("parentId");
            String childRole = c.textSegment().metadata().getString("chunkRole");
            if (pid == null || pid.isBlank() || !"child".equals(childRole)) {
                // 非子块（普通切片/问题切片）原样保留
                result.add(c);
                continue;
            }
            String content = parentContent.get(pid);
            if (content == null || content.isBlank()) {
                // 父块缺失，保留原子块
                result.add(c);
                continue;
            }
            if (!seenParentText.add(content)) continue;   // 同一父块已展开过，跳过
            // 父块内容替换子块文本，保留原 metadata（parentId 仍可追溯，便于排查）
            result.add(Content.from(TextSegment.from(content, Metadata.from(c.textSegment().metadata().toMap()))));
            expanded++;
        }
        log.info("[ParentExpand] 子块命中={} 父块展开={} 命中去重后注入={}",
                chunks.size(), parentContent.size(), expanded);
        return result;
    }
}
