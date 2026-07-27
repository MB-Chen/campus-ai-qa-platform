// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.prompt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sparkx.sparkshop.knowledge.intent.QueryIntent;

import java.util.List;
import java.util.Map;

/**
 * 提示词规划器（文档 5.14.2）—— 意图路由 + 场景路由 + 意图级模板覆盖。
 *
 * 三层选择（优先级从高到低）：
 *  1. 意图专用模板：CHITCHAT/FOLLOW_UP 等非检索意图各有专用 .st（人设/口吻/是否引用历史各不同），
 *     覆盖场景模板。借鉴 WeKnora intent_prompts.yaml：按意图类型给差异化 system prompt。
 *  2. 场景决定基模板：KB_ONLY/MCP_ONLY/MIXED/EMPTY 各对应一个 .st（按证据来源分，与意图正交）
 *  3. 意图级节点覆盖：单意图且节点配了 promptTemplate → 完全覆盖默认模板
 *
 * 由 {@code GenerateStage} 调用构建 system prompt，再拼接证据(<documents>/<tool-data>)+问题。
 */
@Component
public class PromptPlanner {

    private static final Logger log = LoggerFactory.getLogger(PromptPlanner.class);

    private final PromptTemplateLoader templateLoader;

    public PromptPlanner(PromptTemplateLoader templateLoader) {
        this.templateLoader = templateLoader;
    }

    /**
     * 构建系统提示词（带意图路由）。
     *
     * <p>优先级：意图专用模板（CHITCHAT/FOLLOW_UP）→ 意图级节点覆盖 → 场景默认。
     *
     * @param scene      提示词场景（按证据来源分）
     * @param intent     查询意图（IntentStage 判定，决定是否用意图专用模板）
     * @param intentTpls 意图级模板覆盖候选（节点配了 promptTemplate 时传）
     * @param kbContext  KB 上下文（已渲染）
     * @param mcpContext MCP 上下文（已渲染）
     * @return 系统提示词文本
     */
    public String buildSystemPrompt(PromptScene scene, QueryIntent intent, List<String> intentTpls,
                                    String kbContext, String mcpContext) {
        // 1. 意图专用模板：闲聊/追问等非检索意图各有差异化人设，覆盖场景模板
        String intentPath = intentTemplatePath(intent);
        if (intentPath != null) {
            return templateLoader.render(intentPath, Map.of());
        }

        // 2. 意图级节点覆盖：单意图且配了 promptTemplate → 完全覆盖
        if (intentTpls != null && intentTpls.size() == 1) {
            String override = intentTpls.get(0);
            if (override != null && !override.isBlank()) {
                return override;
            }
        }

        // 3. 场景默认模板
        String path = defaultTemplatePath(scene);
        if (path == null || path.isEmpty()) {
            path = "answer-chat-system.st";
        }
        return templateLoader.render(path, Map.of());
    }

    /** 向后兼容：无意图时走场景路由（等价于 intent=null，意图专用模板不生效） */
    public String buildSystemPrompt(PromptScene scene, List<String> intentTpls,
                                    String kbContext, String mcpContext) {
        return buildSystemPrompt(scene, null, intentTpls, kbContext, mcpContext);
    }

    /**
     * 意图专用模板路径。仅对需要差异化人设的非检索意图返回模板名，其余返回 null（走场景路由）。
     *
     * <p>这里只覆盖人设/口吻维度，不覆盖信息边界（KB 证据约束仍在场景模板里）。
     * CHITCHAT（闲聊）/ FOLLOW_UP（追问）这两类高频且原 EMPTY 模板不够贴切的意图启用专用模板；
     * 其他意图（SUMMARIZE/IMAGE_ONLY/DOC_ONLY 等）暂沿用场景默认。
     */
    private String intentTemplatePath(QueryIntent intent) {
        if (intent == null) return null;
        return switch (intent) {
            case CHITCHAT -> "answer-intent-chitchat.st";
            case FOLLOW_UP -> "answer-intent-follow-up.st";
            default -> null;
        };
    }

    /** 场景 → 默认模板路径 */
    private String defaultTemplatePath(PromptScene scene) {
        return switch (scene) {
            case KB_ONLY -> "answer-chat-kb.st";
            case MCP_ONLY -> "answer-chat-mcp.st";
            case MIXED -> "answer-chat-mcp-kb-mixed.st";
            case EMPTY -> "answer-chat-system.st";
        };
    }

    /**
     * 渲染 KB 证据段（<documents> 容器），用 context-format.st 的 documents section。
     *
     * @param documentsEvidence 已渲染的逐条文档证据（含 <question>/<content>）
     */
    public String renderKbEvidence(String documentsEvidence) {
        if (documentsEvidence == null || documentsEvidence.isBlank()) return "";
        return templateLoader.renderSection("context-format.st", "documents",
                Map.of("documents", documentsEvidence));
    }

    /** 渲染会话摘要包装段（<conversation-summary>） */
    public String renderSummaryWrapper(String summary) {
        if (summary == null || summary.isBlank()) return "";
        return templateLoader.renderSection("context-format.st", "summary-wrapper",
                Map.of("summary", summary));
    }
}
