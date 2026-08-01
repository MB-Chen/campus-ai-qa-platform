<template>
  <div>
    <div class="toolbar">
      <n-space>
        <n-button type="primary" size="small" secondary @click="openAdd">
          <template #icon
            ><n-icon><PlusOutlined /></n-icon
          ></template>
          新增问题
        </n-button>
        <n-button size="small" secondary @click="openImport">
          <template #icon>
            <n-icon><UploadOutlined /></n-icon>
          </template>
          批量导入
        </n-button>
        <n-button size="small" secondary @click="downloadTemplate">
          <template #icon>
            <n-icon><DownloadOutlined /></n-icon>
          </template>
          下载模板
        </n-button>
      </n-space>
    </div>

    <n-data-table
      :columns="columns"
      :data="list"
      :loading="loading"
      :row-key="(row: any) => row.id"
      :pagination="pagination"
      remote
      @update:page="onPage"
    />
  </div>

  <!-- 新增/编辑问题 -->
  <n-modal
    v-model:show="editShow"
    preset="card"
    :title="editForm.id ? '编辑问题' : '新增问题'"
    style="width: 520px"
  >
    <n-form label-placement="top">
      <n-form-item label="问题" required>
        <n-input
          v-model:value="editForm.content"
          type="textarea"
          :rows="2"
          placeholder="请输入问题内容"
        />
      </n-form-item>
    </n-form>
    <template #footer>
      <n-space justify="end">
        <n-button @click="editShow = false">取消</n-button>
        <n-button type="primary" secondary :loading="saving" @click="handleSave">保存</n-button>
      </n-space>
    </template>
  </n-modal>

  <!-- 批量导入 -->
  <n-modal
    v-model:show="importShow"
    preset="card"
    title="批量导入问题"
    style="width: 520px"
    :mask-closable="false"
    :close-on-esc="!importing"
  >
    <n-alert type="info" :bordered="false" style="margin-bottom: 12px">
      仅支持 .xls / .xlsx 格式，单列「问题」，首行为表头自动跳过。
      单次最多 500 行；导入为异步处理，提交后请等待进度完成。
    </n-alert>

    <template v-if="!importing && progress === null">
      <n-upload
        :default-upload="false"
        :max="1"
        accept=".xls,.xlsx"
        :file-list="importFileList"
        @change="onImportFileChange"
      >
        <n-upload-dragger>
          <div style="margin-bottom: 8px">
            <n-icon size="36" :depth="3"><UploadOutlined /></n-icon>
          </div>
          <n-text style="font-size: 14px">点击或拖拽 Excel 文件到此处</n-text>
          <n-text depth="3" style="font-size: 12px; display: block; margin-top: 4px">
            未选文件？可先点「下载模板」
          </n-text>
        </n-upload-dragger>
      </n-upload>
    </template>

    <template v-else>
      <div style="padding: 8px 0">
        <n-progress
          type="line"
          :percentage="progressPercent"
          :status="progressStatus"
          processing
        />
        <n-space justify="space-between" style="margin-top: 10px">
          <n-text depth="3" style="font-size: 12px">
            {{ progressText }}
          </n-text>
          <n-text depth="3" style="font-size: 12px">
            {{ progress?.done || 0 }} / {{ progress?.total || 0 }}
          </n-text>
        </n-space>
        <n-text v-if="progress?.failed" type="error" style="font-size: 12px; display: block; margin-top: 6px">
          失败 {{ progress.failed }} 条（详见后端日志）
        </n-text>
      </div>
    </template>

    <template #footer>
      <n-space justify="end">
        <n-button :disabled="importing" @click="closeImport">关闭</n-button>
        <n-button
          v-if="!importing && progress === null"
          type="primary"
          secondary
          :loading="submitting"
          :disabled="importFileList.length === 0"
          @click="submitImport"
        >
          开始导入
        </n-button>
        <n-button
          v-if="progress && progress.status === 'done'"
          type="primary"
          secondary
          @click="finishImport"
        >
          完成
        </n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
  import { ref, reactive, computed, h, onMounted, onBeforeUnmount } from 'vue';
  import { useMessage, useDialog, NButton, NSpace, NTag } from 'naive-ui';
  import type { UploadFileInfo } from 'naive-ui';
  import { PlusOutlined, UploadOutlined, DownloadOutlined } from '@vicons/antd';
  import ExcelJS from 'exceljs';
  import { saveAs } from 'file-saver';
  import {
    getQuestionList,
    addQuestion,
    editQuestion,
    delQuestion,
    importQuestions,
    getImportProgress,
    type KnowledgeQuestion,
  } from '@/api/system/knowledge';

  const props = defineProps<{
    kbId: string;
  }>();

  const message = useMessage();
  const dialog = useDialog();

  const loading = ref(false);
  const list = ref<KnowledgeQuestion[]>([]);
  const page = ref(1);
  const size = ref(10);
  const total = ref(0);

  const pagination = reactive({
    page: 1,
    pageSize: 10,
    itemCount: 0,
    showSizePicker: false,
  });

  const editShow = ref(false);
  const saving = ref(false);
  const editForm = reactive({
    id: undefined as number | undefined,
    content: '',
  });

  const importShow = ref(false);
  const submitting = ref(false);
  const importing = ref(false);
  const importFileList = ref<UploadFileInfo[]>([]);
  const progress = ref<{
    status: string;
    total: number;
    done: number;
    success: number;
    failed: number;
    message?: string;
  } | null>(null);
  let pollTimer: ReturnType<typeof setTimeout> | null = null;

  const progressPercent = computed(() => {
    if (!progress.value || !progress.value.total) return 0;
    return Math.round((progress.value.done / progress.value.total) * 100);
  });
  const progressStatus = computed(() => {
    if (!progress.value) return 'default' as const;
    if (progress.value.status === 'done') return 'success' as const;
    return 'default' as const;
  });
  const progressText = computed(() => {
    if (!progress.value) return '';
    if (progress.value.status === 'done') {
      return `导入完成（成功 ${progress.value.success} 条${progress.value.failed ? '，失败 ' + progress.value.failed + ' 条' : ''}）`;
    }
    return '正在向量化入库，请勿关闭…';
  });

  const columns = [
    { title: '问题', key: 'content', minWidth: 320, ellipsis: { tooltip: true } },
    {
      title: '来源',
      key: 'source',
      width: 100,
      render(row: KnowledgeQuestion) {
        let type: 'info' | 'default' | 'success' = 'default';
        let text = '手动';
        if (row.source === 'ai') {
          type = 'info';
          text = 'AI生成';
        } else if (row.source === 'import') {
          type = 'success';
          text = '批量导入';
        }
        return h(NTag, { size: 'small', type, round: true }, { default: () => text });
      },
    },
    {
      title: '状态',
      key: 'status',
      width: 80,
      render(row: KnowledgeQuestion) {
        return h(
          NTag,
          { size: 'small', type: row.status === 2 ? 'error' : 'success' },
          { default: () => (row.status === 2 ? '禁用' : '正常') }
        );
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render(row: KnowledgeQuestion) {
        return h(
          NSpace,
          { size: 'small' },
          {
            default: () => [
              h(
                NButton,
                {
                  size: 'tiny',
                  secondary: true,
                  type: 'primary',
                  onClick: () => openEdit(row),
                },
                { default: () => '编辑' }
              ),
              h(
                NButton,
                {
                  size: 'tiny',
                  secondary: true,
                  type: 'error',
                  onClick: () => handleDelete(row),
                },
                { default: () => '删除' }
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
      const res: any = await getQuestionList({
        kbId: props.kbId,
        page: page.value,
        size: size.value,
      });
      if (res && res.code === 0 && res.data) {
        const arr = Array.isArray(res.data.data) ? res.data.data : [];
        list.value = arr.filter((x: any) => x && x.id != null);
        total.value = res.data.total || 0;
        pagination.itemCount = total.value;
        pagination.page = page.value;
      } else {
        list.value = [];
        total.value = 0;
        pagination.itemCount = 0;
      }
    } catch (e) {
      message.error('加载问题失败');
      list.value = [];
    } finally {
      loading.value = false;
    }
  }

  function onPage(p: number) {
    page.value = p;
    loadList();
  }

  function openAdd() {
    Object.assign(editForm, { id: undefined, content: '' });
    editShow.value = true;
  }

  function openEdit(row: KnowledgeQuestion) {
    Object.assign(editForm, { id: row.id, content: row.content });
    editShow.value = true;
  }

  async function handleSave() {
    if (!editForm.content.trim()) {
      message.warning('请输入问题内容');
      return;
    }
    saving.value = true;
    try {
      let res: any;
      if (editForm.id) {
        res = await editQuestion({
          id: editForm.id,
          kbId: props.kbId,
          content: editForm.content.trim(),
        });
      } else {
        res = await addQuestion({
          kbId: props.kbId,
          content: editForm.content.trim(),
        });
      }
      if (res && res.code === 0) {
        message.success('已保存');
        editShow.value = false;
        await loadList();
      } else {
        message.error(res?.message || '保存失败');
      }
    } catch (e) {
      message.error('操作失败');
    } finally {
      saving.value = false;
    }
  }

  function handleDelete(row: KnowledgeQuestion) {
    dialog.warning({
      title: '确认删除',
      content: '确定删除该问题？',
      positiveText: '删除',
      negativeText: '取消',
      onPositiveClick: async () => {
        const res: any = await delQuestion(row.id);
        if (res && res.code === 0) {
          message.success('已删除');
          await loadList();
        } else {
          message.error(res?.message || '删除失败');
        }
      },
    });
  }

  onMounted(() => {
    loadList();
  });

  function openImport() {
    importFileList.value = [];
    progress.value = null;
    importing.value = false;
    submitting.value = false;
    importShow.value = true;
  }

  function closeImport() {
    if (importing.value) {
      message.warning('导入进行中，请等待完成');
      return;
    }
    stopPoll();
    importShow.value = false;
    importFileList.value = [];
    progress.value = null;
  }

  function finishImport() {
    stopPoll();
    importShow.value = false;
    importFileList.value = [];
    progress.value = null;
    loadList();
  }

  function onImportFileChange(options: { fileList: UploadFileInfo[] }) {
    importFileList.value = options.fileList.slice(-1);
  }

  async function submitImport() {
    const f = importFileList.value[0]?.file;
    if (!f) {
      message.warning('请先选择 Excel 文件');
      return;
    }
    submitting.value = true;
    try {
      const res: any = await importQuestions(props.kbId, f);
      if (res && res.code === 0 && res.data && res.data.taskId) {
        message.success('已提交，后台处理中');
        importing.value = true;
        progress.value = { status: 'processing', total: 0, done: 0, success: 0, failed: 0 };
        startPoll(res.data.taskId);
      } else {
        message.error(res?.message || '提交失败');
      }
    } catch (e) {
      message.error('提交失败');
    } finally {
      submitting.value = false;
    }
  }

  function startPoll(taskId: string) {
    stopPoll();
    const poll = async () => {
      try {
        const res: any = await getImportProgress(taskId);
        if (res && res.code === 0 && res.data) {
          progress.value = res.data;
          if (res.data.status === 'done') {
            importing.value = false;
            message.success('导入完成');
            return; // 停止轮询
          }
        }
      } catch (e) {
        // 单次查询失败不中断轮询
      }
      pollTimer = setTimeout(poll, 1500);
    };
    poll();
  }

  function stopPoll() {
    if (pollTimer) {
      clearTimeout(pollTimer);
      pollTimer = null;
    }
  }

  async function downloadTemplate() {
    try {
      const wb = new ExcelJS.Workbook();
      const ws = wb.addWorksheet('问题导入');
      ws.columns = [{ header: '问题', key: 'content', width: 50 }];
      // 表头样式
      ws.getRow(1).font = { bold: true };
      ws.getRow(1).alignment = { vertical: 'middle', horizontal: 'center' };
      // 示例行（用户可删）
      ws.addRow({ content: '如何重置密码？' });
      ws.addRow({ content: '发票怎么申请？' });
      const buffer = await wb.xlsx.writeBuffer();
      const blob = new Blob([buffer], {
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      });
      saveAs(blob, '问题导入模板.xlsx');
    } catch (e) {
      message.error('模板生成失败');
    }
  }

  onBeforeUnmount(() => {
    stopPoll();
  });
</script>

<style lang="less" scoped>
  .toolbar {
    margin-bottom: 12px;
  }
</style>
