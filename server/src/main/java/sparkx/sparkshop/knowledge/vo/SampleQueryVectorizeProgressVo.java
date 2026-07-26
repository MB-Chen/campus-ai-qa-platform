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

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 样例查询批量向量化进度（缓存于 Redis，前端轮询读取）。
 *
 * <p>对齐 {@link QuestionImportProgressVo} 的字段结构，前端复用同一套轮询交互。
 */
@Data
public class SampleQueryVectorizeProgressVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** processing / done / failed */
    private String status;

    /** 总条数 */
    private int total;

    /** 已处理条数（success + failed） */
    private int done;

    /** 成功条数 */
    private int success;

    /** 失败条数 */
    private int failed;

    /** 兜底错误信息（任务级失败时填写） */
    private String message;

    public static SampleQueryVectorizeProgressVo processing(int total) {
        SampleQueryVectorizeProgressVo p = new SampleQueryVectorizeProgressVo();
        p.status = "processing";
        p.total = total;
        return p;
    }
}
