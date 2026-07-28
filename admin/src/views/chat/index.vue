<template>
  <div class="chat-create">
    <div class="cc-container">
      <!-- ===== 1. 大标题 ===== -->
      <h1 class="cc-greeting">Hi，我是 SparkX，让你的知识触手可及</h1>

      <!-- ===== 2. 主输入区域（大输入框 + 工具栏） ===== -->
      <div class="cc-input-card">
        <n-input
          ref="inputRef"
          v-model:value="inputValue"
          type="textarea"
          :autosize="{ minRows: 3, maxRows: 8 }"
          placeholder="和我聊聊天吧"
          :disabled="creating"
          :bordered="false"
          class="cc-textarea"
          @keydown.enter.exact.prevent="handleSend"
          @focus="inputFocused = true"
          @blur="inputFocused = false"
        />

        <!-- 工具栏：左侧智能体选择 + 右侧发送 -->
        <div class="cc-toolbar">
          <div class="cc-toolbar-left">
            <!-- 智能体选择：自定义富下拉（图标列表 + 管理入口） -->
            <n-popover
              v-model:show="agentPopoverShow"
              trigger="click"
              placement="bottom-start"
              :width="280"
              :show-arrow="false"
              raw
              class="cc-agent-popover"
            >
              <template #trigger>
                <div class="cc-agent-trigger" :class="{ active: agentPopoverShow }">
                  <n-icon size="14" class="cc-agent-trigger-icon"><RobotOutlined /></n-icon>
                  <span class="cc-agent-trigger-text">{{ selectedAgentName || '选择智能体' }}</span>
                  <n-icon size="12" class="cc-agent-trigger-arrow"><CaretDownOutlined /></n-icon>
                </div>
              </template>

              <div
                class="cc-agent-panel"
                :style="{
                  background: themeVars.cardColor,
                  borderRadius: '10px',
                  boxShadow: themeVars.boxShadow2,
                  border: `1px solid ${themeVars.borderColor}`,
                }"
              >
                <!-- 面板头部：标题 + 管理链接 -->
                <div class="cc-agent-header">
                  <span class="cc-agent-header-title">选择智能体</span>
                  <a class="cc-agent-manage" @click="goAgentManage">
                    <n-icon size="12"><PlusOutlined /></n-icon> 管理
                  </a>
                </div>

                <!-- 分组标题 -->
                <div class="cc-agent-group-label">内置智能体</div>

                <!-- 智能体列表 -->
                <div v-if="agents.length" class="cc-agent-list">
                  <div
                    v-for="agent in agents"
                    :key="agent.id"
                    class="cc-agent-item"
                    :class="{ selected: selectedAgentId === agent.id }"
                    @click="selectAgent(agent)"
                  >
                    <div class="cc-agent-item-left">
                      <n-icon
                        :size="18"
                        :component="getAgentIcon(agent)"
                        class="cc-agent-item-icon"
                      />
                      <span class="cc-agent-item-name">{{ agent.name || '未命名' }}</span>
                    </div>
                    <n-tooltip v-if="agent.description" trigger="hover" :delay="500">
                      <template #trigger>
                        <n-icon :size="14" class="cc-agent-item-info"
                          ><InfoCircleOutlined
                        /></n-icon>
                      </template>
                      {{ agent.description }}
                    </n-tooltip>
                  </div>
                </div>

                <!-- 空状态引导 -->
                <div v-else class="cc-agent-empty">
                  <p>暂无可用智能体</p>
                  <n-button size="tiny" type="primary" secondary @click="goAgentCreate">
                    <template #icon
                      ><n-icon :size="12"><PlusOutlined /></n-icon
                    ></template>
                    去新增
                  </n-button>
                </div>
              </div>
            </n-popover>
          </div>

          <div class="cc-toolbar-right">
            <!-- 发送按钮 -->
            <n-button
              type="primary"
              size="small"
              secondary
              :loading="creating"
              :disabled="!canSend"
              class="cc-send-btn"
              @click="handleSend"
            >
              <template #icon
                ><n-icon size="15"><SendOutlined /></n-icon
              ></template>
              发送
            </n-button>
          </div>
        </div>
      </div>

      <!-- ===== 3. 推荐问题（有推荐问题时显示） ===== -->
      <transition name="cc-fade">
        <div v-if="suggestedQuestions.length && !sqLoading" class="cc-suggested-section">
          <div class="cc-suggested-grid">
            <div
              v-for="(q, i) in suggestedQuestions"
              :key="i"
              class="cc-sq-card"
              @click="onSuggestedClick(q)"
            >
              <span class="cc-sq-text">{{ q }}</span>
            </div>
          </div>
        </div>
      </transition>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { ref, computed, onMounted, nextTick } from 'vue';
  import { useRouter } from 'vue-router';
  import { useMessage, useThemeVars } from 'naive-ui';
  import {
    SendOutlined,
    RobotOutlined,
    CaretDownOutlined,
    PlusOutlined,
    InfoCircleOutlined,
    MessageOutlined,
    BoxPlotOutlined,
    BookOutlined,
    BarChartOutlined,
    ExperimentOutlined,
    ApiOutlined,
  } from '@vicons/antd';
  import { createSessions } from '@/api/system/chat';
  import { getAgentEnabledList, type Agent } from '@/api/system/agent';

  const router = useRouter();
  const message = useMessage();
  const themeVars = useThemeVars();

  // ---- 输入 ----
  const inputValue = ref('');
  const inputRef = ref<any>(null);
  const inputFocused = ref(false);
  const creating = ref(false);

  // ---- 智能体（工具栏「快速问答」选择框） ----
  const agents = ref<Agent[]>([]);
  const selectedAgentId = ref<string | null>(null);
  const agentLoading = ref(false);
  const agentPopoverShow = ref(false);

  // 当前选中智能体的显示名称
  const selectedAgentName = computed(() => {
    if (!selectedAgentId.value) return '';
    return agents.value.find((a) => a.id === selectedAgentId.value)?.name || '';
  });

  // 根据智能体名称/类型匹配图标（可后续改为后端返回 icon 字段）
  const AGENT_ICON_MAP: Record<string, any> = {
    快速问答: MessageOutlined,
    智能推理: BoxPlotOutlined,
    维基: BookOutlined,
    维基问答: BookOutlined,
    数据分析: BarChartOutlined,
    数据分析师: BarChartOutlined,
    实验: ExperimentOutlined,
    工具: ApiOutlined,
  };
  function getAgentIcon(agent: Agent): any {
    // 优先按 name 匹配，默认 RobotOutlined
    const name = agent.name || '';
    for (const [key, icon] of Object.entries(AGENT_ICON_MAP)) {
      if (name.includes(key)) return icon;
    }
    return RobotOutlined;
  }

  // ---- 推荐问题 ----
  const suggestedQuestions = ref<string[]>([]);
  const sqLoading = ref(false);

  // ---- 计算属性 ----
  const canSend = computed(() => !!inputValue.value.trim() && !creating.value);

  // ---- 方法 ----
  async function loadAgents() {
    agentLoading.value = true;
    try {
      const resp: any = await getAgentEnabledList();
      const data = resp?.data ?? resp;
      const list: Agent[] = Array.isArray(data) ? data : data?.list ?? data?.records ?? [];
      agents.value = list;
    } catch (e: any) {
      message.error('加载智能体失败：' + (e?.message || e));
    } finally {
      agentLoading.value = false;
    }
  }

  function loadSuggested() {
    sqLoading.value = true;
    const agent = agents.value.find((a) => a.id === selectedAgentId.value);
    // 从智能体的 suggestedQuestions 字段取；若后端暂无则留空
    setTimeout(() => {
      suggestedQuestions.value = (agent?.suggestedQuestions as string[]) || [];
      sqLoading.value = false;
    }, 100);
  }

  // ---- 智能体面板操作 ----
  // 选中的智能体持久化到 localStorage，刷新页面后仍保持选中
  const SELECTED_AGENT_KEY = 'SPARKX_SELECTED_AGENT';

  function selectAgent(agent: Agent) {
    selectedAgentId.value = agent.id as string;
    localStorage.setItem(SELECTED_AGENT_KEY, selectedAgentId.value);
    agentPopoverShow.value = false; // 关闭下拉
    loadSuggested(); // 刷新推荐问题
  }

  // 恢复上次选中的智能体（须在 loadAgents 之后调用，确保该智能体仍然存在/可用）
  function restoreSelectedAgent() {
    const savedId = localStorage.getItem(SELECTED_AGENT_KEY);
    if (!savedId) return;
    if (agents.value.some((a) => a.id === savedId)) {
      selectedAgentId.value = savedId;
    } else {
      // 智能体已被删除或停用，清掉失效记录
      localStorage.removeItem(SELECTED_AGENT_KEY);
    }
  }

  function goAgentManage() {
    agentPopoverShow.value = false;
    router.push('/agent').catch(() => {});
  }

  function goAgentCreate() {
    agentPopoverShow.value = false;
    router.push('/agent').catch(() => {});
  }

  function onSuggestedClick(q: string) {
    inputValue.value = q;
    nextTick(() => inputRef.value?.focus());
  }

  function navigateToSession(sessionId: string) {
    const query: Record<string, string> = {};
    if (selectedAgentId.value) query.agentId = selectedAgentId.value;
    const q = inputValue.value.trim();
    if (q) query.q = q;
    router.push({ path: `/chat/${sessionId}`, query });
  }

  async function handleSend() {
    const q = inputValue.value.trim();
    if (!q) return;
    creating.value = true;
    try {
      const resp: any = await createSessions({
        agent_id: selectedAgentId.value ?? '',
        query: q,
        title: q.length > 20 ? q.slice(0, 20) + '...' : q,
        agent_config: { enabled: true },
      });
      const body = resp ?? {};
      const data = body.data !== undefined ? body.data : body;
      const id = data?.id ?? body?.id;
      if (id) {
        navigateToSession(id);
      } else {
        throw new Error('响应中缺少会话 id');
      }
    } catch (e: any) {
      console.warn('[chat] createSessions 失败，使用本地预览会话ID：', e);
      message.warning('后端会话接口尚未就绪，已使用本地预览会话');
      navigateToSession(crypto.randomUUID());
    } finally {
      creating.value = false;
    }
  }

  onMounted(async () => {
    await loadAgents();
    restoreSelectedAgent(); // 恢复上次选中的智能体
    loadSuggested();
  });
</script>

<style lang="less" scoped>
  .chat-create {
    display: flex;
    align-items: center;
    justify-content: center;
    // 父级 .layout-content 高度 = 100vh，但其内层 .layout-content-main 有
    // margin:10px(上下共20) + padding-bottom:36px，故页面可用高度约 100vh-56px；
    // 用该值作为最小高度，flex 垂直居中才有参照，否则内容会贴顶。
    min-height: calc(100vh - 56px);
    width: 100%;
    padding: 24px;
    box-sizing: border-box;
    // 跟随主题：亮色=白底，暗色=深色底
    background: v-bind('themeVars.bodyColor');
    transition: background-color 0.2s;
  }

  .cc-container {
    width: 100%;
    max-width: 720px;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 24px;
  }

  /* ========== 1. 标题 ========== */
  .cc-greeting {
    margin: 8px 0 0;
    font-size: 26px;
    font-weight: 600;
    color: v-bind('themeVars.textColorBase');
    letter-spacing: 0.3px;
    text-align: center;
    line-height: 1.4;
  }

  /* ========== 2. 输入卡片 ========== */
  .cc-input-card {
    width: 100%;
    background: v-bind('themeVars.cardColor');
    border: 1px solid v-bind('themeVars.borderColor');
    border-radius: 14px;
    padding: 16px 18px;
    box-shadow: v-bind('themeVars.boxShadow1');
    transition: border-color 0.2s, box-shadow 0.2s, background-color 0.2s;

    &:focus-within {
      border-color: v-bind('themeVars.primaryColor');
      box-shadow: 0 2px 16px rgba(64, 158, 255, 0.1);
    }

    .cc-textarea {
      :deep(.n-input__textarea-el) {
        font-size: 15px;
        line-height: 1.6;
        background: transparent;
        resize: none;
      }
      :deep(.n-input) {
        --n-padding-vertical: 0;
        --n-padding-horizontal: 0;
      }
    }
  }

  /* ========== 工具栏 ========== */
  .cc-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 10px;
    padding-top: 10px;
    border-top: 1px solid v-bind('themeVars.dividerColor');
  }

  .cc-toolbar-left,
  .cc-toolbar-right {
    display: flex;
    align-items: center;
    gap: 2px;
  }

  .cc-agent-trigger {
    display: flex;
    align-items: center;
    gap: 5px;
    padding: 4px 10px;
    border-radius: 8px;
    cursor: pointer;
    font-size: 13px;
    color: v-bind('themeVars.textColor3');
    background: v-bind('themeVars.actionColor');
    border: 1px solid transparent;
    transition: all 0.2s;
    min-width: 120px;
    max-width: 220px;

    &:hover,
    &.active {
      color: v-bind('themeVars.textColorBase');
      border-color: v-bind('themeVars.borderColor');
      background: v-bind('themeVars.cardColor');
    }
  }
  .cc-agent-trigger-icon {
    color: v-bind('themeVars.primaryColor');
    flex-shrink: 0;
  }
  .cc-agent-trigger-text {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .cc-agent-trigger-arrow {
    flex-shrink: 0;
    color: v-bind('themeVars.textColor3');
    transition: transform 0.2s;
  }
  .cc-agent-trigger.active .cc-agent-trigger-arrow {
    transform: rotate(180deg);
  }

  /* ===== 智能体下拉面板 ===== */
  .cc-agent-panel {
    padding: 4px 0;
    /* 背景/边框/圆角/阴影通过 inline-style 设置（raw popover teleport 到 body，scoped 样式可能丢失） */
  }
  .cc-agent-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px 14px 6px;
  }
  .cc-agent-header-title {
    font-size: 14px;
    font-weight: 600;
    color: v-bind('themeVars.textColorBase');
  }
  .cc-agent-manage {
    font-size: 12px;
    color: v-bind('themeVars.primaryColor');
    cursor: pointer;
    display: flex;
    align-items: center;
    gap: 2px;
    text-decoration: none;
    &:hover {
      opacity: 0.8;
    }
  }
  .cc-agent-group-label {
    padding: 6px 14px 4px;
    font-size: 12px;
    color: v-bind('themeVars.textColor3');
    font-weight: 500;
  }
  .cc-agent-list {
    max-height: 240px;
    overflow-y: auto;
    padding: 2px 0;
  }
  .cc-agent-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 14px;
    cursor: pointer;
    border-radius: 8px;
    transition: background-color 0.15s;
    margin: 1px 6px;

    &:hover {
      background: v-bind('themeVars.hoverColor');
    }

    &.selected {
      background: v-bind('themeVars.primaryColorSuppl');
      .cc-agent-item-name {
        color: v-bind('themeVars.primaryColor');
        font-weight: 500;
      }
    }
  }
  .cc-agent-item-left {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
  }
  .cc-agent-item-icon {
    flex-shrink: 0;
    color: v-bind('themeVars.primaryColor');
  }
  .cc-agent-item-name {
    font-size: 13px;
    color: v-bind('themeVars.textColorBase');
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .cc-agent-item-info {
    flex-shrink: 0;
    color: v-bind('themeVars.textColor3');
    &:hover {
      color: v-bind('themeVars.primaryColor');
    }
  }
  .cc-agent-empty {
    padding: 20px 14px;
    text-align: center;
    p {
      font-size: 13px;
      color: v-bind('themeVars.textColor3');
      margin-bottom: 10px;
    }
  }

  .cc-send-btn {
    border-radius: 8px;
    padding: 0 14px;
    height: 32px;
    font-weight: 500;
  }

  /* ========== 3. 推荐问题 ========== */
  .cc-suggested-section {
    width: 100%;
  }

  .cc-suggested-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 10px;
  }

  .cc-sq-card {
    background: v-bind('themeVars.cardColor');
    border: 1px solid v-bind('themeVars.borderColor');
    border-radius: 10px;
    padding: 12px 14px;
    cursor: pointer;
    transition: all 0.18s ease;

    &:hover {
      border-color: v-bind('themeVars.primaryColor');
      box-shadow: v-bind('themeVars.boxShadow2');
      transform: translateY(-1px);
    }
  }

  .cc-sq-text {
    font-size: 13px;
    line-height: 1.5;
    color: v-bind('themeVars.textColor2');
  }

  /* ========== 过渡动画 ========== */
  .cc-fade-enter-active {
    transition: opacity 0.3s ease, transform 0.3s ease;
  }
  .cc-fade-leave-active {
    transition: opacity 0.15s ease;
  }
  .cc-fade-enter-from {
    opacity: 0;
    transform: translateY(8px);
  }
  .cc-fade-leave-to {
    opacity: 0;
  }

  /* ========== 响应式 ========== */
  @media (max-width: 640px) {
    .cc-container {
      gap: 18px;
    }
    .cc-greeting {
      font-size: 22px;
    }
    .cc-suggested-grid {
      grid-template-columns: 1fr;
    }
    .cc-toolbar {
      flex-wrap: wrap;
      gap: 8px;
    }
    .cc-toolbar-right {
      width: 100%;
      justify-content: flex-end;
    }
  }
</style>
