<template>
  <n-drawer v-model:show="show" :width="560" placement="right">
    <n-drawer-content title="调用流程" :native-scrollbar="false">
      <div v-if="data" class="trace-body">
        <!-- 顶部汇总 -->
        <div class="trace-summary">
          <div class="summary-item">
            <n-icon :size="16"><FieldTimeOutlined /></n-icon>
            <span class="summary-label">总耗时</span>
            <span class="summary-val">{{ ((data.totalCost || 0) / 1000).toFixed(2) }}s</span>
          </div>
          <div class="summary-item">
            <n-icon :size="16"><ThunderboltOutlined /></n-icon>
            <span class="summary-label">LLM 调用</span>
            <span class="summary-val">{{ data.llmCallCount ?? 0 }} 次</span>
          </div>
        </div>

        <!-- 原始问题 -->
        <div v-if="data.originalQuery" class="trace-query">
          <span class="trace-query-label">原始问题</span>
          <span class="trace-query-text">{{ data.originalQuery }}</span>
        </div>

        <!-- 阶段耗时时间线 -->
        <div v-if="timingList.length" class="trace-section">
          <div class="section-title">阶段耗时</div>
          <n-timeline size="large">
            <n-timeline-item v-for="st in timingList" :key="st.name" :type="st.type">
              <template #header>
                <div class="timing-head">
                  <span class="timing-name">{{ stageLabel(st.name) }}</span>
                  <span class="timing-time">{{ st.ms }}ms · {{ st.percent }}%</span>
                </div>
              </template>
              <n-progress
                type="line"
                :percentage="st.percent"
                :height="6"
                :border-radius="3"
                :show-indicator="false"
                :status="st.type === 'error' ? 'error' : st.type === 'warning' ? 'warning' : 'success'"
              />
            </n-timeline-item>
          </n-timeline>
        </div>

        <!-- 各阶段上下文详情 -->
        <div class="trace-section">
          <div class="section-title">阶段上下文</div>
          <n-collapse :default-expanded-names="defaultExpanded">
            <!-- 改写拆分 -->
            <n-collapse-item
              v-if="stages['rewrite-split']"
              name="rewrite-split"
              :title="`${stageLabel('rewrite-split')} ${ranTag(stages['rewrite-split'])}`"
            >
              <template v-if="stages['rewrite-split'].ran">
                <div v-if="stages['rewrite-split'].original" class="kv-row">
                  <span class="kv-label">原问题</span>
                  <span class="kv-val">{{ stages['rewrite-split'].original }}</span>
                </div>
                <div v-if="stages['rewrite-split'].rewritten" class="kv-row">
                  <span class="kv-label">改写后</span>
                  <span class="kv-val kv-rewrite">{{ stages['rewrite-split'].rewritten }}</span>
                </div>
                <div
                  v-if="stages['rewrite-split'].subQuestions && stages['rewrite-split'].subQuestions!.length"
                  class="kv-row"
                >
                  <span class="kv-label">子问题</span>
                  <div class="chip-list">
                    <n-tag
                      v-for="(sq, i) in stages['rewrite-split'].subQuestions"
                      :key="i"
                      size="small"
                      round
                      >{{ sq }}</n-tag
                    >
                  </div>
                </div>
              </template>
              <skip-tip v-else stage="改写拆分" />
            </n-collapse-item>

            <!-- 意图判定 -->
            <n-collapse-item
              v-if="stages.intent"
              name="intent"
              :title="`${stageLabel('intent')} ${ranTag(stages.intent)}`"
            >
              <template v-if="stages.intent.code || stages.intent.desc">
                <div class="kv-row">
                  <span class="kv-label">意图</span>
                  <span class="kv-val">
                    <n-tag size="small" type="info">{{ stages.intent.desc || stages.intent.code }}</n-tag>
                    <n-tag
                      v-if="stages.intent.injected"
                      size="small"
                      type="warning"
                      style="margin-left: 6px"
                      >注入</n-tag
                    >
                  </span>
                </div>
                <div class="kv-row">
                  <span class="kv-label">是否检索</span>
                  <span class="kv-val">
                    <n-tag :type="stages.intent.needsRetrieval ? 'success' : 'default'" size="small">
                      {{ stages.intent.needsRetrieval ? '走检索' : '不走检索' }}
                    </n-tag>
                  </span>
                </div>
              </template>
              <skip-tip v-else stage="意图判定" />
            </n-collapse-item>

            <!-- 意图分类 -->
            <n-collapse-item
              v-if="stages['tree-intent']"
              name="tree-intent"
              :title="`${stageLabel('tree-intent')} ${ranTag(stages['tree-intent'])}`"
            >
              <template v-if="stages['tree-intent'].candidates && stages['tree-intent'].candidates!.length">
                <div
                  v-for="(c, i) in stages['tree-intent'].candidates"
                  :key="i"
                  class="candidate-row"
                >
                  <div class="candidate-head">
                    <n-tag size="tiny" :type="kindColor(c.kind)">{{ c.kind || '?' }}</n-tag>
                    <span class="candidate-name">{{ c.name || '未命名' }}</span>
                    <span class="candidate-score">{{ c.score.toFixed(3) }}</span>
                  </div>
                  <n-progress
                    type="line"
                    :percentage="scorePercent(c.score)"
                    :height="5"
                    :show-indicator="false"
                    status="success"
                  />
                  <div v-if="c.fullPath" class="candidate-path">{{ c.fullPath }}</div>
                </div>
              </template>
              <skip-tip v-else stage="意图分类" />
            </n-collapse-item>

            <!-- 歧义引导 -->
            <n-collapse-item
              v-if="stages.guidance"
              name="guidance"
              :title="`${stageLabel('guidance')} ${ranTag(stages.guidance)}`"
            >
              <template v-if="stages.guidance.ran">
                <div class="kv-row">
                  <span class="kv-label">是否反问</span>
                  <span class="kv-val">
                    <n-tag :type="stages.guidance.prompt ? 'warning' : 'success'" size="small">
                      {{ stages.guidance.prompt ? '需澄清' : '意图明确' }}
                    </n-tag>
                  </span>
                </div>
                <div v-if="stages.guidance.message" class="guidance-msg">
                  {{ stages.guidance.message }}
                </div>
              </template>
              <skip-tip v-else stage="歧义引导" />
            </n-collapse-item>

            <!-- 检索召回 -->
            <n-collapse-item
              v-if="stages.retrieve"
              name="retrieve"
              :title="`${stageLabel('retrieve')} ${ranTag(stages.retrieve)}`"
            >
              <template v-if="stages.retrieve.ran">
                <div class="kv-row">
                  <span class="kv-label">召回数</span>
                  <span class="kv-val">{{ stages.retrieve.count ?? 0 }} 条</span>
                </div>
                <div
                  v-if="stages.retrieve.fragments && stages.retrieve.fragments.length"
                  class="fragment-list"
                >
                  <div
                    v-for="(f, i) in stages.retrieve.fragments"
                    :key="i"
                    class="fragment-card"
                  >
                    <div class="fragment-head">
                      <span class="fragment-idx">#{{ i + 1 }}</span>
                      <n-tag size="tiny" :type="channelColor(f.channel)">{{ f.channel || 'vector' }}</n-tag>
                      <n-tag v-if="f.rrfRank" size="tiny" type="default">rank {{ f.rrfRank }}</n-tag>
                    </div>
                    <div class="fragment-text" v-html="renderMd(f.text)"></div>
                  </div>
                </div>
              </template>
              <skip-tip v-else stage="检索召回" />
            </n-collapse-item>

            <!-- 重排 -->
            <n-collapse-item
              v-if="stages.rerank"
              name="rerank"
              :title="`${stageLabel('rerank')} ${ranTag(stages.rerank)}`"
            >
              <template v-if="stages.rerank.ran">
                <div class="kv-row">
                  <span class="kv-label">阈值</span>
                  <span class="kv-val">{{ stages.rerank.threshold ?? '-' }}</span>
                </div>
                <div class="kv-row">
                  <span class="kv-label">TopK</span>
                  <span class="kv-val">{{ stages.rerank.topK ?? '-' }}</span>
                </div>
                <div class="kv-row">
                  <span class="kv-label">保留</span>
                  <span class="kv-val">{{ stages.rerank.keptCount ?? 0 }} 条</span>
                </div>
                <div v-if="stages.rerank.scored && stages.rerank.scored.length" class="scored-list">
                  <div v-for="(s, i) in stages.rerank.scored" :key="i" class="scored-row">
                    <div class="scored-head">
                      <span class="scored-idx">#{{ i + 1 }}</span>
                      <span class="scored-score" :class="{ dismissed: !s.kept }">{{
                        s.score.toFixed(3)
                      }}</span>
                      <n-tag
                        :type="s.kept ? 'success' : 'error'"
                        size="tiny"
                        :bordered="false"
                        >{{ s.kept ? '保留' : '过滤' }}</n-tag
                      >
                    </div>
                    <n-progress
                      type="line"
                      :percentage="scorePercent(s.score)"
                      :height="4"
                      :show-indicator="false"
                      :status="s.kept ? 'success' : 'error'"
                    />
                    <div class="scored-text" v-html="renderMd(s.text)"></div>
                  </div>
                </div>
              </template>
              <skip-tip v-else stage="重排" />
            </n-collapse-item>

            <!-- 合并 -->
            <n-collapse-item
              v-if="stages.merge"
              name="merge"
              :title="`${stageLabel('merge')} ${ranTag(stages.merge)}`"
            >
              <template v-if="stages.merge.ran">
                <div class="kv-row">
                  <span class="kv-label">最终上下文</span>
                  <span class="kv-val">{{ stages.merge.count ?? 0 }} 条</span>
                </div>
              </template>
              <skip-tip v-else stage="合并" />
            </n-collapse-item>

            <!-- 兜底 -->
            <n-collapse-item
              v-if="stages.fallback"
              name="fallback"
              :title="`${stageLabel('fallback')} ${ranTag(stages.fallback)}`"
            >
              <template v-if="stages.fallback.ran">
                <div class="kv-row">
                  <span class="kv-label">策略</span>
                  <span class="kv-val">{{ fallbackLabel(stages.fallback.strategy) }}</span>
                </div>
                <div v-if="stages.fallback.response" class="guidance-msg">
                  {{ stages.fallback.response }}
                </div>
              </template>
              <skip-tip v-else stage="兜底" />
            </n-collapse-item>

            <!-- 生成 -->
            <n-collapse-item
              v-if="stages.generate"
              name="generate"
              :title="`${stageLabel('generate')} ${ranTag(stages.generate)}`"
            >
              <template v-if="stages.generate.ran">
                <div class="kv-row">
                  <span class="kv-label">场景</span>
                  <span class="kv-val">
                    <n-tag size="small" :type="sceneColor(stages.generate.promptScene)">
                      {{ sceneLabel(stages.generate.promptScene) }}
                    </n-tag>
                  </span>
                </div>
              </template>
              <skip-tip v-else stage="生成" />
            </n-collapse-item>
          </n-collapse>
        </div>
      </div>
      <n-empty v-else description="暂无调用流程数据" style="margin-top: 80px" />
    </n-drawer-content>
  </n-drawer>
</template>

<script setup lang="ts">
  import { computed, h } from 'vue';
  import { FieldTimeOutlined, ThunderboltOutlined } from '@vicons/antd';
  import { marked } from 'marked';
  import type { RagStageData } from '@/api/system/agent';

  marked.use({ breaks: true, gfm: true });

  const props = defineProps<{ show: boolean; data?: RagStageData | null }>();
  const emit = defineEmits<{ 'update:show': [v: boolean] }>();

  const show = computed({
    get: () => props.show,
    set: (v) => emit('update:show', v),
  });

  // 阶段名 → 中文（对齐后端 STAGE_LABELS 契约）
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

  const stages = computed(() => props.data?.stages || {});

  // 阶段耗时列表（按 stageTimings 顺序，含占比着色）
  const timingList = computed(() => {
    const timings = props.data?.stageTimings || {};
    const total = props.data?.totalCost || 0;
    return Object.entries(timings).map(([name, ms]) => {
      const percent = total > 0 ? Math.round((ms / total) * 100) : 0;
      let type: 'error' | 'warning' | 'default' = 'default';
      if (percent > 50) type = 'error';
      else if (percent > 20) type = 'warning';
      return { name, ms, percent, type };
    });
  });

  // 默认展开已执行阶段
  const defaultExpanded = computed(() => {
    const ran: string[] = [];
    for (const [name, s] of Object.entries(stages.value)) {
      if (s && s.ran) ran.push(name);
    }
    return ran;
  });

  // 未执行阶段提示组件
  const SkipTip = (_: unknown, { attrs }: any) =>
    h('div', { class: 'skip-tip' }, `该阶段未执行（跳过或未触发）`);

  function ranTag(s: any): string {
    return s?.ran ? '' : '· 未执行';
  }

  // 候选分数转百分比（假设分数 0-1，放大显示）
  function scorePercent(score: number): number {
    return Math.max(0, Math.min(100, Math.round(score * 100)));
  }

  function kindColor(kind?: string): 'success' | 'warning' | 'info' {
    if (kind === 'KB') return 'success';
    if (kind === 'MCP') return 'warning';
    return 'info';
  }

  function channelColor(channel?: string): 'success' | 'warning' | 'info' | 'default' {
    const c = (channel || '').toLowerCase();
    if (c.includes('graph')) return 'warning';
    if (c.includes('keyword')) return 'info';
    if (c.includes('vector')) return 'success';
    return 'default';
  }

  const SCENE_LABELS: Record<string, string> = {
    KB_ONLY: '仅知识库',
    MCP_ONLY: '仅 MCP 工具',
    MIXED: '知识库 + MCP',
    EMPTY: '纯闲聊/无证据',
  };
  function sceneLabel(s?: string): string {
    return SCENE_LABELS[s || ''] || s || '-';
  }
  function sceneColor(s?: string): 'success' | 'warning' | 'info' | 'default' {
    if (s === 'KB_ONLY') return 'success';
    if (s === 'MCP_ONLY') return 'warning';
    if (s === 'MIXED') return 'info';
    return 'default';
  }

  function fallbackLabel(strategy?: string): string {
    if (strategy === 'model') return '走模型兜底';
    if (strategy === 'fixed') return '固定话术';
    return strategy || '-';
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
</script>

<style lang="less" scoped>
  .trace-body {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  /* 顶部汇总 */
  .trace-summary {
    display: flex;
    gap: 24px;
    padding: 12px 14px;
    background: var(--n-color);
    border: 1px solid var(--n-border-color);
    border-radius: 8px;
  }
  .summary-item {
    display: flex;
    align-items: center;
    gap: 6px;
    color: var(--n-text-color-3);
    .summary-label {
      font-size: 13px;
    }
    .summary-val {
      font-size: 15px;
      font-weight: 600;
      color: var(--n-text-color);
      font-variant-numeric: tabular-nums;
    }
  }

  /* 原始问题 */
  .trace-query {
    display: flex;
    gap: 8px;
    padding: 10px 12px;
    background: var(--n-action-color);
    border-radius: 8px;
    align-items: flex-start;
  }
  .trace-query-label {
    flex-shrink: 0;
    font-size: 12px;
    color: var(--n-text-color-3);
    padding-top: 2px;
  }
  .trace-query-text {
    font-size: 13px;
    color: var(--n-text-color);
    line-height: 1.6;
  }

  .trace-section {
    .section-title {
      font-size: 13px;
      font-weight: 600;
      color: var(--n-text-color);
      margin-bottom: 8px;
      padding-left: 2px;
    }
  }

  /* 时间线 */
  .timing-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 4px;
  }
  .timing-name {
    font-size: 13px;
    color: var(--n-text-color);
  }
  .timing-time {
    font-size: 12px;
    color: var(--n-text-color-3);
    font-variant-numeric: tabular-nums;
  }

  /* key-value 行 */
  .kv-row {
    display: flex;
    gap: 10px;
    margin-bottom: 8px;
    align-items: flex-start;
  }
  .kv-label {
    flex-shrink: 0;
    width: 70px;
    font-size: 12px;
    color: var(--n-text-color-3);
    padding-top: 2px;
  }
  .kv-val {
    flex: 1;
    font-size: 13px;
    color: var(--n-text-color);
    line-height: 1.6;
  }
  .kv-rewrite {
    color: var(--n-primary-color);
    font-weight: 500;
  }

  .chip-list {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  /* 意图分类候选 */
  .candidate-row {
    margin-bottom: 10px;
  }
  .candidate-head {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 4px;
  }
  .candidate-name {
    flex: 1;
    font-size: 13px;
    color: var(--n-text-color);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .candidate-score {
    font-size: 12px;
    color: var(--n-text-color-3);
    font-variant-numeric: tabular-nums;
  }
  .candidate-path {
    font-size: 11px;
    color: var(--n-text-color-3);
    margin-top: 2px;
  }

  /* 歧义引导/兜底话术 */
  .guidance-msg {
    margin-top: 4px;
    padding: 8px 10px;
    background: var(--n-action-color);
    border-left: 3px solid var(--n-warning-color);
    border-radius: 4px;
    font-size: 13px;
    color: var(--n-text-color);
    line-height: 1.6;
  }

  /* 召回片段 */
  .fragment-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
    margin-top: 6px;
  }
  .fragment-card {
    padding: 8px 10px;
    background: var(--n-action-color);
    border-radius: 6px;
  }
  .fragment-head {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 4px;
  }
  .fragment-idx {
    font-size: 11px;
    color: var(--n-text-color-3);
    font-weight: 600;
  }
  .fragment-text {
    font-size: 12px;
    line-height: 1.6;
    color: var(--n-text-color-2);
    :deep(p) {
      margin: 2px 0;
    }
  }

  /* 重排打分 */
  .scored-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
    margin-top: 6px;
  }
  .scored-row {
    padding: 8px 10px;
    background: var(--n-action-color);
    border-radius: 6px;
  }
  .scored-head {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 4px;
  }
  .scored-idx {
    font-size: 11px;
    color: var(--n-text-color-3);
    font-weight: 600;
  }
  .scored-score {
    flex: 1;
    font-size: 13px;
    font-weight: 600;
    color: var(--n-primary-color);
    font-variant-numeric: tabular-nums;
    &.dismissed {
      color: var(--n-text-color-3);
      text-decoration: line-through;
    }
  }
  .scored-text {
    margin-top: 4px;
    font-size: 12px;
    line-height: 1.6;
    color: var(--n-text-color-2);
    :deep(p) {
      margin: 2px 0;
    }
  }

  :deep(.skip-tip) {
    font-size: 12px;
    color: var(--n-text-color-3);
    font-style: italic;
    padding: 4px 0;
  }
</style>
