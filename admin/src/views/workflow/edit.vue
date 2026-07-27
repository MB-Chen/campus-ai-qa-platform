<template>
  <div class="wf-editor">
    <top-menu
      class="top-menu"
      @debug="debugHandle"
      @save="saveHandle"
      @back="backHandle"
    />

    <div
      ref="containerRef"
      class="container"
      @drop="onCanvasDrop"
      @dragover="onCanvasDragOver"
    ></div>

    <menu-box
      v-if="menuVisible"
      class="add-menu-box"
      @add-node="addNodeHandle"
    />

    <n-modal
      v-model:show="runtimeVisible"
      preset="card"
      title="执行详情"
      style="width: 760px; max-width: 92vw"
      :close-on-esc="true"
    >
      <runtime-box :key="runtimeKey" :runtime-id="runtimeId" />
    </n-modal>

    <debug-chat
      v-if="chatVisible"
      :key="debugKey"
      :workflow-id="workflowId"
      @close-debug="chatVisible = false"
      @show-detail="showDetailHandle"
    />

    <bottom-menu
      :key="bottomKey"
      :out-open="menuVisible"
      class="bottom-menu"
      @open-menu="openMenuHandle"
      @center="centerHandle"
      @zoom-in="zoomInHandle"
      @zoom-out="zoomOutHandle"
    />

    <!-- 节点配置抽屉 -->
    <n-drawer v-model:show="drawer" :width="600" placement="right">
      <n-drawer-content :native-scrollbar="false" closable>
        <template v-if="nowNode && nowNode.shape !== 'start-node'">
          <n-button
            quaternary
            type="error"
            size="small"
            style="float: right; margin-bottom: 8px"
            @click="delNodeHandle"
          >
            删除节点
          </n-button>
        </template>
        <component
          :is="pageComp"
          :key="compKey"
          :form-data="formData"
          :input-options="inputOptions"
          @port-del="portDelHandle"
          @port-add="portAddHandle"
          @port-update="portUpdate"
          @data-change="dataChangeHandle"
        />
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<script setup>
  import { ref, shallowRef, onMounted, defineAsyncComponent } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import { useMessage } from 'naive-ui';
  import { Graph, Shape } from '@antv/x6';
  // import defaultNodeConfig 同时触发 X6 shape 注册（node.js 末尾的 register）
  import defaultNodeConfig from './node.js';
  import topMenu from './menu/topMenu.vue';
  import bottomMenu from './menu/bottomMenu.vue';
  import menuBox from './menu/menuBox.vue';
  import debugChat from './menu/debug.vue';
  import runtimeBox from './menu/runtime.vue';
  import inputDataUtil from './inputData.js';
  import nodeCheck from './nodeCheck.js';
  import {
    getWorkflowInfo,
    saveWorkflow,
    editWorkflowMeta,
  } from '@/api/system/workflow';

  const route = useRoute();
  const router = useRouter();
  const message = useMessage();

  const workflowId = String(route.query.id || '');
  const startDebug = route.query.debug === '1';

  const containerRef = ref(null);
  const menuVisible = ref(false);
  const drawer = ref(false);
  const chatVisible = ref(startDebug);
  const runtimeVisible = ref(false);
  const bottomKey = ref(Math.random());
  const debugKey = ref(Math.random());
  const compKey = ref(Math.random());
  const runtimeKey = ref(Math.random());
  const runtimeId = ref(0);

  const graphRef = shallowRef(null);
  const nowNode = shallowRef(null);
  const formData = ref({});
  const inputOptions = ref([]);
  const pageComp = shallowRef(null);
  const nodeNoData = ref({ purpose: 0, agent: 0, answer: 0, llm: 0, dataset: 0, switch: 0 });
  const flowData = ref(null);

  // 节点配置抽屉的动态组件映射
  const pageMap = {
    start: () => import('./dialog/startDialog.vue'),
    purpose: () => import('./dialog/purposeDialog.vue'),
    llm: () => import('./dialog/llmDialog.vue'),
    dataset: () => import('./dialog/datasetDialog.vue'),
    answer: () => import('./dialog/answerDialog.vue'),
    switch: () => import('./dialog/switchDialog.vue'),
    agent: () => import('./dialog/agentDialog.vue'),
  };

  // X6 端口显隐
  function setPortsVisible(visibility) {
    setTimeout(() => {
      const ports = document.querySelectorAll('.x6-port-body');
      for (let i = 0; i < ports.length; i++) {
        ports[i].style.visibility = visibility;
      }
    }, 100);
  }
  function resetSel() {
    const g = graphRef.value;
    if (!g) return;
    g.getNodes().forEach((n) => n.updateData({ checked: false }));
  }

  function initGraph() {
    const graph = new Graph({
      container: containerRef.value,
      selecting: true,
      history: true,
      panning: { enabled: true, eventTypes: ['leftMouseDown'] },
      interacting: { nodeMovable: true, edgeMovable: false },
      background: { color: '#f4f4f4' },
      grid: { visible: true },
      scroller: { enabled: true, pageVisible: true, pageBreak: true, pannable: true },
      connecting: {
        connector: 'smooth',
        snap: true,
        allowBlank: false,
        allowLoop: false,
        allowNode: false,
        createEdge() {
          return new Shape.Edge({
            attrs: {
              line: {
                stroke: '#d0d5dc',
                strokeWidth: 2,
                targetMarker: null,
                sourceMarker: null,
              },
            },
            tools: [],
          });
        },
        allowPort(arg) {
          const getPortType = (ports, portId) => {
            const p = ports.find((x) => x.id === portId);
            return p ? p.type : null;
          };
          const sourceType = getPortType(arg.sourceCell.port.ports, arg.sourcePort);
          const targetType = getPortType(arg.targetCell.port.ports, arg.targetPort);
          if (sourceType === 'input') return false;
          if (sourceType === targetType) return false;
          return true;
        },
      },
    });
    graphRef.value = graph;

    // 创建开始节点
    graph.addNode(defaultNodeConfig.startNode(100, 240));

    graph.on('node:mouseenter', () => setPortsVisible('visible'));
    graph.on('node:click', ({ node }) => {
      resetSel();
      nowNode.value = node;
      node.updateData({ checked: true });
      formData.value = node.getData();
      const pages = formData.value.pages;
      pageComp.value = pageMap[pages] ? defineAsyncComponent(pageMap[pages]) : null;
      if (pages !== 'start') {
        getNodeInputData();
      }
      compKey.value = Math.random();
      drawer.value = true;
    });
    graph.on('blank:click', () => {
      resetSel();
      menuVisible.value = false;
      nowNode.value = null;
      bottomKey.value = Math.random();
      setPortsVisible('hidden');
    });
    graph.on('node:mouseleave', () => {
      if (!nowNode.value) setPortsVisible('hidden');
    });
    graph.on('edge:mouseenter', ({ edge }) => {
      edge.addTools([{ name: 'button-remove' }]);
      edge.attr('line', { stroke: '#18a058', strokeWidth: 1 });
    });
    graph.on('edge:mouseleave', ({ edge }) => {
      edge.removeTools();
      edge.attr('line', { stroke: '#d0d5dc', strokeWidth: 2 });
    });
  }

  // 画布操作
  function centerHandle() {
    graphRef.value?.centerContent();
    graphRef.value?.zoom(0);
  }
  function zoomInHandle() {
    graphRef.value?.zoom(0.1);
  }
  function zoomOutHandle() {
    const num = Number(graphRef.value?.zoom().toFixed(1));
    if (num > 0.1) graphRef.value?.zoom(-0.1);
  }
  function openMenuHandle(v) {
    menuVisible.value = v;
  }

  // 动态端口（purpose/switch 多分支）。
  // 端口用 absolute 定位，需要随条件增减/节点内容变化重新排布 Y 坐标。
  // 统一做法：等节点 Vue 内容重渲染后，读节点实际高度，把右侧输出端口均匀分布到
  // 各分支标题行，最后一个 else 端口钉在节点底部，保证连出点始终跟着条件。
  const NODE_WIDTH = 230;

  function relayoutRightPorts(node, branchCount) {
    if (!node) return;
    // 等 Vue 重新渲染节点内容（DOM 高度变化）
    setTimeout(() => {
      // 节点内容高度：读实际 DOM（vue-shape 不自动撑高 node box，要手动 resize）
      const view = graphRef.value?.findViewByCell(node);
      const containerEl = view?.container;
      let h = node.getSize().height;
      if (containerEl) {
        // x6-vue-shape 把组件渲染到容器内第一个子节点
        const inner = containerEl.querySelector('.x6-node-shape, .node-base, [class*="node"]')
          || containerEl.firstElementChild;
        if (inner) {
          h = inner.scrollHeight || inner.offsetHeight || h;
        }
      }
      // 把节点 box 高度同步成内容高度，否则端口/边定位会偏
      node.resize(NODE_WIDTH, Math.max(h, 40));

      const n = branchCount + 1; // 含 else
      const startY = 28;
      const bottomPad = 16;
      const usable = Math.max(h - startY - bottomPad, n * 20);
      const step = n > 1 ? usable / (n - 1) : 0;

      // 当前 output 端口数补齐/裁剪到 n
      let outPorts = node.getPorts().filter((p) => p.type === 'output');
      while (outPorts.length < n) {
        node.addPort({ group: 'rightPorts', args: { x: NODE_WIDTH, y: startY }, type: 'output' });
        outPorts = node.getPorts().filter((p) => p.type === 'output');
      }
      while (outPorts.length > n) {
        node.removePortAt(node.getPorts().length - 1);
        outPorts = node.getPorts().filter((p) => p.type === 'output');
      }
      // 重新分布 Y
      outPorts.forEach((p, i) => {
        const y = n === 1 ? h - bottomPad : startY + step * i;
        node.port.ports.forEach((pp) => {
          if (pp.id === p.id) {
            pp.args = pp.args || {};
            pp.args.x = NODE_WIDTH;
            pp.args.y = y;
          }
        });
      });
      node.setPropByPath('ports/items', node.port.ports);
    }, 60);
  }

  function portAddHandle(val) {
    const node = nowNode.value;
    if (!node) return;
    if (val.type === 'purpose') {
      relayoutRightPorts(node, val.cateList.length);
    } else if (val.type === 'switch') {
      relayoutRightPorts(node, val.ifBranch.length);
    }
  }
  function portUpdate(val) {
    const node = nowNode.value;
    if (!node) return;
    if (val.type === 'switch') {
      relayoutRightPorts(node, val.ifBranch.length);
    } else if (val.type === 'purpose') {
      relayoutRightPorts(node, val.cateList.length);
    }
  }
  function portDelHandle() {
    const node = nowNode.value;
    if (!node) return;
    const outPorts = node.getPorts().filter((p) => p.type === 'output');
    if (outPorts.length) {
      // 删最后一个 output（else 或最后一个分支）
      node.removePortAt(node.getPorts().length - 1);
    }
    // 删后重新排布
    const data = node.store?.data?.data;
    if (data) {
      if (data.pages === 'switch' && data.ifBranch) {
        relayoutRightPorts(node, data.ifBranch.length);
      } else if (data.pages === 'purpose' && data.cateList) {
        relayoutRightPorts(node, data.cateList.length);
      }
    }
  }

  function dataChangeHandle(val) {
    nowNode.value?.updateData(val);
    // switch/purpose 条件增减导致节点高度变化，重排右侧端口跟随
    if (val && (val.type === 'switch' || val.pages === 'switch'
        || val.type === 'purpose' || val.pages === 'purpose')) {
      const node = nowNode.value;
      const data = node?.store?.data?.data;
      if (data?.pages === 'switch' && data.ifBranch) {
        relayoutRightPorts(node, data.ifBranch.length);
      } else if (data?.pages === 'purpose' && data.cateList) {
        relayoutRightPorts(node, data.cateList.length);
      }
    }
  }

  function getNodeInputData() {
    if (nowNode.value && graphRef.value) {
      inputOptions.value = inputDataUtil.getNodeInputData(nowNode.value, graphRef.value);
    }
  }

  /**
   * 添加节点。
   * @param type     节点类型（llm/dataset/...）
   * @param pos      可选，画布坐标 {x,y}（拖拽放置时为鼠标落点）；缺省则居中放置
   */
  function addNodeHandle(type, pos) {
    if (isNaN(nodeNoData.value[type])) {
      nodeNoData.value[type] = 1;
    } else {
      nodeNoData.value[type] += 1;
    }
    // 落点：拖拽用鼠标坐标，否则居中（带轻微随机偏移避免完全重叠）
    let x, y;
    if (pos && typeof pos.x === 'number' && typeof pos.y === 'number') {
      x = pos.x;
      y = pos.y;
    } else {
      const c = graphRef.value.getContentArea();
      const z = graphRef.value.zoom();
      x = (c.x + c.width / 2) / z;
      y = (c.y + c.height / 2) / z;
      x += (Math.random() - 0.5) * 60;
      y += (Math.random() - 0.5) * 60;
    }
    graphRef.value.addNode(
      JSON.parse(
        JSON.stringify(
          defaultNodeConfig[type + 'Node'](x, y, nodeNoData.value[type]),
        ),
      ),
    );
  }

  // 拖拽放置：menuBox 的节点项 draggable，在画布上 drop 时按鼠标坐标放置
  function onCanvasDragOver(e) {
    if (e.dataTransfer) {
      e.dataTransfer.dropEffect = 'copy';
    }
    e.preventDefault();
  }
  function onCanvasDrop(e) {
    const type = e.dataTransfer && e.dataTransfer.getData('application/x-workflow-node');
    if (!type) return;
    e.preventDefault();
    // 鼠标客户端坐标 → 画布逻辑坐标
    const g = graphRef.value;
    if (!g) return;
    const point = g.clientToLocal({ x: e.clientX, y: e.clientY });
    addNodeHandle(type, { x: point.x, y: point.y });
    // 拖放后收起节点面板
    menuVisible.value = false;
  }

  function delNodeHandle() {
    if (!nowNode.value) return;
    graphRef.value.removeNode(nowNode.value.id);
    drawer.value = false;
  }

  // 调试前先保存
  async function debugHandle() {
    const checkRes = nodeCheck.check(graphRef.value.toJSON());
    if (checkRes.code !== 0) {
      message.error(checkRes.msg);
      return;
    }
    await saveHandle(true);
    debugKey.value = Math.random();
    chatVisible.value = true;
  }

  function showDetailHandle(rid) {
    runtimeKey.value = Math.random();
    runtimeId.value = rid;
    runtimeVisible.value = true;
  }

  async function getWorkflowInfo() {
    if (!workflowId) return;
    const res = await getWorkflowInfo(workflowId);
    if (res && res.code === 0 && res.data && res.data.flowData) {
      flowData.value = JSON.parse(res.data.flowData);
      graphRef.value.fromJSON(flowData.value);
      // 统计节点数
      nodeNoData.value = {};
      flowData.value.cells.forEach((node) => {
        if (node.shape !== 'edge') {
          const type = node.shape.split('-')[0];
          nodeNoData.value[type] = (nodeNoData.value[type] || 0) + 1;
        }
      });
      // 加载后重排 switch/purpose 节点右侧端口（确保连出点跟随条件，Q2）
      setTimeout(() => {
        graphRef.value.getNodes().forEach((node) => {
          const data = node.getData();
          if (data && data.pages === 'switch' && data.ifBranch) {
            relayoutRightPorts(node, data.ifBranch.length);
          } else if (data && data.pages === 'purpose' && data.cateList) {
            relayoutRightPorts(node, data.cateList.length);
          }
        });
      }, 100);
    }
  }

  async function saveHandle(silent = false) {
    const checkRes = nodeCheck.check(graphRef.value.toJSON());
    if (checkRes.code !== 0) {
      message.error(checkRes.msg);
      return false;
    }
    const res = await saveWorkflow({
      id: workflowId,
      flowData: JSON.stringify(graphRef.value.toJSON()),
    });
    if (res && res.code === 0) {
      if (!silent) message.success(res.message || '保存成功');
      return true;
    } else {
      message.error(res?.message || '保存失败');
      return false;
    }
  }

  function backHandle() {
    router.push('/workflow/index').catch(() => {});
  }

  onMounted(async () => {
    initGraph();
    await getWorkflowInfo();
    // 进入即调试：保存后打开聊天
    if (startDebug) {
      // 已经 chatVisible=true，无需额外处理
    }
  });
</script>

<style scoped>
  .wf-editor {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100vh;
    background: #f4f4f4;
    z-index: 1;
  }
  .container {
    width: 100%;
    height: 100vh;
  }
  .top-menu {
    position: absolute;
    top: 0;
    right: 0;
    z-index: 999;
  }
  .bottom-menu {
    position: absolute;
    bottom: 20px;
    left: 20px;
    cursor: pointer;
    z-index: 998;
  }
  .add-menu-box {
    position: absolute;
    bottom: 70px;
    left: 100px;
    z-index: 998;
  }
</style>

<!-- 全局样式：X6 vue-shape 把节点组件渲染到画布 DOM（脱离本组件 scoped 作用域），
     所以节点内部用到的 class 必须用非 scoped 的全局样式定义。
     主题色用 Naive UI 默认绿 #18a058 替代原 element-plus 的 --el-color-theme。 -->
<style>
  .flex-center {
    display: flex;
    align-items: center;
  }
  .flex-center-all {
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .line1 {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .node-base {
    display: flex;
    flex-direction: column;
    width: 230px;
    min-height: 40px;
    padding: 10px;
    border: 1px solid #f4f4f4;
    border-radius: 12px;
    box-shadow: 0 1px 2px 0 rgba(16, 24, 40, 0.05);
    background: #fff;
    box-sizing: border-box;
  }
  .node-active {
    border: 1px solid #18a058;
    box-shadow: 0 0 0 2px rgba(24, 160, 88, 0.15);
  }
  .node-name {
    margin-left: 10px;
    font-weight: bold;
    font-size: 14px;
  }
</style>
