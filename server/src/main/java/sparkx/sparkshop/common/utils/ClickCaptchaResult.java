// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.common.utils;

import lombok.Data;

import java.awt.image.BufferedImage;
import java.io.Serializable;
import java.util.List;

/**
 * 汉字点选验证码生成结果
 */
@Data
public class ClickCaptchaResult implements Serializable {

    /**
     * 生成的验证码图片
     */
    private BufferedImage image;

    /**
     * 所有汉字位置信息（含正确顺序，存入 Redis 供校验）
     */
    private List<CharPosition> charPositions;

    /**
     * 需要按顺序点击的目标汉字（按顺序，用于前端提示）
     */
    private List<String> targetChars;

    /**
     * 单个汉字的位置信息
     */
    @Data
    public static class CharPosition implements Serializable {
        /**
         * 汉字内容
         */
        private String text;

        /**
         * 汉字中心 X 坐标
         */
        private int x;

        /**
         * 汉字中心 Y 坐标
         */
        private int y;

        /**
         * 正确的点击顺序（0=第一个，-1=干扰字）
         */
        private int order;

        public CharPosition() {
        }

        public CharPosition(String text, int x, int y, int order) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.order = order;
        }
    }
}
