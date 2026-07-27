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

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import sparkx.sparkshop.knowledge.retrieval.RetrievalContext;

import java.util.List;

/**
 * 知识图谱检索通道的 Noop 兜底实现。
 *
 * <p>当 {@code app.rag.knowledge-graph.enabled=false} 时由 {@code GraphNoopConfig} 装配。
 * {@link #isEnabled} 恒为 false，本轮检索自动被 {@code RetrieveStage.executeChannels} 过滤掉，
 * 零开销、零侵入。
 *
 * <p>★ 之所以需要这个兜底 Bean：{@code RetrieveStage} 注入 {@code List<ConditionalRetrievalChannel>}
 * 不会因缺 KG 通道报错（其他通道照常工作），但保留这个 Noop 让"KG 模块存在性"在 Bean 容器里
 * 始终可查询（便于诊断 / 后续 {@code GraphChannel} 类型注入点扩展）。
 */
public class NoopGraphChannel implements GraphChannel {

    @Override
    public String getName() { return "noop-graph"; }

    @Override
    public int getPriority() { return 5; }

    @Override
    public boolean isEnabled(RetrievalContext ctx) { return false; }

    @Override
    public List<Content> retrieve(Query query, RetrievalContext ctx) { return List.of(); }

    @Override
    public ChannelType getType() { return ChannelType.KNOWLEDGE_GRAPH; }
}
