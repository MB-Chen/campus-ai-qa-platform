// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.common.core;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应结构，与前端约定：
 * <pre>
 * { "code": 0, "message": "success", "data": ... }
 * </pre>
 * code = 0 表示成功，其余表示失败；登录过期统一返回 912。
 */
@Data
public class AjaxResult<T> implements Serializable {

    private Integer code;
    private String message;
    private T data;

    /**
     * 默认无参构造（序列化用）
     */
    public AjaxResult() {
    }

    /**
     * 全参构造
     *
     * @param code    状态码，0 表示成功
     * @param message 提示信息
     * @param data    业务数据
     */
    public AjaxResult(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 成功（带数据）
     */
    public static <T> AjaxResult<T> success(T data) {
        return new AjaxResult<>(0, "success", data);
    }

    /**
     * 成功（无数据）
     */
    public static <T> AjaxResult<T> success() {
        return new AjaxResult<>(0, "success", null);
    }

    /**
     * 失败（默认 -1）
     */
    public static <T> AjaxResult<T> failed(String message) {
        return new AjaxResult<>(-1, message, null);
    }

    /**
     * 失败（自定义 code）
     */
    public static <T> AjaxResult<T> failed(Integer code, String message) {
        return new AjaxResult<>(code, message, null);
    }

    /**
     * 未登录 / token 失效，前端约定 code=912
     */
    public static <T> AjaxResult<T> unauthorized() {
        return new AjaxResult<>(912, "请登录", null);
    }

    /**
     * 无权限
     */
    public static <T> AjaxResult<T> forbidden() {
        return new AjaxResult<>(403, "无权限访问此接口", null);
    }
}
