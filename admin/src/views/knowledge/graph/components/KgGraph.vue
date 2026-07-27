<template>
  <!-- 知识图谱可视化（antv/g6 v5）。封装 g6 渲染、布局、主题、聚焦交互。
       fullscreen=true 时撑满父级高度做真全屏，false 时按视口剩余高度动态撑高内嵌 tab。 -->
  <div class="kg-graph" ref="rootRef" :class="{ 'kg-graph--fullscreen': fullscreen }">
    <!-- 图谱画布容器 -->
    <div ref="containerRef" class="kg-graph__canvas" />

    <!-- 左上角浮动工具栏（玻璃风） -->
    <div class="kg-graph__toolbar">
      <div class="kg-graph__panel">
        <!-- 主题切换 -->
        <button
          class="kg-graph__icon-btn"
          :title="'视觉主题：' + themeLabel"
          @click="cycleTheme"
        >
          <span class="kg-graph__theme-dot" :style="{ background: themeDotColor }" />
          <span class="kg-graph__icon-text">{{ themeLabel }}</span>
        </button>

        <!-- 布局切换 -->
        <button class="kg-graph__icon-btn" title="切换布局" @click="cycleLayout">
          <span class="kg-graph__icon-text">{{ layoutLabel }}</span>
        </button>

        <!-- 分隔 -->
        <span class="kg-graph__divider" />

        <!-- 缩放 -->
        <button class="kg-graph__icon-btn" title="缩小" @click="zoomOut">−</button>
        <span class="kg-graph__zoom-pct">{{ zoomPct }}%</span>
        <button class="kg-graph__icon-btn" title="放大" @click="zoomIn">＋</button>
        <button class="kg-graph__icon-btn" title="重置缩放" @click="zoomReset">⤢</button>

        <!-- 分隔 -->
        <span class="kg-graph__divider" />

        <!-- 统计 -->
        <span class="kg-graph__stats" v-if="view">
          {{ view.nodes.length }} 节点 · {{ view.edges.length }} 关系
        </span>
      </div>
    </div>

    <!-- 类型图例（可收起，避免遮挡图谱） -->
    <div class="kg-graph__legend" v-if="usedTypes.length > 0">
      <div class="kg-graph__legend-head">
        <span class="kg-graph__legend-title">类型</span>
        <button
          class="kg-graph__legend-toggle"
          :title="legendCollapsed ? '展开图例' : '收起图例'"
          @click="legendCollapsed = !legendCollapsed"
        >
          {{ legendCollapsed ? '展开' : '收起' }}
        </button>
      </div>
      <div class="kg-graph__legend-items" v-show="!legendCollapsed">
        <span
          v-for="t in usedTypes"
          :key="t"
          class="kg-graph__legend-item"
          :style="{ '--c': typeColor(t).fill }"
        >
          {{ t || '未分类' }}
        </span>
      </div>
    </div>

    <!-- loading / empty / error 遮罩 -->
    <div class="kg-graph__overlay" v-if="loading || errorMsg || isEmpty">
      <n-spin v-if="loading" size="large" />
      <n-empty v-else-if="errorMsg" :description="errorMsg" />
      <n-empty v-else :description="props.documentId ? '该文档暂无图谱数据，请先抽取' : '该知识库暂无图谱数据，请先抽取'" />
    </div>
  </div>
</template>

<script setup lang="ts">
  import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue';
  import { NSpin, NEmpty } from 'naive-ui';
  import { getKgVisualization } from '@/api/system/knowledgeGraph';

  const props = withDefaults(
    defineProps<{
      kbId: string;
      documentId?: string; // 非空时只查该文档贡献的子图（文档级可视化）
      fullscreen?: boolean; // true=撑满父级做真全屏；false=内嵌固定高度
    }>(),
    { fullscreen: false }
  );
  const emit = defineEmits<{
    (e: 'node-click', node: { id: string; label: string; type: string; description?: string } | null): void;
  }>();

  // 实体类型配色板：按类型首次出现顺序分配，超出后循环取色
  const TYPE_PALETTE = [
    '#5B8FF9', '#61DDAA', '#F6BD16', '#7262FD', '#78D3F8',
    '#F08BB4', '#FF9845', '#9661BC', '#269A99', '#D96D6C',
  ];
  const FALLBACK_COLOR = '#94A3B8';
  // 概览时常显标签的目标数量：按度数取前 ~45 个高连接实体常显，密集图自动抬高门槛
  const LABEL_BUDGET = 45;
  // 边「淡网」：低透明度冷灰，让线退成节点背后的网
  const EDGE_STROKE = '#CBD5E1';
  const EDGE_OPACITY = 0.34;
  // 取景过扫倍数：在「全部可见」贴合比例上再放大，把空角与稀疏边缘裁出画外、让主体铺满整帧
  const FIT_OVERSCAN = 1.3;

  type VizTheme = 'pastel' | 'outline' | 'glass' | 'vivid';
  type LayoutType = 'd3-force' | 'concentric';

  // 后端 /visualization 返回结构（nodes 用 label，edges 用 from/to）
  interface RawNode { id: string; label: string; type?: string; description?: string }
  interface RawEdge { from: string; to: string; type?: string }
  // 统一内部结构（对齐 ragent GraphView）
  interface GNode { id: string; name: string; type: string; description: string }
  interface GEdge { id: string; source: string; target: string; label: string }

  const containerRef = ref<HTMLElement | null>(null);
  const rootRef = ref<HTMLElement | null>(null);
  const legendCollapsed = ref(true);
  const loading = ref(false);
  const errorMsg = ref<string | null>(null);
  const view = ref<{ nodes: GNode[]; edges: GEdge[] } | null>(null);
  const zoomPct = ref(100);

  // 设置状态（模块级单例 + reactive 双写：单例供 g6 空依赖闭包读取，reactive 供模板渲染）
  const theme = ref<VizTheme>('outline');
  const layout = ref<LayoutType>('d3-force');
  let graph: any = null;
  // 聚焦节点 id（用闭包变量供 g6 事件回调读取，实现再次点击同一节点即取消聚焦）
  let focusedId = '';
  // 聚焦相机快照（renderGraph 内填）
  let savedView: { zoom: number; center: [number, number] } | null = null;
  // ★ 卸载标志：renderGraph 是异步链（await import g6 + graph.render），
  //   组件常在 await 期间被卸载（点侧栏菜单走 SPA 路由离开）。若不阻断，
  //   await 返回后仍会 new G6Graph 并 render，产生的 canvas 再无人销毁，
  //   残留盖住后续所有页面（点任何菜单都空白）。onBeforeUnmount 置位后，
  //   renderGraph 各 await/rAF 恢复处立即 return，杜绝卸载后创建 graph。
  let destroyed = false;

  /** #RRGGBB → rgba(r,g,b,a)，用于节点同色光晕 */
  function withAlpha(hex: string, alpha: number): string {
    const m = /^#?([0-9a-fA-F]{6})$/.exec(hex.trim());
    if (!m) return hex;
    const i = parseInt(m[1], 16);
    return `rgba(${(i >> 16) & 255}, ${(i >> 8) & 255}, ${i & 255}, ${alpha})`;
  }

  /** 两个 #RRGGBB 线性混合，返回 #rrggbb；非法输入回退 a。用于主题提亮粉彩 / 描边近白填充 */
  function mixHex(a: string, b: string, t: number): string {
    const pa = /^#?([0-9a-fA-F]{6})$/.exec(a.trim());
    const pb = /^#?([0-9a-fA-F]{6})$/.exec(b.trim());
    if (!pa || !pb) return a;
    const ia = parseInt(pa[1], 16);
    const ib = parseInt(pb[1], 16);
    const r = Math.round(((ia >> 16) & 255) + (((ib >> 16) & 255) - ((ia >> 16) & 255)) * t);
    const g = Math.round(((ia >> 8) & 255) + (((ib >> 8) & 255) - ((ia >> 8) & 255)) * t);
    const b2 = Math.round((ia & 255) + ((ib & 255) - (ia & 255)) * t);
    return `#${((1 << 24) | (r << 16) | (g << 8) | b2).toString(16).slice(1)}`;
  }

  /** 依据类型集合构建 类型→颜色 映射，无类型归入「其他」用灰色 */
  function buildTypeColors(nodes: GNode[]): Record<string, string> {
    const colors: Record<string, string> = {};
    let index = 0;
    for (const node of nodes) {
      const t = (node.type || '').trim();
      if (!t) continue;
      if (!colors[t]) {
        colors[t] = TYPE_PALETTE[index % TYPE_PALETTE.length];
        index += 1;
      }
    }
    return colors;
  }

  /** 类型→颜色映射（响应式，由 view 派生，避免图例读不到 renderGraph 内才赋值的非响应式变量而全灰） */
  const typeColorMap = computed<Record<string, string>>(() => buildTypeColors(view.value?.nodes ?? []));

  /** 模板图例用的稳定取色（按类型哈希） */
  function typeColor(type?: string): { fill: string } {
    if (!type) return { fill: FALLBACK_COLOR };
    return { fill: typeColorMap.value[type] || FALLBACK_COLOR };
  }

  const usedTypes = computed(() => {
    if (!view.value) return [];
    const set = new Set<string>();
    view.value.nodes.forEach((n) => set.add(n.type || ''));
    return Array.from(set);
  });

  /**
   * 主题化节点完整样式：一次给出 尺寸 / 填充 / 填充透明度 / 描边 / 描边宽 / 光晕。
   * 非 vivid 主题统一把叶子缩小拉层级、腾出留白；各主题再各自弱化色块（提亮 / 描边 / 半透明）与光晕。
   */
  function themeNodeStyle(t: VizTheme, baseColor: string, degree: number) {
    const d = Math.min(degree, 12);
    const isHub = degree >= 6;
    if (t === 'vivid') {
      return {
        size: 24 + d * 3,
        fill: baseColor,
        fillOpacity: 1,
        stroke: '#ffffff',
        lineWidth: isHub ? 2 : 1.5,
        shadowColor: withAlpha(baseColor, 0.45),
        shadowBlur: 16,
      };
    }
    const size = 13 + d * 3;
    if (t === 'outline') {
      return {
        size,
        fill: mixHex(baseColor, '#ffffff', 0.86),
        fillOpacity: 1,
        stroke: baseColor,
        lineWidth: isHub ? 2.5 : 1.8,
        shadowColor: withAlpha(baseColor, 0.14),
        shadowBlur: 4,
      };
    }
    if (t === 'glass') {
      return {
        size,
        fill: baseColor,
        fillOpacity: 0.5,
        stroke: '#ffffff',
        lineWidth: 1.25,
        shadowColor: withAlpha(baseColor, 0.3),
        shadowBlur: 12,
      };
    }
    // pastel 柔彩：向白提亮成粉彩
    const soft = mixHex(baseColor, '#ffffff', 0.5);
    return {
      size,
      fill: soft,
      fillOpacity: 1,
      stroke: '#ffffff',
      lineWidth: isHub ? 2 : 1.5,
      shadowColor: withAlpha(soft, 0.34),
      shadowBlur: 10,
    };
  }

  /** 进入/重载时的取景：在「保持节点当前大小」前提下把图铺满视口，并主动过扫裁掉四角空白 */
  async function fitAllNodes(g: any, animate: boolean): Promise<void> {
    const nodes = g.getNodeData();
    let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
    for (const node of nodes) {
      const pos = g.getElementPosition(String(node.id));
      const x = Number(pos[0]);
      const y = Number(pos[1]);
      if (!Number.isFinite(x) || !Number.isFinite(y)) continue;
      minX = Math.min(minX, x);
      minY = Math.min(minY, y);
      maxX = Math.max(maxX, x);
      maxY = Math.max(maxY, y);
    }
    if (!Number.isFinite(minX)) return g.fitView();
    const cx = (minX + maxX) / 2;
    const cy = (minY + maxY) / 2;
    const [vw, vh] = g.getSize();
    const padding = 16;
    const contain = Math.min(
      (vw - padding * 2) / Math.max(maxX - minX, 1),
      (vh - padding * 2) / Math.max(maxY - minY, 1)
    );
    const zoom = Math.max(Math.min(contain * FIT_OVERSCAN, 1.0), 0.68);
    return g.zoomTo(zoom, animate).then(() => {
      const p = g.getViewportByCanvas([cx, cy]);
      return g.translateBy([vw / 2 - Number(p[0]), vh / 2 - Number(p[1])], animate);
    });
  }

  // 方形化强度：0=不动（圆团），1=贴合成方块。取 0.55 得「超椭圆」观感
  const RESHAPE_SQUARENESS = 0.55;

  /**
   * 力导收敛成圆团，放进矩形视口四角恒空。收敛后两步重塑：
   * ① 若团偏高，按视口宽高比做「保面积」各向异性拉伸；
   * ② 角向「方形化」：把每个点按其方位角朝所在象限的角落外推，圆团→圆角方形，节点铺进四角。
   * 仅用于力导，径向布局保持其同心圆形态。
   */
  async function reshapeToViewport(g: any) {
    const nodes = g.getNodeData();
    if (nodes.length < 3) return;
    let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
    const pts: Array<{ id: string; x: number; y: number }> = [];
    for (const node of nodes) {
      const id = String(node.id);
      const pos = g.getElementPosition(id);
      const x = Number(pos[0]);
      const y = Number(pos[1]);
      if (!Number.isFinite(x) || !Number.isFinite(y)) continue;
      pts.push({ id, x, y });
      minX = Math.min(minX, x);
      minY = Math.min(minY, y);
      maxX = Math.max(maxX, x);
      maxY = Math.max(maxY, y);
    }
    const spanX = maxX - minX;
    const spanY = maxY - minY;
    if (!Number.isFinite(spanX) || spanX < 1 || spanY < 1) return;
    const [vw, vh] = g.getSize();
    const targetAspect = vw / vh;
    const currentAspect = spanX / spanY;
    let sx = 1, sy = 1;
    if (currentAspect < targetAspect) {
      sx = Math.min(Math.sqrt(targetAspect / currentAspect), 1.6);
      sy = 1 / sx;
    }
    const cx = (minX + maxX) / 2;
    const cy = (minY + maxY) / 2;
    const halfX = (spanX * sx) / 2;
    const halfY = (spanY * sy) / 2;
    const positions: Record<string, [number, number]> = {};
    for (const p of pts) {
      let dx = (p.x - cx) * sx;
      let dy = (p.y - cy) * sy;
      const nx = dx / halfX;
      const ny = dy / halfY;
      const cheb = Math.max(Math.abs(nx), Math.abs(ny));
      if (cheb > 1e-6) {
        const boost = 1 + RESHAPE_SQUARENESS * (Math.hypot(nx, ny) / cheb - 1);
        dx *= boost;
        dy *= boost;
      }
      positions[p.id] = [cx + dx, cy + dy];
    }
    await g.translateElementTo(positions, false);
  }

  /** 居中并缩放到给定节点集，实现「居中放大」式聚焦 */
  function zoomToNodes(g: any, ids: string[], animate: boolean, maxZoom = 1.8) {
    if (ids.length === 0) return;
    const pts: Array<[number, number]> = [];
    for (const id of ids) {
      const pos = g.getElementPosition(id);
      const x = Number(pos[0]);
      const y = Number(pos[1]);
      if (Number.isFinite(x) && Number.isFinite(y)) pts.push([x, y]);
    }
    if (pts.length === 0) {
      void g.focusElement(ids, animate);
      return;
    }
    let cx = 0, cy = 0;
    for (const [x, y] of pts) { cx += x; cy += y; }
    cx /= pts.length;
    cy /= pts.length;
    const dists = pts.map(([x, y]) => Math.hypot(x - cx, y - cy)).sort((a, b) => a - b);
    const radius = pts.length <= 5 ? dists[dists.length - 1] : dists[Math.floor(dists.length * 0.85)];
    const span = Math.max(radius * 2, 1);
    const [vw, vh] = g.getSize();
    const padding = 120;
    const raw = Math.min((vw - padding * 2) / span, (vh - padding * 2) / span);
    const zoom = Math.max(Math.min(raw, maxZoom), 0.2);
    void g.zoomTo(zoom, animate).then(() => {
      const p = g.getViewportByCanvas([cx, cy]);
      return g.translateBy([vw / 2 - Number(p[0]), vh / 2 - Number(p[1])], animate);
    });
  }

  /** graph 是否已就绪可安全调用相机相关 API（getZoom/getCanvasByViewport 等在画布未 ready 时会抛错） */
  function isGraphReady(g: any): boolean {
    if (!g) return false;
    try {
      return typeof g.getZoom === 'function' && g.getZoom() !== undefined;
    } catch {
      return false;
    }
  }

  /** 点击聚焦：淡出与该节点无关的元素，把该节点及其邻居居中放大 */
  function applyFocus(g: any, id: string) {
    // 防御：画布未就绪（布局未完成/初始化中）时跳过，避免 getZoom 报 Cannot read properties of undefined
    if (!isGraphReady(g)) return;
    if (savedView === null) {
      const [w, h] = g.getSize();
      savedView = { zoom: g.getZoom(), center: g.getCanvasByViewport([w / 2, h / 2]) };
    }
    const neighborIds = g.getNeighborNodesData(id).map((n: any) => String(n.id));
    const keepNodes = new Set<string>([id, ...neighborIds]);
    const keepEdges = new Set(g.getRelatedEdgesData(id).map((e: any) => String(e.id)));
    const states: Record<string, string[]> = {};
    for (const node of g.getNodeData()) {
      states[String(node.id)] = keepNodes.has(String(node.id)) ? ['related'] : ['inactive'];
    }
    for (const edge of g.getEdgeData()) {
      states[String(edge.id)] = keepEdges.has(String(edge.id)) ? ['flow'] : ['inactive'];
    }
    void g.setElementState(states);
    zoomToNodes(g, [id, ...neighborIds], true);
  }

  /** 散焦：清空淡出状态，并把相机还原到聚焦前的快照 */
  function clearFocus(g: any) {
    if (!isGraphReady(g)) {
      // 画布未就绪：只清状态标记，不做相机操作
      savedView = null;
      return;
    }
    try {
      const states: Record<string, string[]> = {};
      for (const node of g.getNodeData()) states[String(node.id)] = [];
      for (const edge of g.getEdgeData()) states[String(edge.id)] = [];
      void g.setElementState(states);
      if (savedView) {
        const v = savedView;
        savedView = null;
        const [w, h] = g.getSize();
        void g.zoomTo(v.zoom, true).then(() => {
          const p = g.getViewportByCanvas(v.center);
          return g.translateBy([w / 2 - p[0], h / 2 - p[1]], true);
        });
      }
    } catch {
      // 防御：图已销毁等异常，静默
    }
  }

  async function loadGraph() {
    if (!props.kbId) return;
    loading.value = true;
    errorMsg.value = null;
    try {
      const res: any = await getKgVisualization(props.kbId, props.documentId);
      if (res && res.code === 0 && res.data) {
        // 字段映射：后端 label→name、from/to→source/target
        const rawNodes: RawNode[] = res.data.nodes || [];
        const rawEdges: RawEdge[] = res.data.edges || [];
        const nodes: GNode[] = rawNodes.map((n) => ({
          id: n.id,
          name: n.label || n.id,
          type: n.type || '',
          description: n.description || '',
        }));
        const edges: GEdge[] = rawEdges
          .filter((e) => e.from && e.to)
          .map((e, i) => ({
            id: `e_${i}_${e.from}_${e.to}`,
            source: e.from,
            target: e.to,
            label: e.type || '',
          }));
        view.value = { nodes, edges };
        await nextTick();
        renderGraph();
      } else {
        errorMsg.value = res?.message || '加载失败';
        view.value = null;
        destroyGraph();
      }
    } catch (e: any) {
      errorMsg.value = e?.message || '加载失败';
      view.value = null;
      destroyGraph();
    } finally {
      loading.value = false;
    }
  }

  const isEmpty = computed(() => {
    return !loading.value && !errorMsg.value && view.value !== null && view.value.nodes.length === 0;
  });

  async function renderGraph() {
    const container = containerRef.value;
    if (!container || !view.value || view.value.nodes.length === 0) return;

    // ★ 容器尺寸为 0 时（fullscreen 模式下父级高度未算好）等待下一帧重试，
    // 否则 g6 以 0 尺寸初始化会导致布局全挤在原点、无法取景。
    const rect = container.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) {
      await nextTick();
      // 卸载后停止重试循环，避免 rAF 在组件销毁后仍调度回来创建 graph
      if (destroyed) return;
      requestAnimationFrame(renderGraph);
      return;
    }

    // 动态加载 g6，避免阻塞首屏
    const G6 = await import('@antv/g6');
    // ★ 卸载竞态防护：组件常在 await import(g6) 期间被卸载（点侧栏菜单走 SPA 路由离开）。
    //   若 await 返回后仍继续 new G6Graph + render，canvas 再无人销毁，会残留盖住后续页面。
    if (destroyed) return;
    const { Graph: G6Graph, CanvasEvent, GraphEvent, NodeEvent } = G6;

    destroyGraph();

    const data = view.value;

    // 节点度数用于尺寸映射
    const degree: Record<string, number> = {};
    for (const edge of data.edges) {
      degree[edge.source] = (degree[edge.source] || 0) + 1;
      degree[edge.target] = (degree[edge.target] || 0) + 1;
    }
    // 碰撞半径按度数：视觉半径 + 呼吸间距，度数越高留白越多
    const radiusById: Record<string, number> = {};
    for (const node of data.nodes) {
      radiusById[node.id] = 12 + Math.min(degree[node.id] || 0, 12) * 1.5 + 22;
    }
    // 标签分级门槛：度数达门槛的实体才常显标签，概览常显标签控制在 ~45 个。
    // ★ 兜底：若最高度数都 < 1（如 edges 为空、全孤立点），门槛设为 0 让所有节点都显标签，
    // 否则会出现"全是点、旁边没字"的情况。
    const sortedDegrees = data.nodes.map((n) => degree[n.id] || 0).sort((a, b) => b - a);
    const maxDegree = sortedDegrees[0] || 0;
    const labelMinDegree =
      sortedDegrees.length > LABEL_BUDGET && maxDegree >= 1
        ? Math.max(sortedDegrees[LABEL_BUDGET], 1)
        : 0;

    // 节点 id→名称映射，供边悬浮展示「实体A → 实体B」
    const nameById: Record<string, string> = {};
    for (const node of data.nodes) nameById[node.id] = node.name;

    const nodes = data.nodes.map((node) => ({
      id: node.id,
      data: {
        name: node.name,
        type: node.type,
        description: node.description,
        degree: degree[node.id] || 0,
        baseColor: typeColorMap.value[node.type.trim()] || FALLBACK_COLOR,
      },
    }));
    // ★ 过滤掉端点不在节点集里的边：g6 v5 遇到 source/target 不存在的边会静默丢弃甚至报错。
    // 同时统计被过滤数，方便排查后端返回的 from/to 是否与 node id 对得上（elementId 一致性）。
    const nodeIdSet = new Set(nodes.map((n) => n.id));
    const validEdges = data.edges.filter((e) => nodeIdSet.has(e.source) && nodeIdSet.has(e.target));
    const droppedEdges = data.edges.length - validEdges.length;
    if (droppedEdges > 0) {
      // eslint-disable-next-line no-console
      console.warn(`[KgGraph] 丢弃 ${droppedEdges} 条端点不在节点集的边（后端 from/to 与 node id 不一致？）`);
    }
    const edges = validEdges.map((edge) => ({
      id: edge.id,
      source: edge.source,
      target: edge.target,
      data: {
        label: edge.label,
        sourceName: nameById[edge.source] || edge.source,
        targetName: nameById[edge.target] || edge.target,
      },
    }));

    const currentTheme = theme.value;
    const styleOf = (d: any) =>
      themeNodeStyle(
        currentTheme,
        String(d.data?.baseColor ?? FALLBACK_COLOR),
        Number(d.data?.degree ?? 0)
      );

    graph = new G6Graph({
      container,
      autoResize: true,
      data: { nodes, edges },
      layout:
        layout.value === 'concentric'
          ? {
              type: 'concentric',
              sortBy: 'degree',
              preventOverlap: true,
              nodeSize: 60,
              equidistant: true,
            }
          : {
              type: 'd3-force',
              animation: false,
              manyBody: { strength: -150, distanceMax: 280, theta: 0.9 },
              link: { distance: 95, strength: 0.55 },
              collide: {
                radius: (node: { id: string }) => radiusById[node.id] ?? 34,
                strength: 1,
                iterations: 2,
              },
              x: { strength: 0.05 },
              y: { strength: 0.05 },
            },
      node: {
        style: {
          size: (d: any) => styleOf(d).size,
          fill: (d: any) => styleOf(d).fill,
          fillOpacity: (d: any) => styleOf(d).fillOpacity,
          stroke: (d: any) => styleOf(d).stroke,
          lineWidth: (d: any) => styleOf(d).lineWidth,
          shadowColor: (d: any) => styleOf(d).shadowColor,
          shadowBlur: (d: any) => styleOf(d).shadowBlur,
          shadowOffsetX: 0,
          shadowOffsetY: 0,
          opacity: 1,
          labelText: (d: any) => String(d.data?.name ?? ''),
          labelPlacement: 'bottom',
          labelFontSize: (d: any) => 10 + Math.min(Number(d.data?.degree ?? 0), 12) * 0.4,
          labelFontWeight: (d: any) => (Number(d.data?.degree ?? 0) >= 6 ? 600 : 400),
          labelFill: (d: any) => (Number(d.data?.degree ?? 0) >= 6 ? '#1E293B' : '#64748B'),
          labelOpacity: (d: any) => (Number(d.data?.degree ?? 0) >= labelMinDegree ? 1 : 0),
          labelBackground: true,
          labelBackgroundFill: '#ffffff',
          labelBackgroundOpacity: (d: any) =>
            Number(d.data?.degree ?? 0) >= labelMinDegree ? 0.6 : 0,
          labelBackgroundRadius: 4,
        },
        state: {
          inactive: { opacity: 0.12, labelOpacity: 0, labelBackgroundOpacity: 0 },
          related: { labelOpacity: 1, labelBackgroundOpacity: 0.6 },
        },
      },
      edge: {
        type: 'quadratic',
        style: {
          stroke: EDGE_STROKE,
          lineWidth: 1,
          curveOffset: 0,
          endArrow: true,
          endArrowType: 'simple',
          endArrowSize: 6,
          opacity: EDGE_OPACITY,
          lineDash: 0,
        },
        state: {
          inactive: { opacity: 0.05 },
          flow: {
            stroke: '#6366F1',
            lineWidth: 1.5,
            lineDash: [6, 6],
            opacity: 0.95,
            endArrow: true,
          },
        },
      },
      behaviors: [
        { type: 'zoom-canvas', sensitivity: 1.6, trigger: ['Control'] },
        'drag-canvas',
        'drag-element',
      ],
      plugins: [
        { type: 'grid-line', follow: true },
        {
          type: 'minimap',
          position: 'left-bottom',
          size: [180, 120],
          padding: 8,
          maskStyle: { border: '1.5px solid #6366F1', background: 'rgba(99, 102, 241, 0.12)' },
          containerStyle: {
            borderRadius: '12px',
            border: '1px solid #E2E8F0',
            boxShadow: '0 6px 16px rgba(15, 23, 42, 0.10)',
            background: 'rgba(255, 255, 255, 0.92)',
            overflow: 'hidden',
          },
        },
        {
          type: 'tooltip',
          trigger: 'hover',
          enable: (event: any) => {
            if (!focusedId) return true;
            const targetId = event?.target?.id;
            if (targetId == null) return true;
            return !(graph?.getElementState(String(targetId)) ?? []).includes('inactive');
          },
          getContent: async (_e: unknown, items: any[]) => {
            const datum = items?.[0]?.data;
            if (!datum) return '';
            if (datum.name !== undefined) {
              const name = escapeHtml(String(datum.name ?? ''));
              const type = escapeHtml(String(datum.type ?? ''));
              const desc = escapeHtml(String(datum.description ?? ''));
              return `<div style="max-width:280px;padding:4px 2px">
                <div style="font-weight:600;color:#0f172a;margin-bottom:2px">${name}</div>
                ${type ? `<div style="font-size:12px;color:#6366f1;margin-bottom:4px">${type}</div>` : ''}
                ${desc ? `<div style="font-size:12px;color:#475569;line-height:1.5;white-space:pre-line">${desc}</div>` : ''}
              </div>`;
            }
            if (datum.sourceName !== undefined) {
              const from = escapeHtml(String(datum.sourceName ?? ''));
              const to = escapeHtml(String(datum.targetName ?? ''));
              const detail = escapeHtml(String(datum.label ?? ''));
              return `<div style="max-width:280px;padding:4px 2px">
                <div style="font-size:11px;color:#94a3b8;margin-bottom:2px">关系</div>
                <div style="font-weight:600;color:#0f172a;margin-bottom:4px">${from} <span style="color:#6366f1">→</span> ${to}</div>
                ${detail ? `<div style="font-size:12px;color:#475569;line-height:1.5">${detail}</div>` : ''}
              </div>`;
            }
            return '';
          },
        },
      ],
    });

    // 力导收敛后：先按视口比例重塑铺满，再取景；径向直接取景
    graph.on(GraphEvent.AFTER_LAYOUT, () => {
      if (!isGraphReady(graph)) return;
      try {
        const settled =
          layout.value === 'd3-force'
            ? reshapeToViewport(graph).then(() => fitAllNodes(graph, false))
            : fitAllNodes(graph, false);
        void settled;
      } catch (e) {
        // 布局后取景失败不阻断渲染
        // eslint-disable-next-line no-console
        console.warn('[KgGraph] 布局后取景失败:', e);
      }
    });
    // 缩放/平移后同步右下角读数
    graph.on(GraphEvent.AFTER_TRANSFORM, () => {
      if (!isGraphReady(graph)) return;
      try {
        zoomPct.value = Math.round(graph.getZoom() * 100);
      } catch {
        /* 画布未就绪 */
      }
    });
    // 点击节点聚焦；再次点击同一节点散焦
    graph.on(NodeEvent.CLICK, (event: any) => {
      const id = event.target?.id;
      if (id == null) return;
      const nodeId = String(id);
      if (focusedId === nodeId) {
        clearFocus(graph);
        focusedId = '';
        emit('node-click', null);
        return;
      }
      applyFocus(graph, nodeId);
      focusedId = nodeId;
      const datum = graph.getNodeData(nodeId);
      emit('node-click', {
        id: nodeId,
        label: String(datum?.data?.name ?? nodeId),
        type: String(datum?.data?.type ?? ''),
        description: String(datum?.data?.description ?? ''),
      });
    });
    // 点击空白散焦
    graph.on(CanvasEvent.CLICK, () => {
      if (focusedId) {
        clearFocus(graph);
        focusedId = '';
        emit('node-click', null);
      }
    });

    void graph.render();

    // ★ 创建后同步容器尺寸（内嵌模式动态撑高 / 全屏模式通知 g6 重读）
    syncHeight();
  }

  function destroyGraph() {
    if (graph) {
      try {
        graph.destroy();
      } catch {
        /* 已销毁 */
      }
      graph = null;
    }
    focusedId = '';
    savedView = null;
  }

  function escapeHtml(text: string): string {
    return String(text)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');
  }

  const THEME_LIST: VizTheme[] = ['outline', 'pastel', 'glass', 'vivid'];
  const THEME_LABELS: Record<VizTheme, string> = {
    outline: '描边',
    pastel: '柔彩',
    glass: '玻璃',
    vivid: '鲜彩',
  };
  const themeLabel = computed(() => THEME_LABELS[theme.value]);
  const themeDotColor = computed(() => {
    // 当前主题下取第一个调色板色做圆点示意
    return mixHex(TYPE_PALETTE[0], '#ffffff', theme.value === 'outline' ? 0.86 : 0);
  });
  function cycleTheme() {
    const idx = THEME_LIST.indexOf(theme.value);
    theme.value = THEME_LIST[(idx + 1) % THEME_LIST.length];
    if (graph) {
      // 只重绘不重排：改主题后重新执行样式映射
      graph.updateNodeData(
        graph.getNodeData().map((node: any) => ({
          id: String(node.id),
          style: {
            ...themeNodeStyle(
              theme.value,
              String(node.data?.baseColor ?? FALLBACK_COLOR),
              Number(node.data?.degree ?? 0)
            ),
          },
        }))
      );
      void graph.draw();
    }
  }

  const layoutLabel = computed(() => (layout.value === 'd3-force' ? '力导' : '径向'));
  function cycleLayout() {
    layout.value = layout.value === 'd3-force' ? 'concentric' : 'd3-force';
    if (view.value) renderGraph();
  }

  function zoomIn() {
    void graph?.zoomBy(1.2, true);
  }
  function zoomOut() {
    void graph?.zoomBy(0.8, true);
  }
  function zoomReset() {
    void graph?.zoomTo(1, true);
  }

  /** 内嵌模式：按视口剩余高度动态撑高根节点，消除底部大块空白；
   *  全屏模式：根节点已是 absolute 铺满父容器（.kg-viz-fullscreen__graph），仅通知 g6 重读尺寸。 */
  function syncHeight() {
    if (props.fullscreen) {
      if (graph) {
        try { graph.resize(); } catch { /* 图未就绪 */ }
      }
      return;
    }
    const root = rootRef.value;
    if (!root) return;
    const rect = root.getBoundingClientRect();
    const available = window.innerHeight - rect.top - 16; // 底部留 16px 边距
    root.style.height = Math.max(available, 360) + 'px';
    if (graph) {
      try { graph.resize(); } catch { /* 图未就绪 */ }
    }
  }

  let resizeTimer: ReturnType<typeof setTimeout> | null = null;
  /** 视口尺寸 / 页面滚动变化时：重算高度并重新取景，让图谱始终铺满 */
  function onViewportChange() {
    if (resizeTimer) clearTimeout(resizeTimer);
    resizeTimer = setTimeout(() => {
      syncHeight();
      if (graph && isGraphReady(graph)) {
        void fitAllNodes(graph, false);
      }
    }, 150);
  }

  // Esc 散焦还原
  function onKeydown(e: KeyboardEvent) {
    if (e.key === 'Escape' && graph && focusedId) {
      clearFocus(graph);
      focusedId = '';
      emit('node-click', null);
    }
  }

  onMounted(() => {
    window.addEventListener('keydown', onKeydown);
    window.addEventListener('resize', onViewportChange);
    window.addEventListener('scroll', onViewportChange, true);
    // 内嵌模式：初次撑高容器（全屏由 absolute 铺满父容器 CSS，待 graph 创建后 resize 重读）
    if (!props.fullscreen) {
      nextTick(() => syncHeight());
    }
    if (props.kbId) loadGraph();
  });

  onBeforeUnmount(() => {
    // ★ 置位卸载标志：阻断 renderGraph 的异步链（await import g6 / rAF 重试）
    //   在卸载后继续创建 graph，从而杜绝残留 canvas 盖住后续页面。
    destroyed = true;
    window.removeEventListener('keydown', onKeydown);
    window.removeEventListener('resize', onViewportChange);
    window.removeEventListener('scroll', onViewportChange, true);
    if (resizeTimer) clearTimeout(resizeTimer);
    destroyGraph();
    // ★ 兜底清理：fullscreen 改为 absolute 后，图谱已锁在内容区子树内，
    //   随组件卸载即被 Vue 移除，本不会残留；此处再清一次根容器 innerHTML，
    //   防止 g6 异步渲染时与卸载竞态导致个别 canvas 滞留。只清理本组件根容器，不影响其它页面。
    if (rootRef.value) {
      rootRef.value.innerHTML = '';
    }
  });

  // kbId 变化时重新加载
  watch(
    () => props.kbId,
    (val) => {
      if (val) loadGraph();
      else destroyGraph();
    }
  );
</script>

<style scoped>
  .kg-graph {
    position: relative;
    width: 100%;
    /* 内嵌模式：固定高度 */
    height: 600px;
    border: 1px solid #eee;
    border-radius: 4px;
    overflow: hidden;
    background: radial-gradient(ellipse at 50% 42%, #ffffff 0%, #f4f6fa 58%, #e9edf4 100%);
  }
  /* fullscreen mode: absolute within .kg-viz-fullscreen__graph (position:relative).
     No longer position:fixed+z-index, so it cannot cover the sidebar submenus or
     teleported popovers. It is confined to the content subtree and removed on unmount. */
  .kg-graph--fullscreen {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    width: auto;
    height: auto;
    border: none;
    border-radius: 0;
  }
  .kg-graph__canvas {
    width: 100%;
    height: 100%;
  }

  /* 左上角浮动工具栏（玻璃风） */
  .kg-graph__toolbar {
    position: absolute;
    top: 12px;
    left: 12px;
    z-index: 10;
    max-width: calc(100% - 24px);
  }
  .kg-graph__panel {
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 6px 8px;
    border-radius: 16px;
    border: 1px solid #e2e8f0;
    background: rgba(255, 255, 255, 0.9);
    box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
    backdrop-filter: blur(8px);
  }
  .kg-graph__icon-btn {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    height: 30px;
    padding: 0 8px;
    border: none;
    border-radius: 10px;
    background: transparent;
    color: #475569;
    font-size: 13px;
    cursor: pointer;
    transition: background 0.15s, color 0.15s;
  }
  .kg-graph__icon-btn:hover {
    background: #f1f5f9;
    color: #1e293b;
  }
  .kg-graph__icon-text {
    font-size: 13px;
  }
  .kg-graph__theme-dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    border: 1px solid rgba(0, 0, 0, 0.08);
  }
  .kg-graph__divider {
    width: 1px;
    height: 18px;
    background: #e2e8f0;
    margin: 0 2px;
  }
  .kg-graph__zoom-pct {
    min-width: 44px;
    text-align: center;
    font-size: 12px;
    color: #64748b;
    font-variant-numeric: tabular-nums;
  }
  .kg-graph__stats {
    font-size: 12px;
    color: #94a3b8;
    white-space: nowrap;
  }

  /* 类型图例（右下角，可收起避免遮挡图谱） */
  .kg-graph__legend {
    position: absolute;
    right: 12px;
    bottom: 12px;
    z-index: 10;
    display: flex;
    flex-direction: column;
    gap: 6px;
    max-width: 50%;
    padding: 8px 10px;
    border-radius: 12px;
    border: 1px solid #e2e8f0;
    background: rgba(255, 255, 255, 0.9);
    box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
    backdrop-filter: blur(8px);
  }
  .kg-graph__legend-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }
  .kg-graph__legend-toggle {
    border: none;
    background: transparent;
    color: #6366f1;
    font-size: 11px;
    line-height: 1;
    cursor: pointer;
    padding: 0 2px;
  }
  .kg-graph__legend-toggle:hover {
    text-decoration: underline;
  }
  .kg-graph__legend-items {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    align-items: center;
    max-height: 160px;
    overflow-y: auto;
  }
  .kg-graph__legend-title {
    font-size: 11px;
    color: #94a3b8;
    margin-right: 2px;
  }
  .kg-graph__legend-item {
    display: inline-flex;
    align-items: center;
    font-size: 12px;
    color: #475569;
  }
  .kg-graph__legend-item::before {
    content: '';
    display: inline-block;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--c, #94a3b8);
    margin-right: 4px;
  }

  /* loading / empty 遮罩 */
  .kg-graph__overlay {
    position: absolute;
    inset: 0;
    z-index: 5;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgba(255, 255, 255, 0.7);
  }
</style>
