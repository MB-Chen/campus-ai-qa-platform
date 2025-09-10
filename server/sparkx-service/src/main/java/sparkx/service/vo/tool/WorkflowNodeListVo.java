// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.service.vo.tool;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class WorkflowNodeListVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 插件id
     */
    private Integer id;

    /**
     * 资源名称
     */
    private String name;

    /**
     * 资源描述
     */
    private String description;

    /**
     * 类型 1:数据库 2:API
     */
    private Integer type;

    /**
     * 状态 1:启用 2:禁用
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}