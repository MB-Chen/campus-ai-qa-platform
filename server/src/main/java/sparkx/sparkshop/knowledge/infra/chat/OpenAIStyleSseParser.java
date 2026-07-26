// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.infra.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * OpenAI 风格 SSE 行解析器（文档 5.10）。
 *
 * 处理 {@code data: <json>} 行：
 *  - 识别 [DONE] 结束标记
 *  - 从 choices[0].delta.content（或 .message.content）提取内容 token
 *  - 从 choices[0].delta.reasoning_content 提取思考 token
 *  - 从 choices[0].finish_reason 判断是否完成
 */
public final class OpenAIStyleSseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OpenAIStyleSseParser() { }

    /**
     * 解析单行 SSE。
     *
     * @param line 已去除行尾换行的原始行
     * @return 解析结果，line 为空或注释返回 {@link ParsedEvent#empty()}
     */
    public static ParsedEvent parseLine(String line) {
        if (line == null) return ParsedEvent.empty();
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith(":")) return ParsedEvent.empty();

        // 去除 "data:" 前缀
        if (!trimmed.startsWith("data:")) return ParsedEvent.empty();
        String payload = trimmed.substring(5).trim();

        if ("[DONE]".equals(payload)) return ParsedEvent.done();

        try {
            JsonNode root = MAPPER.readTree(payload);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) return ParsedEvent.empty();

            JsonNode choice0 = choices.get(0);
            // delta（流式）或 message（非流式兼容）
            JsonNode msgNode = choice0.has("delta") ? choice0.path("delta") : choice0.path("message");

            String content = textOf(msgNode.path("content"));
            String reasoning = textOf(msgNode.path("reasoning_content"));
            String finishReason = textOf(choice0.path("finish_reason"));

            return new ParsedEvent(content, reasoning, finishReason,
                    "[DONE]".equalsIgnoreCase(finishReason) || "stop".equalsIgnoreCase(finishReason)
                            || "length".equalsIgnoreCase(finishReason));
        } catch (Exception e) {
            // JSON 解析失败按空处理，不中断整条流
            return ParsedEvent.empty();
        }
    }

    private static String textOf(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        return node.asText();
    }

    /**
     * 解析结果。
     *
     * @param content       内容 token（可能为 null）
     * @param reasoning     思考 token（可能为 null）
     * @param finishReason  结束原因（stop/length/null）
     * @param finished      是否已结束
     */
    public record ParsedEvent(String content, String reasoning, String finishReason, boolean finished) {
        public static ParsedEvent empty() { return new ParsedEvent(null, null, null, false); }
        public static ParsedEvent done() { return new ParsedEvent(null, null, "stop", true); }

        public boolean isEmpty() { return content == null && reasoning == null && !finished; }
    }
}
