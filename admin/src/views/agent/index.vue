<template>
  <div>
    <div class="n-layout-page-header">
      <n-card :bordered="false" title="智能体">
        管理知识库智能体，配置 RAG 参数，支持 SSE 流式测试对话与 LLM-as-judge 评估报告
      </n-card>
    </div>

    <n-card :bordered="false" class="mt-4 proCard">
      <div class="toolbar">
        <n-input
          v-model:value="keyword"
          class="filter-input"
          placeholder="按名称筛选智能体"
          clearable
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <n-icon><SearchOutlined /></n-icon>
          </template>
        </n-input>
        <n-button type="primary" secondary @click="handleSearch">
          <template #icon
            ><n-icon><SearchOutlined /></n-icon></template
          >搜索
        </n-button>
        <n-button type="primary" secondary @click="openCreate">+ 新建智能体</n-button>
      </div>

      <n-spin :show="loading">
        <n-empty
          v-if="!loading && list.length === 0"
          description="暂无智能体，点击右上角「新建智能体」"
        />
        <div v-else class="kb-grid">
          <div
            v-for="ag in list"
            :key="ag.id"
            class="kb-card"
            :class="{ disabled: ag.status === 2 }"
          >
            <div class="kb-head">
              <span class="kb-name">{{ ag.name || '未命名智能体' }}</span>
              <n-tag :type="ag.status === 2 ? 'default' : 'success'" size="small" round>
                {{ ag.status === 2 ? '禁用' : '正常' }}
              </n-tag>
            </div>
            <div class="kb-desc">{{ ag.description || '暂无描述' }}</div>
            <div class="kb-meta">
              <span class="meta-item">
                <span class="meta-label">关联知识库</span>
                <n-tooltip placement="top" :disabled="!kbNamesText(ag)">
                  <template #trigger>
                    <span class="meta-value">{{ kbCountText(ag) }}</span>
                  </template>
                  {{ kbNamesText(ag) }}
                </n-tooltip>
              </span>
              <span class="meta-item"
                ><span class="meta-label">温度</span
                ><span class="meta-value">{{ ag.temperature ?? '-' }}</span></span
              >
              <span class="meta-item"
                ><span class="meta-label">记忆轮数</span
                ><span class="meta-value">{{ ag.historyTurns ?? '-' }}</span></span
              >
            </div>
            <div class="kb-actions">
              <n-button class="kb-action-btn" size="tiny" quaternary type="primary" @click="openEdit(ag)">
                <template #icon><n-icon><SettingOutlined /></n-icon></template>
                配置
              </n-button>
              <n-button class="kb-action-btn" size="tiny" quaternary @click="openTest(ag)">
                <template #icon><n-icon><MessageOutlined /></n-icon></template>
                测试对话
              </n-button>
              <n-button class="kb-action-btn" size="tiny" quaternary @click="openEval(ag)">
                <template #icon><n-icon><BarChartOutlined /></n-icon></template>
                评估
              </n-button>
              <n-button class="kb-action-btn" size="tiny" quaternary type="error" @click="handleDelete(ag)">
                <template #icon><n-icon><DeleteOutlined /></n-icon></template>
                删除
              </n-button>
            </div>
          </div>
        </div>
      </n-spin>

      <div class="mt-4" style="display: flex; justify-content: flex-end">
        <n-pagination
          v-model:page="page"
          v-model:page-size="size"
          :item-count="total"
          show-size-picker
          :page-sizes="[10, 20, 50]"
          show-quick-jumper
          @update:page="loadList"
          @update:page-size="onSizeChange"
        />
      </div>
    </n-card>

    <AgentSaveModal ref="saveModalRef" @saved="loadList" />
    <AgentTestChat ref="testModalRef" />
    <AgentEvalPanel ref="evalModalRef" />
  </div>
</template>

<script setup lang="ts">
  import { ref, onMounted } from 'vue';
  import { useMessage, useDialog } from 'naive-ui';
  import {
    SearchOutlined,
    SettingOutlined,
    MessageOutlined,
    BarChartOutlined,
    DeleteOutlined,
  } from '@vicons/antd';
  import { getAgentList, delAgent, type Agent } from '@/api/system/agent';
  import AgentSaveModal from './components/AgentSaveModal.vue';
  import AgentTestChat from './components/AgentTestChat.vue';
  import AgentEvalPanel from './components/AgentEvalPanel.vue';

  const message = useMessage();
  const dialog = useDialog();

  const loading = ref(false);
  const list = ref<Agent[]>([]);
  const keyword = ref('');
  const page = ref(1);
  const size = ref(10);
  const total = ref(0);

  const saveModalRef = ref<InstanceType<typeof AgentSaveModal> | null>(null);
  const testModalRef = ref<InstanceType<typeof AgentTestChat> | null>(null);
  const evalModalRef = ref<InstanceType<typeof AgentEvalPanel> | null>(null);

  async function loadList() {
    loading.value = true;
    try {
      const res: any = await getAgentList({
        keyword: keyword.value || undefined,
        page: page.value,
        size: size.value,
      });
      if (res && res.code === 0 && res.data) {
        const arr = Array.isArray(res.data.data) ? res.data.data : [];
        list.value = arr.filter((x: any) => x && x.id != null) as Agent[];
        total.value = res.data.total || 0;
      } else {
        list.value = [];
        total.value = 0;
      }
    } catch (e) {
      message.error('加载智能体列表失败');
      list.value = [];
      total.value = 0;
    } finally {
      loading.value = false;
    }
  }

  function handleSearch() {
    page.value = 1;
    loadList();
  }
  function onSizeChange(s: number) {
    size.value = s;
    page.value = 1;
    loadList();
  }
  function openCreate() {
    saveModalRef.value?.openCreate();
  }
  function openEdit(ag: Agent) {
    saveModalRef.value?.openEdit(ag);
  }
  function openTest(ag: Agent) {
    testModalRef.value?.open(ag);
  }
  function openEval(ag: Agent) {
    evalModalRef.value?.open(ag);
  }

  function kbCountText(ag: Agent): string {
    if (ag.kbMode === 'all') return '全部知识库';
    if (ag.kbMode === 'none') return '不使用';
    const n = (ag.knowledgeBaseIds || []).length;
    return n > 0 ? `${n} 个` : '未关联';
  }
  function kbNamesText(ag: Agent): string {
    if (ag.kbMode === 'all') return '全部知识库';
    if (ag.kbMode === 'none') return '不使用知识库（纯 LLM 对话）';
    return (ag.knowledgeBaseNames || []).filter(Boolean).join('、') || '';
  }

  function handleDelete(ag: Agent) {
    dialog.warning({
      title: '确认删除',
      content: `确定删除智能体「${ag.name}」？其测试会话记忆将一并清除，不可撤销。`,
      positiveText: '删除',
      negativeText: '取消',
      onPositiveClick: async () => {
        const res: any = await delAgent(ag.id!);
        if (res && res.code === 0) {
          message.success('已删除');
          await loadList();
        } else {
          message.error(res?.message || '删除失败');
        }
      },
    });
  }

  onMounted(() => {
    loadList();
  });
</script>

<style lang="less" scoped>
  .toolbar {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 12px;
    margin-bottom: 16px;
  }
  .filter-input {
    max-width: 280px;
  }
  .kb-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: 14px;
  }
  .kb-card {
    position: relative;
    overflow: hidden;
    border: 1px solid #eee;
    border-radius: 8px;
    padding: 14px;
    background: linear-gradient(135deg, #ffffff 0%, rgba(7, 192, 95, 0.04) 100%);
    transition: all 0.2s;
    &:hover {
      border-color: #07c05f;
      box-shadow: 0 4px 12px rgba(7, 192, 95, 0.12);
    }
    &.disabled {
      opacity: 0.6;
      background: #fafafa;
    }
  }
  .kb-head {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
  }
  .kb-avatar {
    font-size: 20px;
    line-height: 1;
  }
  .kb-name {
    font-size: 15px;
    font-weight: 600;
    color: #333;
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .kb-desc {
    color: #888;
    font-size: 13px;
    line-height: 1.5;
    margin-bottom: 10px;
    height: 40px;
    overflow: hidden;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }
  .kb-meta {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 8px;
    padding: 8px 0;
    border-top: 1px dashed #eee;
    border-bottom: 1px dashed #eee;
    margin-bottom: 10px;
  }
  .meta-item {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }
  .meta-label {
    font-size: 11px;
    color: #aaa;
  }
  .meta-value {
    font-size: 13px;
    color: #333;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .kb-actions {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 6px;
  }
  .kb-action-btn {
    min-width: 0;
  }
</style>
