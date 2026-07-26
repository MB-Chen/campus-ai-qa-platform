// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.ingest.mineru;

import sparkx.sparkshop.knowledge.infra.chat.VlmModelBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * MinerU 图片描述器：把 MinerU 解析出的图片用 VLM 转成「描述 + OCR」二合一文本。
 *
 * <p>设计要点：
 * <ul>
 *   <li>图标过滤：宽或高 &lt; {@link #MIN_IMAGE_DIMENSION}px、或字节数 &lt; {@link #MIN_IMAGE_BYTES}
 *       的图直接跳过，不调 VLM（ {@code isIconImage}）。</li>
 *   <li>并发：每张图一个 {@link CompletableFuture} 跑在 {@code ragTaskExecutor} 上，
 *       单文档图片通常 &lt; 50 张，串行调 VLM 太慢。</li>
 *   <li>容错：单张图 VLM 失败/返回空，对应 ref 不进结果 Map，调用方按「无描述」处理（保留原图引用），
 *       不影响其他图。</li>
 * </ul>
 *
 * <p>调用方约定：吃 {@code Map<ref, byte[]>}（ref 形如 {@code images/xxx.jpg}），
 * 返回 {@code Map<ref, description>}，未描述的 ref 不在返回 Map 中。
 */
@Component
public class MinerUImageDescriber {

    private static final Logger log = LoggerFactory.getLogger(MinerUImageDescriber.class);

    /** 图标过滤阈值：宽或高小于此值视为图标，跳过 VLM */
    static final int MIN_IMAGE_DIMENSION = 64;
    /** 图标过滤阈值：字节数小于此值视为图标，跳过 VLM */
    static final int MIN_IMAGE_BYTES = 512;

    /**
     * 二合一 prompt：描述 + OCR 一次调用完成。
     * 要求中文、自包含、OCR 完整、说明结构关系、自然引出。
     */
    private static final String DESCRIBE_PROMPT = """
            请把这张图片的内容转写成一段自包含的知识文本，用于知识库检索与问答。
            要求：
            1. 开头用一两句话说明这张图是什么（类型、主题）。
            2. 随后按图中的分组、层级、流程关系，完整转写所有文字内容（OCR），保持原始结构。
            3. 若是图表、架构图或流程图，请补充说明各部分之间的关系与数据含义。
            4. 保持术语准确完整，不要遗漏关键信息，不要编造图中没有的内容。
            5. 直接输出转写后的文字，不要加「图片描述」之类的标题或前缀，不要输出 markdown 图片语法。""";

    private final VlmModelBridge vlmBridge;
    private final Executor taskExecutor;

    public MinerUImageDescriber(VlmModelBridge vlmBridge,
                                @Qualifier("ragTaskExecutor") Executor taskExecutor) {
        this.vlmBridge = vlmBridge;
        this.taskExecutor = taskExecutor;
    }

    /**
     * 扩展名 → MIME 映射（与 {@link MinerUDocumentParser} 的 CONTENT_TYPES 保持一致）。
     */
    static String mimeOf(String ext) {
        if (ext == null) return "application/octet-stream";
        return switch (ext.toLowerCase()) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "bmp" -> "image/bmp";
            case "gif" -> "image/gif";
            case "tif", "tiff" -> "image/tiff";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

    /**
     * 对一批图片并发跑 VLM 描述。
     *
     * @param images key=ref（images/xxx.jpg），value=图片字节
     * @return key=ref，value=VLM 生成的描述文本；未描述的图（图标过滤/VLM 失败）不在 Map 中
     */
    public Map<String, String> describe(Map<String, byte[]> images) {
        Map<String, String> result = new HashMap<>();
        if (images == null || images.isEmpty()) {
            return result;
        }
        // 为每张图起一个异步任务
        Map<String, CompletableFuture<String>> futures = new HashMap<>();
        for (var e : images.entrySet()) {
            String ref = e.getKey();
            byte[] bytes = e.getValue();
            futures.put(ref, CompletableFuture.supplyAsync(() -> describeOne(ref, bytes), taskExecutor));
        }
        // 收集结果（任意一张失败不影响其他）
        for (var e : futures.entrySet()) {
            String ref = e.getKey();
            try {
                String desc = e.getValue().join();
                if (desc != null && !desc.isBlank()) {
                    result.put(ref, desc.trim());
                }
            } catch (Exception ex) {
                log.warn("[MinerU-image] 等待描述失败 ref={}: {}", ref, ex.getMessage());
            }
        }
        log.info("[MinerU-image] describe batch done total={} success={}", images.size(), result.size());
        return result;
    }

    /**
     * 描述单张图：先图标过滤，再调 VLM。失败返回 null（调用方按「无描述」处理）。
     */
    private String describeOne(String ref, byte[] bytes) {
        if (bytes == null || bytes.length < MIN_IMAGE_BYTES) {
            log.debug("[MinerU-image] 跳过图标(字节过小) ref={} bytes={}", ref, bytes == null ? 0 : bytes.length);
            return null;
        }
        String ext = extOf(ref);
        String mime = mimeOf(ext);
        // 读宽高做图标过滤；非图片格式（ImageIO 抛异常）则不调 VLM，避免浪费
        int[] wh = readDimension(bytes);
        if (wh != null) {
            int w = wh[0], h = wh[1];
            if (w < MIN_IMAGE_DIMENSION || h < MIN_IMAGE_DIMENSION) {
                log.debug("[MinerU-image] 跳过图标(尺寸过小) ref={} {}x{}", ref, w, h);
                return null;
            }
        }
        try {
            long t0 = System.currentTimeMillis();
            String desc = vlmBridge.describeImage(bytes, mime, DESCRIBE_PROMPT, 0);
            long cost = System.currentTimeMillis() - t0;
            log.info("[MinerU-image] describe ok ref={} mime={} bytes={} descLen={} cost={}ms",
                    ref, mime, bytes.length, desc == null ? 0 : desc.length(), cost);
            return desc;
        } catch (Exception ex) {
            log.warn("[MinerU-image] describe 失败 ref={}: {}", ref, ex.getMessage());
            return null;
        }
    }

    /** 读图片宽高；非图片格式返回 null（调用方按「无法判定尺寸」放行，交给 VLM 判断） */
    private static int[] readDimension(byte[] bytes) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage img = ImageIO.read(in);
            if (img == null) return null;
            return new int[]{img.getWidth(), img.getHeight()};
        } catch (Exception e) {
            return null;
        }
    }

    private static String extOf(String name) {
        if (name == null) return "";
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String base = slash < 0 ? name : name.substring(slash + 1);
        int dot = base.lastIndexOf('.');
        return dot < 0 ? "" : base.substring(dot + 1);
    }
}
