// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.validate;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import sparkx.sparkshop.system.vo.PageQuery;

import java.io.Serializable;

/**
 * 知识图谱抽取记录列表入参（按知识库 / 文档 / 状态过滤）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "知识图谱抽取记录列表入参")
public class KgRecordListValidate extends PageQuery implements Serializable {

    @Schema(description = "知识库 id")
    private String kbId;

    @Schema(description = "文档 id")
    private String documentId;

    @Schema(description = "抽取状态")
    private String status;
}
