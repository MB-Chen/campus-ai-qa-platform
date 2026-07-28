<!-- 调试聊天面板：浮窗式，调 streamWorkflowChat 走 SSE。
     每条 assistant 回答完成后，会在气泡下方挂「查看执行详情 / 耗时 / tokens」按钮，
     点详情按该轮 runtimeId 打开执行详情弹窗（对标智能体的 msg-actions）。 -->
<template>
  <div class="customer-chat-box">
    <div class="header">
      <div class="title-box">
        <div class="title-label">编</div>
        <div class="title">编排调试</div>
      </div>
      <n-icon :size="18" style="cursor: pointer" @click="$emit('closeDebug')">
        <CloseOutlined />
      </n-icon>
    </div>
    <div class="content">
      <div ref="msgBoxRef" class="msg-list">
        <div v-for="(m, i) in messages" :key="i" :class="['msg-row', m.role]">
          <div class="msg-col">
            <div class="msg-bubble">{{ m.content }}</div>
            <!-- assistant 完成后：该轮专属的执行详情入口 + 耗时/tokens -->
            <div
              v-if="m.role === 'assistant' && !loading && m.runtimeId && (m.content || m.costSec)"
              class="msg-actions"
            >
              <n-button text type="primary" size="tiny" @click="$emit('showDetail', m.runtimeId)">
                <template #icon><n-icon><ApartmentOutlined /></n-icon></template>
                查看执行详情
              </n-button>
              <span v-if="m.costSec" class="meta-chip">
                <n-icon :size="12"><ClockCircleOutlined /></n-icon>
                {{ m.costSec }}s
              </span>
              <span v-if="m.totalTokens" class="meta-chip">
                <n-icon :size="12"><ThunderboltOutlined /></n-icon>
                {{ m.totalTokens }} tokens
              </span>
            </div>
          </div>
        </div>
        <div v-if="loading" class="msg-row assistant">
          <div class="msg-col">
            <div class="msg-bubble typing">生成中…</div>
          </div>
        </div>
      </div>
      <div class="input-area">
        <n-input
          v-model:value="input"
          type="textarea"
          :autosize="{ minRows: 1, maxRows: 4 }"
          placeholder="输入问题，回车发送（Shift+回车换行）"
          @keydown.enter="onEnter"
          :disabled="loading"
        />
        <n-button type="primary" secondary :loading="loading" @click="send">发送</n-button>
      </div>
    </div>
  </div>
</template>

<script setup>
  import { ref, nextTick } from 'vue';
  import {
    CloseOutlined,
    ApartmentOutlined,
    ClockCircleOutlined,
    ThunderboltOutlined,
  } from '@vicons/antd';
  import { useMessage } from 'naive-ui';
  import { streamWorkflowChat } from '@/api/system/workflow';

  const props = defineProps({
    workflowId: { type: String, required: true },
  });
  const emit = defineEmits(['closeDebug', 'showDetail']);

  const nMessage = useMessage();
  const messages = ref([]);
  const input = ref('');
  const loading = ref(false);
  const msgBoxRef = ref(null);
  const conversationId = ref(''); // 维持多轮记忆
  let abortCtrl = null;

  function onEnter(e) {
    if (e.shiftKey) return; // 换行
    e.preventDefault();
    send();
  }

  async function send() {
    const q = input.value.trim();
    if (!q || loading.value) return;
    messages.value.push({ role: 'user', content: q });
    input.value = '';
    loading.value = true;

    // 占位 assistant 消息：预声明 runtimeId/costSec/totalTokens 字段，
    // 避免 Vue 对数组元素新增 key 不触发响应式（v-if 不重新求值）。
    const assistantIdx = messages.value.length;
    const assistantMsg = ref('');
    messages.value.push({
      role: 'assistant',
      content: '',
      runtimeId: null,
      costSec: null,
      totalTokens: null,
    });
    // 本轮 runtimeId 缓存（node 事件带同一个 runtimeId，取首个即可）
    let roundRuntimeId = null;
    await scrollToBottom();

    abortCtrl = new AbortController();
    try {
      await streamWorkflowChat(
        {
          workflowId: props.workflowId,
          conversationId: conversationId.value || undefined,
          query: q,
        },
        {
          onAnswer: (token) => {
            assistantMsg.value += token;
            messages.value[assistantIdx].content = assistantMsg.value;
            scrollToBottom();
          },
          onNode: (p) => {
            // 节点开始：记录本轮 runtimeId（首个 node 事件即流程 runtimeId）
            if (p.runtimeId && !roundRuntimeId) {
              roundRuntimeId = p.runtimeId;
              messages.value[assistantIdx].runtimeId = roundRuntimeId;
            }
          },
          onComplete: (p) => {
            // ★ 修复 Bug4：把耗时/tokens 写进本轮消息（而非全局 lastRuntimeId），
            // 每条回答各自带入口，多轮对话不再只指向第一次。
            if (p.time != null) {
              messages.value[assistantIdx].costSec = Number(p.time).toFixed(2);
            }
            if (p.totalTokens != null && p.totalTokens > 0) {
              messages.value[assistantIdx].totalTokens = p.totalTokens;
              // 顺带给 assistant 文本补一行汇总（保留原行为）
              assistantMsg.value += `\n\n_— 耗时 ${p.time ?? 0}s，tokens: ${p.totalTokens}_`;
              messages.value[assistantIdx].content = assistantMsg.value;
            }
            // 触发响应式重算（数组元素字段变更 + 浅拷贝数组）
            messages.value = [...messages.value];
          },
          onError: (msg) => {
            messages.value[assistantIdx].content = '❌ ' + (msg || '执行失败');
          },
        },
        abortCtrl.signal,
      );
    } catch (e) {
      messages.value[assistantIdx].content = '❌ 连接异常：' + (e?.message || e);
      nMessage.error('调试连接异常');
    } finally {
      loading.value = false;
      abortCtrl = null;
      // 兜底：若整轮没收到 node 事件（异常情况），用最后已知 runtimeId 补上
      if (!messages.value[assistantIdx].runtimeId) {
        const lastWithId = [...messages.value]
          .reverse()
          .find((m) => m.role === 'assistant' && m.runtimeId);
        if (lastWithId) messages.value[assistantIdx].runtimeId = lastWithId.runtimeId;
      }
      scrollToBottom();
    }
  }

  async function scrollToBottom() {
    await nextTick();
    if (msgBoxRef.value) {
      msgBoxRef.value.scrollTop = msgBoxRef.value.scrollHeight;
    }
  }
</script>

<style scoped>
  .customer-chat-box {
    z-index: 1999;
    border-radius: 8px;
    border: 1px solid #fff;
    background: #f4f4f4;
    box-shadow: 0 4px 8px rgba(31, 35, 41, 0.1);
    position: fixed;
    bottom: 16px;
    right: 16px;
    overflow: hidden;
    width: 450px;
    height: 600px;
    display: flex;
    flex-direction: column;
  }
  .header {
    width: 100%;
    height: 56px;
    background: #fff;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 16px;
    flex-shrink: 0;
  }
  .title-box {
    display: flex;
    align-items: center;
  }
  .title {
    font-size: 14px;
    margin-left: 10px;
  }
  .title-label {
    background: #18a058;
    color: #fff;
    border-radius: 8px;
    height: 35px;
    width: 35px;
    line-height: 35px;
    text-align: center;
    font-weight: bold;
  }
  .content {
    padding: 10px 16px;
    width: 100%;
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }
  .msg-list {
    flex: 1;
    overflow-y: auto;
    padding: 6px 4px;
  }
  .msg-row {
    display: flex;
    margin-bottom: 10px;
  }
  .msg-row.user {
    justify-content: flex-end;
  }
  /* 包一层 msg-col，让气泡与动作按钮垂直排列（assistant 侧左对齐） */
  .msg-col {
    display: flex;
    flex-direction: column;
    max-width: 85%;
    gap: 4px;
  }
  .msg-row.user .msg-col {
    align-items: flex-end;
  }
  .msg-bubble {
    max-width: 100%;
    padding: 8px 12px;
    border-radius: 8px;
    font-size: 13px;
    line-height: 1.6;
    white-space: pre-wrap;
    word-break: break-word;
  }
  .msg-row.user .msg-bubble {
    background: #18a058;
    color: #fff;
  }
  .msg-row.assistant .msg-bubble {
    background: #fff;
    color: #333;
  }
  .msg-bubble.typing {
    color: #999;
  }
  /* 每轮回答的动作区：执行详情 + 耗时 + tokens */
  .msg-actions {
    display: flex;
    align-items: center;
    gap: 10px;
    padding-left: 2px;
    flex-wrap: wrap;
  }
  .meta-chip {
    display: inline-flex;
    align-items: center;
    gap: 3px;
    font-size: 11px;
    color: #999;
    font-variant-numeric: tabular-nums;
  }
  .input-area {
    display: flex;
    gap: 8px;
    align-items: flex-end;
    margin-top: 8px;
  }
</style>
