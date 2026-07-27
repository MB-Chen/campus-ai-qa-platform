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
          placeholder="直接向模型提问"
          :disabled="creating"
          class="cc-textarea"
          @keydown.enter.exact.prevent="handleSend"
          @focus="inputFocused = true"
          @blur="inputFocused = false"
        />

        <!-- 工具栏：左侧智能体选择 + 右侧发送 -->
        <div class="cc-toolbar">
          <div class="cc-toolbar-left">
            <!-- 智能体选择框：下拉选择智能体 -->
            <n-select
              v-model:value="selectedAgentId"
              :options="agentSelectOptions"
              placeholder="快速问答"
              size="small"
              class="cc-agent-select"
              :loading="agentLoading"
              filterable
              clearable
            />
          </div>

          <div class="cc-toolbar-right">
            <!-- 发送按钮 -->
            <n-button
              type="primary"
              size="small"
              :loading="creating"
              :disabled="!canSend"
              class="cc-send-btn"
              @click="handleSend"
            >
              <template #icon><n-icon size="15"><SendOutlined /></n-icon></template>
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
import { SendOutlined } from '@vicons/antd';
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
// 注意：默认用 null（而非 ''），否则 n-select 会把 '' 当作已选中值，
// 导致 placeholder「快速问答」不显示、框看起来是空的。
const agents = ref<Agent[]>([]);
const selectedAgentId = ref<string | null>(null);
const agentLoading = ref(false);

// ---- 推荐问题 ----
const suggestedQuestions = ref<string[]>([]);
const sqLoading = ref(false);

// ---- 计算属性 ----
const canSend = computed(() => !!inputValue.value.trim() && !creating.value);

const agentSelectOptions = computed(() =>
  agents.value.map((a) => ({
    label: a.name || '未命名',
    value: a.id as string,
  }))
);

// ---- 方法 ----
async function loadAgents() {
  agentLoading.value = true;
  try {
    const resp: any = await getAgentEnabledList();
    const data = resp?.data ?? resp;
    const list: Agent[] = Array.isArray(data)
      ? data
      : data?.list ?? data?.records ?? [];
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
      padding: 0;
    }
    :deep(.n-input__border) {
      display: none;
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

.cc-agent-select {
  min-width: 160px;
  max-width: 260px;
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
