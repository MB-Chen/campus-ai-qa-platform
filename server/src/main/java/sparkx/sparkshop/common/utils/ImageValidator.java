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

import cn.hutool.core.util.StrUtil;
import org.springframework.web.multipart.MultipartFile;
import sparkx.sparkshop.common.exception.BusinessException;

import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

/**
 * 图片上传安全校验：扩展名白名单 + ContentType + 文件头魔数 + 大小。
 * <p>
 * 真正的防线是文件头魔数（magic number）：防止把 a.exe 改名 a.png 上传，
 * 即使 ContentType 被伪造也能拦截。
 */
public class ImageValidator {

    /** 允许的扩展名（小写，无点） */
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");

    /** 默认大小上限：5MB */
    private static final long MAX_SIZE = 5L * 1024 * 1024;

    /**
     * 校验上传图片，通过则返回标准化后的小写扩展名（无点）；不通过抛 BusinessException。
     *
     * @param file         上传文件
     * @param maxBytes     大小上限（字节），<=0 用默认 5MB
     * @return 标准化的小写扩展名（如 png）
     */
    public static String validate(MultipartFile file, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择图片");
        }
        long limit = maxBytes > 0 ? maxBytes : MAX_SIZE;
        if (file.getSize() > limit) {
            throw new BusinessException("图片不能超过 " + (limit / 1024 / 1024) + "MB");
        }

        // 1. 扩展名白名单
        String filename = file.getOriginalFilename();
        String ext = "png";
        if (StrUtil.isNotBlank(filename) && filename.contains(".")) {
            ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        }
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException("不支持的图片格式（仅支持 " + String.join("、", ALLOWED_EXT) + "）");
        }

        // 2. ContentType 必须是 image/*
        String contentType = file.getContentType();
        if (StrUtil.isBlank(contentType) || !contentType.startsWith("image/")) {
            throw new BusinessException("文件类型不合法");
        }

        // 3. 文件头魔数校验
        try (InputStream is = file.getInputStream()) {
            byte[] head = new byte[16];
            int read = is.read(head);
            if (!matchMagic(head, Math.max(read, 0), ext)) {
                throw new BusinessException("文件已损坏或不是有效的图片（内容与格式不符）");
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException("图片校验失败：" + e.getMessage());
        }

        return ext;
    }

    /** 重载：使用默认 5MB 上限 */
    public static String validate(MultipartFile file) {
        return validate(file, MAX_SIZE);
    }

    /**
     * 比对文件头魔数。
     *
     * @param head 文件前若干字节
     * @param len  实际读取到的字节数
     * @param ext  小写扩展名
     */
    private static boolean matchMagic(byte[] head, int len, String ext) {
        switch (ext) {
            case "jpg":
            case "jpeg":
                // FF D8 FF
                return len >= 3 && (head[0] & 0xff) == 0xff && (head[1] & 0xff) == 0xd8 && (head[2] & 0xff) == 0xff;
            case "png":
                // 89 50 4E 47 0D 0A 1A 0A
                return len >= 8
                        && (head[0] & 0xff) == 0x89 && (head[1] & 0xff) == 0x50
                        && (head[2] & 0xff) == 0x4e && (head[3] & 0xff) == 0x47
                        && (head[4] & 0xff) == 0x0d && (head[5] & 0xff) == 0x0a
                        && (head[6] & 0xff) == 0x1a && (head[7] & 0xff) == 0x0a;
            case "gif":
                // GIF87a / GIF89a
                if (len < 6) return false;
                return (head[0] & 0xff) == 0x47 && (head[1] & 0xff) == 0x49
                        && (head[2] & 0xff) == 0x46 && (head[3] & 0xff) == 0x38
                        && (((head[4] & 0xff) == 0x37 && (head[5] & 0xff) == 0x61)
                        || ((head[4] & 0xff) == 0x39 && (head[5] & 0xff) == 0x61));
            case "webp":
                // RIFF....WEBP
                if (len < 12) return false;
                return (head[0] & 0xff) == 0x52 && (head[1] & 0xff) == 0x49
                        && (head[2] & 0xff) == 0x46 && (head[3] & 0xff) == 0x46
                        && (head[8] & 0xff) == 0x57 && (head[9] & 0xff) == 0x45
                        && (head[10] & 0xff) == 0x42 && (head[11] & 0xff) == 0x50;
            default:
                return false;
        }
    }
}
