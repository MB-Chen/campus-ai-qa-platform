<template>
  <div>
    <div class="n-layout-page-header">
      <n-card :bordered="false" title="员工管理"> 管理系统中的员工管理的增删改查操作 </n-card>
    </div>
    <n-card :bordered="false" class="mt-4 proCard">
      <!-- 搜索表单 -->
      <n-form inline :model="searchForm" label-placement="left" class="search-form">
        <n-form-item label="姓名">
          <n-input
            v-model:value="searchForm.nickname"
            placeholder="请输入姓名"
            clearable
            style="width: 200px"
          />
        </n-form-item>
        <n-form-item label="登录账号">
          <n-input
            v-model:value="searchForm.account"
            placeholder="请输入登录账号"
            clearable
            style="width: 200px"
          />
        </n-form-item>
        <n-form-item>
          <n-space>
            <n-button strong secondary type="primary" @click="handleSearch">
              <template #icon>
                <n-icon><SearchOutlined /></n-icon>
              </template>
              搜索
            </n-button>
            <n-button @click="handleReset">
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
        :row-key="(row) => row.id"
        :action-column="actionColumn"
        :scroll-x="1200"
        @update:checked-row-keys="handleCheckedRowKeysChange"
      >
        <template #tableTitle>
          <n-button strong secondary type="primary" @click="addAdmin">
            <template #icon>
              <n-icon>
                <PlusOutlined />
              </n-icon>
            </template>
            新增员工
          </n-button>
        </template>
      </BasicTable>
    </n-card>

    <!-- 添加/编辑弹窗 -->
    <CreateModal ref="createModalRef" @success="handleCreateSuccess" />
    <EditModal ref="editModalRef" @success="handleEditSuccess" />
  </div>
</template>

<script lang="ts" setup>
  import { reactive, ref, h } from 'vue';
  import { useMessage, useDialog } from 'naive-ui';
  import { BasicTable, TableAction } from '@/components/Table';
  import { getAdminList, delAdmin } from '@/api/system/admin';
  import { columns } from './components/columns';
  import { PlusOutlined, SearchOutlined, ReloadOutlined } from '@vicons/antd';
  import CreateModal from './components/CreateModal.vue';
  import EditModal from './components/EditModal.vue';

  const message = useMessage();
  const dialog = useDialog();
  const actionRef = ref();
  const createModalRef = ref();
  const editModalRef = ref();

  // 搜索表单
  const searchForm = reactive({
    nickname: '',
    account: '',
  });

  // 操作列配置
  const actionColumn = reactive({
    width: 200,
    title: '操作',
    key: 'action',
    fixed: 'right',
    render(record) {
      return h(TableAction, {
        style: 'button',
        actions: createActions(record),
      });
    },
  });

  // 创建操作按钮
  function createActions(record) {
    return [
      {
        label: '编辑',
        onClick: handleEdit.bind(null, record),
        ifShow: () => {
          // 超级管理员不允许编辑
          return record.id !== 1;
        },
        type: 'primary',
      },
      {
        label: '删除',
        onClick: handleDelete.bind(null, record),
        ifShow: () => {
          // 超级管理员不允许删除
          return record.id !== 1;
        },
        type: 'error',
      },
    ];
  }

  // 表格数据加载函数
  const loadDataTable = async (res) => {
    const params = {
      nickname: '',
      account: '',
      page: res.page || 1,
      limit: res.pageSize || 10,
    };
    if (searchForm.nickname) params.nickname = searchForm.nickname;
    if (searchForm.account) params.account = searchForm.account;
    try {
      const response = await getAdminList(params);
      if (response.code === 0) {
        return {
          data: response.data?.data || [],
          total: response.data?.total || 0,
        };
      } else {
        message.error(response.message || '加载数据失败');
        return { data: [], total: 0 };
      }
    } catch (error) {
      message.error('加载数据失败');
      return { data: [], total: 0 };
    }
  };

  // 搜索
  function handleSearch() {
    actionRef.value?.reload();
  }

  // 重置
  function handleReset() {
    searchForm.nickname = '';
    searchForm.account = '';
    handleSearch();
  }

  // 选择行变化
  function handleCheckedRowKeysChange(keys) {
    console.log('选中行:', keys);
  }

  // 添加管理员
  function addAdmin() {
    createModalRef.value.openModal();
  }

  // 添加成功回调
  function handleCreateSuccess() {
    actionRef.value?.reload();
  }

  // 编辑成功回调
  function handleEditSuccess() {
    actionRef.value?.reload();
  }

  // 编辑员工
  function handleEdit(record: Recordable) {
    editModalRef.value.open(record);
  }

  // 删除员工
  function handleDelete(record: Recordable) {
    dialog.warning({
      title: '确认删除',
      content: `确定要删除员工 "${record.nickname}" 吗？删除后不可恢复。`,
      positiveText: '确定',
      negativeText: '取消',
      onPositiveClick: () => {
        performDelete(record);
      },
    });
  }

  // 执行删除
  async function performDelete(record: Recordable) {
    try {
      const response = await delAdmin(record.id);
      if (response) {
        message.success('员工删除成功');
        actionRef.value?.reload();
      } else {
        message.error('员工删除失败');
      }
    } catch (error) {
      console.error('删除员工失败:', error);
      message.error('删除员工失败，请重试');
    }
  }
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
