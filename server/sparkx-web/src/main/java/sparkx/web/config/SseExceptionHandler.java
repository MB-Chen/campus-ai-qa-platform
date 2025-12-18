// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: SparkX Team
// +----------------------------------------------------------------------

package sparkx.web.config;

import dev.langchain4j.exception.ModelNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sparkx.common.constant.SparkXConstant;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE流式异常处理器
 * 专门处理SSE流式传输过程中发生的异常，确保错误信息能正确传递给前端
 */
@Slf4j
@ControllerAdvice
public class SseExceptionHandler {

    // 存储活跃的SSE连接，用于在全局异常处理中发送错误信息
    public static final ConcurrentHashMap<String, SseEmitter> SSE_EMITTERS = new ConcurrentHashMap<>();

    /**
     * 处理模型未找到异常
     * 当指定的模型不存在或无法访问时，通过SSE发送友好的错误提示
     *
     * @param e ModelNotFoundException
     * @return SseEmitter
     */
    @ExceptionHandler(ModelNotFoundException.class)
    public void handleModelNotFoundException(ModelNotFoundException e) {
        log.error("模型未找到异常: {}", e.getMessage());
        
        // 提取友好的错误信息
        String friendlyMessage = extractFriendlyErrorMessage(e.getMessage());
        
        // 向所有活跃的SSE连接广播错误信息
        broadcastSseError(friendlyMessage);
    }

    /**
     * 处理通用异常
     *
     * @param e Exception
     */
    @ExceptionHandler(Exception.class)
    public void handleGeneralException(Exception e) {
        log.error("SSE流处理过程中发生异常: ", e);
        
        // 向所有活跃的SSE连接广播错误信息
        broadcastSseError("系统繁忙，请稍后再试");
    }

    /**
     * 从原始错误信息中提取友好的错误描述
     *
     * @param errorMessage 原始错误信息
     * @return 友好的错误描述
     */
    private String extractFriendlyErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.isEmpty()) {
            return "请求处理失败";
        }

        // 针对模型未找到的情况提供友好提示
        if (errorMessage.contains("InvalidEndpointOrModel.NotFound")) {
            return "您选择的AI模型暂时不可用，请联系管理员检查模型配置";
        }

        // 默认友好提示
        return "请求处理遇到了一些问题，请稍后再试";
    }

    /**
     * 向所有活跃的SSE连接广播错误信息
     *
     * @param errorMessage 错误信息
     */
    private void broadcastSseError(String errorMessage) {
        SSE_EMITTERS.forEach((key, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name(SparkXConstant.SSEEventName.ERROR)
                        .data(errorMessage));
                emitter.complete();
            } catch (IOException ioException) {
                log.warn("向客户端发送SSE错误信息失败: ", ioException);
                emitter.complete();
            } catch (IllegalStateException illegalStateException) {
                log.warn("SSE连接已关闭，无法发送错误信息: ", illegalStateException);
            }
        });
        
        // 清空连接池
        SSE_EMITTERS.clear();
    }
}