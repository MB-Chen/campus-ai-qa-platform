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
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 新增/编辑外部服务配置参数。
 *
 * <p>config 为 JSON 文本，schema 按 category 不同（详见
 * {@link sparkx.sparkshop.knowledge.service.IExtServiceConfigService} 类注释）。
 * testConnect 复用本入参，无需先落库。
 */
@Data
@Schema(description = "新增/编辑外部服务配置参数")
public class ExtServiceConfigValidate implements Serializable {

    @Schema(description = "配置 id（编辑/测试时必填）")
    private Integer id;

    @Schema(description = "配置名称（如「自建MinerU」）")
    @NotBlank(message = "配置名称不能为空")
    private String name;

    @Schema(description = "服务类别：mineru_self / mineru_cloud / ...")
    @NotBlank(message = "服务类别不能为空")
    private String category;

    @Schema(description = "配置 JSON 文本（schema 按 category）")
    private String config;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "1启用 2禁用")
    private Integer status;

    @Schema(description = "排序（数值小者靠前）")
    private Integer sort;
}
