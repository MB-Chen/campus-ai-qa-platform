<!-- 条件分支节点配置：IF/ELSEIF 分支组，每组多个条件（AND/OR），分支增减动态加减右侧端口 -->
<template>
  <div class="opt-form">
    <div class="title-row">
      <n-icon :component="meta.icon" :color="meta.color" :size="20" />
      <span class="node-name">条件分支</span>
    </div>

    <div class="set-content-box">
      <div class="section-title">条件分支</div>
      <div class="branch-box" v-for="(branch, bIndex) in form.ifBranch" :key="bIndex">
        <div class="branch-head">
          <span class="branch-tag">{{ bIndex === 0 ? 'IF' : 'ELSEIF' }}</span>
          <n-radio-group v-model:value="branch.switch" size="small">
            <n-radio :value="1">AND</n-radio>
            <n-radio :value="2">OR</n-radio>
          </n-radio-group>
        </div>

        <div
          class="cond-row"
          v-for="(cond, cIndex) in branch.data"
          :key="cIndex"
        >
          <input-var-picker
            v-model="cond.input"
            :options="inputOptions"
            @update:model-value="emitChange"
            style="flex: 1"
          />
          <n-select
            v-model:value="cond.tips"
            :options="options"
            placeholder="操作符"
            @update:value="emitChange"
            style="width: 120px; margin-left: 5px"
          />
          <n-input
            v-if="cond.tips > 2"
            v-model:value="cond.value"
            @update:value="emitChange"
            style="width: 90px; margin-left: 5px"
            placeholder="值"
          />
          <n-button
            v-if="!(bIndex === 0 && cIndex === 0)"
            quaternary
            type="error"
            size="small"
            @click="delCond(bIndex, cIndex)"
            >删</n-button
          >
        </div>

        <div class="add-cond" @click="addCond(bIndex)">+ 添加条件</div>
      </div>

      <div class="add-branch" @click="addBranch">+ 添加 ELSEIF</div>

      <div class="branch-box else-box">
        <span class="branch-tag">ELSE</span>
      </div>
    </div>
  </div>
</template>

<script setup>
  import { ref, watch } from 'vue';
  import initConfig from '@/views/workflow/initConfig.js';
  import { iconComponent } from '@/views/workflow/icons/index.js';
  import InputVarPicker from '@/views/workflow/components/InputVarPicker.vue';

  const meta = iconComponent('switch-node');
  const props = defineProps({
    formData: { type: Object, default: () => ({}) },
    inputOptions: { type: Array, default: () => [] },
  });
  const emit = defineEmits(['dataChange', 'portAdd', 'portDel', 'portUpdate']);

  const form = ref(props.formData);
  // 外部 formData 变化时（如切换选中节点）同步更新 form，确保回填正确
  watch(
    () => props.formData,
    (newVal) => {
      if (newVal) form.value = newVal;
    },
    { deep: true },
  );
  // 调试：确认 inputOptions 是否正确传入
  watch(
    () => props.inputOptions,
    (v) => {
      console.log('[switchDialog] inputOptions 更新:', JSON.stringify(v)?.slice(0, 200), '长度:', v?.length);
    },
    { immediate: true },
  );
  const options = JSON.parse(JSON.stringify(initConfig.switchOptions)).map((o) => ({
    label: o.label,
    value: o.type,
  }));

  function addCond(bIndex) {
    form.value.ifBranch[bIndex].data.push({
      input: [],
      tips: '',
      value: '',
    });
    emit('portUpdate', form.value, bIndex);
    emitChange();
  }

  function delCond(bIndex, cIndex) {
    form.value.ifBranch[bIndex].data.splice(cIndex, 1);
    if (form.value.ifBranch[bIndex].data.length === 0) {
      form.value.ifBranch.splice(bIndex, 1);
      emit('portDel', form.value);
    } else {
      emit('portUpdate', form.value, bIndex);
    }
    emitChange();
  }

  function addBranch() {
    form.value.ifBranch.push({
      type: 'elseif',
      switch: 1,
      data: [{ input: [], tips: '', value: '' }],
    });
    emit('portAdd', form.value);
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
  .branch-box {
    margin-top: 10px;
    background: #fff;
    padding: 12px;
    border-radius: 5px;
  }
  .branch-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
  }
  .branch-tag {
    font-weight: 600;
    color: #6172f3;
  }
  .cond-row {
    display: flex;
    align-items: center;
    margin-top: 8px;
  }
  .add-cond {
    margin-top: 10px;
    cursor: pointer;
    color: #18a058;
    font-size: 13px;
  }
  .add-branch {
    margin-top: 10px;
    cursor: pointer;
    color: #18a058;
    font-size: 13px;
    text-align: center;
    padding: 8px;
    background: #fff;
    border-radius: 5px;
  }
  .else-box {
    text-align: center;
  }
</style>
