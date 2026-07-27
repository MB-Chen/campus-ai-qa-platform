<!-- LLM 节点配置：模型/温度/记忆/角色/用户提示词（提示词支持 {{变量}} 插入） -->
<template>
  <div class="opt-form">
    <div class="title-row">
      <n-icon :component="meta.icon" :color="meta.color" :size="20" />
      <span class="node-name">LLM</span>
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
        label-field="label"
        value-field="value"
        @update:value="onModelChange"
        style="width: 100%"
      />
      <div class="slider-row">
        <span class="slider-label">温度</span>
        <n-slider
          v-model:value="form.modelInfo.temperature"
          :step="0.01"
          :min="0"
          :max="2"
          @update:value="emitChange"
          style="flex: 1; margin: 0 12px"
        />
        <span class="slider-val">{{ form.modelInfo.temperature }}</span>
      </div>
      <div class="slider-row">
        <span class="slider-label">上下文轮数</span>
        <n-slider
          v-model:value="form.memory"
          :min="0"
          :max="10"
          @update:value="emitChange"
          style="flex: 1; margin: 0 12px"
        />
        <span class="slider-val">{{ form.memory }}</span>
      </div>
    </div>

    <div class="set-content-box">
      <div class="section-title">角色设置（System）</div>
      <n-input
        v-model:value="form.systemMsg"
        type="textarea"
        placeholder="角色设置（系统提示词）"
        :rows="4"
        @update:value="emitChange"
      />
    </div>

    <div class="set-content-box">
      <div class="section-title-row">
        <span class="section-title">用户提示词</span>
        <n-popover trigger="click" placement="bottom" :width="380">
          <template #trigger>
            <n-button size="small" quaternary type="primary">+ 插入变量</n-button>
          </template>
          <div style="padding: 8px">
            <n-empty
              v-if="!inputOptions || inputOptions.length === 0"
              description="暂无可选上游变量"
              size="small"
            />
            <input-var-picker
              v-else
              v-model="varPick"
              :options="inputOptions"
              @update:model-value="insertVar"
            />
          </div>
        </n-popover>
      </div>
      <div class="prompt-tip">用 {{ '{{变量}}' }} 引用上游节点输出，留空则用原始问题</div>
      <n-input
        v-model:value="form.userPrompt"
        type="textarea"
        placeholder="用户提示词，如：根据 {{sys.result}} 回答 {{sys.question}}"
        :rows="5"
        @update:value="emitChange"
      />
    </div>
  </div>
</template>

<script setup>
  import { ref, onMounted } from 'vue';
  import { iconComponent } from '@/views/workflow/icons/index.js';
  import { getModelList } from '@/api/system/aiModel';
  import InputVarPicker from '@/views/workflow/components/InputVarPicker.vue';

  const meta = iconComponent('llm-node');

  const props = defineProps({
    formData: { type: Object, default: () => ({}) },
    inputOptions: { type: Array, default: () => [] },
  });
  const emit = defineEmits(['dataChange']);

  const form = ref(props.formData);
  const modelOptions = ref([]);
  const varPick = ref([]);

  // 初始化：把字符串 modelId 转成 number 给 n-select
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
    } catch (e) {
      // 忽略
    }
  });

  function onModelChange(val) {
    form.value.modelInfo.modelId = val;
    const m = modelOptions.value.find((x) => x.value === val);
    form.value.modelInfo.modelName = m ? m.label : '';
    emitChange();
  }

  function insertVar(val) {
    // val 是 [{nodeId, field}]（picker 发出的数组）
    const item = Array.isArray(val) && val.length > 0 ? val[0] : null;
    if (item && item.field) {
      form.value.userPrompt = (form.value.userPrompt || '') + ` {{${item.field}}}`;
      emitChange();
    }
    varPick.value = [];
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
  .section-title-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
  }
  .prompt-tip {
    font-size: 12px;
    color: #999;
    margin-bottom: 8px;
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
    width: 90px;
    font-size: 13px;
    color: #1a1a1a;
  }
  .slider-val {
    width: 36px;
    text-align: right;
    font-size: 13px;
    color: #6172f3;
  }
</style>
