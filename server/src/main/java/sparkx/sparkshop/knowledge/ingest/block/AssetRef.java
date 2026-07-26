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
 * 多模态资产引用（移植自 sparkxV2）—— 图片等资产的公网 URL 引用。
 *
 * @param publicUrl     公网访问 URL（MinIO/S3）
 * @param mime          MIME 类型（image/png 等）
 * @param sourceBlockId 来源 Block id
 */
public record AssetRef(String publicUrl, String mime, String sourceBlockId) { }
