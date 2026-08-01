<template>
  <div>
    <div class="toolbar">
      <n-space>
        <n-button type="primary" size="small" secondary @click="handleUpload">
          <template #icon
            ><n-icon><UploadOutlined /></n-icon
          ></template>
          上传文档
        </n-button>
        <n-button
          size="small"
          secondary
          :disabled="checkedIds.length === 0"
          @click="handleEmbedding"
        >
          <template #icon
            ><n-icon><ThunderboltOutlined /></n-icon
          ></template>
          向量化({{ checkedIds.length }})
        </n-button>
        <n-button
          size="small"
          secondary
          :disabled="checkedIds.length === 0"
          @click="handleGenQuestion"
        >
          <template #icon
            ><n-icon><MessageOutlined /></n-icon
          ></template>
          生成问题({{ checkedIds.length }})
        </n-button>
        <n-button
          size="small"
          secondary
          type="error"
          :disabled="checkedIds.length === 0"
          @click="handleDelete"
        >
          <template #icon
            ><n-icon><DeleteOutlined /></n-icon
          ></template>
          删除
        </n-button>
      </n-space>
    </div>

    <n-data-table
      :columns="columns"
      :data="list"
      :loading="loading"
      :row-key="(row: any) => row.id"
      :checked-row-keys="checkedKeys"
      :scroll-x="1620"
      @update:checked-row-keys="onCheck"
    />

    <div class="pager">
      <n-pagination
        v-model:page="page"
        v-model:page-size="size"
        :item-count="total"
        :page-sizes="[10, 20, 50]"
        show-size-picker
        @update:page="loadList"
        @update:page-size="onSizeChange"
      />
    </div>

    <!-- 图谱抽取进度（有进行中的抽取任务时显示） -->
    <n-alert
      v-if="extractingDocIds.length > 0"
      type="info"
      :show-icon="true"
      style="margin-top: 12px"
    >
      <template #header>
        <n-space align="center" :size="12" wrap>
          <n-text strong>图谱抽取进度</n-text>
          <n-text v-for="docId in extractingDocIds" :key="docId" depth="3" style="font-size: 12px">
            {{ fileNameOf(docId) }}：
            {{
              extractProgressMap[docId]
                ? `${extractProgressMap[docId].done}/${extractProgressMap[docId].total} 父块 | 实体 ${extractProgressMap[docId].entityCount} | 关系 ${extractProgressMap[docId].relationCount}`
                : '等待中'
            }}
          </n-text>
        </n-space>
      </template>
    </n-alert>

    <UploadModal ref="uploadModalRef" :kb-id="kbId" @uploaded="loadList" />
    <ParagraphDrawer ref="paragraphDrawerRef" :kb-id="kbId" @saved="loadList" />
    <GenQuestionModal ref="genModalRef" @generated="onQuestionGenerated" />

    <!-- 知识图谱可视化抽屉（从「查看图谱」打开，不再整页跳转） -->
    <KgGraphDrawer
      v-model:show="kgDrawerShow"
      :kb-id="kbId"
      :document-id="kgDrawerDocId"
      :document-name="kgDrawerDocName"
    />
  </div>
</template>

<script setup lang="ts">
  import { ref, h, computed, onMounted, onUnmounted } from 'vue';
  import { useMessage, useDialog, NButton, NSpace, NTag, NIcon, NSwitch } from 'naive-ui';
  import {
    UploadOutlined,
    ThunderboltOutlined,
    MessageOutlined,
    DeleteOutlined,
    LoadingOutlined,
    ProfileOutlined,
    ReloadOutlined,
    ShareAltOutlined,
    ApartmentOutlined,
  } from '@vicons/antd';
  import {
    getDocumentList,
    delDocument,
    embeddingDocument,
    toggleDocumentKg,
    type KnowledgeDocument,
  } from '@/api/system/knowledge';
  import {
    triggerKgExtract,
    getKgExtractProgress,
    getKgConfig,
    type KgExtractionProgress,
  } from '@/api/system/knowledgeGraph';
  import UploadModal from './UploadModal.vue';
  import ParagraphDrawer from './ParagraphDrawer.vue';
  import GenQuestionModal from './GenQuestionModal.vue';
  import KgGraphDrawer from '../graph/components/KgGraphDrawer.vue';
  const props = defineProps<{
    kbId: string;
  }>();

  const message = useMessage();
  const dialog = useDialog();

  // 抽取进度轮询：按 docId 维度跟踪，多个文档可同时抽取
  const extractingDocIds = ref<string[]>([]); // 正在抽取的文档 id 集合（禁用「抽取」按钮）
  const extractProgressMap = ref<Record<string, KgExtractionProgress>>({}); // docId → 进度
  let progressTimer: ReturnType<typeof setTimeout> | null = null;

  /** 切换文档级 KG 开关 */
  async function handleKgToggle(row: KnowledgeDocument, val: boolean) {
    // 开启前先校验全局知识图谱开关（kg_config.enabled=1）：全局未开启则不允许打开单文档图谱，并提示。
    if (val) {
      try {
        const cfgRes: any = await getKgConfig();
        const globalEnabled = cfgRes?.data?.enabled;
        if (globalEnabled !== 1) {
          message.warning('请先在「知识图谱 - 全局配置」中开启知识图谱功能，才能为文档启用图谱');
          return; // 不乐观更新、不调接口，开关保持关闭
        }
      } catch (e: any) {
        message.error(e?.message || '校验全局图谱配置失败');
        return;
      }
    }
    const kgEnabled = val ? 1 : 2;
    const prev = row.kgEnabled ?? 2;
    row.kgEnabled = kgEnabled; // 乐观更新
    try {
      const res: any = await toggleDocumentKg(row.id, kgEnabled);
      if (res && res.code === 0) {
        message.success(
          val ? '已启用该文档的知识图谱' : '已禁用该文档的知识图谱（已有图谱已清理）'
        );
      } else {
        row.kgEnabled = prev; // 回滚
        message.error(res?.message || '设置失败');
      }
    } catch (e: any) {
      row.kgEnabled = prev;
      message.error(e?.message || '设置失败');
    }
  }

  /** 触发单文档图谱抽取（异步），并轮询进度 */
  async function handleExtractKg(row: KnowledgeDocument) {
    if (!props.kbId) return;
    try {
      const res: any = await triggerKgExtract({ kbId: props.kbId, documentIds: [row.id] });
      if (res && res.code === 0 && res.data?.taskId) {
        message.success('抽取已触发');
        extractingDocIds.value = [...new Set([...extractingDocIds.value, row.id])];
        startProgressPolling(row.id, res.data.taskId);
      } else {
        message.error(res?.message || '触发失败');
      }
    } catch (e: any) {
      message.error(e?.message || '触发失败');
    }
  }

  function startProgressPolling(docId: string, taskId: string) {
    // 单一 timer 轮询所有进行中的文档（每个 docId → taskId 映射存内存）
    progressTaskMap[docId] = taskId;
    if (!progressTimer) pollAllProgress();
  }

  const progressTaskMap: Record<string, string> = {};
  function pollAllProgress() {
    const poll = async () => {
      const activeDocIds = Object.keys(progressTaskMap);
      if (activeDocIds.length === 0) {
        progressTimer = null;
        return;
      }
      for (const docId of activeDocIds) {
        try {
          const res: any = await getKgExtractProgress(progressTaskMap[docId]);
          if (res && res.code === 0 && res.data) {
            extractProgressMap.value[docId] = res.data;
            // 实时同步列表行的抽取状态/实体数，让标签跟着变
            const target = list.value.find((d) => d.id === docId);
            if (target) {
              if (res.data.status === 'done') {
                target.kgExtractStatus = 'done';
                target.kgEntityCount = res.data.entityCount;
              } else if (res.data.status === 'failed') {
                target.kgExtractStatus = 'failed';
              } else {
                target.kgExtractStatus = 'extracting';
              }
            }
            if (res.data.status === 'done' || res.data.status === 'failed') {
              delete progressTaskMap[docId];
              extractingDocIds.value = extractingDocIds.value.filter((id) => id !== docId);
              if (res.data.status === 'failed') {
                message.error(`文档抽取失败：${res.data.message || ''}`);
              } else {
                message.success('文档图谱抽取完成');
              }
            }
          }
        } catch {
          // 静默
        }
      }
      if (Object.keys(progressTaskMap).length > 0) {
        progressTimer = setTimeout(poll, 1500);
      } else {
        progressTimer = null;
      }
    };
    poll();
  }

  function stopProgressPolling() {
    if (progressTimer) {
      clearTimeout(progressTimer);
      progressTimer = null;
    }
  }

  /** 打开该文档的图谱可视化抽屉（不再整页跳转，杜绝 canvas 残留盖屏问题） */
  const kgDrawerShow = ref(false);
  const kgDrawerDocId = ref<string | undefined>(undefined);
  const kgDrawerDocName = ref<string>('');
  function openGraphVisualization(row: KnowledgeDocument) {
    kgDrawerDocId.value = row.id;
    kgDrawerDocName.value = row.fileName || '';
    kgDrawerShow.value = true;
  }

  /** 根据 docId 反查文件名（抽取进度提示用） */
  function fileNameOf(docId: string): string {
    const doc = list.value.find((d) => d.id === docId);
    return doc?.fileName || docId;
  }

  const loading = ref(false);
  const deleting = ref(false);
  const list = ref<KnowledgeDocument[]>([]);
  const page = ref(1);
  const size = ref(10);
  const total = ref(0);
  const checkedKeys = ref<string[]>([]);
  const uploadModalRef = ref<InstanceType<typeof UploadModal> | null>(null);
  const paragraphDrawerRef = ref<InstanceType<typeof ParagraphDrawer> | null>(null);
  const genModalRef = ref<InstanceType<typeof GenQuestionModal> | null>(null);
  const checkedIds = computed(() => checkedKeys.value);
  let pollTimer: any = null;
  function formatSize(size?: number) {
    if (!size) return '-';
    if (size < 1024) return size + ' B';
    if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB';
    return (size / 1024 / 1024).toFixed(2) + ' MB';
  }

  // ms → 1.2s / 1m05s（与 ProcessingStatus 一致）
  function formatElapsed(ms?: number): string {
    if (ms == null || ms < 0 || isNaN(ms)) return '';
    if (ms < 1000) return (ms / 1000).toFixed(1) + 's';
    if (ms < 60000) return (ms / 1000).toFixed(1) + 's';
    const mins = Math.floor(ms / 60000);
    const secs = Math.floor((ms % 60000) / 1000);
    return `${mins}m${String(secs).padStart(2, '0')}s`;
  }

  // 平滑计时器：后端每 1.5s 轮询一次返回真实 durationMs（锚点），
  // 两次轮询之间前端用 100ms interval 插值，数字平滑增长不跳。
  // anchors: docId → { baseMs, baseTime }（轮询时校准）
  // tick: ref<number>（每 100ms 自增，驱动整个组件重渲染，statusTag 里的 smoothElapsed 读取随之更新）
  const anchors: Record<string, { baseMs: number; baseTime: number }> = {};
  const smoothElapsed: Record<string, number> = {};
  const tick = ref(0);
  let smoothTimer: ReturnType<typeof setInterval> | null = null;
  function syncAnchors(docs: KnowledgeDocument[]) {
    const activeIds = new Set<string>();
    for (const d of docs) {
      if (d.status === 'processing' && d.embedProgress?.durationMs != null) {
        anchors[d.id] = { baseMs: d.embedProgress.durationMs, baseTime: Date.now() };
        activeIds.add(d.id);
      }
    }
    // 清除不再 processing 的
    for (const id of Object.keys(anchors)) {
      if (!activeIds.has(id)) {
        delete anchors[id];
        delete smoothElapsed[id];
      }
    }
    if (Object.keys(anchors).length > 0 && !smoothTimer) {
      smoothTimer = setInterval(() => {
        for (const [id, a] of Object.entries(anchors)) {
          smoothElapsed[id] = a.baseMs + (Date.now() - a.baseTime);
        }
        tick.value++; // 强制触发组件重渲染
      }, 100);
    } else if (Object.keys(anchors).length === 0 && smoothTimer) {
      clearInterval(smoothTimer);
      smoothTimer = null;
    }
  }

  function statusTag(row: KnowledgeDocument) {
    const status = row.status;
    const map: Record<string, { label: string; type: 'default' | 'info' | 'success' | 'error' }> = {
      pending: { label: '待处理', type: 'default' },
      processing: { label: '向量化中', type: 'info' },
      done: { label: '已完成', type: 'success' },
      failed: { label: '失败', type: 'error' },
    };
    const cfg = map[status || ''] || { label: status || '-', type: 'default' as const };
    if (status === 'processing') {
      // tick 作为读依赖保在 render 闭包里，tick.value 每 100ms 变一次 → 组件重渲染 → 此函数重执行
      void tick.value;
      const elapsed = formatElapsed(smoothElapsed[row.id] ?? row.embedProgress?.durationMs);
      return h(
        NTag,
        { size: 'small', type: cfg.type, round: true },
        {
          default: () => [
            h(NIcon, { size: 12, class: 'doc-status-spin' }, { default: () => h(LoadingOutlined) }),
            cfg.label,
            h('span', { class: 'doc-status-elapsed' }, elapsed),
          ],
        }
      );
    }
    return h(NTag, { size: 'small', type: cfg.type, round: true }, { default: () => cfg.label });
  }

  function questionStatusTag(s?: number) {
    const map: Record<number, { label: string; type: 'default' | 'info' | 'success' }> = {
      1: { label: '待生成', type: 'default' },
      2: { label: '生成中', type: 'info' },
      3: { label: '已生成', type: 'success' },
    };
    const cfg = map[s || 1] || { label: '待生成', type: 'default' as const };
    // 生成中：加一个旋转的 loading 图标，与向量化状态保持一致
    if (s === 2) {
      return h(
        NTag,
        { size: 'small', type: cfg.type, round: true },
        {
          default: () => [
            h(NIcon, { size: 12, class: 'doc-status-spin' }, { default: () => h(LoadingOutlined) }),
            cfg.label,
          ],
        }
      );
    }
    return h(NTag, { size: 'small', type: cfg.type, round: true }, { default: () => cfg.label });
  }

  /** 图谱抽取状态标签：null=未抽取 / pending / extracting / done / failed */
  function kgExtractTag(row: KnowledgeDocument) {
    const status = row.kgExtractStatus;
    if (!status) {
      return h(
        NTag,
        { size: 'small', type: 'default', round: true, bordered: false },
        { default: () => '未抽取' }
      );
    }
    const map: Record<string, { label: string; type: 'default' | 'info' | 'success' | 'error' }> = {
      pending: { label: '待抽取', type: 'default' },
      extracting: { label: '抽取中', type: 'info' },
      done: {
        label: `已抽取${row.kgEntityCount ? '(' + row.kgEntityCount + ')' : ''}`,
        type: 'success',
      },
      failed: { label: '失败', type: 'error' },
    };
    const cfg = map[status] || { label: status, type: 'default' as const };
    // 抽取中：加旋转 loading 图标
    if (status === 'extracting') {
      return h(
        NTag,
        { size: 'small', type: cfg.type, round: true },
        {
          default: () => [
            h(NIcon, { size: 12, class: 'doc-status-spin' }, { default: () => h(LoadingOutlined) }),
            cfg.label,
          ],
        }
      );
    }
    return h(NTag, { size: 'small', type: cfg.type, round: true }, { default: () => cfg.label });
  }

  const columns = [
    { type: 'selection' },
    { title: '文件名', key: 'fileName', minWidth: 220, ellipsis: { tooltip: true } },
    {
      title: '大小',
      key: 'fileSize',
      width: 100,
      render: (row: KnowledgeDocument) => formatSize(row.fileSize),
    },
    {
      title: '向量化状态',
      key: 'status',
      width: 170,
      render: (row: KnowledgeDocument) => statusTag(row),
    },
    { title: '切片数', key: 'chunkCount', width: 80 },
    {
      title: '问题状态',
      key: 'questionStatus',
      width: 110,
      render: (row: KnowledgeDocument) => questionStatusTag(row.questionStatus),
    },
    {
      title: '图谱',
      key: 'kgEnabled',
      width: 150,
      render(row: KnowledgeDocument) {
        return h('div', { style: 'display:flex; align-items:center; gap:8px;' }, [
          h(NSwitch, {
            size: 'small',
            value: (row.kgEnabled ?? 2) === 1,
            'checked-value': true,
            'unchecked-value': false,
            onUpdateValue: (val: boolean) => handleKgToggle(row, val),
          }),
          kgExtractTag(row),
        ]);
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 400,
      fixed: 'right', // 固定在右侧：无论屏幕多窄，操作列始终可见，不会被压缩或横向滚动卷走
      render(row: KnowledgeDocument) {
        // 已向量化（done）显示「重新向量化」，否则显示「向量化」；图标同步切换
        const isDone = row.status === 'done';
        const embIcon = isDone ? ReloadOutlined : ThunderboltOutlined;
        const embText = isDone ? '重新向量化' : '向量化';
        const kgOn = (row.kgEnabled ?? 2) === 1;
        const extracting = extractingDocIds.value.includes(row.id);
        return h(
          NSpace,
          { size: 'small', wrap: false }, // 禁止换行：按钮始终并排
          {
            default: () => [
              h(
                NButton,
                {
                  size: 'tiny',
                  secondary: true,
                  type: 'info',
                  onClick: () => openParagraphs(row),
                },
                {
                  default: () => '查看切片',
                  icon: () => h(NIcon, null, { default: () => h(ProfileOutlined) }),
                }
              ),
              h(
                NButton,
                {
                  size: 'tiny',
                  secondary: true,
                  type: 'primary',
                  onClick: () => embeddingOne(row),
                },
                {
                  default: () => embText,
                  icon: () => h(NIcon, null, { default: () => h(embIcon) }),
                }
              ),
              // 图谱抽取：需先开启文档级 KG 开关
              h(
                NButton,
                {
                  size: 'tiny',
                  secondary: true,
                  type: 'warning',
                  disabled: !kgOn || extracting,
                  loading: extracting,
                  onClick: () => handleExtractKg(row),
                },
                {
                  default: () => (extracting ? '抽取中' : '图谱抽取'),
                  icon: () => h(NIcon, null, { default: () => h(ApartmentOutlined) }),
                }
              ),
              // 查看图谱：跳转该文档的可视化全屏页
              h(
                NButton,
                {
                  size: 'tiny',
                  secondary: true,
                  type: 'success',
                  disabled: !kgOn,
                  onClick: () => openGraphVisualization(row),
                },
                {
                  default: () => '查看图谱',
                  icon: () => h(NIcon, null, { default: () => h(ShareAltOutlined) }),
                }
              ),
            ],
          }
        );
      },
    },
  ];

  async function loadList() {
    if (!props.kbId) return;
    loading.value = true;
    try {
      const res: any = await getDocumentList({
        kbId: props.kbId,
        page: page.value,
        size: size.value,
      });
      if (res && res.code === 0 && res.data) {
        const arr = Array.isArray(res.data.data) ? res.data.data : [];
        list.value = arr.filter((x: any) => x && x.id != null);
        total.value = res.data.total || 0;
      } else {
        list.value = [];
        total.value = 0;
      }
    } catch (e) {
      message.error('加载文档列表失败');
      list.value = [];
    } finally {
      loading.value = false;
    }
    syncAnchors(list.value);
    schedulePoll();
  }

  function onSizeChange(s: number) {
    size.value = s;
    page.value = 1;
    loadList();
  }

  function onCheck(keys: string[]) {
    checkedKeys.value = keys;
  }

  function handleUpload() {
    uploadModalRef.value?.open();
  }

  function openParagraphs(row: KnowledgeDocument) {
    // 按文档维度查看切片：传 documentId + fileName 精准加载
    paragraphDrawerRef.value?.open(row.id, row.fileName);
  }

  function embeddingOne(row: KnowledgeDocument) {
    dialog.warning({
      title: '确认向量化',
      content: `确定对文档「${row.fileName}」重新向量化？`,
      positiveText: '确认',
      negativeText: '取消',
      onPositiveClick: async () => {
        const res: any = await embeddingDocument(row.id);
        if (res && res.code === 0) {
          // 后端异步处理：接口立即返回，文档状态已置为 processing，
          // 由 schedulePoll 自动轮询直到 done/failed
          message.success('已提交向量化，后台处理中');
          await loadList();
        } else {
          message.error(res?.message || '操作失败');
        }
      },
    });
  }

  function handleEmbedding() {
    const ids = checkedKeys.value.join(',');
    dialog.warning({
      title: '确认向量化',
      content: `确定对选中的 ${checkedKeys.value.length} 个文档重新向量化？`,
      positiveText: '确认',
      negativeText: '取消',
      onPositiveClick: async () => {
        const res: any = await embeddingDocument(ids);
        if (res && res.code === 0) {
          // 后端异步处理：接口立即返回，文档状态已置为 processing，
          // 由 schedulePoll 自动轮询直到 done/failed
          message.success('已提交向量化，后台处理中');
          checkedKeys.value = [];
          await loadList();
        } else {
          message.error(res?.message || '操作失败');
        }
      },
    });
  }

  function handleGenQuestion() {
    if (checkedKeys.value.length === 0) {
      message.warning('请先选择文档');
      return;
    }
    // 弹窗选 chat 模型 + 每块问题数，提交后后台异步逐块生成
    genModalRef.value?.open([...checkedKeys.value]);
  }

  // 生成问题提交后：清勾选 + 刷新列表（questionStatus 会变为 2，轮询自动跟踪到 3）
  function onQuestionGenerated() {
    checkedKeys.value = [];
    loadList();
  }

  function handleDelete() {
    if (deleting.value) return;
    dialog.warning({
      title: '确认删除',
      content: `确定删除选中的 ${checkedKeys.value.length} 个文档？将级联删除其切片与对象，不可撤销。`,
      positiveText: '删除',
      negativeText: '取消',
      onPositiveClick: async () => {
        if (deleting.value) return;
        deleting.value = true;
        const hide = message.loading(`正在删除 ${checkedKeys.value.length} 个文档，请稍候...`);
        try {
          const res: any = await delDocument(checkedKeys.value.join(','));
          if (res && res.code === 0) {
            message.success('已删除');
            checkedKeys.value = [];
            await loadList();
          } else {
            message.error(res?.message || '删除失败');
          }
        } catch (e) {
          message.error('删除失败，请重试');
        } finally {
          hide.destroy();
          deleting.value = false;
        }
      },
    });
  }

  // 只有真正进行中的任务才轮询：
  // - status=processing：文档正在向量化（由 embedding 接口异步触发，最终落到 done/failed）
  // - questionStatus=2：问题正在生成（由 generateKbQuestions 异步触发，最终落到 1/3）
  // pending 是「入库完成、待向量化」的稳定终态，不会自动变化，不能轮询它，
  // 否则页面只要有未向量化的文档就会无脑一直轮询。
  function schedulePoll() {
    stopPoll();
    const hasProcessing = list.value.some(
      (d) => d.status === 'processing' || d.questionStatus === 2
    );
    if (hasProcessing) {
      pollTimer = setTimeout(async () => {
        const res: any = await getDocumentList({
          kbId: props.kbId,
          page: page.value,
          size: size.value,
        });
        if (res && res.code === 0 && res.data) {
          const arr = Array.isArray(res.data.data) ? res.data.data : [];
          list.value = arr.filter((x: any) => x && x.id != null);
          total.value = res.data.total || 0;
        }
        syncAnchors(list.value);
        schedulePoll();
      }, 1500);
    }
  }

  function stopPoll() {
    if (pollTimer) {
      clearTimeout(pollTimer);
      pollTimer = null;
    }
  }

  onMounted(() => {
    loadList();
  });

  onUnmounted(() => {
    stopPoll();
    stopProgressPolling();
    if (smoothTimer) {
      clearInterval(smoothTimer);
      smoothTimer = null;
    }
  });
</script>

<style lang="less" scoped>
  .toolbar {
    margin-bottom: 12px;
  }
  .pager {
    margin-top: 12px;
    display: flex;
    justify-content: flex-end;
  }
  /* NTag 是 Naive UI 组件，scoped 下需用 :deep() 穿透到内部的图标 */
  :deep(.doc-status-spin) {
    margin-right: 4px;
    animation: doc-status-spin 1s linear infinite;
  }
  @keyframes doc-status-spin {
    to {
      transform: rotate(360deg);
    }
  }
  /* 向量化中实时耗时数字 */
  :deep(.doc-status-elapsed) {
    margin-left: 6px;
    font-weight: 700;
    font-size: 12px;
    color: #2080f0;
    font-variant-numeric: tabular-nums;
  }
</style>
