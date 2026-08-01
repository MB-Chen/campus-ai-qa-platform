<template>
  <div>
    <div class="n-layout-page-header">
      <n-card :bordered="false" title="样例查询">
        录入常用 Q&amp;A，问题向量化后供问答时做相似度检索，命中阈值直接返回预设答案（不走大模型）
      </n-card>
    </div>

    <n-card :bordered="false" class="mt-4 proCard">
      <!-- 搜索表单 -->
      <n-form inline :model="searchForm" label-placement="left" class="search-form">
        <n-form-item label="关键字">
          <n-input
            v-model:value="searchForm.keyword"
            placeholder="搜索问题/答案"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
        </n-form-item>
        <n-form-item label="状态">
          <n-select
            v-model:value="searchForm.status"
            :options="statusOptions"
            placeholder="全部"
            clearable
            style="width: 120px"
          />
        </n-form-item>
        <n-form-item label="向量化">
          <n-select
            v-model:value="searchForm.vectorized"
            :options="vectorizedOptions"
            placeholder="全部"
            clearable
            style="width: 130px"
          />
        </n-form-item>
        <n-form-item>
          <n-space>
            <n-button secondary type="primary" @click="handleSearch">
              <template #icon>
                <n-icon><SearchOutlined /></n-icon>
              </template>
              搜索
            </n-button>
            <n-button secondary @click="handleReset">
              <template #icon>
                <n-icon><ReloadOutlined /></n-icon>
              </template>
              重置
            </n-button>
          </n-space>
        </n-form-item>
      </n-form>

      <BasicTable
        ref="actionRef"
        :columns="columns"
        :request="loadDataTable"
        :row-key="(row: any) => row.id"
        :action-column="actionColumn"
        :scroll-x="1000"
      >
        <template #tableTitle>
          <n-space size="small">
            <n-button secondary type="primary" @click="openCreate">
              <template #icon>
                <n-icon><PlusOutlined /></n-icon>
              </template>
              新增
            </n-button>
            <n-button secondary @click="openImport">
              <template #icon>
                <n-icon><UploadOutlined /></n-icon>
              </template>
              导入
            </n-button>
            <n-button secondary :loading="exporting" @click="handleExport">
              <template #icon>
                <n-icon><DownloadOutlined /></n-icon>
              </template>
              导出
            </n-button>
            <n-button
              secondary
              type="warning"
              :loading="batchVectorizing"
              :disabled="importing"
              @click="handleVectorizeAll"
            >
              <template #icon>
                <n-icon><ThunderboltOutlined /></n-icon>
              </template>
              批量向量化
            </n-button>
            <n-button secondary @click="openConfig">
              <template #icon>
                <n-icon><SettingOutlined /></n-icon>
              </template>
              配置
            </n-button>
          </n-space>
        </template>
      </BasicTable>
    </n-card>

    <!-- 新增/编辑 -->
    <EditModal ref="editModalRef" @saved="onSaved" />

    <!-- 配置 -->
    <ConfigModal ref="configModalRef" @saved="onConfigSaved" />

    <!-- 导入 -->
    <n-modal
      v-model:show="importShow"
      preset="card"
      title="批量导入样例"
      style="width: 520px"
      :mask-closable="!importing"
    >
      <n-alert type="info" :bordered="false" style="margin-bottom: 12px">
        Excel 格式：首列「问题」，第二列「答案」，首行表头自动跳过。单次最多 500 行；同步处理。
        问题或答案为空的行会跳过。导入后不会自动向量化，需手动点「批量向量化」。
      </n-alert>

      <n-upload
        :default-upload="false"
        :max="1"
        accept=".xls,.xlsx"
        :file-list="importFileList"
        :disabled="importing"
        @change="onImportFileChange"
      >
        <n-upload-dragger>
          <div style="margin-bottom: 8px">
            <n-icon size="36" :depth="3"><UploadOutlined /></n-icon>
          </div>
          <n-text style="font-size: 14px">点击或拖拽 Excel 文件到此处</n-text>
        </n-upload-dragger>
      </n-upload>

      <div v-if="importResult" style="margin-top: 12px">
        <n-alert
          :type="importResult.failed > 0 ? 'warning' : 'success'"
          :bordered="false"
          title="导入完成"
        >
          总计 {{ importResult.total }} 行：成功 {{ importResult.success }} 条，
          跳过 {{ importResult.skipped }} 条，失败 {{ importResult.failed }} 条。
        </n-alert>
      </div>

      <template #footer>
        <n-space justify="end">
          <n-button :disabled="importing" @click="closeImport">关闭</n-button>
          <n-button
            type="primary"
            secondary
            :loading="importing"
            :disabled="importFileList.length === 0"
            @click="submitImport"
          >
            {{ importing ? '导入中…' : '开始导入' }}
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 批量向量化进度 -->
    <n-modal
      v-model:show="vectorizeShow"
      preset="card"
      title="批量向量化"
      style="width: 480px"
      :mask-closable="!vectorizing"
      :close-on-esc="!vectorizing"
    >
      <div style="padding: 8px 0">
        <n-progress
          type="line"
          :percentage="progressPercent"
          :status="progressStatus"
          processing
        />
        <n-space justify="space-between" style="margin-top: 10px">
          <n-text depth="3" style="font-size: 12px">{{ progressText }}</n-text>
          <n-text depth="3" style="font-size: 12px">
            {{ vectorizeProgress?.done || 0 }} / {{ vectorizeProgress?.total || 0 }}
          </n-text>
        </n-space>
        <n-text
          v-if="vectorizeProgress?.failed"
          type="error"
          style="font-size: 12px; display: block; margin-top: 6px"
        >
          失败 {{ vectorizeProgress.failed }} 条（详见后端日志）
        </n-text>
      </div>
      <template #footer>
        <n-space justify="end">
          <n-button :disabled="vectorizing" @click="vectorizeShow = false">关闭</n-button>
          <n-button
            v-if="vectorizeProgress && vectorizeProgress.status === 'done'"
            type="primary"
            secondary
            @click="finishVectorize"
          >
            完成
          </n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
  import { ref, reactive, computed, h, onBeforeUnmount } from 'vue';
  import { useMessage, useDialog, NTag } from 'naive-ui';
  import type { UploadFileInfo } from 'naive-ui';
  import { BasicTable, TableAction } from '@/components/Table';
  import {
    PlusOutlined,
    UploadOutlined,
    DownloadOutlined,
    ThunderboltOutlined,
    SettingOutlined,
    SearchOutlined,
    ReloadOutlined,
  } from '@vicons/antd';
  import { saveAs } from 'file-saver';
  import {
    getSampleQueryList,
    delSampleQuery,
    importSampleQuery,
    exportSampleQuery,
    vectorizeSampleQuery,
    vectorizeSampleQueryBatch,
    getVectorizeProgress,
    type SampleQuery,
    type SampleQueryImportResult,
    type SampleQueryVectorizeProgress,
  } from '@/api/system/sampleQuery';
  import EditModal from './components/EditModal.vue';
  import ConfigModal from './components/ConfigModal.vue';
  const message = useMessage();
  const dialog = useDialog();
  const actionRef = ref();
  const searchForm = reactive({
    keyword: '',
    status: null as number | null,
    vectorized: null as number | null,
  });

  const statusOptions = [
    { label: '启用', value: 1 },
    { label: '禁用', value: 2 },
  ];
  const vectorizedOptions = [
    { label: '已向量化', value: 1 },
    { label: '未向量化', value: 2 },
  ];

  function handleSearch() {
    actionRef.value?.reload();
  }

  function handleReset() {
    searchForm.keyword = '';
    searchForm.status = null;
    searchForm.vectorized = null;
    handleSearch();
  }

  const loadDataTable = async (res: any) => {
    const params: any = {
      page: res.page || 1,
      size: res.pageSize || 10,
    };
    if (searchForm.keyword) params.keyword = searchForm.keyword;
    if (searchForm.status != null) params.status = searchForm.status;
    if (searchForm.vectorized != null) params.vectorized = searchForm.vectorized;
    try {
      const response: any = await getSampleQueryList(params);
      // 拦截器非 0 code 不 reject，列表渲染前必须校验数组 + 过滤 null
      if (response && response.code === 0 && response.data) {
        const arr = Array.isArray(response.data.data) ? response.data.data : [];
        return {
          data: arr.filter((x: any) => x && x.id != null),
          total: response.data.total || 0,
        };
      }
      message.error(response?.message || '加载数据失败');
      return { data: [], total: 0 };
    } catch (e) {
      message.error('加载列表失败');
      return { data: [], total: 0 };
    }
  };

  const editModalRef = ref<InstanceType<typeof EditModal> | null>(null);
  function openCreate() {
    editModalRef.value?.openCreate();
  }

  function openEdit(row: SampleQuery) {
    editModalRef.value?.openEdit(row);
  }

  function onSaved() {
    actionRef.value?.reload();
  }

  function handleDelete(row: SampleQuery) {
    dialog.warning({
      title: '确认删除',
      content: '确定删除该样例？同时会删除其已向量化的数据。',
      positiveText: '删除',
      negativeText: '取消',
      onPositiveClick: async () => {
        const res: any = await delSampleQuery(row.id!);
        if (res && res.code === 0) {
          message.success('已删除');
          actionRef.value?.reload();
        } else {
          message.error(res?.message || '删除失败');
        }
      },
    });
  }

  const importShow = ref(false);
  const importing = ref(false);
  const importFileList = ref<UploadFileInfo[]>([]);
  const importResult = ref<SampleQueryImportResult | null>(null);
  function openImport() {
    importFileList.value = [];
    importResult.value = null;
    importing.value = false;
    importShow.value = true;
  }

  function closeImport() {
    if (importing.value) {
      message.warning('导入进行中，请等待完成');
      return;
    }
    importShow.value = false;
    importFileList.value = [];
    importResult.value = null;
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
    importing.value = true;
    importResult.value = null;
    try {
      const res: any = await importSampleQuery(f);
      if (res && res.code === 0 && res.data) {
        importResult.value = res.data;
        message.success('导入完成');
        actionRef.value?.reload();
      } else {
        message.error(res?.message || '导入失败');
      }
    } catch (e) {
      message.error('导入失败');
    } finally {
      importing.value = false;
    }
  }

  const exporting = ref(false);
  async function handleExport() {
    exporting.value = true;
    try {
      const { blob, fileName } = await exportSampleQuery({
        keyword: searchForm.keyword || undefined,
        status: searchForm.status ?? undefined,
        vectorized: searchForm.vectorized ?? undefined,
      });
      saveAs(blob, fileName);
      message.success('导出成功');
    } catch (e: any) {
      message.error(e?.message || '导出失败');
    } finally {
      exporting.value = false;
    }
  }

  async function handleVectorize(row: SampleQuery) {
    try {
      const res: any = await vectorizeSampleQuery(row.id!);
      if (res && res.code === 0) {
        message.success('已向量化');
        actionRef.value?.reload();
      } else {
        message.error(res?.message || '向量化失败');
      }
    } catch (e) {
      message.error('向量化失败');
    }
  }

  const vectorizeShow = ref(false);
  const vectorizing = ref(false);
  const batchVectorizing = ref(false);
  const vectorizeProgress = ref<SampleQueryVectorizeProgress | null>(null);
  let pollTimer: ReturnType<typeof setTimeout> | null = null;
  const progressPercent = computed(() => {
    if (!vectorizeProgress.value || !vectorizeProgress.value.total) return 0;
    return Math.round((vectorizeProgress.value.done / vectorizeProgress.value.total) * 100);
  });
  const progressStatus = computed(() => {
    if (!vectorizeProgress.value) return 'default' as const;
    if (vectorizeProgress.value.status === 'done') return 'success' as const;
    return 'default' as const;
  });
  const progressText = computed(() => {
    if (!vectorizeProgress.value) return '';
    if (vectorizeProgress.value.status === 'done') {
      return `完成（成功 ${vectorizeProgress.value.success} 条${
        vectorizeProgress.value.failed ? '，失败 ' + vectorizeProgress.value.failed + ' 条' : ''
      }）`;
    }
    return '正在向量化，请勿关闭…';
  });

  async function handleVectorizeAll() {
    dialog.warning({
      title: '批量向量化',
      content: '将对所有启用的样例执行向量化（按当前配置的向量模型），时间较长。确定继续？',
      positiveText: '继续',
      negativeText: '取消',
      onPositiveClick: async () => {
        batchVectorizing.value = true;
        vectorizing.value = true;
        try {
          const res: any = await vectorizeSampleQueryBatch();
          if (res && res.code === 0 && res.data && res.data.taskId) {
            message.success('已提交，后台处理中');
            vectorizeShow.value = true;
            vectorizeProgress.value = {
              status: 'processing',
              total: 0,
              done: 0,
              success: 0,
              failed: 0,
            };
            startPoll(res.data.taskId);
          } else {
            vectorizing.value = false;
            message.error(res?.message || '提交失败');
          }
        } catch (e) {
          vectorizing.value = false;
          message.error('提交失败');
        } finally {
          batchVectorizing.value = false;
        }
      },
    });
  }

  function startPoll(taskId: string) {
    stopPoll();
    const poll = async () => {
      try {
        const res: any = await getVectorizeProgress(taskId);
        if (res && res.code === 0 && res.data) {
          vectorizeProgress.value = res.data;
          if (res.data.status === 'done') {
            vectorizing.value = false;
            message.success('向量化完成');
            actionRef.value?.reload();
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

  function finishVectorize() {
    stopPoll();
    vectorizeShow.value = false;
    vectorizeProgress.value = null;
    vectorizing.value = false;
    actionRef.value?.reload();
  }

  const configModalRef = ref<InstanceType<typeof ConfigModal> | null>(null);
  function openConfig() {
    configModalRef.value?.open();
  }

  function onConfigSaved() {
    // 配置保存后无需刷列表（列表只展示样例本身），但提醒用户重新向量化
    message.info('配置已更新，如更换了模型请重新批量向量化');
  }

  const columns = [
    {
      title: '问题',
      key: 'question',
      minWidth: 280,
      ellipsis: { tooltip: true },
    },
    {
      title: '答案',
      key: 'answer',
      minWidth: 320,
      ellipsis: { tooltip: true },
    },
    {
      title: '向量化',
      key: 'vectorized',
      width: 90,
      render(row: SampleQuery) {
        const done = row.vectorized === 1;
        return h(
          NTag,
          { size: 'small', type: done ? 'success' : 'warning', round: true },
          { default: () => (done ? '已向量化' : '未向量化') }
        );
      },
    },
    {
      title: '来源',
      key: 'source',
      width: 90,
      render(row: SampleQuery) {
        const isImport = row.source === 'import';
        return h(
          NTag,
          { size: 'small', type: isImport ? 'info' : 'default', round: true },
          { default: () => (isImport ? '导入' : '手动') }
        );
      },
    },
    {
      title: '状态',
      key: 'status',
      width: 80,
      render(row: SampleQuery) {
        return h(
          NTag,
          { size: 'small', type: row.status === 2 ? 'error' : 'success' },
          { default: () => (row.status === 2 ? '禁用' : '启用') }
        );
      },
    },
  ];

  const actionColumn = reactive({
    width: 220,
    title: '操作',
    key: 'action',
    fixed: 'right',
    render(record: SampleQuery) {
      return h(TableAction, {
        style: 'button',
        actions: createActions(record),
      });
    },
  });

  function createActions(record: SampleQuery) {
    return [
      {
        label: '编辑',
        onClick: () => openEdit(record),
        type: 'primary',
      },
      {
        label: '向量化',
        onClick: () => handleVectorize(record),
        type: 'warning',
      },
      {
        label: '删除',
        onClick: () => handleDelete(record),
        type: 'error',
      },
    ];
  }

  onBeforeUnmount(() => {
    stopPoll();
  });
</script>

<style lang="less" scoped>
  .proCard {
    padding: 20px;
  }
  .search-form {
    margin-bottom: 16px;
    padding: 16px;
    border-radius: 4px;
  }
</style>
