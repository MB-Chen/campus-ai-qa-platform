// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.ingest.block;

/**
 * Block 来源信息（移植自 sparkxV2）—— 文件名/页码/sheet/bbox。
 */
public record Provenance(String fileName, int page, String sheet, String bbox) {
    public static Provenance of(String fileName) {
        return new Provenance(fileName, 0, null, null);
    }
}
