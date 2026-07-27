<template>
  <n-modal
    v-model:show="show"
    preset="card"
    :title="mode === 'create' ? '新建知识库' : '编辑知识库'"
    style="width: 520px"
  >
    <n-form ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="100px">
      <n-form-item label="知识库名称" path="name">
        <n-input
          v-model:value="form.name"
          placeholder="请输入知识库名称"
          :maxlength="25"
          show-count
        />
      </n-form-item>
      <n-form-item label="描述" path="description">
        <n-input
          v-model:value="form.description"
          type="textarea"
          :rows="3"
          placeholder="可选，描述该知识库用途"
          :maxlength="255"
        />
      </n-form-item>
      <n-form-item v-if="mode === 'create'" label="嵌入模型" path="embeddingModelKey">
        <n-select
          v-model:value="form.embeddingModelKey"
          :options="modelOptions"
          placeholder="请选择嵌入模型"
          :loading="modelLoading"
          filterable
        />
      </n-form-item>
      <n-form-item v-else label="嵌入模型">
        <n-tag type="info" round>{{ modelName || '未绑定' }}</n-tag>
        <span style="margin-left: 8px; font-size: 12px; color: #aaa">创建后不可更改</span>
      </n-form-item>
      <n-form-item label="状态" path="status">
        <n-switch v-model:value="form.status" :checked-value="1" :unchecked-value="2">
          <template #checked>正常</template>
          <template #unchecked>禁用</template>
        </n-switch>
      </n-form-item>
    </n-form>

    <template #footer>
      <n-space justify="end">
        <n-button @click="show = false">取消</n-button>
        <n-button type="primary" strong secondary :loading="saving" @click="handleSave"
          >确定</n-button
        >
      </n-space>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
  import { ref, reactive } from 'vue';
  import { useMessage } from 'naive-ui';
  import type { FormInst, FormRules } from 'naive-ui';
  import { addKb, editKb, type KnowledgeBase } from '@/api/system/knowledge';
  import { getModelList, type AiModel } from '@/api/system/aiModel';

  const emit = defineEmits<{
    (e: 'saved'): void;
  }>();

  const message = useMessage();

  const show = ref(false);
  const mode = ref<'create' | 'edit'>('create');
  const saving = ref(false);
  const formRef = ref<FormInst | null>(null);

  // 嵌入模型选项（type=2, status=1）：按「具体模型」粒度展开
  // 一条 ai_model 的 models 可配多个（逗号分隔），每个模型一个选项
  // value 编码为 `${modelId}::${modelName}`，提交时拆开
  const modelLoading = ref(false);
  const modelOptions = ref<{ label: string; value: string }[]>([]);
  const modelName = ref('');

  const form = reactive({
    id: '' as string,
    name: '',
    description: '',
    embeddingModelKey: '' as string, // `${modelId}::${modelName}`
    status: 1,
  });

  const rules: FormRules = {
    name: [{ required: true, message: '请输入知识库名称', trigger: ['blur', 'input'] }],
    embeddingModelKey: [{ required: true, message: '请选择嵌入模型' }],
  };

  async function loadModels() {
    modelLoading.value = true;
    try {
      const res: any = await getModelList({ type: 2, status: 1 });
      if (res && res.code === 0 && Array.isArray(res.data)) {
        const models = res.data.filter((x: any) => x && x.id != null) as AiModel[];
        const opts: { label: string; value: string }[] = [];
        for (const m of models) {
          // models 字段逗号分隔，拆成每个具体模型一个选项
          const names = (m.models || '')
            .split(',')
            .map((s) => s.trim())
            .filter((s) => s.length > 0);
          if (names.length === 0) {
            // 没配模型名也展示一条（后端会取首项/报错），避免下拉空
            opts.push({
              label: `${m.name || ''}（未配置模型名）`,
              value: `${m.id}::`,
            });
            continue;
          }
          for (const n of names) {
            opts.push({
              label: `${m.name || ''} / ${n}`,
              value: `${m.id}::${n}`,
            });
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

  function openCreate() {
    mode.value = 'create';
    Object.assign(form, {
      id: '',
      name: '',
      description: '',
      embeddingModelKey: '',
      status: 1,
    });
    modelName.value = '';
    show.value = true;
    loadModels();
  }

  function openEdit(kb: KnowledgeBase) {
    mode.value = 'edit';
    Object.assign(form, {
      id: kb.id,
      name: kb.name || '',
      description: kb.description || '',
      embeddingModelKey:
        kb.embeddingModelId != null
          ? `${kb.embeddingModelId}::${kb.embeddingModelName || ''}`
          : '',
      status: kb.status || 1,
    });
    // 编辑态展示：显示名 + 具体模型名
    modelName.value = kb.embeddingModelName
      ? `${kb.embeddingModel || ''} / ${kb.embeddingModelName}`
      : kb.embeddingModel || '';
    show.value = true;
  }

  async function handleSave() {
    try {
      await formRef.value?.validate();
    } catch (e) {
      return;
    }
    if (mode.value === 'create' && !form.embeddingModelKey) {
      message.warning('请选择嵌入模型');
      return;
    }
    saving.value = true;
    try {
      if (mode.value === 'create') {
        // 拆 `${modelId}::${modelName}`，modelName 可能为空（后端取首项）
        const sepIdx = form.embeddingModelKey.indexOf('::');
        const embeddingModelId = Number(form.embeddingModelKey.substring(0, sepIdx));
        const embeddingModelName = form.embeddingModelKey.substring(sepIdx + 2) || undefined;
        const res: any = await addKb({
          name: form.name.trim(),
          description: form.description || undefined,
          embeddingModelId,
          embeddingModelName,
          status: form.status,
        });
        if (res && res.code === 0) {
          message.success('知识库已创建');
          show.value = false;
          emit('saved');
        } else {
          message.error(res?.message || '创建失败');
        }
      } else {
        const res: any = await editKb({
          id: form.id,
          name: form.name.trim(),
          description: form.description || undefined,
          status: form.status,
        });
        if (res && res.code === 0) {
          message.success('已保存');
          show.value = false;
          emit('saved');
        } else {
          message.error(res?.message || '保存失败');
        }
      }
    } catch (e) {
      message.error('操作失败');
    } finally {
      saving.value = false;
    }
  }

  defineExpose({ openCreate, openEdit });
</script>
