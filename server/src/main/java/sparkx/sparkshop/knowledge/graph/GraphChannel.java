// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.graph;

import sparkx.sparkshop.knowledge.retrieval.ConditionalRetrievalChannel;

/**
 * 知识图谱检索通道标记接口。
 *
 * <p>继承自 {@link ConditionalRetrievalChannel}（被 {@code RetrieveStage} 自动收集），
 * 额外作为 {@code @ConditionalOnMissingBean(GraphChannel.class)} 的判定键——
 * 这样 {@code GraphNoopConfig} 能在"没有真实 KG 通道"时装配兜底，
 * 不受其他类型 Channel（IntentDirected/VectorKeywordHybrid）干扰。
 *
 * <p>实现类：
 * <ul>
 *   <li>{@link NoopGraphChannel}：KG 关闭时兜底，{@code isEnabled} 恒 false</li>
 *   <li>{@code KnowledgeGraphChannel}：KG 开启时的真实检索通道（阶段 2 落地）</li>
 * </ul>
 */
public interface GraphChannel extends ConditionalRetrievalChannel {
}
