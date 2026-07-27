<!-- 意图分类节点配置：查询内容入参 + 模型 + 分类清单（分类增减会动态加减右侧端口） -->
<template>
  <div class="opt-form">
    <div class="title-row">
      <n-icon :component="meta.icon" :color="meta.color" :size="20" />
      <span class="node-name">意图分类</span>
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
      <div class="section-title">模型</div>
      <n-select
        v-model:value="form.modelInfo.modelId"
        :options="modelOptions"
        placeholder="选择对话模型"
        @update:value="onModelChange"
        style="width: 100%"
      />
      <div class="slider-row">
        <span class="slider-label">温度</span>
        <n-slider
          v-model:value="form.modelInfo.temperature"
          :min="0"
          :max="2"
          :step="0.01"
          @update:value="emitChange"
          style="flex: 1; margin: 0 12px"
        />
        <span class="slider-val">{{ form.modelInfo.temperature }}</span>
      </div>
    </div>

    <div class="set-content-box">
      <div class="section-title">意图分类</div>
      <div class="param-data">
        <div
          class="cate-row"
          v-for="(item, index) in form.cateList"
          :key="index"
        >
          <n-input v-model:value="item.name" placeholder="请输入分类名" @update:value="emitChange" />
          <n-button
            v-if="index > 0"
            quaternary
            type="error"
            @click="delCate(index)"
          >
            删除
          </n-button>
          <div v-else style="width: 72px"></div>
        </div>
        <div class="add-btn" @click="addCate">+ 添加分类</div>
      </div>
    </div>
  </div>
</template>

<script setup>
  import { ref, onMounted } from 'vue';
  import { iconComponent } from '@/views/workflow/icons/index.js';
  import { getModelList } from '@/api/system/aiModel';
  import InputVarPicker from '@/views/workflow/components/InputVarPicker.vue';

  const meta = iconComponent('purpose-node');
  const props = defineProps({
    formData: { type: Object, default: () => ({}) },
    inputOptions: { type: Array, default: () => [] },
  });
  const emit = defineEmits(['dataChange', 'portAdd', 'portDel']);

  const form = ref(props.formData);
  const modelOptions = ref([]);

  // modelId 转 number
  if (form.value.modelInfo && form.value.modelInfo.modelId) {
    const id = Number(form.value.modelInfo.modelId);
    form.value.modelInfo.modelId = isNaN(id) ? null : id;
  }

  onMounted(async () => {
    try {
      const res = await getModelList({ type: 1, status: 1 });
      if (res && res.code === 0 && Array.isArray(res.data)) {
        modelOptions.value = res.data
          .filter((m) => m && m.id != null)
          .map((m) => ({ label: m.name || `模型${m.id}`, value: m.id }));
      }
    } catch (e) {}
  });

  function onModelChange(val) {
    form.value.modelInfo.modelId = val;
    const m = modelOptions.value.find((x) => x.value === val);
    form.value.modelInfo.modelName = m ? m.label : '';
    emitChange();
  }
  function addCate() {
    form.value.cateList.push({ name: '' });
    emit('portAdd', form.value);
    emitChange();
  }
  function delCate(index) {
    form.value.cateList.splice(index, 1);
    emit('portDel', form.value);
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
    width: 60px;
    font-size: 13px;
  }
  .slider-val {
    width: 36px;
    text-align: right;
    font-size: 13px;
    color: #6172f3;
  }
  .cate-row {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 10px;
  }
  .add-btn {
    width: 100%;
    height: 32px;
    line-height: 32px;
    text-align: center;
    background: #fff;
    border-radius: 5px;
    color: #98a2b2;
    cursor: pointer;
    margin-top: 6px;
  }
  .add-btn:hover {
    color: #18a058;
  }
</style>
