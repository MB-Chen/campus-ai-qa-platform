<template>
  <div>
    <div class="n-layout-page-header">
      <n-card :bordered="false" title="智能体管理">
        管理知识库智能体，配置 RAG 参数，支持 SSE 流式测试对话与 LLM-as-judge 评估报告
      </n-card>
    </div>

    <n-card
      v-if="guideVisible"
      :bordered="false"
      class="mt-4 guide-card"
      size="small"
    >
      <div class="guide-head">
        <span class="guide-title">
          <n-icon :component="BulbOutlined" class="guide-title-icon" />
          新手指引：构建一个智能体需要哪些准备？
        </span>
        <n-button text class="guide-close" @click="dismissGuide">
          <n-icon :component="CloseOutlined" />
        </n-button>
      </div>
      <div class="guide-intro">
        智能体 = <b>知识库</b>（回答素材）+ <b>AI 模型</b>（理解与生成）+ <b>意图路由</b>（按问题分流到对应资源）。
        三者配合，用户提问 → 意图路由判断该查哪个知识库/工具 → 检索召回 → AI 模型基于素材生成回答。建议按下面顺序依次完成。
      </div>
      <div class="guide-steps">
        <div class="step" @click="goto('/knowledge/index')">
          <div class="step-no">1</div>
          <div class="step-body">
            <div class="step-name">
              <n-icon :component="BookOutlined" /> 建知识库
              <n-icon :component="ArrowRightOutlined" class="step-go" />
            </div>
            <div class="step-desc">上传文档并完成向量化，这是智能体回答的素材来源。</div>
          </div>
        </div>
        <div class="step" @click="goto('/ai/model/index')">
          <div class="step-no">2</div>
          <div class="step-body">
            <div class="step-name">
              <n-icon :component="DeploymentUnitOutlined" /> 配 AI 模型
              <n-icon :component="ArrowRightOutlined" class="step-go" />
            </div>
            <div class="step-desc">
              至少配置一个对话模型（chat）；推荐再配向量/重排模型，召回更准。
            </div>
          </div>
        </div>
        <div class="step" @click="goto('/knowledge/intent/index')">
          <div class="step-no">3</div>
          <div class="step-body">
            <div class="step-name">
              <n-icon :component="ApartmentOutlined" /> 设意图路由
              <n-icon :component="ArrowRightOutlined" class="step-go" />
            </div>
            <div class="step-desc">
              把不同问题分流到对应知识库/系统/MCP，让回答更精准（可选，不配则全量检索）。
            </div>
          </div>
        </div>
      </div>
      <div class="guide-footer">
        准备就绪后，点击右上角
        <n-button size="tiny" type="primary" secondary @click="openCreate">+ 新建智能体</n-button>
        关联上面三步即可。
      </div>
    </n-card>

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
              <n-button size="tiny" quaternary type="primary" @click="openEdit(ag)">配置</n-button>
              <n-button size="tiny" quaternary @click="openTest(ag)">测试对话</n-button>
              <n-button size="tiny" quaternary @click="openEval(ag)">评估</n-button>
              <n-button size="tiny" quaternary type="error" @click="handleDelete(ag)"
                >删除</n-button
              >
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
  import { useRouter } from 'vue-router';
  import { useMessage, useDialog } from 'naive-ui';
  import {
    SearchOutlined,
    BookOutlined,
    DeploymentUnitOutlined,
    ApartmentOutlined,
    BulbOutlined,
    ArrowRightOutlined,
    CloseOutlined,
  } from '@vicons/antd';
  import { getAgentList, delAgent, type Agent } from '@/api/system/agent';
  import AgentSaveModal from './components/AgentSaveModal.vue';
  import AgentTestChat from './components/AgentTestChat.vue';
  import AgentEvalPanel from './components/AgentEvalPanel.vue';

  const message = useMessage();
  const dialog = useDialog();
  const router = useRouter();

  // 新手引导：可关闭，关闭状态记到 localStorage，下次进来不再展示
  const GUIDE_KEY = 'agent_guide_dismissed';
  const guideVisible = ref(localStorage.getItem(GUIDE_KEY) !== '1');
  function dismissGuide() {
    guideVisible.value = false;
    localStorage.setItem(GUIDE_KEY, '1');
  }
  function goto(path: string) {
    router.push(path).catch(() => {});
  }

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
  .guide-card {
    border: 1px solid #e3f2e8;
    background: linear-gradient(135deg, #f6ffed 0%, #ffffff 60%);
    :deep(.n-card__content) {
      padding: 14px 18px;
    }
  }
  .guide-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
  }
  .guide-title {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 15px;
    font-weight: 600;
    color: #07a94d;
  }
  .guide-title-icon {
    font-size: 18px;
  }
  .guide-close {
    color: #999;
    &:hover {
      color: #333;
    }
  }
  .guide-intro {
    font-size: 13px;
    line-height: 1.7;
    color: #555;
    b {
      color: #07a94d;
      font-weight: 600;
    }
  }
  .guide-steps {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 12px;
    margin: 14px 0;
  }
  .step {
    display: flex;
    gap: 10px;
    padding: 12px;
    border: 1px solid #e8e8e8;
    border-radius: 8px;
    background: #fff;
    cursor: pointer;
    transition: all 0.18s;
    &:hover {
      border-color: #07c05f;
      box-shadow: 0 4px 12px rgba(7, 192, 95, 0.12);
      transform: translateY(-1px);
    }
  }
  .step-no {
    flex-shrink: 0;
    width: 22px;
    height: 22px;
    border-radius: 50%;
    background: #07c05f;
    color: #fff;
    font-size: 12px;
    font-weight: 600;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .step-body {
    flex: 1;
    min-width: 0;
  }
  .step-name {
    display: flex;
    align-items: center;
    gap: 5px;
    font-size: 14px;
    font-weight: 600;
    color: #333;
    margin-bottom: 4px;
  }
  .step-go {
    font-size: 12px;
    color: #07c05f;
    margin-left: auto;
  }
  .step-desc {
    font-size: 12px;
    line-height: 1.6;
    color: #888;
  }
  .guide-footer {
    font-size: 13px;
    color: #666;
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 4px;
  }

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
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    padding: 8px 0;
    border-top: 1px dashed #eee;
    border-bottom: 1px dashed #eee;
    margin-bottom: 10px;
  }
  .meta-item {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .meta-label {
    font-size: 11px;
    color: #aaa;
  }
  .meta-value {
    font-size: 13px;
    color: #333;
  }
  .kb-actions {
    display: flex;
    gap: 4px;
    flex-wrap: wrap;
  }
</style>
