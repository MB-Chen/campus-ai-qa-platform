<template>
  <div>
    <div class="n-layout-page-header">
      <n-card :bordered="false" :title="kbInfo.name || '知识库详情'">
        <n-space align="center">
          <n-button size="small" @click="goBack">
            <template #icon
              ><n-icon><ArrowLeftOutlined /></n-icon
            ></template>
            返回
          </n-button>
          <n-tag v-if="kbInfo.embeddingModel" type="info" round size="small">
            {{ kbInfo.embeddingModel }}
          </n-tag>
          <n-tag v-if="kbInfo.dimension" size="small">{{ kbInfo.dimension }} 维</n-tag>
          <n-tag size="small">{{ kbInfo.docCount ?? 0 }} 文档</n-tag>
        </n-space>
      </n-card>
    </div>

    <n-card :bordered="false" class="mt-4 proCard">
      <div class="detail-wrap">
        <!-- 左侧菜单 -->
        <div class="detail-menu">
          <div
            v-for="m in menus"
            :key="m.key"
            class="menu-item"
            :class="{ active: activeKey === m.key }"
            @click="activeKey = m.key"
          >
            <n-icon :size="16"><component :is="m.icon" /></n-icon>
            <span>{{ m.label }}</span>
          </div>
        </div>

        <!-- 右侧内容 -->
        <div class="detail-content">
          <n-spin :show="!kbId">
            <div v-if="!kbId" class="empty-tip">缺少知识库参数（kbId）</div>
            <template v-else>
              <DocumentPanel v-if="activeKey === 'document'" :kb-id="kbId" />
              <QuestionPanel v-else-if="activeKey === 'question'" :kb-id="kbId" />
              <HitTestPanel v-else-if="activeKey === 'hitTest'" :kb-id="kbId" />
              <KgHitTestPanel v-else-if="activeKey === 'kgHitTest'" :kb-id="kbId" />
            </template>
          </n-spin>
        </div>
      </div>
    </n-card>
  </div>
</template>

<script setup lang="ts">
  import { ref, computed, onMounted } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import {
    ArrowLeftOutlined,
    FileTextOutlined,
    MessageOutlined,
    ThunderboltOutlined,
    ApartmentOutlined,
  } from '@vicons/antd';
  import { getKbList, type KnowledgeBase } from '@/api/system/knowledge';
  import DocumentPanel from './components/DocumentPanel.vue';
  import QuestionPanel from './components/QuestionPanel.vue';
  import HitTestPanel from './components/HitTestPanel.vue';
  import KgHitTestPanel from './components/KgHitTestPanel.vue';

  const route = useRoute();
  const router = useRouter();

  const kbId = computed(() => (route.query.kbId as string) || '');
  const kbInfo = ref<Partial<KnowledgeBase>>({});

  const menus = [
    { key: 'document', label: '文档管理', icon: FileTextOutlined },
    { key: 'question', label: '问题管理', icon: MessageOutlined },
    { key: 'hitTest', label: '向量测试', icon: ThunderboltOutlined },
    { key: 'kgHitTest', label: '图谱测试', icon: ApartmentOutlined },
  ] as const;

  type MenuKey = (typeof menus)[number]['key'];
  const activeKey = ref<MenuKey>('document');

  async function loadKbInfo() {
    if (!kbId.value) return;
    // 通过列表接口定位（后端无单个 KB 详情接口，复用 index）
    try {
      const res: any = await getKbList({ keyword: undefined, page: 1, size: 500 });
      if (res && res.code === 0 && res.data) {
        const arr = Array.isArray(res.data.data) ? res.data.data : [];
        const found = arr.find((x: any) => x && x.id === kbId.value);
        if (found) kbInfo.value = found;
      }
    } catch (e) {
      // ignore
    }
  }

  function goBack() {
    router.back();
  }

  onMounted(() => {
    const tab = route.query.tab as string;
    if (tab && menus.some((m) => m.key === tab)) {
      activeKey.value = tab as MenuKey;
    }
    loadKbInfo();
  });
</script>

<style lang="less" scoped>
  .proCard {
    padding: 20px;
  }
  .detail-wrap {
    display: flex;
    gap: 16px;
    min-height: 480px;
  }
  .detail-menu {
    width: 160px;
    flex-shrink: 0;
    border-right: 1px solid #f0f0f0;
    padding-right: 12px;
  }
  .menu-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 12px;
    border-radius: 6px;
    cursor: pointer;
    font-size: 14px;
    color: #555;
    transition: all 0.2s;
    margin-bottom: 4px;
    &:hover {
      background: #f5f5f5;
    }
    &.active {
      background: rgba(7, 192, 95, 0.1);
      color: #07c05f;
      font-weight: 600;
    }
  }
  .detail-content {
    flex: 1;
    min-width: 0;
  }
  .empty-tip {
    text-align: center;
    color: #aaa;
    padding: 60px 0;
  }
</style>
