<template>
  <n-layout style="min-height: 500px">
    <!-- 内容区 -->
    <n-layout-content content-style="padding: 16px;">
      <!-- 操作栏 -->
      <n-space class="mb-4">
        <!-- 使用资源按钮 (仅在选择模式下显示) -->
        <n-badge :value="selectedImages.length" :show="needSelect && selectedImages.length > 0">
          <n-button
            v-if="needSelect"
            type="success"
            strong
            secondary
            :disabled="selectedImages.length === 0"
            @click="handleConfirmUse"
          >
            <template #icon>
              <n-icon><CheckCircleFilled /></n-icon>
            </template>
            使用资源
          </n-button>
        </n-badge>

        <n-upload
          :action="uploadUrl"
          :headers="uploadHeaders"
          :data="{ merchant_id: -1 }"
          :show-file-list="false"
          @before-upload="handleBeforeUpload"
          @finish="handleUploadFinish"
        >
          <n-button type="primary" strong secondary>
            <template #icon>
              <n-icon><UploadOutlined /></n-icon>
            </template>
            上传图片
          </n-button>
        </n-upload>

        <n-button
          strong
          secondary
          type="error"
          :disabled="selectedImages.length === 0"
          @click="handleBatchDelete"
        >
          <template #icon>
            <n-icon><DeleteOutlined /></n-icon>
          </template>
          删除文件 ({{ selectedImages.length }})
        </n-button>
      </n-space>

      <!-- 图片列表 -->
      <n-spin :show="loading">
        <div v-if="imageList.length > 0" class="image-grid" :style="{ width: needSelect ? '100%' : '100%' }">
          <div
            v-for="item in imageList"
            :key="item.id"
            class="image-item"
            :class="{ selected: isSelected(item.id) }"
            @click="toggleSelect(item.id)"
          >
            <div class="image-wrapper">
              <!-- 选择模式下使用img标签，非选择模式下使用n-image支持预览 -->
              <img
                v-if="needSelect"
                :src="item.url"
                :alt="item.name"
                class="image"
              />
              <n-image
                v-else
                :src="item.url"
                :alt="item.name"
                object-fit="cover"
                :preview-src="item.url"
                class="image"
              />
              <div v-if="isSelected(item.id)" class="selected-mask">
                <n-icon size="24" color="#fff">
                  <CheckCircleFilled />
                </n-icon>
              </div>
            </div>
            <div class="image-name">{{ item.name }}</div>
          </div>
        </div>
        <n-empty v-else description="暂无图片" class="mt-8" />
      </n-spin>

      <!-- 分页 -->
      <div class="mt-4 flex justify-end">
        <n-pagination
          v-model:page="pagination.page"
          :page-count="pagination.pageCount"
          :page-size="pagination.pageSize"
          show-size-picker
          :page-sizes="[18, 36, 54, 72]"
          @update:page="handlePageChange"
          @update:page-size="handlePageSizeChange"
        />
      </div>
    </n-layout-content>
  </n-layout>
</template>

<script lang="ts" setup>
import { ref, reactive, onMounted, computed } from 'vue';
import { useMessage, useDialog } from 'naive-ui';
import {
  UploadOutlined,
  DeleteOutlined,
  CheckCircleFilled,
} from '@vicons/antd';
import {
  getAttachmentList,
  delAttachment,
} from '@/api/system/attachment';
import { useUserStore } from '@/store/modules/user';

// Props 定义
interface Props {
  // 需要选择使用资源
  needSelect?: boolean;
  // 最大选择数量 (0 表示不限制)
  selectNum?: number;
  // 选择资源类型 1:图片 2:视频
  fileType?: number;
  // 一页显示多少资源
  pageSize?: number;
}

const props = withDefaults(defineProps<Props>(), {
  needSelect: true,
  selectNum: 0,
  fileType: 1,
  pageSize: 18,
});

// Emits 定义
const emit = defineEmits<{
  'selected-img': [files: any[]];
}>();

const message = useMessage();
const dialog = useDialog();

const loading = ref(false);
const imageList = ref<any[]>([]);
const selectedImages = ref<number[]>([]);
const selectedFiles = ref<any[]>([]);

const pagination = reactive({
  page: 1,
  pageSize: props.pageSize,
  pageCount: 1,
  total: 0,
});

const uploadUrl = computed(() => {
  return import.meta.env.VITE_GLOB_API_URL + '/government.attachment/upload';
});

const uploadHeaders = computed(() => {
  const store = useUserStore();
  return {
    token: store.getToken,
  };
});

function isSelected(id: number) {
  return selectedImages.value.includes(id);
}

function toggleSelect(id: number) {
  const index = selectedImages.value.indexOf(id);
  if (index > -1) {
    selectedImages.value.splice(index, 1);
    selectedFiles.value.splice(index, 1);
  } else {
    // 检查选择数量限制
    if (props.needSelect && props.selectNum > 0 && selectedImages.value.length >= props.selectNum) {
      message.error(`最多选择 ${props.selectNum} 个资源`);
      return;
    }
    selectedImages.value.push(id);
    const file = imageList.value.find((item) => item.id === id);
    if (file) {
      selectedFiles.value.push(file);
    }
  }
}

async function loadImageList() {
  loading.value = true;
  try {
    const res = await getAttachmentList({
      merchant_id: -1,
      page: pagination.page,
      limit: pagination.pageSize,
    });
    if (res.code === 0) {
      imageList.value = res.data.data || [];
      pagination.total = res.data.total || 0;
      pagination.pageCount = Math.ceil(pagination.total / pagination.pageSize);
    }
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
}

function handleBeforeUpload() {
  loading.value = true;
}

function handleUploadFinish({ event }: any) {
  loading.value = false;
  try {
    const response = JSON.parse(event.target.response);
    if (response.code === 0) {
      message.success('上传成功');
      loadImageList();
    } else {
      message.error(response.msg || '上传失败');
    }
  } catch (error) {
    message.error('上传失败');
  }
}

function handleBatchDelete() {
  dialog.warning({
    title: '确认删除',
    content: `确定要删除选中的 ${selectedImages.value.length} 个文件吗？`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const res = await delAttachment(selectedImages.value);
        if (res.code === 0) {
          message.success('删除成功');
          selectedImages.value = [];
          selectedFiles.value = [];
          loadImageList();
        } else {
          message.error(res.msg || '删除失败');
        }
      } catch (error) {
        console.error(error);
      }
    },
  });
}

// 确认使用选中的资源
function handleConfirmUse() {
  const files = JSON.parse(JSON.stringify(selectedFiles.value));
  emit('selected-img', files);
  // 清空选择
  selectedImages.value = [];
  selectedFiles.value = [];
}

function handlePageChange(page: number) {
  pagination.page = page;
  loadImageList();
}

function handlePageSizeChange(pageSize: number) {
  pagination.pageSize = pageSize;
  pagination.page = 1;
  loadImageList();
}

onMounted(() => {
  loadImageList();
});

// 暴露方法给父组件
defineExpose({
  loadImageList,
});
</script>

<style lang="less" scoped>
.image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
  gap: 12px;
}

.image-item {
  cursor: pointer;
  transition: all 0.3s;

  &:hover {
    transform: translateY(-2px);
  }

  &.selected .image-wrapper {
    border-color: #18a058;
  }
}

.image-wrapper {
  position: relative;
  width: 100%;
  aspect-ratio: 1;
  border: 2px solid transparent;
  border-radius: 8px;
  overflow: hidden;
  background: #f5f5f5;
}

.image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.selected-mask {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(24, 160, 88, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
}

.image-name {
  margin-top: 6px;
  font-size: 11px;
  color: #666;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
