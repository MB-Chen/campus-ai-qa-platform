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
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sparkx.sparkshop.knowledge.config.RagProperties;

/**
 * 提示词模板加载器（文档 5.14.7）—— 新增 .st 体系，与现有 {@code PromptTemplateManager}(YAML) 并存。
 *
 * 能力：
 *  - .st 文件 + {占位符} 替换
 *  - section 复用：单文件用 {@code --- section: name ---} 分段，{@link #renderSection} 渲染某段
 *  - i18n：按 {@code app.rag.language} 加载 {@code prompt/{language}/{path}}
 *  - 缓存整个文件 + 分段内容，避免重复 IO
 *
 * 与 YAML 体系分工：本加载器服务场景化提示词（KB/MCP/MIXED，内容长、需 section 复用），
 * 现有 PromptTemplateManager 继续服务 rewrite/intent/fallback（简短 YAML）。
 */
@Component
public class PromptTemplateLoader {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplateLoader.class);
    /** section 分隔符：{@code --- section: name ---} */
    private static final Pattern SECTION = Pattern.compile("^---\\s*section:\\s*(\\S+)\\s*---\\s*$");

    private final ResourceLoader resourceLoader;
    private final String language;

    /** 整文件缓存：path → 原文 */
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    /** 分段缓存：path → (sectionName → 原文) */
    private final Map<String, Map<String, String>> sectionCache = new ConcurrentHashMap<>();

    public PromptTemplateLoader(ResourceLoader resourceLoader, RagProperties props) {
        this.resourceLoader = resourceLoader;
        // 语言从 app.rag.language 取（默认 zh），用于加载 prompt/{language}/
        this.language = (props.getLanguage() != null && !props.getLanguage().isBlank()) ? props.getLanguage() : "zh";
    }

    /** 渲染整个模板文件，{占位符} 替换 */
    public String render(String path, Map<String, String> slots) {
        String tpl = cache.computeIfAbsent(path, this::loadFile);
        return fillSlots(tpl, slots);
    }

    /** 渲染某一段（section），复用单文件多片段 */
    public String renderSection(String path, String section, Map<String, String> slots) {
        Map<String, String> sections = sectionCache.computeIfAbsent(path, this::parseSections);
        String tpl = sections.get(section);
        return tpl == null ? "" : fillSlots(tpl, slots);
    }

    /** 是否存在某段（用于判断意图级模板覆盖等） */
    public boolean hasSection(String path, String section) {
        Map<String, String> sections = sectionCache.computeIfAbsent(path, this::parseSections);
        return sections.containsKey(section);
    }


    /** 从 classpath:prompt/{language}/{path} 加载文件 */
    private String loadFile(String path) {
        String classpathPath = "classpath:prompt/" + language + "/" + path;
        try {
            Resource resource = resourceLoader.getResource(classpathPath);
            if (!resource.exists()) {
                // 回退到默认 zh 目录
                resource = resourceLoader.getResource("classpath:prompt/zh/" + path);
            }
            if (!resource.exists()) {
                log.warn("[PromptLoader] 模板不存在: {}", classpathPath);
                return "";
            }
            try (InputStream is = resource.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("[PromptLoader] 加载模板失败 {}: {}", classpathPath, e.getMessage());
            return "";
        }
    }

    /** 解析单文件为 section 映射（无 section 分隔符则整体作为 default 段） */
    private Map<String, String> parseSections(String path) {
        Map<String, String> sections = new LinkedHashMap<>();
        String content = cache.computeIfAbsent(path, this::loadFile);
        String current = "default";
        StringBuilder buf = new StringBuilder();
        for (String line : content.split("\n", -1)) {
            Matcher m = SECTION.matcher(line.trim());
            if (m.matches()) {
                if (!buf.isEmpty()) {
                    sections.put(current, buf.toString().trim());
                    buf = new StringBuilder();
                }
                current = m.group(1);
            } else {
                buf.append(line).append("\n");
            }
        }
        if (!buf.isEmpty()) sections.put(current, buf.toString().trim());
        return sections;
    }

    /** {占位符} 替换；null 值转空串；3+ 连续换行压成 2 个 */
    private String fillSlots(String tpl, Map<String, String> slots) {
        if (tpl == null || tpl.isEmpty()) return "";
        String result = tpl;
        if (slots != null) {
            for (Map.Entry<String, String> e : slots.entrySet()) {
                result = result.replace("{" + e.getKey() + "}",
                        e.getValue() == null ? "" : e.getValue());
            }
        }
        return result.replaceAll("\n{3,}", "\n\n").trim();
    }
}
