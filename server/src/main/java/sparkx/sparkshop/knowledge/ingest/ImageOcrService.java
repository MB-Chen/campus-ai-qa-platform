// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.ingest;

import sparkx.sparkshop.knowledge.mapper.ChunkMapper;
import sparkx.sparkshop.knowledge.infra.TsVectorGenerator;
import sparkx.sparkshop.knowledge.infra.chat.VlmModelBridge;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * 图片 OCR + Caption 异步服务
 * 对文档中的图片：OCR 提取文字 + VLM 生成描述，作为独立 chunk 入库。
 *
 * <p>仅在 {@code app.rag.mineru.image-mode=keep}（图片保留 MinIO URL）模式下由
 * {@code DocumentIngestService} 触发；{@code describe}/{@code drop} 模式下图片在解析阶段已处理，
 * markdown 无 URL，本服务不会被调用。
 *
 * <p>⚠️ 历史问题：旧实现 {@code vlmModel.chat(prompt + "[image: " + url + "]")} 把 URL 当纯文本拼接，
 * 模型实际看不到图。本实现改为：先用 URL 拉取图片字节（HTTP GET），再走 {@link VlmModelBridge#describeImage}
 * （正确的多模态调用）。
 *
 */
@Service
public class ImageOcrService {

    private static final Logger log = LoggerFactory.getLogger(ImageOcrService.class);

    private final VlmModelBridge vlmBridge;
    private final ChunkMapper chunkMapper;
    private final ObjectMapper mapper = new ObjectMapper();

    public ImageOcrService(VlmModelBridge vlmBridge,
                           ChunkMapper chunkMapper) {
        this.vlmBridge = vlmBridge;
        this.chunkMapper = chunkMapper;
    }

    @Async("ragTaskExecutor")   // 虚拟线程异步，不阻塞入库主流程
    public void processImage(String imageUrl, String parentChunkId, String kbId) {
        try {
            // 拉取图片字节（URL 来自 MinerUDocumentParser.relocateImages 写入的 MinIO 预签名 URL）
            byte[] imgBytes = fetchImage(imageUrl);
            if (imgBytes.length == 0) {
                log.warn("[ImageOCR] 拉取图片失败/空 url={}", imageUrl);
                return;
            }
            String mime = guessMime(imageUrl);

            // OCR + Caption 二合一描述（VlmModelBridge 内部走正确的多模态调用）
            String desc = vlmBridge.describeImage(imgBytes, mime,
                    "请提取这张图片中的所有文字内容（OCR，保持原始结构），并简要描述图片内容。用中文输出。", 0);

            if (desc != null && !desc.isBlank()) {
                saveChunk(kbId, desc, "image_caption", parentChunkId, imageUrl);
                log.info("[ImageOCR] done url={} descLen={}", imageUrl, desc.length());
            }
        } catch (Exception e) {
            log.error("[ImageOCR] failed url={}", imageUrl, e);
        }
    }

    /** 简化版：仅写文本 chunk（无向量），用于图片描述入库。tsv 由 HanLP 预分词写入，支持关键词检索。 */
    private void saveChunk(String kbId, String content,
                           String chunkType, String parentChunkId, String imageUrl) {
        try {
            String meta = mapper.writeValueAsString(Map.of(
                    "kb_id", kbId,
                    "chunkType", chunkType,
                    "parentId", parentChunkId == null ? "" : parentChunkId,
                    "image_url", imageUrl,
                    "chunkRole", "child"));
            chunkMapper.insertTextOnly(
                    "c_" + UUID.randomUUID().toString().replace("-", ""),
                    kbId, content, TsVectorGenerator.toTsVector(content), meta);
        } catch (Exception e) {
            log.warn("[ImageOCR] saveChunk failed: {}", e.getMessage());
        }
    }

    private static byte[] fetchImage(String url) throws Exception {
        // 用 HttpURLConnection 直拉，避免引入额外 HTTP 客户端依赖
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(60000);
        try (java.io.InputStream in = conn.getInputStream();
             java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        } finally {
            conn.disconnect();
        }
    }

    private static String guessMime(String url) {
        String u = url == null ? "" : url.toLowerCase();
        if (u.endsWith(".jpg") || u.endsWith(".jpeg")) return "image/jpeg";
        if (u.endsWith(".png")) return "image/png";
        if (u.endsWith(".bmp")) return "image/bmp";
        if (u.endsWith(".gif")) return "image/gif";
        if (u.endsWith(".tif") || u.endsWith(".tiff")) return "image/tiff";
        if (u.endsWith(".webp")) return "image/webp";
        return "image/png";
    }
}
