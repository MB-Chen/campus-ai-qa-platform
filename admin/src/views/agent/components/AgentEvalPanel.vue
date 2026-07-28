<template>
  <n-drawer v-model:show="show" :width="1180" :mask-closable="false" @after-leave="onClose">
    <n-drawer-content :title="`评估报告 · ${agent?.name || ''}`" closable>
      <div class="eval-wrap">
        <!-- 测试集编辑区 -->
        <n-card size="small" title="测试问题集" :bordered="false" class="mb-3">
          <template #header-extra>
            <n-space size="small">
              <n-button
                size="small"
                quaternary
                type="primary"
                :loading="seeding"
                @click="onGenSeed"
              >
                AI 生成测试集
              </n-button>
              <n-button
                size="small"
                type="primary"
                secondary
                strong
                :loading="running"
                :disabled="cases.length === 0"
                @click="onRunEval"
              >
                开始评估
              </n-button>
            </n-space>
          </template>
          <n-data-table
            :columns="caseColumns"
            :data="cases"
            :pagination="false"
            size="small"
            :max-height="200"
          />
        </n-card>

        <!-- 报告区 -->
        <template v-if="report">
          <!-- 总评条 -->
          <n-alert :type="gradeAlertType(report.grade)" :show-icon="true" class="mb-3 verdict-bar">
            <div class="verdict-head">
              <span class="verdict-badge" :class="'badge-' + report.grade">{{
                report.gradeLabel || '-'
              }}</span>
              <span class="verdict-summary">{{ report.summary }}</span>
            </div>
            <ul v-if="report.tips && report.tips.length" class="verdict-tips">
              <li v-for="(t, i) in report.tips" :key="i">{{ t }}</li>
            </ul>
          </n-alert>

          <!-- 指标卡片 -->
          <n-grid :cols="24" :x-gap="12" :y-gap="12" class="mb-3">
            <n-gi :span="6">
              <n-card size="small" class="metric-card">
                <div class="metric-label">平均综合分</div>
                <div class="metric-value" :class="scoreClass(report.avgScore)">
                  {{ report.avgScore.toFixed(1) }}<span class="metric-unit">/10</span>
                </div>
              </n-card>
            </n-gi>
            <n-gi :span="6">
              <n-card size="small" class="metric-card">
                <div class="metric-label">用例数</div>
                <div class="metric-value">{{ report.total }}</div>
              </n-card>
            </n-gi>
            <n-gi :span="6">
              <n-card size="small" class="metric-card">
                <div class="metric-label">异常率</div>
                <div class="metric-value" :class="report.errorRate > 0.1 ? 'rate-low' : ''">
                  {{ (report.errorRate * 100).toFixed(0) }}%
                </div>
              </n-card>
            </n-gi>
            <n-gi :span="6">
              <n-card size="small" class="metric-card">
                <div class="metric-label">耗时</div>
                <div class="metric-value-sm">{{ (report.costMs / 1000).toFixed(1) }}s</div>
              </n-card>
            </n-gi>
          </n-grid>

          <n-grid :cols="24" :x-gap="12" class="mb-3">
            <!-- 能力雷达图 -->
            <n-gi :span="12">
              <n-card size="small" title="能力雷达（5 维评分）" :bordered="false">
                <div ref="radarChartRef" style="width: 100%; height: 320px"></div>
              </n-card>
            </n-gi>
            <!-- 分数分布 -->
            <n-gi :span="12">
              <n-card size="small" title="综合分分布" :bordered="false">
                <div ref="barChartRef" style="width: 100%; height: 320px"></div>
              </n-card>
            </n-gi>
          </n-grid>

          <!-- 案例明细 -->
          <n-card size="small" :bordered="false" title="评估明细">
            <n-data-table
              :columns="detailColumns"
              :data="report.details"
              :pagination="{ pageSize: 10 }"
              size="small"
              :max-height="360"
            />
          </n-card>
        </template>
        <n-empty
          v-else
          description="配置测试问题后点击「开始评估」，将用 LLM 对回答多维打分"
          style="padding: 40px 0"
        />
      </div>
    </n-drawer-content>
  </n-drawer>
</template>

<script setup lang="ts">
  import { ref, h, nextTick } from 'vue';
  import { NTag, NInput, useMessage } from 'naive-ui';
  import type { DataTableColumns } from 'naive-ui';
  import echarts from '@/utils/lib/echarts';
  import {
    evalAgent,
    evalAgentSeed,
    type Agent,
    type AgentEvalCase,
    type AgentEvalReport,
  } from '@/api/system/agent';

  const message = useMessage();

  const show = ref(false);
  const agent = ref<Agent | null>(null);
  const cases = ref<AgentEvalCase[]>([]);
  const report = ref<AgentEvalReport | null>(null);
  const running = ref(false);
  const seeding = ref(false);

  const radarChartRef = ref<HTMLElement | null>(null);
  const barChartRef = ref<HTMLElement | null>(null);
  let radarChart: echarts.ECharts | null = null;
  let barChart: echarts.ECharts | null = null;

  // localStorage 持久化测试集
  const LS_KEY_PREFIX = 'agent_eval_cases_';

  const caseColumns: DataTableColumns<AgentEvalCase> = [
    {
      title: '问题',
      key: 'query',
      render: (row, idx) =>
        h(NInput, {
          value: row.query,
          placeholder: '输入测试问题',
          type: 'textarea',
          autosize: { minRows: 1, maxRows: 3 },
          'onUpdate:value': (v: string) => {
            cases.value[idx].query = v;
            persist();
          },
        }),
    },
    {
      title: '期望答案（可选）',
      key: 'expectedAnswer',
      width: 240,
      render: (row, idx) =>
        h(NInput, {
          value: row.expectedAnswer,
          placeholder: '参考答案',
          type: 'textarea',
          autosize: { minRows: 1, maxRows: 3 },
          'onUpdate:value': (v: string) => {
            cases.value[idx].expectedAnswer = v;
            persist();
          },
        }),
    },
    {
      title: '操作',
      key: 'op',
      width: 70,
      render: (_row, idx) =>
        h(
          'a',
          {
            style: 'color: #d03050; cursor: pointer',
            onClick: () => {
              cases.value.splice(idx, 1);
              persist();
            },
          },
          '删除'
        ),
    },
  ];

  const detailColumns: DataTableColumns<any> = [
    { title: '问题', key: 'query', ellipsis: { tooltip: true }, width: 180 },
    { title: '回答', key: 'answer', ellipsis: { tooltip: true } },
    {
      title: '综合分',
      key: 'overall',
      width: 90,
      render: (row) =>
        h(
          NTag,
          {
            size: 'small',
            type: row.error
              ? 'error'
              : row.overall >= 7
              ? 'success'
              : row.overall >= 5
              ? 'warning'
              : 'error',
            round: true,
          },
          { default: () => (row.error ? '异常' : row.overall.toFixed(1)) }
        ),
    },
    {
      title: '相关',
      key: 'relevance',
      width: 70,
      render: (r) => (r.error ? '-' : r.relevance.toFixed(1)),
    },
    {
      title: '准确',
      key: 'accuracy',
      width: 70,
      render: (r) => (r.error ? '-' : r.accuracy.toFixed(1)),
    },
    {
      title: '完整',
      key: 'completeness',
      width: 70,
      render: (r) => (r.error ? '-' : r.completeness.toFixed(1)),
    },
    {
      title: '依据',
      key: 'groundedness',
      width: 70,
      render: (r) => (r.error ? '-' : r.groundedness.toFixed(1)),
    },
    {
      title: '简洁',
      key: 'conciseness',
      width: 70,
      render: (r) => (r.error ? '-' : r.conciseness.toFixed(1)),
    },
    { title: '评语', key: 'comment', ellipsis: { tooltip: true }, width: 160 },
  ];

  function open(ag: Agent) {
    agent.value = ag;
    report.value = null;
    cases.value = loadCases(ag.id!);
    if (cases.value.length === 0) {
      cases.value = [{ query: '', expectedAnswer: '', note: '' }];
    }
    show.value = true;
  }

  function onClose() {
    disposeCharts();
    report.value = null;
  }

  function persist() {
    if (agent.value?.id) {
      localStorage.setItem(LS_KEY_PREFIX + agent.value.id, JSON.stringify(cases.value));
    }
  }

  function loadCases(agentId: string): AgentEvalCase[] {
    try {
      const raw = localStorage.getItem(LS_KEY_PREFIX + agentId);
      if (raw) {
        const arr = JSON.parse(raw);
        if (Array.isArray(arr)) return arr;
      }
    } catch {
      // ignore
    }
    return [];
  }

  async function onGenSeed() {
    if (!agent.value) return;
    seeding.value = true;
    try {
      const res: any = await evalAgentSeed(agent.value.id!, 3);
      if (res && res.code === 0 && Array.isArray(res.data)) {
        const newCases = res.data
          .filter((q: any) => typeof q === 'string' && q.trim())
          .map((q: string) => ({ query: q.trim(), expectedAnswer: '', note: '' }));
        // 先清掉 open() 插入的空占位行，避免合并后多出空行
        cases.value = cases.value.filter((c) => c.query && c.query.trim());
        // 合并到现有（去重）
        const exist = new Set(cases.value.map((c) => c.query));
        for (const c of newCases) {
          if (!exist.has(c.query)) cases.value.push(c);
        }
        persist();
        message.success(`已生成 ${newCases.length} 个测试问题`);
      } else {
        message.error(res?.message || '生成失败');
      }
    } catch {
      message.error('生成失败');
    } finally {
      seeding.value = false;
    }
  }

  async function onRunEval() {
    if (!agent.value) return;
    const validCases = cases.value.filter((c) => c.query && c.query.trim());
    if (validCases.length === 0) {
      message.warning('请至少填写一个测试问题');
      return;
    }
    running.value = true;
    report.value = null;
    try {
      const res: any = await evalAgent(agent.value.id!, validCases);
      if (res && res.code === 0 && res.data) {
        report.value = res.data;
        await nextTick();
        renderCharts();
      } else {
        message.error(res?.message || '评估失败');
      }
    } catch {
      message.error('评估失败');
    } finally {
      running.value = false;
    }
  }

  function renderCharts() {
    if (!report.value) return;
    renderRadar();
    renderBar();
  }

  function renderRadar() {
    if (!radarChartRef.value || !report.value) return;
    if (!radarChart) {
      radarChart = echarts.init(radarChartRef.value);
    }
    const dims = report.value.dimensions || [];
    const indicator = dims.map((d) => ({ name: d.label, max: 10 }));
    const values = dims.map((d) => Number(d.score.toFixed(2)));
    radarChart.setOption({
      tooltip: {},
      radar: {
        indicator,
        radius: '65%',
        splitNumber: 5,
        axisName: { color: '#666', fontSize: 12 },
      },
      series: [
        {
          type: 'radar',
          data: [
            {
              value: values,
              name: '评分',
              areaStyle: { color: 'rgba(7, 192, 95, 0.2)' },
              lineStyle: { color: '#07c05f' },
              itemStyle: { color: '#07c05f' },
            },
          ],
        },
      ],
    });
  }

  function renderBar() {
    if (!barChartRef.value || !report.value) return;
    if (!barChart) {
      barChart = echarts.init(barChartRef.value);
    }
    const bins = report.value.scoreBins || [];
    barChart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 40, right: 20, top: 30, bottom: 30 },
      xAxis: { type: 'category', data: bins.map((b) => b.bin), name: '综合分' },
      yAxis: { type: 'value', name: '用例数' },
      series: [
        {
          type: 'bar',
          data: bins.map((b) => ({
            value: b.count,
            itemStyle: {
              color: b.bin.includes('8')
                ? '#18a058'
                : b.bin.includes('6')
                ? '#07c05f'
                : b.bin.includes('4')
                ? '#f0a020'
                : '#d03050',
            },
          })),
          barWidth: '50%',
          label: { show: true, position: 'top' },
        },
      ],
    });
  }

  function disposeCharts() {
    radarChart?.dispose();
    barChart?.dispose();
    radarChart = null;
    barChart = null;
  }

  function scoreClass(v: number): string {
    if (v >= 7) return 'rate-high';
    if (v >= 5) return 'rate-mid';
    return 'rate-low';
  }

  function gradeAlertType(g?: string): 'success' | 'warning' | 'error' | 'info' {
    switch (g) {
      case 'excellent':
        return 'success';
      case 'good':
        return 'info';
      case 'fair':
        return 'warning';
      default:
        return 'error';
    }
  }

  defineExpose({ open });
</script>

<style lang="less" scoped>
  :deep(.n-drawer-content) {
    display: flex;
    flex-direction: column;
  }
  :deep(.n-drawer-body-content-wrapper) {
    flex: 1;
    display: flex;
    flex-direction: column;
    padding-bottom: 0;
  }
  .eval-wrap {
    flex: 1;
    overflow-y: auto;
  }
  .verdict-head {
    display: flex;
    align-items: center;
    gap: 10px;
  }
  .verdict-badge {
    font-size: 13px;
    font-weight: 600;
    padding: 2px 10px;
    border-radius: 10px;
    color: #fff;
    &.badge-excellent {
      background: #18a058;
    }
    &.badge-good {
      background: #07c05f;
    }
    &.badge-fair {
      background: #f0a020;
    }
    &.badge-poor {
      background: #d03050;
    }
  }
  .verdict-summary {
    font-size: 14px;
  }
  .verdict-tips {
    margin: 8px 0 0;
    padding-left: 18px;
    font-size: 13px;
    color: #666;
    li {
      margin-bottom: 3px;
    }
  }
  .metric-card {
    text-align: center;
  }
  .metric-label {
    font-size: 12px;
    color: #999;
    margin-bottom: 4px;
  }
  .metric-value {
    font-size: 26px;
    font-weight: 600;
    color: #333;
    &.rate-high {
      color: #18a058;
    }
    &.rate-mid {
      color: #f0a020;
    }
    &.rate-low {
      color: #d03050;
    }
  }
  .metric-unit {
    font-size: 13px;
    color: #999;
    font-weight: normal;
  }
  .metric-value-sm {
    font-size: 18px;
    color: #333;
  }
</style>
