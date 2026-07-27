// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.workflow.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 节点运行时上下文，在 DAG 执行时传递给每个节点。
 */
@Data
public class NodeRuntimeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 运行时 id */
    private long runtimeId;

    /** 来源节点 id */
    private String sourceId;

    /** 用户 id */
    private String userId;

    /** 边信息：source cell → 出边列表 */
    private Map<String, List<EdgeVo>> edges;

    /** 全部节点：cell id → NodeVo */
    private Map<String, NodeVo> nodes;

    /** 当前节点信息 */
    private NodeVo nodeInfo;

    /** 会话 id（记忆隔离） */
    private String sessionId;
}
