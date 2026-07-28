<template>
  <div class="wf-editor">
    <top-menu class="top-menu" @debug="debugHandle" @save="saveHandle" @back="backHandle" />

    <div
      ref="containerRef"
      class="container"
      @drop="onCanvasDrop"
      @dragover="onCanvasDragOver"
    ></div>

    <menu-box v-if="menuVisible" class="add-menu-box" @add-node="addNodeHandle" />

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
  import { ref, shallowRef, onMounted, onBeforeUnmount, defineAsyncComponent } from 'vue';
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
  import { getWorkflowInfo, saveWorkflow, editWorkflowMeta } from '@/api/system/workflow';

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
    console.log('[edit] initGraph 开始, container 尺寸:', {
      offsetWidth: containerRef.value?.offsetWidth,
      offsetHeight: containerRef.value?.offsetHeight,
      parentOffsetWidth: containerRef.value?.parentElement?.offsetWidth,
      parentOffsetHeight: containerRef.value?.parentElement?.offsetHeight,
      innerWidth: window.innerWidth,
      innerHeight: window.innerHeight,
    });
    const graph = new Graph({
      container: containerRef.value,
      selecting: true,
      history: true,
      panning: { enabled: true, eventTypes: ['leftMouseDown'] },
      interacting: { nodeMovable: true, edgeMovable: false },
      background: { color: '#f4f4f4' },
      grid: { visible: true },
      // autoResize: X6 用 SizeSensor 监听容器尺寸并自动 resize。
      // 开启 scroller 后，可视窗口尺寸由 scroller 管理，必须靠 autoResize 才能跟随容器，
      // 否则初始化后窗口大小写死，大屏下右侧大片画布不显示。
      autoResize: true,
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
      // 深拷贝：避免对话框直接 mutate 节点数据（配置抽屉持有同一引用会直接修改原对象），
      // 导致 updateData 时 prev 和新值内容相同（lodash isEqual 判无变化，不触发 change:data）
      formData.value = JSON.parse(JSON.stringify(node.getData()));
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

  // 画布尺寸自适应：X6 初始化时把容器尺寸固化进 inline style，之后不会跟随容器变化。
  // 配合 graph 配置里的 autoResize，这里再用一个显式的 ResizeObserver 监听真实可用空间
  // （.wf-editor，absolute inset:0 撑满内容区），在窗口/侧边栏变化时主动 resize 兜底，
  // 确保大屏下画布铺满、不再有右侧大片空白。
  let resizeObserver = null;
  function syncGraphSize(tag = '') {
    const g = graphRef.value;
    const cont = containerRef.value;
    const host = cont?.parentElement; // .wf-editor
    if (!g || !host) return;
    const w = host.offsetWidth;
    const h = host.offsetHeight;
    if (w > 0 && h > 0) {
      g.resize(w, h);
      // 关键修复：container 上有一道 max-width（= 初始化时的 clientWidth，如 1536px），
      // 把 graph.resize 写入的 width 死死卡住，导致大屏下画布右侧始终留白。
      // inline max-width:none 能压过任何来源的 max-width 限制。
      if (cont) {
        cont.style.maxWidth = 'none';
        cont.style.maxHeight = 'none';
      }
      const grid = cont?.querySelector('.x6-graph-grid');
      console.log(
        '[edit] syncGraphSize',
        tag,
        'resize',
        w,
        'x',
        h,
        '| containerOffsetW',
        cont?.offsetWidth,
        '| gridOffsetW',
        grid?.offsetWidth,
        '| computedMaxW',
        cont ? getComputedStyle(cont).maxWidth : null
      );
    }
  }
  function onWinResize() {
    syncGraphSize();
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

  // 动态右侧输出端口的同步与定位，全部在 dataChangeHandle 里完成。
  // purposeDialog/switchDialog 在增删分支时总是先 emit(portAdd/portDel/portUpdate)
  // 再 emit(dataChange)，所以端口重排只需挂在 dataChange 上即可覆盖所有分支增减场景。
  //
  // 端口语义（与后端一致）：
  //   - purpose：端口数 == cateList.length（无 else，FlowNodeParser 按端口 Y 排序成 targetList）
  //   - switch ：端口数 == ifBranch.length + 1（末位为 else，SwitchNode 把最后一条边当 else）
  function portAddHandle() {}
  function portUpdate() {}
  function portDelHandle() {}

  async function dataChangeHandle(val) {
    if (!nowNode.value) return;
    // val 已经是在 node:click 时深拷贝后的副本（非实时引用），
    // updateData 时 X6 store 会检测到与原数据的差异，触发 change:data → 节点重渲染。
    // 端口对齐由节点组件（switch.vue / purpose.vue）内部的 change:data 处理，无需在此调用。
    nowNode.value.updateData(val);
  }

  function getNodeInputData() {
    if (nowNode.value && graphRef.value) {
      inputOptions.value = inputDataUtil.getNodeInputData(nowNode.value, graphRef.value);
      console.log(
        '[edit] inputOptions 已赋值, 长度:',
        inputOptions.value?.length,
        '内容:',
        JSON.stringify(inputOptions.value)?.slice(0, 300)
      );
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
      JSON.parse(JSON.stringify(defaultNodeConfig[type + 'Node'](x, y, nodeNoData.value[type])))
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

  async function loadWorkflowInfo() {
    if (!workflowId) return;
    const res = await getWorkflowInfo(workflowId);
    if (res && res.code === 0 && res.data && res.data.flowData) {
      flowData.value = JSON.parse(res.data.flowData);
      graphRef.value.fromJSON(flowData.value);
      // fromJSON 后节点位置可能偏离可视区，主动居中
      requestAnimationFrame(() => {
        graphRef.value?.centerContent();
      });
      // 统计节点数
      nodeNoData.value = {};
      flowData.value.cells.forEach((node) => {
        if (node.shape !== 'edge') {
          const type = node.shape.split('-')[0];
          nodeNoData.value[type] = (nodeNoData.value[type] || 0) + 1;
        }
      });
      // 端口对齐由各节点组件（switch.vue / purpose.vue）在挂载和 change:data 时自行处理，
      // 此处无需额外调用 relayoutNodePorts。
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
      // 后端 message 可能是英文（如 "success"），统一展示中文提示
      if (!silent) message.success('保存成功');
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
    // 先按当前容器尺寸铺满，再加载流程数据（fromJSON 后再校正一次，避免被重置）
    syncGraphSize('onMounted-init');
    await loadWorkflowInfo();
    syncGraphSize('onMounted-afterLoad');
    // 监听容器尺寸变化：ResizeObserver 覆盖侧边栏折叠/容器 resize，
    // window resize 兜底（部分嵌入 webview 不向 ResizeObserver 派发视口变化）
    const host = containerRef.value?.parentElement;
    if (host && typeof ResizeObserver !== 'undefined') {
      resizeObserver = new ResizeObserver(() => syncGraphSize('ResizeObserver'));
      resizeObserver.observe(host);
    }
    window.addEventListener('resize', onWinResize);
    // 进入即调试：保存后打开聊天
    if (startDebug) {
      // 已经 chatVisible=true，无需额外处理
    }
  });

  onBeforeUnmount(() => {
    if (resizeObserver) {
      resizeObserver.disconnect();
      resizeObserver = null;
    }
    window.removeEventListener('resize', onWinResize);
  });
</script>

<style scoped>
  /* 全屏编辑器：absolute + inset:0 撑满定位祖先（.n-layout-content 内容区）。
     注意不要用 100vh——那会让画布脱离父级流、顶出滚动条；画布实际尺寸交给
     ResizeObserver 驱动 graph.resize 跟随 container 的真实宽高。 */
  .wf-editor {
    position: absolute;
    inset: 0;
    background: #f4f4f4;
    z-index: 1;
  }
  .container {
    width: 100%;
    height: 100%;
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

  /* 抵消 MainView 路由切换动画（fade-scale）。
     X6 在节点入场期间向 .container 写入 inline 宽高，配合全局 transition:all 会
     干扰 transitionend，导致 .wf-editor 卡在 enter-from（opacity:0、scale(1.2)），
     画布被放大 1.2 倍溢出视口 → 右侧大片空白。全屏编辑器无需该动画，直接强制还原。 */
  .wf-editor.fade-scale-enter-active,
  .wf-editor.fade-scale-enter-from,
  .wf-editor.fade-scale-enter-to,
  .wf-editor.fade-scale-leave-active,
  .wf-editor.fade-scale-leave-from,
  .wf-editor.fade-scale-leave-to {
    opacity: 1;
    transform: none;
    transition: none;
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
