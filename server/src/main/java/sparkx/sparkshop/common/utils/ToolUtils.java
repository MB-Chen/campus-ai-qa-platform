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

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.SecureUtil;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * 工具方法：密码加密、验证码、时间
 */
public class ToolUtils {

    private ToolUtils() {
    }

    /**
     * 生成密码：md5(md5(password + salt) + salt)
     * 与前端/PHP 端 makePassword 保持同一算法即可校验通过
     */
    public static String makePassword(String password, String salt) {
        return SecureUtil.md5(SecureUtil.md5(password + salt) + salt);
    }

    /**
     * 校验密码
     */
    public static boolean verifyPassword(String dbPassword, String password, String salt) {
        return dbPassword != null && dbPassword.equals(makePassword(password, salt));
    }

    /**
     * 生成随机盐（simpleUUID，32 位无横线）
     */
    public static String makeSalt() {
        return IdUtil.simpleUUID();
    }

    /**
     * 当前时间
     */
    public static LocalDateTime nowDateTime() {
        return LocalDateTime.now();
    }


    /**
     * 常用汉字字库
     */
    private static final String CHAR_POOL = "的一是不了人我在有他这为之大来以个中上们到说国和地也子时道出会三要于下得可你年生";

    /**
     * 验证码画布尺寸
     */
    private static final int CANVAS_WIDTH = 300;
    private static final int CANVAS_HEIGHT = 180;

    /**
     * 汉字点击判定的容差范围（像素）
     */
    public static final int CLICK_TOLERANCE = 28;

    /**
     * 目标汉字数量
     */
    private static final int TARGET_COUNT = 4;

    /**
     * 干扰汉字数量
     */
    private static final int INTERFERENCE_COUNT = 2;

    /**
     * 生成汉字点选验证码图片
     * 后端生成图片并记录每个汉字的坐标和正确顺序
     *
     * @return ClickCaptchaResult 包含图片、汉字位置、目标提示
     */
    public static ClickCaptchaResult createClickCaptcha() {
        // 1. 从字库中随机选取目标汉字 + 干扰汉字
        List<String> pool = new ArrayList<>();
        for (int i = 0; i < CHAR_POOL.length(); i++) {
            pool.add(String.valueOf(CHAR_POOL.charAt(i)));
        }
        Collections.shuffle(pool);

        List<String> targetChars = new ArrayList<>(pool.subList(0, TARGET_COUNT));
        List<String> interferenceChars = new ArrayList<>(pool.subList(TARGET_COUNT, TARGET_COUNT + INTERFERENCE_COUNT));

        // 2. 构建所有汉字的位置信息
        List<ClickCaptchaResult.CharPosition> allPositions = new ArrayList<>();

        // 目标汉字，分配顺序 0/1/2/3
        for (int i = 0; i < targetChars.size(); i++) {
            allPositions.add(new ClickCaptchaResult.CharPosition(targetChars.get(i), 0, 0, i));
        }
        // 干扰汉字，order = -1
        for (String ch : interferenceChars) {
            allPositions.add(new ClickCaptchaResult.CharPosition(ch, 0, 0, -1));
        }

        // 打乱显示顺序
        Collections.shuffle(allPositions);

        // 3. 在画布有效区域内随机布点（边距防越界 + 碰撞检测防重叠）
        //    不再使用死网格，避免汉字永远落在固定几个点上
        int padding = 38;            // 边距：字号28 + 旋转±25°扩张余量，确保汉字完整在画布内
        double minDistance = 62.0;   // 汉字中心间最小间距，避免视觉重叠
        int maxAttempts = 300;       // 单个汉字最大尝试次数，防止极端情况下死循环

        List<ClickCaptchaResult.CharPosition> placed = new ArrayList<>();
        for (ClickCaptchaResult.CharPosition pos : allPositions) {
            boolean placedOk = false;
            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                int x = padding + RandomUtil.randomInt(CANVAS_WIDTH - 2 * padding + 1);
                int y = padding + RandomUtil.randomInt(CANVAS_HEIGHT - 2 * padding + 1);
                // 碰撞检测：与已放置的汉字保持最小间距
                boolean overlap = false;
                for (ClickCaptchaResult.CharPosition p : placed) {
                    double dx = x - p.getX();
                    double dy = y - p.getY();
                    if (Math.sqrt(dx * dx + dy * dy) < minDistance) {
                        overlap = true;
                        break;
                    }
                }
                if (!overlap) {
                    pos.setX(x);
                    pos.setY(y);
                    placed.add(pos);
                    placedOk = true;
                    break;
                }
            }
            // 极端兜底：尝试用尽仍未找到不重叠位置，放宽间距落点（保证流程不中断）
            if (!placedOk) {
                int x = padding + RandomUtil.randomInt(CANVAS_WIDTH - 2 * padding + 1);
                int y = padding + RandomUtil.randomInt(CANVAS_HEIGHT - 2 * padding + 1);
                pos.setX(x);
                pos.setY(y);
                placed.add(pos);
            }
        }

        // 4. 绘制图片
        BufferedImage image = drawCaptchaImage(allPositions);

        // 5. 组装结果
        ClickCaptchaResult result = new ClickCaptchaResult();
        result.setImage(image);
        result.setCharPositions(allPositions);
        result.setTargetChars(targetChars);

        return result;
    }

    /**
     * 绘制验证码图片
     */
    private static BufferedImage drawCaptchaImage(List<ClickCaptchaResult.CharPosition> positions) {
        BufferedImage image = new BufferedImage(CANVAS_WIDTH, CANVAS_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 背景（浅色渐变）
        GradientPaint bg = new GradientPaint(0, 0, new Color(248, 249, 250),
                CANVAS_WIDTH, CANVAS_HEIGHT, new Color(235, 238, 242));
        g.setPaint(bg);
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        // 干扰线
        for (int i = 0; i < 8; i++) {
            g.setColor(getRandomColor(80, 160));
            g.setStroke(new BasicStroke(1.2f));
            int x1 = RandomUtil.randomInt(CANVAS_WIDTH);
            int y1 = RandomUtil.randomInt(CANVAS_HEIGHT);
            int x2 = RandomUtil.randomInt(CANVAS_WIDTH);
            int y2 = RandomUtil.randomInt(CANVAS_HEIGHT);
            g.drawLine(x1, y1, x2, y2);
        }

        // 噪点
        for (int i = 0; i < 80; i++) {
            g.setColor(getRandomColor(150, 220));
            int x = RandomUtil.randomInt(CANVAS_WIDTH);
            int y = RandomUtil.randomInt(CANVAS_HEIGHT);
            g.fillOval(x, y, 2, 2);
        }

        // 绘制汉字
        Font baseFont = new Font("微软雅黑", Font.BOLD, 28);
        for (ClickCaptchaResult.CharPosition pos : positions) {
            Graphics2D g2 = (Graphics2D) g.create();

            // 随机倾斜角度（-25° ~ 25°）
            double angle = (RandomUtil.randomDouble() - 0.5) * 50;
            g2.rotate(Math.toRadians(angle), pos.getX(), pos.getY());

            // 随机颜色（深色，确保可见）
            g2.setColor(getRandomColor(20, 130));
            g2.setFont(baseFont);

            // 居中绘制
            FontMetrics fm = g2.getFontMetrics();
            int textWidth = fm.stringWidth(pos.getText());
            int textHeight = fm.getAscent();
            g2.drawString(pos.getText(), pos.getX() - textWidth / 2, pos.getY() + textHeight / 2);

            g2.dispose();
        }

        // 边框
        g.setColor(new Color(220, 223, 228));
        g.setStroke(new BasicStroke(1f));
        g.drawRect(0, 0, CANVAS_WIDTH - 1, CANVAS_HEIGHT - 1);

        g.dispose();
        return image;
    }

    /**
     * 生成随机颜色（指定范围）
     */
    private static Color getRandomColor(int min, int max) {
        int r = min + RandomUtil.randomInt(max - min);
        int g = min + RandomUtil.randomInt(max - min);
        int b = min + RandomUtil.randomInt(max - min);
        return new Color(r, g, b);
    }

    /**
     * 将 BufferedImage 转为 base64 字符串（不带 data: 前缀）
     */
    public static String imageToBase64(BufferedImage image) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            javax.imageio.ImageIO.write(image, "png", baos);
        } catch (Exception e) {
            throw new RuntimeException("图片转换失败", e);
        }
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }
}
