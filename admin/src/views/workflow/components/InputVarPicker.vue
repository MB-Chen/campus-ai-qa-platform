<!-- 输入变量选择器。
     直接绑定 inputData 数组（新契约 Array<{nodeId, field}>）。
     - multiple=false（单输入节点 LLM/Dataset/Agent/Purpose）：管理 inputData[0]
     - multiple=true（Answer 等聚合节点）：管理整列表，可增删行

     options 为 inputData.js 产出的级联结构：
     [{value: nodeId, label, color, children: [{value: field, label}]}]

     向下兼容：若 inputData 是旧的扁平 [nodeId, field]，mounted 时迁移成 [{nodeId, field}]。 -->
<template>
  <div class="picker">
    <!-- 单输入模式 -->
    <template v-if="!multiple">
      <n-select
        :value="curNodeId"
        :options="nodeOptions"
        placeholder="选择上游节点"
        @update:value="onSingleNodeChange"
        style="flex: 1"
      />
      <n-select
        :value="curField"
        :options="fieldOptions"
        placeholder="选择变量"
        @update:value="onSingleFieldChange"
        :disabled="!curNodeId"
        style="flex: 1; margin-left: 8px"
      />
    </template>

    <!-- 多输入模式（Answer） -->
    <template v-else>
      <div class="multi-list">
        <div
          v-for="(item, idx) in items"
          :key="idx"
          class="multi-row"
        >
          <n-select
            :value="item.nodeId"
            :options="nodeOptions"
            placeholder="上游节点"
            @update:value="(v) => onMultiNodeChange(idx, v)"
            style="flex: 1"
          />
          <n-select
            :value="item.field"
            :options="fieldOptsFor(item.nodeId)"
            placeholder="变量"
            @update:value="(v) => onMultiFieldChange(idx, v)"
            :disabled="!item.nodeId"
            style="flex: 1; margin-left: 6px"
          />
          <n-button
            quaternary
            type="error"
            size="small"
            @click="removeRow(idx)"
            :disabled="items.length === 1"
            >删</n-button
          >
        </div>
        <div class="add-row" @click="addRow">+ 添加变量</div>
      </div>
    </template>
  </div>
</template>

<script setup>
  import { computed } from 'vue';

  const props = defineProps({
    // 绑定值：Array<{nodeId, field}>
    modelValue: { type: Array, default: () => [] },
    options: { type: Array, default: () => [] },
    multiple: { type: Boolean, default: false },
  });
  const emit = defineEmits(['update:modelValue']);

  // 把模型规整成 [{nodeId, field}]（兼容旧扁平 [nodeId, field]）
  const items = computed(() => {
    const v = props.modelValue;
    if (!Array.isArray(v) || v.length === 0) return [];
    // 旧契约：元素是字符串 [nodeId, field]
    if (typeof v[0] === 'string') {
      return [{ nodeId: v[0] || null, field: v[1] || null }];
    }
    return v.map((it) => ({
      nodeId: it.nodeId ?? null,
      field: it.field ?? null,
    }));
  });

  const curNodeId = computed(() => items.value[0]?.nodeId ?? null);
  const curField = computed(() => items.value[0]?.field ?? null);

  const nodeOptions = computed(() =>
    (props.options || []).map((o) => ({ label: o.label, value: o.value })),
  );

  function fieldOptsFor(nodeId) {
    const node = (props.options || []).find((o) => o.value === nodeId);
    return node && node.children ? node.children : [];
  }
  const fieldOptions = computed(() => fieldOptsFor(curNodeId.value));

  function emitList(list) {
    // 过滤掉不完整的项
    emit(
      'update:modelValue',
      list.filter((it) => it.nodeId && it.field),
    );
  }

  // 单输入
  function onSingleNodeChange(val) {
    emitList([{ nodeId: val, field: null }]);
  }
  function onSingleFieldChange(val) {
    emitList([{ nodeId: curNodeId.value, field: val }]);
  }

  // 多输入
  function onMultiNodeChange(idx, val) {
    const list = items.value.map((it, i) =>
      i === idx ? { nodeId: val, field: null } : { ...it },
    );
    emitList(list);
  }
  function onMultiFieldChange(idx, val) {
    const list = items.value.map((it, i) =>
      i === idx ? { nodeId: it.nodeId, field: val } : { ...it },
    );
    emitList(list);
  }
  function addRow() {
    emitList([...items.value, { nodeId: null, field: null }]);
  }
  function removeRow(idx) {
    const list = items.value.filter((_, i) => i !== idx);
    emitList(list.length ? list : [{ nodeId: null, field: null }]);
  }
</script>

<style scoped>
  .picker {
    width: 100%;
  }
  .multi-list {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
  .multi-row {
    display: flex;
    align-items: center;
  }
  .add-row {
    margin-top: 4px;
    color: #18a058;
    font-size: 13px;
    cursor: pointer;
  }
</style>
