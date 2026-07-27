<template>
  <div class="eval-panel">
    <!-- 单条实时调试 -->
    <n-card size="small" :bordered="false" class="mb-3" title="单条实时调试">
      <n-input-group>
        <n-input
          v-model:value="singleQuery"
          placeholder="输入一句话，立即看意图分类结果（对齐线上 LLM 链路）"
          clearable
          style="max-width: 520px"
          @keyup.enter="runSingle"
        />
        <n-button type="primary" secondary :loading="singleLoading" @click="runSingle"
          >分类</n-button
        >
      </n-input-group>
      <div v-if="singleResult.length" class="single-result">
        <n-tag
          v-for="(c, i) in singleResult"
          :key="c.id + i"
          :type="i === 0 ? 'success' : 'default'"
          size="small"
          round
        >
          {{ i + 1 }}. {{ c.name }}（{{ c.kind }}） {{ (c.score * 100).toFixed(0) }}%
          <span v-if="c.score >= 1" style="margin-left: 2px; opacity: 0.7">·规则</span>
        </n-tag>
      </div>
      <n-text v-else-if="singleDone" depth="3" style="font-size: 12px">
        无候选（空预测，可能各节点置信度均低于 {{ minScore }}）
      </n-text>
    </n-card>

    <!-- 工具条 -->
    <div class="eval-toolbar">
      <n-space align="center" wrap>
        <n-button
          type="primary"
          secondary
          :loading="evaluating"
          :disabled="!cases.length"
          @click="runEval"
        >
          <template #icon
            ><n-icon><ThunderboltOutlined /></n-icon
          ></template>
          一键评估
        </n-button>
        <n-button
          type="primary"
          secondary
          :loading="seedGenLoading"
          :disabled="!flatNodes.length"
          @click="genSeedByAi"
        >
          <template #icon>
            <n-icon><RobotOutlined /></n-icon>
          </template>
          AI 生成测试集
        </n-button>
        <n-button secondary @click="addRow"
          ><template #icon
            ><n-icon><PlusOutlined /></n-icon></template
          >添加用例</n-button
        >
        <n-button secondary @click="clearCases" :disabled="!cases.length">清空</n-button>
        <n-divider vertical />
        <span class="opt-label">TopK</span>
        <n-input-number v-model:value="topN" :min="1" :max="10" size="small" style="width: 80px" />
        <span class="opt-label">minScore</span>
        <n-input-number
          v-model:value="minScore"
          :min="0"
          :max="1"
          :step="0.05"
          size="small"
          style="width: 100px"
        />
        <n-tag size="small" :bordered="false">共 {{ cases.length }} 条</n-tag>
      </n-space>
    </div>

    <!-- 测试集表格 -->
    <n-data-table
      :columns="tableColumns"
      :data="cases"
      :pagination="{ pageSize: 20 }"
      :row-key="(_r: any, i: number) => i"
      size="small"
      :bordered="false"
      :max-height="320"
      class="mb-3"
    />

    <!-- 报告 -->
    <template v-if="report">
      <!-- 总评条：把指标翻译成人话，一眼判断效果好坏 -->
      <n-alert
        :type="gradeAlertType(report.grade)"
        :show-icon="true"
        class="mb-3 verdict-bar"
      >
        <div class="verdict-head">
          <span class="verdict-badge" :class="'badge-' + report.grade">
            {{ report.gradeLabel || '-' }}
          </span>
          <span class="verdict-summary">{{ report.summary }}</span>
        </div>
        <ul v-if="report.tips && report.tips.length" class="verdict-tips">
          <li v-for="(t, i) in report.tips" :key="i">{{ t }}</li>
        </ul>
      </n-alert>

      <!-- 指标卡片 -->
      <n-grid :cols="24" :x-gap="12" :y-gap="12" class="mb-3">
        <n-gi :span="5">
          <n-card size="small" class="metric-card">
            <div class="metric-label">Top1 准确率</div>
            <div class="metric-value" :class="rateClass(report.accuracy1)">
              {{ pct(report.accuracy1) }}
            </div>
          </n-card>
        </n-gi>
        <n-gi :span="5">
          <n-card size="small" class="metric-card">
            <div class="metric-label">Top3 准确率</div>
            <div class="metric-value" :class="rateClass(report.accuracy3)">
              {{ pct(report.accuracy3) }}
            </div>
          </n-card>
        </n-gi>
        <n-gi :span="4">
          <n-card size="small" class="metric-card">
            <div class="metric-label">空预测率</div>
            <div class="metric-value" :class="report.emptyRate > 0.2 ? 'rate-low' : ''">
              {{ pct(report.emptyRate) }}
            </div>
          </n-card>
        </n-gi>
        <n-gi :span="5">
          <n-card size="small" class="metric-card">
            <div class="metric-label">平均置信度</div>
            <div class="metric-value">{{ pct(report.avgTop1Score) }}</div>
          </n-card>
        </n-gi>
        <n-gi :span="5">
          <n-card size="small" class="metric-card">
            <div class="metric-label">用例 / 耗时</div>
            <div class="metric-value-sm"
              >{{ report.total }} 条 · {{ (report.costMs / 1000).toFixed(1) }}s</div
            >
            <div v-if="report.errorRate > 0" class="metric-sub rate-low">
              异常率 {{ pct(report.errorRate) }}
            </div>
          </n-card>
        </n-gi>
      </n-grid>

      <n-grid :cols="24" :x-gap="12">
        <!-- 各类别 P/R/F1 -->
        <n-gi :span="14">
          <n-card size="small" title="各意图类别指标" :bordered="false">
            <n-data-table
              :columns="nodeColumns"
              :data="report.perNode"
              :pagination="false"
              size="small"
              :max-height="300"
            />
          </n-card>
        </n-gi>
        <!-- 置信度校准 -->
        <n-gi :span="10">
          <n-card size="small" title="置信度校准（按 Top1 分数分桶，桶内命中率）" :bordered="false">
            <div v-for="b in report.calibration" :key="b.bin" class="calib-row">
              <span class="calib-bin">{{ b.bin }}</span>
              <n-progress
                type="line"
                :percentage="Math.round(b.acc * 100)"
                :height="14"
                :border-radius="4"
                :fill-border-radius="4"
                :indicator-placement="'inside'"
                :status="calibStatus(b)"
                style="flex: 1"
              />
              <span class="calib-count">{{ b.count }} 条</span>
            </div>
            <n-text depth="3" style="font-size: 12px">
              理想：分数越高命中率越高（右高左低）。若高分桶命中率低，说明置信度不可信、阈值需调高。
            </n-text>
          </n-card>
        </n-gi>
      </n-grid>

      <!-- 误判案例 -->
      <n-card size="small" :bordered="false" class="mt-3" title="误判案例（Top1 未命中期望）">
        <n-empty v-if="!report.misclassified.length" description="无误判 🎉" />
        <n-data-table
          v-else
          :columns="misColumns"
          :data="report.misclassified"
          :pagination="{ pageSize: 10 }"
          size="small"
          :row-key="(_r: any, i: number) => i"
        />
      </n-card>
    </template>
    <n-empty v-else description="加载或编写测试集后点击「一键评估」" style="margin-top: 30px" />
  </div>
</template>

<script setup lang="ts">
  import { ref, h, computed, watch } from 'vue';
  import { useMessage } from 'naive-ui';
  import { NInput, NSelect, NButton, NTag, NSpace, NPopconfirm } from 'naive-ui';
  import type { DataTableColumns } from 'naive-ui';
  import { ThunderboltOutlined, PlusOutlined, RobotOutlined } from '@vicons/antd';
  import {
    evalIntentBatch,
    evalIntentSingle,
    evalIntentSeedGen,
    type IntentEvalCase,
    type IntentEvalReport,
    type IntentHitCandidate,
    type IntentEvalResult,
    type IntentNodeMetric,
    type IntentCalibrationBin,
  } from '@/api/system/knowledge';
  import type { IntentNodeTree } from '@/api/system/knowledge';

  const props = defineProps<{ intentTree: IntentNodeTree[] }>();
  const message = useMessage();

  // ---------- 参数 ----------
  const topN = ref(3);
  const minScore = ref(0.35);
  const LS_KEY = 'xservice_intent_eval_cases_v1';

  // ---------- 期望意图下拉选项（从意图树拍平） ----------
  const flatNodes = computed<IntentNodeTree[]>(() => {
    const out: IntentNodeTree[] = [];
    const walk = (nodes: IntentNodeTree[]) => {
      for (const n of nodes) {
        if (n.id === '__virtual_root__') {
          if (n.children) walk(n.children);
          continue;
        }
        out.push(n);
        if (n.children) walk(n.children);
      }
    };
    walk(props.intentTree);
    return out.filter((n) => n && n.id);
  });
  const nodeOptions = computed(() =>
    flatNodes.value.map((n) => ({
      label: `${n.name}（${n.kind}）`,
      value: n.id,
    }))
  );

  // ---------- 测试集 ----------
  const cases = ref<IntentEvalCase[]>(loadFromLs());

  function loadFromLs(): IntentEvalCase[] {
    try {
      const raw = localStorage.getItem(LS_KEY);
      if (raw) {
        const arr = JSON.parse(raw);
        if (Array.isArray(arr)) return arr;
      }
    } catch {
      /* ignore */
    }
    return [];
  }
  function saveToLs() {
    try {
      localStorage.setItem(LS_KEY, JSON.stringify(cases.value));
    } catch {
      /* ignore */
    }
  }
  watch(cases, saveToLs, { deep: true });

  /** AI 基于意图配置自动生成多样化测试用例（贴近真实用户问法，不照抄 examples） */
  const seedGenLoading = ref(false);
  async function genSeedByAi() {
    if (!flatNodes.value.length) {
      message.warning('意图树为空，请先到「意图列表」配置意图');
      return;
    }
    seedGenLoading.value = true;
    try {
      const res: any = await evalIntentSeedGen(4);
      if (res && res.code === 0 && Array.isArray(res.data) && res.data.length) {
        cases.value = res.data;
        report.value = null;
        message.success(`AI 已生成 ${res.data.length} 条测试用例`);
      } else {
        message.error(res?.message || '生成失败，请稍后重试');
      }
    } catch (e: any) {
      message.error(e?.message || '生成异常');
    } finally {
      seedGenLoading.value = false;
    }
  }

  function addRow() {
    cases.value.push({ query: '', expectNodeId: '', expectNodeName: '', note: '' });
  }
  function delRow(i: number) {
    cases.value.splice(i, 1);
  }
  function clearCases() {
    cases.value = [];
    report.value = null;
  }

  // ---------- 表格列（测试集，可内联编辑） ----------
  const tableColumns = computed<DataTableColumns<IntentEvalCase>>(() => [
    {
      title: '用户问题',
      key: 'query',
      width: 380,
      render: (row, i) =>
        h(NInput, {
          value: row.query,
          placeholder: '输入用户问题',
          onUpdateValue: (v: string) => {
            cases.value[i].query = v;
          },
        }),
    },
    {
      title: '期望命中意图',
      key: 'expectNodeId',
      width: 240,
      render: (row, i) =>
        h(NSelect, {
          value: row.expectNodeId || null,
          options: nodeOptions.value,
          filterable: true,
          clearable: true,
          placeholder: '留空表示期望空预测',
          onUpdateValue: (v: string | null) => {
            const opt = nodeOptions.value.find((o) => o.value === v);
            cases.value[i].expectNodeId = v || '';
            cases.value[i].expectNodeName = opt ? opt.label.split('（')[0] : '';
          },
        }),
    },
    {
      title: '备注',
      key: 'note',
      render: (row, i) =>
        h(NInput, {
          value: row.note,
          placeholder: '可选',
          onUpdateValue: (v: string) => {
            cases.value[i].note = v;
          },
        }),
    },
    {
      title: '操作',
      key: 'op',
      width: 70,
      render: (_row, i) =>
        h(
          NPopconfirm,
          { onPositiveClick: () => delRow(i) },
          {
            trigger: () =>
              h(NButton, { size: 'small', type: 'error', quaternary: true }, () => h('span', '删')),
            default: () => '删除该用例？',
          }
        ),
    },
  ]);

  // ---------- 评估 ----------
  const evaluating = ref(false);
  const report = ref<IntentEvalReport | null>(null);

  async function runEval() {
    const valid = cases.value.filter((c) => c.query && c.query.trim());
    if (!valid.length) {
      message.warning('没有有效的用例（query 不能为空）');
      return;
    }
    evaluating.value = true;
    report.value = null;
    try {
      const res: any = await evalIntentBatch(valid, {
        topN: topN.value,
        minScore: minScore.value,
      });
      if (res && res.code === 0 && res.data) {
        report.value = res.data as IntentEvalReport;
        message.success(
          `评估完成：Top1 ${pct(res.data.accuracy1)} / Top3 ${pct(res.data.accuracy3)}`
        );
      } else {
        message.error(res?.message || '评估失败');
      }
    } catch (e: any) {
      message.error(e?.message || '评估异常');
    } finally {
      evaluating.value = false;
    }
  }

  // ---------- 单条调试 ----------
  const singleQuery = ref('');
  const singleLoading = ref(false);
  const singleResult = ref<IntentHitCandidate[]>([]);
  const singleDone = ref(false);

  async function runSingle() {
    if (!singleQuery.value.trim()) return;
    singleLoading.value = true;
    singleResult.value = [];
    singleDone.value = false;
    try {
      const res: any = await evalIntentSingle(singleQuery.value.trim(), {
        topN: topN.value,
        minScore: minScore.value,
      });
      if (res && res.code === 0 && Array.isArray(res.data)) {
        singleResult.value = res.data.filter((c: any) => c && c.id);
      }
      singleDone.value = true;
    } catch (e: any) {
      message.error(e?.message || '分类异常');
    } finally {
      singleLoading.value = false;
    }
  }

  // ---------- 节点指标表 ----------
  const nodeColumns: DataTableColumns<IntentNodeMetric> = [
    { title: '意图', key: 'name', width: 160 },
    { title: '类型', key: 'kind', width: 80 },
    { title: '用例数', key: 'caseCount', width: 80, align: 'center' },
    { title: '正确', key: 'correct', width: 70, align: 'center' },
    { title: '错误', key: 'wrong', width: 70, align: 'center' },
    {
      title: '精确率 P',
      key: 'precision',
      align: 'center',
      render: (r) => pct(r.precision),
    },
    {
      title: '召回率 R',
      key: 'recall',
      align: 'center',
      render: (r) => pct(r.recall),
    },
    {
      title: 'F1',
      key: 'f1',
      align: 'center',
      render: (r) => h('span', { class: rateClass(r.f1) }, pct(r.f1)),
    },
  ];

  // ---------- 误判表 ----------
  const misColumns: DataTableColumns<IntentEvalResult> = [
    { title: '问题', key: 'query', width: 260, ellipsis: { tooltip: true } },
    {
      title: '期望',
      key: 'expectNodeName',
      width: 130,
      render: (r) =>
        h(
          NTag,
          { size: 'small', type: 'info' },
          () => r.expectNodeName || r.expectNodeId || '(空)'
        ),
    },
    {
      title: '实际 Top1',
      key: 'hitNodeName',
      width: 180,
      render: (r) =>
        r.empty
          ? h(NTag, { size: 'small', type: 'warning' }, () => '空预测')
          : h(
              NSpace,
              { size: 4, align: 'center', wrap: false },
              () => [
                h(
                  NTag,
                  { size: 'small', type: 'error' },
                  () => `${r.hitNodeName || r.hitNodeId} (${r.hitKind})`
                ),
                r.ruleShortCircuit
                  ? h(NTag, { size: 'small', type: 'info', bordered: false }, () => '规则')
                  : null,
              ] as any
            ),
    },
    {
      title: '分数',
      key: 'score',
      width: 80,
      align: 'center',
      render: (r) => (r.score == null ? '-' : pct(r.score)),
    },
    {
      title: 'Top3 候选',
      key: 'topKHits',
      render: (r) => {
        if (!r.topKHits || !r.topKHits.length) return '-';
        return h(
          NSpace,
          { size: 4, wrap: true },
          () =>
            r.topKHits!.map((c, i) =>
              h(
                NTag,
                {
                  size: 'small',
                  type: c.id === r.expectNodeId ? 'success' : 'default',
                  bordered: false,
                },
                () => `${i + 1}.${c.name} ${(c.score * 100).toFixed(0)}%`
              )
            ) as any
        );
      },
    },
  ];

  // ---------- 工具 ----------
  function pct(v?: number | null): string {
    if (v == null || isNaN(v)) return '-';
    return `${(v * 100).toFixed(1)}%`;
  }
  function rateClass(v?: number | null): string {
    if (v == null) return '';
    if (v >= 0.8) return 'rate-high';
    if (v >= 0.6) return 'rate-mid';
    return 'rate-low';
  }
  function calibStatus(b: IntentCalibrationBin): 'success' | 'warning' | 'error' | 'default' {
    if (b.count === 0) return 'default';
    if (b.acc >= 0.8) return 'success';
    if (b.acc >= 0.5) return 'warning';
    return 'error';
  }
  /** 评级 → n-alert 配色 */
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
</script>

<style lang="less" scoped>
  .eval-panel {
    padding: 4px 0;
  }
  .eval-toolbar {
    margin-bottom: 12px;
  }
  .opt-label {
    font-size: 13px;
    color: #666;
    margin-left: 8px;
  }
  .single-result {
    margin-top: 8px;
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
  }
  .metric-card {
    text-align: center;
    :deep(.n-card__content) {
      padding: 12px;
    }
  }
  .metric-label {
    font-size: 12px;
    color: #999;
    margin-bottom: 4px;
  }
  .metric-value {
    font-size: 22px;
    font-weight: 700;
    color: #333;
  }
  .metric-value-sm {
    font-size: 16px;
    font-weight: 700;
    color: #333;
  }
  .metric-sub {
    font-size: 11px;
    margin-top: 2px;
  }
  .rate-high {
    color: #18a058;
  }
  .rate-mid {
    color: #f0a020;
  }
  .rate-low {
    color: #d03050;
  }
  .calib-row {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 10px;
  }
  .calib-bin {
    width: 70px;
    font-size: 12px;
    color: #666;
    flex-shrink: 0;
  }
  .calib-count {
    width: 50px;
    font-size: 12px;
    color: #999;
    text-align: right;
    flex-shrink: 0;
  }
  .verdict-bar {
    border-radius: 8px;
  }
  .verdict-head {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }
  .verdict-badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    padding: 2px 12px;
    border-radius: 12px;
    font-size: 13px;
    font-weight: 700;
    color: #fff;
    flex-shrink: 0;
  }
  .badge-excellent {
    background: #18a058;
  }
  .badge-good {
    background: #2080f0;
  }
  .badge-fair {
    background: #f0a020;
  }
  .badge-poor {
    background: #d03050;
  }
  .verdict-summary {
    font-size: 14px;
    font-weight: 600;
    color: #333;
  }
  .verdict-tips {
    margin: 8px 0 0 0;
    padding-left: 18px;
    font-size: 13px;
    color: #666;
    line-height: 1.7;
  }
  .verdict-tips li {
    list-style: disc;
  }
</style>
