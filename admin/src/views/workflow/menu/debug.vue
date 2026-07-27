<!-- 调试聊天面板：浮窗式，调 streamWorkflowChat 走 SSE。complete 事件可回溯执行详情。 -->
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
          <div class="msg-bubble">{{ m.content }}</div>
        </div>
        <div v-if="loading" class="msg-row assistant">
          <div class="msg-bubble typing">生成中…</div>
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
        <n-button type="primary" :loading="loading" @click="send">发送</n-button>
      </div>
      <div v-if="lastRuntimeId" class="detail-entry">
        <n-button text type="primary" @click="$emit('showDetail', lastRuntimeId)">
          查看执行详情
        </n-button>
      </div>
    </div>
  </div>
</template>

<script setup>
  import { ref, nextTick } from 'vue';
  import { CloseOutlined } from '@vicons/antd';
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
  const lastRuntimeId = ref(null);
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
    const assistantMsg = ref('');
    messages.value.push({ role: 'assistant', content: '' });
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
            messages.value[messages.value.length - 1].content = assistantMsg.value;
            scrollToBottom();
          },
          onNode: (p) => {
            // 节点开始：记录 runtimeId 供执行详情回溯
            if (p.runtimeId) {
              lastRuntimeId.value = p.runtimeId;
            }
          },
          onComplete: (p) => {
            if (p.totalTokens != null) {
              assistantMsg.value += `\n\n_— 耗时 ${p.time ?? 0}s，tokens: ${p.totalTokens}_`;
              messages.value[messages.value.length - 1].content = assistantMsg.value;
            }
          },
          onError: (msg) => {
            messages.value[messages.value.length - 1].content =
              '❌ ' + (msg || '执行失败');
          },
        },
        abortCtrl.signal,
      );
    } catch (e) {
      messages.value[messages.value.length - 1].content =
        '❌ 连接异常：' + (e?.message || e);
      nMessage.error('调试连接异常');
    } finally {
      loading.value = false;
      abortCtrl = null;
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
  .msg-bubble {
    max-width: 80%;
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
  .input-area {
    display: flex;
    gap: 8px;
    align-items: flex-end;
    margin-top: 8px;
  }
  .detail-entry {
    margin-top: 6px;
    text-align: right;
  }
</style>
