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
 * 图片块（移植自 sparkxV2）—— VLM 图生文描述 + 原图 URL。
 *
 * ★ ImageChunker：atomic，content 渲染 ![](url)，description 作 embedding 文本。
 * 杜绝"有图无描述"召回不到的残缺数据。
 */
public record ImageBlock(String id, Provenance provenance, String outlinePath,
                         String description, String assetUrl) implements Block {
    @Override public Type type() { return Type.IMAGE; }
}
