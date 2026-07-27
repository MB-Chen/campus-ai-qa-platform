// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.workflow.engine;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import sparkx.sparkshop.common.exception.BusinessException;

/**
 * 按 shape 名（kebab-case）解析节点 bean。
 * <p>llm-node → LlmNode（包 {@code sparkx.sparkshop.workflow.engine.node}），从 Spring 容器取。
 */
@Slf4j
@Component
public class NodeProvider {

    @Autowired
    private ApplicationContext context;

    /**
     * 根据节点 shape 名取节点处理器。
     *
     * @param nodeName shape 名，如 "llm-node"
     */
    public IWorkflowNode handle(String nodeName) {
        try {
            String className = "sparkx.sparkshop.workflow.engine.node." + kebabToPascalCase(nodeName);
            Class<?> clazz = Class.forName(className);
            return (IWorkflowNode) context.getBean(clazz);
        } catch (ClassNotFoundException e) {
            log.error("节点处理类未找到: {}", e.getMessage());
        }
        throw new BusinessException("未支持的节点类型: " + nodeName);
    }

    private String kebabToPascalCase(String kebab) {
        if (StrUtil.isBlank(kebab)) {
            return "";
        }
        String[] parts = kebab.split("-");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(StrUtil.upperFirst(part));
            }
        }
        return sb.toString();
    }
}
