<template>
  <n-modal v-model:show="show" preset="card" title="生成问题" style="width: 550px">
    <n-alert type="info" :bordered="false" style="margin-bottom: 12px">
      将基于所选
      {{ docCount }} 个文档的原文分块逐个生成问题，生成的问题会作为独立切片入库并生成向量，
      以此提升检索召回率。处理在后台异步进行，可在文档列表查看生成状态。
    </n-alert>
    <n-form label-placement="left" label-width="110px">
      <n-form-item label="对话模型">
        <n-select
          v-model:value="form.modelId"
          :options="modelOptions"
          :loading="modelLoading"
          placeholder="不选则走候选链默认模型"
          clearable
          filterable
        />
      </n-form-item>
      <n-form-item label="每块问题数">
        <n-input-number
          v-model:value="form.questionCount"
          :min="1"
          :max="10"
          :step="1"
          style="width: 160px"
        />
        <n-text depth="3" style="font-size: 12px; margin-left: 10px">
          每个原文分块生成的问题数（1-10）
        </n-text>
      </n-form-item>
    </n-form>
    <template #footer>
      <n-space justify="end">
        <n-button @click="show = false">取消</n-button>
        <n-button type="primary" secondary :loading="saving" @click="handleSubmit"
          >开始生成</n-button
        >
      </n-space>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
  import { ref, reactive, computed } from 'vue';
  import { useMessage } from 'naive-ui';
  import { generateKbQuestions } from '@/api/system/knowledge';
  import { getModelList, type AiModel } from '@/api/system/aiModel';

  const emit = defineEmits<{ (e: 'generated'): void }>();
  const message = useMessage();

  const show = ref(false);
  const saving = ref(false);
  const modelLoading = ref(false);
  const modelOptions = ref<{ label: string; value: number }[]>([]);
  // 当前要生成问题的文档 id 列表（由 open 传入）
  const documentIds = ref<string[]>([]);
  const docCount = computed(() => documentIds.value.length);

  const form = reactive({
    modelId: undefined as number | undefined,
    questionCount: 3,
  });

  async function loadModels() {
    modelLoading.value = true;
    try {
      const res: any = await getModelList({ type: 1, status: 1 });
      if (res && res.code === 0 && Array.isArray(res.data)) {
        const models = res.data.filter((x: any) => x && x.id != null) as AiModel[];
        modelOptions.value = models.map((m) => ({
          label: `${m.name || ''}${m.models ? ' (' + m.models + ')' : ''}`,
          value: m.id as number,
        }));
      } else {
        modelOptions.value = [];
      }
    } catch (e) {
      modelOptions.value = [];
    } finally {
      modelLoading.value = false;
    }
  }

  /**
   * 打开生成问题弹窗。
   * @param ids 勾选的文档 id 列表
   */
  function open(ids: string[]) {
    if (!ids || ids.length === 0) {
      message.warning('请先选择文档');
      return;
    }
    documentIds.value = [...ids];
    Object.assign(form, { modelId: undefined, questionCount: 3 });
    show.value = true;
    loadModels();
  }

  async function handleSubmit() {
    if (documentIds.value.length === 0) {
      message.warning('请先选择文档');
      return;
    }
    if (!form.questionCount || form.questionCount < 1 || form.questionCount > 10) {
      message.warning('每个分块的问题数需在 1-10 之间');
      return;
    }
    saving.value = true;
    try {
      const res: any = await generateKbQuestions({
        documentIds: documentIds.value,
        modelId: form.modelId,
        questionCount: form.questionCount,
      });
      if (res && res.code === 0) {
        message.success('已提交生成任务，后台处理中');
        show.value = false;
        emit('generated');
      } else {
        message.error(res?.message || '生成失败');
      }
    } catch (e: any) {
      message.error(e?.message || '生成失败');
    } finally {
      saving.value = false;
    }
  }

  defineExpose({ open });
</script>
