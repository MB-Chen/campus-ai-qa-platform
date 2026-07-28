<!-- 条件分支节点：显示 IF/ELSEIF/ELSE 分支条件 -->
<template>
  <div class="node-base" :class="{ 'node-active': active }" ref="baseEl">
    <div class="flex-center">
      <n-icon :component="meta.icon" :color="meta.color" :size="18" />
      <span class="node-name" v-if="no === 1">{{ meta.name }}</span>
      <span class="node-name" v-else>{{ meta.name }}{{ no - 1 }}</span>
    </div>

    <div
      class="tips-text branch-block"
      v-for="(item, index) in nodeInnerData.ifBranch"
      :key="index"
    >
      <div class="flex-center branch-head" :data-port="index">
        <span v-if="index === 0">IF</span>
        <span v-else>ELSEIF</span>
        <span class="logic" v-if="item.switch === 1">AND</span>
        <span class="logic" v-if="item.switch === 2">OR</span>
      </div>
      <div
        class="flex-center tips-item"
        v-for="(item2, index2) in item.data"
        :key="index2"
      >
        <span class="line1 cond-text"
          >{{ condField(item2.input) }} {{ optionsMap.get(item2.tips) }}
          {{ item2.value }}</span
        >
      </div>
    </div>

    <div class="tips-text else-block">
      <span>ELSE</span>
    </div>
  </div>
</template>

<script setup>
  import { ref, inject, nextTick, onMounted, onBeforeUnmount } from 'vue';
  import initConfig from '@/views/workflow/initConfig.js';
  import { iconComponent } from '@/views/workflow/icons/index.js';

  const getNode = inject('getNode');
  const getGraph = inject('getGraph');
  const meta = iconComponent('switch-node');

  const nodeData = getNode().getData();
  const no = ref(nodeData.no || 1);
  const nodeInnerData = ref(nodeData);
  const active = ref(false);
  const baseEl = ref(null);

  const optionsMap = new Map();
  JSON.parse(JSON.stringify(initConfig.switchOptions)).forEach((item) => {
    optionsMap.set(item.type, item.label);
  });

  // 全图 field → 中文名 映射，用于条件变量显示中文（如 sys.question → 用户问题）。
  // 遍历图中所有节点的 sysData/userData/outData 构建，找不到时降级显示原 field。
  const fieldMap = ref({});
  function rebuildFieldMap() {
    const graph = getGraph ? getGraph() : null;
    if (!graph) return;
    const m = {};
    graph.getNodes().forEach((node) => {
      const data = node.getData();
      if (!data) return;
      const vars =
        data.pages === 'start'
          ? (data.sysData || []).concat(data.userData || [])
          : data.outData || [];
      vars.forEach((v) => {
        if (v && v.field) m[v.field] = v.name || v.field;
      });
    });
    fieldMap.value = m;
  }
  rebuildFieldMap();

  // 条件变量显示：兼容新 [{nodeId, field}] 与旧 [nodeId, field]，优先显示中文名
  function condField(input) {
    if (!Array.isArray(input) || input.length === 0) return '';
    const first = input[0];
    const field = first instanceof Object ? first.field || '' : input[1] || '';
    return fieldMap.value[field] || field;
  }

  const NODE_WIDTH = 230;
  // 端口视觉微调：整体下移像素数（正值=下移）
  const PORT_Y_OFFSET = 6;

  /**
   * 对齐右侧输出端口到各分支行。
   * 用 getBoundingClientRect 测量真实视口坐标差值，除以缩放比还原为节点逻辑坐标。
   * 这是已验证可行的方法——问题只在挂载时序。
   */
  function alignOutputPorts() {
    const node = getNode();
    const el = baseEl.value;
    if (!el || !node) return;

    // nextTick 等 Vue 更完 DOM，rAF 等 X6 内部渲染完
    nextTick(() => {
      scheduleAlign(node, el, 8);
    });
  }

  function scheduleAlign(node, el, retries) {
    if (retries <= 0) return;
    requestAnimationFrame(() => {
      doAlignPorts(node, el);
      // 如果 DOM 元素数量还不匹配（新增/删除分支后），继续重试
      const data = node.getData();
      const expected = (data.ifBranch || []).length;
      const found = el.querySelectorAll('.branch-block').length;
      if (found !== expected && retries > 1) {
        scheduleAlign(node, el, retries - 1);
      }
    });
  }

  function doAlignPorts(node, el) {
    const branchBlocks = el.querySelectorAll('.branch-block');
    const elseBlock = el.querySelector('.else-block');
    if (!branchBlocks.length && !elseBlock) return;

    // .x6-node 是 X6 节点最外层容器，也是端口 absolute 坐标的原点
    const x6Node = el.closest('.x6-node') || el;
    const nodeRect = x6Node.getBoundingClientRect();

    // 缩放校正：CSS 宽度固定 230px，实际渲染宽度 / 230 = 当前缩放
    const scale = nodeRect.width / NODE_WIDTH || 1;

    // 同步节点模型高度
    node.resize(NODE_WIDTH, Math.round(nodeRect.height / scale));

    const outputPorts = node.getPorts().filter((p) => p.type === 'output');
    let outIdx = 0;

    for (const port of outputPorts) {
      let targetEl;
      if (outIdx < branchBlocks.length) {
        // IF / ELSEIF → 对齐到分支标题行
        targetEl =
          branchBlocks[outIdx].querySelector('.branch-head') ||
          branchBlocks[outIdx];
      } else {
        targetEl = elseBlock; // ELSE
      }
      if (!targetEl) continue;

      const targetRect = targetEl.getBoundingClientRect();
      // 视口坐标差值 / 缩放 + 微调偏移 = 端口逻辑 Y 坐标
      const y = Math.round(
        (targetRect.top - nodeRect.top + targetRect.height / 2) / scale +
          PORT_Y_OFFSET,
      );

      node.portProp(port.id, 'args', { x: NODE_WIDTH, y });
      outIdx++;
    }

    // 强制 X6 重新渲染端口
    const ports = node.getPorts();
    node.setPropByPath('ports/items', [...ports]);
  }

  const node = getNode();
  node.on('change:data', ({ current }) => {
    active.value = current.checked;
    nodeInnerData.value = current;
    // 数据变化（如上游节点改了输出变量）后刷新中文名映射
    rebuildFieldMap();
    alignOutputPorts();
  });

  // 挂载即对齐；ResizeObserver 在布局稳定后再对齐一次（解决拖拽新建时偏移）
  let ro = null;
  let alignTimer = null;
  onMounted(() => {
    alignOutputPorts();
    // 拖拽新建时画布缩放/位置可能还没稳定，延迟 300ms 再对齐一次
    alignTimer = setTimeout(() => alignOutputPorts(), 300);
    if (baseEl.value) {
      let ticking = false;
      ro = new ResizeObserver(() => {
        if (ticking) return;
        ticking = true;
        requestAnimationFrame(() => {
          ticking = false;
          alignOutputPorts();
        });
      });
      ro.observe(baseEl.value);
    }
  });
  onBeforeUnmount(() => {
    if (ro) ro.disconnect();
    if (alignTimer) clearTimeout(alignTimer);
  });
</script>

<style scoped>
  .tips-text {
    width: 100%;
    background: #f4f4f4;
    padding: 5px 10px;
    border-radius: 5px;
    margin-top: 10px;
  }
  .branch-block {
    display: flex;
    flex-direction: column;
  }
  .branch-head {
    justify-content: space-between;
    font-size: 13px;
  }
  .logic {
    font-size: 12px;
    color: #888;
  }
  .tips-item {
    background: #fff;
    padding: 5px 10px;
    border-radius: 5px;
    font-size: 12px;
    margin-top: 5px;
  }
  .cond-text {
    color: #6172f3;
  }
</style>
