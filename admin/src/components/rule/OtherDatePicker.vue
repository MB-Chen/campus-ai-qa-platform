<template>
  <div class="other-date-picker">
    <div class="date-rows">
      <div v-for="(dateRange, index) in dateRanges" :key="index" class="date-row">
        <n-date-picker
          v-model:value="dateRange.value"
          type="daterange"
          :is-date-disabled="isDateDisabled"
          placeholder="请选择时间范围"
          style="flex: 1"
          clearable
        />
        <n-button
          size="tiny"
          type="error"
          @click="removeDateRange(index)"
        >
          <template #icon>
            <n-icon><DeleteOutlined /></n-icon>
          </template>
        </n-button>
      </div>
    </div>
    <n-button size="small" dashed type="primary" @click="addDateRange" class="add-date-btn">
      <template #icon>
        <n-icon><PlusOutlined /></n-icon>
      </template>
      添加时间范围
    </n-button>
  </div>
</template>

<script lang="ts" setup>
  import { ref, watch } from 'vue';
  import { DeleteOutlined, PlusOutlined } from '@vicons/antd';

  interface Props {
    modelValue?: string;
  }

  interface Emits {
    (e: 'update:modelValue', value: string): void;
  }

  const props = defineProps<Props>();
  const emit = defineEmits<Emits>();

  const dateRanges = ref<Array<{ value: [number, number] | null }>>([]);

  // 解析字符串值到日期范围数组
  function parseValueToRanges(value: string | undefined): Array<{ value: [number, number] | null }> {
    if (!value || value.trim() === '') {
      return [];
    }
    try {
      const parsed = JSON.parse(value);
      if (Array.isArray(parsed)) {
        return parsed.map((item: any) => ({
          value: item && item.start && item.end ? [item.start, item.end] : null,
        }));
      }
    } catch (e) {
      console.error('解析日期范围失败:', e);
    }
    return [];
  }

  // 将日期范围数组转换为字符串值
  function rangesToValue(ranges: Array<{ value: [number, number] | null }>): string {
    const validRanges = ranges
      .filter(r => r.value && r.value[0] && r.value[1])
      .map(r => ({
        start: r.value![0],
        end: r.value![1],
      }));
    return JSON.stringify(validRanges);
  }

  // 初始化日期范围
  if (props.modelValue) {
    dateRanges.value = parseValueToRanges(props.modelValue);
  }

  // 监听外部值变化
  watch(() => props.modelValue, (newValue) => {
    if (newValue) {
      const newRanges = parseValueToRanges(newValue);
      // 只有当值真正变化时才更新
      if (JSON.stringify(newRanges) !== JSON.stringify(dateRanges.value)) {
        dateRanges.value = newRanges;
      }
    } else {
      dateRanges.value = [];
    }
  });

  // 监听日期范围变化，同步到外部
  watch(dateRanges, (newRanges) => {
    emit('update:modelValue', rangesToValue(newRanges));
  }, { deep: true });

  function addDateRange() {
    dateRanges.value.push({ value: null });
  }

  function removeDateRange(index: number) {
    dateRanges.value.splice(index, 1);
  }

  // 禁用日期（可选，根据需求实现）
  function isDateDisabled(timestamp: number) {
    return false;
  }
</script>

<style lang="less" scoped>
  .other-date-picker {
    .date-rows {
      display: flex;
      flex-direction: column;
      gap: 8px;
      margin-bottom: 12px;
      max-height: 300px;
      overflow-y: auto;

      .date-row {
        display: flex;
        align-items: center;
        gap: 8px;
      }
    }

    .add-date-btn {
      width: 100%;
    }
  }
</style>
