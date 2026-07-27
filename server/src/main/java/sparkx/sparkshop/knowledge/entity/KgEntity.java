// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识图谱实体向量索引（对齐 WeKnora：实体向量在 PostgreSQL + pgvector）。
 *
 * <p>⚠️ {@code embedding} / {@code tsv} 两列不通过 MyBatis-Plus 自动映射（pgvector/tsvector
 * 类型 JDBC 不认），向量化与检索走 {@link sparkx.sparkshop.knowledge.mapper.KgEntityMapper}
 * 的原生 SQL。{@link #vectorized} 是普通 SMALLINT 字段，可正常参与 LambdaQueryWrapper 过滤。
 *
 * <p>图谱关系（实体→实体）存在 Neo4j（langchain4j-neo4j 的 Neo4jGraph 管理），
 * 本表只存「实体本身的向量索引」用于语义召回，{@link #neo4jElementId} 保留对应 Neo4j 节点 id 便于联动。
 */
@Data
@TableName("kg_entity")
public class KgEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关联知识库 id */
    @TableField(value = "kb_id")
    private String kbId;

    /** 关联文档 id（文档级隔离键，同名实体跨文档各自独立） */
    @TableField(value = "doc_id")
    private String docId;

    /** 实体名（原始抽取值） */
    @TableField(value = "name")
    private String name;

    /** 消歧后规范名（作为 Neo4j 节点唯一键的一部分 (kb_id, doc_id, canonical_name)） */
    @TableField(value = "canonical_name")
    private String canonicalName;

    /** 实体类型：人/组织/产品/概念/... */
    @TableField(value = "entity_type")
    private String entityType;

    /** 实体描述（参与向量化，与 name 拼接后 embedding） */
    @TableField(value = "description")
    private String description;

    /** 别名 JSON 数组 ["北京","帝都"]（文本形式存储，读取后自行解析） */
    @TableField(value = "aliases")
    private String aliases;

    /** 对应 Neo4j 节点 elementId（可视化联动用） */
    @TableField(value = "neo4j_element_id")
    private String neo4jElementId;

    /** 来源文档 JSON 数组（文本形式存储） */
    @TableField(value = "source_doc_ids")
    private String sourceDocIds;

    /** 来源父块 JSON 数组（parent_chunks.id，文本形式存储） */
    @TableField(value = "source_parent_ids")
    private String sourceParentIds;

    /** 0未向量化 1已向量化（embedding 是否已填充） */
    @TableField(value = "vectorized")
    private Integer vectorized;

    /** 1启用 2禁用 */
    @TableField(value = "status")
    private Integer status;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;
}
