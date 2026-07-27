// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.workflow.enums;

/**
 * 编排节点类型。code 为 X6 的 shape 名，作为节点分发 key。
 */
public enum NodeTypeEnum {

    AGENT("agent-node", "智能体节点"),
    ANSWER("answer-node", "回复节点"),
    DATASET("dataset-node", "知识库节点"),
    LLM("llm-node", "大模型节点"),
    PURPOSE("purpose-node", "意图分类节点"),
    SWITCH("switch-node", "分支节点");

    private final String code;
    private final String msg;

    NodeTypeEnum(String code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public String getCode() {
        return this.code;
    }

    public String getMsg() {
        return this.msg;
    }
}
