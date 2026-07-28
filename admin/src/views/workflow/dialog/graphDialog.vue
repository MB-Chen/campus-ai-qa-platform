<!-- 知识图谱节点配置：查询入参 + 绑定知识库 + 绑定文档（限定该文档的知识图谱）+ 召回数 -->
<template>
  <div class="opt-form">
    <div class="title-row">
      <n-icon :component="meta.icon" :color="meta.color" :size="20" />
      <span class="node-name">知识图谱</span>
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
      <div class="section-title">绑定知识库与文档</div>
      <n-select
        v-model:value="form.kbId"
        :options="kbOptions"
        filterable
        placeholder="选择知识库"
        :loading="kbLoading"
        @update:value="onKbChange"
        style="width: 100%"
      />
      <div class="kb-doc-item" v-if="form.kbId">
        <div class="kb-doc-head">
          <n-icon :component="meta.icon" :color="meta.color" :size="14" />
          <span class="kb-doc-name">{{ getKbName(form.kbId) }}</span>
          <n-text depth="3" style="font-size: 12px">请选择已开启知识图谱的文档</n-text>
        </div>
        <n-select
          v-model:value="form.docIds"
          :options="docOptions"
          multiple
          filterable
          placeholder="选择文档（可多选）"
          :loading="docLoading"
          max-tag-count="6"
          @update:value="emitChange"
          style="width: 100%; margin-top: 4px"
        />
      </div>
      <div class="slider-row">
        <span class="slider-label">召回数量</span>
        <n-slider
          v-model:value="form.topRank"
          :min="1"
          :max="20"
          @update:value="emitChange"
          style="flex: 1; margin: 0 12px"
        />
        <span class="slider-val">{{ form.topRank }}</span>
      </div>
      <n-alert type="info" :show-icon="false" style="margin-top: 10px; font-size: 12px">
        多个检索节点（知识检索 / 知识图谱）同时连到同一 LLM 时，会自动做 RRF 多源融合后再喂给大模型。
      </n-alert>
    </div>
  </div>
</template>

<script setup>
  import { ref, onMounted } from 'vue';
  import { iconComponent } from '@/views/workflow/icons/index.js';
  import { getKbList, getDocumentList } from '@/api/system/knowledge';
  import InputVarPicker from '@/views/workflow/components/InputVarPicker.vue';

  const meta = iconComponent('graph-node');
  const props = defineProps({
    formData: { type: Object, default: () => ({}) },
    inputOptions: { type: Array, default: () => [] },
  });
  const emit = defineEmits(['dataChange']);

  const form = ref(props.formData);
  const kbOptions = ref([]);
  const docOptions = ref([]);
  const kbLoading = ref(false);
  const docLoading = ref(false);

  // 保证 docIds 是数组
  if (!Array.isArray(form.value.docIds)) {
    form.value.docIds = [];
  }

  onMounted(async () => {
    kbLoading.value = true;
    try {
      const res = await getKbList({ page: 1, size: 500 });
      if (res && res.code === 0 && res.data && Array.isArray(res.data.data)) {
        kbOptions.value = res.data.data
          .filter((kb) => kb && kb.id)
          .map((kb) => ({ label: kb.name || '未命名', value: kb.id }));
      }
    } catch (e) {} finally {
      kbLoading.value = false;
    }
    if (form.value.kbId) {
      loadDocOptions(form.value.kbId);
    }
  });

  function getKbName(kbId) {
    const opt = kbOptions.value.find((k) => k.value === kbId);
    return opt ? opt.label : kbId;
  }

  async function loadDocOptions(kbId) {
    if (!kbId) return;
    docLoading.value = true;
    try {
      const res = await getDocumentList({ kbId, page: 1, size: 200 });
      const list =
        res && res.code === 0 && res.data && Array.isArray(res.data.data) ? res.data.data : [];
      // 优先只展示已开启知识图谱（kgEnabled=1）的文档；接口未返回该字段时退化为全部
      const kgDocs = list.filter((d) => d.kgEnabled === 1);
      const usable = kgDocs.length > 0 ? kgDocs : list;
      docOptions.value = usable
        .filter((d) => d && d.id)
        .map((d) => ({ label: d.fileName || d.id, value: d.id }));
    } catch (e) {
      docOptions.value = [];
    } finally {
      docLoading.value = false;
    }
  }

  function onKbChange(kbId) {
    // 切换知识库时清空已选文档
    form.value.docIds = [];
    emitChange();
    if (kbId) {
      loadDocOptions(kbId);
    } else {
      docOptions.value = [];
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
    background: #722ed1;
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
    border-left: 3px solid #722ed1;
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
    color: #722ed1;
  }
</style>
