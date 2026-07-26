// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.ingest.mineru;

import java.util.Map;

/**
 * MinerU 解析统一结果 —— 自建 / 云端两条路径都收敛到本结构。
 *
 * @param markdown 解析得到的 Markdown 正文（含 {@code ![](images/xxx.jpg)} 形式的图片引用）
 * @param images   图片引用名 → 已 base64 解码后的原始字节，由调用方上传到对象存储并改写 Markdown
 */
public record MinerUResult(String markdown, Map<String, byte[]> images) {

    public static MinerUResult empty() {
        return new MinerUResult("", Map.of());
    }
}
