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

/**
 * MinerU 解析选项。
 *
 * 由 {@link sparkx.sparkshop.knowledge.service.IExtServiceConfigService#getMineruConfig(String)}
 * 按当前模式（SELF / CLOUD）从 {@code ext_service_config} 表构造，传给 {@link MinerUClient} 作为请求参数。
 * 云端模式忽略 endpoint/model/vlmServerUrl；自建模式忽略 apiKey。
 *
 * @param mode            部署模式
 * @param endpoint        自建 MinerU 根地址（仅 SELF）
 * @param apiKey          云端 token（仅 CLOUD）
 * @param model           后端：pipeline / vlm-* / hybrid-*（SELF）；model_version：pipeline / vlm / MinerU-HTML（CLOUD）
 * @param vlmServerUrl    vLLM 服务地址（仅 vlm-http-client / hybrid-http-client 后端）
 * @param enableFormula   识别公式
 * @param enableTable     识别表格
 * @param enableOcr       是否 OCR（false 时 parse_method=txt）
 * @param language        OCR 语言：ch / en / ...
 * @param pollIntervalSec 云端轮询间隔（秒）
 * @param timeoutSec      单任务总超时（秒）
 */
public record MinerUOptions(
        Mode mode,
        String endpoint,
        String apiKey,
        String model,
        String vlmServerUrl,
        boolean enableFormula,
        boolean enableTable,
        boolean enableOcr,
        String language,
        int pollIntervalSec,
        int timeoutSec) {

    public enum Mode { SELF, CLOUD }
}
