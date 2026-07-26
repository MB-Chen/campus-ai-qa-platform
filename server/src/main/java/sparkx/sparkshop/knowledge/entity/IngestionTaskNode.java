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
 * 入库任务节点日志实体（移植自 sparkxV2 t_ingestion_task_node）。
 * 每节点独立日志，精确定位问题。status: success/failed/skipped/error。
 */
@Data
@TableName("t_ingestion_task_node")
public class IngestionTaskNode implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "task_id")
    private String taskId;

    @TableField(value = "node_id")
    private String nodeId;

    /** success/failed/skipped/error */
    @TableField(value = "status")
    private String status;

    @TableField(value = "message")
    private String message;

    @TableField(value = "duration_ms")
    private Long durationMs;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;
}
