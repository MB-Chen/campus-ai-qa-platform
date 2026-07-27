<template>
  <n-modal
    v-model:show="show"
    preset="card"
    :title="form.id ? '编辑样例' : '新增样例'"
    style="width: 640px"
    :mask-closable="false"
  >
    <n-form ref="formRef" :model="form" :rules="rules" label-placement="top">
      <n-form-item label="问题" path="question">
        <n-input
          v-model:value="form.question"
          type="textarea"
          :rows="3"
          placeholder="用户可能问的问题（参与向量化）"
          :maxlength="2000"
        />
      </n-form-item>
      <n-form-item label="答案" path="answer">
        <n-input
          v-model:value="form.answer"
          type="textarea"
          :rows="5"
          placeholder="命中后直接返回给用户的答案（不向量化）"
          :maxlength="5000"
        />
      </n-form-item>
      <n-form-item label="状态">
        <n-switch v-model:value="form.status" :checked-value="1" :unchecked-value="2">
          <template #checked>启用</template>
          <template #unchecked>禁用</template>
        </n-switch>
      </n-form-item>
    </n-form>

    <template #footer>
      <n-space justify="end">
        <n-button @click="show = false">取消</n-button>
        <n-button type="primary" secondary :loading="saving" @click="handleSave">保存</n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
  import { ref, reactive } from 'vue';
  import { useMessage } from 'naive-ui';
  import type { FormInst, FormRules } from 'naive-ui';
  import { addSampleQuery, editSampleQuery, type SampleQuery } from '@/api/system/sampleQuery';

  const emit = defineEmits<{ (e: 'saved'): void }>();
  const message = useMessage();

  const show = ref(false);
  const saving = ref(false);
  const formRef = ref<FormInst | null>(null);

  const form = reactive({
    id: undefined as number | undefined,
    question: '',
    answer: '',
    status: 1,
  });

  const rules: FormRules = {
    question: [{ required: true, message: '请输入问题', trigger: ['blur', 'input'] }],
    answer: [{ required: true, message: '请输入答案', trigger: ['blur', 'input'] }],
  };

  function openCreate() {
    saving.value = false;
    Object.assign(form, { id: undefined, question: '', answer: '', status: 1 });
    show.value = true;
  }

  function openEdit(row: SampleQuery) {
    saving.value = false;
    Object.assign(form, {
      id: row.id,
      question: row.question || '',
      answer: row.answer || '',
      status: row.status ?? 1,
    });
    show.value = true;
  }

  async function handleSave() {
    try {
      await formRef.value?.validate();
    } catch (e) {
      return;
    }
    saving.value = true;
    try {
      const trimmedQuestion = form.question.trim();
      const trimmedAnswer = form.answer.trim();
      const res: any = form.id
        ? await editSampleQuery({
            id: form.id,
            question: trimmedQuestion,
            answer: trimmedAnswer,
            status: form.status,
          })
        : await addSampleQuery({
            question: trimmedQuestion,
            answer: trimmedAnswer,
            status: form.status,
          });
      if (res && res.code === 0) {
        message.success('已保存');
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

  defineExpose({ openCreate, openEdit });
</script>
