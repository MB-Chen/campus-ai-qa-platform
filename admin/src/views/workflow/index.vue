<template>
  <div>
    <div class="n-layout-page-header">
      <n-card :bordered="false" title="编排">
        可视化流程编排：拖拽 Start → 知识检索/LLM/意图分类/条件分支/智能体 → 回复 节点， 串联 DAG
        并调试，支持 SSE 流式执行与执行详情回溯
      </n-card>
    </div>

    <n-card :bordered="false" class="mt-4 proCard">
      <div class="toolbar">
        <n-input
          v-model:value="keyword"
          class="filter-input"
          placeholder="按名称筛选编排"
          clearable
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <n-icon><SearchOutlined /></n-icon>
          </template>
        </n-input>
        <n-button type="primary" secondary @click="handleSearch">
          <template #icon
            ><n-icon><SearchOutlined /></n-icon
          ></template>
          搜索
        </n-button>
        <n-button type="primary" secondary @click="openCreate">+ 新建编排</n-button>
      </div>

      <n-spin :show="loading">
        <n-empty
          v-if="!loading && list.length === 0"
          description="暂无编排，点击右上角「新建编排」"
        />
        <div v-else class="kb-grid">
          <div
            v-for="wf in list"
            :key="wf.id"
            class="kb-card"
            :class="{ disabled: wf.status === 2 }"
          >
            <div class="kb-head">
              <n-icon :component="DeploymentUnitOutlined" class="kb-avatar" />
              <span class="kb-name">{{ wf.name || '未命名编排' }}</span>
              <n-tag :type="wf.status === 2 ? 'default' : 'success'" size="small" round>
                {{ wf.status === 2 ? '禁用' : '正常' }}
              </n-tag>
            </div>
            <div class="kb-desc">{{ wf.description || '暂无描述' }}</div>
            <div class="kb-meta">
              <span class="meta-item">
                <span class="meta-label">更新时间</span>
                <span class="meta-value">{{ formatTime(wf.updatedAt) }}</span>
              </span>
            </div>
            <div class="kb-actions">
              <n-button class="kb-action-btn" size="tiny" quaternary type="primary" @click="openEdit(wf)">
                <template #icon><n-icon><EditOutlined /></n-icon></template>
                编辑
              </n-button>
              <n-button class="kb-action-btn" size="tiny" quaternary type="info" @click="openDebug(wf)">
                <template #icon><n-icon><BugOutlined /></n-icon></template>
                调试
              </n-button>
              <n-button class="kb-action-btn" size="tiny" quaternary @click="handleCopy(wf)">
                <template #icon><n-icon><CopyOutlined /></n-icon></template>
                复制
              </n-button>
              <n-button class="kb-action-btn" size="tiny" quaternary type="error" @click="handleDelete(wf)">
                <template #icon><n-icon><DeleteOutlined /></n-icon></template>
                删除
              </n-button>
            </div>
          </div>
        </div>
      </n-spin>

      <div class="mt-4" style="display: flex; justify-content: flex-end">
        <n-pagination
          v-model:page="page"
          v-model:page-size="size"
          :item-count="total"
          show-size-picker
          :page-sizes="[10, 20, 50]"
          show-quick-jumper
          @update:page="loadList"
          @update:page-size="onSizeChange"
        />
      </div>
    </n-card>

    <!-- 新建编排弹窗 -->
    <n-modal v-model:show="showCreate" preset="card" title="新建编排" style="width: 520px">
      <n-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-placement="left"
        label-width="100px"
      >
        <n-form-item label="编排名称" path="name">
          <n-input
            v-model:value="createForm.name"
            placeholder="请输入编排名称"
            :maxlength="50"
            show-count
          />
        </n-form-item>
        <n-form-item label="描述" path="description">
          <n-input
            v-model:value="createForm.description"
            type="textarea"
            :rows="3"
            placeholder="可选，描述该编排用途"
            :maxlength="255"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showCreate = false">取消</n-button>
          <n-button type="primary" secondary :loading="creating" @click="handleCreateConfirm"
            >确定</n-button
          >
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
  import { ref, reactive, onMounted } from 'vue';
  import { useRouter } from 'vue-router';
  import { useMessage, useDialog, type FormInst, type FormRules } from 'naive-ui';
  import {
    SearchOutlined,
    DeploymentUnitOutlined,
    EditOutlined,
    BugOutlined,
    CopyOutlined,
    DeleteOutlined,
  } from '@vicons/antd';
  import {
    getWorkflowList,
    addWorkflow,
    delWorkflow,
    copyWorkflow,
    type Workflow,
  } from '@/api/system/workflow';

  const message = useMessage();
  const dialog = useDialog();
  const router = useRouter();

  const loading = ref(false);
  const list = ref<Workflow[]>([]);
  const keyword = ref('');
  const page = ref(1);
  const size = ref(10);
  const total = ref(0);

  // 新建编排弹窗
  const showCreate = ref(false);
  const creating = ref(false);
  const createFormRef = ref<FormInst | null>(null);
  const createForm = reactive({
    name: '',
    description: '',
  });
  const createRules: FormRules = {
    name: [{ required: true, message: '请输入编排名称', trigger: ['blur', 'input'] }],
  };

  async function loadList() {
    loading.value = true;
    try {
      const res: any = await getWorkflowList({
        keyword: keyword.value || undefined,
        page: page.value,
        size: size.value,
      });
      if (res && res.code === 0 && res.data) {
        const arr = Array.isArray(res.data.data) ? res.data.data : [];
        list.value = arr.filter((x: any) => x && x.id != null) as Workflow[];
        total.value = res.data.total || 0;
      } else {
        list.value = [];
        total.value = 0;
      }
    } catch (e) {
      message.error('加载编排列表失败');
      list.value = [];
      total.value = 0;
    } finally {
      loading.value = false;
    }
  }

  function handleSearch() {
    page.value = 1;
    loadList();
  }
  function onSizeChange(s: number) {
    size.value = s;
    page.value = 1;
    loadList();
  }

  async function openCreate() {
    showCreate.value = true;
  }

  async function handleCreateConfirm() {
    try {
      await createFormRef.value?.validate();
    } catch (e) {
      return;
    }
    creating.value = true;
    try {
      const res: any = await addWorkflow({
        name: createForm.name.trim(),
        description: createForm.description || undefined,
      });
      if (res && res.code === 0 && res.data?.id) {
        showCreate.value = false;
        message.success('编排已创建');
        router.push('/workflow/edit?id=' + res.data.id).catch(() => {});
      } else {
        message.error(res?.message || '新建失败');
      }
    } catch (e) {
      message.error('新建失败');
    } finally {
      creating.value = false;
    }
  }
  function openEdit(wf: Workflow) {
    router.push('/workflow/edit?id=' + wf.id).catch(() => {});
  }
  function openDebug(wf: Workflow) {
    router.push('/workflow/edit?id=' + wf.id + '&debug=1').catch(() => {});
  }

  async function handleCopy(wf: Workflow) {
    const res: any = await copyWorkflow(wf.id!);
    if (res && res.code === 0) {
      message.success('已复制');
      await loadList();
    } else {
      message.error(res?.message || '复制失败');
    }
  }

  function handleDelete(wf: Workflow) {
    dialog.warning({
      title: '确认删除',
      content: `确定删除编排「${wf.name}」？其运行时数据将一并清除，不可撤销。`,
      positiveText: '删除',
      negativeText: '取消',
      onPositiveClick: async () => {
        const res: any = await delWorkflow(wf.id!);
        if (res && res.code === 0) {
          message.success('已删除');
          await loadList();
        } else {
          message.error(res?.message || '删除失败');
        }
      },
    });
  }

  function formatTime(s?: string): string {
    if (!s) return '-';
    // 截到分钟，并把 ISO 的 T 换成空格
    const t = s.length > 16 ? s.substring(0, 16) : s;
    return t.replace('T', ' ');
  }

  onMounted(() => {
    loadList();
  });
</script>

<style lang="less" scoped>
  .toolbar {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 12px;
    margin-bottom: 16px;
  }
  .filter-input {
    max-width: 280px;
  }
  .kb-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: 14px;
  }
  .kb-card {
    position: relative;
    overflow: hidden;
    border: 1px solid #eee;
    border-radius: 8px;
    padding: 14px;
    background: linear-gradient(135deg, #ffffff 0%, rgba(5, 150, 105, 0.04) 100%);
    transition: all 0.2s;
    &:hover {
      border-color: #059669;
      box-shadow: 0 4px 12px rgba(5, 150, 105, 0.12);
    }
    &.disabled {
      opacity: 0.6;
      background: #fafafa;
    }
  }
  .kb-head {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
  }
  .kb-avatar {
    font-size: 20px;
    color: #059669;
  }
  .kb-name {
    font-size: 15px;
    font-weight: 600;
    color: #333;
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .kb-desc {
    color: #888;
    font-size: 13px;
    line-height: 1.5;
    margin-bottom: 10px;
    height: 40px;
    overflow: hidden;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }
  .kb-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    padding: 8px 0;
    border-top: 1px dashed #eee;
    border-bottom: 1px dashed #eee;
    margin-bottom: 10px;
  }
  .meta-item {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .meta-label {
    font-size: 11px;
    color: #aaa;
  }
  .meta-value {
    font-size: 13px;
    color: #333;
  }
  .kb-actions {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 6px;
  }
  .kb-action-btn {
    min-width: 0;
  }
</style>
