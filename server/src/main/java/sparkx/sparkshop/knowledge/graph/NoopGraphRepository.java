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

import java.util.List;
import java.util.Map;

/**
 * 知识图谱仓库的 Noop 空实现。
 *
 * <p>★ 装配时机：{@link sparkx.sparkshop.knowledge.config.KnowledgeGraphConfig} 在
 * {@code ext_service_config} 表里未配置 Neo4j 连接时返回本实例（对齐 WeKnora 的 nil-driver 模式）。
 *
 * <p>★ 行为：所有写操作静默返回 0（相当于跳过），读操作返回空集合，不抛异常、不阻断 RAG 链路。
 * 上游 {@code GraphExtractionService}/{@code KnowledgeGraphChannel}/{@code CommunityService}
 * 通过 {@link #getSchema()} 返回 null 识别"未配置"状态并自行短路（避免空跑 LLM 浪费 token）。
 *
 * <p>★ 设计意图：让 {@code GraphRepository} 在 Bean 容器里始终存在（非 null），
 * 下游注入点可去掉 {@code @Autowired(required=false)}，消除遍布代码的 null 判断。
 * 真正的启停由 {@code kg_config.enabled}（页面运行期开关）控制，不再依赖启动时 yml 门控。
 */
public class NoopGraphRepository implements GraphRepository {

    @Override
    public void ensureConstraints() {
        // 未配置 Neo4j，无需建约束
    }

    @Override
    public int mergeEntities(String kbId, List<GraphExtractionResult.Entity> entities,
                              String docId, String parentId, List<String> chunkIds) {
        return 0;
    }

    @Override
    public int mergeRelations(String kbId, String docId, List<GraphExtractionResult.Relation> relations) {
        return 0;
    }

    @Override
    public Map<String, String> findElementIds(String kbId, String docId, List<String> canonicalNames) {
        return Map.of();
    }

    @Override
    public Map<String, Double> findRelatedChunkIds(String kbId, String docId, List<String> canonicalNames,
                                                    int hopDepth, double secondHopWeight) {
        return Map.of();
    }

    @Override
    public int deleteByDocument(String kbId, String docId, List<String> docChunkIds) {
        return 0;
    }

    @Override
    public Map<String, Object> findSubgraph(String kbId, int limit) {
        return Map.of();
    }

    @Override
    public Map<String, Object> findDocumentSubgraph(String kbId, String documentId, int limit) {
        return Map.of();
    }

    /**
     * 未配置 Neo4j 的唯一信号：返回 null。
     * 调用方（testConnect/hitTest/visualization/抽取入口）据此识别"未配置"并走友好分支，
     * 不靠 instanceof 判断（保持面向接口）。
     */
    @Override
    public String getSchema() {
        return null;
    }

    @Override
    public int detectCommunities(String kbId, int minCommunitySize) {
        return 0;
    }

    @Override
    public List<Map<String, Object>> listCommunities(String kbId) {
        return List.of();
    }

    @Override
    public int writeCommunitySummaries(String kbId, Map<Integer, String> summaries) {
        return 0;
    }

    @Override
    public List<Map<String, Object>> findRelatedCommunities(String kbId, List<String> entityNames, int topK) {
        return List.of();
    }
}
