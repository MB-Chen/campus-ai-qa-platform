// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库文档展示 VO
 */
@Data
@Schema(description = "知识库文档")
public class DocumentVo implements Serializable {

    @Schema(description = "文档 id")
    private String id;

    @Schema(description = "知识库 id")
    private String kbId;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "文件大小（字节）")
    private Long fileSize;

    @Schema(description = "MinIO 对象 key（详情接口返回，供前端判断可否下载）")
    private String storageUrl;

    @Schema(description = "向量化状态: pending/processing/done/failed")
    private String status;

    @Schema(description = "子块数")
    private Integer chunkCount;

    @Schema(description = "问题生成状态: 1待生成 2生成中 3已生成")
    private Integer questionStatus;

    @Schema(description = "是否启用 1正常 2禁用")
    private Integer active;

    @Schema(description = "知识图谱开关 1=启用 2=禁用（文档级）")
    private Integer kgEnabled;

    @Schema(description = "图谱抽取状态：null=未抽取 / pending / extracting / done / failed")
    private String kgExtractStatus;

    @Schema(description = "已抽取实体数（kg_extraction_record.entity_count，未抽取时为 null）")
    private Integer kgEntityCount;

    @Schema(description = "入库耗时统计（jsonb 反序列化）")
    private IngestionSummary ingestionSummary;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间（状态变更时刻，前端用于推算 processing 已耗时）")
    private LocalDateTime updatedAt;

    @Schema(description = "向量化实时进度（仅 status=processing 时有值，含 embed 阶段实时耗时）")
    private IngestionSummary.StageStat embedProgress;
}
