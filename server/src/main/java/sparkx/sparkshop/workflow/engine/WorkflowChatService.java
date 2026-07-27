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

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sparkx.sparkshop.common.exception.BusinessException;
import sparkx.sparkshop.common.utils.AdminContextUtils;
import sparkx.sparkshop.workflow.entity.Workflow;
import sparkx.sparkshop.workflow.entity.WorkflowRuntime;
import sparkx.sparkshop.workflow.entity.WorkflowRuntimeContext;
import sparkx.sparkshop.workflow.mapper.WorkflowMapper;
import sparkx.sparkshop.workflow.mapper.WorkflowRuntimeContextMapper;
import sparkx.sparkshop.workflow.mapper.WorkflowRuntimeMapper;
import sparkx.sparkshop.workflow.validate.WorkflowChatValidate;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;

/**
 * 编排调试对话入口。建 runtime + start-node 上下文，触发 {@link FlowNodeParser} 异步执行。
 * <p>SSE 事件协议见 {@link WorkflowSseHelper}。
 */
@Slf4j
@Service
public class WorkflowChatService {

    /** title 最大长度 */
    private static final int TITLE_MAX_LEN = 255;

    @Resource
    private WorkflowMapper workflowMapper;

    @Resource
    private WorkflowRuntimeMapper workflowRuntimeMapper;

    @Resource
    private WorkflowRuntimeContextMapper workflowRuntimeContextMapper;

    @Resource
    private FlowNodeParser flowNodeParser;

    /**
     * 启动一次编排调试对话（SSE 流式）。
     *
     * @param req 入参（workflowId + conversationId + query）
     * @return SseEmitter
     */
    public SseEmitter chat(WorkflowChatValidate req) {
        Workflow wf = workflowMapper.selectById(req.getWorkflowId());
        if (wf == null) {
            throw new BusinessException("编排不存在");
        }
        if (StrUtil.isBlank(wf.getFlowData())) {
            throw new BusinessException("编排未设计流程，请先保存");
        }

        // 在请求线程里取当前用户（异步线程拿不到）
        String userId;
        try {
            userId = String.valueOf(AdminContextUtils.getAdminId());
        } catch (Exception e) {
            userId = "wf:operator";
        }
        String sessionId = StrUtil.isBlank(req.getConversationId())
                ? IdUtil.fastSimpleUUID() : req.getConversationId();

        // runtime 行
        WorkflowRuntime runtime = new WorkflowRuntime();
        String title = req.getQuery();
        if (title != null && title.length() > TITLE_MAX_LEN) {
            title = title.substring(0, TITLE_MAX_LEN);
        }
        runtime.setTitle(title);
        runtime.setUserId(userId);
        runtime.setWorkflowId(wf.getId());
        runtime.setCreatedAt(LocalDateTime.now());
        runtime.setUpdatedAt(LocalDateTime.now());
        workflowRuntimeMapper.insert(runtime);

        // start-node 上下文：seed sys.* 系统变量
        JSONObject outputData = JSONUtil.createObj();
        outputData.set("sys.question", req.getQuery());
        outputData.set("sys.time", LocalDateTime.now().toString());
        try {
            outputData.set("sys.ip", InetAddress.getLocalHost().getHostAddress());
        } catch (UnknownHostException e) {
            outputData.set("sys.ip", "127.0.0.1");
        }
        outputData.set("sys.workflowId", wf.getId());
        outputData.set("sys.sessionId", sessionId);

        WorkflowRuntimeContext startCtx = new WorkflowRuntimeContext();
        startCtx.setStep(1);
        startCtx.setNodeType("start-node");
        startCtx.setRuntimeId(runtime.getId());
        startCtx.setOutputData(outputData.toString());
        startCtx.setCreatedAt(LocalDateTime.now());
        workflowRuntimeContextMapper.insert(startCtx);

        // SSE：不超时，由客户端断开或流程完成驱动结束
        SseEmitter emitter = new SseEmitter(0L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(t -> log.warn("[WorkflowChat] SSE 异常: {}", t.getMessage()));

        // 触发异步执行
        flowNodeParser.setEmitter(emitter);
        flowNodeParser.setRuntimeId(runtime.getId());
        flowNodeParser.run(wf.getFlowData(), userId, sessionId);

        return emitter;
    }
}
