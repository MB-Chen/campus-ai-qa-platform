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

import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Neo4j 图谱仓库实现：用 Neo4j Java Driver 5.x 原生 Session API 执行 Cypher。
 *
 * <p>★ 事务：{@code Session.executeWrite/executeRead} 由 driver 管理重试与提交，
 * 业务代码只写 {@code tx.run(cypher, params)}。
 *
 * <p>★ 参数化防注入：实体/关系属性全部走 {@code $param}，不拼接。
 * 唯一例外是关系 <b>类型</b>（如 {@code [:WORKS_FOR]}）——Cypher 语法不允许关系类型参数化，
 * 这里用反引号包裹后拼接，来源是 KB 白名单（非用户输入），且做字符白名单校验防注入。
 *
 * <p>★ 隔离：所有 Cypher 都带 {@code WHERE n.kb_id = $kbId}，租户/KB 间零干扰。
 *
 * <p>依赖来源：{@code langchain4j-community-neo4j} 传递的 {@code org.neo4j.driver:neo4j-java-driver}。
 */
public class Neo4jGraphRepository implements GraphRepository {

    private static final Logger log = LoggerFactory.getLogger(Neo4jGraphRepository.class);

    /**
     * 关系类型清洗：去掉反引号和控制字符（防 Cypher 注入），保留中文/字母/数字/下划线/短横。
     * ★ 旧版正则 ^[A-Z][A-Z0-9_]{0,62}$ 只允许英文大写，会把 LLM 抽出的中文关系类型（包含/位于/使用…）
     * 全过滤掉，导致一条关系都写不进 Neo4j。中文关系类型用反引号包裹后 Neo4j 完全支持。
     * 清洗后的类型统一用反引号包裹拼进 Cypher（见 mergeRelations），故只需排除反引号本身即可防注入。
     */
    private static final java.util.regex.Pattern REL_TYPE_SANITIZE =
            java.util.regex.Pattern.compile("[`\\x00-\\x1f\\x7f]");

    /** 关系类型清洗：去掉反引号与控制字符，trim 后空则丢弃。返回 null 表示该关系类型非法 */
    private static String sanitizeRelType(String type) {
        if (type == null) return null;
        String cleaned = REL_TYPE_SANITIZE.matcher(type).replaceAll("").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private final Driver driver;

    public Neo4jGraphRepository(Driver driver) {
        this.driver = driver;
    }

    /**
     * 容器关闭时回收 driver 连接池。
     * <p>★ driver 由 {@link sparkx.sparkshop.knowledge.config.KnowledgeGraphConfig#graphRepository}
     * 内联创建（不再独立 @Bean），由本实例持有，故在此 close。
     */
    @PreDestroy
    public void close() {
        try {
            driver.close();
            log.info("[KnowledgeGraph] Neo4j driver 已关闭");
        } catch (Exception e) {
            log.warn("[KnowledgeGraph] Neo4j driver 关闭异常: {}", e.getMessage());
        }
    }

    @Override
    public void ensureConstraints() {
        // 文档级隔离：唯一约束 (kb_id, doc_id, canonical_name)，同名实体跨文档各自独立节点
        try (Session session = driver.session()) {
            // ★ 先删旧约束 entity_kb_canonical_unique（KB 聚合模型遗留）：
            //   旧约束是 (kb_id, canonical_name) UNIQUE，文档隔离下同名实体跨文档需建多个节点，
            //   旧约束还在会导致第二个文档抽到同名实体时 ConstraintValidationFailed 抽取失败。
            //   DROP IF EXISTS 幂等，约束不存在时不报错。
            session.run("DROP CONSTRAINT entity_kb_canonical_unique IF EXISTS");
            session.run("CREATE CONSTRAINT entity_kb_doc_canonical_unique IF NOT EXISTS " +
                    "FOR (n:Entity) REQUIRE (n.kb_id, n.doc_id, n.canonical_name) IS UNIQUE");
            // kb_id + doc_id 索引加速按 KB / 文档过滤
            session.run("CREATE INDEX entity_kb_id IF NOT EXISTS FOR (n:Entity) ON (n.kb_id)");
            session.run("CREATE INDEX entity_doc_id IF NOT EXISTS FOR (n:Entity) ON (n.doc_id)");
            session.run("CREATE INDEX entity_name IF NOT EXISTS FOR (n:Entity) ON (n.name)");
        }
    }

    @Override
    public int mergeEntities(String kbId, List<GraphExtractionResult.Entity> entities,
                              String docId, String parentId, List<String> chunkIds) {
        if (entities == null || entities.isEmpty()) return 0;

        // 构造 UNWIND 参数（每个实体一行）
        List<Map<String, Object>> rows = new ArrayList<>(entities.size());
        for (GraphExtractionResult.Entity e : entities) {
            String canonical = blankToNull(e.canonicalName());
            if (canonical == null) canonical = blankToNull(e.name());
            if (canonical == null) continue;  // 无规范名跳过

            Map<String, Object> row = new HashMap<>();
            row.put("canonical", canonical);
            row.put("name", blankToNull(e.name()));
            row.put("type", blankToNull(e.type()));
            row.put("description", blankToNull(e.description()));
            row.put("aliases", e.aliases() == null ? List.of() : e.aliases());
            row.put("parentId", parentId);
            row.put("chunkIds", chunkIds == null ? List.of() : chunkIds);
            rows.add(row);
        }
        if (rows.isEmpty()) return 0;

        // 文档级隔离：MERGE 唯一键 = (kb_id, doc_id, canonical_name)
        String cypher = """
                UNWIND $rows AS r
                MERGE (n:Entity {kb_id: $kbId, doc_id: $docId, canonical_name: r.canonical})
                ON CREATE SET n.created_at = timestamp()
                SET n.name = coalesce(r.name, n.name),
                    n.entity_type = coalesce(r.type, n.entity_type),
                    n.description = coalesce(r.description, n.description),
                    n.aliases = apoc.coll.toSet(apoc.coll.union(coalesce(n.aliases, []), coalesce(r.aliases, []))),
                    n.parent_ids = apoc.coll.union(coalesce(n.parent_ids, []), CASE WHEN r.parentId IS NULL THEN [] ELSE [r.parentId] END),
                    n.chunk_ids = apoc.coll.union(coalesce(n.chunk_ids, []), coalesce(r.chunkIds, [])),
                    n.updated_at = timestamp()
                """;
        Map<String, Object> params = new HashMap<>();
        params.put("kbId", kbId);
        params.put("docId", docId);
        params.put("rows", rows);
        try (Session session = driver.session()) {
            return session.executeWrite(tx -> {
                Result result = tx.run(cypher, params);
                result.consume();  // 确保执行完成
                return rows.size();
            });
        } catch (Exception e) {
            // apoc 可能未装，降级到不用 apoc 的版本
            log.warn("[Neo4jRepo] mergeEntities 失败（尝试 apoc 降级）: {}", e.getMessage());
            return mergeEntitiesNoApoc(kbId, docId, rows);
        }
    }

    /** 不依赖 APOC 的降级 MERGE（用普通 list + 去重 UDF 替代 apoc.coll.union） */
    private int mergeEntitiesNoApoc(String kbId, String docId, List<Map<String, Object>> rows) {
        String cypher = """
                UNWIND $rows AS r
                MERGE (n:Entity {kb_id: $kbId, doc_id: $docId, canonical_name: r.canonical})
                ON CREATE SET n.created_at = timestamp(),
                              n.parent_ids = [], n.chunk_ids = [], n.aliases = []
                SET n.name = coalesce(r.name, n.name),
                    n.entity_type = coalesce(r.type, n.entity_type),
                    n.description = coalesce(r.description, n.description),
                    n.aliases = r.aliases,
                    n.parent_ids = n.parent_ids + (CASE WHEN r.parentId IS NULL THEN [] ELSE [r.parentId] END),
                    n.chunk_ids = n.chunk_ids + coalesce(r.chunkIds, []),
                    n.updated_at = timestamp()
                """;
        Map<String, Object> params = new HashMap<>();
        params.put("kbId", kbId);
        params.put("docId", docId);
        params.put("rows", rows);
        try (Session session = driver.session()) {
            return session.executeWrite(tx -> {
                tx.run(cypher, params).consume();
                return rows.size();
            });
        } catch (Exception e) {
            log.error("[Neo4jRepo] mergeEntities 降级仍失败 kbId={} docId={}: {}", kbId, docId, e.getMessage());
            return 0;
        }
    }

    @Override
    public int mergeRelations(String kbId, String docId, List<GraphExtractionResult.Relation> relations) {
        if (relations == null || relations.isEmpty()) return 0;

        // 按关系类型分组（Cypher 关系类型不能参数化，需按类型分别 MERGE）。
        // ★ 关系类型清洗（sanitizeRelType）：去掉反引号和控制字符防注入，保留中文（如 包含/使用）。
        // 旧版用 ^[A-Z][A-Z0-9_]+$ 白名单会把中文关系全过滤掉，导致 0 条关系写入。
        Map<String, List<GraphExtractionResult.Relation>> byType = relations.stream()
                .map(r -> {
                    String cleaned = sanitizeRelType(r.type());
                    return cleaned == null ? null
                            : new java.util.AbstractMap.SimpleEntry<>(cleaned,
                                    new GraphExtractionResult.Relation(r.head(), cleaned, r.tail()));
                })
                .filter(java.util.Objects::nonNull)
                .filter(e -> blankToNull(e.getValue().head()) != null && blankToNull(e.getValue().tail()) != null)
                .collect(Collectors.groupingBy(java.util.AbstractMap.SimpleEntry::getKey,
                        Collectors.mapping(java.util.AbstractMap.SimpleEntry::getValue, Collectors.toList())));

        int total = 0;
        for (var entry : byType.entrySet()) {
            String relType = entry.getKey();
            List<Map<String, Object>> rows = entry.getValue().stream()
                    .map(r -> {
                        Map<String, Object> row = new HashMap<>();
                        row.put("head", r.head());
                        row.put("tail", r.tail());
                        return row;
                    })
                    .toList();
            // 反引号包裹关系类型（已清洗掉反引号，安全）。Neo4j 反引号包裹的关系类型支持中文
            // 文档级隔离：MATCH 两端节点带 doc_id（关系只在文档内建立）
            String cypher = """
                    UNWIND $rows AS r
                    MATCH (a:Entity {kb_id: $kbId, doc_id: $docId, canonical_name: r.head})
                    MATCH (b:Entity {kb_id: $kbId, doc_id: $docId, canonical_name: r.tail})
                    MERGE (a)-[:`%s`]->(b)
                    """.formatted(relType);
            Map<String, Object> params = new HashMap<>();
            params.put("kbId", kbId);
            params.put("docId", docId);
            params.put("rows", rows);
            try (Session session = driver.session()) {
                Integer n = session.executeWrite(tx -> {
                    Result result = tx.run(cypher, params);
                    result.consume();
                    return rows.size();
                });
                total += n;
            } catch (Exception e) {
                log.warn("[Neo4jRepo] mergeRelations[{}] 失败: {}", relType, e.getMessage());
            }
        }
        return total;
    }

    @Override
    public Map<String, String> findElementIds(String kbId, String docId, List<String> canonicalNames) {
        if (canonicalNames == null || canonicalNames.isEmpty()) return Map.of();
        // docId 非空时限定到单文档，否则 KB 级（跨文档，同名实体取其一）
        boolean byDoc = docId != null && !docId.isBlank();
        String cypher = byDoc
                ? "MATCH (n:Entity {kb_id: $kbId, doc_id: $docId}) WHERE n.canonical_name IN $names "
                  + "RETURN n.canonical_name AS canonical, elementId(n) AS eid"
                : "MATCH (n:Entity {kb_id: $kbId}) WHERE n.canonical_name IN $names "
                  + "RETURN n.canonical_name AS canonical, elementId(n) AS eid";
        Map<String, Object> params = new HashMap<>();
        params.put("kbId", kbId);
        if (byDoc) params.put("docId", docId);
        params.put("names", canonicalNames);
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Result result = tx.run(cypher, params);
                Map<String, String> map = new HashMap<>();
                while (result.hasNext()) {
                    Record r = result.next();
                    map.put(r.get("canonical").asString(), r.get("eid").asString());
                }
                return map;
            });
        } catch (Exception e) {
            log.warn("[Neo4jRepo] findElementIds 失败: {}", e.getMessage());
            return Map.of();
        }
    }

    @Override
    public Map<String, Double> findRelatedChunkIds(String kbId, String docId, List<String> canonicalNames,
                                                     int hopDepth, double secondHopWeight) {
        if (canonicalNames == null || canonicalNames.isEmpty()) return Map.of();

        // docId 非空时限定单文档子图，否则 KB 级（跨文档扩展）
        boolean byDoc = docId != null && !docId.isBlank();

        // 一跳：直接命中实体的 chunk_ids
        Map<String, Double> result = new HashMap<>();
        String directCypher = byDoc
                ? """
                  MATCH (n:Entity {kb_id: $kbId, doc_id: $docId})
                  WHERE n.canonical_name IN $names
                  RETURN n.canonical_name AS name, n.chunk_ids AS chunks
                  """
                : """
                  MATCH (n:Entity {kb_id: $kbId})
                  WHERE n.canonical_name IN $names
                  RETURN n.canonical_name AS name, n.chunk_ids AS chunks
                  """;
        Map<String, Object> params = new HashMap<>();
        params.put("kbId", kbId);
        if (byDoc) params.put("docId", docId);
        params.put("names", canonicalNames);
        try (Session session = driver.session()) {
            session.executeRead(tx -> {
                Result r = tx.run(directCypher, params);
                while (r.hasNext()) {
                    Record rec = r.next();
                    List<Object> chunks = rec.get("chunks").asList();
                    for (Object chunkId : chunks) {
                        result.merge(chunkId.toString(), 1.0, Double::sum);
                    }
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("[Neo4jRepo] findRelatedChunkIds 直接命中失败: {}", e.getMessage());
        }

        // 二跳：邻居实体的 chunk_ids（衰减权重）。文档级隔离下邻居也带 doc_id
        if (hopDepth >= 2) {
            String neighborCypher = byDoc
                    ? """
                      MATCH (n:Entity {kb_id: $kbId, doc_id: $docId})-[r]-(m:Entity {kb_id: $kbId, doc_id: $docId})
                      WHERE n.canonical_name IN $names
                      RETURN DISTINCT m.canonical_name AS name, m.chunk_ids AS chunks
                      """
                    : """
                      MATCH (n:Entity {kb_id: $kbId})-[r]-(m:Entity {kb_id: $kbId})
                      WHERE n.canonical_name IN $names
                      RETURN DISTINCT m.canonical_name AS name, m.chunk_ids AS chunks
                      """;
            try (Session session = driver.session()) {
                session.executeRead(tx -> {
                    Result r = tx.run(neighborCypher, params);
                    while (r.hasNext()) {
                        Record rec = r.next();
                        List<Object> chunks = rec.get("chunks").asList();
                        for (Object chunkId : chunks) {
                            result.merge(chunkId.toString(), secondHopWeight, Double::sum);
                        }
                    }
                    return null;
                });
            } catch (Exception e) {
                log.warn("[Neo4jRepo] findRelatedChunkIds 二跳失败: {}", e.getMessage());
            }
        }
        return result;
    }

    @Override
    public int deleteByDocument(String kbId, String docId, List<String> docChunkIds) {
        // 文档级隔离：直接删该文档全部 Entity 节点（DETACH DELETE 连带删关系）。
        // 同名实体不跨文档聚合，故不存在减贡献粒度问题。docChunkIds 参数仅为兼容旧调用方，不再使用。
        String cypher = """
                MATCH (n:Entity {kb_id: $kbId, doc_id: $docId})
                DETACH DELETE n
                """;
        Map<String, Object> params = Map.of("kbId", kbId, "docId", docId);
        try (Session session = driver.session()) {
            return session.executeWrite(tx -> {
                Result result = tx.run(cypher, params);
                int count = result.consume().counters().nodesDeleted();
                return count;
            });
        } catch (Exception e) {
            log.warn("[Neo4jRepo] deleteByDocument 失败 kbId={} docId={}: {}", kbId, docId, e.getMessage());
            return 0;
        }
    }

    @Override
    public Map<String, Object> findSubgraph(String kbId, int limit) {
        // ★ 先对「节点」去重 LIMIT，再在这些限定节点间收集关系。
        // 旧写法把 LIMIT 放在 OPTIONAL MATCH 之后，LIMIT 作用在「节点×出边」的行流上而非节点数：
        // 有向匹配 (n)-[r]->(m) 下，只做尾实体/无边节点产生 r IS NULL 的行，叠加 Neo4j 行序不确定，
        // 前 N 行可能全是 null-r，CASE WHEN r IS NULL THEN null 把所有边归零 → 返回 edges:[]（节点正常）。
        // 现在 LIMIT 作用于去重后的节点（200=200 个节点），OPTIONAL MATCH 用 m IN limitedNodes
        // 关联到限定节点集，截断的关系不会污染；ORDER BY 让节点选择稳定消除行序不确定性。
        String cypher = """
                MATCH (n:Entity {kb_id: $kbId})
                WITH n ORDER BY n.canonical_name LIMIT $limit
                WITH collect(n) AS limitedNodes
                UNWIND limitedNodes AS n
                OPTIONAL MATCH (n)-[r]->(m)
                WHERE m IN limitedNodes
                WITH collect(DISTINCT {
                    id: elementId(n),
                    label: coalesce(n.name, n.canonical_name),
                    type: n.entity_type,
                    description: n.description
                }) AS nodes,
                collect(DISTINCT CASE WHEN r IS NULL THEN null ELSE {
                    from: elementId(startNode(r)),
                    to: elementId(endNode(r)),
                    type: type(r)
                } END) AS edgesRaw
                RETURN nodes, [x IN edgesRaw WHERE x IS NOT NULL] AS edges
                """;
        Map<String, Object> params = Map.of("kbId", kbId, "limit", limit);
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Result result = tx.run(cypher, params);
                if (!result.hasNext()) return Map.of("nodes", List.of(), "edges", List.of());
                Record r = result.next();
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("nodes", r.get("nodes").asList(Value::asMap));
                out.put("edges", r.get("edges").asList(Value::asMap));
                return out;
            });
        } catch (Exception e) {
            log.warn("[Neo4jRepo] findSubgraph 失败: {}", e.getMessage());
            return Map.of("nodes", List.of(), "edges", List.of());
        }
    }

    @Override
    public Map<String, Object> findDocumentSubgraph(String kbId, String documentId, int limit) {
        // 与 findSubgraph 同样的「先对节点去重 LIMIT 再收集关系」范式，
        // 文档级隔离：直接按 doc_id 属性过滤（不再用 doc_ids 数组包含）。
        String cypher = """
                MATCH (n:Entity {kb_id: $kbId, doc_id: $docId})
                WITH n ORDER BY n.canonical_name LIMIT $limit
                WITH collect(n) AS limitedNodes
                UNWIND limitedNodes AS n
                OPTIONAL MATCH (n)-[r]->(m)
                WHERE m IN limitedNodes
                WITH collect(DISTINCT {
                    id: elementId(n),
                    label: coalesce(n.name, n.canonical_name),
                    type: n.entity_type,
                    description: n.description
                }) AS nodes,
                collect(DISTINCT CASE WHEN r IS NULL THEN null ELSE {
                    from: elementId(startNode(r)),
                    to: elementId(endNode(r)),
                    type: type(r)
                } END) AS edgesRaw
                RETURN nodes, [x IN edgesRaw WHERE x IS NOT NULL] AS edges
                """;
        Map<String, Object> params = Map.of("kbId", kbId, "docId", documentId, "limit", limit);
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Result result = tx.run(cypher, params);
                if (!result.hasNext()) return Map.of("nodes", List.of(), "edges", List.of());
                Record r = result.next();
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("nodes", r.get("nodes").asList(Value::asMap));
                out.put("edges", r.get("edges").asList(Value::asMap));
                return out;
            });
        } catch (Exception e) {
            log.warn("[Neo4jRepo] findDocumentSubgraph 失败: {}", e.getMessage());
            return Map.of("nodes", List.of(), "edges", List.of());
        }
    }

    @Override
    public String getSchema() {
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                // 简单返回节点/关系计数作为连通性证明
                Result r = tx.run("MATCH (n) RETURN count(n) AS nodes");
                long nodes = r.single().get("nodes").asLong();
                return "OK (nodes=" + nodes + ")";
            });
        }
    }


    /**
     * Leiden 社区检测（依赖 GDS）。
     *
     * <p>★ 用 cypher projection 而非 graph projection（无需预先建命名图），
     * 步骤：project → leiden.write（写 communityId 属性）→ drop。
     *
     * <p>★ 降级：若 leiden 不可用（GDS 版本低），尝试 louvain；都失败返回 -1。
     * GDS 未安装时异常特征明确（procedure not found），上层据此跳过 global 模式。
     */
    @Override
    public int detectCommunities(String kbId, int minCommunitySize) {
        String graphName = "kg_" + sanitizeGraphName(kbId);
        // 1. cypher projection（节点=该 KB 的 Entity，边=同 KB 的 Entity 间关系）
        String project = """
                CALL gds.graph.project.cypher(
                    $graphName,
                    'MATCH (n:Entity {kb_id: $kbId}) RETURN id(n) AS id',
                    'MATCH (n:Entity {kb_id: $kbId})-[r]-(m:Entity {kb_id: $kbId}) RETURN id(n) AS source, id(m) AS target'
                )
                """;
        // 2. Leiden 写回 communityId 属性到节点（includeIntermediateCommunities=false 简化）
        String leiden = """
                CALL gds.leiden.write($graphName, {
                    writeProperty: 'community_id',
                    includeIntermediateCommunities: false,
                    minCommunitySize: $minSize
                })
                YIELD communityCount
                RETURN communityCount
                """;
        String louvain = """
                CALL gds.louvain.write($graphName, {
                    writeProperty: 'community_id',
                    minCommunitySize: $minSize
                })
                YIELD communityCount
                RETURN communityCount
                """;
        String drop = "CALL gds.graph.drop($graphName, false)";

        Map<String, Object> projectParams = Map.of("graphName", graphName, "kbId", kbId);
        Map<String, Object> algoParams = Map.of("graphName", graphName, "minSize", minCommunitySize);
        Map<String, Object> dropParams = Map.of("graphName", graphName);

        try (Session session = driver.session()) {
            // 先 drop 残留（幂等，不存在不报错）
            try {
                session.run(drop, dropParams).consume();
            } catch (Exception ignore) { /* 图不存在时忽略 */ }

            // project
            session.run(project, projectParams).consume();

            // leiden（失败降级 louvain）
            try {
                return session.executeWrite(tx -> {
                    Result r = tx.run(leiden, algoParams);
                    if (!r.hasNext()) return -1;
                    return r.next().get("communityCount").asInt();
                });
            } catch (Exception leidenEx) {
                log.warn("[Neo4jRepo] gds.leiden 不可用，降级 louvain: {}", leidenEx.getMessage());
                try {
                    return session.executeWrite(tx -> {
                        Result r = tx.run(louvain, algoParams);
                        if (!r.hasNext()) return -1;
                        return r.next().get("communityCount").asInt();
                    });
                } catch (Exception louvainEx) {
                    log.error("[Neo4jRepo] gds.louvain 也失败 kbId={}: {}", kbId, louvainEx.getMessage());
                    return -1;
                }
            }
        } catch (Exception e) {
            log.error("[Neo4jRepo] detectCommunities 整体失败 kbId={}（GDS 可能未安装）: {}",
                    kbId, e.getMessage());
            return -1;
        } finally {
            // 无论成功失败，清理内存图
            try (Session session = driver.session()) {
                session.run(drop, dropParams).consume();
            } catch (Exception ignore) { /* ignore */ }
        }
    }

    /** 列出某 KB 所有社区及其成员实体（供 LLM 生成社区摘要用） */
    @Override
    public List<Map<String, Object>> listCommunities(String kbId) {
        String cypher = """
                MATCH (n:Entity {kb_id: $kbId})
                WHERE n.community_id IS NOT NULL
                WITH n.community_id AS cid, collect({
                    name: n.canonical_name,
                    type: n.entity_type,
                    description: n.description
                }) AS entities
                RETURN cid AS communityId, entities
                ORDER BY size(entities) DESC
                """;
        Map<String, Object> params = Map.of("kbId", kbId);
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Result r = tx.run(cypher, params);
                List<Map<String, Object>> out = new ArrayList<>();
                while (r.hasNext()) {
                    Record rec = r.next();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("communityId", rec.get("communityId").asInt());
                    row.put("entities", rec.get("entities").asList(Value::asMap));
                    out.add(row);
                }
                return out;
            });
        } catch (Exception e) {
            log.warn("[Neo4jRepo] listCommunities 失败 kbId={}: {}", kbId, e.getMessage());
            return List.of();
        }
    }

    /** 写回社区摘要到 Entity 节点的 community_summary 属性 */
    @Override
    public int writeCommunitySummaries(String kbId, Map<Integer, String> summaries) {
        if (summaries == null || summaries.isEmpty()) return 0;
        List<Map<String, Object>> rows = new ArrayList<>(summaries.size());
        summaries.forEach((cid, summary) -> {
            Map<String, Object> row = new HashMap<>();
            row.put("cid", cid);
            row.put("summary", summary);
            rows.add(row);
        });
        String cypher = """
                UNWIND $rows AS r
                MATCH (n:Entity {kb_id: $kbId})
                WHERE n.community_id = r.cid
                SET n.community_summary = r.summary
                """;
        Map<String, Object> params = new HashMap<>();
        params.put("kbId", kbId);
        params.put("rows", rows);
        try (Session session = driver.session()) {
            return session.executeWrite(tx -> {
                tx.run(cypher, params).consume();
                return rows.size();
            });
        } catch (Exception e) {
            log.warn("[Neo4jRepo] writeCommunitySummaries 失败 kbId={}: {}", kbId, e.getMessage());
            return 0;
        }
    }

    /**
     * global 模式：召回与 query 实体相关的社区摘要作为证据。
     *
     * <p>策略：先按社区成员命中 query 实体数排序，再按社区规模补足，取 topK。
     */
    @Override
    public List<Map<String, Object>> findRelatedCommunities(String kbId, List<String> entityNames, int topK) {
        if (entityNames == null || entityNames.isEmpty()) return List.of();
        String cypher = """
                MATCH (n:Entity {kb_id: $kbId})
                WHERE n.community_id IS NOT NULL
                WITH n.community_id AS cid,
                     collect(DISTINCT n.canonical_name) AS members,
                     collect(DISTINCT n.community_summary)[0] AS summary
                WITH cid, members, summary,
                     size([m IN members WHERE m IN $names]) AS hitCount,
                     size(members) AS memberCount
                WHERE hitCount > 0 OR summary IS NOT NULL
                RETURN cid AS communityId, summary AS summary, hitCount AS hitEntities, memberCount
                ORDER BY hitCount DESC, memberCount DESC
                LIMIT $topK
                """;
        Map<String, Object> params = Map.of("kbId", kbId, "names", entityNames, "topK", topK);
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Result r = tx.run(cypher, params);
                List<Map<String, Object>> out = new ArrayList<>();
                while (r.hasNext()) {
                    Record rec = r.next();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("communityId", rec.get("communityId").asInt());
                    Value summary = rec.get("summary");
                    row.put("summary", summary.isNull() ? "" : summary.asString());
                    row.put("hitEntities", rec.get("hitEntities").asInt());
                    out.add(row);
                }
                return out;
            });
        } catch (Exception e) {
            log.warn("[Neo4jRepo] findRelatedCommunities 失败 kbId={}: {}", kbId, e.getMessage());
            return List.of();
        }
    }


    /** 把 kbId（业务字符串）转为合法的 GDS 图名（字母数字下划线） */
    private static String sanitizeGraphName(String kbId) {
        if (kbId == null) return "default";
        StringBuilder sb = new StringBuilder();
        for (char c : kbId.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '_') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        String s = sb.toString();
        // GDS 图名必须以字母开头
        if (s.isEmpty() || !Character.isLetter(s.charAt(0))) {
            s = "k" + s;
        }
        return s;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
