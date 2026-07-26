<template>
  <div class="w-full">
    <div class="upload">
      <div class="upload-card">
        <!--图片列表-->
        <div
          class="upload-card-item"
          :style="getCSSProperties"
          v-for="(item, index) in imgList"
          :key="`img_${index}`"
        >
          <div class="upload-card-item-info">
            <div class="img-box">
              <img :src="item" />
            </div>
            <div class="img-box-actions">
              <n-icon size="18" class="mx-2 action-icon" @click="preview(item)">
                <EyeOutlined />
              </n-icon>
              <n-icon size="18" class="mx-2 action-icon" @click="remove(index)">
                <DeleteOutlined />
              </n-icon>
            </div>
          </div>
        </div>

        <!--选择图片-->
        <div
          class="upload-card-item upload-card-item-select-picture"
          :style="getCSSProperties"
          v-if="imgList.length < maxNumber"
        >
          <div class="flex flex-col justify-center" @click="openImageManager">
            <n-icon size="18" class="m-auto">
              <PictureOutlined />
            </n-icon>
            <span class="upload-title">选择图片</span>
          </div>
        </div>
      </div>
    </div>

    <!--上传图片-->
    <n-space>
      <n-alert title="提示" type="info" v-if="helpText" class="flex w-full">
        {{ helpText }}
      </n-alert>
    </n-space>

    <!-- 图片资源管理器 -->
    <ImageManager
      ref="imageManagerRef"
      title="选择图片"
      :multiple="multiple"
      :max-select="maxNumber - imgList.length"
      @select="handleImageSelect"
    />

    <!--预览图片-->
    <n-modal
      v-model:show="showModal"
      preset="card"
      title="预览"
      :bordered="false"
      :style="{ width: '520px' }"
    >
      <img :src="previewUrl" />
    </n-modal>
  </div>
</template>

<script lang="ts">
  import { defineComponent, toRefs, reactive, computed, watch, ref } from 'vue';
  import { EyeOutlined, DeleteOutlined, PictureOutlined } from '@vicons/antd';
  import { basicProps } from './props';
  import { useMessage, useDialog } from 'naive-ui';
  import ImageManager from '@/components/ImageManager/index.vue';

  export default defineComponent({
    name: 'ImageSelect',

    components: { EyeOutlined, DeleteOutlined, PictureOutlined, ImageManager },
    props: {
      ...basicProps,
    },
    emits: ['uploadChange', 'delete'],
    setup(props, { emit }) {
      const getCSSProperties = computed(() => {
        return {
          width: `${props.width}px`,
          height: `${props.height}px`,
        };
      });

      const message = useMessage();
      const dialog = useDialog();
      const imageManagerRef = ref();

      const state = reactive({
        showModal: false,
        previewUrl: '',
        originalImgList: [] as string[],
        imgList: [] as string[],
      });

      //赋值默认图片显示
      watch(
        () => props.value,
        () => {
          state.imgList = props.value.map((item) => {
            return getImgUrl(item);
          });
          state.originalImgList = [...props.value];
        },
        { immediate: true }
      );

      //预览
      function preview(url: string) {
        state.showModal = true;
        state.previewUrl = url;
      }

      //删除
      function remove(index: number) {
        dialog.info({
          title: '提示',
          content: '你确定要删除吗？',
          positiveText: '确定',
          negativeText: '取消',
          onPositiveClick: () => {
            state.imgList.splice(index, 1);
            state.originalImgList.splice(index, 1);
            emit('uploadChange', state.originalImgList);
            emit('delete', state.originalImgList);
          },
          onNegativeClick: () => {},
        });
      }

      //组装完整图片地址
      function getImgUrl(url: string): string {
        // 如果已经是完整URL，直接返回
        if (/^(http|https):\/\//g.test(url)) {
          return url;
        }
        // 如果是相对路径，直接返回（由后端处理完整URL）
        return url;
      }

      // 打开图片管理器
      function openImageManager() {
        if (imageManagerRef.value) {
          imageManagerRef.value.openModal();
        } else {
          console.error('imageManagerRef is not defined');
        }
      }

      // 处理图片选择
      function handleImageSelect(images: any) {
        const selectedImages = Array.isArray(images) ? images : [images];

        selectedImages.forEach((image: any) => {
          if (image && image.url) {
            state.imgList.push(getImgUrl(image.url));
            state.originalImgList.push(image.url);
          }
        });

        emit('uploadChange', state.originalImgList);
        //message.success('图片选择成功');
      }

      return {
        ...toRefs(state),
        preview,
        remove,
        getCSSProperties,
        openImageManager,
        handleImageSelect,
        imageManagerRef,
      };
    },
  });
</script>

<style lang="less">
  .upload {
    width: 100%;
    overflow: hidden;

    &-card {
      width: auto;
      height: auto;
      display: flex;
      flex-wrap: wrap;
      align-items: center;

      &-item {
        margin: 0 8px 8px 0;
        position: relative;
        padding: 8px;
        border: 1px solid #d9d9d9;
        border-radius: 2px;
        display: flex;
        justify-content: center;
        flex-direction: column;
        align-items: center;

        &:hover {
          background: 0 0;

          .upload-card-item-info::before {
            opacity: 1;
          }

          &-info::before {
            opacity: 1;
          }
        }

        &-info {
          position: relative;
          height: 100%;
          width: 100%;
          padding: 0;
          overflow: hidden;

          &:hover {
            .img-box-actions {
              opacity: 1;
            }
          }

          &::before {
            position: absolute;
            z-index: 1;
            width: 100%;
            height: 100%;
            background-color: rgba(0, 0, 0, 0.5);
            opacity: 0;
            transition: all 0.3s;
            content: ' ';
          }

          .img-box {
            position: relative;
            //padding: 8px;
            //border: 1px solid #d9d9d9;
            border-radius: 2px;
          }

          .img-box-actions {
            position: absolute;
            top: 50%;
            left: 50%;
            z-index: 10;
            white-space: nowrap;
            transform: translate(-50%, -50%);
            opacity: 0;
            transition: all 0.3s;
            display: flex;
            align-items: center;
            justify-content: space-between;

            &:hover {
              background: 0 0;
            }

            .action-icon {
              color: rgba(255, 255, 255, 0.85);

              &:hover {
                cursor: pointer;
                color: #fff;
              }
            }
          }
        }
      }

      &-item-select-picture {
        border: 1px dashed #d9d9d9;
        border-radius: 2px;
        cursor: pointer;
        background: #fafafa;
        color: #666;

        .upload-title {
          color: #666;
        }

        &:hover {
          border-color: #18a058;
          color: #18a058;

          .upload-title {
            color: #18a058;
          }
        }
      }
    }
  }
</style>
