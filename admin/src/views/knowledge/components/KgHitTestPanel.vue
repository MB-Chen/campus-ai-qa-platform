<template>
  <n-spin :show="loading">
    <n-form label-placement="left" label-width="90px">
      <n-form-item label="测试文档">
        <n-select
          v-model:value="form.documentId"
          :options="documentOptions"
          placeholder="请选择文档（图谱按文档抽取，需选定文档）"
          style="width: 360px"
        />
        <span style="margin-left: 8px; color: #aaa; font-size: 12px">
          仅列出已开启知识图谱的文档
        </span>
      </n-form-item>
      <n-form-item label="TopK">
        <n-input-number v-model:value="form.topK" :min="1" :max="50" style="width: 200px" />
      </n-form-item>
      <n-form-item label="检索模式">
        <n-radio-group v-model:value="form.retrievalMode">
          <n-radio value="local">local（子图扩展）</n-radio>
          <n-radio value="global">global（社区摘要）</n-radio>
          <n-radio value="hybrid">hybrid（双路并行）</n-radio>
        </n-radio-group>
        <span style="margin-left: 8px; color: #aaa; font-size: 12px">
          默认读全局配置；global/hybrid 需先启用社区检测并运行社区检测
        </span>
      </n-form-item>
      <n-form-item label="查询内容">
        <n-input
          v-model:value="form.query"
          type="textarea"
          :rows="4"
          placeholder="输入要测试图谱检索的问句（如：张三在北京公司做什么？）"
        />
      </n-form-item>
      <n-form-item label=" ">
        <n-button type="primary" secondary :loading="loading" @click="handleTest">
          <template #icon
            ><n-icon><ApartmentOutlined /></n-icon
          ></template>
          图谱检索
        </n-button>
      </n-form-item>
    </n-form>

    <div class="result-section">
      <div class="result-head">
        图谱检索结果
        <n-text depth="3" style="font-size: 12px; font-weight: normal; margin-left: 8px">
          query → LLM 抽实体 → 向量召回该文档实体 → 子图扩展 → 关联 chunk
        </n-text>
      </div>

      <n-empty
        v-if="!loading && !result"
        description="选择文档并输入问句后点击「图谱检索」，查看实体召回全链路"
      />

      <template v-else-if="result">
        <n-alert
          :type="result.message ? 'warning' : 'info'"
          :show-icon="true"
          v-if="result.message"
          style="margin-bottom: 12px"
        >
          {{ result.message }}
        </n-alert>

        <!-- 当前检索模式回显 -->
        <div class="kg-block" style="margin-top: 0">
          <n-tag :type="result.mode === 'hybrid' ? 'warning' : result.mode === 'global' ? 'success' : 'info'" size="small" round>
            当前检索模式：{{ result.mode }}
          </n-tag>
          <n-text depth="3" style="font-size: 12px; margin-left: 8px">
            {{ modeDescription(result.mode) }}
          </n-text>
        </div>

        <!-- 1. 抽取的实体（所有模式都跑抽实体 + 实体链接，作为观察链路的前置） -->
        <div class="kg-block">
          <n-text depth="2" style="font-weight: 600">
            ① Query 抽取的实体（{{ (result.extractedEntities || []).length }}）
          </n-text>
          <n-space :size="6" style="margin-top: 6px">
            <n-tag
              v-for="(e, i) in result.extractedEntities || []"
              :key="i"
              size="small"
              type="info"
            >
              {{ e }}
            </n-tag>
            <n-text
              v-if="(result.extractedEntities || []).length === 0"
              depth="3"
              style="font-size: 12px"
            >
              无
            </n-text>
          </n-space>
        </div>

        <!-- 2. 命中的 kg_entity（global-only 模式下隐藏：local 路未启用，实体匹配仅作中间观察） -->
        <div class="kg-block" v-if="result.mode !== 'global'">
          <n-text depth="2" style="font-weight: 600">
            ② 向量召回的实体（{{ (result.matchedEntities || []).length }}）
          </n-text>
          <n-table :bordered="false" :single-line="false" size="small" style="margin-top: 6px">
            <thead>
              <tr>
                <th>ID</th>
                <th>名称</th>
                <th>规范名</th>
                <th>类型</th>
                <th>分数</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="m in result.matchedEntities || []" :key="m.id">
                <td>{{ m.id }}</td>
                <td>{{ m.name }}</td>
                <td>{{ m.canonical_name || m.canonicalName }}</td>
                <td>
                  <n-tag size="tiny">{{ m.entity_type || m.entityType || '-' }}</n-tag>
                </td>
                <td>{{ Number(m.score || 0).toFixed(3) }}</td>
              </tr>
              <tr v-if="(result.matchedEntities || []).length === 0">
                <td colspan="5" style="text-align: center; color: #999">无匹配实体</td>
              </tr>
            </tbody>
          </n-table>
        </div>

        <!-- 3. 关联的 chunks（local 路结果；global-only 模式下隐藏） -->
        <div class="kg-block" v-if="result.mode !== 'global'">
          <n-text depth="2" style="font-weight: 600">
            ③ {{ result.mode === 'hybrid' ? 'local 路' : '' }}子图扩展关联的 chunks（{{
              (result.relatedChunks || []).length
            }}，graphScore 排序）
          </n-text>
          <!-- 3. 关联的 chunks：放弃 n-list（整体外框+分割线导致边线相连），改用独立卡片 -->
          <div
            v-if="(result.relatedChunks || []).length > 0"
            class="kg-chunk-list"
            style="margin-top: 6px"
          >
            <div v-for="(c, i) in renderedRelatedChunks" :key="i" class="kg-chunk-card">
              <div class="kg-chunk-header">
                <n-tag size="tiny" type="success"
                  >score {{ Number(c.graphScore || c.graph_score || 0).toFixed(2) }}</n-tag
                >
                <n-text code style="font-size: 12px">{{ c.id || c.chunkId }}</n-text>
              </div>
              <div class="kg-chunk-content md-body" v-html="c.contentHtml"></div>
            </div>
          </div>
          <n-text v-else depth="3" style="font-size: 12px; display: block; margin-top: 6px">
            无关联 chunks（可能是该文档图谱为空或未抽取）
          </n-text>
        </div>

        <!-- 4. 社区摘要召回（global 路结果；仅 global/hybrid 模式显示） -->
        <div class="kg-block" v-if="result.mode === 'global' || result.mode === 'hybrid'">
          <n-text depth="2" style="font-weight: 600">
            ④ {{ result.mode === 'hybrid' ? 'global 路' : '' }}社区摘要召回（{{
              (result.relatedCommunities || []).length
            }} 个社区，按命中实体数排序）
          </n-text>
          <div
            v-if="(result.relatedCommunities || []).length > 0"
            class="kg-chunk-list"
            style="margin-top: 6px"
          >
            <div v-for="(c, i) in renderedRelatedCommunities" :key="i" class="kg-chunk-card">
              <div class="kg-chunk-header">
                <n-tag size="tiny" type="warning">社区 {{ c.communityId }}</n-tag>
                <n-tag size="tiny" type="info">命中实体 {{ c.hitEntities ?? 0 }}</n-tag>
              </div>
              <div class="kg-chunk-content md-body" v-html="c.summaryHtml"></div>
            </div>
          </div>
          <n-text v-else depth="3" style="font-size: 12px; display: block; margin-top: 6px">
            无社区摘要召回（可能是社区检测未运行 / 摘要未生成，或 query 实体未命中任何社区成员）
          </n-text>
        </div>
      </template>
    </div>
  </n-spin>
</template>

<script setup lang="ts">
  import { ref, reactive, computed, watch, onMounted } from 'vue';
  import { useMessage } from 'naive-ui';
  import { marked } from 'marked';
  import { ApartmentOutlined } from '@vicons/antd';
  import { getDocumentList } from '@/api/system/knowledge';
  import { kgHitTest, getKgConfig } from '@/api/system/knowledgeGraph';

  marked.use({ breaks: true, gfm: true });

  /** 简单 markdown → HTML（与 ParagraphDrawer/UploadModal/HitTestPanel 一致，非流式无需消毒） */
  function renderMd(text: string): string {
    if (!text) return '';
    return marked.parse(text) as string;
  }

  const props = defineProps<{
    kbId: string;
  }>();

  const message = useMessage();

  const loading = ref(false);
  const result = ref<any>(null);

  // 同步预算好 HTML 挂到对象上，模板只读字符串，避免在模板/computed 里直接调 marked.parse 触发递归更新告警
  const renderedRelatedChunks = computed(() =>
    (result.value?.relatedChunks || []).map((c: any) => ({ ...c, contentHtml: renderMd(c.content || '') }))
  );

  // global 路社区摘要 markdown 渲染（同上，预算 HTML）
  const renderedRelatedCommunities = computed(() =>
    (result.value?.relatedCommunities || []).map((c: any) => ({ ...c, summaryHtml: renderMd(c.summary || '') }))
  );

  /** 检索模式说明文案（结果区回显用） */
  function modeDescription(mode: string): string {
    if (mode === 'global') return '社区摘要召回（KB 级，宏观/全局问题）';
    if (mode === 'hybrid') return 'local 子图扩展 + global 社区摘要 双路并行';
    return '向量召回实体 → 子图扩展取 chunk（精确实体相关问题）';
  }

  // 文档列表：只列出已开启知识图谱（kgEnabled=1）的文档
  const documentOptions = ref<Array<{ label: string; value: string }>>([]);
  const form = reactive({
    documentId: '' as string,
    topK: 10,
    query: '',
    retrievalMode: 'local' as 'local' | 'global' | 'hybrid',
  });

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
      // 只列已开启知识图谱的文档
      documentOptions.value = list
        .filter((d: any) => d && d.id != null && (d.kgEnabled ?? 2) === 1)
        .map((d: any) => ({
          label: `${d.fileName || d.id}（${d.chunkCount ?? 0} 块）`,
          value: d.id,
        }));
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
    result.value = null;
    try {
      const res: any = await kgHitTest({
        kbId: props.kbId,
        documentId: form.documentId,
        query: form.query.trim(),
        topK: form.topK,
        retrievalMode: form.retrievalMode,
      });
      if (res && res.code === 0) {
        result.value = res.data;
      } else {
        message.error(res?.message || '图谱检索失败');
      }
    } catch (e: any) {
      message.error(e?.message || '图谱检索失败');
    } finally {
      loading.value = false;
    }
  }

  /** 读取全局 kg_config.retrieval_mode 作为面板默认模式（用户可临时覆盖，不改全局配置） */
  async function loadDefaultMode() {
    try {
      const res: any = await getKgConfig();
      const m = res && res.code === 0 && res.data ? res.data.retrievalMode : null;
      if (m === 'local' || m === 'global' || m === 'hybrid') {
        form.retrievalMode = m;
      }
    } catch {
      // 静默：读取失败保持 local 默认
    }
  }

  onMounted(() => {
    loadDocuments();
    loadDefaultMode();
  });

  watch(
    () => props.kbId,
    () => {
      form.documentId = '';
      result.value = null;
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
  .kg-block {
    margin-top: 12px;
  }
  /* 每个片段是独立卡片，互不相连（之前用 n-list 整体外框+分割线，边线会连在一起） */
  .kg-chunk-list {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
  .kg-chunk-card {
    border: 1px solid #eee;
    border-radius: 6px;
    padding: 12px 16px;
    background: #fff;
    transition: border-color 0.2s;
    &:hover {
      border-color: #d4e8d8;
    }
  }
  .kg-chunk-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
  }
  .kg-chunk-content {
    font-size: 13px;
    line-height: 1.6;
    color: #555;
    word-break: break-word;
  }
  /* markdown 渲染内容（与 ParagraphDrawer / UploadModal / HitTestPanel 一致） */
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
