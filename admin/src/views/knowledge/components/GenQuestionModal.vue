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
          v-model:value="form.modelKey"
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
  // ★ value 为 `${modelId}::${modelName}`（一个 ai_model 多模型名时拉平成多条）
  const modelOptions = ref<{ label: string; value: string }[]>([]);
  // 当前要生成问题的文档 id 列表（由 open 传入）
  const documentIds = ref<string[]>([]);
  const docCount = computed(() => documentIds.value.length);

  const form = reactive({
    // ★ 对话模型组合 key：`${modelId}::${modelName}`（一对多拉平，提交时拆成 id+name）
    modelKey: null as string | null,
    questionCount: 3,
  });

  async function loadModels() {
    modelLoading.value = true;
    try {
      const res: any = await getModelList({ type: 1, status: 1 });
      if (res && res.code === 0 && Array.isArray(res.data)) {
        const models = (res.data as AiModel[]).filter((x) => x && x.id != null);
        const opts: { label: string; value: string }[] = [];
        for (const m of models) {
          const names = (m.models || '')
            .split(',')
            .map((s: string) => s.trim())
            .filter((s) => s.length > 0);
          if (names.length === 0) {
            opts.push({ label: `${m.name || ''}（未配置模型名）`, value: `${m.id}::` });
            continue;
          }
          for (const n of names) {
            opts.push({ label: `${m.name || ''} / ${n}`, value: `${m.id}::${n}` });
          }
        }
        modelOptions.value = opts;
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
    Object.assign(form, { modelKey: null, questionCount: 3 });
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
      // ★ 拆组合 key → modelId + modelName，后端 KnowledgeDocumentServiceImpl 透传到 chat 调用
      let modelId: number | undefined;
      let modelName: string | undefined;
      if (form.modelKey) {
        const sepIdx = form.modelKey.indexOf('::');
        modelId = Number(form.modelKey.substring(0, sepIdx));
        modelName = form.modelKey.substring(sepIdx + 2) || undefined;
      }
      const res: any = await generateKbQuestions({
        documentIds: documentIds.value,
        modelId,
        modelName,
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
