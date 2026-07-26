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
 * 知识图谱抽取进度（缓存于 Redis，前端轮询读取）。
 *
 * <p>对齐 {@link SampleQueryVectorizeProgressVo} 的字段结构，前端复用同一套轮询交互。
 */
@Data
public class KgExtractionProgressVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** processing / done / failed */
    private String status;

    /** 待抽取父块总数 */
    private int total;

    /** 已处理父块数（success + failed） */
    private int done;

    /** 成功父块数 */
    private int success;

    /** 失败父块数 */
    private int failed;

    /** 累计抽取实体数 */
    private int entityCount;

    /** 累计抽取关系数 */
    private int relationCount;

    /** 兜底错误信息（任务级失败时填写） */
    private String message;

    public static KgExtractionProgressVo processing(int total) {
        KgExtractionProgressVo p = new KgExtractionProgressVo();
        p.status = "processing";
        p.total = total;
        return p;
    }
}
