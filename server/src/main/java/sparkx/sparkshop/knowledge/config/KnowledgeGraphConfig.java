// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sparkx.sparkshop.knowledge.graph.GraphRepository;
import sparkx.sparkshop.knowledge.graph.Neo4jGraphRepository;
import sparkx.sparkshop.knowledge.graph.NoopGraphRepository;
import sparkx.sparkshop.knowledge.service.IExtServiceConfigService;

/**
 * 知识图谱模块 Bean 装配。
 *
 * <p>★ 动态装配（对齐 WeKnora nil-driver 模式）：本类<b>始终装配</b>，不再依赖启动时 yml 门控。
 * 装配时查 {@code ext_service_config} 表：
 * <ul>
 *   <li>已配置 Neo4j → 建 {@link Driver}（内联在 {@code graphRepository} 内）+ {@link Neo4jGraphRepository}，启动时验证连通性</li>
 *   <li>未配置 → 返回 {@link NoopGraphRepository}（写操作返回 0、读操作返回空集合），不阻断启动</li>
 * </ul>
 * 真正的启停由 {@code kg_config.enabled}（页面运行期开关）控制，无需改 yml 重启。
 *
 * <p>★ 实体向量在 PostgreSQL（{@code kg_entity} 表 + pgvector，对齐 WeKnora 架构），
 * 图谱关系在 Neo4j（{@link Neo4jGraphRepository} 用 driver 原生 Session API 执行 Cypher）。
 *
 * <p>Neo4j 连接信息从 {@code ext_service_config} 表读取（category=neo4j_self），
 * 通过 {@link IExtServiceConfigService#getNeo4jConnection()} 获取。
 *
 * <p>⚠️ 不在此声明 {@code ObjectMapper} Bean（项目红线：全局 ObjectMapper 由 SB 自动装配，
 * 专用 mapper 用 {@code new ObjectMapper()} 字段）。
 *
 * <p>依赖来源：{@code langchain4j-community-neo4j}（官方文档：https://docs.langchain4j.dev/integrations/embedding-stores/neo4j）
 * 传递 {@code org.neo4j.driver:neo4j-java-driver}，
 * 这里直接用 driver 原生 API（{@link GraphDatabase#driver} + {@code Session.executeWrite/executeRead}），
 * 不依赖 {@code Neo4jGraph} 的封装方法（避免跨小版本 API 差异），稳定性最强。
 * 若后续需要 {@code Neo4jText2CypherRetriever} 或 {@code Neo4jEmbeddingStore}，
 * 可直接从同依赖引入，无需改 pom。
 */
@Configuration
public class KnowledgeGraphConfig {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphConfig.class);

    /**
     * 图谱仓库：封装实体/关系 MERGE、子图查询、schema 查询等 Cypher 操作。
     *
     * <p>★ 装配策略（动态）：
     * <ul>
     *   <li>{@code ext_service_config} 未配置 Neo4j → 返回 {@link NoopGraphRepository}（下游通过
     *       {@code getSchema()==null} 识别未配置状态，自行短路避免空跑 LLM）</li>
     *   <li>已配置 → 建 driver（验证连通性，失败抛异常阻止装配，对齐 WeKnora「显式配了就必须连通」）
     *       + {@link Neo4jGraphRepository}（driver 生命周期由 repo 的 @PreDestroy 回收）</li>
     * </ul>
     */
    @Bean
    public GraphRepository graphRepository(IExtServiceConfigService extServiceConfigService) {
        String[] conn = extServiceConfigService.getNeo4jConnection();
        if (conn == null) {
            log.info("[KnowledgeGraph] Neo4j 未配置（ext_service_config 表无 neo4j_self 启用项），"
                    + "装配 NoopGraphRepository，KG 功能就绪待配置后重启生效。运行期开关 kg_config.enabled 仍可正常启停。");
            return new NoopGraphRepository();
        }
        String uri = conn[0];
        String username = conn[1];
        String password = conn[2];
        Driver driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password));
        // 启动时验证连通性（失败抛出 → bean 装配失败 → 启动报错，提醒用户修正 Neo4j 配置）
        try {
            driver.verifyConnectivity();
            log.info("[KnowledgeGraph] Neo4j 连接成功: {}", uri);
        } catch (Exception e) {
            log.error("[KnowledgeGraph] Neo4j 连接失败 {}: {}", uri, e.getMessage());
            throw e;
        }
        Neo4jGraphRepository repo = new Neo4jGraphRepository(driver);
        // 启动时幂等建唯一约束（Entity: (kb_id, canonical_name)），保证 MERGE 幂等
        try {
            repo.ensureConstraints();
            log.info("[KnowledgeGraph] Neo4j 约束初始化完成");
        } catch (Exception e) {
            log.warn("[KnowledgeGraph] Neo4j 约束初始化失败（继续运行，可能影响 MERGE 幂等性）: {}", e.getMessage());
        }
        return repo;
    }
}
