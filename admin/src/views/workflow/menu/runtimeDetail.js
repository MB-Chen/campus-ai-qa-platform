/*
 * 编排「执行详情」可复用渲染逻辑。
 * 从 runtime.vue 抽出，供 runtime.vue（执行详情弹窗）与 debug.vue（调试页内嵌步骤流）共用。
 *
 * 数据契约（与后端 FlowNodeParser + 各 Node 落库一致）：
 *   outputData: 全局 sys.* 平铺 + 节点产出按 node.<cell> 分区（sys.content/sys.result/sys.purposeName/...）
 *   modelData : 节点原配置 + 调试字段（costMs / renderedSystemMsg / renderedUserPrompt / prompt /
 *               rawAnswer / hitIndex / hitName / switch.evaluation / switch.hitBranch / answerType ...）
 * 未知节点类型或解析失败 → 兜底回退到原始 JSON，保证不丢数据。
 */
import { h, defineComponent } from 'vue';
import { marked } from 'marked';
import { NODE_ICON_META } from '@/views/workflow/icons/index.js';

marked.use({ breaks: true, gfm: true });

// ====== 数据解析辅助（各类型组件共享） ======
// 安全 parse JSON
export function safeParse(str) {
  if (!str) return {};
  try {
    return JSON.parse(str);
  } catch {
    return {};
  }
}
// 格式化 JSON 文本（兜底展示用）
export function formatJson(str) {
  if (!str) return '{}';
  try {
    return JSON.stringify(JSON.parse(str), null, 2);
  } catch {
    return str;
  }
}
// 从 outputData 取全局 sys.* 字段（平铺）
export function getSys(output, field) {
  if (!output) return '';
  return output[field] != null ? String(output[field]) : '';
}
// 从 outputData 取本节点（按 cell）分区产出
export function getNodePartition(output, cell) {
  if (!output || !cell) return {};
  const p = output['node.' + cell];
  return p && typeof p === 'object' ? p : {};
}
// markdown 渲染（容错）
export function renderMd(text) {
  if (!text) return '';
  try {
    const normalized = text.replace(/^(#{1,6})(?=\S)/gm, '$1 ');
    return marked.parse(normalized);
  } catch {
    return text;
  }
}

// 把 X6 shape 名（如 llm-node）归一成短类型 key（llm），用于匹配渲染组件
export function shapeTypeOf(nodeType) {
  if (!nodeType) return '';
  // 后端落库的 nodeType：start-node / llm-node / ...（部分老数据可能是 llm/answer 等短名）
  if (nodeType.endsWith('-node')) return nodeType.slice(0, -5);
  return nodeType;
}

// 解析一行的 meta：图标/名称/短类型/耗时
export function nodeMeta(item) {
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

export function costClass(ms) {
  if (ms >= 10000) return 'cost-danger';
  if (ms >= 3000) return 'cost-warning';
  return 'cost-ok';
}

// ====== 公共渲染片段（返回 vnode） ======
export function kv(label, val, opts = {}) {
  const text = val == null ? '' : String(val);
  const valNode = opts.md
    ? h('div', { class: ['kv-val', opts.strong ? 'kv-strong' : ''], innerHTML: renderMd(text) })
    : h('span', { class: ['kv-val', opts.strong ? 'kv-strong' : ''] }, text || '-');
  return h('div', { class: 'kv-row' }, [h('span', { class: 'kv-label' }, label), valNode]);
}
export function codeBlock(title, text) {
  return h('div', { class: 'detail-title' }, [title, h('pre', { class: 'code-view' }, text || '')]);
}
export function mdBlock(title, text) {
  return h('div', null, [
    h('div', { class: 'detail-title' }, title),
    h('div', { class: 'md-output', innerHTML: renderMd(text) }),
  ]);
}
export function statRow(pairs) {
  return h(
    'div',
    { class: 'stat-row' },
    pairs.map(([k, v]) =>
      h('div', { class: 'stat-item' }, [
        h('span', { class: 'stat-k' }, k),
        h('span', { class: 'stat-v' }, String(v ?? '-')),
      ])
    )
  );
}

// ====== 各节点类型的渲染组件（函数式，复用上面的 helper） ======
// start-node：入口，展示这次问的是什么
export const startDetail = defineComponent({
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
export const llmDetail = defineComponent({
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
      children.push(
        statRow([
          ['耗时', md.costMs != null ? (md.costMs / 1000).toFixed(2) + 's' : '-'],
          ['模型', md.modelInfo?.modelId ? `#${md.modelInfo.modelId}` : '-'],
          ['温度', md.modelInfo?.temperature != null ? md.modelInfo.temperature : '0.3'],
          ['记忆轮数', md.memory != null ? md.memory : 0],
        ])
      );
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
export const datasetDetail = defineComponent({
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
      children.push(
        statRow([
          ['召回数', (count != null ? count : fragments.length) + ' 条'],
          ['是否重排', reranked ? '是' : '否'],
          ['重排模型', rerankModelId ? `#${rerankModelId}` : '-'],
        ])
      );
      if (fragments.length) {
        children.push(h('div', { class: 'detail-title' }, '召回片段'));
        children.push(
          h(
            'div',
            { class: 'frag-list' },
            fragments.map((f, i) =>
              h('div', { class: 'frag-card' }, [
                h('div', { class: 'frag-head' }, [h('span', { class: 'frag-idx' }, '#' + (i + 1))]),
                h('div', { class: 'frag-text', innerHTML: renderMd(f.text || '') }),
              ])
            )
          )
        );
      } else {
        children.push(h('div', { class: 'note-tip' }, '未召回任何片段'));
      }
      return h('div', { class: 'detail-stack' }, children);
    };
  },
});

// purpose-node：分类清单 + 命中 + 原始回复
export const purposeDetail = defineComponent({
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
          h(
            'div',
            { class: 'cate-list' },
            cateList.map((c, i) =>
              h('div', { class: ['cate-item', md.hitIndex === i ? 'cate-hit' : ''] }, [
                h('span', { class: 'cate-idx' }, i + 1),
                h('span', { class: 'cate-name' }, c.name || ''),
                md.hitIndex === i ? h('span', { class: 'cate-tag' }, '命中') : null,
              ])
            )
          )
        );
      }
      children.push(
        statRow([
          ['命中分类', md.hitName || '-'],
          ['耗时', md.costMs != null ? (md.costMs / 1000).toFixed(2) + 's' : '-'],
        ])
      );
      if (md.rawAnswer) children.push(codeBlock('模型原始回复', md.rawAnswer));
      return h('div', { class: 'detail-stack' }, children);
    };
  },
});

// switch-node：分支条件判断表
export const switchDetail = defineComponent({
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
          children.push(
            h('div', { class: ['branch-block', isHit ? 'branch-hit' : ''] }, [
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
            ])
          );
        });
        if (hitBranch == null || hitBranch < 0) {
          children.push(h('div', { class: 'note-tip' }, '所有分支均未命中，走 else 分支'));
        }
      } else {
        // 兜底：旧数据只有 switch.result
        const part = getNodePartition(safeParse(p.item.outputData), p.item.cell);
        children.push(kv('命中结果', part['switch.result'] || 'else'));
      }
      children.push(
        statRow([['耗时', md.costMs != null ? (md.costMs / 1000).toFixed(2) + 's' : '-']])
      );
      return h('div', { class: 'detail-stack' }, children);
    };
  },
});

// agent-node：输入 + 输出
export const agentDetail = defineComponent({
  props: { item: { type: Object, required: true } },
  setup(p) {
    return () => {
      const part = getNodePartition(safeParse(p.item.outputData), p.item.cell);
      const md = safeParse(p.item.modelData);
      const children = [];
      children.push(kv('输入问题', part['agent.input'] || '', { md: true }));
      children.push(mdBlock('智能体输出', part['sys.agentContent'] || ''));
      children.push(
        statRow([['耗时', md.costMs != null ? (md.costMs / 1000).toFixed(2) + 's' : '-']])
      );
      return h('div', { class: 'detail-stack' }, children);
    };
  },
});

// answer-node：回复类型 + 内容
export const answerDetail = defineComponent({
  props: { item: { type: Object, required: true } },
  setup(p) {
    return () => {
      const part = getNodePartition(safeParse(p.item.outputData), p.item.cell);
      const md = safeParse(p.item.modelData);
      const children = [];
      children.push(
        statRow([
          ['回复类型', md.answerTypeLabel || (md.answerType === 1 ? '引用变量' : '静态文本')],
          ['耗时', md.costMs != null ? (md.costMs / 1000).toFixed(2) + 's' : '-'],
        ])
      );
      children.push(mdBlock('回复内容', part['sys.answer'] || ''));
      return h('div', { class: 'detail-stack' }, children);
    };
  },
});

// 兜底：未知类型 / 解析失败 → 原始 JSON
export const rawDetail = defineComponent({
  props: { item: { type: Object, required: true } },
  setup(p) {
    return () =>
      h('div', null, [
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

// ====== 统一包装组件：按节点类型派发 ======
// 模板里用 <node-body :item="item" /> 即可，无需再写一长串 v-if/v-else-if。
export const NodeBody = defineComponent({
  name: 'NodeBody',
  props: { item: { type: Object, required: true } },
  setup(p) {
    return () => {
      const type = nodeMeta(p.item).type;
      switch (type) {
        case 'start':
          return h(startDetail, { item: p.item });
        case 'llm':
          return h(llmDetail, { item: p.item });
        case 'dataset':
          return h(datasetDetail, { item: p.item });
        case 'purpose':
          return h(purposeDetail, { item: p.item });
        case 'switch':
          return h(switchDetail, { item: p.item });
        case 'agent':
          return h(agentDetail, { item: p.item });
        case 'answer':
          return h(answerDetail, { item: p.item });
        default:
          return h(rawDetail, { item: p.item });
      }
    };
  },
});
