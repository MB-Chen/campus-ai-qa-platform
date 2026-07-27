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
 * 图谱仓库接口：封装 Neo4j 的 Cypher 操作（实体/关系 MERGE、子图查询、schema 查询等）。
 *
 * <p>★ 隔离方式（对齐 WeKnora，文档级隔离）：实体节点 MERGE 唯一键为
 * {@code (kb_id, doc_id, canonical_name)}。同名实体在不同文档里是不同节点，
 * 不跨文档聚合。这样：删除文档=直接删该文档全部节点（简单）；按文档检索/可视化天然支持。
 *
 * <p>★ 幂等：实体/关系 MERGE 保证重复抽取同一文档不会产生重复节点
 * （onMatch 累积 parent_ids/chunk_ids；doc_id 不再是数组而是单值）。
 *
 * <p>实现：{@link Neo4jGraphRepository}（用 Neo4j Java Driver 原生 Session API）。
 */
public interface GraphRepository {

    /** 幂等建唯一约束（启动时调一次） */
    void ensureConstraints();

    /**
     * 批量 MERGE 实体（按 kb_id + doc_id + canonical_name 幂等，onMatch 累积 parent_ids/chunk_ids）。
     *
     * @param kbId        知识库 id
     * @param entities    实体列表（已消歧）
     * @param docId       当前文档 id（文档级隔离键）
     * @param parentId    当前父块 id
     * @param chunkIds    该父块下的子块 id 列表（关联到实体节点，检索时用于回溯原文）
     */
    int mergeEntities(String kbId, List<GraphExtractionResult.Entity> entities,
                      String docId, String parentId, List<String> chunkIds);

    /**
     * 批量 MERGE 关系（head/tail 均为 canonical_name，限定在同一文档内）。
     * 关系类型 relType 直接拼到 Cypher（来自 KB 白名单，非用户输入，安全）。
     *
     * @param kbId   知识库 id
     * @param docId  当前文档 id（关系在文档内建立）
     * @param relations 关系列表
     * @return MERGE 的关系数
     */
    int mergeRelations(String kbId, String docId, List<GraphExtractionResult.Relation> relations);

    /**
     * 查询某 KB（可选限定文档）下某批 canonical_name 对应节点的 elementId（回填 kg_entity.neo4j_element_id 用）。
     *
     * @param docId 可选文档 id；非空时只查该文档下的节点
     * @return Map<canonical_name, elementId>
     */
    Map<String, String> findElementIds(String kbId, String docId, List<String> canonicalNames);

    /**
     * 子图 chunk 召回：给定实体 canonical_name 列表，返回一跳/二跳邻居实体关联的 chunk_ids。
     * 用于检索侧：向量召回实体 → 子图扩展 → 取关联 chunk 原文。
     *
     * @param kbId            知识库 id
     * @param docId           可选文档 id；非空时只在该文档子图内扩展（文档级检索）
     * @param canonicalNames  实体 canonical_name 列表
     * @param hopDepth        跳数（1 或 2）
     * @param secondHopWeight 二跳衰减权重（hopDepth=2 时生效）
     * @return Map&lt;chunkId, score&gt;（score = 命中实体数 × 衰减权重，用于排序）
     */
    Map<String, Double> findRelatedChunkIds(String kbId, String docId, List<String> canonicalNames,
                                             int hopDepth, double secondHopWeight);

    /**
     * 删除某文档的全部图谱数据（文档级隔离下直接删该文档所有节点，连同关系）。
     *
     * <p>实现语义：{@code DETACH DELETE} 所有 {@code doc_id = $docId} 的 Entity 节点。
     * 文档级隔离下同名实体不跨文档聚合，故不存在「减贡献粒度」问题，删除干净利落。
     *
     * @param docChunkIds 保留参数以兼容旧调用方；文档级隔离下不再需要（按 doc_id 直接删），可传空
     */
    int deleteByDocument(String kbId, String docId, List<String> docChunkIds);

    /** 查询连通子图（可视化用，限制节点数）。KB 级，跨该 KB 所有文档 */
    Map<String, Object> findSubgraph(String kbId, int limit);

    /**
     * 查询某文档的子图（可视化用，限制节点数）。
     * 按 Entity 节点的 doc_id 过滤，仅返回该文档抽取出来的实体及其之间的连边。
     */
    Map<String, Object> findDocumentSubgraph(String kbId, String documentId, int limit);

    /** 测试连通性（配置页 testConnect 用） */
    String getSchema();


    /**
     * ★ 第四期：运行 Leiden 社区检测（依赖 Neo4j GDS 插件），把社区 id 写回 Entity 节点。
     *
     * <p>算法流程：
     * <ol>
     *   <li>按 kb_id 投影子图到内存图（GDS graph.fetch/cypher projection）</li>
     *   <li>调用 {@code gds.leiden.write}（或降级 {@code gds.louvain.write}）写入 communityId 属性</li>
     *   <li>drop 内存图</li>
     * </ol>
     *
     * @param kbId        知识库 id（按 KB 独立做社区检测）
     * @param minCommunitySize 最小社区规模（小于此值的孤立节点归到社区 -1）
     * @return 社区数量；GDS 未安装或调用失败返回 -1
     */
    int detectCommunities(String kbId, int minCommunitySize);

    /**
     * ★ 第四期：读取某 KB 下所有社区的社区 id 及其成员实体名/描述（供 LLM 生成社区摘要）。
     *
     * @return List&lt;Map&gt;：每个元素 {communityId, entities: [{name, type, description}, ...]}
     */
    List<Map<String, Object>> listCommunities(String kbId);

    /**
     * ★ 第四期：把 LLM 生成的社区摘要写回 Entity 节点的 community_summary 属性
     * （或独立表 kg_community，本接口先以参数形式接收）。
     *
     * @param kbId        知识库 id
     * @param summaries   Map&lt;communityId, 摘要文本&gt;
     * @return 写入条数
     */
    int writeCommunitySummaries(String kbId, Map<Integer, String> summaries);

    /**
     * ★ 第四期：global 模式检索——按 query 相关性召回 topK 个社区摘要作为证据。
     *
     * <p>策略：用 query 实体名先匹配社区成员，命中成员最多的社区优先；
     * 再按社区规模排序，取 topK 个社区返回其摘要。
     *
     * @param kbId          知识库 id
     * @param entityNames   query 抽取的实体名
     * @param topK          召回社区数
     * @return List&lt;Map&gt;：每个元素 {communityId, summary, hitEntities}
     */
    List<Map<String, Object>> findRelatedCommunities(String kbId, List<String> entityNames, int topK);
}
