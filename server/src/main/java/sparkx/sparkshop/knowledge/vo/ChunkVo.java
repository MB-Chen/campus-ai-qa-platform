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
 * 段落/子块展示 VO
 */
@Data
@Schema(description = "段落/子块")
public class ChunkVo implements Serializable {

    @Schema(description = "子块 id")
    private String id;

    @Schema(description = "知识库 id")
    private String kbId;

    @Schema(description = "文本内容")
    private String content;

    @Schema(description = "元数据 JSON")
    private String metadata;

    @Schema(description = "所属父块 id（文档维度列表回填，便于前端分组）")
    private String parentId;

    @Schema(description = "所属父块全文（文档维度列表回填，折叠展示用）")
    private String parentContent;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
