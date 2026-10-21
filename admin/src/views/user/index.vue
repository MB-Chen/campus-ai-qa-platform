<template>
  <div>
    <div class="n-layout-page-header">
      <n-card :bordered="false" title="用户管理">
        平台账号统一由管理员在此维护：新增账号、修改昵称与密码、启用/禁用。
        全部接口经 JWT 登录鉴权，禁用账号无法登录系统。
      </n-card>
    </div>

    <n-card :bordered="false" class="mt-4 proCard">
      <div class="user-toolbar">
        <n-space align="center" justify="space-between" style="width: 100%">
          <n-space align="center">
            <n-input
              v-model:value="query.account"
              placeholder="按账号搜索"
              clearable
              style="width: 180px"
              @keyup.enter="reload"
            />
            <n-input
              v-model:value="query.nickname"
              placeholder="按昵称搜索"
              clearable
              style="width: 180px"
              @keyup.enter="reload"
            />
            <n-button secondary @click="reload" :loading="loading">
              <template #icon><n-icon><SearchOutlined /></n-icon></template>
              查询
            </n-button>
          </n-space>
          <n-button type="primary" secondary @click="openCreate">
            <template #icon><n-icon><PlusOutlined /></n-icon></template>
            新增账号
          </n-button>
        </n-space>
      </div>

      <n-data-table
        :columns="columns"
        :data="list"
        :loading="loading"
        remote
        :pagination="pagination"
        :row-key="(row: AdminUser) => row.id"
        :scroll-x="900"
      />
    </n-card>

    <!-- 新增/编辑弹窗 -->
    <n-modal
      v-model:show="showModal"
      preset="card"
      :title="form.id ? '编辑账号' : '新增账号'"
      style="width: 460px"
    >
      <n-form ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="80">
        <n-form-item label="账号" path="account">
          <n-input v-model:value="form.account" placeholder="2-30 位登录账号" :disabled="!!form.id" />
        </n-form-item>
        <n-form-item label="昵称" path="nickname">
          <n-input v-model:value="form.nickname" placeholder="显示名称" />
        </n-form-item>
        <n-form-item :label="form.id ? '新密码' : '密码'" :path="form.id ? undefined : 'password'">
          <n-input
            v-model:value="form.password"
            type="password"
            :placeholder="form.id ? '留空表示不修改密码' : '默认 123456'"
          />
        </n-form-item>
        <n-form-item label="状态" path="status">
          <n-radio-group v-model:value="form.status">
            <n-radio :value="1">正常</n-radio>
            <n-radio :value="2">禁用</n-radio>
          </n-radio-group>
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showModal = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="handleSave">保存</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
  import { ref, reactive, computed, onMounted, h } from 'vue';
  import { useMessage, useDialog, NSwitch, NButton, NSpace } from 'naive-ui';
  import { SearchOutlined, PlusOutlined } from '@vicons/antd';
  import {
    getAdminUserList,
    addAdminUser,
    editAdminUser,
    delAdminUser,
    type AdminUser,
    type AdminUserPayload,
  } from '@/api/system/adminUser';

  const message = useMessage();
  const dialog = useDialog();

  const loading = ref(false);
  const saving = ref(false);
  const list = ref<AdminUser[]>([]);
  const total = ref(0);
  const page = ref(1);
  const limit = ref(10);
  const query = reactive({ account: '', nickname: '' });

  const showModal = ref(false);
  const formRef = ref();
  const form = reactive<AdminUserPayload>({
    id: undefined,
    account: '',
    nickname: '',
    password: '',
    status: 1,
  });

  const rules = {
    account: [
      { required: true, message: '账号不能为空', trigger: 'blur' },
      { min: 2, max: 30, message: '账号长度 2-30', trigger: 'blur' },
    ],
    nickname: [{ required: true, message: '昵称不能为空', trigger: 'blur' }],
    password: [{ required: true, message: '请设置初始密码', trigger: 'blur' }],
  };

  const pagination = computed(() => ({
    page: page.value,
    pageSize: limit.value,
    itemCount: total.value,
    showSizePicker: true,
    pageSizes: [10, 20, 50],
    onChange: (p: number) => {
      page.value = p;
      load();
    },
    onUpdatePageSize: (ps: number) => {
      limit.value = ps;
      page.value = 1;
      load();
    },
  }));

  const columns = [
    { title: 'ID', key: 'id', width: 70 },
    { title: '账号', key: 'account', width: 160 },
    { title: '昵称', key: 'nickname', width: 160 },
    {
      title: '状态',
      key: 'status',
      width: 110,
      render: (row: AdminUser) =>
        h(NSwitch, {
          size: 'small',
          value: row.status === 1,
          'onUpdate:value': (v: boolean) => handleToggle(row, v),
        }),
    },
    { title: '最后登录时间', key: 'lastLoginTime', width: 180, render: (r: AdminUser) => r.lastLoginTime || '-' },
    { title: '创建时间', key: 'createTime', width: 180, render: (r: AdminUser) => r.createTime || '-' },
    {
      title: '操作',
      key: 'actions',
      width: 180,
      render: (row: AdminUser) =>
        h(NSpace, { size: 'small' }, () => [
          h(NButton, { size: 'tiny', secondary: true, onClick: () => openEdit(row) }, () => '编辑'),
          row.id === 1
            ? null
            : h(
                NButton,
                { size: 'tiny', type: 'error', secondary: true, onClick: () => handleDelete(row) },
                () => '删除'
              ),
        ]),
    },
  ];

  async function load() {
    loading.value = true;
    try {
      const res = await getAdminUserList({
        page: page.value,
        limit: limit.value,
        account: query.account || undefined,
        nickname: query.nickname || undefined,
      });
      if (res?.code === 0) {
        list.value = res.data?.data ?? [];
        total.value = res.data?.total ?? 0;
      } else {
        message.error(res?.message || '加载失败');
      }
    } catch (e) {
      message.error('加载账号列表失败');
    } finally {
      loading.value = false;
    }
  }

  function reload() {
    page.value = 1;
    load();
  }

  function resetForm() {
    form.id = undefined;
    form.account = '';
    form.nickname = '';
    form.password = '';
    form.status = 1;
  }

  function openCreate() {
    resetForm();
    showModal.value = true;
  }

  function openEdit(row: AdminUser) {
    form.id = row.id;
    form.account = row.account;
    form.nickname = row.nickname;
    form.password = '';
    form.status = row.status;
    showModal.value = true;
  }

  async function handleSave() {
    await formRef.value?.validate();
    saving.value = true;
    try {
      const res = form.id
        ? await editAdminUser({ id: form.id, account: form.account, nickname: form.nickname, status: form.status, password: form.password || undefined })
        : await addAdminUser({ account: form.account, nickname: form.nickname, password: form.password, status: form.status });
      if (res?.code === 0) {
        message.success(form.id ? '已保存' : '已新增，初始密码即页面上填写的密码');
        showModal.value = false;
        await load();
      } else {
        message.error(res?.message || '保存失败');
      }
    } finally {
      saving.value = false;
    }
  }

  async function handleToggle(row: AdminUser, enabled: boolean) {
    const res = await editAdminUser({
      id: row.id,
      account: row.account,
      nickname: row.nickname,
      status: enabled ? 1 : 2,
    });
    if (res?.code === 0) {
      message.success(enabled ? '已启用' : '已禁用');
      await load();
    } else {
      message.error(res?.message || '操作失败');
    }
  }

  function handleDelete(row: AdminUser) {
    dialog.warning({
      title: '确认删除',
      content: `确定删除账号「${row.account}」？`,
      positiveText: '删除',
      negativeText: '取消',
      onPositiveClick: async () => {
        const res = await delAdminUser(row.id);
        if (res?.code === 0) {
          message.success('已删除');
          await load();
        } else {
          message.error(res?.message || '删除失败');
        }
      },
    });
  }

  onMounted(load);
</script>

<style lang="less" scoped>
  .proCard {
    padding: 20px;
  }
  .user-toolbar {
    margin-bottom: 16px;
  }
</style>
