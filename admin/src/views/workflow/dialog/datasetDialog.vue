<!-- 知识检索节点配置：查询入参 + 知识库多选 + 相似度/召回数/重排模型 -->
<template>
  <div class="opt-form">
    <div class="title-row">
      <n-icon :component="meta.icon" :color="meta.color" :size="20" />
      <span class="node-name">知识检索</span>
    </div>

    <div class="set-content-box">
      <div class="section-title">输入参数</div>
      <div class="row-box">
        <span>查询内容</span>
        <input-var-picker
          v-model="form.inputData"
          :options="inputOptions"
          @update:model-value="emitChange"
          style="margin-left: 16px; flex: 1"
        />
      </div>
    </div>

    <div class="set-content-box">
      <div class="section-title">输出参数</div>
      <div class="param-data">
        <div class="flex-center data-item" v-for="(item, index) in form.outData" :key="index">
          <div class="flex-center">
            <div class="menu-icon"><n-icon :component="meta.icon" color="#fff" :size="14" /></div>
            <div class="var-field">{{ item.field }}</div>
          </div>
          <div class="var-name">{{ item.name }}</div>
        </div>
      </div>
    </div>

    <div class="set-content-box">
      <div class="section-title">知识库</div>
      <n-select
        v-model:value="selectedKbIds"
        :options="kbOptions"
        multiple
        filterable
        placeholder="选择知识库（可多选）"
        :loading="kbLoading"
        @update:value="onKbChange"
        style="width: 100%"
      />
      <!-- 每个已选知识库单独限定文档（避免跨库向量模型不一致） -->
      <div class="kb-doc-item" v-for="kbId in selectedKbIds" :key="kbId">
        <div class="kb-doc-head">
          <n-icon :component="meta.icon" :color="meta.color" :size="14" />
          <span class="kb-doc-name">{{ getKbName(kbId) }}</span>
          <n-text depth="3" style="font-size: 12px">不选则检索该库全部文档</n-text>
        </div>
        <n-select
          :value="getKbDocIds(kbId)"
          :options="docOptionsMap[kbId] || []"
          multiple
          filterable
          placeholder="留空=检索整库；可选具体文档"
          :loading="docLoadingMap[kbId]"
          max-tag-count="6"
          @update:value="(ids) => onDocChange(kbId, ids)"
          style="width: 100%; margin-top: 4px"
        />
      </div>
      <div class="slider-row">
        <span class="slider-label">相似度</span>
        <n-slider
          v-model:value="form.similarity"
          :step="0.01"
          :min="0"
          :max="1"
          @update:value="emitChange"
          style="flex: 1; margin: 0 12px"
        />
        <span class="slider-val">{{ form.similarity }}</span>
      </div>
      <div class="slider-row">
        <span class="slider-label">召回数量</span>
        <n-slider
          v-model:value="form.topRank"
          :min="1"
          :max="10"
          @update:value="emitChange"
          style="flex: 1; margin: 0 12px"
        />
        <span class="slider-val">{{ form.topRank }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
  import { ref, onMounted } from 'vue';
  import { iconComponent } from '@/views/workflow/icons/index.js';
  import { getKbList, getDocumentList } from '@/api/system/knowledge';
  import InputVarPicker from '@/views/workflow/components/InputVarPicker.vue';

  const meta = iconComponent('dataset-node');
  const props = defineProps({
    formData: { type: Object, default: () => ({}) },
    inputOptions: { type: Array, default: () => [] },
  });
  const emit = defineEmits(['dataChange']);

  const form = ref(props.formData);
  const kbOptions = ref([]);
  const kbLoading = ref(false);
  // 每个知识库的文档选项 map：{ [kbId]: [{label, value}] }
  const docOptionsMap = ref({});
  // 每个知识库的文档加载态 map：{ [kbId]: boolean }
  const docLoadingMap = ref({});
  // 选中知识库 id 列表（回显）——与 form.datasets 保持同步
  const selectedKbIds = ref(
    Array.isArray(form.value.datasets)
      ? form.value.datasets.map((d) => d.datasetId).filter(Boolean)
      : []
  );

  // 保证每个 dataset 项有 docIds 数组（旧数据没有时补默认值）
  if (Array.isArray(form.value.datasets)) {
    form.value.datasets.forEach((d) => {
      if (!Array.isArray(d.docIds)) d.docIds = [];
    });
  }

  onMounted(async () => {
    // 知识库列表
    kbLoading.value = true;
    try {
      const res = await getKbList({ page: 1, size: 500 });
      if (res && res.code === 0 && res.data && Array.isArray(res.data.data)) {
        kbOptions.value = res.data.data
          .filter((kb) => kb && kb.id)
          .map((kb) => ({ label: kb.name || '未命名', value: kb.id }));
      }
    } catch (e) {
    } finally {
      kbLoading.value = false;
    }
    // 回显：已选知识库各自加载文档列表
    selectedKbIds.value.forEach((kbId) => loadDocOptions(kbId));
  });

  /** 取知识库显示名 */
  function getKbName(kbId) {
    const opt = kbOptions.value.find((k) => k.value === kbId);
    return opt ? opt.label : kbId;
  }

  /** 取某知识库当前选中的文档 id 列表 */
  function getKbDocIds(kbId) {
    const item = (form.value.datasets || []).find((d) => d.datasetId === kbId);
    return (item && Array.isArray(item.docIds) && [...item.docIds]) || [];
  }

  /** 加载单个知识库的文档列表（每库独立，避免跨库向量模型不一致） */
  async function loadDocOptions(kbId) {
    if (!kbId) return;
    docLoadingMap.value = { ...docLoadingMap.value, [kbId]: true };
    try {
      const res = await getDocumentList({ kbId, page: 1, size: 200 });
      const list =
        res && res.code === 0 && res.data && Array.isArray(res.data.data) ? res.data.data : [];
      docOptionsMap.value = {
        ...docOptionsMap.value,
        [kbId]: list
          .filter((d) => d && d.id)
          .map((d) => ({ label: d.fileName || d.id, value: d.id })),
      };
    } catch (e) {
      docOptionsMap.value = { ...docOptionsMap.value, [kbId]: [] };
    } finally {
      docLoadingMap.value = { ...docLoadingMap.value, [kbId]: false };
    }
  }

  function onKbChange(ids) {
    const next = ids || [];
    // 保留已选项（含其 docIds），追加新选，删除已移除的库
    const oldMap = {};
    (form.value.datasets || []).forEach((d) => {
      oldMap[d.datasetId] = d;
    });
    form.value.datasets = next.map((id) => {
      const opt = kbOptions.value.find((k) => k.value === id);
      if (oldMap[id]) return oldMap[id]; // 保留原有 docIds
      // 新增库：加载文档列表
      loadDocOptions(id);
      return { datasetId: id, title: opt ? opt.label : id, docIds: [] };
    });
    selectedKbIds.value = next;
    emitChange();
  }

  /** 某知识库的文档选择变化 */
  function onDocChange(kbId, docIds) {
    const item = (form.value.datasets || []).find((d) => d.datasetId === kbId);
    if (item) {
      item.docIds = docIds || [];
      emitChange();
    }
  }

  function emitChange() {
    emit('dataChange', form.value);
  }
</script>

<style scoped>
  .opt-form {
    width: 100%;
    background: #f4f4f4;
    border-radius: 5px;
    padding: 20px;
  }
  .set-content-box + .set-content-box {
    margin-top: 16px;
  }
  .title-row {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 16px;
  }
  .node-name {
    font-weight: bold;
  }
  .section-title {
    font-weight: 600;
    margin-bottom: 8px;
  }
  .row-box {
    display: flex;
    align-items: center;
    background: #fff;
    padding: 10px;
    border-radius: 5px;
  }
  .param-data {
    display: flex;
    flex-direction: column;
  }
  .data-item {
    padding: 10px;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
    background: #fff;
    border-radius: 5px;
  }
  .menu-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 22px;
    height: 22px;
    background: #6172f3;
    border-radius: 5px;
    flex-shrink: 0;
  }
  .var-field {
    margin-left: 10px;
    font-size: 13px;
  }
  .var-name {
    font-size: 13px;
    color: #646a73;
  }
  .slider-row {
    display: flex;
    align-items: center;
    margin-top: 10px;
    padding: 8px 10px;
    background: #fff;
    border-radius: 5px;
  }
  .kb-doc-item {
    margin-top: 10px;
    padding: 10px;
    background: #fff;
    border-radius: 5px;
    border-left: 3px solid #6172f3;
  }
  .kb-doc-head {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 4px;
  }
  .kb-doc-name {
    font-size: 13px;
    font-weight: 600;
    margin-right: 8px;
  }
  .slider-label {
    width: 80px;
    font-size: 13px;
  }
  .slider-val {
    width: 36px;
    text-align: right;
    font-size: 13px;
    color: #6172f3;
  }
</style>
