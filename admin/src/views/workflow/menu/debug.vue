<!-- 调试面板：抽屉式内容（由父组件 workflow/edit.vue 用 n-drawer 包裹）。
     「合二为一」改造：输入消息发送后，下方按执行顺序输出每一步的详情卡片，
     把原「聊天 + 查看执行详情弹窗」合并成单一时间线。
     —— 流式过程中按 node/node_end 事件实时显示步骤骨架（执行中/完成），
        整轮 complete 后用 getRunDetail(runtimeId) 一次性回填每步真实详情（prompt/输出/耗时/召回片段…），
        详情渲染复用 runtimeDetail.js 的 NodeBody（与「执行详情」弹窗完全一致）。 -->
<template>
  <div class="debug-panel">
    <!-- 输入区固定在顶部：发送后下方滚动区按顺序输出执行流程 -->
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
    <div ref="msgBoxRef" class="msg-list">
      <template v-for="(m, mi) in messages" :key="mi">
        <!-- 用户提问 -->
        <div v-if="m.kind === 'user'" class="msg-row user">
          <div class="msg-bubble">{{ m.content }}</div>
        </div>

        <!-- 一轮执行：最终回复 + 步骤时间线 -->
        <div v-else class="msg-row assistant">
          <div class="round-col">
            <!-- 最终回复（answer 流式累加；异常时显示错误） -->
            <div
              v-if="m.answer || m.status === 'error'"
              class="round-answer md markdown-content"
              :class="{ 'round-error': m.status === 'error' }"
              v-html="renderMd(m.status === 'error' ? m.answer || '执行失败' : m.answer)"
            ></div>
            <div
              v-else-if="m.status === 'running' && m.steps.length === 0"
              class="round-answer typing"
            >
              生成中…
            </div>

            <!-- 步骤时间线 -->
            <div v-if="m.steps.length" class="step-timeline">
              <div
                v-for="(step, si) in m.steps"
                :key="step.cell || si"
                class="step-card"
                :class="{ 'is-open': isOpen(mi, si), 'is-running': step.status === 'running' }"
              >
                <div class="step-head" @click="toggleStep(mi, si)">
                  <n-icon
                    :component="CaretRightOutlined"
                    :size="14"
                    :style="{ transform: isOpen(mi, si) ? 'rotate(90deg)' : '' }"
                    class="step-caret"
                  />
                  <span class="step-num">{{ step.step || si + 1 }}</span>
                  <n-icon
                    v-if="stepMeta(step).icon"
                    :component="stepMeta(step).icon"
                    :color="stepMeta(step).color"
                    :size="16"
                  />
                  <span class="step-name">{{
                    stepMeta(step).name || step.nodeType || '节点'
                  }}</span>
                  <!-- 耗时徽标 -->
                  <span
                    v-if="stepMeta(step).costMs != null"
                    class="cost-badge"
                    :class="costClass(stepMeta(step).costMs)"
                  >
                    {{ (stepMeta(step).costMs / 1000).toFixed(2) }}s
                  </span>
                  <!-- 执行中转圈 -->
                  <span v-if="step.status === 'running'" class="step-spin"></span>
                  <span v-else class="step-check">✓</span>
                </div>
                <n-collapse-transition :show="isOpen(mi, si)">
                  <div class="step-body">
                    <div v-if="!m.detailLoaded" class="detail-loading">详情加载中…</div>
                    <node-body v-else :item="step" />
                  </div>
                </n-collapse-transition>
              </div>
            </div>

            <!-- 整轮汇总：耗时 + tokens -->
            <div v-if="m.status === 'done' && (m.costSec || m.totalTokens)" class="round-foot">
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
      </template>
    </div>
  </div>
</template>

<script setup>
  import { ref, reactive, nextTick } from 'vue';
  import { CaretRightOutlined, ClockCircleOutlined, ThunderboltOutlined } from '@vicons/antd';
  import { marked } from 'marked';
  import { useMessage } from 'naive-ui';
  import { streamWorkflowChat, getRunDetail } from '@/api/system/workflow';
  // 复用「执行详情」的节点类型渲染件 + meta 解析
  import { NodeBody, nodeMeta, costClass } from './runtimeDetail.js';

  const props = defineProps({
    workflowId: { type: String, required: true },
  });

  // 与聊天页一致：单 \n 转 <br>，开启 gfm（表格/任务列表/删除线）
  marked.use({ breaks: true, gfm: true });

  const nMessage = useMessage();
  const messages = ref([]);
  const input = ref('');
  const loading = ref(false);
  const msgBoxRef = ref(null);
  const conversationId = ref(''); // 维持多轮记忆
  // 展开的步骤集合：key = `${roundIdx}-${stepIdx}`
  const expanded = ref(new Set());
  let abortCtrl = null;

  // 取步骤 meta（图标/名称/耗时），优先用回填后的真实数据，运行时骨架也能显示节点名
  function stepMeta(step) {
    return nodeMeta(step);
  }

  function isOpen(mi, si) {
    return expanded.value.has(`${mi}-${si}`);
  }
  function toggleStep(mi, si) {
    const key = `${mi}-${si}`;
    const set = new Set(expanded.value);
    if (set.has(key)) set.delete(key);
    else set.add(key);
    expanded.value = set;
  }

  // markdown 渲染：与 chat/index.vue 同款（补 # 后缺空格的标题）
  function renderMd(text) {
    if (!text) return '';
    try {
      const normalized = text.replace(/^(#{1,6})(?=\S)/gm, '$1 ');
      return marked.parse(normalized);
    } catch {
      return text;
    }
  }

  function onEnter(e) {
    if (e.shiftKey) return; // 换行
    e.preventDefault();
    send();
  }

  async function send() {
    const q = input.value.trim();
    if (!q || loading.value) return;
    messages.value.push({ kind: 'user', content: q });
    input.value = '';
    loading.value = true;

    // 一轮执行消息：预声明全部字段保证响应式
    const roundIdx = messages.value.length;
    const round = reactive({
      kind: 'round',
      query: q,
      runtimeId: null,
      status: 'running', // running | done | error
      answer: '',
      costSec: null,
      totalTokens: null,
      steps: [], // { cell, nodeType, status, step?, outputData?, modelData? }
      detailLoaded: false,
    });
    messages.value.push(round);
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
            round.answer += token;
            scrollToBottom();
          },
          onNode: (p) => {
            // 节点开始：记录本轮 runtimeId（首个 node 事件即流程 runtimeId）
            if (p.runtimeId && !roundRuntimeId) {
              roundRuntimeId = p.runtimeId;
              round.runtimeId = roundRuntimeId;
            }
            // 同一 cell 不重复追加（理论上不会，做个保护）
            const exists = round.steps.some((s) => s.cell === p.cell);
            if (!exists) {
              round.steps.push({
                cell: p.cell,
                nodeType: p.nodeType,
                status: 'running',
              });
              scrollToBottom();
            }
          },
          onNodeEnd: (p) => {
            // 节点结束：标记完成（详情待 complete 后统一回填）
            const s = round.steps.find((x) => x.cell === p.cell);
            if (s) s.status = 'done';
          },
          onComplete: async (p) => {
            if (p.time != null) round.costSec = Number(p.time).toFixed(2);
            if (p.totalTokens != null && p.totalTokens > 0) round.totalTokens = p.totalTokens;
            round.status = 'done';
            // 回填每步真实详情
            await fetchDetail(round, roundRuntimeId);
            scrollToBottom();
          },
          onError: (msg) => {
            round.status = 'error';
            round.answer = '❌ ' + (msg || '执行失败');
          },
        },
        abortCtrl.signal
      );
    } catch (e) {
      round.status = 'error';
      round.answer = '❌ 连接异常：' + (e?.message || e);
      nMessage.error('调试连接异常');
    } finally {
      loading.value = false;
      abortCtrl = null;
      // 兜底：若整轮没收到 node 事件（异常情况），用最后已知 runtimeId 补上并尝试拉详情
      if (!round.runtimeId) {
        const lastWithId = [...messages.value]
          .reverse()
          .find((m) => m.kind === 'round' && m.runtimeId);
        if (lastWithId) {
          round.runtimeId = lastWithId.runtimeId;
          if (round.status !== 'error') await fetchDetail(round, round.runtimeId);
        }
      }
      scrollToBottom();
    }
  }

  // 整轮结束后拉取每一步的真实详情（outputData/modelData/step），合并到 steps。
  // 优先以后端返回行（按 step 排序）为准；运行时拿到的 nodeType/name 作为兜底。
  async function fetchDetail(round, rid) {
    const runtimeId = rid || round.runtimeId;
    if (!runtimeId) {
      round.detailLoaded = true;
      return;
    }
    try {
      const res = await getRunDetail(runtimeId);
      if (res && res.code === 0 && Array.isArray(res.data)) {
        // 过滤脏行，按 step 排序
        const rows = res.data
          .filter((x) => x && (x.outputData || x.modelData))
          .slice()
          .sort((a, b) => (a.step ?? 0) - (b.step ?? 0));
        if (rows.length) {
          // 以后端真实行替换骨架：cell 能对上的合并，对不上的直接用后端行
          const merged = rows.map((row) => {
            const matched = round.steps.find((s) => s.cell && s.cell === row.cell);
            return {
              cell: row.cell || matched?.cell,
              nodeType: row.nodeType || matched?.nodeType,
              status: 'done',
              step: row.step,
              outputData: row.outputData,
              modelData: row.modelData,
            };
          });
          round.steps = merged;
        }
      }
    } catch {
      // 拉取失败：保留运行时骨架，但标记已加载（避免一直转「加载中」）
    } finally {
      round.detailLoaded = true;
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
  .debug-panel {
    display: flex;
    flex-direction: column;
    height: 100%;
    background: #f4f4f4;
    overflow: hidden;
  }
  .msg-list {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    padding: 14px 16px;
  }
  .msg-row {
    display: flex;
    margin-bottom: 16px;
  }
  .msg-row.user {
    justify-content: flex-end;
  }
  .msg-row.user .msg-bubble {
    max-width: 88%;
    padding: 9px 13px;
    border-radius: 8px;
    font-size: 14px;
    line-height: 1.6;
    word-break: break-word;
    white-space: pre-wrap;
    background: #18a058;
    color: #fff;
  }

  /* 一轮执行区 */
  .round-col {
    display: flex;
    flex-direction: column;
    width: 100%;
    gap: 8px;
  }
  .round-answer {
    max-width: 100%;
    padding: 9px 13px;
    border-radius: 8px;
    font-size: 14px;
    line-height: 1.6;
    word-break: break-word;
    white-space: normal;
    background: #fff;
    color: #333;
  }
  .round-answer.typing {
    color: #999;
  }
  .round-answer.round-error {
    background: #fff1f0;
    color: #cf1322;
  }

  /* 步骤时间线 */
  .step-timeline {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
  .step-card {
    background: #fff;
    border: 1px solid #eee;
    border-radius: 6px;
    padding: 7px 10px;
    transition: border-color 0.15s;
  }
  .step-card.is-open {
    border-color: #d0d5dc;
  }
  .step-card.is-running {
    border-color: #6172f3;
  }
  .step-head {
    display: flex;
    align-items: center;
    gap: 6px;
    cursor: pointer;
    user-select: none;
  }
  .step-caret {
    color: #999;
    flex-shrink: 0;
  }
  .step-num {
    width: 18px;
    height: 18px;
    line-height: 18px;
    text-align: center;
    background: #646a73;
    color: #fff;
    border-radius: 50%;
    font-size: 11px;
    flex-shrink: 0;
  }
  .step-name {
    font-size: 13px;
    font-weight: 500;
    color: #333;
  }
  .cost-badge {
    margin-left: 2px;
    padding: 1px 7px;
    border-radius: 10px;
    font-size: 11px;
    font-variant-numeric: tabular-nums;
  }
  .cost-ok {
    background: #f6ffed;
    color: #18a058;
  }
  .cost-warning {
    background: #fff7e6;
    color: #fa8c16;
  }
  .cost-danger {
    background: #fff1f0;
    color: #cf1322;
  }
  /* 执行中转圈 */
  .step-spin {
    margin-left: auto;
    width: 12px;
    height: 12px;
    border: 2px solid #6172f3;
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
    color: #18a058;
    font-size: 13px;
    font-weight: 700;
    flex-shrink: 0;
  }
  .step-body {
    margin-top: 8px;
    font-size: 13px;
  }
  .detail-loading {
    padding: 8px 4px;
    color: #999;
    font-size: 12px;
  }

  /* 整轮汇总 */
  .round-foot {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 2px 2px 0;
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
    padding: 12px 16px;
    background: #fff;
    border-bottom: 1px solid #eee;
    flex-shrink: 0;
  }

  /* ====== 节点详情渲染样式（从 runtime.vue 同步，NodeBody 依赖这些 class） ====== */
  .detail-stack {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }
  .detail-grid {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  .detail-title {
    font-size: 12px;
    font-weight: 600;
    color: #333;
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
    color: #999;
    padding-top: 2px;
  }
  :deep(.kv-val) {
    flex: 1;
    font-size: 13px;
    color: #333;
    line-height: 1.6;
    word-break: break-word;
  }
  :deep(.kv-strong) {
    color: #18a058;
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
    background: #f5f6f7;
    border-radius: 6px;
    padding: 10px 12px;
    font-size: 13px;
    line-height: 1.6;
    color: #333;
    word-break: break-word;
  }
  :deep(.md-output p) {
    margin: 4px 0;
  }
  :deep(.md-output pre) {
    background: #282c34;
    color: #abb2bf;
    padding: 10px;
    border-radius: 6px;
    overflow-x: auto;
    font-size: 12px;
  }
  :deep(.md-output code) {
    background: rgba(0, 0, 0, 0.06);
    padding: 2px 4px;
    border-radius: 3px;
    font-size: 12px;
  }
  :deep(.md-output pre code) {
    background: transparent;
    padding: 0;
  }

  :deep(.stat-row) {
    display: flex;
    flex-wrap: wrap;
    gap: 16px;
    padding: 8px 10px;
    background: #f5f6f7;
    border-radius: 6px;
  }
  :deep(.stat-item) {
    display: flex;
    align-items: center;
    gap: 5px;
  }
  :deep(.stat-k) {
    font-size: 12px;
    color: #999;
  }
  :deep(.stat-v) {
    font-size: 13px;
    font-weight: 600;
    color: #333;
    font-variant-numeric: tabular-nums;
  }

  :deep(.frag-list) {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  :deep(.frag-card) {
    padding: 8px 10px;
    background: #f5f6f7;
    border-left: 3px solid #18a058;
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
    color: #999;
    font-weight: 600;
  }
  :deep(.frag-text) {
    font-size: 12px;
    line-height: 1.6;
    color: #555;
  }

  :deep(.cate-list) {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  :deep(.cate-item) {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 10px;
    background: #f5f6f7;
    border-radius: 4px;
  }
  :deep(.cate-hit) {
    background: #f6ffed;
    border: 1px solid #b7eb8f;
  }
  :deep(.cate-idx) {
    width: 20px;
    height: 20px;
    line-height: 20px;
    text-align: center;
    background: #d0d5dc;
    color: #fff;
    border-radius: 50%;
    font-size: 11px;
    font-weight: 600;
  }
  :deep(.cate-hit .cate-idx) {
    background: #18a058;
  }
  :deep(.cate-name) {
    flex: 1;
    font-size: 13px;
    color: #333;
  }
  :deep(.cate-tag) {
    padding: 1px 7px;
    background: #18a058;
    color: #fff;
    border-radius: 10px;
    font-size: 11px;
  }

  :deep(.branch-block) {
    padding: 8px 10px;
    background: #f5f6f7;
    border-radius: 4px;
    margin-bottom: 6px;
  }
  :deep(.branch-hit) {
    background: #f6ffed;
    border: 1px solid #b7eb8f;
  }
  :deep(.branch-head) {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 6px;
  }
  :deep(.branch-name) {
    font-size: 13px;
    font-weight: 600;
    color: #333;
  }
  :deep(.branch-logic) {
    padding: 0 6px;
    background: #d0d5dc;
    color: #fff;
    border-radius: 8px;
    font-size: 10px;
  }
  :deep(.cond-row) {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 3px 0;
    font-size: 12px;
    flex-wrap: wrap;
  }
  :deep(.cond-pass) {
    color: #18a058;
  }
  :deep(.cond-fail) {
    color: #999;
  }
  :deep(.cond-field) {
    font-weight: 600;
  }
  :deep(.cond-op) {
    padding: 0 5px;
    background: #fff;
    border-radius: 3px;
    font-size: 11px;
    color: #6172f3;
  }
  :deep(.cond-val),
  :deep(.cond-actual) {
    font-family: monospace;
    font-size: 11px;
    max-width: 200px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  :deep(.cond-arrow) {
    color: #bbb;
  }
  :deep(.cond-result) {
    font-weight: 700;
  }

  :deep(.note-tip) {
    padding: 6px 10px;
    background: #e6f7ff;
    border-left: 3px solid #1890ff;
    border-radius: 4px;
    font-size: 12px;
    color: #555;
  }

  :deep(.detail-box) {
    background: #f5f6f7;
    border-radius: 4px;
  }
  :deep(.json-view) {
    margin: 0;
    white-space: pre-wrap;
    word-break: break-word;
    font-size: 12px;
    color: #333;
    max-height: 240px;
    overflow-y: auto;
    padding: 8px 12px;
  }
</style>
