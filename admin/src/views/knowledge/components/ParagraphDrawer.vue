<template>
  <n-drawer v-model:show="show" :width="900" placement="right">
    <n-drawer-content :title="drawerTitle" closable>
      <n-spin :show="loading">
        <div class="drawer-toolbar">
          <n-space>
            <n-button type="primary" size="small" secondary @click="handleAdd">
              <template #icon
                ><n-icon><PlusOutlined /></n-icon
              ></template>
              新增切片
            </n-button>
            <n-tag v-if="activeDocumentName" size="small" type="info" round>
              {{ activeDocumentName }}
            </n-tag>
          </n-space>
          <n-space :size="4">
            <!-- 阶段耗时统计（柱状图 + 明细表） -->
            <n-tooltip v-if="activeDocumentId">
              <template #trigger>
                <n-button
                  size="small"
                  quaternary
                  :loading="statsLoading"
                  @click="openStats"
                >
                  <template #icon
                    ><n-icon><BarChartOutlined /></n-icon
                  ></template>
                </n-button>
              </template>
              入库耗时统计
            </n-tooltip>
            <!-- 下载原文件 -->
            <n-tooltip v-if="activeDocumentId">
              <template #trigger>
                <n-button
                  size="small"
                  quaternary
                  :loading="downloading"
                  @click="handleDownload"
                >
                  <template #icon
                    ><n-icon><DownloadOutlined /></n-icon
                  ></template>
                </n-button>
              </template>
              下载原文件
            </n-tooltip>
            <span class="total-text">共 {{ total }} 条</span>
          </n-space>
        </div>

        <n-empty v-if="!loading && renderedChunks.length === 0" description="暂无切片" />

        <!-- 按父块分组展示（父块下挂属于它的子块；无父块的子块归「独立切片」组） -->
        <div class="chunk-list scrollbar-thin">
          <div
            v-for="g in groupedChunks"
            :key="g.parentId || 'independent'"
            class="parent-group"
          >
            <!-- 父块分组头部 -->
            <div class="parent-head" :class="{ 'parent-head-mute': !g.parentId }">
              <template v-if="g.parentId">
                <span class="parent-tag">父块 #{{ g.groupNo }}</span>
                <span class="parent-meta">{{ g.parentContent.length }} 字符 · {{ g.children.length }} 子块</span>
                <n-button
                  v-if="g.parentContent"
                  size="tiny"
                  quaternary
                  type="primary"
                  @click="toggleParent(g.parentId)"
                >
                  {{ expandedParents[g.parentId] ? '收起父块' : '展开父块' }}
                </n-button>
              </template>
              <template v-else>
                <span class="parent-tag parent-tag-mute">独立切片</span>
                <span class="parent-meta">{{ g.children.length }} 条（未启用父子分块）</span>
              </template>
            </div>

            <!-- 父块全文（可折叠，仅父块组且后端回填了内容时显示） -->
            <div
              v-if="g.parentId && g.parentContent && expandedParents[g.parentId]"
              class="parent-body md-body"
              v-html="g.parentHtml"
            ></div>

            <!-- 子块卡片列表 -->
            <div
              v-for="rc in g.children"
              :key="rc.original.id"
              class="chunk-item"
            >
              <div class="chunk-header">
                <div class="chunk-header-left">
                  <span class="chunk-index">
                    {{ g.parentId ? `父块 #${g.groupNo} · 子块 ${rc.childNo}` : `切片 ${rc.childNo}` }}
                  </span>
                  <n-tag
                    v-if="rc.original.id"
                    size="small"
                    round
                    :type="isActive(rc.original) ? 'success' : 'default'"
                  >
                    {{ isActive(rc.original) ? '启用' : '停用' }}
                  </n-tag>
                  <span v-if="!rc.original.id" class="chunk-unsaved-tag">未保存</span>
                </div>
                <div class="chunk-header-right">
                  <span class="chunk-meta">{{ (rc.original.content || '').length }} 字符</span>
                  <n-button size="tiny" quaternary type="info" @click="openEditByChunk(rc)">
                    <template #icon
                      ><n-icon><EditOutlined /></n-icon
                    ></template>
                    编辑
                  </n-button>
                  <n-button
                    v-if="rc.original.id"
                    size="tiny"
                    quaternary
                    @click="toggleActive(rc.original)"
                  >
                    {{ isActive(rc.original) ? '停用' : '启用' }}
                  </n-button>
                  <n-button size="tiny" quaternary type="error" @click="handleDelete(rc.original)">
                    <template #icon
                      ><n-icon><DeleteOutlined /></n-icon
                    ></template>
                  </n-button>
                </div>
              </div>

              <!-- 内容（markdown 渲染，只读展示） -->
              <div
                v-if="rc.contentHtml"
                class="chunk-content md-body"
                v-html="rc.contentHtml"
              ></div>
              <div v-else class="chunk-content chunk-content-empty">（空内容，点击编辑添加）</div>
            </div>
          </div>
        </div>

        <div v-if="total > 0" class="pager">
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
      </n-spin>
    </n-drawer-content>
  </n-drawer>

  <!-- 编辑切片弹窗（复用 UploadModal 的编辑弹窗形态） -->
  <n-modal
    v-model:show="editVisible"
    preset="card"
    :title="editTarget?.original.id ? '编辑切片' : '新增切片'"
    style="width: 720px"
    :bordered="false"
  >
    <n-form label-placement="top">
      <n-form-item label="切片内容">
        <n-input
          v-model:value="editForm.content"
          type="textarea"
          :rows="12"
          maxlength="8000"
          show-count
        />
      </n-form-item>
    </n-form>
    <template #footer>
      <n-space justify="end">
        <n-button @click="editVisible = false">取消</n-button>
        <n-button type="primary" secondary :loading="saving" @click="saveEdit">
          保存
        </n-button>
      </n-space>
    </template>
  </n-modal>

  <!-- 入库耗时统计弹窗 -->
  <IngestionTimelineModal ref="timelineModalRef" />
</template>

<script setup lang="ts">
  import { ref, computed } from 'vue';
  import { useMessage, useDialog } from 'naive-ui';
  import { marked } from 'marked';
  import {
    PlusOutlined,
    EditOutlined,
    DeleteOutlined,
    BarChartOutlined,
    DownloadOutlined,
  } from '@vicons/antd';
  import {
    getParagraphList,
    addParagraph,
    editParagraph,
    delParagraph,
    activeParagraph,
    downloadDocument,
    type ParagraphChunk,
  } from '@/api/system/knowledge';
  import IngestionTimelineModal from './IngestionTimelineModal.vue';

  marked.use({ breaks: true, gfm: true });

  /** 简单 markdown → HTML（切片预览用，不需要流式/消毒） */
  function renderMd(text: string): string {
    if (!text) return '';
    return marked.parse(text) as string;
  }

  const props = defineProps<{
    kbId: string;
    documentId?: string;
    documentName?: string;
  }>();

  const emit = defineEmits<{
    (e: 'saved'): void;
  }>();

  const message = useMessage();
  const dialog = useDialog();

  const show = ref(false);
  const loading = ref(false);
  const saving = ref(false);

  // 渲染缓存：避免模板里直接调 marked.parse 触发递归更新告警
  interface RenderedChunk {
    original: ParagraphChunk;
    contentHtml: string;
    /** 子块在所属父块分组内的序号（1 起，渲染时计算） */
    childNo?: number;
  }
  const renderedChunks = ref<RenderedChunk[]>([]);

  /** 父块分组（保留入栈顺序）：parentId 为空串表示「独立切片」组 */
  interface ParentGroup {
    parentId: string;
    parentContent: string;
    parentHtml: string;
    /** 父块分组序号（1 起，仅父块组有意义） */
    groupNo?: number;
    children: RenderedChunk[];
  }

  // 父块全文展开态：key=parentId，true=展开
  const expandedParents = ref<Record<string, boolean>>({});

  // 按文档维度时用当前文档 id；否则按知识库维度
  const activeDocumentId = ref<string>('');
  const activeDocumentName = ref<string>('');

  const page = ref(1);
  const size = ref(20);
  const total = ref(0);

  // 阶段耗时统计弹窗 + 下载状态
  const timelineModalRef = ref<InstanceType<typeof IngestionTimelineModal> | null>(null);
  const statsLoading = ref(false);
  const downloading = ref(false);

  // 打开耗时统计弹窗（按文档维度才显示）
  function openStats() {
    if (!activeDocumentId.value) {
      message.info('请按文档维度打开后再查看耗时统计');
      return;
    }
    statsLoading.value = true;
    timelineModalRef.value?.open(activeDocumentId.value);
    // open 内部会管理 loading，这里仅做兜底复位
    setTimeout(() => {
      statsLoading.value = false;
    }, 1500);
  }

  // 下载原文件（Alova 取 blob → 触发浏览器下载）
  async function handleDownload() {
    if (!activeDocumentId.value) {
      message.info('请按文档维度打开后再下载');
      return;
    }
    downloading.value = true;
    try {
      const { blob, fileName } = await downloadDocument(activeDocumentId.value);
      // 触发浏览器下载
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = fileName || activeDocumentName.value || 'document';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      // 释放 blob URL（下一轮事件循环，确保下载已发起）
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      message.success('下载已开始');
    } catch (e: any) {
      // downloadDocument 已识别业务错误并抛出具体 message
      const msg = e?.message || '下载失败';
      message.error(msg);
    } finally {
      downloading.value = false;
    }
  }

  const drawerTitle = computed(() => {
    return activeDocumentName.value ? `切片预览 · ${activeDocumentName.value}` : '切片管理';
  });

  /** active 标记存于 metadata JSON：{"active":1} */
  function isActive(c: ParagraphChunk) {
    if (!c.metadata) return true; // 默认启用
    try {
      const m = JSON.parse(c.metadata);
      return m && m.active !== 0;
    } catch (e) {
      return true;
    }
  }

  function rebuildRendered(list: ParagraphChunk[]) {
    renderedChunks.value = list.map((c) => ({
      original: c,
      contentHtml: renderMd(c.content || ''),
    }));
  }

  /**
   * 按父块分组：
   *  - 有 parentId 的子块 → 归到对应父块组（按首次出现顺序）
   *  - 无 parentId 的子块（普通分块文档 / 手动录入）→ 归到「独立切片」组（parentId=''）
   * 父块全文来自后端 parentContent 回填；缺失则只显示分组头，不展开。
   */
  const groupedChunks = computed<ParentGroup[]>(() => {
    const map = new Map<string, ParentGroup>();
    const order: string[] = [];
    // 父块组序号：仅对真正有 parentId 的组分配，独立切片组不编号
    let parentSeq = 0;

    renderedChunks.value.forEach((rc) => {
      const rawPid = rc.original.parentId;
      const pid = rawPid && String(rawPid).trim() ? String(rawPid) : '';
      let g = map.get(pid);
      if (!g) {
        g = {
          parentId: pid,
          parentContent: rc.original.parentContent || '',
          parentHtml: '',
          children: [],
        };
        if (pid) {
          g.groupNo = ++parentSeq;
          g.parentHtml = renderMd(g.parentContent);
        }
        map.set(pid, g);
        order.push(pid);
      }
      g.children.push(rc);
    });

    // 给每个子块编号（组内 1 起）
    order.forEach((pid) => {
      const g = map.get(pid)!;
      g.children.forEach((rc, i) => {
        rc.childNo = i + 1;
      });
    });

    return order.map((pid) => map.get(pid)!);
  });

  function toggleParent(pid: string) {
    expandedParents.value[pid] = !expandedParents.value[pid];
  }

  async function loadList() {
    if (!props.kbId && !activeDocumentId.value) return;
    // 翻页/重载时清空父块展开态，避免按 parentId 串到上一页的展开记录
    expandedParents.value = {};
    loading.value = true;
    try {
      const res: any = await getParagraphList({
        kbId: activeDocumentId.value ? undefined : props.kbId || undefined,
        documentId: activeDocumentId.value || undefined,
        page: page.value,
        size: size.value,
      });
      if (res && res.code === 0 && res.data) {
        const arr = Array.isArray(res.data.data) ? res.data.data : [];
        const filtered = arr.filter((x: any) => x && x.id != null) as ParagraphChunk[];
        rebuildRendered(filtered);
        total.value = res.data.total || 0;
      } else {
        rebuildRendered([]);
        total.value = 0;
      }
    } catch (e) {
      message.error('加载切片失败');
      rebuildRendered([]);
    } finally {
      loading.value = false;
    }
  }

  function onSizeChange(s: number) {
    size.value = s;
    page.value = 1;
    loadList();
  }

  /** 打开：不传参 = 知识库维度；传 documentId = 文档维度 */
  function open(documentId?: string, documentName?: string) {
    activeDocumentId.value = documentId || '';
    activeDocumentName.value = documentName || '';
    page.value = 1;
    size.value = 20;
    show.value = true;
    loadList();
  }

  function handleAdd() {
    editTarget.value = null;
    editForm.value = { content: '' };
    editVisible.value = true;
  }

  const editVisible = ref(false);
  const editTarget = ref<RenderedChunk | null>(null);
  const editForm = ref<{ content: string }>({ content: '' });

  function openEditByChunk(rc: RenderedChunk) {
    if (!rc) return;
    editTarget.value = rc;
    editForm.value = { content: rc.original.content || '' };
    editVisible.value = true;
  }

  async function saveEdit() {
    const content = (editForm.value.content || '').trim();
    if (!content) {
      message.warning('内容不能为空');
      return;
    }
    saving.value = true;
    try {
      const target = editTarget.value;
      const isNew = !target || !target.original.id;
      if (isNew) {
        const res: any = await addParagraph({
          kbId: props.kbId,
          content,
        });
        if (res && res.code === 0) {
          message.success('已新增，需重新向量化生效');
          emit('saved');
          editVisible.value = false;
          await loadList();
        } else {
          message.error(res?.message || '新增失败');
        }
      } else {
        const res: any = await editParagraph({
          id: target.original.id,
          kbId: props.kbId,
          content,
        });
        if (res && res.code === 0) {
          message.success('已保存，内容变更需重新向量化生效');
          emit('saved');
          editVisible.value = false;
          await loadList();
        } else {
          message.error(res?.message || '保存失败');
        }
      }
    } catch (e) {
      message.error('操作失败');
    } finally {
      saving.value = false;
    }
  }

  function handleDelete(c: ParagraphChunk) {
    if (!c.id) {
      message.warning('该项未保存，无需删除');
      return;
    }
    dialog.warning({
      title: '确认删除',
      content: '确定删除该切片？删除后需重新向量化生效。',
      positiveText: '删除',
      negativeText: '取消',
      onPositiveClick: async () => {
        const res: any = await delParagraph(c.id);
        if (res && res.code === 0) {
          message.success('已删除');
          emit('saved');
          await loadList();
        } else {
          message.error(res?.message || '删除失败');
        }
      },
    });
  }

  function toggleActive(c: ParagraphChunk) {
    if (!c.id) {
      message.warning('请先保存');
      return;
    }
    const next = isActive(c) ? 0 : 1;
    activeParagraph(c.id, next).then((res: any) => {
      if (res && res.code === 0) {
        message.success(next === 1 ? '已启用' : '已停用');
        loadList();
      } else {
        message.error(res?.message || '操作失败');
      }
    });
  }

  defineExpose({ open });
</script>

<style lang="less" scoped>
  .drawer-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;
  }
  .total-text {
    font-size: 12px;
    color: #888;
  }
  .chunk-list {
    display: flex;
    flex-direction: column;
    gap: 12px;
    max-height: calc(100vh - 260px);
    overflow-y: auto;
  }
  .chunk-item {
    border-radius: 4px;
    padding: 12px 16px;
    background: #fff;
    border: 1px solid #eee;
    margin-top: 10px;
  }
  /* 父块分组容器：父块头部 + 可选父块全文 + 子块卡片 */
  .parent-group {
    border: 1px solid #e8efe9;
    border-radius: 6px;
    padding: 10px 12px;
    background: linear-gradient(180deg, rgba(7, 192, 95, 0.03) 0%, #fff 60%);
  }
  .parent-head {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
    flex-wrap: wrap;
  }
  .parent-tag {
    font-size: 12px;
    font-weight: 600;
    color: #07c05f;
    background: rgba(7, 192, 95, 0.1);
    padding: 2px 8px;
    border-radius: 4px;
  }
  .parent-tag-mute {
    color: #888;
    background: #f0f0f0;
  }
  .parent-head-mute {
    opacity: 0.85;
  }
  .parent-meta {
    font-size: 12px;
    color: #999;
  }
  /* 父块全文（可折叠），与子块内容同款 md-body 样式，底色略区分 */
  .parent-body {
    font-size: 13px;
    line-height: 1.7;
    color: #444;
    background: #fafafa;
    border-left: 3px solid #c5f0d4;
    padding: 10px 12px;
    border-radius: 0 4px 4px 0;
    margin-bottom: 10px;
    word-break: break-word;
  }
  .chunk-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
    padding-bottom: 6px;
    border-bottom: 1px solid #f0f0f0;
    flex-wrap: wrap;
    gap: 8px;
  }
  .chunk-header-left {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .chunk-index {
    color: #aaa;
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 0.5px;
  }
  .chunk-unsaved-tag {
    font-size: 11px;
    color: #d03050;
    background: rgba(208, 48, 80, 0.08);
    padding: 1px 6px;
    border-radius: 8px;
  }
  .chunk-header-right {
    display: flex;
    align-items: center;
    gap: 4px;
  }
  .chunk-meta {
    color: #ccc;
    font-size: 11px;
    margin-right: 4px;
  }
  .chunk-content {
    font-size: 13px;
    line-height: 1.7;
    color: #555;
    word-break: break-word;
  }
  .chunk-content-empty {
    color: #bbb;
    font-style: italic;
  }
  .pager {
    margin-top: 16px;
    display: flex;
    justify-content: flex-end;
  }
  .scrollbar-thin {
    scrollbar-width: thin;
    &::-webkit-scrollbar {
      width: 6px;
    }
    &::-webkit-scrollbar-thumb {
      background: #ddd;
      border-radius: 3px;
    }
  }

  /* markdown 渲染内容（与 UploadModal 一致） */
  .md-body {
    :deep(h1),
    :deep(h2),
    :deep(h3),
    :deep(h4),
    :deep(h5),
    :deep(h6) {
      margin: 12px 0 6px;
      font-weight: 600;
      line-height: 1.4;
      &:first-child {
        margin-top: 0;
      }
    }
    :deep(h1) {
      font-size: 18px;
    }
    :deep(h2) {
      font-size: 16px;
    }
    :deep(h3) {
      font-size: 15px;
    }
    :deep(h4) {
      font-size: 14px;
    }
    :deep(p) {
      margin: 0 0 8px;
      &:last-child {
        margin-bottom: 0;
      }
    }
    :deep(ul),
    :deep(ol) {
      margin: 4px 0 8px;
      padding-left: 20px;
    }
    :deep(li) {
      margin: 2px 0;
    }
    :deep(blockquote) {
      margin: 8px 0;
      padding: 4px 12px;
      border-left: 3px solid #ddd;
      color: #666;
    }
    :deep(code) {
      background: #f5f5f5;
      padding: 1px 4px;
      border-radius: 3px;
      font-size: 12px;
    }
    :deep(pre) {
      background: #f5f5f5;
      padding: 10px 12px;
      border-radius: 4px;
      overflow-x: auto;
      margin: 8px 0;
      code {
        background: none;
        padding: 0;
      }
    }
    :deep(table) {
      border-collapse: collapse;
      margin: 8px 0;
      width: 100%;
      th,
      td {
        border: 1px solid #e0e0e0;
        padding: 6px 8px;
        font-size: 12px;
      }
      th {
        background: #f9f9f9;
        font-weight: 600;
      }
    }
    :deep(hr) {
      border: none;
      border-top: 1px solid #eee;
      margin: 12px 0;
    }
    :deep(img) {
      max-width: 100%;
      border-radius: 4px;
    }
  }
</style>
