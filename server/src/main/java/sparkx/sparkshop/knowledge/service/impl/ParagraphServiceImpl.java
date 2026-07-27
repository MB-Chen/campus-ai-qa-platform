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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import sparkx.sparkshop.common.exception.BusinessException;
import sparkx.sparkshop.knowledge.entity.ChunkEntity;
import sparkx.sparkshop.knowledge.entity.ParentChunkEntity;
import sparkx.sparkshop.knowledge.infra.TsVectorGenerator;
import sparkx.sparkshop.knowledge.mapper.ChunkMapper;
import sparkx.sparkshop.knowledge.mapper.ParentChunkMapper;
import sparkx.sparkshop.knowledge.service.IParagraphService;
import sparkx.sparkshop.knowledge.validate.ParagraphListValidate;
import sparkx.sparkshop.knowledge.validate.ParagraphValidate;
import sparkx.sparkshop.knowledge.vo.ChunkVo;
import sparkx.sparkshop.system.vo.PageResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 段落/子块业务实现（手动增删改；手动录入仅走关键词 tsv，不带向量）。
 */
@Service
public class ParagraphServiceImpl implements IParagraphService {

    private static final Logger log = LoggerFactory.getLogger(ParagraphServiceImpl.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private ChunkMapper chunkMapper;

    @Resource
    private ParentChunkMapper parentChunkMapper;

    /** 段落列表（按知识库或文档维度），分页。文档维度自动回填父块全文。 */
    @Override
    public PageResult<ChunkVo> page(ParagraphListValidate query) {
        // 按文档维度：走 JSONB 提取 metadata->>'document_id'，精确分页（修复原 like 兜底匹配不到的问题）
        if (query.getDocumentId() != null && !query.getDocumentId().isBlank()) {
            int size = query.safeSize();
            long offset = (long) (query.safePage() - 1) * size;
            List<ChunkEntity> rows = chunkMapper.selectByDocumentId(query.getDocumentId(), offset, size);
            long total = chunkMapper.countByDocumentId(query.getDocumentId());
            List<ChunkVo> vos = rows.stream().map(this::toVo).collect(Collectors.toList());
            // 文档维度：回填父块全文，前端按父块分组展示
            fillParentContent(vos);
            return new PageResult<>(vos, total);
        }
        // 按知识库维度：排除问题切片（metadata.type='question'），仅展示原文切片
        LambdaQueryWrapper<ChunkEntity> wrapper = new LambdaQueryWrapper<ChunkEntity>()
                .eq(query.getKbId() != null && !query.getKbId().isBlank(), ChunkEntity::getKbId, query.getKbId())
                // jsonb 列条件无法用 Lambda 表达，走 apply 拼原生 SQL 片段（兼容旧数据 type 为 null）
                .apply("(metadata->>'type' IS NULL OR metadata->>'type' <> 'question')")
                .orderByDesc(ChunkEntity::getCreatedAt);
        IPage<ChunkEntity> mpPage = new Page<>(query.safePage(), query.safeSize());
        IPage<ChunkEntity> result = chunkMapper.selectPage(mpPage, wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(this::toVo).collect(Collectors.toList()),
                result.getTotal());
    }

    /** 手动新增段落（仅写文本 + tsv，不带向量，供关键词检索命中）。 */
    @Override
    public void add(ParagraphValidate validate) {
        String content = validate.getContent();
        String chunkId = "c_" + UUID.randomUUID().toString().replace("-", "");
        // 走原生 insertTextOnly：同时写 tsv（Java 端 HanLP 预分词），
        // trigger 已禁用，必须显式写 tsv 才能被关键词检索命中。
        chunkMapper.insertTextOnly(chunkId, validate.getKbId(), content,
                TsVectorGenerator.toTsVector(content), "{}");
    }

    /** 编辑段落内容，同步刷新 tsv 关键词索引。 */
    @Override
    public void edit(ParagraphValidate validate) {
        if (validate.getId() == null || validate.getId().isBlank()) {
            throw new BusinessException("段落 id 不能为空");
        }
        ChunkEntity chunk = chunkMapper.selectById(validate.getId());
        if (chunk == null) {
            throw new BusinessException("段落不存在");
        }
        String content = validate.getContent();
        // trigger 已禁用，编辑文本后必须同步刷新 tsv，否则关键词索引陈旧。
        chunkMapper.updateContentAndTsv(validate.getId(), content, TsVectorGenerator.toTsVector(content));
    }

    /** 切换段落启停状态（active 记录在 metadata JSON 中）。 */
    @Override
    public void switchActive(String id, Integer active) {
        ChunkEntity chunk = chunkMapper.selectById(id);
        if (chunk == null) {
            throw new BusinessException("段落不存在");
        }
        // active 标记写入 metadata（实体无独立 active 列），用 JSON 解析避免正则脆弱性
        chunk.setMetadata(writeActive(chunk.getMetadata(), active));
        chunkMapper.updateById(chunk);
    }

    /** 删除段落。 */
    @Override
    public void remove(String id) {
        chunkMapper.deleteById(id);
    }

    /**
     * 文档维度列表：从当页子块 metadata 收集 parentId，批量查父块全文并回填到 VO。
     * 与 {@link sparkx.sparkshop.knowledge.retrieval.ParentChildRetriever} 同款容错：
     * 父块查询失败仅 warn 降级，不影响子块列表本身。
     * 没有 parentId 的子块（普通分块文档）保持 null，前端归到「独立切片」分组。
     */
    private void fillParentContent(List<ChunkVo> vos) {
        if (vos == null || vos.isEmpty()) return;

        // 1. 收集去重的 parentId，同时记录到 VO（便于前端分组，免去逐条 parse metadata）
        Set<String> parentIds = new LinkedHashSet<>();
        for (ChunkVo vo : vos) {
            String pid = readParentId(vo.getMetadata());
            vo.setParentId(pid);
            if (pid != null && !pid.isBlank()) {
                parentIds.add(pid);
            }
        }
        if (parentIds.isEmpty()) return;

        // 2. 批量查父块全文，建 parentId -> content 映射
        Map<String, String> parentContent = new HashMap<>();
        try {
            List<ParentChunkEntity> parents = parentChunkMapper.findByIds(new ArrayList<>(parentIds));
            if (parents != null) {
                for (ParentChunkEntity p : parents) {
                    parentContent.put(p.getId(), p.getContent());
                }
            }
        } catch (Exception e) {
            log.warn("[Paragraph] 父块回填失败，降级为只展示子块: {}", e.getMessage());
            return;
        }

        // 3. 回填到每个 VO
        for (ChunkVo vo : vos) {
            String pid = vo.getParentId();
            if (pid != null && !pid.isBlank()) {
                vo.setParentContent(parentContent.get(pid));
            }
        }
    }

    /** 从 metadata JSON 文本解析 parentId，非法 JSON / 缺字段时返回 null。 */
    private String readParentId(String metadata) {
        if (metadata == null || metadata.isBlank()) return null;
        try {
            JsonNode root = MAPPER.readTree(metadata);
            JsonNode node = root.get("parentId");
            if (node == null || node.isNull()) return null;
            String v = node.asText("");
            return v.isBlank() ? null : v;
        } catch (Exception e) {
            return null;
        }
    }

    /** 切片实体 → VO 转换。 */
    private ChunkVo toVo(ChunkEntity chunk) {
        ChunkVo vo = new ChunkVo();
        vo.setId(chunk.getId());
        vo.setKbId(chunk.getKbId());
        vo.setContent(chunk.getContent());
        vo.setMetadata(chunk.getMetadata());
        vo.setCreatedAt(chunk.getCreatedAt());
        return vo;
    }

    /**
     * 把 active 写入 metadata JSON 文本：解析原 JSON，set active 后重新序列化。
     * 原始 metadata 非法 JSON 时降级为 {active:n}。
     */
    private String writeActive(String metadata, Integer active) {
        try {
            JsonNode root = (metadata == null || metadata.isBlank())
                    ? MAPPER.createObjectNode()
                    : MAPPER.readTree(metadata);
            ObjectNode obj = root.isObject() ? (ObjectNode) root : MAPPER.createObjectNode();
            obj.put("active", active);
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"active\":" + active + "}";
        }
    }
}
