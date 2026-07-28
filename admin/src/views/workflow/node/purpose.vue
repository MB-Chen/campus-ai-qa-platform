<!-- 意图分类节点：显示模型名 + 分类清单 -->
<template>
  <div class="node-base" :class="{ 'node-active': active }" ref="baseEl">
    <div class="flex-center">
      <n-icon :component="meta.icon" :color="meta.color" :size="18" />
      <span class="node-name" v-if="no === 1">{{ meta.name }}</span>
      <span class="node-name" v-else>{{ meta.name }}{{ no - 1 }}</span>
    </div>

    <div class="flex-center tips-text">
      <div class="menu-icon" :style="{ background: meta.color }">
        <n-icon :component="meta.icon" color="#fff" :size="14" />
      </div>
      <span
        v-if="nodeInnerData.modelInfo && nodeInnerData.modelInfo.modelName"
        class="line1 model-name"
        >{{ nodeInnerData.modelInfo.modelName }}</span
      >
      <span v-else class="line1 model-name placeholder">请设置语言模型</span>
    </div>

    <div
      class="flex-center tips-text cate-item"
      v-for="(item, index) in nodeInnerData.cateList"
      :key="index"
      :data-port="index"
    >
      {{ item.name }}
    </div>
  </div>
</template>

<script setup>
  import { ref, inject, nextTick, onMounted, onBeforeUnmount } from 'vue';
  import { iconComponent } from '@/views/workflow/icons/index.js';

  const getNode = inject('getNode');
  const meta = iconComponent('purpose-node');

  const nodeData = getNode().getData();
  const no = ref(nodeData.no || 1);
  const nodeInnerData = ref(nodeData);
  const active = ref(false);
  const baseEl = ref(null);

  const NODE_WIDTH = 230;
  // 端口视觉微调：整体下移像素数（正值=下移）
  const PORT_Y_OFFSET = 6;

  /**
   * 对齐右侧输出端口到各分类行。
   * 用 getBoundingClientRect 测量真实视口坐标差值，除以缩放比还原为节点逻辑坐标。
   */
  function alignOutputPorts() {
    const node = getNode();
    const el = baseEl.value;
    if (!el || !node) return;

    nextTick(() => {
      scheduleAlign(node, el, 8);
    });
  }

  function scheduleAlign(node, el, retries) {
    if (retries <= 0) return;
    requestAnimationFrame(() => {
      doAlignPorts(node, el);
      // DOM 数量不匹配时继续重试（新增/删除分类后）
      const data = node.getData();
      const expected = (data.cateList || []).length;
      const found = el.querySelectorAll('.cate-item').length;
      if (found !== expected && retries > 1) {
        scheduleAlign(node, el, retries - 1);
      }
    });
  }

  /**
   * 同步输出端口数量 == cateList.length。
   * 新建节点初始只有 1 个端口；用户增删分类后需要动态补齐/裁剪。
   */
  function syncOutputPorts(node) {
    const data = node.getData();
    const expected = (data.cateList || []).length;
    if (expected <= 0) return;

    const outputPorts = node.getPorts().filter((p) => p.type === 'output');
    const current = outputPorts.length;

    if (current < expected) {
      // 端口不足 → 补齐
      for (let i = current; i < expected; i++) {
        node.addPort({
          group: 'rightPorts',
          args: { x: NODE_WIDTH, y: 100 + i * 46 }, // 临时位置，随后 doAlignPorts 精调
          type: 'output',
          id: `out-${i}`,
        });
      }
    } else if (current > expected) {
      // 端口过多 → 从末尾移除
      for (let i = current - 1; i >= expected; i--) {
        const port = outputPorts[i];
        if (port) node.removePort(port.id);
      }
    }
  }

  function doAlignPorts(node, el) {
    const cateItems = el.querySelectorAll('.cate-item');
    if (!cateItems.length) return;

    // .x6-node 是端口 absolute 坐标的原点
    const x6Node = el.closest('.x6-node') || el;
    const nodeRect = x6Node.getBoundingClientRect();

    // 缩放校正
    const scale = nodeRect.width / NODE_WIDTH || 1;

    // 同步节点高度
    node.resize(NODE_WIDTH, Math.round(nodeRect.height / scale));

    const outputPorts = node.getPorts().filter((p) => p.type === 'output');
    let outIdx = 0;

    for (const port of outputPorts) {
      if (outIdx >= cateItems.length) break;
      const targetEl = cateItems[outIdx];
      const targetRect = targetEl.getBoundingClientRect();
      const y = Math.round(
        (targetRect.top - nodeRect.top + targetRect.height / 2) / scale + PORT_Y_OFFSET
      );
      node.portProp(port.id, 'args', { x: NODE_WIDTH, y });
      outIdx++;
    }

    // 强制刷新
    const ports = node.getPorts();
    node.setPropByPath('ports/items', [...ports]);
  }

  const node = getNode();
  node.on('change:data', ({ current }) => {
    active.value = current.checked;
    nodeInnerData.value = current;
    // 先同步端口数量（增删分类后端口数必须 == cateList.length），再对齐位置
    syncOutputPorts(node);
    alignOutputPorts();
  });

  // 挂载即对齐 + 延迟重对齐（解决拖拽新建时偏移）+ ResizeObserver
  let ro = null;
  let alignTimer = null;
  onMounted(() => {
    // 挂载时先保证端口数量正确（历史数据/初始 1 个端口的情况）
    syncOutputPorts(node);
    alignOutputPorts();
    // 拖拽新建时画布可能还没稳定，延迟 300ms 再对齐一次
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
    height: 30px;
    background: #f4f4f4;
    padding: 5px 10px;
    border-radius: 5px;
    margin-top: 10px;
  }
  .cate-item {
    height: auto;
    min-height: 26px;
    font-size: 12px;
  }
  .menu-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 22px;
    height: 22px;
    color: #fff;
    border-radius: 5px;
    flex-shrink: 0;
  }
  .model-name {
    font-size: 13px;
    margin-left: 5px;
  }
  .placeholder {
    color: #999;
  }
</style>
