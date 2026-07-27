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

import sparkx.sparkshop.knowledge.ingest.mineru.MinerUParserFactory;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 按文档类型/引擎路由解析器
 *
 * 引擎（engine 参数）：
 *  - tika（默认）/ pdfbox / poi：本地解析器
 *  - mineru：自建 MinerU 同步解析（{@code POST <endpoint>/file_parse}），需 app.rag.mineru.endpoint
 *  - mineru_cloud：MinerU 云端 batch+poll（mineru.net/api/v4），需 app.rag.mineru.api-key
 *
 * 注意 1.17.0：DocumentParser.parse(InputStream) 返回 List<Document>（1.x 破坏性变更）。
 * 这里仅按引擎返回解析器实例，解析由调用方执行。
 *
 * MinerU 工厂用 {@link ObjectProvider} 松耦合注入：未配置或 bean 缺失时，mineru 引擎抛可读异常，
 * 不影响默认 tika 引擎启动。
 */
@Service
public class DocumentTypeRouter {

    private final ObjectProvider<MinerUParserFactory> minerUFactoryProvider;

    public DocumentTypeRouter(ObjectProvider<MinerUParserFactory> minerUFactoryProvider) {
        this.minerUFactoryProvider = minerUFactoryProvider;
    }

    /**
     * 返回合适的 LangChain4j DocumentParser。
     * @param fileName 文件名（用于扩展名辅助判断 + MinerU 文件名）
     * @param engine   tika | pdfbox | poi | mineru | mineru_cloud
     */
    public DocumentParser route(String fileName, String engine) {
        String eng = engine == null ? "tika" : engine;
        return switch (eng) {
            case "pdfbox" -> new ApachePdfBoxDocumentParser();
            case "poi" -> new ApachePoiDocumentParser();
            case "mineru", "mineru_cloud" -> {
                MinerUParserFactory factory = minerUFactoryProvider.getIfAvailable();
                if (factory == null) {
                    throw new IllegalStateException("MinerU 解析器未启用（缺少 MinerUParserFactory bean）");
                }
                yield factory.create(fileName, eng);
            }
            default -> new ApacheTikaDocumentParser();
            // Tika 自动识别格式，且可启用 Tesseract OCR 处理扫描件
        };
    }

    private String extOf(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase();
    }
}
