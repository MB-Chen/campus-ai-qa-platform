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
        <h1 class="cc-greeting">
          {{ selectedAgentWelcome || 'Hi，我是 SparkX，让你的知识触手可及' }}
        </h1>

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
                    <n-icon size="14" class="cc-agent-trigger-icon">
                      <DeploymentUnitOutlined v-if="selectedKind === 'workflow'" />
                      <RobotOutlined v-else />
                    </n-icon>
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
                  <!-- 智能体分组 -->
                  <div class="cc-agent-group-label">智能体</div>
                  <div v-if="agentItems.length" class="cc-agent-list">
                    <div
                      v-for="item in agentItems"
                      :key="'a_' + item.id"
                      class="cc-agent-item"
                      :class="{
                        selected:
                          selectedKind === 'agent' && selectedAgentId === item.id,
                      }"
                      @click="selectTarget(item)"
                    >
                      <div class="cc-agent-item-left">
                        <n-icon
                          :size="18"
                          :component="getAgentIcon(item)"
                          class="cc-agent-item-icon"
                        />
                        <span class="cc-agent-item-name">{{ item.name || '未命名' }}</span>
                      </div>
                      <n-tooltip v-if="item.description" trigger="hover" :delay="500">
                        <template #trigger>
                          <n-icon :size="14" class="cc-agent-item-info"
                            ><InfoCircleOutlined
                          /></n-icon>
                        </template>
                        {{ item.description }}
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

                  <!-- 编排智能体分组 -->
                  <div class="cc-agent-group-label">编排智能体</div>
                  <div v-if="workflowItems.length" class="cc-agent-list">
                    <div
                      v-for="item in workflowItems"
                      :key="'w_' + item.id"
                      class="cc-agent-item"
                      :class="{
                        selected:
                          selectedKind === 'workflow' && selectedAgentId === item.id,
                      }"
                      @click="selectTarget(item)"
                    >
                      <div class="cc-agent-item-left">
                        <n-icon :size="18" class="cc-agent-item-icon">
                          <DeploymentUnitOutlined />
                        </n-icon>
                        <span class="cc-agent-item-name">{{ item.name || '未命名编排' }}</span>
                      </div>
                      <n-tooltip v-if="item.description" trigger="hover" :delay="500">
                        <template #trigger>
                          <n-icon :size="14" class="cc-agent-item-info"
                            ><InfoCircleOutlined
                          /></n-icon>
                        </template>
                        {{ item.description }}
                      </n-tooltip>
                    </div>
                  </div>
                  <div v-else class="cc-agent-empty">
                    <p>暂无可用编排</p>
                    <n-button size="tiny" type="primary" secondary @click="goWorkflowManage">
                      <template #icon
                        ><n-icon :size="12"><PlusOutlined /></n-icon
                      ></template>
                      去新建
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
                  <n-icon size="14">
                    <DeploymentUnitOutlined v-if="currentKind === 'workflow'" />
                    <RobotOutlined v-else />
                  </n-icon>
                  <span>{{ currentTarget?.name || '选择模型' }}</span>
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
                <!-- 智能体分组 -->
                <div class="model-group-label">智能体</div>
                <div
                  v-for="item in agentItems"
                  :key="'h_a_' + item.id"
                  class="model-item"
                  :class="{
                    selected: currentKind === 'agent' && currentAgentId === item.id,
                  }"
                  @click="switchAgent(item)"
                >
                  <n-icon :size="16" :component="getAgentIcon(item)" />
                  <span>{{ item.name || '未命名' }}</span>
                </div>
                <!-- 编排智能体分组 -->
                <div class="model-group-label">编排智能体</div>
                <div
                  v-for="item in workflowItems"
                  :key="'h_w_' + item.id"
                  class="model-item"
                  :class="{
                    selected:
                      currentKind === 'workflow' && currentAgentId === item.id,
                  }"
                  @click="switchAgent(item)"
                >
                  <n-icon :size="16"><DeploymentUnitOutlined /></n-icon>
                  <span>{{ item.name || '未命名编排' }}</span>
                </div>
              </div>
            </n-popover>
          </div>
        </header>

        <!-- 消息列表 -->
        <div ref="msgBoxRef" class="chat-messages">
          <div class="chat-messages-inner">
            <!-- 空状态：复刻欢迎页大居中布局 -->
            <template v-if="currentMessages.length === 0">
              <div class="chat-empty-hero">
                <div class="empty-hero-icon">
                  <n-icon :size="40" color="#fff"><RobotOutlined /></n-icon>
                </div>
                <h2 class="empty-hero-title">
                  你好，我是 {{ currentTarget?.name || 'SparkX' }}
                </h2>
                <p class="empty-hero-sub">{{ currentWelcome || '有什么可以帮你的吗？' }}</p>

                <div
                  v-if="currentSuggestedQuestions.length"
                  class="empty-hero-suggested"
                >
                  <div class="empty-hero-grid">
                    <div
                      v-for="(q, qi) in currentSuggestedQuestions"
                      :key="qi"
                      class="empty-hero-card"
                      @click="onSuggestedClick(q)"
                    >
                      <n-icon size="14" class="empty-hero-card-icon"
                        ><ThunderboltOutlined
                      /></n-icon>
                      <span class="empty-hero-card-text">{{ q }}</span>
                    </div>
                  </div>
                </div>

                <div v-else class="empty-hero-hint">
                  <n-icon size="13"><EditOutlined /></n-icon>
                  <span>在下方输入框开始对话</span>
                </div>
              </div>
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
                  {{ currentTarget?.name || 'SparkX' }}
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
                  <button
                    v-if="msg.workflowSteps?.length"
                    class="msg-action-btn"
                    title="调用流程"
                    @click="openWorkflowTrace(msg)"
                  >
                    <n-icon size="14"><ApartmentOutlined /></n-icon>
                  </button>
                  <button v-if="msg.totalCost" class="msg-action-btn msg-cost" disabled>
                    总耗时 {{ (msg.totalCost / 1000).toFixed(2) }}s
                  </button>
                  <button v-if="msg.totalTokens" class="msg-action-btn msg-cost" disabled>
                    {{ msg.totalTokens }} tokens
                  </button>
                </div>
                <!-- 调用流程步骤（编排智能体消息，可折叠） -->
                <div
                  v-if="msg.role === 'assistant' && msg.workflowSteps?.length"
                  class="msg-step-timeline"
                >
                  <div class="step-summary-bar" @click="toggleMsgStepsExpand(i)">
                    <n-icon size="13" class="step-summary-caret" :class="{ open: isMsgStepsExpanded(i) }">
                      <CaretRightOutlined />
                    </n-icon>
                    <n-icon size="13"><ApartmentOutlined /></n-icon>
                    <span class="step-summary-label">调用流程</span>
                    <span class="step-summary-chain">
                      <template v-for="(step, si) in msg.workflowSteps" :key="si">
                        <span class="step-chain-node">{{ stepHeadMeta(step).name || step.nodeType || '节点' }}</span>
                        <span v-if="si < msg.workflowSteps!.length - 1" class="step-chain-arrow">›</span>
                      </template>
                    </span>
                    <span v-if="msg.totalCost" class="step-summary-cost">
                      {{ (msg.totalCost / 1000).toFixed(2) }}s
                    </span>
                  </div>
                  <n-collapse-transition :show="isMsgStepsExpanded(i)">
                    <div class="step-timeline">
                      <div
                        v-for="(step, si) in msg.workflowSteps"
                        :key="step.cell || si"
                        class="step-card"
                        :class="{
                          'is-open': isStepExpanded(i, si),
                          'is-running': step.status === 'running',
                        }"
                      >
                        <div class="step-head" @click="toggleStepExpand(i, si)">
                          <n-icon
                            :component="CaretRightOutlined"
                            :size="14"
                            :style="{ transform: isStepExpanded(i, si) ? 'rotate(90deg)' : '' }"
                            class="step-caret"
                          />
                          <span class="step-num">{{ step.step || si + 1 }}</span>
                          <n-icon
                            v-if="stepHeadIcon(step)"
                            :component="stepHeadIcon(step)"
                            :color="stepHeadMeta(step).color"
                            :size="16"
                          />
                          <span class="step-name">{{
                            stepHeadMeta(step).name || step.nodeType || '节点'
                          }}</span>
                          <span
                            v-if="stepHeadMeta(step).costMs != null"
                            class="cost-badge"
                            :class="costClass(stepHeadMeta(step).costMs)"
                          >
                            {{ (stepHeadMeta(step).costMs / 1000).toFixed(2) }}s
                          </span>
                          <span v-if="step.status === 'running'" class="step-spin"></span>
                          <span v-else class="step-check">✓</span>
                        </div>
                        <n-collapse-transition :show="isStepExpanded(i, si)">
                          <div class="step-body">
                            <node-body :item="step" />
                          </div>
                        </n-collapse-transition>
                      </div>
                    </div>
                  </n-collapse-transition>
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

    <!-- 调用流程抽屉（智能体 RAG 各阶段上下文） -->
    <RagTraceDrawer v-model:show="traceDrawerVisible" :data="traceDrawerData" />

    <!-- 调用流程抽屉（编排智能体各节点步骤） -->
    <WorkflowTraceDrawer
      v-model:show="workflowTraceVisible"
      :steps="workflowTraceSteps"
      :meta="workflowTraceMeta"
    />
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
    DeploymentUnitOutlined,
    CaretRightOutlined,
    ThunderboltOutlined,
    EditOutlined,
  } from '@vicons/antd';
  import {
    createSessions,
    getSessionsList,
    getSessionMessages,
    saveSessionMessage,
    deleteSession,
  } from '@/api/system/chat';
  import {
    getAgentEnabledList,
    streamAgentChat,
    type Agent,
    type AgentChatMessage,
    type AgentReference,
    type RagStageData,
  } from '@/api/system/agent';
  import {
    getWorkflowList,
    streamWorkflowChat,
    getRunDetail,
    type Workflow,
    type WorkflowStep,
  } from '@/api/system/workflow';
  import { NodeBody, nodeMeta, costClass } from '@/views/workflow/menu/runtimeDetail.js';
  import RagTraceDrawer from '@/views/agent/components/RagTraceDrawer.vue';
  import WorkflowTraceDrawer from '@/views/chat/components/WorkflowTraceDrawer.vue';
  import { useTypewriter } from '@/composables/useTypewriter';
  import { marked } from 'marked';

  marked.use({ breaks: true, gfm: true });

  const route = useRoute();
  const router = useRouter();
  const message = useMessage();
  const themeVars = useThemeVars();

  // ========== 类型定义 ==========
  /** 对话目标类型：智能体 / 编排智能体 */
  type ChatKind = 'agent' | 'workflow';

  interface Conversation {
    id: string;
    title: string;
    agentId: string;
    /** 本会话绑定的目标类型（agent 智能体 / workflow 编排智能体） */
    kind: ChatKind;
    messages: AgentChatMessage[];
    updatedAt: string;
  }

  /** 下拉里统一展示的条目（智能体 / 编排智能体都映射成它） */
  interface TargetItem {
    id: string;
    name?: string;
    description?: string;
    kind: ChatKind;
    /** 智能体的推荐问题（编排暂无） */
    suggestedQuestions?: string[];
  }

  // ========== 欢迎页状态 ==========
  const inputValue = ref('');
  const inputRef = ref<any>(null);
  const creating = ref(false);
  const agents = ref<Agent[]>([]);
  const workflows = ref<Workflow[]>([]);
  const selectedAgentId = ref<string | null>(null);
  /** 当前选中目标的类型：agent / workflow */
  const selectedKind = ref<ChatKind>('agent');
  const agentPopoverShow = ref(false);
  const suggestedQuestions = ref<string[]>([]);
  const sqLoading = ref(false);

  // 智能体持久化（同时存类型，以便还原「编排智能体」选择）
  const SELECTED_AGENT_KEY = 'SPARKX_SELECTED_AGENT';
  const SELECTED_KIND_KEY = 'SPARKX_SELECTED_KIND';

  const selectedAgentName = computed(() => {
    if (!selectedAgentId.value) {
      return '';
    }
    if (selectedKind.value === 'workflow') {
      return workflows.value.find((w) => w.id === selectedAgentId.value)?.name || '';
    }
    return agents.value.find((a) => a.id === selectedAgentId.value)?.name || '';
  });

  /** 欢迎页：当前选中智能体的开场白（编排智能体暂无） */
  const selectedAgentWelcome = computed(() => {
    if (selectedKind.value === 'workflow') return '';
    const agent = agents.value.find((a) => a.id === selectedAgentId.value);
    return agent?.welcome || '';
  });

  /** 智能体分组（映射成统一 TargetItem） */
  const agentItems = computed<TargetItem[]>(
    () => agents.value.map((a) => ({
      id: a.id as string,
      name: a.name,
      description: a.description,
      kind: 'agent',
      suggestedQuestions: a.suggestedQuestions as string[] | undefined,
    }))
  );
  /** 编排智能体分组（仅展示启用项，status !== 2） */
  const workflowItems = computed<TargetItem[]>(() =>
    workflows.value
      .filter((w) => w.status !== 2)
      .map((w) => ({
        id: w.id as string,
        name: w.name,
        description: w.description,
        kind: 'workflow',
      }))
  );

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
  function getAgentIcon(agent: Agent | TargetItem): any {
    if ((agent as TargetItem).kind === 'workflow') {
      return DeploymentUnitOutlined;
    }
    const name = agent.name || '';
    for (const [key, icon] of Object.entries(AGENT_ICON_MAP)) {
      if (name.includes(key)) return icon;
    }
    return RobotOutlined;
  }

  const canSend = computed(
    () => !!inputValue.value.trim() && !creating.value && !!selectedAgentId.value
  );

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

  /** 加载编排列表（复用分页接口，取前 100 条，仅展示启用项） */
  async function loadWorkflows() {
    try {
      const resp: any = await getWorkflowList({ page: 1, size: 100 });
      const data = resp?.data ?? {};
      const arr: Workflow[] = Array.isArray(data?.data)
        ? data.data
        : Array.isArray(data?.list)
          ? data.list
          : Array.isArray(data)
            ? data
            : [];
      workflows.value = arr.filter((x: any) => x && x.id != null);
    } catch (e: any) {
      // 编排加载失败不阻塞主流程
      workflows.value = [];
    }
  }

  function loadSuggested() {
    sqLoading.value = true;
    const agent = agents.value.find((a) => a.id === selectedAgentId.value);
    setTimeout(() => {
      // 编排智能体暂无推荐问题
      suggestedQuestions.value =
        selectedKind.value === 'agent' ? (agent?.suggestedQuestions as string[]) || [] : [];
      sqLoading.value = false;
    }, 100);
  }

  /** 统一选择：智能体或编排智能体 */
  function selectTarget(item: TargetItem) {
    selectedKind.value = item.kind;
    selectedAgentId.value = item.id;
    localStorage.setItem(SELECTED_AGENT_KEY, item.id);
    localStorage.setItem(SELECTED_KIND_KEY, item.kind);
    agentPopoverShow.value = false;
    loadSuggested();
  }

  function restoreSelectedAgent() {
    const savedId = localStorage.getItem(SELECTED_AGENT_KEY);
    if (!savedId) return;
    const savedKind = (localStorage.getItem(SELECTED_KIND_KEY) as ChatKind) || 'agent';
    if (savedKind === 'workflow') {
      if (workflows.value.some((w) => w.id === savedId)) {
        selectedKind.value = 'workflow';
        selectedAgentId.value = savedId;
      } else {
        localStorage.removeItem(SELECTED_AGENT_KEY);
        localStorage.removeItem(SELECTED_KIND_KEY);
      }
    } else if (agents.value.some((a) => a.id === savedId)) {
      selectedKind.value = 'agent';
      selectedAgentId.value = savedId;
    } else {
      localStorage.removeItem(SELECTED_AGENT_KEY);
      localStorage.removeItem(SELECTED_KIND_KEY);
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

  function goWorkflowManage() {
    agentPopoverShow.value = false;
    router.push('/workflow').catch(() => {});
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
  const conversations = ref<Conversation[]>([]);
  const activeSessionId = ref<string>('');
  const sidebarCollapsed = ref(false);

  // 当前活跃会话的智能体
  const currentAgentId = ref<string>('');
  // 当前活跃会话的目标类型（agent / workflow）
  const currentKind = ref<ChatKind>('agent');

  function nowStr(): string {
    const d = new Date();
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(
      d.getMinutes()
    )}`;
  }

  /** 后端时间字符串（yyyy-MM-dd HH:mm:ss）转侧边栏展示格式 MM-DD HH:mm */
  function fmtTime(s?: string): string {
    if (!s) return nowStr();
    // 兼容 yyyy-MM-ddTHH:mm:ss / yyyy-MM-dd HH:mm:ss
    const m = s.replace('T', ' ').match(/\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}/);
    if (!m) return s;
    return m[0].slice(5);
  }

  /** 把后端会话 VO 映射成本地 Conversation（messages 延迟加载） */
  function voToConv(vo: any): Conversation {
    return {
      id: String(vo.id),
      title: vo.title || '新会话',
      agentId: String(vo.agentId ?? ''),
      kind: (vo.kind as ChatKind) || 'agent',
      messages: [],
      updatedAt: fmtTime(vo.updatedAt),
    };
  }

  /** 从后端拉取会话列表 */
  async function loadConversations() {
    try {
      const resp: any = await getSessionsList(1, 100);
      const body = resp ?? {};
      const data = body.code === 0 ? body.data : body;
      const arr: any[] = Array.isArray(data?.data) ? data.data : Array.isArray(data) ? data : [];
      conversations.value = arr
        .filter((x: any) => x && x.id != null)
        .map((x: any) => voToConv(x))
        .sort((a: Conversation, b: Conversation) => (b.updatedAt > a.updatedAt ? 1 : -1));
    } catch {
      conversations.value = [];
    }
  }

  /** 激活会话时从后端拉消息列表填充 */
  async function loadSessionMessages(id: string): Promise<void> {
    const conv = conversations.value.find((x) => x.id === id);
    if (!conv) return;
    try {
      const resp: any = await getSessionMessages(id);
      const body = resp ?? {};
      const data = body.code === 0 ? body.data : body;
      const arr: any[] = Array.isArray(data) ? data : [];
      conv.messages = arr.map((m: any) => ({
        role: m.role,
        content: m.content || '',
        references: m.references ?? null,
        stageData: m.stageData,
        stageTimings: {},
        workflowSteps: m.workflowSteps,
        totalCost: m.totalCost,
        totalTokens: m.totalTokens,
        streaming: false,
      }));
      conv.messages = [...conv.messages];
      await nextTick();
      scrollBottom();
    } catch {
      conv.messages = [];
    }
  }

  /** 落库一条消息（user / assistant），失败仅告警，不阻塞流程 */
  async function persistMessage(conv: Conversation, data: Record<string, any>): Promise<void> {
    try {
      // jsonb 列在后端是 String 入参：对象/数组先序列化，字符串/null 原样透传
      const JSON_KEYS = ['references', 'stageData', 'workflowSteps'];
      const payload: Record<string, any> = { ...data };
      for (const k of JSON_KEYS) {
        const v = payload[k];
        if (v != null && typeof v !== 'string') {
          payload[k] = JSON.stringify(v);
        }
      }
      await saveSessionMessage(conv.id, payload as any);
    } catch {
      // 落库失败不影响本地展示
    }
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

  async function activateSession(id: string) {
    if (streaming.value) {
      message.warning('生成中，请先停止');
      return;
    }
    activeSessionId.value = id;
    const c = conversations.value.find((x) => x.id === id);
    if (c) {
      currentAgentId.value = c.agentId;
      currentKind.value = c.kind || 'agent';
    }
    resetStream();
    chatInput.value = '';
    // 从后端拉取该会话的消息明细
    await loadSessionMessages(id);
  }

  function goLanding() {
    activeSessionId.value = '';
    chatInput.value = '';
  }

  async function deleteConversation(id: string) {
    if (streaming.value) {
      message.warning('生成中，请先停止');
      return;
    }
    conversations.value = conversations.value.filter((c) => c.id !== id);
    try {
      await deleteSession(id);
    } catch {
      // 删除接口失败不阻塞本地
    }
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

  /** 当前活跃会话的目标（智能体 / 编排智能体），统一成 TargetItem */
  const currentTarget = computed<TargetItem | null>(() => {
    if (currentKind.value === 'workflow') {
      const w = workflows.value.find((x) => x.id === currentAgentId.value);
      return w
        ? {
            id: w.id as string,
            name: w.name,
            description: w.description,
            kind: 'workflow',
          }
        : null;
    }
    const a = agents.value.find((x) => x.id === currentAgentId.value);
    return a
      ? {
          id: a.id as string,
          name: a.name,
          description: a.description,
          kind: 'agent',
          suggestedQuestions: a.suggestedQuestions as string[] | undefined,
        }
      : null;
  });

  /** 聊天模式：当前活跃会话绑定的智能体开场白（编排智能体暂无） */
  const currentWelcome = computed(() => {
    if (currentKind.value === 'workflow') return '';
    const a = agents.value.find((x) => x.id === currentAgentId.value);
    return a?.welcome || '';
  });

  // 保留向后兼容（部分模板/逻辑引用 currentAgent?.name）
  const currentAgent = computed(() => {
    if (currentKind.value === 'workflow') {
      const w = workflows.value.find((x) => x.id === currentAgentId.value);
      return (w as unknown as Agent) || null;
    }
    return agents.value.find((a) => a.id === currentAgentId.value) || null;
  });

  const currentSessionTitle = computed(() => {
    const c = conversations.value.find((x) => x.id === activeSessionId.value);
    return c?.title || '新对话';
  });

  const currentSuggestedQuestions = computed(() => {
    if (currentKind.value === 'workflow') return []; // 编排暂无推荐问题
    const agent = currentAgent.value;
    return (agent?.suggestedQuestions as string[]) || [];
  });

  // 推荐追问（从当前智能体取）
  const suggestedFollowUp = computed(() => {
    // 可以后续根据上下文动态生成，目前先用固定示例或智能体的建议问题
    if (currentKind.value === 'workflow') return [];
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
    // 用户消息落库
    persistMessage(conv, { role: 'user', content: query });

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

    await scrollBottom();

    abortCtl = new AbortController();
    try {
      if (conv.kind === 'workflow') {
        // 编排智能体：走 workflow SSE，捕获各节点步骤（node/node_end），
        // complete 后用 getRunDetail 回填每步真实上下文（召回片段/prompt/耗时）
        let roundRuntimeId: number | null = null;
        const roundMsg = conv.messages[streamingIdx.value];
        await streamWorkflowChat(
          { workflowId: currentAgentId.value, conversationId: conv.id, query },
          {
            onAnswer: (token) => {
              streamFullText.value += token;
            },
            onNode: (p) => {
              // 首个 node 事件即本轮 runtimeId
              if (p.runtimeId && !roundRuntimeId) roundRuntimeId = p.runtimeId;
              if (!roundMsg) return;
              roundMsg.workflowSteps = roundMsg.workflowSteps || [];
              // 同一 cell 不重复追加
              if (!roundMsg.workflowSteps.some((s) => s.cell === p.cell)) {
                roundMsg.workflowSteps.push({
                  cell: p.cell,
                  nodeType: p.nodeType,
                  status: 'running',
                });
                conv.messages = [...conv.messages];
              }
            },
            onNodeEnd: (p) => {
              if (!roundMsg?.workflowSteps) return;
              const s = roundMsg.workflowSteps.find((x) => x.cell === p.cell);
              if (s) s.status = 'done';
              conv.messages = [...conv.messages];
            },
            onComplete: async (payload) => {
              if (roundMsg) {
                roundMsg.content = streamFullText.value;
                roundMsg.totalCost =
                  typeof payload.time === 'number' ? payload.time * 1000 : 0;
                roundMsg.totalTokens =
                  typeof payload.totalTokens === 'number' ? payload.totalTokens : undefined;
                // 回填每步真实详情
                await fetchWorkflowDetail(roundMsg, roundRuntimeId);
                roundMsg.streaming = false;
              }
              streamDone.value = true;
              conv.updatedAt = nowStr();
              conv.messages = [...conv.messages];
              // assistant 消息落库（编排步骤 / 耗时 / token）
              persistMessage(conv, {
                role: 'assistant',
                content: roundMsg?.content || streamFullText.value,
                workflowSteps: roundMsg?.workflowSteps,
                totalCost: roundMsg?.totalCost,
                totalTokens: roundMsg?.totalTokens,
              });
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
      } else {
        await streamAgentChat(
          { agentId: currentAgentId.value, conversationId: conv.id, query, sessionId: conv.id },
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
              // ★ assistant 消息落库已改由后端 AgentChatService 在 complete 时权威写入，
              //   此处不再落库，避免前端异步落库失败导致库里缺 LLM 回复。
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
      }
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
        // 把当前会话挪到列表顶部，刷新时间
        conv.updatedAt = nowStr();
        moveConvToTop(conv);
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

  // ========== 调用流程（智能体 RAG 各阶段上下文） ==========
  const traceDrawerVisible = ref(false);
  const traceDrawerData = ref<RagStageData | null>(null);
  function openTrace(data: RagStageData) {
    traceDrawerData.value = data;
    traceDrawerVisible.value = true;
  }

  // ========== 调用流程（编排智能体各节点步骤） ==========
  const workflowTraceVisible = ref(false);
  const workflowTraceSteps = ref<WorkflowStep[]>([]);
  const workflowTraceMeta = ref<{ name?: string; totalCost?: number; totalTokens?: number }>({});
  function openWorkflowTrace(msg: AgentChatMessage) {
    workflowTraceSteps.value = msg.workflowSteps || [];
    workflowTraceMeta.value = {
      name: currentTarget.value?.name,
      totalCost: msg.totalCost,
      totalTokens: msg.totalTokens,
    };
    workflowTraceVisible.value = true;
  }

  /** complete 后用 getRunDetail 回填每步真实上下文（按 step 排序，对齐 debug.vue 逻辑） */
  async function fetchWorkflowDetail(msg: AgentChatMessage, runtimeId: number | null) {
    if (!runtimeId) return;
    try {
      const res: any = await getRunDetail(runtimeId);
      if (res && res.code === 0 && Array.isArray(res.data)) {
        const rows = res.data
          .filter((x: any) => x && (x.outputData || x.modelData))
          .slice()
          .sort((a: any, b: any) => (a.step ?? 0) - (b.step ?? 0));
        if (!rows.length) return;
        const skeleton = msg.workflowSteps || [];
        const merged: WorkflowStep[] = rows.map((row: any) => {
          const matched = skeleton.find((s) => s.cell && s.cell === row.cell);
          return {
            cell: row.cell || matched?.cell,
            nodeType: row.nodeType || matched?.nodeType,
            status: 'done',
            step: row.step,
            outputData: row.outputData,
            modelData: row.modelData,
          };
        });
        msg.workflowSteps = merged;
      }
    } catch {
      // 拉取失败：保留运行时骨架
    }
  }

  // 步骤展开状态（消息下精简步骤条）：key = `${msgIdx}-${stepIdx}`
  const expandedSteps = ref(new Set<string>());
  function stepHeadIcon(step: WorkflowStep): any {
    return nodeMeta(step).icon;
  }
  function stepHeadMeta(step: WorkflowStep) {
    return nodeMeta(step);
  }
  function toggleStepExpand(msgIdx: number, stepIdx: number) {
    const key = `${msgIdx}-${stepIdx}`;
    const set = new Set(expandedSteps.value);
    if (set.has(key)) set.delete(key);
    else set.add(key);
    expandedSteps.value = set;
  }
  function isStepExpanded(msgIdx: number, stepIdx: number): boolean {
    return expandedSteps.value.has(`${msgIdx}-${stepIdx}`);
  }

  // 整条消息的步骤区折叠（精简条 ↔ 时间线展开）
  const expandedMsgSteps = ref(new Set<number>());
  function toggleMsgStepsExpand(msgIdx: number) {
    const set = new Set(expandedMsgSteps.value);
    if (set.has(msgIdx)) set.delete(msgIdx);
    else set.add(msgIdx);
    expandedMsgSteps.value = set;
  }
  function isMsgStepsExpanded(msgIdx: number): boolean {
    return expandedMsgSteps.value.has(msgIdx);
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

  function switchAgent(item: TargetItem) {
    currentAgentId.value = item.id;
    currentKind.value = item.kind;
    const conv = conversations.value.find((c) => c.id === activeSessionId.value);
    if (conv) {
      conv.agentId = item.id;
      conv.kind = item.kind;
    }
  }

  // ========== 欢迎页发送 → 进入聊天模式 ==========
  async function handleSend() {
    const q = inputValue.value.trim();
    if (!q) return;
    creating.value = true;
    try {
      const kind: ChatKind = selectedKind.value;
      const aid =
        selectedAgentId.value ||
        (kind === 'workflow'
          ? (workflowItems.value[0]?.id as string)
          : (agents.value[0]?.id as string)) ||
        '';
      if (!aid) {
        message.warning('请先选择一个智能体或编排智能体');
        creating.value = false;
        return;
      }
      const title = q.length > 20 ? q.slice(0, 20) + '...' : q;

      // 在后端创建会话，拿到持久化 id
      let sessionId = '';
      try {
        const resp: any = await createSessions({
          agentId: aid,
          query: q,
          title,
          kind,
        });
        const body = resp ?? {};
        const data = body.code === 0 && body.data !== undefined ? body.data : body;
        sessionId = String(data?.id ?? '');
      } catch (e: any) {
        message.error('创建会话失败：' + (e?.message || e));
        return;
      }
      if (!sessionId) {
        message.error('创建会话失败');
        return;
      }

      // 用后端 id 建本地会话壳（消息在 doSend 里落库）
      const conv: Conversation = {
        id: sessionId,
        title,
        agentId: aid,
        kind,
        messages: [],
        updatedAt: nowStr(),
      };
      conversations.value.unshift(conv);

      // 切换到聊天模式
      activeSessionId.value = sessionId;
      currentAgentId.value = aid;
      currentKind.value = kind;

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

  /** 把指定会话挪到列表顶部（用于发送/收消息后排序靠前） */
  function moveConvToTop(conv: Conversation) {
    const idx = conversations.value.findIndex((c) => c.id === conv.id);
    if (idx > 0) {
      conversations.value.splice(idx, 1);
      conversations.value.unshift(conv);
    }
  }

  // ========== 初始化 ==========
  onMounted(async () => {
    await loadAgents();
    await loadWorkflows();
    restoreSelectedAgent();
    loadSuggested();

    // 从后端加载会话历史
    await loadConversations();

    // 如果路由带 :id 参数，激活对应会话
    const routeId = String(route.params.id || '');
    if (routeId) {
      const conv = conversations.value.find((c) => c.id === routeId);
      if (conv) {
        activeSessionId.value = conv.id;
        // 从 query 取 agentId / kind 和初始问题
        const qAgentId = (route.query.agentId as string) || '';
        const qKind = (route.query.kind as ChatKind) || '';
        if (qAgentId) {
          currentAgentId.value = qAgentId;
          currentKind.value = qKind || conv.kind || 'agent';
        } else if (conv.agentId) {
          currentAgentId.value = conv.agentId;
          currentKind.value = conv.kind || 'agent';
        } else if (selectedAgentId.value) {
          currentAgentId.value = selectedAgentId.value;
          currentKind.value = selectedKind.value;
        }
        await loadSessionMessages(conv.id);
      }

      const initQ = (route.query.q as string) || '';
      if (initQ && activeSessionId.value) {
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
    color: v-bind('themeVars.textColor3');
    font-size: 14px;
    transition: all 0.15s;
    flex-shrink: 0;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    border-radius: 6px;
    &:hover {
      color: #fff;
      background: v-bind('themeVars.errorColor');
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
    max-height: 320px;
    overflow-y: auto;
  }
  .model-group-label {
    padding: 6px 14px 2px;
    font-size: 11px;
    color: v-bind('themeVars.textColor3');
    font-weight: 500;
    &:not(:first-child) {
      margin-top: 4px;
      border-top: 1px solid v-bind('themeVars.dividerColor');
    }
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

  /* 消息区：外层滚动，内层容器限宽 1000px 居中 */
  .chat-messages {
    flex: 1;
    overflow-y: auto;
    padding: 24px 32px;
    display: flex;
    flex-direction: column;
  }
  .chat-messages-inner {
    max-width: 1000px;
    width: 100%;
    margin: 0 auto;
    display: flex;
    flex-direction: column;
    gap: 4px;
    flex: 1;
  }

  /* 空状态：复刻欢迎页大居中布局 */
  .chat-empty-hero {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    text-align: center;
    padding: 40px 20px 60px;
    min-height: 480px;
  }
  .empty-hero-icon {
    width: 72px;
    height: 72px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(
      135deg,
      v-bind('themeVars.primaryColorHover') 0%,
      v-bind('themeVars.primaryColor') 100%
    );
    box-shadow: 0 8px 20px rgba(0, 0, 0, 0.1),
      0 0 0 6px v-bind('themeVars.primaryColorSuppl');
    margin-bottom: 20px;
  }
  .empty-hero-title {
    margin: 0 0 8px;
    font-size: 26px;
    font-weight: 600;
    color: v-bind('themeVars.textColorBase');
    letter-spacing: 0.3px;
    line-height: 1.4;
  }
  .empty-hero-sub {
    margin: 0 0 32px;
    font-size: 15px;
    color: v-bind('themeVars.textColor3');
  }
  .empty-hero-suggested {
    width: 100%;
    max-width: 640px;
  }
  .empty-hero-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 10px;
  }
  .empty-hero-card {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    padding: 12px 14px;
    background: v-bind('themeVars.cardColor');
    border: 1px solid v-bind('themeVars.borderColor');
    border-radius: 10px;
    cursor: pointer;
    text-align: left;
    transition: all 0.18s ease;
    &:hover {
      border-color: v-bind('themeVars.primaryColor');
      box-shadow: v-bind('themeVars.boxShadow2');
      transform: translateY(-1px);
      .empty-hero-card-icon {
        color: v-bind('themeVars.primaryColor');
      }
    }
  }
  .empty-hero-card-icon {
    color: v-bind('themeVars.textColor3');
    flex-shrink: 0;
    margin-top: 2px;
    transition: color 0.18s;
  }
  .empty-hero-card-text {
    flex: 1;
    font-size: 13px;
    line-height: 1.5;
    color: v-bind('themeVars.textColor2');
    word-break: break-word;
  }
  .empty-hero-hint {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 6px 14px;
    font-size: 12px;
    color: v-bind('themeVars.textColor3');
    background: v-bind('themeVars.actionColor');
    border-radius: 20px;
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

  /* ========== 调用流程步骤（编排智能体，消息下内联） ========== */
  .msg-step-timeline {
    margin-top: 6px;
    width: 100%;
  }
  /* 精简条：折叠态展示链路概要 */
  .step-summary-bar {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 6px 10px;
    background: v-bind('themeVars.actionColor');
    border: 1px solid v-bind('themeVars.borderColor');
    border-radius: 8px;
    cursor: pointer;
    transition: border-color 0.15s;
    &:hover {
      border-color: v-bind('themeVars.primaryColor');
    }
  }
  .step-summary-caret {
    color: v-bind('themeVars.textColor3');
    flex-shrink: 0;
    transition: transform 0.15s;
    &.open {
      transform: rotate(90deg);
    }
  }
  .step-summary-label {
    font-size: 12px;
    color: v-bind('themeVars.primaryColor');
    font-weight: 500;
    flex-shrink: 0;
  }
  .step-summary-chain {
    flex: 1;
    display: flex;
    align-items: center;
    gap: 4px;
    overflow: hidden;
    flex-wrap: wrap;
  }
  .step-chain-node {
    font-size: 12px;
    color: v-bind('themeVars.textColor2');
  }
  .step-chain-arrow {
    font-size: 11px;
    color: v-bind('themeVars.textColor3');
  }
  .step-summary-cost {
    font-size: 11px;
    color: v-bind('themeVars.textColor3');
    font-variant-numeric: tabular-nums;
    flex-shrink: 0;
  }

  /* 步骤时间线 */
  .step-timeline {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin-top: 6px;
  }
  .step-card {
    background: v-bind('themeVars.cardColor');
    border: 1px solid v-bind('themeVars.borderColor');
    border-radius: 6px;
    padding: 7px 10px;
    transition: border-color 0.15s;
    &.is-open {
      border-color: v-bind('themeVars.dividerColor');
    }
    &.is-running {
      border-color: v-bind('themeVars.primaryColor');
    }
  }
  .step-head {
    display: flex;
    align-items: center;
    gap: 6px;
    cursor: pointer;
    user-select: none;
  }
  .step-caret {
    color: v-bind('themeVars.textColor3');
    flex-shrink: 0;
  }
  .step-num {
    width: 18px;
    height: 18px;
    line-height: 18px;
    text-align: center;
    background: v-bind('themeVars.textColor3');
    color: #fff;
    border-radius: 50%;
    font-size: 11px;
    flex-shrink: 0;
  }
  .step-name {
    flex: 1;
    font-size: 13px;
    font-weight: 500;
    color: v-bind('themeVars.textColorBase');
  }
  .cost-badge {
    margin-left: 2px;
    padding: 1px 7px;
    border-radius: 10px;
    font-size: 11px;
    font-variant-numeric: tabular-nums;
    flex-shrink: 0;
  }
  .cost-ok {
    background: v-bind('themeVars.successColorSuppl');
    color: #fff;
  }
  .cost-warning {
    background: v-bind('themeVars.warningColorSuppl');
    color: v-bind('themeVars.warningColor');
  }
  .cost-danger {
    background: v-bind('themeVars.errorColorSuppl');
    color: v-bind('themeVars.errorColor');
  }
  /* 执行中转圈 */
  .step-spin {
    margin-left: auto;
    width: 12px;
    height: 12px;
    border: 2px solid v-bind('themeVars.primaryColor');
    border-top-color: transparent;
    border-radius: 50%;
    animation: step-spin 0.7s linear infinite;
    flex-shrink: 0;
  }
  @keyframes step-spin {
    to {
      transform: rotate(360deg);
    }
  }
  .step-check {
    margin-left: auto;
    color: v-bind('themeVars.successColor');
    font-size: 13px;
    font-weight: 700;
    flex-shrink: 0;
  }
  .step-body {
    margin-top: 8px;
    font-size: 13px;
  }

  /* ===== NodeBody 渲染依赖的深层 class（scoped 需 :deep） ===== */
  :deep(.detail-stack) {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }
  :deep(.detail-grid) {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  :deep(.detail-title) {
    font-size: 12px;
    font-weight: 600;
    color: v-bind('themeVars.textColorBase');
    margin-bottom: 2px;
  }
  :deep(.kv-row) {
    display: flex;
    gap: 10px;
    align-items: flex-start;
  }
  :deep(.kv-label) {
    flex-shrink: 0;
    width: 76px;
    font-size: 12px;
    color: v-bind('themeVars.textColor3');
    padding-top: 2px;
  }
  :deep(.kv-val) {
    flex: 1;
    font-size: 13px;
    color: v-bind('themeVars.textColorBase');
    line-height: 1.6;
    word-break: break-word;
  }
  :deep(.kv-strong) {
    color: v-bind('themeVars.primaryColor');
    font-weight: 600;
  }
  :deep(.code-view) {
    background: #282c34;
    color: #abb2bf;
    padding: 10px;
    border-radius: 6px;
    overflow-x: auto;
    font-size: 12px;
    line-height: 1.5;
    white-space: pre-wrap;
    word-break: break-word;
    margin: 4px 0 0;
  }
  :deep(.md-output) {
    background: v-bind('themeVars.actionColor');
    border-radius: 6px;
    padding: 10px 12px;
    font-size: 13px;
    line-height: 1.6;
    color: v-bind('themeVars.textColorBase');
    word-break: break-word;
    p {
      margin: 4px 0;
    }
    pre {
      background: #282c34;
      color: #abb2bf;
      padding: 10px;
      border-radius: 6px;
      overflow-x: auto;
      font-size: 12px;
    }
    code {
      background: rgba(0, 0, 0, 0.06);
      padding: 2px 4px;
      border-radius: 3px;
      font-size: 12px;
    }
    pre code {
      background: transparent;
      padding: 0;
    }
  }
  :deep(.stat-row) {
    display: flex;
    flex-wrap: wrap;
    gap: 16px;
    padding: 8px 10px;
    background: v-bind('themeVars.actionColor');
    border-radius: 6px;
  }
  :deep(.stat-item) {
    display: flex;
    align-items: center;
    gap: 5px;
  }
  :deep(.stat-k) {
    font-size: 12px;
    color: v-bind('themeVars.textColor3');
  }
  :deep(.stat-v) {
    font-size: 13px;
    font-weight: 600;
    color: v-bind('themeVars.textColorBase');
    font-variant-numeric: tabular-nums;
  }
  :deep(.frag-list) {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  :deep(.frag-card) {
    padding: 8px 10px;
    background: v-bind('themeVars.actionColor');
    border-left: 3px solid v-bind('themeVars.primaryColor');
    border-radius: 4px;
  }
  :deep(.frag-head) {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 4px;
  }
  :deep(.frag-idx) {
    font-size: 11px;
    color: v-bind('themeVars.textColor3');
    font-weight: 600;
  }
  :deep(.frag-text) {
    font-size: 12px;
    line-height: 1.6;
    color: v-bind('themeVars.textColor2');
  }
  :deep(.note-tip) {
    padding: 6px 10px;
    background: v-bind('themeVars.infoColorSuppl');
    border-left: 3px solid v-bind('themeVars.infoColor');
    border-radius: 4px;
    font-size: 12px;
    color: v-bind('themeVars.textColor2');
  }
  :deep(.detail-box) {
    background: v-bind('themeVars.actionColor');
    border-radius: 4px;
  }
  :deep(.json-view) {
    margin: 0;
    white-space: pre-wrap;
    word-break: break-word;
    font-size: 12px;
    color: v-bind('themeVars.textColorBase');
    max-height: 240px;
    overflow-y: auto;
    padding: 8px 12px;
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
