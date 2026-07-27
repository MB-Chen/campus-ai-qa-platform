// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.infra.chat;

/**
 * 模型客户端调用异常（文档 5.10）。
 * 统一封装供应商调用失败（网络/限流/未授权/服务端错误/响应异常），
 * 携带 {@link ModelClientErrorType} 便于熔断器与日志区分。
 *
 * 该异常非业务异常（不沿用 RagException），由容错层内部捕获并触发降级，
 * 不直接抛到 Controller 层。
 */
public class ModelClientException extends RuntimeException {

    private final ModelClientErrorType errorType;

    public ModelClientException(ModelClientErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
    }

    public ModelClientException(ModelClientErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
    }

    public ModelClientErrorType getErrorType() { return errorType; }
}
