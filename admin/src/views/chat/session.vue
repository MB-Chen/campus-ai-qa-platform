<template>
  <div class="session-page">
    <!-- 顶栏 -->
    <div class="sp-head">
      <n-button text size="medium" @click="goBack">
        <template #icon><n-icon><ArrowLeftOutlined /></n-icon></template>
        返回
      </n-button>
      <div class="sp-title">
        <span class="sp-title-text">会话</span>
        <n-tag size="small" :bordered="false" type="default">{{ shortId }}</n-tag>
      </div>
      <n-select
        v-model:value="currentAgentId"
        :options="agentOptions"
        placeholder="智能体"
        style="width: 200px"
        @update:value="onAgentChange"
      />
    </div>

    <!-- 消息列表 -->
    <div ref="msgBoxRef" class="sp-msg-list">
      <n-empty
        v-if="messages.length === 0"
        description="发送第一条消息开始对话"
        style="margin-top: 80px"
      />
      <div
        v-for="(msg, i) in messages"
        :key="i"
        class="sp-row"
        :class="msg.role"
      >
        <img
          class="sp-avatar"
          :src="msg.role === 'user' ? '/images/user.png' : (currentAgent?.avatar || '/images/robot.png')"
          alt="avatar"
        />
        <div class="sp-body">
          <div class="sp-md" v-html="renderMd(displayedOf(i))"></div>
          <span v-if="msg.streaming" class="sp-dot">●●●</span>
          <div
            v-if="msg.role === 'assistant' && !msg.streaming && (msg.references?.length || msg.totalCost)"
            class="sp-actions"
          >
            <n-button
              v-if="msg.references && msg.references.length"
              text
              type="primary"
              size="tiny"
              @click="openRefs(msg.references!)"
            >
              <template #icon><n-icon><FileTextOutlined /></n-icon></template>
              引用来源（{{ msg.references.length }}）
            </n-button>
            <span v-if="msg.totalCost" class="sp-cost">总耗时 {{ (msg.totalCost / 1000).toFixed(2) }}s</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="sp-input-area">
      <textarea
        v-model="input"
        class="sp-input"
        rows="3"
        placeholder="输入问题，Enter 发送，Shift+Enter 换行"
        :disabled="streaming"
        @keydown.enter.exact.prevent="onSend"
      ></textarea>
      <div class="sp-input-actions">
        <n-button v-if="streaming" type="error" ghost size="small" @click="onStop">停止生成</n-button>
        <n-button
          v-else
          type="primary"
          size="small"
          :disabled="!canSend"
          @click="onSend"
        >
          <template #icon><n-icon><SendOutlined /></n-icon></template>
          发送
        </n-button>
      </div>
    </div>

    <!-- 引用来源抽屉 -->
    <n-drawer v-model:show="refsDrawerVisible" :width="420" placement="right">
      <n-drawer-content title="引用来源">
        <div v-for="r in refsDrawerData" :key="r.index" class="sp-ref-item">
          <n-tag size="small" round type="info">{{ r.index }}</n-tag>
          <div class="sp-ref-body" v-html="renderMd(r.content)"></div>
        </div>
        <n-empty v-if="refsDrawerData.length === 0" description="暂无引用" />
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<script setup lang="ts">
  import { ref, computed, nextTick, onMounted } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import { useMessage } from 'naive-ui';
  import {
    ArrowLeftOutlined,
    FileTextOutlined,
    SendOutlined,
  } from '@vicons/antd';
  import { marked } from 'marked';
  import {
    streamAgentChat,
    getAgentEnabledList,
    type Agent,
    type AgentChatMessage,
    type AgentReference,
  } from '@/api/system/agent';
  import { useTypewriter } from '@/composables/useTypewriter';

  marked.use({ breaks: true, gfm: true });

  const route = useRoute();
  const router = useRouter();
  const message = useMessage();

  const sessionId = computed(() => String(route.params.id || ''));
  const shortId = computed(() => (sessionId.value ? sessionId.value.slice(0, 8) : '—'));

  const messages = ref<AgentChatMessage[]>([]);
  const input = ref('');
  const streaming = ref(false);
  const msgBoxRef = ref<HTMLElement | null>(null);

  const agents = ref<Agent[]>([]);
  const currentAgentId = ref<string>('');
  const currentAgent = computed(() => agents.value.find((a) => a.id === currentAgentId.value) || null);
  const agentOptions = computed(() =>
    agents.value.map((a) => ({ label: a.name || a.id || '未命名智能体', value: a.id as string }))
  );

  const canSend = computed(() => !!input.value.trim() && !!currentAgentId.value && !streaming.value);

  // ---- 流式打字机 ----
  const streamFullText = ref('');
  const streamDone = ref(false);
  const { displayed: typedDisplayed } = useTypewriter(
    () => streamFullText.value,
    () => streamDone.value
  );
  const streamingIdx = ref(-1);

  function displayedOf(i: number): string {
    const msg = messages.value[i];
    if (msg.role === 'assistant' && i === streamingIdx.value && streaming.value) {
      return typedDisplayed.value;
    }
    return msg.content;
  }

  // ---- 引用来源抽屉 ----
  const refsDrawerVisible = ref(false);
  const refsDrawerData = ref<AgentReference[]>([]);
  function openRefs(refs: AgentReference[]) {
    refsDrawerData.value = refs || [];
    refsDrawerVisible.value = true;
  }

  // ---- 智能体加载 ----
  async function loadAgents() {
    try {
      const resp: any = await getAgentEnabledList();
      const data = resp?.data ?? resp;
      const list: Agent[] = Array.isArray(data)
        ? data
        : data?.list ?? data?.records ?? [];
      agents.value = list;
      const fromQuery = (route.query.agentId as string) || '';
      if (fromQuery && list.some((a) => a.id === fromQuery)) {
        currentAgentId.value = fromQuery;
      } else if (list.length) {
        currentAgentId.value = (list[0].id as string) || '';
      }
    } catch (e: any) {
      message.error('加载智能体失败：' + (e?.message || e));
    }
  }

  function onAgentChange() {
    // 切换智能体仅影响后续发送，不重置当前消息
  }

  // ---- 发送 ----
  let abortCtl: AbortController | null = null;

  async function onSend() {
    const q = input.value.trim();
    if (!q || streaming.value || !currentAgentId.value) return;

    messages.value.push({ role: 'user', content: q });
    input.value = '';

    // 占位 assistant 消息；预声明字段确保响应式
    resetStream();
    streamingIdx.value = messages.value.length;
    messages.value.push({
      role: 'assistant',
      content: '',
      streaming: true,
      totalCost: 0,
      stageTimings: {},
    });
    streaming.value = true;
    await scrollBottom();

    abortCtl = new AbortController();
    try {
      await streamAgentChat(
        { agentId: currentAgentId.value, conversationId: sessionId.value, query: q },
        {
          onAnswer: (token) => {
            streamFullText.value += token;
          },
          onComplete: (payload) => {
            const msg = messages.value[streamingIdx.value];
            if (msg) {
              msg.content = payload.answer;
              msg.references = payload.references;
              msg.stageTimings = payload.stageTimings;
              msg.totalCost = payload.totalCost;
              msg.streaming = false;
            }
            streamDone.value = true;
            messages.value = [...messages.value];
          },
          onError: (errMsg) => {
            const msg = messages.value[streamingIdx.value];
            if (msg) {
              msg.content = `⚠️ ${errMsg}`;
              msg.streaming = false;
            }
            streamDone.value = true;
            message.error(errMsg);
          },
        },
        abortCtl.signal
      );
    } catch (e: any) {
      // 主动停止（abort）时忽略；否则提示请求失败
      if (e?.name !== 'AbortError') {
        const msg = messages.value[streamingIdx.value];
        if (msg) {
          msg.content = `⚠️ 请求失败：${e?.message || e}`;
          msg.streaming = false;
        }
        message.error('对话请求失败');
      }
    } finally {
      streaming.value = false;
      nextTick(() => {
        const msg = messages.value[streamingIdx.value];
        if (msg) msg.content = streamFullText.value || msg.content;
        scrollBottom();
      });
    }
  }

  function onStop() {
    abortCtl?.abort();
    streaming.value = false;
    message.info('已停止生成');
  }

  function resetStream() {
    streamFullText.value = '';
    streamDone.value = false;
    streamingIdx.value = -1;
  }

  function goBack() {
    router.push('/chat/index');
  }

  async function scrollBottom() {
    await nextTick();
    if (msgBoxRef.value) {
      msgBoxRef.value.scrollTop = msgBoxRef.value.scrollHeight;
    }
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

  onMounted(async () => {
    await loadAgents();
    // 若落地页带初始问题，自动发送
    const initQ = (route.query.q as string) || '';
    if (initQ && currentAgentId.value) {
      input.value = initQ;
      onSend();
    }
  });
</script>

<style lang="less" scoped>
  .session-page {
    display: flex;
    flex-direction: column;
    height: 100%;
    width: 100%;
    background: var(--body-color, #f7f8fa);
  }

  .sp-head {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 16px;
    border-bottom: 1px solid var(--border-color, #eee);
    background: var(--card-color, #fff);
    .sp-title {
      flex: 1;
      display: flex;
      align-items: center;
      gap: 8px;
      .sp-title-text {
        font-size: 15px;
        font-weight: 600;
        color: var(--text-color, #333);
      }
    }
  }

  .sp-msg-list {
    flex: 1;
    overflow-y: auto;
    padding: 16px 20px;
  }

  .sp-row {
    display: flex;
    gap: 10px;
    margin-bottom: 16px;
    &.user {
      flex-direction: row-reverse;
      .sp-body {
        align-items: flex-end;
      }
      .sp-md {
        background: var(--primary-color, #18a058);
        color: #fff;
      }
    }
  }

  .sp-avatar {
    flex-shrink: 0;
    width: 32px;
    height: 32px;
    border-radius: 50%;
    object-fit: cover;
    background: #f0f0f0;
    display: block;
  }

  .sp-body {
    display: flex;
    flex-direction: column;
    max-width: 72%;
    gap: 6px;
  }

  .sp-md {
    background: var(--card-color, #fff);
    border: 1px solid var(--border-color, #eee);
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

  .sp-dot {
    color: var(--primary-color, #18a058);
    font-size: 10px;
    animation: sp-blink 1s infinite;
    letter-spacing: 2px;
  }
  @keyframes sp-blink {
    0%,
    100% {
      opacity: 0.3;
    }
    50% {
      opacity: 1;
    }
  }

  .sp-actions {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }
  .sp-cost {
    font-size: 11px;
    color: var(--text-color-3, #aaa);
  }

  .sp-input-area {
    border-top: 1px solid var(--border-color, #eee);
    padding: 12px 16px;
    background: var(--card-color, #fff);
    .sp-input {
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
        border-color: var(--primary-color, #18a058);
      }
      &:disabled {
        background: #f5f5f5;
        cursor: not-allowed;
      }
    }
    .sp-input-actions {
      display: flex;
      justify-content: flex-end;
      margin-top: 8px;
    }
  }

  .sp-ref-item {
    margin-bottom: 16px;
    padding-bottom: 16px;
    border-bottom: 1px solid #f0f0f0;
    &:last-child {
      border-bottom: none;
    }
    .sp-ref-body {
      margin-top: 6px;
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
    }
  }
</style>
