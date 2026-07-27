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
        @update:value="onKbChange"
        style="width: 100%"
      />
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
      <div class="slider-row">
        <span class="slider-label">重排模型</span>
        <n-select
          v-model:value="form.rerankModelId"
          :options="rerankOptions"
          placeholder="不使用重排"
          clearable
          @update:value="emitChange"
          style="flex: 1; margin-left: 12px"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
  import { ref, onMounted } from 'vue';
  import { iconComponent } from '@/views/workflow/icons/index.js';
  import { getRerankModelList } from '@/api/system/aiModel';
  import { getKbList } from '@/api/system/knowledge';
  import InputVarPicker from '@/views/workflow/components/InputVarPicker.vue';

  const meta = iconComponent('dataset-node');
  const props = defineProps({
    formData: { type: Object, default: () => ({}) },
    inputOptions: { type: Array, default: () => [] },
  });
  const emit = defineEmits(['dataChange']);

  const form = ref(props.formData);
  const kbOptions = ref([]);
  const rerankOptions = ref([]);
  // 选中知识库 id 列表（回显）
  const selectedKbIds = ref(
    Array.isArray(form.value.datasets)
      ? form.value.datasets.map((d) => d.datasetId).filter(Boolean)
      : [],
  );

  onMounted(async () => {
    // 知识库列表
    try {
      const res = await getKbList({ page: 1, size: 200 });
      if (res && res.code === 0 && res.data && Array.isArray(res.data.data)) {
        kbOptions.value = res.data.data
          .filter((kb) => kb && kb.id)
          .map((kb) => ({ label: kb.name || kb.id, value: kb.id }));
      }
    } catch (e) {}
    // 重排模型
    try {
      const res = await getRerankModelList();
      if (res && res.code === 0 && Array.isArray(res.data)) {
        rerankOptions.value = res.data
          .filter((m) => m && m.id != null)
          .map((m) => ({ label: m.name || `模型${m.id}`, value: String(m.id) }));
      }
    } catch (e) {}
  });

  function onKbChange(ids) {
    form.value.datasets = (ids || []).map((id) => {
      const opt = kbOptions.value.find((k) => k.value === id);
      return { datasetId: id, title: opt ? opt.label : id };
    });
    emitChange();
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
    background: #6172f3;
    padding: 3px;
    border-radius: 5px;
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
