<template>
  <n-modal
    v-model:show="showModal"
    preset="card"
    :title="title"
    :style="{ width: '900px' }"
    :bordered="false"
    @after-leave="handleClose"
  >
    <div class="image-manager-container">
      <!-- 上传按钮 -->
      <div class="mb-4">
        <n-upload
          :custom-request="handleUpload"
          :show-file-list="false"
          accept="image/*"
        >
          <n-button strong secondary type="primary">
            <template #icon>
              <n-icon><UploadOutlined /></n-icon>
            </template>
            上传图片
          </n-button>
        </n-upload>
      </div>

      <!-- 图片列表 -->
      <div class="image-list" v-if="loading === false">
        <div
          v-for="(item, index) in imageList"
          :key="index"
          class="image-item"
          :class="{ selected: isSelected(item) }"
          @click="toggleSelect(item)"
        >
          <img :src="item.url" :alt="item.name" />
          <div class="image-mask" v-if="isSelected(item)">
            <div class="mask-actions">
              <n-button
                circle
                size="small"
                type="error"
                strong
                secondary
                @click.stop="handleDelete(item)"
              >
                <template #icon>
                  <n-icon><DeleteOutlined /></n-icon>
                </template>
              </n-button>
              <n-icon size="24" color="#fff">
                <CheckOutlined />
              </n-icon>
            </div>
          </div>
        </div>
      </div>

      <!-- 加载状态 -->
      <div class="loading-container" v-if="loading">
        <n-spin size="large" />
      </div>

      <!-- 空状态 -->
      <n-empty v-if="!loading && imageList.length === 0" description="暂无图片" />
    </div>

    <template #footer>
      <div class="flex justify-between items-center">
        <span class="text-gray-500 text-sm">
          已选择: {{ selectedImages.length }} / {{ maxSelect }}
        </span>
        <n-space>
          <n-button strong secondary @click="handleClose">取消</n-button>
          <n-button strong secondary type="primary" :disabled="selectedImages.length === 0" @click="handleConfirm">
            确定
          </n-button>
        </n-space>
      </div>
    </template>
  </n-modal>
</template>

<script lang="ts">
  export default {
    name: 'ImageManager'
  }
</script>

<script lang="ts" setup>
  import { ref, computed } from 'vue';
  import { NModal, NButton, NSpace, NSpin, NEmpty, NIcon, NUpload, useMessage } from 'naive-ui';
  import { CheckOutlined, UploadOutlined, DeleteOutlined } from '@vicons/antd';
  import { getAttachmentList, uploadAttachment, delAttachment } from '@/api/system/attachment';

  interface ImageItem {
    url: string;
    name?: string;
    id?: string | number;
  }

  interface Props {
    title?: string;
    multiple?: boolean;
    maxSelect?: number;
  }

  const props = withDefaults(defineProps<Props>(), {
    title: '选择图片',
    multiple: false,
    maxSelect: 1,
  });

  const emit = defineEmits(['select']);
  const message = useMessage();

  const showModal = ref(false);
  const loading = ref(false);
  const imageList = ref<ImageItem[]>([]);
  const selectedImages = ref<ImageItem[]>([]);
  const uploading = ref(false);

  const maxSelect = computed(() => props.maxSelect);

  function isSelected(item: ImageItem): boolean {
    return selectedImages.value.some((selected) => selected.url === item.url);
  }

  function toggleSelect(item: ImageItem) {
    if (props.multiple) {
      const index = selectedImages.value.findIndex((selected) => selected.url === item.url);
      if (index > -1) {
        selectedImages.value.splice(index, 1);
      } else if (selectedImages.value.length < maxSelect.value) {
        selectedImages.value.push(item);
      }
    } else {
      selectedImages.value = [item];
    }
  }

  function openModal() {
    showModal.value = true;
    selectedImages.value = [];
    loadImages();
  }

  function closeModal() {
    showModal.value = false;
  }

  function handleClose() {
    closeModal();
  }

  function handleConfirm() {
    const result = props.multiple ? [...selectedImages.value] : selectedImages.value[0];
    emit('select', result);
    closeModal();
  }

  async function loadImages() {
    loading.value = true;
    try {
      // 调用API获取merchant_id=0的图片资源
      const response = await getAttachmentList({ merchant_id: 0 });
      if (response.code === 0) {
        imageList.value = (response.data?.data || []).map((item: any) => ({
          url: item.url,
          name: item.name || item.original_name,
          id: item.id
        }));
      } else {
        imageList.value = [];
      }
    } catch (error) {
      console.error('加载图片失败:', error);
      imageList.value = [];
    } finally {
      loading.value = false;
    }
  }

  // 自定义上传请求
  async function handleUpload({ file, onFinish, onError }: any) {
    uploading.value = true;
    try {
      const formData = new FormData();
      formData.append('file', file.file);
      formData.append('merchant_id', '0');

      const response = await uploadAttachment(formData);
      if (response.code === 0) {
        message.success('上传成功');
        // 重新加载图片列表
        await loadImages();
      } else {
        message.error(response.message || '上传失败');
      }
    } catch (error) {
      console.error('上传失败:', error);
      message.error('上传失败');
    } finally {
      uploading.value = false;
      onFinish();
    }
  }

  // 删除图片
  async function handleDelete(item: ImageItem) {
    if (!item.id) {
      message.error('无法删除：缺少图片ID');
      return;
    }
    
    // 使用确认对话框
    const confirmed = window.confirm('确定要删除这张图片吗？');
    if (!confirmed) return;

    try {
      const response = await delAttachment([item.id as number]);
      if (response.code === 0) {
        message.success('删除成功');
        // 如果删除的是已选中的图片，从选中列表中移除
        const selectedIndex = selectedImages.value.findIndex(selected => selected.id === item.id);
        if (selectedIndex > -1) {
          selectedImages.value.splice(selectedIndex, 1);
        }
        // 重新加载图片列表
        await loadImages();
      } else {
        message.error(response.message || '删除失败');
      }
    } catch (error) {
      console.error('删除失败:', error);
      message.error('删除失败');
    }
  }

  defineExpose({
    openModal,
    closeModal,
  });
</script>

<style lang="less" scoped>
  .image-manager-container {
    min-height: 400px;
    max-height: 500px;
    overflow-y: auto;
  }

  .image-list {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
    gap: 12px;
    padding: 8px;
  }

  .image-item {
    position: relative;
    aspect-ratio: 1;
    border: 2px solid transparent;
    border-radius: 8px;
    overflow: hidden;
    cursor: pointer;
    transition: all 0.3s;

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    }

    &.selected {
      border-color: #18a058;
    }

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .image-mask {
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background-color: rgba(24, 160, 88, 0.5);
      display: flex;
      align-items: center;
      justify-content: center;

      .mask-actions {
        display: flex;
        align-items: center;
        gap: 12px;
      }
    }
  }

  .loading-container {
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 400px;
  }
</style>
