<template>
  <n-modal
    v-model:show="show"
    preset="card"
    :title="`测试对话 · ${agent?.name || ''}`"
    style="width: 1100px"
    :mask-closable="false"
    @after-leave="onClose"
  >
    <div class="chat-layout">
      <div class="sidebar">
        <div class="sidebar-head">
          <n-button size="small" type="primary" block secondary @click="newConversation">
            <template #icon
              ><n-icon><PlusOutlined /></n-icon></template
            >新建会话
          </n-button>
        </div>
        <div class="conv-list">
          <div
            v-for="c in conversations"
            :key="c.id"
            class="conv-item"
            :class="{ active: c.id === currentId }"
            @click="switchConversation(c.id)"
          >
            <div class="conv-info">
              <div class="conv-title">{{ c.title || '新会话' }}</div>
              <div class="conv-time">{{ c.updatedAt }}</div>
            </div>
            <n-icon class="conv-del" @click.stop="deleteConversation(c.id)">
              <DeleteOutlined />
            </n-icon>
          </div>
          <n-empty
            v-if="conversations.length === 0"
            description="暂无会话"
            size="small"
            style="margin-top: 30px"
          />
        </div>
      </div>

      <div class="chat-main">
        <div ref="msgBoxRef" class="msg-list">
          <template v-if="currentMessages.length === 0 && agent">
            <div v-if="agent.welcome" class="msg-welcome">{{ agent.welcome }}</div>
            <div v-if="(agent.suggestedQuestions || []).length" class="suggested">
              <div class="suggested-title">推荐问题</div>
              <n-space>
                <n-tag
                  v-for="(q, i) in agent.suggestedQuestions"
                  :key="i"
                  checkable
                  size="medium"
                  @click="onSuggestedClick(q)"
                >
                  {{ q }}
                </n-tag>
              </n-space>
            </div>
            <n-empty v-else description="输入问题开始测试" style="margin-top: 80px" />
          </template>

          <div v-for="(msg, i) in currentMessages" :key="i" class="msg-row" :class="msg.role">
            <img
              class="msg-avatar"
              :src="msg.role === 'user' ? '/images/user.png' : (agent?.avatarImage || '/images/robot.png')"
              alt="avatar"
            />
            <div class="msg-body">
              <div class="md-body" v-html="renderMd(displayedOf(i))"></div>
              <span v-if="msg.streaming" class="streaming-dot">●●●</span>
              <!-- 引用来源 + 总耗时 同一行显示（仅 assistant 消息完成后） -->
              <div
                v-if="msg.role === 'assistant' && !msg.streaming && (msg.references?.length || msg.totalCost)"
                class="msg-actions"
              >
                <n-button
                  v-if="msg.references && msg.references.length"
                  text
                  type="primary"
                  size="tiny"
                  @click="openRefs(msg.references)"
                >
                  <template #icon><n-icon><FileTextOutlined /></n-icon></template>
                  引用来源（{{ msg.references.length }}）
                </n-button>
                <n-button
                  v-if="msg.totalCost"
                  text
                  type="primary"
                  size="tiny"
                  @click="openTiming(msg)"
                >
                  <template #icon><n-icon><ClockCircleOutlined /></n-icon></template>
                  总耗时 {{ (msg.totalCost / 1000).toFixed(2) }}s
                </n-button>
              </div>
            </div>
          </div>
        </div>

        <div class="input-area">
          <textarea
            v-model="input"
            class="chat-input"
            rows="3"
            placeholder="输入问题，Enter 发送，Shift+Enter 换行"
            :disabled="streaming"
            @keydown.enter.exact.prevent="onSend"
          ></textarea>
          <div class="input-actions">
            <n-button v-if="streaming" type="error" ghost size="small" @click="onStop"
              >停止生成</n-button
            >
            <n-button
              v-else
              type="primary"
              size="small"
              secondary
              :disabled="!input.trim() || !agent"
              @click="onSend"
              >发送</n-button
            >
          </div>
        </div>
      </div>
    </div>
    <!-- 引用来源抽屉 -->
    <n-drawer v-model:show="refsDrawerVisible" :width="420" placement="right">
      <n-drawer-content title="引用来源">
        <div v-for="r in refsDrawerData" :key="r.index" class="drawer-ref-item">
          <div class="drawer-ref-header">
            <n-tag size="small" round type="info">{{ r.index }}</n-tag>
          </div>
          <div class="drawer-ref-body" v-html="renderMd(r.content)"></div>
        </div>
        <n-empty v-if="refsDrawerData.length === 0" description="暂无引用" />
      </n-drawer-content>
    </n-drawer>
    <!-- RAG 阶段耗时弹窗（时间线 + 占比玫瑰图） -->
    <n-modal
      v-model:show="timingModalVisible"
      preset="card"
      title="RAG 阶段耗时"
      style="width: 560px; max-width: 92vw"
      :bordered="false"
      @after-leave="disposeRoseChart"
    >
      <div v-if="timingMsg" class="timing-modal-body">
        <div class="timing-head">
          <n-icon class="timing-head-icon"><FieldTimeOutlined /></n-icon>
          <span class="timing-head-total">总耗时 {{ timingMsg.totalCost }}ms（{{ (timingMsg.totalCost / 1000).toFixed(2) }}s）</span>
        </div>
        <n-tabs type="line" :value="activeTimingTab" @update:value="onTabChange">
          <!-- 时间线视图 -->
          <n-tab-pane name="timeline" tab="时间线">
            <n-timeline size="large">
              <n-timeline-item
                v-for="st in stageListOf(timingMsg)"
                :key="st.name"
                :type="st.type"
              >
                <template #header>
                  <div class="timing-item-head">
                    <span class="timing-item-name">{{ stageLabel(st.name) }}</span>
                    <span class="timing-item-time">{{ st.ms }}ms · {{ st.percent }}%</span>
                  </div>
                </template>
                <n-progress
                  type="line"
                  :percentage="st.percent"
                  :height="8"
                  :border-radius="4"
                  :fill-border-radius="4"
                  :show-indicator="false"
                  :status="st.type === 'error' ? 'error' : st.type === 'warning' ? 'warning' : 'success'"
                />
              </n-timeline-item>
            </n-timeline>
          </n-tab-pane>

          <!-- 占比南丁格尔玫瑰图视图（echarts） -->
          <n-tab-pane name="pie" tab="占比">
            <div ref="roseChartRef" class="rose-chart"></div>
          </n-tab-pane>
        </n-tabs>
      </div>
      <n-empty v-else description="暂无耗时数据" />
    </n-modal>
  </n-modal>
</template>

<script setup lang="ts">
  import { ref, computed, nextTick } from 'vue';
  import { useMessage } from 'naive-ui';
  import { PlusOutlined, DeleteOutlined, FileTextOutlined, ClockCircleOutlined, FieldTimeOutlined } from '@vicons/antd';
  import { marked } from 'marked';
  import { streamAgentChat, type Agent, type AgentChatMessage } from '@/api/system/agent';
  import { useTypewriter } from '@/composables/useTypewriter';
  import * as echarts from 'echarts';

  marked.use({ breaks: true, gfm: true });

  const message = useMessage();

  const show = ref(false);
  const agent = ref<Agent | null>(null);
  const input = ref('');
  const streaming = ref(false);
  const msgBoxRef = ref<HTMLElement | null>(null);

  interface Conversation {
    id: string;
    title: string;
    messages: AgentChatMessage[];
    updatedAt: string;
  }

  const conversations = ref<Conversation[]>([]);
  const currentId = ref<string>('');

  const currentMessages = computed(() => {
    const c = conversations.value.find((x) => x.id === currentId.value);
    return c ? c.messages : [];
  });

  // 打字机：当前流式 assistant 消息的全文
  const streamFullText = ref('');
  const streamDone = ref(false);
  const { displayed: typedDisplayed } = useTypewriter(
    () => streamFullText.value,
    () => streamDone.value
  );
  const typewriterText = computed(() => typedDisplayed.value);
  const streamingIdx = ref(-1);

  // 引用来源抽屉
  const refsDrawerVisible = ref(false);
  const refsDrawerData = ref<{ index: number; content: string }[]>([]);
  function openRefs(refs: { index: number; content: string }[]) {
    refsDrawerData.value = refs || [];
    refsDrawerVisible.value = true;
  }

  // ===== RAG 阶段耗时时间线（弹窗展示） =====
  // ★ 用独立 ref 存"当前正在查看的消息"，彻底规避 ref 对象新增 key 的响应式陷阱。
  //   openTiming 时把 msg 引用赋给 timingMsg，弹窗读取 timingMsg 的字段即可触发渲染。
  const timingModalVisible = ref(false);
  const timingMsg = ref<AgentChatMessage | null>(null);
  function openTiming(msg: AgentChatMessage) {
    timingMsg.value = msg;
    activeTimingTab.value = 'timeline'; // 每次打开默认回到时间线 tab
    timingModalVisible.value = true;
  }

  // 阶段名 → 中文名映射
  const STAGE_LABELS: Record<string, string> = {
    'rewrite-split': '改写拆分',
    intent: '意图判定',
    'tree-intent': '意图分类',
    guidance: '歧义引导',
    'vague-clarify': '模糊澄清',
    retrieve: '检索召回',
    rerank: '重排',
    merge: '合并',
    fallback: '兜底',
    generate: '生成回答',
  };
  function stageLabel(name: string): string {
    return STAGE_LABELS[name] || name;
  }

  // 计算单条消息的阶段耗时列表（含占比），按执行顺序
  function stageListOf(msg: AgentChatMessage) {
    const timings = msg.stageTimings || {};
    const total = msg.totalCost || 0;
    return Object.entries(timings).map(([name, ms]) => {
      const percent = total > 0 ? Math.round((ms / total) * 100) : 0;
      // 按占比着色：>50% 红色突显瓶颈，>20% 警示，其余默认
      let type: 'error' | 'warning' | 'default' = 'default';
      if (percent > 50) type = 'error';
      else if (percent > 20) type = 'warning';
      return { name, ms, percent, type };
    });
  }

  // 南丁格尔玫瑰图（echarts pie + roseType:'radius'）：角度均分，半径按耗时长度编码。
  // tab-pane 默认 v-if，切到"占比"时容器才挂载；tab-click 触发时 pane 可能还没渲染完，
  // 故轮询等容器真正可见（offsetWidth>0）再 init，否则 echarts 在 0 尺寸容器上画不出来。
  const roseChartRef = ref<HTMLDivElement | null>(null);
  let roseChart: echarts.ECharts | null = null;

  // 现代图表配色（参考 AntV G2 默认色板）：低饱和、和谐、高级感。
  // 首位珊瑚红 #E8684A 给瓶颈段（已按 ms 降序排首位），其余循环柔和色。
  const PIE_COLORS = ['#E8684A', '#5B8FF9', '#5AD8A6', '#F6BD16', '#6DC8EC', '#9270CA', '#FF9D4D', '#5D7092'];

  function disposeRoseChart() {
    if (roseChart) {
      roseChart.dispose();
      roseChart = null;
    }
  }

  // 受控的当前 tab（默认时间线）
  const activeTimingTab = ref('timeline');

  // tab 切换：切到"占比"时渲染玫瑰图（naive-ui tabs 用 update:value 事件，无 tab-click）
  function onTabChange(panName: string) {
    activeTimingTab.value = panName;
    if (panName !== 'pie') return;
    nextTick(() => waitVisibleAndRender());
  }

  // 轮询等待容器可见（最多 ~600ms），可见后立即渲染
  function waitVisibleAndRender() {
    let tries = 0;
    const timer = window.setInterval(() => {
      tries++;
      const el = roseChartRef.value;
      if ((el && el.offsetWidth > 0 && el.offsetHeight > 0) || tries > 30) {
        window.clearInterval(timer);
        renderRoseChart();
      }
    }, 20);
  }

  function renderRoseChart() {
    const el = roseChartRef.value;
    if (!el || !timingMsg.value) return;
    const list = stageListOf(timingMsg.value);
    if (list.length === 0) return;
    // 若该 DOM 上已有 echarts 实例（切走又切回时 Vue 复用 DOM），先 dispose 避免冲突
    const existing = echarts.getInstanceByDom(el);
    if (existing) existing.dispose();
    disposeRoseChart();
    roseChart = echarts.init(el);
    const totalMs = timingMsg.value.totalCost || 0;
    // 按 ms 降序，瓶颈段红色排第一
    const sorted = [...list].sort((a, b) => b.ms - a.ms);
    const data = sorted.map((st, i) => ({
      name: stageLabel(st.name),
      value: st.ms,
      ms: st.ms,
      percent: st.percent,
      itemStyle: { color: PIE_COLORS[i % PIE_COLORS.length] },
    }));
    roseChart.setOption({
      tooltip: {
        trigger: 'item',
        formatter: (p: any) => {
          const d = p.data;
          return `${p.name}<br/>耗时：${d.ms}ms<br/>占比：${d.percent}%`;
        },
      },
      // 中心总耗时：用 title 在容器顶部显示（避开 graphic rich text 兼容问题）
      title: {
        text: `${(totalMs / 1000).toFixed(2)}s`,
        subtext: '总耗时',
        left: 'center',
        top: 'center',
        textStyle: { fontSize: 20, fontWeight: 'bold', color: '#262626' },
        subtextStyle: { fontSize: 11, color: '#bfbfbf' },
        itemGap: 4,
      },
      series: [
        {
          type: 'pie',
          roseType: 'radius', // 南丁格尔玫瑰：半径编码数值
          radius: ['20%', '70%'], // 内半径留出中心文字区
          center: ['50%', '50%'],
          data,
          label: {
            formatter: '{b}\n{d}%',
            fontSize: 11,
            color: '#8c8c8c',
          },
          labelLine: { length: 10, length2: 12, lineStyle: { color: '#d9d9d9' } },
          // 圆角扇区 + 白色描边分隔，现代感；hover 柔和阴影
          itemStyle: { borderColor: '#fff', borderWidth: 2, borderRadius: 6 },
          emphasis: {
            itemStyle: { shadowBlur: 16, shadowColor: 'rgba(0,0,0,0.12)' },
            scale: true,
            scaleSize: 6,
          },
        },
      ],
    });
    // init 时容器可能仍在布局中（尺寸 0），强制 resize 让 echarts 按当前真实尺寸重绘
    roseChart.resize();
  }

  function displayedOf(i: number): string {
    const msg = currentMessages.value[i];
    if (msg.role === 'assistant' && i === streamingIdx.value && streaming.value) {
      return typewriterText.value;
    }
    return msg.content;
  }

  function open(ag: Agent) {
    agent.value = ag;
    conversations.value = loadConversations(ag.id!);
    if (conversations.value.length === 0) {
      const c = createConversation();
      conversations.value = [c];
    }
    currentId.value = conversations.value[0].id;
    resetStream();
    show.value = true;
    nextTick(scrollBottom);
  }

  function createConversation(): Conversation {
    return { id: genId(), title: '新会话', messages: [], updatedAt: nowStr() };
  }

  function newConversation() {
    if (streaming.value) return;
    const c = createConversation();
    conversations.value.unshift(c);
    currentId.value = c.id;
    persist();
    resetStream();
    input.value = '';
  }

  function switchConversation(id: string) {
    if (streaming.value) {
      message.warning('生成中，请先停止');
      return;
    }
    currentId.value = id;
    resetStream();
    input.value = '';
    nextTick(scrollBottom);
  }

  function deleteConversation(id: string) {
    if (streaming.value) {
      message.warning('生成中，请先停止');
      return;
    }
    conversations.value = conversations.value.filter((c) => c.id !== id);
    if (conversations.value.length === 0) {
      const c = createConversation();
      conversations.value = [c];
    }
    if (currentId.value === id) {
      currentId.value = conversations.value[0].id;
    }
    persist();
  }

  function resetStream() {
    streamFullText.value = '';
    streamDone.value = false;
    streamingIdx.value = -1;
  }

  function lsKey(agentId: string) {
    return `agent_convs_${agentId}`;
  }
  function loadConversations(agentId: string): Conversation[] {
    try {
      const raw = localStorage.getItem(lsKey(agentId));
      if (raw) {
        const arr = JSON.parse(raw);
        if (Array.isArray(arr)) return arr;
      }
    } catch {
      // ignore
    }
    return [];
  }
  function persist() {
    if (agent.value?.id) {
      // 只保留最近 20 个会话，避免无限增长
      conversations.value = conversations.value.slice(0, 20);
      localStorage.setItem(lsKey(agent.value.id), JSON.stringify(conversations.value));
    }
  }

  function onSuggestedClick(q: string) {
    input.value = q;
  }

  async function onSend() {
    const q = input.value.trim();
    if (!q || streaming.value || !agent.value) return;
    const conv = conversations.value.find((c) => c.id === currentId.value);
    if (!conv) return;

    // 用户消息入会话
    conv.messages.push({ role: 'user', content: q });
    // 首条消息作为会话标题
    if (conv.title === '新会话') {
      conv.title = q.length > 20 ? q.slice(0, 20) + '...' : q;
    }
    conv.updatedAt = nowStr();
    input.value = '';

    // 占位 assistant 消息
    // ★ 预声明 totalCost/stageTimings 字段：Vue3 ref 数组里的对象，新增属性无法被响应式追踪，
    //   必须在初始对象里就存在该字段，后续 onComplete 赋值才能触发 v-if 重新求值（否则时间线按钮不显示）
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

    try {
      await streamAgentChat(
        { agentId: agent.value.id!, conversationId: conv.id, query: q },
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
              msg.streaming = false;
            }
            streamDone.value = true;
            conv.updatedAt = nowStr();
            // ★ 触发 currentMessages computed 重新求值：
            //   computed 依赖 conversations.value，仅改 msg 属性不会让 computed 失效，
            //   模板里 v-if="msg.totalCost" 不会重新求值（时间线按钮不显示的根因）。
            //   浅拷贝数组触发引用变更，强制 computed + v-for 重算。
            conv.messages = [...conv.messages];
            persist();
          },
          onError: (errMsg) => {
            const msg = conv.messages[streamingIdx.value];
            if (msg) {
              msg.content = `⚠️ ${errMsg}`;
              msg.streaming = false;
            }
            streamDone.value = true;
            message.error(errMsg);
          },
        }
      );
    } catch (e: any) {
      const msg = conv.messages[streamingIdx.value];
      if (msg) {
        msg.content = `⚠️ 请求失败：${e?.message || e}`;
        msg.streaming = false;
      }
      streamDone.value = true;
    } finally {
      streaming.value = false;
      nextTick(() => {
        const msg = conv.messages[streamingIdx.value];
        if (msg) msg.content = streamFullText.value || msg.content;
        scrollBottom();
        persist();
      });
    }
  }

  function onStop() {
    streaming.value = false;
    message.info('已停止生成');
  }

  function onClose() {
    streaming.value = false;
    persist();
  }

  async function scrollBottom() {
    await nextTick();
    if (msgBoxRef.value) {
      msgBoxRef.value.scrollTop = msgBoxRef.value.scrollHeight;
    }
  }

  function genId(): string {
    return 'c' + Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
  }
  function nowStr(): string {
    const d = new Date();
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(
      d.getMinutes()
    )}`;
  }
  function renderMd(text: string): string {
    if (!text) return '';
    try {
      // 预处理：# 后无空格的标题补上空格（###2. → ### 2.）
      const normalized = text.replace(/^(#{1,6})(?=\S)/gm, '$1 ');
      return marked.parse(normalized) as string;
    } catch {
      return text;
    }
  }

  defineExpose({ open });
</script>

<style lang="less" scoped>
  .chat-layout {
    display: flex;
    height: 68vh;
    border: 1px solid #eee;
    border-radius: 8px;
    overflow: hidden;
  }

  .sidebar {
    width: 240px;
    flex-shrink: 0;
    border-right: 1px solid #eee;
    background: #fafafa;
    display: flex;
    flex-direction: column;
  }
  .sidebar-head {
    padding: 12px;
    border-bottom: 1px solid #eee;
  }
  .conv-list {
    flex: 1;
    overflow-y: auto;
    padding: 8px;
  }
  .conv-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 12px;
    border-radius: 6px;
    cursor: pointer;
    transition: all 0.15s;
    margin-bottom: 4px;
    &:hover {
      background: #f0f0f0;
      .conv-del {
        opacity: 1;
      }
    }
    &.active {
      background: #e8f5e9;
      .conv-title {
        color: #07c05f;
        font-weight: 600;
      }
    }
  }
  .conv-info {
    flex: 1;
    overflow: hidden;
  }
  .conv-title {
    font-size: 13px;
    color: #333;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .conv-time {
    font-size: 11px;
    color: #aaa;
    margin-top: 2px;
  }
  .conv-del {
    opacity: 0;
    color: #d03050;
    font-size: 14px;
    transition: opacity 0.15s;
    flex-shrink: 0;
    &:hover {
      transform: scale(1.15);
    }
  }

  .chat-main {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-width: 0;
  }
  .msg-list {
    flex: 1;
    overflow-y: auto;
    padding: 16px 20px;
  }
  .msg-welcome {
    background: #f6ffed;
    border: 1px solid #b7eb8f;
    border-radius: 8px;
    padding: 10px 14px;
    color: #333;
    font-size: 13px;
    margin-bottom: 14px;
  }
  .suggested {
    margin-bottom: 14px;
    .suggested-title {
      font-size: 12px;
      color: #aaa;
      margin-bottom: 6px;
    }
  }
  .msg-row {
    display: flex;
    gap: 10px;
    margin-bottom: 16px;
    &.user {
      flex-direction: row-reverse;
      .msg-body {
        align-items: flex-end;
      }
      .md-body {
        background: #e8f5e9;
        color: #333;
      }
    }
  }
  .msg-avatar {
    flex-shrink: 0;
    width: 32px;
    height: 32px;
    border-radius: 50%;
    object-fit: cover;
    object-position: center;
    background: #f0f0f0;
    display: block;
  }
  .msg-body {
    display: flex;
    flex-direction: column;
    max-width: 72%;
    gap: 6px;
  }
  .md-body {
    background: #f5f5f5;
    border-radius: 8px;
    padding: 10px 14px;
    font-size: 14px;
    line-height: 1.6;
    word-break: break-word;
    :deep(p) {
      margin: 4px 0;
    }
    :deep(pre) {
      background: #282c34;
      color: #abb2bf;
      padding: 10px;
      border-radius: 6px;
      overflow-x: auto;
      font-size: 12px;
    }
    :deep(code) {
      background: rgba(0, 0, 0, 0.06);
      padding: 2px 4px;
      border-radius: 3px;
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
  .streaming-dot {
    color: #07c05f;
    font-size: 10px;
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
  .msg-actions {
    margin-top: 4px;
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }
  // RAG 阶段耗时时间线弹窗
  .timing-modal-body {
    padding: 4px 4px 8px;
  }
  .timing-head {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 16px;
    padding: 10px 12px;
    background: #f6ffed;
    border: 1px solid #b7eb8f;
    border-radius: 6px;
  }
  .timing-head-icon {
    font-size: 18px;
    color: #07c05f;
  }
  .timing-head-total {
    font-size: 14px;
    color: #333;
    font-weight: 600;
  }
  .timing-item-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 6px;
  }
  .timing-item-name {
    font-size: 13px;
    color: #333;
  }
  .timing-item-time {
    font-size: 12px;
    color: #999;
    font-variant-numeric: tabular-nums;
  }
  // 南丁格尔玫瑰图容器（echarts）
  .rose-chart {
    width: 100%;
    height: 340px;
  }
  .drawer-ref-item {
    margin-bottom: 16px;
    padding-bottom: 16px;
    border-bottom: 1px solid #f0f0f0;
    &:last-child {
      border-bottom: none;
    }
  }
  .drawer-ref-header {
    margin-bottom: 6px;
  }
  .drawer-ref-body {
    font-size: 13px;
    line-height: 1.7;
    color: #555;
    word-break: break-word;
    :deep(p) {
      margin: 4px 0;
    }
    :deep(pre) {
      background: #f5f5f5;
      padding: 8px;
      border-radius: 4px;
      font-size: 12px;
      overflow-x: auto;
    }
    :deep(code) {
      background: rgba(0, 0, 0, 0.06);
      padding: 1px 4px;
      border-radius: 3px;
      font-size: 12px;
    }
  }
  .input-area {
    border-top: 1px solid #eee;
    padding: 12px 16px;
    .chat-input {
      width: 100%;
      box-sizing: border-box;
      resize: none;
      border: 1px solid #e0e0e0;
      border-radius: 6px;
      padding: 8px 10px;
      font-family: inherit;
      font-size: 14px;
      line-height: 1.5;
      color: #333;
      outline: none;
      transition: border-color 0.15s;
      &:focus {
        border-color: #07c05f;
      }
      &:disabled {
        background: #f5f5f5;
        cursor: not-allowed;
      }
    }
    .input-actions {
      display: flex;
      justify-content: flex-end;
      margin-top: 8px;
    }
  }
</style>
