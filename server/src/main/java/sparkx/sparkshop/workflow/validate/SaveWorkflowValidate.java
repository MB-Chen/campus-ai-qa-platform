// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.workflow.validate;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 保存流程设计入参。
 */
@Data
public class SaveWorkflowValidate implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 编排 id */
    @NotEmpty(message = "编排 id 不能为空")
    private String id;

    /** 流程设计 JSON */
    @NotEmpty(message = "设计数据不能为空")
    private String flowData;
}
