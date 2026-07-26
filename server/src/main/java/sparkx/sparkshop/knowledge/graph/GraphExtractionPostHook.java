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

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import sparkx.sparkshop.knowledge.entity.KgConfig;
import sparkx.sparkshop.knowledge.entity.KnowledgeDocument;
import sparkx.sparkshop.knowledge.ingest.IngestPostContext;
import sparkx.sparkshop.knowledge.ingest.IngestPostHook;
import sparkx.sparkshop.knowledge.mapper.KgConfigMapper;
import sparkx.sparkshop.knowledge.mapper.KnowledgeDocumentMapper;

/**
 * 知识图谱抽取入库后钩子（替代旧版 DocumentIngestService.ingest() 里硬编码的 KG 触发逻辑）。
 *
 * <p>★ 动态装配：本类始终注册（{@code @Component}）。Neo4j 未配置时下游
 * {@code GraphExtractionService.isKgAvailable()} 会短路，不会空跑抽取。
 *
 * <p>★ order=50：先于未来的其它增强（问答对预生成等）执行。
 * KG 抽取内部用 @Async，触发即返回，不阻塞 ingest 主流程。
 *
 * <p>★ 双闸校验：{@link #shouldRun} 检查全局运行期开关 + 文档级开关。
 * 图谱抽取按文档维度控制（而非知识库维度）：文档上传/重新向量化入库完成后，
 * 仅当该文档的 kg_enabled=1 且全局开关开启时才触发抽取。
 */
@Component
@Order(50)
public class GraphExtractionPostHook implements IngestPostHook {

    private static final Logger log = LoggerFactory.getLogger(GraphExtractionPostHook.class);

    @Resource
    private GraphExtractionService graphExtractionService;

    @Resource
    private KgConfigMapper kgConfigMapper;

    @Resource
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    /**
     * 双闸校验：全局运行期开关 + 文档级开关。
     * Neo4j 未配置时下游抽取服务自行短路，此处无需判断基础设施层。
     */
    @Override
    public boolean shouldRun(IngestPostContext ctx) {
        if (ctx == null || ctx.docId() == null || ctx.docId().isBlank()) return false;
        try {
            // 1. 全局开关
            KgConfig config = kgConfigMapper.selectById(1);
            if (config == null || config.getEnabled() == null || config.getEnabled() != 1) return false;
            // 2. 文档级开关（kg_enabled: 1=启用 2=禁用，空值视为禁用）
            KnowledgeDocument doc = knowledgeDocumentMapper.selectById(ctx.docId());
            return doc != null && doc.getKgEnabled() != null && doc.getKgEnabled() == 1;
        } catch (Exception e) {
            log.debug("[KgHook] shouldRun 检查异常，视为未启用: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 触发异步抽取（best-effort，失败只 warn 不阻断）。
     * 异步由 GraphExtractionService.triggerExtraction 内部 @Async 保证。
     */
    @Override
    public void afterIngest(IngestPostContext ctx) {
        graphExtractionService.triggerExtraction(ctx.docId(), ctx.kbId());
        log.info("[KgHook] 已触发图谱抽取 docId={} kbId={}", ctx.docId(), ctx.kbId());
    }

    @Override
    public int getOrder() { return 50; }
}
