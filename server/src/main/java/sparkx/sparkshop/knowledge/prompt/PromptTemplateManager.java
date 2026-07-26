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

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.HashMap;
import java.util.Map;

/**
 * 提示词模板管理器 。
 * 启动时加载 classpath:prompts/*.yaml，按 key 渲染 {{占位符}}。
 *
 * 改进点 #11：支持 i18n，按 language 选模板（模板内部用 {{language}} 变量）。
 */
@Component
public class PromptTemplateManager {

    private final Map<String, String> templates = new HashMap<>();

    public PromptTemplateManager() throws Exception {
        var resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:prompts/*.yaml");
        for (Resource res : resources) {
            try (var is = res.getInputStream()) {
                Map<String, Object> yaml = new Yaml().load(is);
                if (yaml != null) {
                    flatten("", yaml, templates);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void flatten(String prefix, Map<String, Object> map, Map<String, String> out) {
        map.forEach((k, v) -> {
            String key = prefix.isEmpty() ? k : prefix + "." + k;
            if (v instanceof Map) {
                flatten(key, (Map<String, Object>) v, out);
            } else if (v != null) {
                out.put(key, v.toString());
            }
        });
    }

    /**
     * 渲染占位符 {{key}}。未提供值的占位符原样保留。
     */
    public String render(String templateKey, Map<String, String> vars) {
        String tpl = templates.get(templateKey);
        if (tpl == null) {
            throw new IllegalArgumentException("模板未找到: " + templateKey
                    + "（已加载模板: " + templates.keySet() + "）");
        }
        if (vars != null) {
            for (var e : vars.entrySet()) {
                tpl = tpl.replace("{{" + e.getKey() + "}}",
                        e.getValue() == null ? "" : e.getValue());
            }
        }
        return tpl;
    }

    /** 是否存在该模板 */
    public boolean exists(String templateKey) {
        return templates.containsKey(templateKey);
    }
}
