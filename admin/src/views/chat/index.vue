<template>
  <div class="chat-app" :class="{ 'has-session': !!activeSessionId }">
    <!-- ==================== 左侧边栏：新建对话 + 历史会话列表（仅有会话记录时显示） ==================== -->
    <aside
      v-if="conversations.length"
      class="chat-sidebar"
      :class="{ collapsed: sidebarCollapsed }"
    >
      <div class="sidebar-top">
        <button v-if="!sidebarCollapsed" class="sidebar-new-btn" @click="newConversation">
          <n-icon size="15"><PlusOutlined /></n-icon>
          <span>新建对话</span>
        </button>
        <button class="sidebar-toggle-btn" @click="sidebarCollapsed = !sidebarCollapsed">
          <n-icon
            :size="14"
            :component="sidebarCollapsed ? MenuUnfoldOutlined : MenuFoldOutlined"
          />
        </button>
      </div>

      <div v-if="!sidebarCollapsed" class="sidebar-list">
        <div v-if="conversations.length" class="sidebar-group-label">历史会话</div>
        <div
          v-for="c in conversations"
          :key="c.id"
          class="sidebar-item"
          :class="{ active: c.id === activeSessionId }"
          @click="activateSession(c.id)"
        >
          <div class="sidebar-item-body">
            <div class="sidebar-item-title">{{ c.title || '新会话' }}</div>
            <div class="sidebar-item-time">{{ c.updatedAt }}</div>
          </div>
          <n-icon class="sidebar-item-del" @click.stop="deleteConversation(c.id)"
            ><DeleteOutlined
          /></n-icon>
        </div>
        <n-empty
          v-if="conversations.length === 0"
          description="暂无历史会话"
          size="small"
          style="margin-top: 30px"
        />
      </div>

      <!-- 折叠态只显示图标列表 -->
      <div v-else class="sidebar-list-mini">
        <div
          v-for="c in conversations.slice(0, 10)"
          :key="c.id"
          class="sidebar-mini-dot"
          :class="{ active: c.id === activeSessionId }"
          :title="c.title || '新会话'"
          @click="activateSession(c.id)"
        ></div>
      </div>
    </aside>

    <!-- ==================== 模式 A：欢迎/新建会话（无活跃会话时） ==================== -->
    <div v-if="!activeSessionId" class="chat-landing">
      <div class="cc-container">
        <!-- 大标题 -->
        <h1 class="cc-greeting">Hi，我是 SparkX，让你的知识触手可及</h1>

        <!-- 主输入区域 -->
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
          />

          <!-- 工具栏 -->
          <div class="cc-toolbar">
            <div class="cc-toolbar-left">
              <n-popover
                v-model:show="agentPopoverShow"
                trigger="click"
                placement="bottom-start"
                :width="280"
                :show-arrow="false"
                raw
              >
                <template #trigger>
                  <div class="cc-agent-trigger" :class="{ active: agentPopoverShow }">
                    <n-icon size="14" class="cc-agent-trigger-icon"><RobotOutlined /></n-icon>
                    <span class="cc-agent-trigger-text">{{
                      selectedAgentName || '选择智能体'
                    }}</span>
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
                  <div class="cc-agent-header">
                    <span class="cc-agent-header-title">选择智能体</span>
                    <a class="cc-agent-manage" @click="goAgentManage">
                      <n-icon size="12"><PlusOutlined /></n-icon> 管理
                    </a>
                  </div>
                  <div class="cc-agent-group-label">内置智能体</div>
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

        <!-- 推荐问题 -->
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

    <!-- ==================== 模式 B：聊天界面（有活跃会话时） ==================== -->
    <template v-else>
      <!-- 右侧主区域 -->
      <main class="chat-main">
        <!-- 顶栏 -->
        <header class="chat-header">
          <div class="chat-header-left">
            <n-button text size="medium" @click="goLanding">
              <template #icon
                ><n-icon><ArrowLeftOutlined /></n-icon
              ></template>
            </n-button>
            <span class="chat-header-title">{{ currentSessionTitle }}</span>
          </div>
          <div class="chat-header-right">
            <n-popover trigger="click" placement="bottom-end" :width="240" :show-arrow="false" raw>
              <template #trigger>
                <div class="chat-model-trigger">
                  <n-icon size="14"><RobotOutlined /></n-icon>
                  <span>{{ currentAgent?.name || '选择模型' }}</span>
                  <n-icon size="12"><CaretDownOutlined /></n-icon>
                </div>
              </template>
              <div
                class="model-panel"
                :style="{
                  background: themeVars.cardColor,
                  borderRadius: '10px',
                  boxShadow: themeVars.boxShadow2,
                  border: `1px solid ${themeVars.borderColor}`,
                }"
              >
                <div
                  v-for="agent in agents"
                  :key="agent.id"
                  class="model-item"
                  :class="{ selected: currentAgentId === agent.id }"
                  @click="switchAgent(agent.id as string)"
                >
                  <n-icon :size="16" :component="getAgentIcon(agent)" />
                  <span>{{ agent.name || '未命名' }}</span>
                </div>
              </div>
            </n-popover>
          </div>
        </header>

        <!-- 消息列表 -->
        <div ref="msgBoxRef" class="chat-messages">
          <div class="chat-messages-inner">
            <!-- 空状态 / 欢迎语 -->
            <template v-if="currentMessages.length === 0">
              <div class="chat-welcome">
                <div class="welcome-avatar">
                  <n-icon :size="28" color="#fff"><RobotOutlined /></n-icon>
                </div>
                <div class="welcome-text"
                  >你好！我是 {{ currentAgent?.name || 'SparkX' }}，有什么可以帮你的吗？</div
                >
              </div>
              <div v-if="currentSuggestedQuestions.length" class="chat-suggested-chips">
                <button
                  v-for="(q, i) in currentSuggestedQuestions"
                  :key="i"
                  class="suggested-chip"
                  @click="onSuggestedClick(q)"
                  >{{ q }}</button
                >
              </div>
              <n-empty v-else description="输入问题开始对话" style="margin-top: 60px" />
            </template>

            <!-- 消息列表 -->
            <div v-for="(msg, i) in currentMessages" :key="i" class="msg-row" :class="msg.role">
              <img
                class="msg-avatar"
                :src="msg.role === 'user' ? '/images/user.png' : '/images/robot.png'"
                alt=""
              />
              <div class="msg-content">
                <div v-if="msg.role === 'assistant'" class="msg-sender">
                  {{ currentAgent?.name || 'SparkX' }}
                  <n-tag
                    v-if="msg.role === 'assistant'"
                    size="tiny"
                    :bordered="false"
                    type="default"
                    class="msg-model-tag"
                    >思考结果</n-tag
                  >
                </div>
                <div class="msg-bubble" :class="msg.role" v-html="renderMd(displayedOf(i))"></div>
                <span v-if="msg.streaming" class="streaming-dots">●●●</span>
                <!-- 操作栏（仅 assistant 消息完成后） -->
                <div v-if="msg.role === 'assistant' && !msg.streaming" class="msg-actions">
                  <button class="msg-action-btn" title="复制" @click="copyText(msg.content)">
                    <n-icon size="14"><CopyOutlined /></n-icon>
                  </button>
                  <button class="msg-action-btn" title="重新生成" @click="regenerateMessage(i)">
                    <n-icon size="14"><ReloadOutlined /></n-icon>
                  </button>
                  <button
                    v-if="msg.references?.length"
                    class="msg-action-btn"
                    title="引用来源"
                    @click="openRefs(msg.references!)"
                  >
                    <n-icon size="14"><FileTextOutlined /></n-icon>
                  </button>
                  <button
                    v-if="msg.stageData"
                    class="msg-action-btn"
                    title="调用流程"
                    @click="openTrace(msg.stageData!)"
                  >
                    <n-icon size="14"><ApartmentOutlined /></n-icon>
                  </button>
                  <button v-if="msg.totalCost" class="msg-action-btn msg-cost" disabled>
                    总耗时 {{ (msg.totalCost / 1000).toFixed(2) }}s
                  </button>
                </div>
                <!-- 推荐追问（仅最后一条 assistant 消息后） -->
                <div
                  v-if="
                    msg.role === 'assistant' &&
                    !msg.streaming &&
                    i === currentMessages.length - 1 &&
                    suggestedFollowUp.length
                  "
                  class="follow-up-chips"
                >
                  <button
                    v-for="(fq, fi) in suggestedFollowUp"
                    :key="fi"
                    class="suggested-chip small"
                    @click="onSuggestedClick(fq)"
                    >{{ fq }}</button
                  >
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 输入区 -->
        <div class="chat-input-area">
          <div class="cc-input-card">
            <n-input
              ref="chatInputRef"
              v-model:value="chatInput"
              type="textarea"
              :autosize="{ minRows: 3, maxRows: 8 }"
              placeholder="和我聊聊天吧"
              :disabled="streaming"
              :bordered="false"
              class="cc-textarea"
              @keydown.enter.exact.prevent="onChatSend"
            />
            <div class="cc-toolbar">
              <div class="cc-toolbar-left">
                <!-- 聊天模式下可放快捷工具 -->
              </div>
              <div class="cc-toolbar-right">
                <n-button v-if="streaming" type="error" ghost size="small" round @click="onStop"
                  >停止</n-button
                >
                <n-button
                  v-else
                  type="primary"
                  size="small"
                  round
                  :disabled="!canChatSend"
                  @click="onChatSend"
                >
                  <template #icon
                    ><n-icon size="14"><SendOutlined /></n-icon
                  ></template>
                  发送
                </n-button>
              </div>
            </div>
          </div>
        </div>
      </main>
    </template>

    <!-- 引用来源抽屉 -->
    <n-drawer v-model:show="refsDrawerVisible" :width="420" placement="right">
      <n-drawer-content title="引用来源">
        <div v-for="r in refsDrawerData" :key="r.index" class="drawer-ref-item">
          <n-tag size="small" round type="info">{{ r.index }}</n-tag>
          <div class="drawer-ref-body" v-html="renderMd(r.content)"></div>
        </div>
        <n-empty v-if="refsDrawerData.length === 0" description="暂无引用" />
      </n-drawer-content>
    </n-drawer>

    <!-- 调用流程抽屉（RAG 各阶段上下文） -->
    <RagTraceDrawer v-model:show="traceDrawerVisible" :data="traceDrawerData" />
  </div>
</template>

<script setup lang="ts">
  import { ref, computed, onMounted, nextTick, watch } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
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
    ArrowLeftOutlined,
    DeleteOutlined,
    MenuFoldOutlined,
    MenuUnfoldOutlined,
    CopyOutlined,
    ReloadOutlined,
    FileTextOutlined,
    ApartmentOutlined,
  } from '@vicons/antd';
  import { createSessions } from '@/api/system/chat';
  import {
    getAgentEnabledList,
    streamAgentChat,
    type Agent,
    type AgentChatMessage,
    type AgentReference,
    type RagStageData,
  } from '@/api/system/agent';
  import RagTraceDrawer from '@/views/agent/components/RagTraceDrawer.vue';
  import { useTypewriter } from '@/composables/useTypewriter';
  import { marked } from 'marked';

  marked.use({ breaks: true, gfm: true });

  const route = useRoute();
  const router = useRouter();
  const message = useMessage();
  const themeVars = useThemeVars();

  // ========== 类型定义 ==========
  interface Conversation {
    id: string;
    title: string;
    agentId: string;
    messages: AgentChatMessage[];
    updatedAt: string;
  }

  // ========== 欢迎页状态 ==========
  const inputValue = ref('');
  const inputRef = ref<any>(null);
  const creating = ref(false);
  const agents = ref<Agent[]>([]);
  const selectedAgentId = ref<string | null>(null);
  const agentPopoverShow = ref(false);
  const suggestedQuestions = ref<string[]>([]);
  const sqLoading = ref(false);

  // 智能体持久化
  const SELECTED_AGENT_KEY = 'SPARKX_SELECTED_AGENT';

  const selectedAgentName = computed(() => {
    if (!selectedAgentId.value) return '';
    return agents.value.find((a) => a.id === selectedAgentId.value)?.name || '';
  });

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
    const name = agent.name || '';
    for (const [key, icon] of Object.entries(AGENT_ICON_MAP)) {
      if (name.includes(key)) return icon;
    }
    return RobotOutlined;
  }

  const canSend = computed(() => !!inputValue.value.trim() && !creating.value);

  async function loadAgents() {
    try {
      const resp: any = await getAgentEnabledList();
      const data = resp?.data ?? resp;
      const list: Agent[] = Array.isArray(data) ? data : data?.list ?? data?.records ?? [];
      agents.value = list;
    } catch (e: any) {
      message.error('加载智能体失败：' + (e?.message || e));
    }
  }

  function loadSuggested() {
    sqLoading.value = true;
    const agent = agents.value.find((a) => a.id === selectedAgentId.value);
    setTimeout(() => {
      suggestedQuestions.value = (agent?.suggestedQuestions as string[]) || [];
      sqLoading.value = false;
    }, 100);
  }

  function selectAgent(agent: Agent) {
    selectedAgentId.value = agent.id as string;
    localStorage.setItem(SELECTED_AGENT_KEY, selectedAgentId.value);
    agentPopoverShow.value = false;
    loadSuggested();
  }

  function restoreSelectedAgent() {
    const savedId = localStorage.getItem(SELECTED_AGENT_KEY);
    if (!savedId) return;
    if (agents.value.some((a) => a.id === savedId)) {
      selectedAgentId.value = savedId;
    } else {
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
    if (!activeSessionId.value) {
      inputValue.value = q;
      nextTick(() => inputRef.value?.focus());
    } else {
      chatInput.value = q;
      nextTick(() => chatInputRef.value?.focus());
    }
  }

  // ========== 会话管理 ==========
  const CONV_LS_PREFIX = 'sparkx_chat_conv_';
  const conversations = ref<Conversation[]>([]);
  const activeSessionId = ref<string>('');
  const sidebarCollapsed = ref(false);

  // 当前活跃会话的智能体
  const currentAgentId = ref<string>('');

  function convLsKey(id: string) {
    return `${CONV_LS_PREFIX}${id}`;
  }

  function loadConversationsFromStorage(): Conversation[] {
    const result: Conversation[] = [];
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (key?.startsWith(CONV_LS_PREFIX)) {
        try {
          const raw = JSON.parse(localStorage.getItem(key) || '');
          if (raw && raw.id) result.push(raw);
        } catch {
          // ignore corrupt
        }
      }
    }
    // 按更新时间倒序
    result.sort((a, b) => (b.updatedAt > a.updatedAt ? 1 : -1));
    return result;
  }

  function persistConv(c: Conversation) {
    localStorage.setItem(convLsKey(c.id), JSON.stringify(c));
  }

  function removeConvStorage(id: string) {
    localStorage.removeItem(convLsKey(id));
  }

  function genId(): string {
    return 's' + Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
  }

  function nowStr(): string {
    const d = new Date();
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(
      d.getMinutes()
    )}`;
  }

  function createLocalConv(agentId: string, title = '新会话'): Conversation {
    return { id: genId(), title, agentId, messages: [], updatedAt: nowStr() };
  }

  function newConversation() {
    if (streaming.value) {
      message.warning('生成中，请先停止');
      return;
    }
    // 回到欢迎输入页，发送第一条消息时才真正创建会话（豆包式交互）
    activeSessionId.value = '';
    resetStream();
    chatInput.value = '';
    inputValue.value = '';
    nextTick(() => inputRef.value?.focus());
  }

  function activateSession(id: string) {
    if (streaming.value) {
      message.warning('生成中，请先停止');
      return;
    }
    activeSessionId.value = id;
    const c = conversations.value.find((x) => x.id === id);
    if (c) {
      currentAgentId.value = c.agentId;
    }
    resetStream();
    chatInput.value = '';
    nextTick(scrollBottom);
  }

  function goLanding() {
    activeSessionId.value = '';
    chatInput.value = '';
  }

  function deleteConversation(id: string) {
    if (streaming.value) {
      message.warning('生成中，请先停止');
      return;
    }
    conversations.value = conversations.value.filter((c) => c.id !== id);
    removeConvStorage(id);
    if (activeSessionId.value === id) {
      goLanding();
    }
  }

  // ========== 聊天核心 ==========
  const chatInput = ref('');
  const chatInputRef = ref<any>(null);
  const streaming = ref(false);
  const msgBoxRef = ref<HTMLElement | null>(null);

  // 流式打字机
  const streamFullText = ref('');
  const streamDone = ref(false);
  const { displayed: typedDisplayed } = useTypewriter(
    () => streamFullText.value,
    () => streamDone.value
  );
  const streamingIdx = ref(-1);

  let abortCtl: AbortController | null = null;

  const currentMessages = computed(() => {
    const c = conversations.value.find((x) => x.id === activeSessionId.value);
    return c ? c.messages : [];
  });

  const currentAgent = computed(
    () => agents.value.find((a) => a.id === currentAgentId.value) || null
  );

  const currentSessionTitle = computed(() => {
    const c = conversations.value.find((x) => x.id === activeSessionId.value);
    return c?.title || '新对话';
  });

  const currentSuggestedQuestions = computed(() => {
    const agent = currentAgent.value;
    return (agent?.suggestedQuestions as string[]) || [];
  });

  // 推荐追问（从当前智能体取）
  const suggestedFollowUp = computed(() => {
    // 可以后续根据上下文动态生成，目前先用固定示例或智能体的建议问题
    const agent = currentAgent.value;
    const qs = (agent?.suggestedQuestions as string[]) || [];
    return qs.slice(0, 3);
  });

  const canChatSend = computed(
    () => !!chatInput.value.trim() && !!currentAgentId.value && !streaming.value
  );

  function displayedOf(i: number): string {
    const msg = currentMessages.value[i];
    if (msg.role === 'assistant' && i === streamingIdx.value && streaming.value) {
      return typedDisplayed.value;
    }
    return msg.content;
  }

  function resetStream() {
    streamFullText.value = '';
    streamDone.value = false;
    streamingIdx.value = -1;
  }

  async function doSend(query: string) {
    if (!currentAgentId.value || !activeSessionId.value) return;

    const conv = conversations.value.find((c) => c.id === activeSessionId.value);
    if (!conv) return;

    // 用户消息入队
    conv.messages.push({ role: 'user', content: query });
    // 首条消息作为标题
    if (conv.title === '新会话') {
      conv.title = query.length > 20 ? query.slice(0, 20) + '...' : query;
    }
    conv.updatedAt = nowStr();

    // 占位 assistant 消息
    resetStream();
    streamingIdx.value = conv.messages.length;
    conv.messages.push({
      role: 'assistant',
      content: '',
      streaming: true,
      totalCost: 0,
      stageTimings: {},
    });
    streaming.value = true;
    conv.updatedAt = nowStr();
    persistConv(conv);

    await scrollBottom();

    abortCtl = new AbortController();
    try {
      await streamAgentChat(
        { agentId: currentAgentId.value, conversationId: conv.id, query },
        {
          onAnswer: (token) => {
            streamFullText.value += token;
          },
          onComplete: (payload) => {
            const msg = conv.messages[streamingIdx.value];
            if (msg) {
              msg.content = payload.answer;
              msg.references = payload.references;
              msg.stageTimings = payload.stageTimings;
              msg.totalCost = payload.totalCost;
              msg.stageData = payload.stageData;
              msg.streaming = false;
            }
            streamDone.value = true;
            conv.updatedAt = nowStr();
            conv.messages = [...conv.messages]; // 触发响应式更新
            persistConv(conv);
          },
          onError: (errMsg) => {
            const msg = conv.messages[streamingIdx.value];
            if (msg) {
              msg.content = `\u26A0\uFE0F ${errMsg}`;
              msg.streaming = false;
            }
            streamDone.value = true;
            message.error(errMsg);
          },
        },
        abortCtl.signal
      );
    } catch (e: any) {
      if (e?.name !== 'AbortError') {
        const msg = conv.messages[streamingIdx.value];
        if (msg) {
          msg.content = `\u26A0\uFE0F 请求失败：${e?.message || e}`;
          msg.streaming = false;
        }
        message.error('对话请求失败');
      }
    } finally {
      streaming.value = false;
      nextTick(() => {
        const msg = conv.messages[streamingIdx.value];
        if (msg) msg.content = streamFullText.value || msg.content;
        scrollBottom();
        persistConv(conv);
      });
    }
  }

  async function onChatSend() {
    const q = chatInput.value.trim();
    if (!q || streaming.value || !currentAgentId.value) return;
    chatInput.value = '';
    await doSend(q);
  }

  function onStop() {
    abortCtl?.abort();
    streaming.value = false;
    message.info('已停止生成');
  }

  function regenerateMessage(idx: number) {
    if (streaming.value) return;
    const msgs = currentMessages.value;
    if (idx < 1 || msgs[idx].role !== 'assistant') return;
    // 找到对应的 user 消息重新发送
    const userMsg = msgs[idx - 1];
    if (!userMsg || userMsg.role !== 'user') return;
    // 截断到该 user 消息
    const conv = conversations.value.find((c) => c.id === activeSessionId.value)!;
    conv.messages = msgs.slice(0, idx - 1);
    persistConv(conv);
    chatInput.value = userMsg.content;
    nextTick(() => onChatSend());
  }

  // ========== 引用来源 ==========
  const refsDrawerVisible = ref(false);
  const refsDrawerData = ref<AgentReference[]>([]);
  function openRefs(refs: AgentReference[]) {
    refsDrawerData.value = refs || [];
    refsDrawerVisible.value = true;
  }

  // ========== 调用流程（RAG 各阶段上下文） ==========
  const traceDrawerVisible = ref(false);
  const traceDrawerData = ref<RagStageData | null>(null);
  function openTrace(data: RagStageData) {
    traceDrawerData.value = data;
    traceDrawerVisible.value = true;
  }

  // ========== 工具方法 ==========
  function copyText(text: string) {
    navigator.clipboard
      .writeText(text)
      .then(() => message.success('已复制'))
      .catch(() => {});
  }

  function renderMd(text: string): string {
    if (!text) return '';
    try {
      const normalized = text.replace(/^(#{1,6})(?=\S)/gm, '$1 ');
      return marked.parse(normalized) as string;
    } catch {
      return text;
    }
  }

  async function scrollBottom() {
    await nextTick();
    if (msgBoxRef.value) {
      msgBoxRef.value.scrollTop = msgBoxRef.value.scrollHeight;
    }
  }

  function switchAgent(agentId: string) {
    currentAgentId.value = agentId;
    const conv = conversations.value.find((c) => c.id === activeSessionId.value);
    if (conv) {
      conv.agentId = agentId;
      persistConv(conv);
    }
  }

  // ========== 欢迎页发送 → 进入聊天模式 ==========
  async function handleSend() {
    const q = inputValue.value.trim();
    if (!q) return;
    creating.value = true;
    try {
      const aid = selectedAgentId.value || (agents.value[0]?.id as string) || '';
      // 先创建本地会话
      const conv = createLocalConv(aid, q.length > 20 ? q.slice(0, 20) + '...' : q);
      conversations.value.unshift(conv);
      persistConv(conv);

      // 切换到聊天模式
      activeSessionId.value = conv.id;
      currentAgentId.value = aid;

      // 尝试同步到后端（失败不影响本地使用）
      try {
        const resp: any = await createSessions({
          agent_id: aid,
          query: q,
          title: conv.title,
          agent_config: { enabled: true },
        });
        const body = resp ?? {};
        const data = body.data !== undefined ? body.data : body;
        const remoteId = data?.id ?? body?.id;
        if (remoteId && remoteId !== conv.id) {
          // 后端返回了不同的 id，迁移数据
          removeConvStorage(conv.id);
          conv.id = remoteId;
          activeSessionId.value = remoteId;
          persistConv(conv);
        }
      } catch {
        // 后端未就绪，纯本地模式继续
      }

      // 发送消息
      chatInput.value = q;
      inputValue.value = '';
      await nextTick();
      await doSend(q);
    } catch (e: any) {
      console.warn('[chat] 发送失败', e);
      message.error('发送失败，请重试');
    } finally {
      creating.value = false;
    }
  }

  // ========== 初始化 ==========
  onMounted(async () => {
    await loadAgents();
    restoreSelectedAgent();
    loadSuggested();

    // 加载本地会话历史
    conversations.value = loadConversationsFromStorage();

    // 如果路由带 :id 参数，激活对应会话
    const routeId = String(route.params.id || '');
    if (routeId) {
      let conv = conversations.value.find((c) => c.id === routeId);
      if (!conv) {
        // 路由中的会话不在本地，创建空壳并尝试加载
        conv = createLocalConv('', routeId.slice(0, 12));
        conversations.value.unshift(conv);
        persistConv(conv);
      }
      activeSessionId.value = conv.id;
      // 从 query 取 agentId 和初始问题
      const qAgentId = (route.query.agentId as string) || '';
      if (qAgentId) currentAgentId.value = qAgentId;
      else if (conv.agentId) currentAgentId.value = conv.agentId;
      else if (selectedAgentId.value) currentAgentId.value = selectedAgentId.value;

      const initQ = (route.query.q as string) || '';
      if (initQ) {
        await nextTick();
        chatInput.value = initQ;
        onChatSend();
      }
    }
  });

  // 监听路由变化（用户在聊天中点击浏览器前进/后退）
  watch(
    () => route.params.id,
    (newId) => {
      const id = String(newId || '');
      if (id && id !== activeSessionId.value) {
        activateSession(id);
      } else if (!id && activeSessionId.value) {
        goLanding();
      }
    }
  );
</script>

<style lang="less" scoped>
  .chat-app {
    display: flex;
    width: 100%;
    height: calc(100vh - 56px);
    background: v-bind('themeVars.bodyColor');
    overflow: hidden;

    &.has-session {
      padding: 0;
    }
  }

  /* ========== 欢迎/新建模式 ========== */
  .chat-landing {
    flex: 1;
    min-width: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: calc(100vh - 56px);
    padding: 24px;
    box-sizing: border-box;
  }

  .cc-container {
    width: 100%;
    max-width: 720px;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 24px;
  }

  .cc-greeting {
    margin: 8px 0 0;
    font-size: 26px;
    font-weight: 600;
    color: v-bind('themeVars.textColorBase');
    letter-spacing: 0.3px;
    text-align: center;
    line-height: 1.4;
  }

  .cc-input-card {
    width: 100%;
    background: v-bind('themeVars.cardColor');
    border: 1px solid v-bind('themeVars.borderColor');
    border-radius: 14px;
    padding: 16px 18px;
    box-shadow: v-bind('themeVars.boxShadow1');
    transition: border-color 0.2s, box-shadow 0.2s;
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

  .cc-agent-panel {
    padding: 4px 0;
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

  /* ========== 左侧边栏（常驻） ========== */
  .sidebar-group-label {
    padding: 6px 12px 4px;
    font-size: 12px;
    color: v-bind('themeVars.textColor3');
    user-select: none;
  }

  .chat-sidebar {
    width: 260px;
    flex-shrink: 0;
    display: flex;
    flex-direction: column;
    background: v-bind('themeVars.cardColor');
    border-right: 1px solid v-bind('themeVars.borderColor');
    transition: width 0.25s ease;
    overflow: hidden;

    &.collapsed {
      width: 48px;
    }
  }

  .sidebar-top {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 12px;
    border-bottom: 1px solid v-bind('themeVars.dividerColor');
  }

  .sidebar-new-btn {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    padding: 7px 12px;
    border: 1px solid v-bind('themeVars.primaryColor');
    border-radius: 8px;
    background: transparent;
    color: v-bind('themeVars.primaryColor');
    cursor: pointer;
    font-size: 13px;
    font-weight: 500;
    transition: all 0.15s;
    &:hover {
      background: v-bind('themeVars.primaryColorSuppl');
    }
  }

  .sidebar-toggle-btn {
    flex-shrink: 0;
    width: 30px;
    height: 30px;
    display: flex;
    align-items: center;
    justify-content: center;
    border: none;
    background: none;
    border-radius: 6px;
    cursor: pointer;
    color: v-bind('themeVars.textColor3');
    transition: all 0.15s;
    &:hover {
      background: v-bind('themeVars.hoverColor');
      color: v-bind('themeVars.textColorBase');
    }
  }

  .sidebar-list {
    flex: 1;
    overflow-y: auto;
    padding: 8px;
  }

  .sidebar-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 12px;
    border-radius: 10px;
    cursor: pointer;
    transition: all 0.15s;
    margin-bottom: 2px;
    &:hover {
      background: v-bind('themeVars.hoverColor');
      .sidebar-item-del {
        opacity: 1;
      }
    }
    &.active {
      background: v-bind('themeVars.hoverColor');
      border-left: 3px solid v-bind('themeVars.primaryColor');
      .sidebar-item-title {
        color: v-bind('themeVars.textColorBase');
        font-weight: 600;
      }
    }
  }
  .sidebar-item-body {
    flex: 1;
    overflow: hidden;
    min-width: 0;
  }
  .sidebar-item-title {
    font-size: 13px;
    color: v-bind('themeVars.textColorBase');
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .sidebar-item-time {
    font-size: 11px;
    color: v-bind('themeVars.textColor3');
    margin-top: 2px;
  }
  .sidebar-item-del {
    opacity: 0;
    color: v-bind('themeVars.errorColor');
    font-size: 14px;
    transition: all 0.15s;
    flex-shrink: 0;
    padding: 2px;
    border-radius: 4px;
    &:hover {
      background: v-bind('themeVars.errorColorSuppl');
    }
  }

  .sidebar-list-mini {
    flex: 1;
    overflow-y: auto;
    padding: 8px;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
  }
  .sidebar-mini-dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: v-bind('themeVars.borderColor');
    cursor: pointer;
    transition: all 0.15s;
    flex-shrink: 0;
    &.active {
      background: v-bind('themeVars.primaryColor');
      transform: scale(1.3);
    }
    &:hover {
      background: v-bind('themeVars.textColor3');
    }
  }

  /* ========== 聊天模式：主区域 ========== */
  .chat-main {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-width: 0;
    height: 100%;
    overflow: hidden;
  }

  /* 顶栏 */
  .chat-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px 24px;
    border-bottom: 1px solid v-bind('themeVars.borderColor');
    background: v-bind('themeVars.cardColor');
    flex-shrink: 0;
  }
  .chat-header-left {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .chat-header-title {
    font-size: 15px;
    font-weight: 600;
    color: v-bind('themeVars.textColorBase');
  }
  .chat-header-right {
    display: flex;
    align-items: center;
  }

  .chat-model-trigger {
    display: flex;
    align-items: center;
    gap: 5px;
    padding: 5px 12px;
    border-radius: 8px;
    cursor: pointer;
    font-size: 13px;
    color: v-bind('themeVars.textColor2');
    background: v-bind('themeVars.actionColor');
    border: 1px solid transparent;
    transition: all 0.15s;
    &:hover {
      border-color: v-bind('themeVars.borderColor');
      color: v-bind('themeVars.textColorBase');
    }
  }

  .model-panel {
    padding: 4px 0;
  }
  .model-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 14px;
    cursor: pointer;
    font-size: 13px;
    color: v-bind('themeVars.textColorBase');
    transition: background 0.12s;
    margin: 1px 6px;
    border-radius: 8px;
    &:hover {
      background: v-bind('themeVars.hoverColor');
    }
    &.selected {
      background: v-bind('themeVars.primaryColorSuppl');
      color: v-bind('themeVars.primaryColor');
      font-weight: 500;
    }
  }

  /* 消息区：外层滚动，内层容器限宽 800px 居中 */
  .chat-messages {
    flex: 1;
    overflow-y: auto;
    padding: 24px 32px;
  }
  .chat-messages-inner {
    max-width: 1000px;
    margin: 0 auto;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  /* 欢迎语 */
  .chat-welcome {
    display: flex;
    align-items: flex-start;
    gap: 12px;
    padding: 20px 0;
    margin-bottom: 12px;
  }
  .welcome-avatar {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    flex-shrink: 0;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .welcome-text {
    font-size: 15px;
    line-height: 1.6;
    color: v-bind('themeVars.textColorBase');
  }

  /* 推荐问题 chip */
  .chat-suggested-chips {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 20px;
  }
  .suggested-chip {
    padding: 8px 16px;
    border: 1px solid v-bind('themeVars.borderColor');
    border-radius: 20px;
    background: v-bind('themeVars.cardColor');
    font-size: 13px;
    color: v-bind('themeVars.textColor2');
    cursor: pointer;
    transition: all 0.15s;
    font-family: inherit;
    &:hover {
      border-color: v-bind('themeVars.primaryColor');
      color: v-bind('themeVars.primaryColor');
    }
    &.small {
      padding: 5px 12px;
      font-size: 12px;
    }
  }

  /* 消息行 */
  .msg-row {
    display: flex;
    gap: 12px;
    margin-bottom: 20px;
    max-width: 100%;

    &.user {
      flex-direction: row-reverse;
      .msg-content {
        align-items: flex-end;
      }
      .msg-bubble {
        background: v-bind('themeVars.primaryColor');
        color: #fff;
        border-radius: 16px 4px 16px 16px;
      }
    }
    &.assistant {
      .msg-bubble {
        background: v-bind('themeVars.cardColor');
        border: 1px solid v-bind('themeVars.borderColor');
        border-radius: 4px 16px 16px 16px;
      }
    }
  }

  .msg-avatar {
    flex-shrink: 0;
    width: 34px;
    height: 34px;
    border-radius: 50%;
    object-fit: cover;
    background: v-bind('themeVars.actionColor');
    display: block;
  }

  .msg-content {
    display: flex;
    flex-direction: column;
    max-width: 72%;
    gap: 6px;
  }

  .msg-sender {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    font-weight: 600;
    color: v-bind('themeVars.textColorBase');
    margin-bottom: 2px;
  }
  .msg-model-tag {
    font-weight: 400;
    font-size: 11px;
  }

  .msg-bubble {
    padding: 12px 16px;
    font-size: 14px;
    line-height: 1.7;
    word-break: break-word;
    :deep(p) {
      margin: 4px 0;
    }
    :deep(pre) {
      background: #282c34;
      color: #abb2bf;
      padding: 12px;
      border-radius: 8px;
      overflow-x: auto;
      font-size: 13px;
    }
    :deep(code) {
      background: rgba(0, 0, 0, 0.06);
      padding: 2px 5px;
      border-radius: 4px;
      font-size: 13px;
    }
    :deep(pre code) {
      background: transparent;
      padding: 0;
    }
    :deep(table) {
      border-collapse: collapse;
      th,
      td {
        border: 1px solid #ddd;
        padding: 4px 8px;
      }
    }
  }

  .streaming-dots {
    color: v-bind('themeVars.primaryColor');
    font-size: 11px;
    animation: blink 1s infinite;
    letter-spacing: 2px;
  }
  @keyframes blink {
    0%,
    100% {
      opacity: 0.3;
    }
    50% {
      opacity: 1;
    }
  }

  /* 操作按钮 */
  .msg-actions {
    display: flex;
    align-items: center;
    gap: 4px;
    flex-wrap: wrap;
    margin-top: 2px;
  }
  .msg-action-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    border: none;
    background: none;
    border-radius: 6px;
    cursor: pointer;
    color: v-bind('themeVars.textColor3');
    transition: all 0.12s;
    &:hover {
      background: v-bind('themeVars.hoverColor');
      color: v-bind('themeVars.textColorBase');
    }
    &.msg-cost {
      width: auto;
      padding: 0 8px;
      font-size: 11px;
      color: v-bind('themeVars.textColor3');
      cursor: default;
    }
    &:hover.msg-cost {
      background: none;
    }
  }

  .follow-up-chips {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 4px;
  }

  /* 输入区 —— 复用欢迎页 .cc-input-card 样式 */
  .chat-input-area {
    flex-shrink: 0;
    padding: 12px 24px 20px;
    background: v-bind('themeVars.bodyColor');
    display: flex;
    justify-content: center;
  }
  .chat-input-area .cc-input-card {
    max-width: 1000px;
    width: 100%;
  }

  /* 引用来源抽屉 */
  .drawer-ref-item {
    margin-bottom: 16px;
    padding-bottom: 16px;
    border-bottom: 1px solid v-bind('themeVars.dividerColor');
    &:last-child {
      border-bottom: none;
    }
  }
  .drawer-ref-body {
    margin-top: 6px;
    font-size: 13px;
    line-height: 1.7;
    color: v-bind('themeVars.textColor2');
    word-break: break-word;
    :deep(p) {
      margin: 4px 0;
    }
    :deep(pre) {
      background: #f5f5f5;
      padding: 8px;
      border-radius: 4px;
      overflow-x: auto;
      font-size: 12px;
    }
    :deep(code) {
      background: rgba(0, 0, 0, 0.06);
      padding: 1px 4px;
      border-radius: 3px;
      font-size: 12px;
    }
  }

  /* 响应式 */
  @media (max-width: 768px) {
    .chat-sidebar {
      position: absolute;
      z-index: 10;
      height: 100%;
      box-shadow: 4px 0 16px rgba(0, 0, 0, 0.1);
    }
    .chat-sidebar.collapsed {
      width: 0;
      overflow: hidden;
    }
    .chat-messages {
      padding: 16px;
    }
    .msg-content {
      max-width: 88%;
    }
    .chat-input-area {
      padding: 10px 12px 12px;
    }
  }
</style>
