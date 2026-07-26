// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.common.config;

import sparkx.sparkshop.common.core.AjaxResult;
import sparkx.sparkshop.common.exception.BusinessException;
import sparkx.sparkshop.knowledge.common.exception.RagException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理
 */
@Slf4j
@RestControllerAdvice
public class ExceptionHandlerConfig {

    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public AjaxResult<Object> handleBusiness(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return AjaxResult.failed(e.getCode(), e.getMessage());
    }

    /**
     * RAG 知识库异常（移植自 sparkxV2 RagException）
     */
    @ExceptionHandler(RagException.class)
    public AjaxResult<Object> handleRag(RagException e) {
        log.warn("RAG 异常: {}", e.getMessage());
        return AjaxResult.failed(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验异常（@RequestBody @Valid）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public AjaxResult<Object> handleValid(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : "参数错误";
        return AjaxResult.failed(400, msg);
    }

    /**
     * 参数校验异常（表单绑定）
     */
    @ExceptionHandler(BindException.class)
    public AjaxResult<Object> handleBind(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : "参数错误";
        return AjaxResult.failed(400, msg);
    }

    /**
     * 兜底异常
     */
    @ExceptionHandler(Exception.class)
    public AjaxResult<Object> handleException(Exception e) {
        log.error("系统异常", e);
        return AjaxResult.failed("服务器繁忙，请稍后再试");
    }
}
