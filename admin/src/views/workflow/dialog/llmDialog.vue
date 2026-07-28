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
        filterable
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

    <!-- ★ 重排配置：rerank 统一在 LLM 节点做。
         检索节点只召回候选，本节点融合后按重排模型打分取 top，避免各检索节点各自重排的重复/不一致。 -->
    <div class="set-content-box">
      <div class="section-title">召回重排</div>
      <div class="slider-row">
        <span class="slider-label">重排模型</span>
        <n-select
          v-model:value="form.rerankModelId"
          :options="rerankOptions"
          placeholder="不使用重排"
          clearable
          :loading="rerankLoading"
          @update:value="emitChange"
          style="flex: 1; margin-left: 12px"
        />
      </div>
      <div class="slider-row">
        <span class="slider-label">重排数量</span>
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
      <div class="prompt-tip" v-pre>用 {{ 变量 }} 引用上游节点输出，留空则用原始问题</div>
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
  import { getModelList, getRerankModelList, MODEL_TYPE } from '@/api/system/aiModel';
  import InputVarPicker from '@/views/workflow/components/InputVarPicker.vue';

  const meta = iconComponent('llm-node');

  const props = defineProps({
    formData: { type: Object, default: () => ({}) },
    inputOptions: { type: Array, default: () => [] },
  });
  const emit = defineEmits(['dataChange']);

  const form = ref(props.formData);
  const modelOptions = ref([]);
  const rerankOptions = ref([]);
  const rerankLoading = ref(false);
  const varPick = ref([]);

  // 初始化：把字符串 modelId 转成 number 给 n-select
  if (form.value.modelInfo && form.value.modelInfo.modelId) {
    const id = Number(form.value.modelInfo.modelId);
    form.value.modelInfo.modelId = isNaN(id) ? null : id;
  }

  // ★ rerank 配置兜底：旧节点 data 可能没有 rerankModelId/topRank 字段（迁移自 dataset 节点前），
  //   补默认值；rerankModelId 空串归一化为 null，让 n-select 能显示 placeholder。
  if (form.value.rerankModelId === undefined || form.value.rerankModelId === '') {
    form.value.rerankModelId = null;
  }
  if (form.value.topRank == null) {
    form.value.topRank = 3;
  }

  // 模型选项：与知识图谱页一致，label 显示「配置名 / 首个具体模型」，
  // 并把完整记录挂在 raw 上，便于选择时取真实模型名
  function toModelOption(m) {
    const firstModel =
      String(m.models || '')
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean)[0] || '';
    return {
      label: `${m.name || `模型${m.id}`}${firstModel ? ' / ' + firstModel : ''}`,
      value: m.id,
      raw: m,
    };
  }

  // ★ 重排模型拉平：一个 ai_model 的 models 逗号分隔时，拆成每个具体模型一条 option。
  //   与 datasetDialog/AgentSaveModal 的 toRerankModelOptions 一致，便于选择具体子模型。
  function toRerankModelOptions(models) {
    const opts = [];
    for (const m of models) {
      const names = String(m.models || '')
        .split(',')
        .map((s) => s.trim())
        .filter((s) => s.length > 0);
      if (names.length === 0) {
        opts.push({ label: `${m.name || ''}（未配置模型名）`, value: String(m.id) });
        continue;
      }
      for (const n of names) {
        opts.push({ label: `${m.name || ''} / ${n}`, value: String(m.id) });
      }
    }
    return opts;
  }

  onMounted(async () => {
    try {
      const res = await getModelList({ type: MODEL_TYPE.CHAT, status: 1 });
      if (res && res.code === 0 && Array.isArray(res.data)) {
        modelOptions.value = res.data.filter((m) => m && m.id != null).map(toModelOption);
      }
    } catch (e) {
      // 忽略
    }
    // 重排模型列表（rerank 统一在 LLM 节点配置）
    rerankLoading.value = true;
    try {
      const res = await getRerankModelList();
      if (res && res.code === 0 && Array.isArray(res.data)) {
        rerankOptions.value = toRerankModelOptions(res.data.filter((m) => m && m.id != null));
      }
    } catch (e) {
      // 忽略
    } finally {
      rerankLoading.value = false;
    }
  });

  function onModelChange(val) {
    form.value.modelInfo.modelId = val;
    const opt = modelOptions.value.find((x) => x.value === val);
    // modelName 取首个具体模型名（与知识图谱一致），而非配置显示名
    const firstModel =
      opt && opt.raw
        ? String(opt.raw.models || '')
            .split(',')
            .map((s) => s.trim())
            .filter(Boolean)[0] || ''
        : '';
    form.value.modelInfo.modelName = firstModel;
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
