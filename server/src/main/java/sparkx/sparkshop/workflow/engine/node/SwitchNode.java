// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.workflow.engine.node;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sparkx.sparkshop.workflow.engine.IWorkflowNode;
import sparkx.sparkshop.workflow.engine.WorkflowRuntimeHelper;
import sparkx.sparkshop.workflow.entity.WorkflowRuntimeContext;
import sparkx.sparkshop.workflow.enums.NodeTypeEnum;
import sparkx.sparkshop.workflow.mapper.WorkflowRuntimeContextMapper;
import sparkx.sparkshop.workflow.vo.EdgeVo;
import sparkx.sparkshop.workflow.vo.NodeRuntimeVo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * 条件分支节点。按 ifBranch 条件组判断（AND/OR），命中走对应分支，全不命中走 else（最后一条边）。
 * 12 种操作符：为空/不为空/包含/不包含/等于/大于等于/小于/长度等于/长度大于等于/长度大于/长度小于等于/长度小于。
 */
@Slf4j
@Component
public class SwitchNode implements IWorkflowNode {

    @Autowired
    private WorkflowRuntimeContextMapper runtimeContextMapper;

    @Autowired
    private WorkflowRuntimeHelper runtimeHelper;

    @Setter
    public SseEmitter emitter;

    @Setter
    public CountDownLatch latch;

    @Override
    public List<EdgeVo> handle(NodeRuntimeVo runtimeVo) {
        JSONObject nodeObject = runtimeVo.getNodeInfo().getData();
        JSONArray ifBranch = nodeObject.getJSONArray("ifBranch");

        boolean match = false;
        WorkflowRuntimeContext context = null;
        int index = -1;

        for (int i = 0; i < ifBranch.size(); i++) {
            JSONObject nowNodeObject = ifBranch.getJSONObject(i);
            Integer judging = nowNodeObject.getInt("switch");
            JSONArray inputArr = nowNodeObject.getJSONArray("data");
            List<Boolean> matchArr = new ArrayList<>();

            for (int j = 0; j < inputArr.size(); j++) {
                JSONArray inputJsonArr = inputArr.getJSONObject(j).getJSONArray("input");
                // cond.input 现为 [{nodeId, field}]；兼容旧扁平 [nodeId, field]
                String inputIndex;
                String inputSourceId;
                if (inputJsonArr != null && !inputJsonArr.isEmpty()
                        && inputJsonArr.get(0) instanceof JSONObject firstObj) {
                    inputSourceId = firstObj.getStr("nodeId") == null ? "" : firstObj.getStr("nodeId");
                    inputIndex = firstObj.getStr("field") == null ? "" : firstObj.getStr("field");
                } else {
                    inputIndex = (inputJsonArr != null && inputJsonArr.size() > 1
                            && inputJsonArr.get(1) != null) ? inputJsonArr.get(1).toString() : "";
                    inputSourceId = (inputJsonArr != null && !inputJsonArr.isEmpty()
                            && inputJsonArr.get(0) != null) ? inputJsonArr.get(0).toString() : "";
                }

                context = runtimeHelper.getRuntimeContext(
                        runtimeVo.getRuntimeId(), runtimeVo.getSourceId(), inputSourceId);
                if (context == null) {
                    return null;
                }
                // 按分区读取变量值（兼容旧扁平字段）
                String inputData = runtimeHelper.readVar(context.getOutputData(), inputSourceId, inputIndex);
                int tips = inputArr.getJSONObject(j).getInt("tips");
                String value = inputArr.getJSONObject(j).getStr("value");

                boolean matchRes = switchTest(tips, inputData, value);
                matchArr.add(matchRes);
            }

            // AND
            if (judging != null && judging == 1) {
                if (matchArr.stream().allMatch(b -> b)) {
                    match = true;
                    index = i;
                }
            } else {
                // OR
                for (Boolean b : matchArr) {
                    if (b) {
                        match = true;
                        index = i;
                        break;
                    }
                }
            }
            if (match) {
                break;
            }
        }

        List<EdgeVo> nextEdgeVoList = runtimeVo.getEdges().get(runtimeVo.getNodeInfo().getId());
        String switchResult = match ? ifBranch.getJSONObject(index).getStr("type") : "else";
        if (!match) {
            index = nextEdgeVoList.size() - 1;
        }

        if (context != null) {
            // 继承上游 outputData，switch.result 写入本节点分区
            String outData = runtimeHelper.writeVar(context.getOutputData(),
                    runtimeVo.getNodeInfo().getId(), "switch.result", switchResult);
            WorkflowRuntimeContext contextEntity = new WorkflowRuntimeContext();
            contextEntity.setStep(context.getStep() + 1);
            contextEntity.setNodeType(NodeTypeEnum.SWITCH.getCode());
            contextEntity.setRuntimeId(runtimeVo.getRuntimeId());
            contextEntity.setOutputData(outData);
            contextEntity.setCell(runtimeVo.getNodeInfo().getId());
            contextEntity.setCreatedAt(LocalDateTime.now());
            runtimeContextMapper.insert(contextEntity);
        }

        List<EdgeVo> ret = new LinkedList<>();
        ret.add(nextEdgeVoList.get(index));
        latch.countDown();
        return ret;
    }

    /** 条件判断 */
    private boolean switchTest(int tips, String inputVal, String value) {
        if (inputVal == null) {
            inputVal = "";
        }
        return switch (tips) {
            case 1 -> StrUtil.isBlank(inputVal);
            case 2 -> StrUtil.isNotBlank(inputVal);
            case 3 -> inputVal.contains(value);
            case 4 -> !inputVal.contains(value);
            case 5 -> inputVal.equals(value);
            case 6 -> parseInt(inputVal) >= parseInt(value);
            case 7 -> parseInt(inputVal) < parseInt(value);
            case 8 -> inputVal.length() == parseInt(value);
            case 9 -> inputVal.length() >= parseInt(value);
            case 10 -> inputVal.length() > parseInt(value);
            case 11 -> inputVal.length() <= parseInt(value);
            case 12 -> inputVal.length() < parseInt(value);
            default -> false;
        };
    }

    private int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
