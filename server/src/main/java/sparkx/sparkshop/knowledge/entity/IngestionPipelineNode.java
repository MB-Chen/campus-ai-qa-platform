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
 * 入库流水线定义节点实体（移植自 sparkxV2 t_ingestion_pipeline_node）。
 * DB 驱动的节点连线：fetcher→parser→[enhancer]→chunker→[enricher]→indexer。
 */
@Data
@TableName("t_ingestion_pipeline_node")
public class IngestionPipelineNode implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "pipeline_id")
    private String pipelineId;

    @TableField(value = "node_id")
    private String nodeId;

    /** fetcher/parser/enhancer/chunker/enricher/indexer */
    @TableField(value = "node_type")
    private String nodeType;

    /** 连线：下一节点 id */
    @TableField(value = "next_node_id")
    private String nextNodeId;

    /** 节点配置 JSON */
    @TableField(value = "settings_json")
    private String settingsJson;

    /** 条件执行（规则 DSL）JSON */
    @TableField(value = "condition_json")
    private String conditionJson;

    @TableField(value = "enabled")
    private Boolean enabled;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;
}
