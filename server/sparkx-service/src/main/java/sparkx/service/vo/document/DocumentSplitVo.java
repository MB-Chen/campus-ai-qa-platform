// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.service.vo.document;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class DocumentSplitVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文件分段
     */
    private List<DocumentItemVo> content;

    /**
     * 文件标题
     */
    private String name;

    /**
     * 文本字符数
     */
    private long fileSize;
}