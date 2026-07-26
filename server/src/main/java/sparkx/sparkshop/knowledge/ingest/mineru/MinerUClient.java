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

import sparkx.sparkshop.knowledge.config.MinerUConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * MinerU HTTP 客户端 —— {@code MinerUReader}（自建）与 {@code MinerUCloudReader}（云端）。
 *
 * 两条路径都收敛到 {@link MinerUResult}（Markdown + 图片字节 Map）：
 *
 * <h3>自建 SELF（同步）</h3>
 * <pre>
 *   POST {endpoint}/file_parse   (multipart/form-data，无鉴权，超时较长)
 *   字段：return_md=true return_images=true return_content_list=true
 *         parse_method=ocr|txt backend={model} lang_list={language}
 *         table_enable formula_enable start_page_id=0 end_page_id=99999
 *         server_url={vlmServerUrl}（仅 vlm-http-client/hybrid-http-client）
 *   响应：results.document 优先，回退 results.files；images 为 base64
 * </pre>
 *
 * <h3>云端 CLOUD（三步 batch + 轮询）</h3>
 * <pre>
 *   1. POST https://mineru.net/api/v4/file-urls/batch  (Bearer apiKey)  → batch_id + file_urls[]
 *   2. PUT  file_urls[0]  (原始字节，无 Content-Type) → 上传
 *   3. GET  https://mineru.net/api/v4/extract-results/batch/{batch_id}（每 pollInterval 秒）
 *      state=done：优先 markdown/content/text；否则下载 full_zip_url 解压取最浅 .md + 引用图片
 * </pre>
 */
@Component
public class MinerUClient {

    private static final Logger log = LoggerFactory.getLogger(MinerUClient.class);

    /** 云端 API 根地址（mineru.net，hardcoded） */
    private static final String CLOUD_BASE = "https://mineru.net/api/v4";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final MediaType OCTET = MediaType.get("application/octet-stream");

    private final OkHttpClient http;
    /**
     * MinerU 响应解析专用 ObjectMapper，仅用于解析 MinerU API 的 JSON（markdown/images 等，无日期字段）。
     * 故意不依赖 Spring 的全局 ObjectMapper：一方面隔离全局 Jackson 配置，另一方面避免把
     * {@code ObjectMapper} 注册成 Bean 会压制 Spring Boot 的 {@code JacksonAutoConfiguration}
     * （那会导致 MVC 序列化 {@code java.time.*} 失败）。与本项目其它 ~14 处
     * {@code private ObjectMapper mapper = new ObjectMapper();} 用法一致。
     */
    private final ObjectMapper mapper = new ObjectMapper();

    public MinerUClient(@Qualifier("mineruHttpClient") OkHttpClient http) {
        this.http = http;
    }

    // 自建模式

    /**
     * 自建 MinerU 同步解析。
     *
     * @param bytes    文件字节
     * @param fileName 文件名（用于 multipart 文件名与日志）
     * @param opts     选项（用 endpoint/model/vlmServerUrl/enable 系列/language）
     */
    public MinerUResult parseSelfHosted(byte[] bytes, String fileName, MinerUOptions opts) {
        String endpoint = requireNonBlank(opts.endpoint(), "自建 MinerU endpoint 未配置（app.rag.mineru.endpoint）");
        MinerUConfig.validateOutboundUrl(endpoint);
        String base = stripTrailingSlash(endpoint);
        String url = base + "/file_parse";

        MultipartBody.Builder mb = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("return_md", "true")
                .addFormDataPart("return_images", "true")
                .addFormDataPart("return_content_list", "true")
                .addFormDataPart("return_middle_json", "false")
                .addFormDataPart("return_model_output", "false")
                .addFormDataPart("response_format_zip", "false")
                .addFormDataPart("table_enable", String.valueOf(opts.enableTable()))
                .addFormDataPart("formula_enable", String.valueOf(opts.enableFormula()))
                .addFormDataPart("parse_method", opts.enableOcr() ? "ocr" : "txt")
                .addFormDataPart("start_page_id", "0")
                .addFormDataPart("end_page_id", "99999")
                .addFormDataPart("backend", opts.model() == null ? "pipeline" : opts.model())
                .addFormDataPart("lang_list", opts.language() == null ? "ch" : opts.language());
        // 仅 vlm-http-client / hybrid-http-client 后端需要 server_url
        String model = opts.model() == null ? "" : opts.model();
        if (opts.vlmServerUrl() != null && !opts.vlmServerUrl().isBlank()
                && (model.contains("vlm-http") || model.contains("hybrid-http"))) {
            mb.addFormDataPart("server_url", opts.vlmServerUrl());
        }
        String partName = "document";
        mb.addFormDataPart("files", partName,
                RequestBody.create(bytes, MediaType.parse("application/octet-stream")));

        Request req = new Request.Builder().url(url).post(mb.build()).build();
        log.info("[MinerU-self] POST {} fileName={} bytes={}", url, fileName, bytes.length);
        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                throw new IOException("MinerU 自建解析失败 HTTP " + resp.code() + ": " + truncate(body, 500));
            }
            return parseSelfHostedJson(body);
        } catch (IOException e) {
            throw new RuntimeException("MinerU 自建解析异常: " + e.getMessage(), e);
        }
    }

    /** 解析自建 /file_parse 响应：优先 results.document，回退 results.files */
    private MinerUResult parseSelfHostedJson(String body) throws IOException {
        JsonNode root = mapper.readTree(body);
        JsonNode results = root.path("results");
        JsonNode doc = !results.path("document").isMissingNode() ? results.path("document")
                : results.path("files");
        if (doc.isMissingNode()) {
            throw new IOException("MinerU 响应缺少 results.document/files: " + truncate(body, 500));
        }
        String md = textOf(doc, "md_content");
        Map<String, byte[]> images = decodeImages(doc.path("images"));
        return new MinerUResult(md == null ? "" : md, images);
    }

    // 云端模式（三步 batch + 轮询）

    /**
     * 云端 MinerU 解析（mineru.net/api/v4）。
     *
     * @param bytes    文件字节
     * @param fileName 文件名
     * @param opts     选项（用 apiKey/model/enable 系列/language/pollIntervalSec/timeoutSec）
     */
    public MinerUResult parseCloud(byte[] bytes, String fileName, MinerUOptions opts) {
        requireNonBlank(opts.apiKey(), "MinerU 云端 api-key 未配置（app.rag.mineru.api-key）");
        String dataId = UUID.randomUUID().toString();

        // 1. 申请上传地址
        String[] batchAndUrl = applyUploadUrl(fileName, dataId, opts);
        String batchId = batchAndUrl[0];
        String uploadUrl = batchAndUrl[1];
        log.info("[MinerU-cloud] batchId={} 申请上传地址 ok，开始上传 {} 字节", batchId, bytes.length);

        // 2. PUT 上传原始字节（无 Content-Type，对标 MinerU 文档 / issue #4145）
        uploadFile(uploadUrl, bytes);

        // 3. 轮询结果
        return pollResult(batchId, opts);
    }

    /** Step 1：POST /file-urls/batch，返回 {batchId, fileUrl} */
    private String[] applyUploadUrl(String fileName, String dataId, MinerUOptions opts) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("files", java.util.List.of(Map.of(
                "name", fileName == null ? "document" : fileName,
                "data_id", dataId)));
        payload.put("model_version", opts.model() == null ? "pipeline" : opts.model());
        payload.put("is_ocr", opts.enableOcr());
        payload.put("enable_formula", opts.enableFormula());
        payload.put("enable_table", opts.enableTable());
        payload.put("language", opts.language() == null ? "ch" : opts.language());

        Request req = new Request.Builder()
                .url(CLOUD_BASE + "/file-urls/batch")
                .header("Authorization", "Bearer " + opts.apiKey())
                .post(RequestBody.create(toJson(payload), JSON))
                .build();
        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                throw new IOException("MinerU 申请上传地址失败 HTTP " + resp.code() + ": " + truncate(body, 500));
            }
            JsonNode root = mapper.readTree(body);
            // { "code":0, "data": { "batch_id": "...", "file_urls": ["https://..."] } }
            JsonNode data = root.path("data");
            String batchId = textOf(data, "batch_id");
            JsonNode urls = data.path("file_urls");
            if (batchId == null || batchId.isBlank() || !urls.isArray() || urls.isEmpty()) {
                throw new IOException("MinerU 申请上传地址响应缺字段: " + truncate(body, 500));
            }
            return new String[]{batchId, urls.get(0).asText()};
        } catch (IOException e) {
            throw new RuntimeException("MinerU 申请上传地址异常: " + e.getMessage(), e);
        }
    }

    /** Step 2：PUT 原始字节到预签名 URL */
    private void uploadFile(String uploadUrl, byte[] bytes) {
        // 预签名 URL 一般已是公网地址，不再做 SSRF 校验（由 mineru.net 提供）
        // ⚠️ MediaType 必须传 null：MinerU 颁发的 OSS 预签名 URL 是按「不带 Content-Type」算签名的
        // （见 OSS 回错 StringToSign 的期望与 MinerU issue #4145）。若传 application/octet-stream，
        // OkHttp 会把 Content-Type 头带上去，OSS 端重算签名与请求 URL 里的 SignatureProvided 不一致，
        // 直接 403 SignatureDoesNotMatch。传 null 后 OkHttp 不会发该头。
        Request req = new Request.Builder()
                .url(uploadUrl)
                .put(RequestBody.create(bytes, null))
                .build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                String body = resp.body() != null ? resp.body().string() : "";
                throw new IOException("MinerU 文件上传失败 HTTP " + resp.code() + ": " + truncate(body, 500));
            }
        } catch (IOException e) {
            throw new RuntimeException("MinerU 文件上传异常: " + e.getMessage(), e);
        }
    }

    /** Step 3：轮询 /extract-results/batch/{batchId}，state=done 后取 Markdown */
    private MinerUResult pollResult(String batchId, MinerUOptions opts) {
        long deadline = System.currentTimeMillis() + Math.max(1, opts.timeoutSec()) * 1000L;
        long interval = Math.max(1, opts.pollIntervalSec()) * 1000L;
        String url = CLOUD_BASE + "/extract-results/batch/" + urlEncode(batchId);

        int tick = 0;
        while (System.currentTimeMillis() < deadline) {
            tick++;
            Request req = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + opts.apiKey())
                    .get()
                    .build();
            try (Response resp = http.newCall(req).execute()) {
                String body = resp.body() != null ? resp.body().string() : "";
                if (!resp.isSuccessful()) {
                    throw new IOException("MinerU 轮询失败 HTTP " + resp.code() + ": " + truncate(body, 500));
                }
                JsonNode root = mapper.readTree(body);
                JsonNode extract = root.path("data").path("extract_result");
                // extract_result 可能是对象或数组（单文件取第一个）
                JsonNode item = extract.isArray() ? (!extract.isEmpty() ? extract.get(0) : null) : extract;
                if (item != null && !item.isMissingNode()) {
                    String state = textOf(item, "state");
                    if ("done".equalsIgnoreCase(state)) {
                        return extractDone(item);
                    }
                    if ("failed".equalsIgnoreCase(state)) {
                        throw new RuntimeException("MinerU 解析失败: "
                                + textOf(item, "err_msg"));
                    }
                    // running：继续轮询
                    if (log.isDebugEnabled() && item.has("extract_progress")) {
                        JsonNode p = item.path("extract_progress");
                        log.debug("[MinerU-cloud] 轮询#{} state=running progress={}/{}",
                                tick, p.path("extracted_pages").asText(), p.path("total_pages").asText());
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("MinerU 轮询异常: " + e.getMessage(), e);
            }
            sleepQuiet(interval);
        }
        throw new RuntimeException("MinerU 解析超时（" + opts.timeoutSec() + "s），batchId=" + batchId);
    }

    /** state=done 的单项：优先内联 markdown/content/text，否则下载 full_zip_url 解压 */
    private MinerUResult extractDone(JsonNode item) {
        String md = firstNonBlank(textOf(item, "markdown"), textOf(item, "content"), textOf(item, "text"));
        if (md != null && !md.isBlank()) {
            log.info("[MinerU-cloud] 解析完成（内联 markdown），长度 {}", md.length());
            return new MinerUResult(md, Map.of());
        }
        String zipUrl = textOf(item, "full_zip_url");
        if (zipUrl == null || zipUrl.isBlank()) {
            throw new RuntimeException("MinerU 解析完成但无 markdown/zip 结果");
        }
        log.info("[MinerU-cloud] 内联为空，下载 zip: {}", zipUrl);
        return extractFromZip(zipUrl);
    }

    /** 下载 full_zip_url，找最浅（路径最短）的 .md，并提取其引用的图片 */
    private MinerUResult extractFromZip(String zipUrl) {
        Request req = new Request.Builder().url(zipUrl).get().build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new IOException("MinerU zip 下载失败 HTTP " + resp.code());
            }
            // 先把 zip 全量读入内存，再解压（避免流式读 zip 受 OkHttp 连接限制）
            byte[] zipBytes = resp.body().bytes();
            return unzipMarkdown(zipBytes);
        } catch (IOException e) {
            throw new RuntimeException("MinerU zip 下载/解压异常: " + e.getMessage(), e);
        }
    }

    /** 解压 zip：取路径最浅的 .md（同级再按字母序兜底），并收集 images 目录下的图片 */
    private MinerUResult unzipMarkdown(byte[] zipBytes) throws IOException {
        String md = null;
        String mdEntry = null;
        Map<String, byte[]> images = new HashMap<>();
        // 先扫一遍收集所有 entry（找 md + 图片）
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                byte[] data = readAll(zis);
                if (name.toLowerCase().endsWith(".md")) {
                    // 选路径最浅的 md（separator 段数最少），并列兜底按字母序
                    if (mdEntry == null || depth(name) < depth(mdEntry)
                            || (depth(name) == depth(mdEntry) && name.compareTo(mdEntry) < 0)) {
                        mdEntry = name;
                        md = new String(data, StandardCharsets.UTF_8);
                    }
                } else if (isImageName(name)) {
                    images.put(stripDir(name), data);
                }
                zis.closeEntry();
            }
        }
        if (md == null) {
            throw new IOException("MinerU zip 中未找到 .md 文件");
        }
        return new MinerUResult(md, images);
    }

    // 工具

    /** 把 base64 图片 Map 解码为字节 Map（key 保持原引用名，如 images/xxx.jpg） */
    private Map<String, byte[]> decodeImages(JsonNode imagesNode) {
        if (imagesNode == null || imagesNode.isMissingNode() || !imagesNode.isObject()) {
            return Map.of();
        }
        Map<String, byte[]> out = new HashMap<>();
        imagesNode.fields().forEachRemaining(e -> {
            String ref = e.getKey();           // images/xxx.jpg
            String b64 = e.getValue().asText("");
            String pure = stripDataPrefix(b64);
            if (!pure.isEmpty()) {
                try {
                    out.put(ref, Base64.getDecoder().decode(pure));
                } catch (IllegalArgumentException ex) {
                    log.warn("[MinerU] 图片 base64 解码失败 ref={}，跳过", ref);
                }
            }
        });
        return out;
    }

    private static String stripDataPrefix(String b64) {
        if (b64 == null) return "";
        int comma = b64.indexOf(',');
        // 形如 data:image/png;base64,xxxx
        if (comma >= 0 && comma < b64.length() - 1 && b64.startsWith("data:")) {
            return b64.substring(comma + 1);
        }
        return b64.replaceAll("\\s+", "");
    }

    private static String textOf(JsonNode node, String field) {
        if (node == null || node.isMissingNode()) return null;
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        return v.isTextual() ? v.asText() : v.toString();
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static String requireNonBlank(String v, String msg) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException(msg);
        return v;
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static boolean isImageName(String name) {
        String l = name.toLowerCase();
        return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png")
                || l.endsWith(".bmp") || l.endsWith(".gif") || l.endsWith(".tif") || l.endsWith(".tiff");
    }

    /** 取文件名最后一段（去目录） */
    private static String stripDir(String name) {
        int i = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return i < 0 ? name : name.substring(i + 1);
    }

    private static int depth(String path) {
        int d = 0;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '/' || c == '\\') d++;
        }
        return d;
    }

    private static byte[] readAll(ZipInputStream zis) throws IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = zis.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (IOException e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }
    }

    private static void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("MinerU 轮询被中断", e);
        }
    }
}
