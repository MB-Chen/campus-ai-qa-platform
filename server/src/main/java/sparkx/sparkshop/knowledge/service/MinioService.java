// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.service;

import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import sparkx.sparkshop.knowledge.config.MinioConfig;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 操作封装
 */
@Slf4j
@Service
public class MinioService {

    @Resource
    private MinioClient minioClient;

    @Resource
    private MinioConfig minioConfig;

    /**
     * 启动时确保桶存在
     */
    @PostConstruct
    public void init() {
        ensureBucket();
    }

    /**
     * 确保桶存在
     */
    public void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(
                    io.minio.BucketExistsArgs.builder().bucket(minioConfig.getBucket()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioConfig.getBucket()).build());
                log.info("MinIO 桶已创建: {}", minioConfig.getBucket());
            }
        } catch (Exception e) {
            log.error("初始化 MinIO 桶失败: {}", e.getMessage());
        }
    }

    /**
     * 上传文件
     *
     * @param objectName 对象名（含路径）
     * @param file       文件
     */
    public void upload(String objectName, MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectName)
                    .stream(is, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        }
    }

    /**
     * 下载文件到 HttpServletResponse（浏览器直接下载）
     *
     * @param objectName 对象名
     * @param filename   下载显示的文件名
     * @param response   响应
     */
    public void download(String objectName, String filename, HttpServletResponse response) throws Exception {
        try (InputStream is = minioClient.getObject(GetObjectArgs.builder()
                .bucket(minioConfig.getBucket())
                .object(objectName)
                .build())) {
            response.setContentType("application/octet-stream");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename=" + encoded);

            ServletOutputStream os = response.getOutputStream();
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) {
                os.write(buf, 0, len);
            }
            os.flush();
        }
    }

    /**
     * 获取文件流（用于在线预览回写）
     *
     * @param objectName 对象名
     * @return 输入流
     */
    public InputStream getObject(String objectName) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(minioConfig.getBucket())
                .object(objectName)
                .build());
    }

    /**
     * 删除文件
     *
     * @param objectName 对象名
     */
    public void remove(String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            log.error("删除 MinIO 对象失败: {} - {}", objectName, e.getMessage());
        }
    }

    /**
     * 复制对象（服务端拷贝，生成独立副本）
     *
     * @param srcObjectName 源对象名
     * @param dstObjectName 目标对象名
     */
    public void copy(String srcObjectName, String dstObjectName) throws Exception {
        minioClient.copyObject(CopyObjectArgs.builder()
                .bucket(minioConfig.getBucket())
                .object(dstObjectName)
                .source(CopySource.builder()
                        .bucket(minioConfig.getBucket())
                        .object(srcObjectName)
                        .build())
                .build());
    }

    /**
     * 生成预签名 URL（默认 2 小时有效）
     *
     * @param objectName 对象名
     * @return 预签名 URL
     */
    public String presignedUrl(String objectName) {
        return presignedUrl(objectName, Duration.ofHours(2));
    }

    /**
     * 生成预签名 URL
     *
     * @param objectName 对象名
     * @param expiry     有效期
     * @return 预签名 URL
     */
    public String presignedUrl(String objectName, Duration expiry) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(minioConfig.getBucket())
                    .object(objectName)
                    .expiry((int) expiry.toSeconds(), TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            log.error("生成预签名 URL 失败: {} - {}", objectName, e.getMessage());
            return "";
        }
    }

    /**
     * 获取桶名
     */
    public String getBucket() {
        return minioConfig.getBucket();
    }

    /**
     * 获取 endpoint
     */
    public String getEndpoint() {
        return minioConfig.getEndpoint();
    }
}
