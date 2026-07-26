// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.retrieval;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;

import java.util.List;

/**
 * 可条件化启用的检索通道（文档 5.4.1）—— SearchChannel 设计。
 *
 * 引擎执行前 {@code filter(channel -> channel.isEnabled(ctx))}，只有满足条件的通道参与本轮检索，
 * 实现"意图驱动检索路由"而非简单全并行（省算力）。
 *
 * 两个内置通道：
 *  - {@link IntentDirectedChannel}：意图清晰时只检索对应 KB，priority 最高
 *  - {@link VectorKeywordHybridChannel}：意图模糊/缺失时全局混合检索兜底
 * 二者互补：意图清晰只用定向；模糊/缺失时全局兜底；中等置信度触发双通道。
 */
public interface ConditionalRetrievalChannel {

    /** 通道名（日志/指标用） */
    String getName();

    /** 优先级（结果合并去重用，数字越小优先级越高） */
    int getPriority();

    /**
     * ★ 通道权重（RRF 融合用，默认 1.0）。
     *
     * <p>用于 {@code FusionPostProcessor} 的 RRF 公式 {@code score = Σ w_ch / (k + rank_ch)}：
     * <ul>
     *   <li>1.0（默认）：与向量/关键词通道平权，适合「证据质量稳定、可信度高」的成熟通道</li>
     *   <li>0.5：降权，适合「新接入、噪声偏多」的通道（如知识图谱跑在全局图上、证据仅结果侧过滤）</li>
     *   <li>2.0：加权，适合「强意图场景下高精度」的通道</li>
     * </ul>
     *
     * <p>★ 调参依据：观察 {@code RerankStage} 的通道归因日志（graphIn/graphOut），
     * 若某通道长期 rerank 存活率低，则下调权重；存活率长期为 0 说明当前是纯成本，应考虑关通道。
     */
    default double getWeight() { return 1.0; }

    /** ★ 动态启用判定（核心扩展点）：本轮是否参与检索 */
    boolean isEnabled(RetrievalContext ctx);

    /** 实际检索 */
    List<Content> retrieve(Query query, RetrievalContext ctx);

    /** 通道类型 */
    ChannelType getType();

    enum ChannelType { INTENT_DIRECTED, HYBRID_GLOBAL, KEYWORD, PARENT_CHILD, KNOWLEDGE_GRAPH }
}
