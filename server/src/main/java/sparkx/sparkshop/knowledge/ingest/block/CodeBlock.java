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

/** 代码块（移植自 sparkxV2）—— atomic，整块一个 chunk */
public record CodeBlock(String id, Provenance provenance, String outlinePath,
                        String language, String code) implements Block {
    @Override public Type type() { return Type.CODE; }
}
