<template>
  <n-drawer v-model:show="show" :width="560" placement="right" :mask-closable="false">
    <n-drawer-content title="样例查询配置" closable :native-scrollbar="false">
      <n-form label-placement="left" label-width="110px" autocomplete="off">
        <n-divider title-placement="left" style="margin-top: 0">向量模型</n-divider>
        <n-form-item label="模型提供商">
          <n-select
            v-model:value="form.embeddingModelId"
            :options="modelOptions"
            placeholder="选择启用的向量模型"
            @update:value="onModelChange"
          />
        </n-form-item>
        <n-form-item label="具体模型名">
          <n-select
            v-model:value="form.embeddingModelName"
            :options="modelNameOptions"
            :disabled="!form.embeddingModelId"
            placeholder="先选模型提供商，再选具体模型名"
          />
          <template #feedback>
            <span style="color: #aaa; font-size: 12px">
              向量化问题时使用的 embedding 模型；切换模型后需重新批量向量化
            </span>
          </template>
        </n-form-item>

        <n-divider title-placement="left">检索</n-divider>
        <n-form-item label="相似度阈值">
          <n-input-number
            v-model:value="form.similarityThreshold"
            :min="0"
            :max="1"
            :step="0.01"
            :precision="3"
            style="width: 200px"
          />
          <span style="margin-left: 8px; color: #aaa; font-size: 12px">
            0~1，用户问题与样例问题相似度 ≥ 此值才返回预设答案
          </span>
        </n-form-item>
      </n-form>

      <n-alert type="info" :bordered="false" style="margin-top: 8px">
        切换向量模型或修改阈值后，已录入的样例需要重新向量化才能按新模型检索。
      </n-alert>

      <template #footer>
        <n-space justify="end">
          <n-button @click="show = false">取消</n-button>
          <n-button type="primary" strong secondary :loading="saving" @click="handleSave">
            保存
          </n-button>
        </n-space>
      </template>
    </n-drawer-content>
  </n-drawer>
</template>

<script setup lang="ts">
  import { ref, reactive, computed } from 'vue';
  import { useMessage } from 'naive-ui';
  import { getModelList } from '@/api/system/aiModel';
  import { getSampleQueryConfig, saveSampleQueryConfig, type SampleQueryConfig } from '@/api/system/sampleQuery';

  const emit = defineEmits<{ (e: 'saved'): void }>();
  const message = useMessage();

  const show = ref(false);
  const saving = ref(false);

  /** ai_model 列表项（来自 getModelList，字段 id/name/models） */
  const modelList = ref<any[]>([]);

  const form = reactive({
    embeddingModelId: undefined as number | undefined,
    embeddingModelName: undefined as string | undefined,
    similarityThreshold: 0.85,
  });

  /** 模型提供商下拉：用 ai_model 行的 id + name 展示 */
  const modelOptions = computed(() =>
    modelList.value.map((m: any) => ({
      label: `${m.name || '未命名'}（${m.provider || ''}）`,
      value: m.id,
      raw: m,
    }))
  );

  /** 当前选中提供商下的具体模型名下拉（来自 ai_model.models 逗号分隔） */
  const modelNameOptions = computed(() => {
    if (!form.embeddingModelId) return [];
    const raw = modelList.value.find((m: any) => m.id === form.embeddingModelId);
    if (!raw || !raw.models) return [];
    return String(raw.models)
      .split(',')
      .map((s) => s.trim())
      .filter((s) => s.length > 0)
      .map((s) => ({ label: s, value: s }));
  });

  /** 切换提供商：清空具体模型名（避免跨提供商残留） */
  function onModelChange(_val: number) {
    form.embeddingModelName = undefined;
  }

  async function loadModels() {
    try {
      const res: any = await getModelList({ type: 2, status: 1 });
      if (res && res.code === 0 && Array.isArray(res.data)) {
        modelList.value = res.data.filter((m: any) => m && m.id != null);
      } else {
        modelList.value = [];
      }
    } catch (e) {
      modelList.value = [];
    }
  }

  async function loadConfig() {
    try {
      const res: any = await getSampleQueryConfig();
      if (res && res.code === 0 && res.data) {
        form.embeddingModelId = res.data.embeddingModelId ?? undefined;
        form.embeddingModelName = res.data.embeddingModelName ?? undefined;
        form.similarityThreshold = res.data.similarityThreshold ?? 0.85;
      }
    } catch (e) {
      // 配置读取失败用默认值
    }
  }

  async function open() {
    saving.value = false;
    show.value = true;
    await Promise.all([loadModels(), loadConfig()]);
  }

  async function handleSave() {
    saving.value = true;
    try {
      const payload: Partial<SampleQueryConfig> = {
        embeddingModelId: form.embeddingModelId,
        embeddingModelName: form.embeddingModelName,
        similarityThreshold: form.similarityThreshold,
      };
      const res: any = await saveSampleQueryConfig(payload);
      if (res && res.code === 0) {
        message.success('配置已保存');
        show.value = false;
        emit('saved');
      } else {
        message.error(res?.message || '保存失败');
      }
    } catch (e) {
      message.error('操作失败');
    } finally {
      saving.value = false;
    }
  }

  defineExpose({ open });
</script>
