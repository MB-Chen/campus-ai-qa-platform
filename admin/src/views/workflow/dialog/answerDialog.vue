<!-- 回复节点配置：引用上游变量（支持多个，Q3）或自定义文本。
     answerType=1 引用变量时：
       - 单变量：直接取该变量值回复
       - 多变量：用「回复模板」用 {{变量}} 聚合（如把 LLM1+LLM2 输出拼接） -->
<template>
  <div class="opt-form">
    <div class="title-row">
      <n-icon :component="meta.icon" :color="meta.color" :size="20" />
      <span class="node-name">回复</span>
    </div>

    <div class="set-content-box">
      <div class="section-title">回复内容</div>
      <div class="row-box">
        <span>内容来源</span>
        <n-radio-group v-model:value="form.answerType" style="margin-left: 16px">
          <n-radio :value="1">引用变量</n-radio>
          <n-radio :value="2">自定义</n-radio>
        </n-radio-group>
      </div>

      <div v-if="form.answerType === 1" class="ref-box">
        <div class="sub-tip" v-pre>
          可引用多个上游变量（如条件分支后的多个 LLM）。下方「回复模板」留空则按顺序拼接；
          多变量时用 <code>{{1}}</code> <code>{{2}}</code> …
          指代上方第几个变量（推荐，能区分同字段不同节点），或用
          <code>{{sys.content}}</code> 按字段名取首个命中。
        </div>
        <input-var-picker
          v-model="form.inputData"
          :options="inputOptions"
          :multiple="true"
          @update:model-value="emitChange"
        />

        <div class="section-title" style="margin-top: 12px">
          回复模板<span class="sub-tip-inline">（可选，多变量时聚合）</span>
        </div>
        <div class="tpl-hint">引用变量顺序：{{ indexedVarsText }}</div>
        <n-input
          v-model:value="form.answerTemplate"
          type="textarea"
          placeholder="留空=按顺序拼接；示例：结论一：{{1}}\n结论二：{{2}}"
          :rows="4"
          @update:value="emitChange"
        />
      </div>

      <div v-if="form.answerType === 2">
        <n-input
          v-model:value="form.answer"
          type="textarea"
          placeholder="自定义回复内容"
          :rows="4"
          style="margin-top: 10px"
          @update:value="emitChange"
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
  </div>
</template>

<script setup>
  import { ref, computed } from 'vue';
  import { iconComponent } from '@/views/workflow/icons/index.js';
  import InputVarPicker from '@/views/workflow/components/InputVarPicker.vue';

  const meta = iconComponent('answer-node');
  const props = defineProps({
    formData: { type: Object, default: () => ({}) },
    inputOptions: { type: Array, default: () => [] },
  });
  const emit = defineEmits(['dataChange']);

  const form = ref(props.formData);
  // 兜底：answerTemplate 字段可能旧数据没有
  if (form.value.answerTemplate == null) {
    form.value.answerTemplate = '';
  }

  // 引用变量顺序提示：{{1}} = 节点名.字段名，{{2}} = ...
  const indexedVarsText = computed(() => {
    const v = form.value.inputData;
    if (!Array.isArray(v) || v.length === 0) return '（先添加上游变量）';
    return v
      .map((it, i) => {
        if (!it || !it.nodeId) return `{{${i + 1}}}=（未选）`;
        const node = (props.inputOptions || []).find((o) => o.value === it.nodeId);
        return `{{${i + 1}}}=${node?.label || it.nodeId}.${it.field || '?'}`;
      })
      .join('，');
  });

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
    margin-top: 10px;
    border-radius: 5px;
  }
  .ref-box {
    margin-top: 10px;
    background: #fff;
    padding: 10px;
    border-radius: 5px;
  }
  .sub-tip {
    font-size: 12px;
    color: #888;
    margin-bottom: 8px;
    line-height: 1.6;
  }
  .sub-tip-inline {
    font-weight: normal;
    font-size: 12px;
    color: #999;
  }
  .tpl-hint {
    font-size: 12px;
    color: #6172f3;
    margin-bottom: 6px;
    line-height: 1.5;
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
</style>
