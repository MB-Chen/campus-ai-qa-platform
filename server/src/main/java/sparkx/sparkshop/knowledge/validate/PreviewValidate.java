// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------
package sparkx.sparkshop.knowledge.validate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.io.Serializable;
import java.util.List;

/**
 * 试切预览入参（multipart 表单字段对象绑定，不写 @RequestBody）。
 *
 * <p>前端用 FormData append：
 * <pre>
 * formData.append('files', file1)   // 多次 append 同名 = List
 * formData.append('engine', 'tika')
 * formData.append('chunkSize', '512')
 * </pre>
 */
@Data
@Schema(description = "试切预览入参")
public class PreviewValidate implements Serializable {

    @Schema(description = "文件列表（multipart）")
    @NotEmpty(message = "请选择文件")
    private List<MultipartFile> files;

    @Schema(description = "默认解析引擎（当 parserEngineRules 未匹配时使用；默认 tika）")
    private String engine;

    @Schema(description = "按文件类型分组指定解析引擎（覆盖默认引擎；" +
            "每条规则将一组文件扩展名映射到一个引擎，如 [{fileTypes:[\"pdf\"],engine:\"pdfbox\"}]）")
    private List<ParserEngineRule> parserEngineRules;

    @Schema(description = "parserEngineRules 的 JSON 字符串形式（multipart 传参用；后端解析后赋给 parserEngineRules 字段）")
    private String parserEngineRulesJson;

    @Schema(description = "分块大小（可空，走全局配置）")
    private Integer chunkSize;

    @Schema(description = "分块重叠（可空，走全局配置）")
    private Integer overlap;

    @Schema(description = "分块策略：auto/heading/heuristic/recursive（可空，走全局配置）")
    private String strategy;

    @Schema(description = "是否启用父子分块（可空，默认关闭）")
    private Boolean enableParentChild;

    @Schema(description = "父块大小（可空，走全局配置；仅 enableParentChild=true 生效）")
    private Integer parentChunkSize;

    @Schema(description = "子块大小（可空，走全局配置；仅 enableParentChild=true 生效）")
    private Integer childChunkSize;

    @Schema(description = "是否启用问题生成（可空，默认关闭；开启后预览会调用 LLM 为每个子块生成问题，耗时增加）")
    private Boolean enableQuestionGen;

    @Schema(description = "每个子块生成的问题数量（1-10，默认 3；仅 enableQuestionGen=true 生效）")
    private Integer questionCount;

    @Schema(description = "是否对 Excel/CSV 启用 QA 切分（可空，默认关闭；" +
            "仅对 xls/xlsx/csv 生效，开启后每行一个切片：第一列=问题(title)，第二列=答案(content)，首行表头跳过；" +
            "不开启时 Excel/CSV 默认走表格行感知切分（多行打包、列字母标记）。" +
            "同批次的非表格文件不受影响，仍按 strategy/chunkSize 切分）")
    private Boolean qaMode;

    @Schema(description = "自定义分隔符列表的 JSON 字符串（可空，仅 strategy=legacy 生效；" +
            "如 [\"\\n\\n\",\"\\n\",\"。\"]。为空走默认 [\"\\n\\n\",\"\\n\"]，等价于按段落切分）")
    private String separatorsJson;

    /** 按文件类型分组指定解析引擎的规则 */
    @Data
    @Schema(description = "解析引擎规则")
    public static class ParserEngineRule implements Serializable {

        @Schema(description = "该规则覆盖的文件扩展名列表，如 [\"pdf\"] 或 [\"docx\",\"doc\"]")
        private List<String> fileTypes;

        @Schema(description = "解析引擎名称，如 tika/pdfbox/poi/mineru")
        private String engine;
    }
}
