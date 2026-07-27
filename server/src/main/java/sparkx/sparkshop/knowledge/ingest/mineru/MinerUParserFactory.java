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

import sparkx.sparkshop.knowledge.config.RagProperties;
import sparkx.sparkshop.knowledge.service.IExtServiceConfigService;
import sparkx.sparkshop.knowledge.service.MinioService;
import dev.langchain4j.data.document.DocumentParser;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * MinerU 解析器工厂。
 *
 * 由 {@code DocumentTypeRouter} 通过 {@code ObjectProvider} 松耦合注入：
 *  - {@code create(fileName, "mineru")}      → 自建同步解析（需 ext_service_config 表中有启用的 mineru_self 记录）
 *  - {@code create(fileName, "mineru_cloud")} → 云端 batch+poll（需启用的 mineru_cloud 记录）
 *
 * <p>★ 配置来源：MinerU 服务连接（endpoint/apiKey/model）读 {@code ext_service_config} 表
 * （页面「知识库 → 外部服务配置」可编辑），由 {@link IExtServiceConfigService#getMineruConfig(String)} 构造 {@link MinerUOptions}。
 * 图片处理策略读 {@code app.rag.mineru.image-mode}（{@link RagProperties}，默认 {@code describe}）。
 *
 * <p>图片处理三种模式见 {@link MinerUDocumentParser} 类注释。{@code keep} 模式仍走 MinIO
 * （{@link MinioService} + {@link MinioClient}），{@code describe}/{@code drop} 模式不依赖 MinIO。
 */
@Component
public class MinerUParserFactory {

    private static final Logger log = LoggerFactory.getLogger(MinerUParserFactory.class);

    private final MinerUClient client;
    private final IExtServiceConfigService extServiceConfigService;
    private final MinerUImageDescriber imageDescriber;
    private final RagProperties ragProps;
    private final MinioClient minioClient;
    private final MinioService minioService;

    public MinerUParserFactory(MinerUClient client,
                               IExtServiceConfigService extServiceConfigService,
                               MinerUImageDescriber imageDescriber,
                               RagProperties ragProps,
                               MinioClient minioClient,
                               MinioService minioService) {
        this.client = client;
        this.extServiceConfigService = extServiceConfigService;
        this.imageDescriber = imageDescriber;
        this.ragProps = ragProps;
        this.minioClient = minioClient;
        this.minioService = minioService;
    }

    /**
     * 按引擎名构造 MinerU 解析器。
     *
     * <p>engine 到 category 映射与必填项校验全部下沉到
     * {@link IExtServiceConfigService#getMineruConfig(String)}，本方法只负责组装解析器。
     *
     * @param engine {@code mineru}（自建）或 {@code mineru_cloud}（云端）
     */
    public DocumentParser create(String fileName, String engine) {
        MinerUOptions opts = extServiceConfigService.getMineruConfig(engine);
        log.info("[MinerU-factory] engine={} fileName={} mode={} endpoint={} backend={} imageMode={}",
                engine, fileName, opts.mode(),
                opts.mode() == MinerUOptions.Mode.SELF ? opts.endpoint() : "mineru.net",
                opts.model(),
                ragProps.getMineru().getImageMode());
        return new MinerUDocumentParser(client, opts, fileName,
                imageDescriber, ragProps, minioClient, minioService);
    }
}

