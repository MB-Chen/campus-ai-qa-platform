// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.service.vo.application;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class ChatInfoVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 应用id
     */
    private String accessToken;

    /**
     * 是否开启调试
     */
    private Boolean debug;
}