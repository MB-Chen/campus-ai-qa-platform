<template>
  <div class="dashboard-container">
    <!-- 顶部：标题 + 时间窗口切换 + 刷新 -->
    <n-card :bordered="false" class="dashboard-header">
      <div class="header-row">
        <div class="header-left">
          <span class="page-title">运营总览</span>
          <span class="update-time" v-if="overview?.updatedAt">
            最后更新：{{ formatTime(overview.updatedAt) }}
          </span>
        </div>
        <div class="header-right">
          <n-radio-group v-model:value="win" size="small" @update:value="loadAll">
            <n-radio-button value="24h">24小时</n-radio-button>
            <n-radio-button value="7d">7天</n-radio-button>
            <n-radio-button value="30d">30天</n-radio-button>
          </n-radio-group>
          <n-button size="small" @click="loadAll" :loading="loading">
            <template #icon><n-icon><ReloadOutlined /></n-icon></template>
            刷新
          </n-button>
        </div>
      </div>
    </n-card>

    <!-- 主区域：左主列 + 右侧栏 -->
    <div class="dashboard-body">
      <!-- 左主列 -->
      <div class="main-col">
        <!-- 核心 KPI 卡片 -->
        <n-grid :cols="4" :x-gap="12" class="mb-4">
          <n-gi v-for="card in kpiCards" :key="card.label">
            <n-card :bordered="false" size="small" class="kpi-card">
              <div class="kpi-label">{{ card.label }}</div>
              <div class="kpi-value">{{ card.value }}</div>
              <div class="kpi-delta" v-if="card.deltaPct != null">
                <n-icon :color="card.deltaPct >= 0 ? '#18a058' : '#d03050'">
                  <CaretUpOutlined v-if="card.deltaPct >= 0" />
                  <CaretDownOutlined v-else />
                </n-icon>
                <span :style="{ color: card.deltaPct >= 0 ? '#18a058' : '#d03050' }">
                  {{ Math.abs(card.deltaPct).toFixed(1) }}%
                </span>
                <span class="kpi-delta-text">较上周期</span>
              </div>
            </n-card>
          </n-gi>
        </n-grid>

        <!-- 流量概览面积图 -->
        <n-card title="流量概览（消息数趋势）" :bordered="false" class="mb-4">
          <div ref="trafficChartRef" style="height: 300px" />
        </n-card>

        <!-- 趋势分析 -->
        <n-grid :cols="2" :x-gap="12" :y-gap="12">
          <n-gi>
            <n-card title="会话趋势" :bordered="false">
              <div ref="sessionChartRef" style="height: 260px" />
            </n-card>
          </n-gi>
          <n-gi>
            <n-card title="活跃用户趋势" :bordered="false">
              <div ref="userChartRef" style="height: 260px" />
            </n-card>
          </n-gi>
          <n-gi>
            <n-card title="响应时间趋势" :bordered="false">
              <div ref="latencyChartRef" style="height: 260px" />
            </n-card>
          </n-gi>
          <n-gi>
            <n-card title="质量趋势（错误率 / 无知识率）" :bordered="false">
              <div ref="qualityChartRef" style="height: 260px" />
            </n-card>
          </n-gi>
        </n-grid>
      </div>

      <!-- 右侧栏 -->
      <div class="side-col">
        <!-- AI 性能卡片 -->
        <n-card :bordered="false" class="mb-4">
          <div class="ai-perf-header">
            <span class="card-title">AI 性能</span>
            <n-tag :type="healthTagType" size="small" round>{{ healthText }}</n-tag>
          </div>
          <!-- 成功率环形 -->
          <div class="success-ring">
            <n-progress
              type="circle"
              :percentage="performance.successRate || 0"
              :color="ringColor"
              :stroke-width="8"
            >
              <div class="ring-inner">
                <div class="ring-value">{{ (performance.successRate || 0).toFixed(1) }}%</div>
                <div class="ring-label">成功率</div>
              </div>
            </n-progress>
          </div>
          <!-- 性能指标 -->
          <div class="perf-row">
            <span class="perf-label">平均响应</span>
            <span class="perf-value">{{ formatMs(performance.avgLatencyMs) }}</span>
          </div>
          <div class="perf-row">
            <span class="perf-label">P95 响应</span>
            <span class="perf-value">{{ formatMs(performance.p95LatencyMs) }}</span>
          </div>
          <!-- 质量快照 -->
          <div class="quality-snapshot">
            <div class="quality-title">质量快照</div>
            <div class="quality-bars">
              <div class="qbar-item" v-for="q in qualityBars" :key="q.label">
                <div class="qbar-label">{{ q.label }}</div>
                <div class="qbar-track">
                  <div class="qbar-fill" :style="{ height: q.pct + '%', background: q.color }" />
                </div>
                <div class="qbar-value">{{ q.value.toFixed(1) }}%</div>
              </div>
            </div>
          </div>
          <!-- 运营效率 -->
          <div class="efficiency">
            <div class="quality-title">运营效率</div>
            <div class="perf-row" v-for="e in efficiency" :key="e.label">
              <span class="perf-label">{{ e.label }}</span>
              <span class="perf-value">{{ e.value }}</span>
            </div>
          </div>
        </n-card>

        <!-- 运营洞察 -->
        <n-card title="运营洞察" :bordered="false">
          <div class="insight-list">
            <div class="insight-item" v-for="(ins, i) in insights" :key="i">
              <n-tag :type="ins.tagType" size="tiny" round>{{ ins.tagText }}</n-tag>
              <div class="insight-title">{{ ins.title }}</div>
              <div class="insight-desc" v-if="ins.desc">{{ ins.desc }}</div>
            </div>
            <n-empty v-if="insights.length === 0" description="暂无洞察数据" size="small" />
          </div>
        </n-card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue';
  import * as echarts from 'echarts';
  import {
    ReloadOutlined,
    CaretUpOutlined,
    CaretDownOutlined,
  } from '@vicons/antd';
  import {
    getDashboardOverview,
    getDashboardPerformance,
    getDashboardTrends,
  } from '@/api/system/dashboard';

  const win = ref('7d');
  const loading = ref(false);
  const overview = ref<any>({});
  const performance = ref<any>({});

  const kpiCards = computed(() => {
    const k = overview.value?.kpis || {};
    const sessions = k.sessions?.value ?? 0;
    const messages = k.messages?.value ?? 0;
    const activeUsers = k.activeUsers?.value ?? 0;
    const depth = sessions > 0 ? (messages / sessions).toFixed(1) : '0';
    return [
      { label: '活跃用户', value: activeUsers, deltaPct: k.activeUsers?.deltaPct ?? null },
      { label: '会话数', value: sessions, deltaPct: k.sessions?.deltaPct ?? null },
      { label: '消息数', value: messages, deltaPct: k.messages?.deltaPct ?? null },
      { label: '会话深度（条/会话）', value: depth, deltaPct: null },
    ];
  });

  const trafficChartRef = ref<HTMLElement>();
  const sessionChartRef = ref<HTMLElement>();
  const userChartRef = ref<HTMLElement>();
  const latencyChartRef = ref<HTMLElement>();
  const qualityChartRef = ref<HTMLElement>();

  let trafficChart: echarts.ECharts | null = null;
  let sessionChart: echarts.ECharts | null = null;
  let userChart: echarts.ECharts | null = null;
  let latencyChart: echarts.ECharts | null = null;
  let qualityChart: echarts.ECharts | null = null;

  const ringColor = computed(() => {
    const r = performance.value?.successRate ?? 0;
    if (r >= 95) return '#18a058';
    if (r >= 85) return '#f0a020';
    return '#d03050';
  });

  const healthTagType = computed<'success' | 'warning' | 'error' | 'default'>(() => {
    const p = performance.value || {};
    if (p.errorRate > 5 || (p.successRate < 95 && (p.successRate ?? 0) > 0)) return 'error';
    if (p.noDocRate > 20) return 'warning';
    if ((p.successRate ?? 0) > 0) return 'success';
    return 'default';
  });

  const healthText = computed(() => {
    const t = healthTagType.value;
    return t === 'success' ? '运行正常' : t === 'warning' ? '需要关注' : t === 'error' ? '风险偏高' : '暂无数据';
  });

  const qualityBars = computed(() => {
    const p = performance.value || {};
    return [
      { label: '错误率', value: p.errorRate ?? 0, pct: Math.min(p.errorRate ?? 0, 100), color: '#d03050' },
      { label: '无知识率', value: p.noDocRate ?? 0, pct: Math.min(p.noDocRate ?? 0, 100), color: '#f0a020' },
      { label: '慢响应率', value: p.slowRate ?? 0, pct: Math.min(p.slowRate ?? 0, 100), color: '#2080f0' },
    ];
  });

  const efficiency = computed(() => {
    const k = overview.value?.kpis || {};
    const s = k.sessions?.value ?? 0;
    const m = k.messages?.value ?? 0;
    const u = k.activeUsers?.value ?? 0;
    return [
      { label: '人均会话', value: u > 0 ? (s / u).toFixed(1) : '0' },
      { label: '单会话消息', value: s > 0 ? (m / s).toFixed(1) : '0' },
      { label: '人均消息', value: u > 0 ? (m / u).toFixed(1) : '0' },
    ];
  });

  const insights = computed(() => {
    const p = performance.value || {};
    const list: { tagType: 'error' | 'warning' | 'info' | 'success'; tagText: string; title: string; desc?: string }[] = [];
    if (p.errorRate > 5 || (p.successRate < 95 && (p.successRate ?? 0) > 0)) {
      list.push({
        tagType: 'error', tagText: '异常',
        title: '链路稳定性触发告警',
        desc: `成功率 ${(p.successRate ?? 0).toFixed(1)}%，错误率 ${(p.errorRate ?? 0).toFixed(1)}%，建议排查异常链路。`,
      });
    }
    if ((p.noDocRate ?? 0) > 20) {
      list.push({
        tagType: 'warning', tagText: '建议',
        title: '召回质量需优化',
        desc: `无知识率 ${(p.noDocRate ?? 0).toFixed(1)}%，建议补充知识库内容或调整检索配置。`,
      });
    }
    if ((p.avgLatencyMs ?? 0) > 15000) {
      list.push({
        tagType: 'warning', tagText: '建议',
        title: '响应性能需要关注',
        desc: `平均响应 ${formatMs(p.avgLatencyMs)}，超过 15s 阈值。`,
      });
    }
    if (list.length === 0 && (p.successRate ?? 0) > 0) {
      list.push({ tagType: 'success', tagText: '趋势', title: '系统可用性稳定', desc: `成功率保持 ${(p.successRate ?? 0).toFixed(1)}%。` });
    }
    return list.slice(0, 3);
  });

  function formatMs(ms?: number) {
    if (ms == null || ms === 0) return '-';
    if (ms < 1000) return `${Math.round(ms)}ms`;
    if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
    return `${(ms / 60000).toFixed(1)}min`;
  }

  function formatTime(ts?: number) {
    if (!ts) return '';
    return new Date(ts).toLocaleString('zh-CN', { hour12: false });
  }

  function initChart(el: HTMLElement | undefined): echarts.ECharts | null {
    if (!el) return null;
    return echarts.init(el);
  }

  function ensureCharts() {
    trafficChart = trafficChart || initChart(trafficChartRef.value);
    sessionChart = sessionChart || initChart(sessionChartRef.value);
    userChart = userChart || initChart(userChartRef.value);
    latencyChart = latencyChart || initChart(latencyChartRef.value);
    qualityChart = qualityChart || initChart(qualityChartRef.value);
  }

  function buildLineOption(
    seriesData: { ts: number; value: number }[],
    title: string,
    yType: 'number' | 'percent' | 'duration' = 'number'
  ): echarts.EChartsOption {
    const xData = seriesData.map((d) => new Date(d.ts).toLocaleString('zh-CN', {
      month: '2-digit', day: '2-digit', hour: win.value === '24h' ? '2-digit' : undefined, hour12: false,
    }));
    const values = seriesData.map((d) => d.value);
    const fmt = (v: number) => {
      if (yType === 'percent') return `${v.toFixed(1)}%`;
      if (yType === 'duration') return formatMs(v);
      return String(Math.round(v));
    };
    return {
      tooltip: { trigger: 'axis', formatter: (p: any) => `${p[0].axisValue}<br/>${title}: <b>${fmt(p[0].value)}</b>` },
      grid: { left: 50, right: 20, top: 20, bottom: 30 },
      xAxis: { type: 'category', data: xData, axisLabel: { fontSize: 10 } },
      yAxis: {
        type: 'value',
        axisLabel: { formatter: (v: number) => fmt(v), fontSize: 10 },
      },
      series: [{
        type: 'line',
        data: values,
        smooth: true,
        areaStyle: { opacity: 0.15 },
        lineStyle: { width: 2 },
        itemStyle: { color: '#07c05f' },
      }],
    };
  }

  function buildQualityOption(series: { name: string; data: { ts: number; value: number }[] }[]): echarts.EChartsOption {
    const xData = (series[0]?.data || []).map((d) => new Date(d.ts).toLocaleString('zh-CN', {
      month: '2-digit', day: '2-digit', hour: win.value === '24h' ? '2-digit' : undefined, hour12: false,
    }));
    return {
      tooltip: { trigger: 'axis', formatter: (p: any) => p.map((i: any) => `${i.seriesName}: <b>${i.value.toFixed(1)}%</b>`).join('<br/>') },
      legend: { data: series.map((s) => s.name), bottom: 0, textStyle: { fontSize: 11 } },
      grid: { left: 40, right: 20, top: 20, bottom: 40 },
      xAxis: { type: 'category', data: xData, axisLabel: { fontSize: 10 } },
      yAxis: { type: 'value', axisLabel: { formatter: '{value}%', fontSize: 10 } },
      series: series.map((s) => ({
        name: s.name,
        type: 'line',
        data: s.data.map((d) => d.value),
        smooth: true,
        lineStyle: { width: 2 },
      })),
    };
  }

  async function loadAll() {
    loading.value = true;
    try {
      const [ovRes, perfRes] = await Promise.all([
        getDashboardOverview(win.value),
        getDashboardPerformance(win.value),
      ]);
      if (ovRes?.code === 0) overview.value = ovRes.data || {};
      if (perfRes?.code === 0) performance.value = perfRes.data || {};

      await nextTick();
      ensureCharts();

      // 趋势数据
      const [msgT, sessT, userT, latT, qualT] = await Promise.all([
        getDashboardTrends('messages', win.value),
        getDashboardTrends('sessions', win.value),
        getDashboardTrends('activeUsers', win.value),
        getDashboardTrends('avgLatency', win.value),
        getDashboardTrends('quality', win.value),
      ]);

      const toData = (res: any): { ts: number; value: number }[] => {
        const arr = res?.code === 0 ? (res.data?.series?.[0]?.data || []) : [];
        return Array.isArray(arr) ? arr.filter((x: any) => x && x.ts != null) : [];
      };

      trafficChart?.setOption(buildLineOption(toData(msgT), '消息数'), true);
      sessionChart?.setOption(buildLineOption(toData(sessT), '会话数'), true);
      userChart?.setOption(buildLineOption(toData(userT), '活跃用户'), true);
      latencyChart?.setOption(buildLineOption(toData(latT), '响应时间', 'duration'), true);

      const qSeries = (qualT?.code === 0 ? qualT.data?.series : []) || [];
      const mapped = (Array.isArray(qSeries) ? qSeries : []).map((s: any) => ({
        name: s.name || '',
        data: (s.data || []).filter((x: any) => x && x.ts != null),
      }));
      qualityChart?.setOption(buildQualityOption(mapped), true);
    } catch (e) {
      console.error('dashboard load failed', e);
    } finally {
      loading.value = false;
    }
  }

  function handleResize() {
    trafficChart?.resize();
    sessionChart?.resize();
    userChart?.resize();
    latencyChart?.resize();
    qualityChart?.resize();
  }

  onMounted(() => {
    loadAll();
    window.addEventListener('resize', handleResize);
  });

  onBeforeUnmount(() => {
    window.removeEventListener('resize', handleResize);
    trafficChart?.dispose();
    sessionChart?.dispose();
    userChart?.dispose();
    latencyChart?.dispose();
    qualityChart?.dispose();
  });
</script>

<style lang="less" scoped>
  .dashboard-container {
    padding: 0;
  }
  .dashboard-header {
    margin-bottom: 12px;
    :deep(.n-card__content) { padding: 14px 20px; }
  }
  .header-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  .header-left {
    display: flex;
    align-items: baseline;
    gap: 16px;
  }
  .page-title {
    font-size: 18px;
    font-weight: 600;
  }
  .update-time {
    font-size: 12px;
    color: #999;
  }
  .header-right {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .dashboard-body {
    display: grid;
    grid-template-columns: 1fr 340px;
    gap: 12px;
    align-items: start;
    @media (max-width: 1200px) {
      grid-template-columns: 1fr;
    }
  }
  .kpi-card {
    text-align: center;
    :deep(.n-card__content) { padding: 16px; }
  }
  .kpi-label {
    font-size: 13px;
    color: #999;
    margin-bottom: 6px;
  }
  .kpi-value {
    font-size: 26px;
    font-weight: 700;
    color: #333;
  }
  .kpi-delta {
    margin-top: 6px;
    font-size: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 2px;
  }
  .kpi-delta-text {
    color: #aaa;
    margin-left: 4px;
  }
  .side-col {
    position: sticky;
    top: 12px;
  }
  .ai-perf-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;
  }
  .card-title {
    font-weight: 600;
    font-size: 15px;
  }
  .success-ring {
    display: flex;
    justify-content: center;
    margin-bottom: 16px;
  }
  .ring-inner {
    text-align: center;
  }
  .ring-value {
    font-size: 22px;
    font-weight: 700;
    color: #07c05f;
  }
  .ring-label {
    font-size: 12px;
    color: #999;
  }
  .perf-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 6px 0;
    border-bottom: 1px solid #f5f5f5;
    font-size: 13px;
    &:last-child { border-bottom: none; }
  }
  .perf-label {
    color: #999;
  }
  .perf-value {
    font-weight: 600;
    color: #333;
  }
  .quality-snapshot, .efficiency {
    margin-top: 14px;
  }
  .quality-title {
    font-size: 13px;
    font-weight: 600;
    color: #666;
    margin-bottom: 8px;
  }
  .quality-bars {
    display: flex;
    justify-content: space-around;
    gap: 12px;
  }
  .qbar-item {
    text-align: center;
    flex: 1;
  }
  .qbar-label {
    font-size: 11px;
    color: #999;
    margin-bottom: 4px;
  }
  .qbar-track {
    height: 60px;
    width: 18px;
    background: #f0f0f0;
    border-radius: 4px;
    display: flex;
    align-items: flex-end;
    margin: 0 auto 4px;
    overflow: hidden;
  }
  .qbar-fill {
    width: 100%;
    border-radius: 4px;
    min-height: 2px;
    transition: height 0.3s;
  }
  .qbar-value {
    font-size: 11px;
    font-weight: 600;
    color: #333;
  }
  .insight-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }
  .insight-item {
    padding: 8px 10px;
    background: #fafafa;
    border-radius: 6px;
  }
  .insight-title {
    font-size: 13px;
    font-weight: 600;
    margin-top: 4px;
  }
  .insight-desc {
    font-size: 12px;
    color: #888;
    margin-top: 2px;
  }
</style>
