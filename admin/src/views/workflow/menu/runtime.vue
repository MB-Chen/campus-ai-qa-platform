<!--
  执行详情：按节点类型分别渲染（对标智能体 RagTraceDrawer），让调试能看到每个环节的调用上下文。
  数据契约（与后端 FlowNodeParser + 各 Node 落库一致）：
    outputData: 全局 sys.* 平铺 + 节点产出按 node.<cell> 分区（sys.content/sys.result/sys.purposeName/...）
    modelData : 节点原配置 + 调试字段（costMs / renderedSystemMsg / renderedUserPrompt / prompt /
                rawAnswer / hitIndex / hitName / switch.evaluation / switch.hitBranch / answerType ...）
  未知节点类型或解析失败 → 兜底回退到原始 JSON，保证不丢数据。
-->
<template>
  <div class="runtime-list">
    <n-spin :show="loading">
      <n-empty v-if="!loading && runtimeData.length === 0" description="暂无执行数据" />

      <!-- 顶部汇总（仅当有耗时数据时展示） -->
      <div v-if="!loading && totalTimeText" class="summary-bar">
        <div class="summary-item">
          <n-icon :size="15"><FieldTimeOutlined /></n-icon>
          <span class="summary-label">总耗时</span>
          <span class="summary-val">{{ totalTimeText }}</span>
        </div>
        <div class="summary-item">
          <n-icon :size="15"><ApartmentOutlined /></n-icon>
          <span class="summary-label">节点</span>
          <span class="summary-val">{{ runtimeData.length }} 个</span>
        </div>
        <div v-if="totalTokens > 0" class="summary-item">
          <n-icon :size="15"><ThunderboltOutlined /></n-icon>
          <span class="summary-label">tokens</span>
          <span class="summary-val">{{ totalTokens }}</span>
        </div>
      </div>

      <div
        v-for="(item, index) in runtimeData"
        :key="index"
        class="runtime-item"
        :class="{ 'is-hit': nodeMeta(item).costMs != null && currentIndex !== index }"
      >
        <div class="runtime-title" @click="toggle(index)">
          <div class="runtime-icon">
            <n-icon
              :component="CaretRightOutlined"
              :style="{ transform: currentIndex === index ? 'rotate(90deg)' : '' }"
            />
            <n-icon
              v-if="nodeMeta(item).icon"
              :component="nodeMeta(item).icon"
              :color="nodeMeta(item).color"
              :size="18"
            />
            <span class="node-label">{{ nodeMeta(item).name || item.nodeType }}</span>
            <!-- 耗时徽标 -->
            <span v-if="nodeMeta(item).costMs != null" class="cost-badge" :class="costClass(nodeMeta(item).costMs)">
              {{ (nodeMeta(item).costMs / 1000).toFixed(2) }}s
            </span>
          </div>
          <div class="runtime-status">
            第<div class="run-step">{{ item.step }}</div>步
          </div>
        </div>
        <n-collapse-transition :show="currentIndex === index">
          <div class="runtime-content-body">
            <!-- 按节点类型分场景渲染 -->
            <start-detail v-if="nodeMeta(item).type === 'start'" :item="item" />
            <llm-detail v-else-if="nodeMeta(item).type === 'llm'" :item="item" />
            <dataset-detail v-else-if="nodeMeta(item).type === 'dataset'" :item="item" />
            <purpose-detail v-else-if="nodeMeta(item).type === 'purpose'" :item="item" />
            <switch-detail v-else-if="nodeMeta(item).type === 'switch'" :item="item" />
            <agent-detail v-else-if="nodeMeta(item).type === 'agent'" :item="item" />
            <answer-detail v-else-if="nodeMeta(item).type === 'answer'" :item="item" />
            <raw-detail v-else :item="item" />
          </div>
        </n-collapse-transition>
      </div>
    </n-spin>
  </div>
</template>

<script setup>
  import { ref, computed, h, defineComponent, watch } from 'vue';
  import {
    CaretRightOutlined,
    FieldTimeOutlined,
    ApartmentOutlined,
    ThunderboltOutlined,
  } from '@vicons/antd';
  import { marked } from 'marked';
  import { getRunDetail } from '@/api/system/workflow';
  import { NODE_ICON_META } from '@/views/workflow/icons/index.js';

  marked.use({ breaks: true, gfm: true });

  const props = defineProps({
    runtimeId: { type: Number, default: 0 },
  });

  const loading = ref(false);
  const runtimeData = ref([]);
  // 初始 -1：不默认展开第一个（原 Bug：0 会命中 index=0 默认展开）
  const currentIndex = ref(-1);

  // 把 X6 shape 名（如 llm-node）归一成短类型 key（llm），用于匹配渲染组件
  function shapeTypeOf(nodeType) {
    if (!nodeType) return '';
    // 后端落库的 nodeType：start-node / llm-node / ...（部分老数据可能是 llm/answer 等短名）
    if (nodeType.endsWith('-node')) return nodeType.slice(0, -5);
    return nodeType;
  }

  // 解析一行的 meta：图标/名称/短类型/耗时
  function nodeMeta(item) {
    const shapeKey = item.nodeType || '';
    const iconMeta = NODE_ICON_META[shapeKey] || NODE_ICON_META[shapeKey + '-node'] || {};
    const type = shapeTypeOf(shapeKey);
    let costMs = null;
    try {
      const md = item.modelData ? JSON.parse(item.modelData) : {};
      if (typeof md.costMs === 'number') costMs = md.costMs;
    } catch {
      // ignore
    }
    return { icon: iconMeta.icon, name: iconMeta.name, color: iconMeta.color, type, costMs };
  }

  function costClass(ms) {
    if (ms >= 10000) return 'cost-danger';
    if (ms >= 3000) return 'cost-warning';
    return 'cost-ok';
  }

  function toggle(index) {
    currentIndex.value = currentIndex.value === index ? -1 : index;
  }

  // 顶部汇总：所有节点耗时求和 + token 求和
  const totalTimeText = computed(() => {
    let total = 0;
    for (const item of runtimeData.value) {
      const m = nodeMeta(item);
      if (m.costMs != null) total += m.costMs;
    }
    if (total <= 0) return '';
    return (total / 1000).toFixed(2) + 's';
  });
  const totalTokens = computed(() => {
    let t = 0;
    for (const item of runtimeData.value) {
      try {
        const md = item.modelData ? JSON.parse(item.modelData) : {};
        if (typeof md.totalTokenCount === 'number') t += md.totalTokenCount;
      } catch {
        // ignore
      }
    }
    return t;
  });

  // ====== 数据解析辅助（各类型组件共享） ======
  // 安全 parse JSON
  function safeParse(str) {
    if (!str) return {};
    try {
      return JSON.parse(str);
    } catch {
      return {};
    }
  }
  // 格式化 JSON 文本（兜底展示用）
  function formatJson(str) {
    if (!str) return '{}';
    try {
      return JSON.stringify(JSON.parse(str), null, 2);
    } catch {
      return str;
    }
  }
  // 从 outputData 取全局 sys.* 字段（平铺）
  function getSys(output, field) {
    if (!output) return '';
    return output[field] != null ? String(output[field]) : '';
  }
  // 从 outputData 取本节点（按 cell）分区产出
  function getNodePartition(output, cell) {
    if (!output || !cell) return {};
    const p = output['node.' + cell];
    return p && typeof p === 'object' ? p : {};
  }
  // markdown 渲染（容错）
  function renderMd(text) {
    if (!text) return '';
    try {
      const normalized = text.replace(/^(#{1,6})(?=\S)/gm, '$1 ');
      return marked.parse(normalized);
    } catch {
      return text;
    }
  }

  // ====== 各节点类型的渲染组件（函数式，复用上面的 helper） ======
  // start-node：入口，展示这次问的是什么
  const startDetail = defineComponent({
    props: { item: { type: Object, required: true } },
    setup(p) {
      return () => {
        const out = safeParse(p.item.outputData);
        const q = getSys(out, 'sys.question');
        return h('div', { class: 'detail-grid' }, [
          kv('用户问题', q, { md: true, strong: true }),
          kv('会话 id', getSys(out, 'sys.sessionId')),
          kv('提问时间', getSys(out, 'sys.time')),
          kv('IP', getSys(out, 'sys.ip')),
          kv('编排 id', getSys(out, 'sys.workflowId')),
        ]);
      };
    },
  });

  // llm-node：渲染后 prompt + 输出 + 耗时 + token
  const llmDetail = defineComponent({
    props: { item: { type: Object, required: true } },
    setup(p) {
      return () => {
        const md = safeParse(p.item.modelData);
        const part = getNodePartition(safeParse(p.item.outputData), p.item.cell);
        const content = part['sys.content'] || '';
        const children = [];
        if (md.renderedSystemMsg) children.push(codeBlock('System Prompt', md.renderedSystemMsg));
        if (md.renderedUserPrompt) children.push(codeBlock('User Prompt', md.renderedUserPrompt));
        children.push(mdBlock('输出内容', content));
        children.push(statRow([
          ['耗时', md.costMs != null ? (md.costMs / 1000).toFixed(2) + 's' : '-'],
          ['模型', md.modelInfo?.modelId ? `#${md.modelInfo.modelId}` : '-'],
          ['温度', md.modelInfo?.temperature != null ? md.modelInfo.temperature : '0.3'],
          ['记忆轮数', md.memory != null ? md.memory : 0],
        ]));
        if (md.totalTokenCount === 0 && md.tokenNote) {
          children.push(h('div', { class: 'note-tip' }, 'ℹ ' + md.tokenNote));
        }
        return h('div', { class: 'detail-stack' }, children);
      };
    },
  });

  // dataset-node：召回片段卡片
  // 注意：后端 writeVar 的 field 是带点号的字面量 key（如 "datasets.fragments"），
  // outputData 结构为 node.<cell>:{ "datasets.fragments":[...], "datasets.count":3, ... }，
  // 这里必须用方括号读取，不能用 . 访问。
  const datasetDetail = defineComponent({
    props: { item: { type: Object, required: true } },
    setup(p) {
      return () => {
        const part = getNodePartition(safeParse(p.item.outputData), p.item.cell);
        const fragments = Array.isArray(part['datasets.fragments']) ? part['datasets.fragments'] : [];
        const count = part['datasets.count'];
        const reranked = part['datasets.reranked'];
        const rerankModelId = part['datasets.rerankModelId'];
        const question = part['datasets.question'];
        const children = [];
        children.push(kv('检索问题', question || '', { md: true }));
        children.push(statRow([
          ['召回数', (count != null ? count : fragments.length) + ' 条'],
          ['是否重排', reranked ? '是' : '否'],
          ['重排模型', rerankModelId ? `#${rerankModelId}` : '-'],
        ]));
        if (fragments.length) {
          children.push(h('div', { class: 'detail-title' }, '召回片段'));
          children.push(
            h('div', { class: 'frag-list' }, fragments.map((f, i) =>
              h('div', { class: 'frag-card' }, [
                h('div', { class: 'frag-head' }, [
                  h('span', { class: 'frag-idx' }, '#' + (i + 1)),
                ]),
                h('div', { class: 'frag-text', innerHTML: renderMd(f.text || '') }),
              ])
            ))
          );
        } else {
          children.push(h('div', { class: 'note-tip' }, '未召回任何片段'));
        }
        return h('div', { class: 'detail-stack' }, children);
      };
    },
  });

  // purpose-node：分类清单 + 命中 + 原始回复
  const purposeDetail = defineComponent({
    props: { item: { type: Object, required: true } },
    setup(p) {
      return () => {
        const md = safeParse(p.item.modelData);
        const cateList = Array.isArray(md.cateList) ? md.cateList : [];
        const children = [];
        // 分类清单（高亮命中项）
        if (cateList.length) {
          children.push(h('div', { class: 'detail-title' }, '分类清单'));
          children.push(
            h('div', { class: 'cate-list' }, cateList.map((c, i) =>
              h('div', {
                class: ['cate-item', md.hitIndex === i ? 'cate-hit' : ''],
              }, [
                h('span', { class: 'cate-idx' }, (i + 1)),
                h('span', { class: 'cate-name' }, c.name || ''),
                md.hitIndex === i ? h('span', { class: 'cate-tag' }, '命中') : null,
              ])
            ))
          );
        }
        children.push(statRow([
          ['命中分类', md.hitName || '-'],
          ['耗时', md.costMs != null ? (md.costMs / 1000).toFixed(2) + 's' : '-'],
        ]));
        if (md.rawAnswer) children.push(codeBlock('模型原始回复', md.rawAnswer));
        return h('div', { class: 'detail-stack' }, children);
      };
    },
  });

  // switch-node：分支条件判断表
  const switchDetail = defineComponent({
    props: { item: { type: Object, required: true } },
    setup(p) {
      return () => {
        const md = safeParse(p.item.modelData);
        const evaluation = md['switch.evaluation'] || md.switch?.evaluation || [];
        const arr = Array.isArray(evaluation) ? evaluation : [];
        const children = [];
        const hitBranch = md['switch.hitBranch'];
        if (arr.length) {
          children.push(h('div', { class: 'detail-title' }, '条件判断'));
          arr.forEach((branch, bi) => {
            const isHit = hitBranch === branch.branch;
            const conds = Array.isArray(branch.conditions) ? branch.conditions : [];
            children.push(h('div', { class: ['branch-block', isHit ? 'branch-hit' : ''] }, [
              h('div', { class: 'branch-head' }, [
                h('span', { class: 'branch-name' }, `分支 ${branch.branch + 1}`),
                h('span', { class: 'branch-logic' }, branch.logic || ''),
                isHit ? h('span', { class: 'cate-tag' }, '命中') : null,
              ]),
              ...conds.map((c) =>
                h('div', { class: ['cond-row', c.pass ? 'cond-pass' : 'cond-fail'] }, [
                  h('span', { class: 'cond-field' }, c.field || ''),
                  h('span', { class: 'cond-op' }, c.op || ''),
                  h('span', { class: 'cond-val' }, String(c.expect ?? '')),
                  h('span', { class: 'cond-arrow' }, '→'),
                  h('span', { class: 'cond-actual' }, String(c.actual ?? '')),
                  h('span', { class: 'cond-result' }, c.pass ? '✓' : '✗'),
                ])
              ),
            ]));
          });
          if (hitBranch == null || hitBranch < 0) {
            children.push(h('div', { class: 'note-tip' }, '所有分支均未命中，走 else 分支'));
          }
        } else {
          // 兜底：旧数据只有 switch.result
          const part = getNodePartition(safeParse(p.item.outputData), p.item.cell);
          children.push(kv('命中结果', part['switch.result'] || 'else'));
        }
        children.push(statRow([['耗时', md.costMs != null ? (md.costMs / 1000).toFixed(2) + 's' : '-']]));
        return h('div', { class: 'detail-stack' }, children);
      };
    },
  });

  // agent-node：输入 + 输出
  const agentDetail = defineComponent({
    props: { item: { type: Object, required: true } },
    setup(p) {
      return () => {
        const part = getNodePartition(safeParse(p.item.outputData), p.item.cell);
        const md = safeParse(p.item.modelData);
        const children = [];
        children.push(kv('输入问题', part['agent.input'] || '', { md: true }));
        children.push(mdBlock('智能体输出', part['sys.agentContent'] || ''));
        children.push(statRow([['耗时', md.costMs != null ? (md.costMs / 1000).toFixed(2) + 's' : '-']]));
        return h('div', { class: 'detail-stack' }, children);
      };
    },
  });

  // answer-node：回复类型 + 内容
  const answerDetail = defineComponent({
    props: { item: { type: Object, required: true } },
    setup(p) {
      return () => {
        const part = getNodePartition(safeParse(p.item.outputData), p.item.cell);
        const md = safeParse(p.item.modelData);
        const children = [];
        children.push(statRow([
          ['回复类型', md.answerTypeLabel || (md.answerType === 1 ? '引用变量' : '静态文本')],
          ['耗时', md.costMs != null ? (md.costMs / 1000).toFixed(2) + 's' : '-'],
        ]));
        children.push(mdBlock('回复内容', part['sys.answer'] || ''));
        return h('div', { class: 'detail-stack' }, children);
      };
    },
  });

  // 兜底：未知类型 / 解析失败 → 原始 JSON
  const rawDetail = defineComponent({
    props: { item: { type: Object, required: true } },
    setup(p) {
      return () => h('div', null, [
        h('div', { class: 'detail-box' }, [
          h('div', { class: 'detail-title' }, '输出数据'),
          h('pre', { class: 'json-view' }, formatJson(p.item.outputData)),
        ]),
        p.item.modelData
          ? h('div', { class: 'detail-box', style: 'margin-top: 10px' }, [
              h('div', { class: 'detail-title' }, '模型/配置数据'),
              h('pre', { class: 'json-view' }, formatJson(p.item.modelData)),
            ])
          : null,
      ]);
    },
  });

  // ====== 公共渲染片段（返回 vnode） ======
  function kv(label, val, opts = {}) {
    const text = val == null ? '' : String(val);
    const valNode = opts.md
      ? h('div', { class: ['kv-val', opts.strong ? 'kv-strong' : ''], innerHTML: renderMd(text) })
      : h('span', { class: ['kv-val', opts.strong ? 'kv-strong' : ''] }, text || '-');
    return h('div', { class: 'kv-row' }, [
      h('span', { class: 'kv-label' }, label),
      valNode,
    ]);
  }
  function codeBlock(title, text) {
    return h('div', { class: 'detail-title' }, [
      title,
      h('pre', { class: 'code-view' }, text || ''),
    ]);
  }
  function mdBlock(title, text) {
    return h('div', null, [
      h('div', { class: 'detail-title' }, title),
      h('div', { class: 'md-output', innerHTML: renderMd(text) }),
    ]);
  }
  function statRow(pairs) {
    return h('div', { class: 'stat-row' }, pairs.map(([k, v]) =>
      h('div', { class: 'stat-item' }, [
        h('span', { class: 'stat-k' }, k),
        h('span', { class: 'stat-v' }, String(v ?? '-')),
      ])
    ));
  }

  async function load() {
    if (!props.runtimeId) return;
    loading.value = true;
    try {
      const res = await getRunDetail(props.runtimeId);
      if (res && res.code === 0 && Array.isArray(res.data)) {
        // 过滤掉 outputData 和 modelData 都为空的脏行
        runtimeData.value = res.data.filter((x) => x && (x.outputData || x.modelData));
      } else {
        runtimeData.value = [];
      }
    } catch {
      runtimeData.value = [];
    } finally {
      loading.value = false;
    }
  }

  watch(
    () => props.runtimeId,
    () => load(),
    { immediate: true },
  );
</script>

<style scoped>
  .runtime-list {
    display: flex;
    flex-direction: column;
    width: 100%;
    max-height: 60vh;
    overflow-y: auto;
  }
  .summary-bar {
    display: flex;
    gap: 20px;
    flex-wrap: wrap;
    padding: 10px 14px;
    margin-bottom: 4px;
    background: var(--n-action-color, #f6ffed);
    border: 1px solid #b7eb8f;
    border-radius: 6px;
  }
  .summary-item {
    display: flex;
    align-items: center;
    gap: 6px;
    color: #555;
  }
  .summary-label {
    font-size: 12px;
    color: #888;
  }
  .summary-val {
    font-size: 14px;
    font-weight: 600;
    color: #18a058;
    font-variant-numeric: tabular-nums;
  }
  .runtime-item {
    margin-top: 10px;
    cursor: pointer;
    background: #fff;
    border: 1px solid #eee;
    border-radius: 6px;
    padding: 10px 14px;
  }
  .runtime-title {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  .runtime-icon {
    display: flex;
    align-items: center;
    gap: 6px;
  }
  .node-label {
    font-size: 14px;
    font-weight: 500;
  }
  .cost-badge {
    margin-left: 4px;
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
  .runtime-status {
    display: flex;
    align-items: center;
    font-size: 13px;
    color: #666;
  }
  .run-step {
    background: #646a73;
    width: 20px;
    height: 20px;
    border-radius: 50%;
    margin: 0 5px;
    line-height: 20px;
    text-align: center;
    color: #fff;
    font-size: 12px;
  }
  .runtime-content-body {
    font-size: 13px;
    margin-top: 10px;
  }

  /* 分场景详情通用结构 */
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

  /* key-value 行 */
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

  /* prompt 代码块 */
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

  /* markdown 输出 */
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

  /* 统计行 */
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

  /* 召回片段 */
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

  /* 意图分类清单 */
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

  /* switch 分支判断 */
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

  /* 兜底 JSON */
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
