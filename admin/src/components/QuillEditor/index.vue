<template>
  <div class="quill-editor-container">
    <QuillEditor
      ref="quillEditorRef"
      v-model:content="content"
      :options="editorOptions"
      content-type="html"
      theme="snow"
      @update:content="handleContentChange"
      @ready="onEditorReady"
    />
  </div>
</template>

<script lang="ts" setup>
  import { ref, watch, computed } from 'vue';
  import { QuillEditor } from '@vueup/vue-quill';
  import '@vueup/vue-quill/dist/vue-quill.snow.css';

  interface Props {
    modelValue: string;
    placeholder?: string;
    height?: string;
  }

  const props = withDefaults(defineProps<Props>(), {
    modelValue: '',
    placeholder: '请输入内容',
    height: '300px',
  });

  const emit = defineEmits(['update:modelValue', 'image-click']);

  const quillEditorRef = ref<any>(null);
  const content = ref(props.modelValue);

  const editorOptions = computed(() => ({
    placeholder: props.placeholder,
    modules: {
      toolbar: {
        container: [
          ['bold', 'italic', 'underline', 'strike'],
          [{ header: [1, 2, 3, 4, 5, 6, false] }],
          [{ list: 'ordered' }, { list: 'bullet' }],
          [{ color: [] }, { background: [] }],
          [{ align: [] }],
          ['link', 'image'],
          ['clean'],
        ],
        handlers: {
          image: () => {
            emit('image-click');
          },
        },
      },
    },
  }));

  function onEditorReady() {
    // 编辑器就绪，toolbar handler 已通过 options 注册
  }

  function handleContentChange(newContent: string) {
    emit('update:modelValue', newContent);
  }

  function getQuill() {
    return quillEditorRef.value?.getQuill?.();
  }

  watch(
    () => props.modelValue,
    (newVal) => {
      if (newVal !== content.value) {
        content.value = newVal;
      }
    }
  );

  defineExpose({ getQuill });
</script>

<style lang="less" scoped>
  .quill-editor-container {
    width: 100%;
    :deep(.ql-container) {
      height: v-bind('height');
    }
  }
</style>
