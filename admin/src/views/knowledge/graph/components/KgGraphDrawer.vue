<template>
  <!-- 知识图谱可视化抽屉：从文档「查看图谱」打开，不再整页跳转。
       关键：用 v-if 控制内部 KgGraph 的挂载，抽屉关闭即销毁 g6 实例与 canvas，
       杜绝「全屏页跳转后 canvas 残留盖住后续页面」的顽疾。每次打开都是全新实例。 -->
  <n-drawer
    :show="show"
    :width="drawerWidth"
    placement="right"
    :auto-focus="false"
    :close-on-esc="true"
    @update:show="onUpdateShow"
  >
    <n-drawer-content
      :title="title"
      closable
      :native-scrollbar="false"
      :body-content-style="bodyStyle"
    >
      <template #header-extra>
        <n-button size="small" type="primary" secondary :disabled="!kbId" @click="reload">
          <template #icon>
            <n-icon><ReloadOutlined /></n-icon>
          </template>
          重新加载
        </n-button>
      </template>

      <!-- 图谱区域：作为 fullscreen KgGraph（absolute 铺满）的定位上下文 -->
      <div class="kg-drawer__graph">
        <KgGraph
          v-if="show && kbId"
          :key="(kbId || '') + '_' + (documentId || '') + '_' + reloadKey"
          :kb-id="kbId"
          :document-id="documentId"
          fullscreen
          @node-click="onNodeClick"
        />

        <!-- 节点详情浮动卡片（由 KgGraph 抛出 node-click 驱动，浮在图谱右上） -->
        <transition name="kg-detail-fade">
          <div v-if="selectedNode" class="kg-drawer__detail">
            <div class="kg-drawer__detail-head">
              <span class="kg-drawer__detail-title">节点详情</span>
              <button class="kg-drawer__detail-close" title="关闭" @click="closeDetail"> × </button>
            </div>
            <n-descriptions label-placement="left" bordered :column="1" size="small">
              <n-descriptions-item label="名称">{{ selectedNode.label }}</n-descriptions-item>
              <n-descriptions-item label="类型">
                <n-tag size="small">{{ selectedNode.type || '未分类' }}</n-tag>
              </n-descriptions-item>
              <n-descriptions-item label="描述">{{
                selectedNode.description || '-'
              }}</n-descriptions-item>
            </n-descriptions>
            <p class="kg-drawer__detail-tip">
              点击图中节点聚焦其关系子网；点击空白或按 Esc 散焦。
            </p>
          </div>
        </transition>
      </div>
    </n-drawer-content>
  </n-drawer>
</template>

<script setup lang="ts">
  import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue';
  import {
    NDrawer,
    NDrawerContent,
    NButton,
    NIcon,
    NTag,
    NDescriptions,
    NDescriptionsItem,
  } from 'naive-ui';
  import { ReloadOutlined } from '@vicons/antd';
  import KgGraph from './KgGraph.vue';

  const props = withDefaults(
    defineProps<{
      show: boolean;
      kbId?: string;
      documentId?: string;
      documentName?: string;
    }>(),
    { show: false }
  );
  const emit = defineEmits<{ (e: 'update:show', v: boolean): void }>();

  // 抽屉宽度：尽量宽以完整展示图谱，但留一点边距不顶满；超宽屏封顶 1300px。
  const drawerWidth = ref(1200);
  function updateWidth() {
    const w = window.innerWidth;
    drawerWidth.value = Math.min(Math.max(w - 40, 360), 1300);
  }

  const title = computed(() => {
    const doc = props.documentName ? ` · ${props.documentName}` : '';
    return `知识图谱可视化${doc}`;
  });

  // 抽屉内容区样式：去掉默认 padding，让图谱铺满；drawer 右侧高度=视口高，
  // 减掉 header（约 56px）后作为图谱区高度，确保 KgGraph 有真实尺寸可渲染。
  const bodyStyle = {
    padding: '0',
    display: 'flex',
    'flex-direction': 'column',
    flex: '1',
    'min-height': '0',
  };

  /** 重新加载：切换 KgGraph 的 key 触发销毁重建（重新拉数据 + 重排） */
  const reloadKey = ref(0);
  function reload() {
    if (!props.kbId) return;
    reloadKey.value++;
  }

  // 节点详情（浮动卡片）
  const selectedNode = ref<{
    id: string;
    label: string;
    type: string;
    description?: string;
  } | null>(null);

  /** KgGraph 抛出的节点点击：有节点则显示详情卡，null（散焦）则关 */
  function onNodeClick(node: typeof selectedNode.value) {
    selectedNode.value = node;
  }
  function closeDetail() {
    selectedNode.value = null;
  }

  function onUpdateShow(v: boolean) {
    emit('update:show', v);
  }

  // 抽屉关闭时清空详情卡，避免下次打开残留
  watch(
    () => props.show,
    (v) => {
      if (!v) selectedNode.value = null;
    }
  );

  onMounted(() => {
    updateWidth();
    window.addEventListener('resize', updateWidth);
  });
  onBeforeUnmount(() => {
    window.removeEventListener('resize', updateWidth);
  });
</script>

<style lang="less" scoped>
  .kg-drawer__graph {
    /* fullscreen KgGraph（absolute 铺满）的定位上下文。
       height 锁死为视口减 header，保证容器有真实尺寸供 g6 初始化布局与取景。 */
    position: relative;
    flex: 1 1 auto;
    width: 100%;
    height: calc(100vh - 56px);
    min-height: 0;
    overflow: hidden;
  }

  /* 节点详情浮动卡片：叠在图谱右上角 */
  .kg-drawer__detail {
    position: absolute;
    top: 12px;
    right: 12px;
    z-index: 30;
    width: 280px;
    max-height: calc(100% - 24px);
    overflow: auto;
    padding: 12px;
    background: rgba(255, 255, 255, 0.96);
    border: 1px solid #e8ecf2;
    border-radius: 8px;
    box-shadow: 0 8px 24px rgba(15, 23, 42, 0.14);
  }
  .kg-drawer__detail-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
  }
  .kg-drawer__detail-title {
    font-weight: 600;
    font-size: 13px;
    color: #0f172a;
  }
  .kg-drawer__detail-close {
    width: 22px;
    height: 22px;
    line-height: 1;
    border: none;
    border-radius: 4px;
    background: transparent;
    color: #94a3b8;
    font-size: 18px;
    cursor: pointer;
    transition: all 0.15s;
    &:hover {
      background: #f1f5f9;
      color: #475569;
    }
  }
  .kg-drawer__detail-tip {
    margin: 10px 0 0;
    font-size: 12px;
    color: #94a3b8;
    line-height: 1.5;
  }

  /* 详情卡淡入淡出 */
  .kg-detail-fade-enter-active,
  .kg-detail-fade-leave-active {
    transition: opacity 0.18s ease, transform 0.18s ease;
  }
  .kg-detail-fade-enter-from,
  .kg-detail-fade-leave-to {
    opacity: 0;
    transform: translateX(12px);
  }
</style>
