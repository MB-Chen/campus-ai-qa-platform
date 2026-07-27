<template>
  <!-- 知识图谱可视化全屏页：改用 antv/g6 v5（KgGraph 组件）。
       两种进入方式：
       ① 从文档操作「查看图谱」跳转：带 query.kbId + documentId → 文档模式，锁定该文档子图，不让选
       ② 从知识图谱配置页全屏入口：只带 query.kbId → 知识库模式，可切换知识库 -->
  <div class="kg-viz-fullscreen">
    <div class="n-layout-page-header">
      <n-card :bordered="false" title="知识图谱可视化">
        <n-space align="center">
          <!-- 文档模式：显示文档名标签，不可切换 -->
          <n-tag v-if="isDocMode" type="info" size="small" round>
            文档：{{ selectedDocumentName || selectedDocumentId }}
          </n-tag>
          <!-- 知识库模式：可切换知识库 -->
          <n-select
            v-else
            v-model:value="selectedKbId"
            placeholder="选择知识库"
            :options="kbOptions"
            filterable
            style="width: 280px"
          />
          <n-button type="primary" secondary @click="reload" :disabled="!selectedKbId">
            <template #icon
              ><n-icon><ReloadOutlined /></n-icon
            ></template>
            重新加载
          </n-button>
          <n-button secondary @click="goBack">
            <template #icon
              ><n-icon><ArrowLeftOutlined /></n-icon
            ></template>
            返回
          </n-button>
          <n-text depth="3">基于 antv/g6 v5，可拖拽/缩放，点击节点聚焦其子网</n-text>
        </n-space>
      </n-card>
    </div>

    <!-- 图谱画布：fullscreen 撑满剩余视口高度，真全屏 -->
    <div class="kg-viz-fullscreen__graph">
      <KgGraph
        v-if="selectedKbId"
        :key="selectedKbId + '_' + (selectedDocumentId || '') + '_' + reloadKey"
        :kb-id="selectedKbId"
        :document-id="selectedDocumentId"
        fullscreen
        @node-click="onNodeClick"
      />
      <div v-else class="kg-viz-fullscreen__empty">
        <n-empty description="请选择知识库" />
      </div>
    </div>

    <!-- 节点详情侧栏（由 KgGraph 抛出 node-click 驱动） -->
    <n-drawer v-model:show="showDetail" :width="400" placement="right">
      <n-drawer-content title="节点详情" closable>
        <n-descriptions
          v-if="selectedNode"
          label-placement="left"
          bordered
          :column="1"
          size="small"
        >
          <n-descriptions-item label="名称">{{ selectedNode.label }}</n-descriptions-item>
          <n-descriptions-item label="类型">
            <n-tag size="small">{{ selectedNode.type || '未分类' }}</n-tag>
          </n-descriptions-item>
          <n-descriptions-item label="描述">{{
            selectedNode.description || '-'
          }}</n-descriptions-item>
        </n-descriptions>
        <n-text depth="3" style="display: block; margin-top: 12px; font-size: 12px">
          点击图中节点聚焦其关系子网；点击空白或按 Esc 散焦。
        </n-text>
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<script setup lang="ts">
  import { ref, computed, onMounted } from 'vue';
  import { useRoute } from 'vue-router';

  import {
    NCard,
    NSpace,
    NSelect,
    NButton,
    NText,
    NTag,
    NEmpty,
    NIcon,
    NDrawer,
    NDrawerContent,
    NDescriptions,
    NDescriptionsItem,
  } from 'naive-ui';
  import { ReloadOutlined, ArrowLeftOutlined } from '@vicons/antd';
  import { getKbList, getDocumentList } from '@/api/system/knowledge';
  import KgGraph from './components/KgGraph.vue';

  const route = useRoute();
  const selectedKbId = ref<string | null>(null);
  const selectedDocumentId = ref<string | undefined>(undefined);
  const selectedDocumentName = ref<string>('');
  const kbOptions = ref<{ label: string; value: string }[]>([]);

  // 文档模式：从文档操作跳转带 documentId 时为 true，锁定该文档子图
  const isDocMode = computed(() => !!selectedDocumentId.value);

  // 节点详情侧栏
  const showDetail = ref(false);
  const selectedNode = ref<{
    id: string;
    label: string;
    type: string;
    description?: string;
  } | null>(null);

  /** KgGraph 抛出的节点点击：有节点则开抽屉，null（散焦）则关 */
  function onNodeClick(node: typeof selectedNode.value) {
    selectedNode.value = node;
    showDetail.value = node != null;
  }

  /** 重新加载：切换 KgGraph 的 key 触发重建（重新拉数据 + 重排） */
  const reloadKey = ref(0);
  function reload() {
    if (!selectedKbId.value) return;
    reloadKey.value++;
  }

  /**
   * 返回：用 location.href 整页跳转到知识库详情页。
   * ★ 必须整页刷新：g6 v5 fullscreen 模式的 canvas 用 position:fixed，路由内跳转时
   *   graph.destroy() 不一定能移除 DOM，残留 canvas 会盖住后续所有页面（点任何菜单都空白）。
   *   location.href 触发整页重载，所有 DOM 清零，彻底杜绝残留。
   */
  function goBack() {
    const kbId = selectedKbId.value;
    if (kbId) {
      window.location.href = `/knowledge/detail?kbId=${encodeURIComponent(kbId)}`;
    } else {
      window.location.href = '/knowledge/index';
    }
  }

  async function loadKbOptions() {
    try {
      const res: any = await getKbList({ page: 1, size: 200 });
      if (res && res.code === 0 && res.data?.data) {
        kbOptions.value = res.data.data.map((kb: any) => ({ label: kb.name, value: kb.id }));
      }
    } catch {
      // 静默
    }
  }

  /** 文档模式下反查文档名（顶部标签显示用） */
  async function loadDocName(kbId: string, docId: string) {
    try {
      const res: any = await getDocumentList({ kbId, page: 1, size: 200 });
      if (res && res.code === 0 && Array.isArray(res.data?.data)) {
        const doc = res.data.data.find((d: any) => d.id === docId);
        if (doc) selectedDocumentName.value = doc.fileName || docId;
      }
    } catch {
      // 静默
    }
  }

  onMounted(async () => {
    const kbId = route.query.kbId as string;
    const documentId = route.query.documentId as string | undefined;
    // 文档模式：带 documentId，无需加载知识库下拉
    if (documentId) {
      selectedDocumentId.value = documentId;
      if (kbId) {
        selectedKbId.value = kbId;
        loadDocName(kbId, documentId);
      }
    } else {
      // 知识库模式：加载知识库下拉，自动选中 query.kbId
      await loadKbOptions();
      if (kbId) selectedKbId.value = kbId;
    }
  });
</script>

<style scoped>
  .kg-viz-fullscreen {
    /* 作为 fullscreen 图谱（KgGraph 内 absolute 铺满）的定位上下文，
       并撑满内容区（layout-content 高 100vh，减掉主内容 10px 上下外边距）。
       overflow:hidden 防止 absolute 画布溢出。 */
    position: relative;
    height: calc(100vh - 20px);
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }
  /* 图谱区域：KgGraph 在 fullscreen 模式下 absolute 铺满本容器；
     本容器 position:relative 即为其定位锚点，flex:1 撑满剩余高度。 */
  .kg-viz-fullscreen__graph {
    position: relative;
    flex: 1 1 auto;
    min-height: 0;
  }
  .kg-viz-fullscreen__empty {
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    border: 1px solid #eee;
    border-radius: 4px;
  }
</style>
