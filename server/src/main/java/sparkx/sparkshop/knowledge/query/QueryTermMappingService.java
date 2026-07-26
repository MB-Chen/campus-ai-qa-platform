// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.query;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 术语归一化服务（文档 5.3.1）—— 确定性规则替换。
 *
 * 按 priority 替换同义词（"社保"→"社会保险"），改写前后都作为兜底。
 * 纯规则不调 LLM，零延迟零成本。
 *
 * 内置常见企业术语映射，可通过 application.yml 扩展（后续增强）。
 */
@Component
public class QueryTermMappingService {

    /** 术语映射表（原文 → 归一化），LinkedHashMap 保序 */
    private final Map<Pattern, String> mappings = new LinkedHashMap<>();

    public QueryTermMappingService() {
        // 内置常见企业术语归一化
        register("社保", "社会保险");
        register("公积金", "住房公积金");
        register("个税", "个人所得税");
        register("OA", "办公自动化系统");
        register("HR", "人力资源系统");
        register("CRM", "客户关系管理系统");
        register("ERP", "企业资源计划系统");
    }

    /** 注册术语映射 */
    public void register(String from, String to) {
        mappings.put(Pattern.compile(Pattern.quote(from)), to);
    }

    /** 归一化：按映射表替换同义词 */
    public String normalize(String question) {
        if (question == null || question.isBlank()) return question;
        String result = question;
        for (Map.Entry<Pattern, String> entry : mappings.entrySet()) {
            result = entry.getKey().matcher(result).replaceAll(entry.getValue());
        }
        return result;
    }
}
