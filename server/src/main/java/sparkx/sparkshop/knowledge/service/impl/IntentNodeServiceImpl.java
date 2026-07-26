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
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import sparkx.sparkshop.common.exception.BusinessException;
import sparkx.sparkshop.knowledge.entity.IntentNodeEntity;
import sparkx.sparkshop.knowledge.intent.IntentTreeCacheManager;
import sparkx.sparkshop.knowledge.mapper.IntentNodeMapper;
import sparkx.sparkshop.knowledge.service.IntentNodeService;
import sparkx.sparkshop.knowledge.vo.IntentNodeSaveVo;
import sparkx.sparkshop.knowledge.vo.IntentNodeTreeVo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 意图树后台管理服务实现
 */
@Service
public class IntentNodeServiceImpl implements IntentNodeService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> VALID_KINDS = Set.of("KB", "SYSTEM", "MCP");

    @Resource
    private IntentNodeMapper intentNodeMapper;
    @Resource
    private IntentTreeCacheManager intentTreeCacheManager;

    /** 查询整棵意图树（含禁用节点，内存组装 children）。 */
    @Override
    public List<IntentNodeTreeVo> tree() {
        // 查全部未删除节点（含禁用），内存组装树
        List<IntentNodeEntity> rows = intentNodeMapper.selectList(
                new LambdaQueryWrapper<IntentNodeEntity>()
                        .eq(IntentNodeEntity::getDeleted, false)
                        .orderByAsc(IntentNodeEntity::getCreatedAt));

        Map<String, IntentNodeTreeVo> byId = new LinkedHashMap<>();
        for (IntentNodeEntity row : rows) {
            byId.put(row.getId(), toVo(row));
        }

        // 拼接 children，收集根
        List<IntentNodeTreeVo> roots = new ArrayList<>();
        for (IntentNodeTreeVo node : byId.values()) {
            String pid = node.getParentId();
            if (pid == null || pid.isBlank() || !byId.containsKey(pid)) {
                roots.add(node);
            } else {
                IntentNodeTreeVo parent = byId.get(pid);
                if (parent.getChildren() == null) {
                    parent.setChildren(new ArrayList<>());
                }
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    /** 新增意图节点，自动生成 id 并刷新缓存。 */
    @Override
    public void add(IntentNodeSaveVo vo) {
        validate(vo, true);
        IntentNodeEntity entity = new IntentNodeEntity();
        entity.setId(genId(vo.getName()));
        entity.setParentId(vo.getParentId());
        entity.setLevel(0); // 单层模型，level 恒为 0（保留字段仅为兼容旧数据）
        entity.setKind(vo.getKind());
        entity.setName(vo.getName());
        entity.setDescription(vo.getDescription());
        entity.setExamples(toJson(vo.getExamples()));
        // KB 节点：优先用传入的 collectionName，否则用 kbId 兜底
        entity.setCollectionName("KB".equals(vo.getKind())
                ? (vo.getCollectionName() != null && !vo.getCollectionName().isBlank()
                        ? vo.getCollectionName() : vo.getKbId())
                : null);
        entity.setDocIds(toJson(vo.getDocIds()));
        entity.setMcpToolId("MCP".equals(vo.getKind()) ? vo.getMcpToolId() : null);
        entity.setPromptTemplate(vo.getPromptTemplate());
        entity.setParamPromptTemplate("MCP".equals(vo.getKind()) ? vo.getParamPromptTemplate() : null);
        entity.setTopK(vo.getTopK());
        entity.setEnabled(vo.getEnabled() == null || vo.getEnabled());
        entity.setDeleted(false);
        entity.setCreatedAt(LocalDateTime.now());
        intentNodeMapper.insert(entity);
        intentTreeCacheManager.clearIntentTreeCache();
    }

    /** 编辑意图节点（校验防环路），更新后刷新缓存。 */
    @Override
    public void edit(IntentNodeSaveVo vo) {
        if (vo.getId() == null || vo.getId().isBlank()) {
            throw new BusinessException("节点ID不能为空");
        }
        IntentNodeEntity exist = intentNodeMapper.selectById(vo.getId());
        if (exist == null || Boolean.TRUE.equals(exist.getDeleted())) {
            throw new BusinessException("节点不存在");
        }
        validate(vo, false);

        exist.setParentId(vo.getParentId());
        exist.setLevel(0); // 单层模型，level 恒为 0
        exist.setKind(vo.getKind());
        exist.setName(vo.getName());
        exist.setDescription(vo.getDescription());
        exist.setExamples(toJson(vo.getExamples()));
        exist.setCollectionName("KB".equals(vo.getKind())
                ? (vo.getCollectionName() != null && !vo.getCollectionName().isBlank()
                        ? vo.getCollectionName() : vo.getKbId())
                : null);
        exist.setDocIds(toJson(vo.getDocIds()));
        exist.setMcpToolId("MCP".equals(vo.getKind()) ? vo.getMcpToolId() : null);
        exist.setPromptTemplate(vo.getPromptTemplate());
        exist.setParamPromptTemplate("MCP".equals(vo.getKind()) ? vo.getParamPromptTemplate() : null);
        exist.setTopK(vo.getTopK());
        exist.setEnabled(vo.getEnabled() == null || vo.getEnabled());
        intentNodeMapper.updateById(exist);
        intentTreeCacheManager.clearIntentTreeCache();
    }

    /** 逻辑删除节点及其所有后代，刷新缓存。 */
    @Override
    public void delete(String id) {
        if (id == null || id.isBlank()) {
            throw new BusinessException("节点ID不能为空");
        }
        // 收集本节点及所有后代，逻辑删除
        Set<String> toDelete = new HashSet<>();
        toDelete.add(id);
        collectDescendants(id, toDelete);

        LambdaUpdateWrapper<IntentNodeEntity> uw = new LambdaUpdateWrapper<>();
        uw.in(IntentNodeEntity::getId, toDelete).set(IntentNodeEntity::getDeleted, true);
        intentNodeMapper.update(null, uw);
        intentTreeCacheManager.clearIntentTreeCache();
    }

    /** 批量启用节点，刷新缓存。 */
    @Override
    public void batchEnable(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        LambdaUpdateWrapper<IntentNodeEntity> uw = new LambdaUpdateWrapper<>();
        uw.in(IntentNodeEntity::getId, ids).set(IntentNodeEntity::getEnabled, true);
        intentNodeMapper.update(null, uw);
        intentTreeCacheManager.clearIntentTreeCache();
    }

    /** 批量禁用节点，刷新缓存。 */
    @Override
    public void batchDisable(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        LambdaUpdateWrapper<IntentNodeEntity> uw = new LambdaUpdateWrapper<>();
        uw.in(IntentNodeEntity::getId, ids).set(IntentNodeEntity::getEnabled, false);
        intentNodeMapper.update(null, uw);
        intentTreeCacheManager.clearIntentTreeCache();
    }


    /** 校验节点参数合法性（名称、类型、MCP 工具 ID、KB 关联）。 */
    private void validate(IntentNodeSaveVo vo, boolean isCreate) {
        if (vo.getName() == null || vo.getName().isBlank()) {
            throw new BusinessException("名称不能为空");
        }
        if (vo.getKind() == null || !VALID_KINDS.contains(vo.getKind())) {
            throw new BusinessException("类型必须为 KB / SYSTEM / MCP");
        }
        // MCP 必须填工具ID
        if ("MCP".equals(vo.getKind()) && (vo.getMcpToolId() == null || vo.getMcpToolId().isBlank())) {
            throw new BusinessException("MCP 类型必须填写工具ID");
        }
        // KB 类型必须关联知识库（collectionName 或 kbId 二选一）
        if ("KB".equals(vo.getKind())) {
            boolean hasColl = vo.getCollectionName() != null && !vo.getCollectionName().isBlank();
            boolean hasKb = vo.getKbId() != null && !vo.getKbId().isBlank();
            if (!hasColl && !hasKb) {
                throw new BusinessException("KB 类型必须关联知识库");
            }
        }
    }

    /** 递归收集所有后代 id */
    private void collectDescendants(String parentId, Set<String> acc) {
        List<IntentNodeEntity> children = intentNodeMapper.selectList(
                new LambdaQueryWrapper<IntentNodeEntity>()
                        .eq(IntentNodeEntity::getParentId, parentId)
                        .eq(IntentNodeEntity::getDeleted, false));
        for (IntentNodeEntity child : children) {
            if (acc.add(child.getId())) {
                collectDescendants(child.getId(), acc);
            }
        }
    }

    /** 实体 → VO 转换（供后台管理展示）。 */
    private IntentNodeTreeVo toVo(IntentNodeEntity row) {
        IntentNodeTreeVo vo = new IntentNodeTreeVo();
        vo.setId(row.getId());
        vo.setParentId(row.getParentId());
        vo.setLevel(row.getLevel());
        vo.setKind(row.getKind());
        vo.setName(row.getName());
        vo.setDescription(row.getDescription());
        vo.setExamples(parseExamples(row.getExamples()));
        vo.setCollectionName(row.getCollectionName());
        vo.setDocIds(parseDocIds(row.getDocIds()));
        vo.setMcpToolId(row.getMcpToolId());
        vo.setPromptTemplate(row.getPromptTemplate());
        vo.setParamPromptTemplate(row.getParamPromptTemplate());
        vo.setTopK(row.getTopK());
        vo.setEnabled(row.getEnabled() == null || row.getEnabled());
        vo.setCreatedAt(row.getCreatedAt() == null ? null
                : row.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return vo;
    }

    /** 生成节点 id：前缀 + 名称哈希 + 时间戳后4位，保证可读且唯一 */
    private String genId(String name) {
        String prefix = "intent";
        String hash = name == null ? "" : Integer.toHexString(Math.abs(name.hashCode()));
        String ts = String.valueOf(System.currentTimeMillis());
        return prefix + "_" + hash + ts.substring(ts.length() - 6);
    }

    /** 示例列表序列化为 JSON 文本。 */
    private String toJson(List<String> examples) {
        if (examples == null || examples.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(examples);
        } catch (Exception e) {
            return null;
        }
    }

    /** JSON 文本反序列化为示例列表。 */
    private List<String> parseExamples(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    /** JSON 文本反序列化为文档ID列表（KB 限定文档）。 */
    private List<String> parseDocIds(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            return null;
        }
    }
}
