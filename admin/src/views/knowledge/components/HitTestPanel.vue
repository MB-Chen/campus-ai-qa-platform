<template>
  <n-spin :show="loading">
    <n-form label-placement="left" label-width="90px">
      <n-form-item label="检索模式">
        <n-radio-group v-model:value="form.mode">
          <n-radio-button value="embedding">向量检索</n-radio-button>
          <n-radio-button value="text">全文检索</n-radio-button>
          <n-radio-button value="mix">混合检索</n-radio-button>
        </n-radio-group>
      </n-form-item>
      <n-form-item label="测试文档">
        <n-select
          v-model:value="form.documentId"
          :options="documentOptions"
          placeholder="请选择文档"
          style="width: 320px"
        />
      </n-form-item>
      <n-form-item label="相似度阈值">
        <n-input-number
          v-model:value="form.similarity"
          :min="0"
          :max="1"
          :step="0.05"
          style="width: 200px"
          :disabled="form.mode === 'text'"
        />
        <span style="margin-left: 8px; color: #aaa; font-size: 12px">{{ similarityTip }}</span>
      </n-form-item>
      <n-form-item label="返回条数">
        <n-input-number v-model:value="form.topRank" :min="1" :max="50" style="width: 200px" />
      </n-form-item>
      <n-form-item label="查询内容">
        <n-input
          v-model:value="form.query"
          type="textarea"
          :rows="4"
          placeholder="输入要测试检索的问句"
        />
      </n-form-item>
      <n-form-item label=" ">
        <n-button type="primary" secondary :loading="loading" @click="handleTest">
          <template #icon
            ><n-icon><ThunderboltOutlined /></n-icon
          ></template>
          测试
        </n-button>
      </n-form-item>
    </n-form>

    <div class="result-section">
      <div class="result-head">检索结果（{{ results.length }} 条）</div>
      <n-empty
        v-if="!loading && results.length === 0"
        description="暂无结果，输入问句后点击「测试」"
      />
      <div class="result-list">
        <div v-for="(r, idx) in renderedResults" :key="(r.chunkId || '') + idx" class="result-item">
          <div class="result-top">
            <n-tag type="success" size="small" round>{{ formatScore(r.score) }}</n-tag>
            <span class="result-doc">{{ r.documentName || '未知文档' }}</span>
            <span class="result-chunk">{{ r.chunkId }}</span>
          </div>
          <div class="result-content md-body" v-html="r.contentHtml"></div>
        </div>
      </div>
    </div>
  </n-spin>
</template>

<script setup lang="ts">
  import { ref, reactive, computed, watch, onMounted } from 'vue';
  import { useMessage } from 'naive-ui';
  import { marked } from 'marked';
  import { ThunderboltOutlined } from '@vicons/antd';
  import {
    hitTest,
    getDocumentList,
    type HitTestResult,
    type HitTestMode,
  } from '@/api/system/knowledge';

  marked.use({ breaks: true, gfm: true });

  /** 简单 markdown → HTML（召回内容预览，与 ParagraphDrawer/UploadModal 一致，非流式无需消毒） */
  function renderMd(text: string): string {
    if (!text) return '';
    return marked.parse(text) as string;
  }

  const props = defineProps<{
    kbId: string;
  }>();

  const message = useMessage();

  const loading = ref(false);
  const results = ref<HitTestResult[]>([]);

  // 同步预算好 HTML 挂到对象上，模板只读字符串，避免在模板/computed 里直接调 marked.parse 触发递归更新告警
  const renderedResults = computed(() =>
    results.value.map((r) => ({ ...r, contentHtml: renderMd(r.content || '') }))
  );

  // 文档列表（单选，必须选一个文档才能测试——没做意图识别前不开放全库测试，避免噪声混杂）
  const documentOptions = ref<Array<{ label: string; value: string }>>([]);
  const form = reactive({
    mode: 'embedding' as HitTestMode,
    similarity: 0.5,
    topRank: 5,
    query: '',
    documentId: '' as string,
  });

  // 相似度阈值仅对向量检索生效：向量分数是余弦相似度（0~1），可用阈值过滤；
  // 全文检索分数是 ts_rank_cd，量纲不同，不能用同一阈值；混合检索走 RRF 融合，
  // 融合分也只在向量侧按阈值过滤，关键词侧不参与。
  const similarityTip = computed(() => {
    switch (form.mode) {
      case 'embedding':
        return '向量相似度低于此值将被过滤';
      case 'mix':
        return '仅对混合检索中的向量召回生效，关键词召回不参与过滤';
      case 'text':
      default:
        return '全文检索不使用相似度阈值，按返回条数截断';
    }
  });

  // 加载文档列表。严格遵守「HTTP 响应拦截器非 0 code 不 reject」约定：
  // 必须校验 res.code===0 && Array.isArray(res.data.data) 再用，避免非数组赋给 options 后 n-select 崩溃。
  async function loadDocuments() {
    if (!props.kbId) {
      documentOptions.value = [];
      form.documentId = '';
      return;
    }
    try {
      const res: any = await getDocumentList({ kbId: props.kbId, page: 1, size: 200 });
      const list =
        res && res.code === 0 && res.data && Array.isArray(res.data.data) ? res.data.data : [];
      documentOptions.value = list
        .filter((d: any) => d && d.id != null)
        .map((d: any) => ({
          label: `${d.fileName || d.id}（${d.chunkCount ?? 0} 块）`,
          value: d.id,
        }));
      // 有文档且当前未选中（或选中的不在列表里）时，自动选中第一个
      if (documentOptions.value.length > 0) {
        if (!form.documentId || !documentOptions.value.some((o) => o.value === form.documentId)) {
          form.documentId = documentOptions.value[0].value;
        }
      } else {
        form.documentId = '';
      }
    } catch (e) {
      documentOptions.value = [];
      form.documentId = '';
    }
  }

  function formatScore(score?: number) {
    if (score == null) return '-';
    return Number(score).toFixed(4);
  }

  async function handleTest() {
    if (!props.kbId) {
      message.warning('缺少知识库');
      return;
    }
    if (!form.documentId) {
      message.warning('请选择要测试的文档');
      return;
    }
    if (!form.query.trim()) {
      message.warning('请输入查询内容');
      return;
    }
    loading.value = true;
    results.value = [];
    try {
      const res: any = await hitTest({
        kbId: props.kbId,
        documentId: form.documentId,
        query: form.query.trim(),
        mode: form.mode,
        similarity: form.similarity,
        topRank: form.topRank,
      });
      if (res && res.code === 0 && Array.isArray(res.data)) {
        results.value = res.data.filter((x: any) => x && x.chunkId != null);
      } else {
        results.value = [];
        if (res && res.code !== 0) message.error(res.message || '检索失败');
      }
    } catch (e) {
      message.error('检索失败');
      results.value = [];
    } finally {
      loading.value = false;
    }
  }

  onMounted(loadDocuments);

  // 外部切换 kbId 时：重新加载文档列表 + 清空结果 + 重置文档选择
  watch(
    () => props.kbId,
    () => {
      form.documentId = '';
      results.value = [];
      loadDocuments();
    }
  );
</script>

<style lang="less" scoped>
  .result-section {
    margin-top: 16px;
    border-top: 1px solid #f0f0f0;
    padding-top: 12px;
  }
  .result-head {
    font-weight: 600;
    margin-bottom: 10px;
    color: #333;
  }
  .result-list {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }
  .result-item {
    border: 1px solid #eee;
    border-radius: 6px;
    padding: 14px 16px;
  }
  .result-top {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 6px;
  }
  .result-doc {
    font-size: 12px;
    color: #1890ff;
  }
  .result-chunk {
    font-size: 11px;
    color: #aaa;
    font-family: Consolas, Monaco, monospace;
    margin-left: auto;
  }
  .result-content {
    font-size: 13px;
    line-height: 1.6;
    color: #555;
    word-break: break-word;
  }
  /* markdown 渲染内容（与 ParagraphDrawer / UploadModal 一致） */
  .md-body {
    :deep(h1),
    :deep(h2),
    :deep(h3),
    :deep(h4),
    :deep(h5),
    :deep(h6) {
      margin: 12px 0 6px;
      font-weight: 600;
      line-height: 1.4;
      &:first-child {
        margin-top: 0;
      }
    }
    :deep(h1) {
      font-size: 18px;
    }
    :deep(h2) {
      font-size: 16px;
    }
    :deep(h3) {
      font-size: 15px;
    }
    :deep(h4) {
      font-size: 14px;
    }
    :deep(p) {
      margin: 0 0 8px;
      &:last-child {
        margin-bottom: 0;
      }
    }
    :deep(ul),
    :deep(ol) {
      margin: 4px 0 8px;
      padding-left: 20px;
    }
    :deep(li) {
      margin: 2px 0;
    }
    :deep(blockquote) {
      margin: 8px 0;
      padding: 4px 12px;
      border-left: 3px solid #ddd;
      color: #666;
    }
    :deep(code) {
      background: #f5f5f5;
      padding: 1px 4px;
      border-radius: 3px;
      font-size: 12px;
    }
    :deep(pre) {
      background: #f5f5f5;
      padding: 10px 12px;
      border-radius: 4px;
      overflow-x: auto;
      margin: 8px 0;
      code {
        background: none;
        padding: 0;
      }
    }
    :deep(table) {
      border-collapse: collapse;
      margin: 8px 0;
      width: 100%;
      th,
      td {
        border: 1px solid #e0e0e0;
        padding: 6px 8px;
        font-size: 12px;
      }
      th {
        background: #f9f9f9;
        font-weight: 600;
      }
    }
    :deep(hr) {
      border: none;
      border-top: 1px solid #eee;
      margin: 12px 0;
    }
    :deep(img) {
      max-width: 100%;
      border-radius: 4px;
    }
  }
</style>
