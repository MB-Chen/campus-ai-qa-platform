<template>
  <n-modal
    v-model:show="show"
    preset="card"
    title="入库耗时统计"
    style="width: 720px"
    :bordered="false"
    @after-leave="onClosed"
  >
    <n-spin :show="loading">
      <!-- 空态：老文档 / 未走新流程 -->
      <n-empty
        v-if="!loading && !summary"
        description="暂无耗时数据（该文档可能由旧版本上传，或尚未完成入库）"
      />

      <template v-else-if="summary">
        <!-- 头部摘要 -->
        <div class="summary-head">
          <n-tag size="small" round type="info">
            引擎：{{ engineLabel(summary.engine) }}
          </n-tag>
          <n-tag size="small" round type="success">
            总耗时 {{ formatDuration(summary.totalMs) }}
          </n-tag>
        </div>

        <!-- 柱状图：每次打开都用新 DOM（key 随 renderSeq 变化），避免 ECharts 实例残留 -->
        <div
          v-if="hasChartData"
          :key="'chart-' + renderSeq"
          ref="chartRef"
          class="chart-box"
        ></div>

        <!-- 明细表 -->
        <n-data-table
          :columns="columns"
          :data="tableData"
          :bordered="false"
          size="small"
          :pagination="false"
        />
      </template>
    </n-spin>

    <template #footer>
      <n-space justify="end">
        <n-button @click="show = false">关闭</n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
  import { ref, computed, nextTick, h } from 'vue';
  import { useMessage, NTag } from 'naive-ui';
  import * as echarts from 'echarts';
  import {
    getIngestionSummary,
    type IngestionSummary,
    type StageStat,
  } from '@/api/system/knowledge';

  const message = useMessage();

  const show = ref(false);
  const loading = ref(false);
  const summary = ref<IngestionSummary | null>(null);
  const chartRef = ref<HTMLElement>();
  // 每次 open 自增，作为 chart 容器的 :key，强制 Vue 重建 DOM 节点，
  // 彻底规避 ECharts 复用已销毁/已分离的旧实例导致「再开图没了」。
  const renderSeq = ref(0);
  let chart: echarts.ECharts | null = null;

  // 引擎名 → 中文
  function engineLabel(engine?: string): string {
    const map: Record<string, string> = {
      tika: 'Tika',
      pdfbox: 'PdfBox',
      poi: 'POI',
      mineru: 'MinerU 自建',
      mineru_cloud: 'MinerU 云端',
    };
    if (!engine) return '未知';
    return map[engine] || engine;
  }

  // ms → 人类可读
  function formatDuration(ms?: number): string {
    if (ms == null || isNaN(ms) || ms < 0) return '—';
    if (ms < 1000) return Math.round(ms) + 'ms';
    if (ms < 60000) return (ms / 1000).toFixed(2) + 's';
    const mins = Math.floor(ms / 60000);
    const rem = ((ms % 60000) / 1000).toFixed(1);
    return `${mins}m${rem}s`;
  }

  // 是否有可绘制的图表数据（至少一个阶段 durationMs>0）
  const hasChartData = computed(() => {
    if (!summary.value?.stages || summary.value.stages.length === 0) return false;
    return summary.value.stages.some((s) => s.durationMs > 0);
  });

  // 明细表数据
  const tableData = computed(() => {
    if (!summary.value?.stages) return [];
    return summary.value.stages.map((s: StageStat) => ({
      label: s.label || s.key,
      duration: formatDuration(s.durationMs),
      status: s.status,
      detail: s.detail || '',
    }));
  });

  const columns = [
    { title: '阶段', key: 'label', width: 100 },
    { title: '耗时', key: 'duration', width: 100 },
    {
      title: '状态',
      key: 'status',
      width: 90,
      render: (row: any) => statusTag(row.status),
    },
    { title: '说明', key: 'detail', ellipsis: { tooltip: true } },
  ];

  function statusTag(status: string) {
    const map: Record<string, { label: string; type: any }> = {
      success: { label: '成功', type: 'success' },
      failed: { label: '失败', type: 'error' },
      skipped: { label: '跳过', type: 'default' },
      running: { label: '进行中', type: 'info' },
    };
    const cfg = map[status] || { label: status || '-', type: 'default' };
    return h(
      NTag,
      { size: 'small', type: cfg.type, round: true },
      { default: () => cfg.label }
    );
  }

  // 销毁 ECharts 实例（弹窗关闭后调，释放内存 + 避免下次 open 复用旧实例）
  function disposeChart() {
    if (chart) {
      try {
        chart.dispose();
      } catch {
        // 忽略已销毁的情况
      }
      chart = null;
    }
  }

  // 弹窗完全关闭后的清理（@after-leave 在过渡结束后触发，此时 DOM 已隐藏）
  function onClosed() {
    disposeChart();
    // 不清 summary，避免关闭瞬间的空态闪烁；下次 open 会重置
  }

  // 渲染柱状图（每次 open 调一次；chartRef 因 :key 变化已重新挂载新 DOM）
  function renderChart() {
    if (!chartRef.value || !summary.value?.stages) return;
    // 容器是新建的 DOM，旧实例必然为 null；保险起见先 dispose
    disposeChart();
    chart = echarts.init(chartRef.value);
    const stages = summary.value.stages;
    const labels = stages.map((s) => s.label || s.key);
    const values = stages.map((s) => Number((s.durationMs / 1000).toFixed(2)));
    // 失败阶段标红，跳过标灰，其余主题绿
    const colors = stages.map((s) =>
      s.status === 'failed' ? '#d03050' : s.status === 'skipped' ? '#bbb' : '#07c05f'
    );
    chart.setOption({
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: (params: any) => {
          const p = Array.isArray(params) ? params[0] : params;
          const idx = p.dataIndex;
          const s = stages[idx];
          return `${s.label || s.key}<br/>耗时：${formatDuration(s.durationMs)}<br/>状态：${s.status}`;
        },
      },
      // 横向柱状图：yAxis=类别（阶段名），xAxis=数值（秒）
      grid: { left: 70, right: 50, top: 16, bottom: 24 },
      yAxis: {
        type: 'category',
        data: labels,
        axisLabel: { fontSize: 12 },
        inverse: true,
      },
      xAxis: {
        type: 'value',
        name: '秒',
        axisLabel: { fontSize: 11 },
      },
      series: [
        {
          type: 'bar',
          data: values.map((v, i) => ({ value: v, itemStyle: { color: colors[i] } })),
          barWidth: '45%',
          label: {
            show: true,
            position: 'right',
            formatter: (p: any) => formatDuration(stages[p.dataIndex].durationMs),
            fontSize: 11,
          },
        },
      ],
    });
    chart.resize();
  }

  // 打开弹窗：传 documentId 拉数据（文档详情页用）
  async function open(documentId: string) {
    if (!documentId) {
      message.warning('缺少文档 id');
      return;
    }
    // 重置状态 + 自增 renderSeq 触发 chart 容器 DOM 重建
    summary.value = null;
    renderSeq.value++;
    show.value = true;
    loading.value = true;
    try {
      const res: any = await getIngestionSummary(documentId);
      if (res && res.code === 0 && res.data) {
        summary.value = res.data as IngestionSummary;
        // 等 DOM 渲染完（含 :key 变化重建的 chartRef）再画图
        await nextTick();
        renderChart();
      } else {
        summary.value = null;
      }
    } catch (e) {
      message.error('加载耗时数据失败');
      summary.value = null;
    } finally {
      loading.value = false;
    }
  }

  // 直接传 summary 弹（预览阶段用，不必再调接口）
  function openWithSummary(s: IngestionSummary | null) {
    summary.value = s;
    renderSeq.value++;
    show.value = true;
    loading.value = false;
    nextTick(() => renderChart());
  }

  defineExpose({ open, openWithSummary });
</script>

<style lang="less" scoped>
  .summary-head {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 12px;
  }
  .chart-box {
    width: 100%;
    height: 240px;
    margin-bottom: 16px;
  }
</style>
