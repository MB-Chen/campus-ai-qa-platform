<template>
  <div class="attachment-upload-container">
    <n-upload
      :max="5"
      :file-list="fileList"
      :action="uploadAction"
      :headers="uploadHeaders"
      :accept="acceptTypes"
      :show-file-list="true"
      :multiple="true"
      :max-size="20 * 1024 * 1024"
      :disabled="fileList.length >= 5"
      name="file"
      @change="handleChange"
      @finish="handleFinish"
      @remove="handleRemove"
      @before-upload="handleBeforeUpload"
    >
      <n-button :disabled="fileList.length >= 5">
        <template #icon>
          <n-icon>
            <UploadOutlined />
          </n-icon>
        </template>
        上传附件 (最多5份)
      </n-button>
    </n-upload>
    <n-alert type="info" class="mt-2">
      支持格式：Word、Excel、PPT、PDF，单个文件不超过20M
    </n-alert>
  </div>
</template>

<script lang="ts" setup>
  import { ref, watch, computed } from 'vue';
  import { NUpload, NButton, NIcon, NAlert, useMessage } from 'naive-ui';
  import { UploadOutlined } from '@vicons/antd';
  import { useGlobSetting } from '@/hooks/setting';
  import { useUser } from '@/store/modules/user';

  interface FileInfo {
    id: string;
    name: string;
    status: string;
    url?: string;
    percentage?: number;
    file?: File;
  }

  interface AttachmentInfo {
    name: string;
    path: string;
  }

  interface Props {
    modelValue: AttachmentInfo[];
  }

  const props = withDefaults(defineProps<Props>(), {
    modelValue: () => [],
  });

  const emit = defineEmits(['update:modelValue']);

  const message = useMessage();
  const { apiUrl } = useGlobSetting();
  const userStore = useUser();

  const fileList = ref<FileInfo[]>([]);
  const acceptTypes = '.doc,.docx,.xls,.xlsx,.ppt,.pptx,.pdf';
  const maxSize = 20; // 20MB

  // 上传地址
  const uploadAction = computed(() => `${apiUrl}/admin.common/upload`);

  // 上传请求头
  const uploadHeaders = computed(() => ({
    token: userStore.getToken,
  }));

  // 上传前验证
  function handleBeforeUpload({ file }: any) {
    console.log('handleBeforeUpload called', { file });
    const fileInfo = file.file;
    
    // 检查文件大小
    if (fileInfo.size / 1024 / 1024 > maxSize) {
      message.error(`文件大小不能超过${maxSize}M`);
      return false;
    }

    return true;
  }

  // 文件列表变化处理
  function handleChange(options: any) {
    console.log('handleChange called', options);
    const { fileList: newFileList } = options;
    // 更新文件列表，保留所有属性
    fileList.value = newFileList.map((item: any) => ({
      id: item.id,
      name: item.name,
      status: item.status,
      url: item.url,
      percentage: item.percentage,
      file: item.file,
    }));
  }

  // 上传完成处理
  function handleFinish({ file, event }: any) {
    console.log('handleFinish called', { file, event });
    try {
      const response = JSON.parse(event?.target?.response);
      console.log('Upload response:', response);
      
      if (response.code === 0) {
        // 更新文件列表中对应的状态
        const index = fileList.value.findIndex((item) => item.id === file.id);
        console.log('File index:', index);
        if (index > -1) {
          fileList.value[index].status = 'finished';
          fileList.value[index].url = response.data.path;
          fileList.value[index].percentage = 100;
          // 保存原始文件名
          fileList.value[index].originalName = file.name;
        }
        
        updateModelValue();
      } else {
        message.error(response.msg || '上传失败');
        // 移除失败的文件
        const index = fileList.value.findIndex((item) => item.id === file.id);
        if (index > -1) {
          fileList.value.splice(index, 1);
        }
      }
    } catch (error) {
      console.error('Upload error:', error);
      message.error('上传失败');
      // 移除失败的文件
      const index = fileList.value.findIndex((item) => item.id === file.id);
      if (index > -1) {
        fileList.value.splice(index, 1);
      }
    }
  }

  // 删除文件
  function handleRemove({ file }: any) {
    const index = fileList.value.findIndex((item) => item.id === file.id);
    if (index > -1) {
      fileList.value.splice(index, 1);
      updateModelValue();
    }
  }

  // 更新绑定的值
  function updateModelValue() {
    const attachments = fileList.value
      .filter((item) => item.status === 'finished' && item.url)
      .map((item) => {
        // 返回包含名称和路径的对象
        return {
          name: (item as any).originalName || item.name || item.url!.split('/').pop() || '',
          path: item.url!.replace(apiUrl, ''),
        };
      });
    emit('update:modelValue', attachments);
  }

  // 初始化文件列表
  function initFileList() {
    if (props.modelValue && props.modelValue.length > 0) {
      fileList.value = props.modelValue.map((item, index) => {
        const url = typeof item === 'string' ? item : item.path;
        const name = typeof item === 'string' ? url.split('/').pop() || `附件${index + 1}` : item.name;
        return {
          id: `file_${index}`,
          name: name,
          originalName: name,
          status: 'finished',
          url: url.startsWith('http') ? url : `${apiUrl}${url}`,
        };
      });
    } else {
      fileList.value = [];
    }
  }

  // 监听外部值变化
  watch(
    () => props.modelValue,
    () => {
      initFileList();
    },
    { deep: true }
  );

  // 初始化
  initFileList();
</script>

<style lang="less" scoped>
  .attachment-upload-container {
    .mt-2 {
      margin-top: 8px;
    }
  }
</style>
