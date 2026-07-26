// +----------------------------------------------------------------------
// | SparkX 基于大语言模型和编排的企业智能体开发平台
// +----------------------------------------------------------------------
// | Copyright (c) 2022~2099 http://ai.sparkshop.cn All rights reserved.
// +----------------------------------------------------------------------
// | Licensed SparkX 并不是自由软件，未经许可不能去掉 SparkX 相关版权
// +----------------------------------------------------------------------
// | Author: NickBai  <1902822973@qq.com>
// +----------------------------------------------------------------------

package sparkx.sparkshop.knowledge.config;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;

import java.util.Arrays;
import java.util.List;

/**
 * 重排打分模型装配（移植自 sparkxV2）。
 *
 * 为保证可编译且不依赖外部 Cohere 服务在线，这里实现一个基于嵌入余弦相似度的本地 ScoringModel。
 * 它遵循 LangChain4j 1.17.0 的 ScoringModel 接口契约：
 *   {@code scoreAll(List<TextSegment> passages, String query) -> Response<List<Double>>}
 *   {@code score(TextSegment passage, String query) -> Response<Double>}（默认方法，基于 scoreAll 实现）
 *
 * 若需要真实 Cohere Rerank，可替换为
 *   CohereScoringModel.builder().baseUrl(...).apiKey(...).modelName(...).build()
 */
public class CohereScoringModelConfig {

    private final RagProperties.Rerank rerank;
    private final EmbeddingModel embeddingModel;

    public CohereScoringModelConfig(RagProperties.Rerank rerank, EmbeddingModel embeddingModel) {
        this.rerank = rerank;
        this.embeddingModel = embeddingModel;
    }

    public ScoringModel scoringModel() {
        return new EmbeddingBasedScoringModel(embeddingModel);
    }

    /**
     * 基于嵌入余弦相似度的打分模型 —— 零外部依赖，保证可编译。
     */
    public static class EmbeddingBasedScoringModel implements ScoringModel {

        private final EmbeddingModel embeddingModel;

        public EmbeddingBasedScoringModel(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
        }

        /**
         * 批量打分（接口唯一抽象方法）。
         * 注意 1.17.0 的参数顺序为 (passages, query)。
         */
        @Override
        public Response<List<Double>> scoreAll(List<TextSegment> passages, String query) {
            Embedding qEmb = embeddingModel.embed(query).content();
            List<Embedding> passageEmb = embeddingModel.embedAll(passages).content();
            double[] qNorm = normalize(toDouble(qEmb));

            double[] scores = new double[passages.size()];
            for (int i = 0; i < passageEmb.size(); i++) {
                double[] pVec = normalize(toDouble(passageEmb.get(i)));
                scores[i] = cosine(qNorm, pVec);
            }
            return Response.from(Arrays.stream(scores).boxed().toList());
        }

        private static double[] toDouble(Embedding e) {
            float[] v = e.vector();
            double[] out = new double[v.length];
            for (int i = 0; i < v.length; i++) out[i] = v[i];
            return out;
        }

        private static double[] normalize(double[] v) {
            double norm = 0;
            for (double x : v) norm += x * x;
            norm = Math.sqrt(norm);
            if (norm == 0) return v;
            double[] out = new double[v.length];
            for (int i = 0; i < v.length; i++) out[i] = v[i] / norm;
            return out;
        }

        private static double cosine(double[] a, double[] b) {
            double sum = 0;
            int n = Math.min(a.length, b.length);
            for (int i = 0; i < n; i++) sum += a[i] * b[i];
            return sum;
        }
    }
}
