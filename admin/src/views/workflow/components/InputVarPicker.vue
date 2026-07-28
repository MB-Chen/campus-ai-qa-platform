<!-- 输入变量选择器。
     用 n-cascader 级联选择器复刻原版 el-cascader 的交互：
     一个框展开「上游节点 > 变量」直接选（如：开始 > sys.question）。

     数据契约（与后端一致）：
       - modelValue: Array<{nodeId, field}>
         （AnswerNode/DatasetNode/AgentNode/LlmNode/PurposeNode/SwitchNode 后端均按
          inputs.get(0).get("nodeId"/"field") 解析）
       - options: inputData.js 产出的级联结构
           [{value: nodeId, label, color, children: [{value: field, label}]}]

     实现要点：
       n-cascader 单选时 value 是「被选中节点的 value（单个值）」——和 el-cascader
       返回路径数组 [nodeId, field] 不同。为同时携带 nodeId 和 field（后端需要），
       把叶子 value 编码成复合 key "nodeId||field"，再用 keyMap 反查回 {nodeId, field}。
       这样「同名 field、不同节点」也不会冲突。
       show-path=true 让输入框显示完整路径「开始 / 用户问题」。 -->
<template>
  <div class="picker">
    <!-- 单输入模式（LLM/Dataset/Agent/Purpose/条件分支） -->
    <template v-if="!multiple">
      <n-cascader
        :value="singleKey"
        :options="cascaderOptions"
        :show-path="true"
        clearable
        filterable
        placeholder="选择上游变量"
        @update:value="onSingleChange"
        style="flex: 1"
      />
    </template>

    <!-- 多输入模式（Answer） -->
    <template v-else>
      <div class="multi-list">
        <div
          v-for="(item, idx) in multiItems"
          :key="idx"
          class="multi-row"
        >
          <n-cascader
            :value="keyOf(item)"
            :options="cascaderOptions"
            :show-path="true"
            clearable
            filterable
            placeholder="选择上游变量"
            @update:value="(v) => onMultiChange(idx, v)"
            style="flex: 1"
          />
          <n-button
            quaternary
            type="error"
            size="small"
            @click="removeRow(idx)"
            :disabled="multiItems.length === 1"
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

  const SEP = '||';

  // 把外部 modelValue 规整成 [{nodeId, field}]（兼容旧扁平 [nodeId, field]）
  function normalize(v) {
    if (!Array.isArray(v) || v.length === 0) return [];
    if (typeof v[0] === 'string') {
      return [{ nodeId: v[0] || null, field: v[1] || null }];
    }
    return v
      .filter((it) => it && (it.nodeId || it.field))
      .map((it) => ({ nodeId: it.nodeId ?? null, field: it.field ?? null }));
  }

  // 给 n-cascader 的 options：叶子 value 编码成复合 key "nodeId||field"，
  // 使单选 value 能同时携带 nodeId 与 field。
  const cascaderOptions = computed(() =>
    (props.options || []).map((node) => ({
      value: node.value,
      label: node.label,
      children: (node.children || []).map((f) => ({
        value: `${node.value}${SEP}${f.value}`,
        label: f.label,
      })),
    })),
  );

  // 复合 key → {nodeId, field} 反查表
  const keyMap = computed(() => {
    const m = {};
    (props.options || []).forEach((node) => {
      (node.children || []).forEach((f) => {
        m[`${node.value}${SEP}${f.value}`] = { nodeId: node.value, field: f.value };
      });
    });
    return m;
  });

  // {nodeId, field} → 复合 key（用于回填 cascader value）
  function keyOf(item) {
    if (!item || !item.nodeId || !item.field) return null;
    return `${item.nodeId}${SEP}${item.field}`;
  }

  // —— 单输入 ——
  const singleItems = computed(() => normalize(props.modelValue));
  const singleKey = computed(() => keyOf(singleItems.value[0]));

  function onSingleChange(val) {
    if (val == null || val === '') {
      emit('update:modelValue', []);
      return;
    }
    const found = keyMap.value[val];
    emit('update:modelValue', found ? [found] : []);
  }

  // —— 多输入（Answer） ——
  const multiItems = computed(() => {
    const norm = normalize(props.modelValue);
    return norm.length ? norm : [{ nodeId: null, field: null }];
  });

  function onMultiChange(idx, val) {
    const list = multiItems.value.map((it, i) => {
      if (i !== idx) return { ...it };
      if (val == null || val === '') return { nodeId: null, field: null };
      return keyMap.value[val] || { nodeId: null, field: null };
    });
    emit('update:modelValue', list.filter((it) => it.nodeId && it.field));
  }

  function addRow() {
    // 现有完整项 + 一个空行
    emit(
      'update:modelValue',
      [
        ...multiItems.value.map((it) => ({ ...it })),
        { nodeId: null, field: null },
      ].filter((it, i, arr) => (i === arr.length - 1 ? true : it.nodeId && it.field)),
    );
  }

  function removeRow(idx) {
    const list = multiItems.value
      .filter((_, i) => i !== idx)
      .filter((it) => it.nodeId && it.field);
    emit('update:modelValue', list.length ? list : []);
  }
</script>

<style scoped>
  .picker {
    display: flex;
    align-items: center;
    width: 100%;
  }
  .multi-list {
    display: flex;
    flex-direction: column;
    gap: 6px;
    width: 100%;
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
