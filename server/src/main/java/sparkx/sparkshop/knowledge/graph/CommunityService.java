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
import org.springframework.scheduling.annotation.Async;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import sparkx.sparkshop.knowledge.entity.KgConfig;
import sparkx.sparkshop.knowledge.infra.LLMService;
import sparkx.sparkshop.knowledge.infra.chat.LlmChatRequest;
import sparkx.sparkshop.knowledge.mapper.KgConfigMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 社区检测 + 社区摘要生成服务（第四期）—— global 检索模式的前置数据准备。
 *
 * <p>★ 动态装配：本类始终注册。Neo4j 未配置时 {@link #triggerDetect} 通过
 * {@code graphRepository.getSchema()==null} 短路，避免空跑 Leiden/LLM。
 *
 * <p>★ 触发方式：手动（前端"运行社区检测"按钮调 controller）。
 * 不随文档抽取自动触发——社区检测应跑在「该 KB 全部文档抽取完成、图谱稳定」后，
 * 适合用户确认数据齐备后手动点。
 *
 * <p>★ 流程：
 * <ol>
 *   <li>{@link GraphRepository#detectCommunities} 跑 Leiden（GDS），写回 community_id 属性</li>
 *   <li>{@link GraphRepository#listCommunities} 列出所有社区成员</li>
 *   <li>对每个社区用抽取 LLM 生成摘要（输入成员的 name/type/description，输出一段概括）</li>
 *   <li>{@link GraphRepository#writeCommunitySummaries} 写回 community_summary 属性</li>
 * </ol>
 *
 * <p>★ 容错：GDS 未安装（detectCommunities 返回 -1）时整体降级——不生成摘要、不影响 local 模式检索。
 *
 * <p>★ Self 代理模式：{@code triggerDetect} 同步入口通过 self 调 {@code @Async} 方法，
 * 避免 this 调用绕过 AOP（对齐 GraphExtractionService 范式）。
 */
@Service
public class CommunityService {

    private static final Logger log = LoggerFactory.getLogger(CommunityService.class);

    /** 最小社区规模（小于此值的孤立节点归到 -1 社区，不单独成群） */
    private static final int DEFAULT_MIN_COMMUNITY_SIZE = 3;

    /** 每个社区摘要的成员上限（避免超长 prompt） */
    private static final int MAX_MEMBERS_PER_SUMMARY = 30;

    @Resource
    private GraphRepository graphRepository;

    @Resource
    private KgConfigMapper kgConfigMapper;

    @Resource
    private LLMService llmService;

    @Lazy
    @Resource
    private CommunityService self;

    /** 同步入口：触发异步社区检测 + 摘要生成 */
    public void triggerDetect(String kbId) {
        // Neo4j 未配置（Noop）时短路，避免空跑 Leiden/LLM
        if (kbId == null || kbId.isBlank() || graphRepository.getSchema() == null) return;
        self.detectAndSummarizeAsync(kbId);
    }

    /**
     * 异步执行：Leiden 社区检测 → 列社区 → LLM 生成摘要 → 写回。
     * 整体 try/catch 兜底，失败不向上抛。
     */
    @Async("ragTaskExecutor")
    public void detectAndSummarizeAsync(String kbId) {
        try {
            // 1. Leiden 社区检测
            int communityCount = graphRepository.detectCommunities(kbId, DEFAULT_MIN_COMMUNITY_SIZE);
            if (communityCount <= 0) {
                log.warn("[Community] 社区检测失败或 GDS 未安装 kbId={}（count={}），跳过摘要生成",
                        kbId, communityCount);
                return;
            }
            log.info("[Community] Leiden 社区检测完成 kbId={} communityCount={}", kbId, communityCount);

            // 2. 列社区成员
            List<Map<String, Object>> communities = graphRepository.listCommunities(kbId);
            if (communities.isEmpty()) {
                log.warn("[Community] 社区列表为空 kbId={}", kbId);
                return;
            }

            // 3. LLM 生成摘要
            KgConfig config = kgConfigMapper.selectById(1);
            Map<Integer, String> summaries = new LinkedHashMap<>();
            for (Map<String, Object> c : communities) {
                Integer cid = (Integer) c.get("communityId");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> members = (List<Map<String, Object>>) c.get("entities");
                if (members == null || members.isEmpty()) continue;

                try {
                    String summary = generateCommunitySummary(kbId, cid, members, config);
                    if (summary != null && !summary.isBlank()) {
                        summaries.put(cid, summary);
                    }
                } catch (Exception e) {
                    log.warn("[Community] 社区摘要生成失败 kbId={} cid={}: {}", kbId, cid, e.getMessage());
                }
            }

            // 4. 写回
            if (!summaries.isEmpty()) {
                int written = graphRepository.writeCommunitySummaries(kbId, summaries);
                log.info("[Community] 摘要写回完成 kbId={} communities={} written={}",
                        kbId, communities.size(), written);
            }
        } catch (Throwable t) {
            log.error("[Community] 异步任务整体失败 kbId={}: {}", kbId, t.getMessage(), t);
        }
    }

    /**
     * LLM 生成单个社区摘要：输入社区成员实体列表，输出一段概括性描述。
     * 失败返回 null（不写回，保留社区检测的 community_id 但无 summary）。
     */
    private String generateCommunitySummary(String kbId, int communityId,
                                             List<Map<String, Object>> members, KgConfig config) {
        // 控制 prompt 长度：成员数 > 上限时截断
        int limit = Math.min(members.size(), MAX_MEMBERS_PER_SUMMARY);
        StringBuilder entityList = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            Map<String, Object> m = members.get(i);
            String name = m.get("name") == null ? "" : m.get("name").toString();
            String type = m.get("type") == null ? "" : m.get("type").toString();
            String desc = m.get("description") == null ? "" : m.get("description").toString();
            entityList.append("- ").append(name);
            if (!type.isBlank()) entityList.append("（").append(type).append("）");
            if (!desc.isBlank()) entityList.append("：").append(desc);
            entityList.append("\n");
        }
        if (members.size() > limit) {
            entityList.append("...（及其它 ").append(members.size() - limit).append(" 个实体）\n");
        }

        String systemPrompt = """
                你是知识图谱社区摘要助手。给你一个社区的成员实体列表，请生成一段概括性摘要。
                要求：
                1. 用 2-3 句话概括这组实体的主题、相互关系、所属领域
                2. 不要罗列实体名，要提炼共性
                3. 直接输出摘要文本，不要 markdown，不要解释
                """;
        String userPrompt = "社区 #" + communityId + " 包含以下实体（共 " + members.size() + " 个）：\n" + entityList;

        LlmChatRequest req = LlmChatRequest.of(systemPrompt, userPrompt, 0.3);
        Integer modelId = config == null ? null : config.getExtractModelId();
        return llmService.chat(req, modelId);
    }
}
