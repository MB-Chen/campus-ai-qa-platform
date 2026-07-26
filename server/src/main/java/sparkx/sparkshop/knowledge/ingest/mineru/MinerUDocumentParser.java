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

import sparkx.sparkshop.clouddrive.service.MinioService;
import sparkx.sparkshop.knowledge.config.RagProperties;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MinerU 文档解析器 —— 把 {@link MinerUClient} 的结果适配为 LangChain4j {@link DocumentParser}。
 *
 * 职责：
 *  1. 调 {@link MinerUClient}（自建同步 / 云端轮询）拿到 Markdown + 图片字节
 *  2. 按 {@code app.rag.mineru.image-mode} 处理图片（默认 {@code describe}）：
 *     <ul>
 *       <li>{@code describe}：{@link MinerUImageDescriber} 调 VLM 把每张图转成「描述 + OCR」二合一文本，
 *           原地替换 markdown 里的 {@code ![](ref)}；无描述的图（图标过滤/VLM 失败）引用删除。</li>
 *       <li>{@code drop}：直接删除所有图片引用，不调 VLM。</li>
 *       <li>{@code keep}：保留旧行为——上 MinIO + 改写为 7 天预签名 URL（向后兼容）。</li>
 *     </ul>
 *  3. 返回 {@code Document.from(markdown)}
 *
 * <p>本对象每次解析新建（持 fileName/opts），由 {@link MinerUParserFactory} 构造，非 Spring Bean。
 */
public class MinerUDocumentParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(MinerUDocumentParser.class);

    /** 匹配 Markdown 图片引用：![alt](ref)，ref 形如 images/xxx.jpg 或 full/xxx.png */
    private static final Pattern MD_IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\(([^)]+)\\)");

    /** 自建/云端模式都支持的图片 content-type 推断 */
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png",
            "bmp", "image/bmp", "gif", "image/gif", "tif", "image/tiff",
            "tiff", "image/tiff", "webp", "image/webp");

    private final MinerUClient client;
    private final MinerUOptions options;
    private final String fileName;
    private final MinerUImageDescriber imageDescriber;
    private final RagProperties ragProps;
    // keep 模式才用（describe/drop 模式不依赖）
    private final MinioClient minioClient;
    private final MinioService minioService;

    public MinerUDocumentParser(MinerUClient client, MinerUOptions options, String fileName,
                                MinerUImageDescriber imageDescriber, RagProperties ragProps,
                                MinioClient minioClient, MinioService minioService) {
        this.client = client;
        this.options = options;
        this.fileName = fileName;
        this.imageDescriber = imageDescriber;
        this.ragProps = ragProps;
        this.minioClient = minioClient;
        this.minioService = minioService;
    }

    @Override
    public Document parse(InputStream inputStream) {
        byte[] bytes = readAll(inputStream);
        log.info("[MinerU-parser] mode={} fileName={} bytes={}", options.mode(), fileName, bytes.length);

        MinerUResult result = switch (options.mode()) {
            case SELF -> client.parseSelfHosted(bytes, fileName, options);
            case CLOUD -> client.parseCloud(bytes, fileName, options);
        };

        String markdown = result.markdown();
        if (markdown == null || markdown.isBlank()) {
            throw new RuntimeException("MinerU 解析结果为空 Markdown");
        }

        // 按配置策略处理图片
        String mode = resolveImageMode();
        int imgCount = result.images() == null ? 0 : result.images().size();
        if (imgCount > 0) {
            markdown = switch (mode) {
                case "describe" -> describeImages(markdown, result.images());
                case "drop" -> dropImages(markdown);
                case "keep" -> relocateImages(markdown, result.images());
                default -> describeImages(markdown, result.images());
            };
        }
        log.info("[MinerU-parser] done fileName={} mdLen={} images={} imageMode={}",
                fileName, markdown.length(), imgCount, mode);
        return Document.from(markdown);
    }

    private String resolveImageMode() {
        String m = ragProps == null || ragProps.getMineru() == null ? null
                : ragProps.getMineru().getImageMode();
        return (m == null || m.isBlank()) ? "describe" : m.trim().toLowerCase();
    }

    /**
     * describe 模式：VLM 把图转成「描述 + OCR」文本，原地替换 markdown 图片引用。
     * 有描述 → 替换为引用块风格的文本块（前后空行，让下游 splitter 当独立段落）；
     * 无描述（图标过滤 / VLM 报错 / 未配置视觉模型）→ 保留原图引用，不删图。
     * 图片本身是知识载体，删掉会让 chunk 丢失这部分信息，宁留勿删。
     */
    private String describeImages(String markdown, Map<String, byte[]> images) {
        Map<String, String> refToDesc = imageDescriber.describe(images);
        Matcher m = MD_IMAGE.matcher(markdown);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String ref = m.group(1).trim();
            String desc = resolveDesc(ref, refToDesc);
            String replacement = desc != null
                    ? "\n\n[图片描述] " + desc + "\n\n"
                    : m.group(0);   // 无描述保留原图引用
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 解析 markdown 引用名到 VLM 描述（兼容 images/xxx.jpg、xxx.jpg 两种 key 风格） */
    private String resolveDesc(String ref, Map<String, String> refToDesc) {
        if (refToDesc.containsKey(ref)) return refToDesc.get(ref);
        String nameOnly = stripDir(ref);
        return refToDesc.get(nameOnly);
    }

    /** drop 模式：直接删除所有 markdown 图片引用 */
    private String dropImages(String markdown) {
        return MD_IMAGE.matcher(markdown).replaceAll("");
    }

    /**
     * keep 模式（旧行为）：上 MinIO + 改写为 7 天预签名 URL。
     * 保留供 {@code image-mode=keep} 向后兼容，describe/drop 模式不调用。
     */
    private String relocateImages(String markdown, Map<String, byte[]> images) {
        String batch = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String bucket = minioService.getBucket();
        Map<String, String> refToUrl = new java.util.HashMap<>();
        for (var e : images.entrySet()) {
            String ref = e.getKey();
            String nameOnly = stripDir(ref);
            String ext = extOf(nameOnly);
            String objectName = "mineru/" + batch + "/" + nameOnly;
            String ct = CONTENT_TYPES.getOrDefault(ext, "application/octet-stream");
            try {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .stream(new ByteArrayInputStream(e.getValue()), e.getValue().length, -1)
                        .contentType(ct)
                        .build());
                String url = minioService.presignedUrl(objectName, Duration.ofDays(7));
                refToUrl.put(ref, url);
                refToUrl.putIfAbsent(nameOnly, url);
            } catch (Exception ex) {
                log.warn("[MinerU-parser] 图片上传 MinIO 失败 ref={}，原引用保留: {}", ref, ex.getMessage());
            }
        }
        Matcher m = MD_IMAGE.matcher(markdown);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String ref = m.group(1).trim();
            String url = refToUrl.containsKey(ref) ? refToUrl.get(ref) : refToUrl.get(stripDir(ref));
            String replacement = url != null ? "![](" + url + ")" : m.group(0);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static byte[] readAll(InputStream is) {
        try (java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("读取输入流失败: " + e.getMessage(), e);
        }
    }

    private static String stripDir(String name) {
        int i = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return i < 0 ? name : name.substring(i + 1);
    }

    private static String extOf(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase();
    }
}
