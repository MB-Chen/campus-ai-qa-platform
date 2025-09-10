// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.common.enums;

public enum QuestionStatusEnum {

    PENDING(1, "待生成"),
    RUNNING(2, "生成中"),
    COMPLETE(3, "索引完成");

    /**
     * 构造方法
     */
    private final int code;
    private final String msg;

    QuestionStatusEnum(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }


    /**
     * 获取状态码
     *
     * @author fzr
     * @return Long
     */
    public int getCode() {
        return this.code;
    }

    /**
     * 获取提示
     *
     * @author fzr
     * @return String
     */
    public String getMsg() {
        return this.msg;
    }
}
