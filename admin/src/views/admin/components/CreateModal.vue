<template>
  <n-modal
    v-model:show="showModal"
    preset="card"
    title="新增员工"
    :bordered="false"
    style="width: 600px"
  >
    <n-form
      ref="formRef"
      :model="formParams"
      :rules="rules"
      label-placement="left"
      label-width="80px"
      class="pt-4"
    >
      <n-form-item label="姓名" path="nickname">
        <n-input v-model:value="formParams.nickname" placeholder="请输入姓名" :maxlength="50" />
      </n-form-item>
      <n-form-item label="登录账号" path="account">
        <n-input
          v-model:value="formParams.account"
          placeholder="请输入登录账号"
          :maxlength="30"
          autocomplete="off"
        />
      </n-form-item>
      <n-form-item label="角色" path="roleId">
        <n-select v-model:value="formParams.roleId" :options="roleOptions" placeholder="请选择角色" />
      </n-form-item>
      <n-form-item label="所属部门" path="deptId">
        <n-tree-select
          v-model:value="formParams.deptId"
          :options="deptTreeOptions"
          key-field="value"
          label-field="label"
          children-field="children"
          default-expand-all
          clearable
          placeholder="请选择所属部门"
        />
      </n-form-item>
      <n-form-item label="密码" path="password">
        <n-input
          v-model:value="formParams.password"
          type="password"
          show-password-on="click"
          placeholder="请输入密码"
          :maxlength="64"
          autocomplete="new-password"
        />
      </n-form-item>
      <n-form-item label="状态" path="status">
        <n-radio-group v-model:value="formParams.status">
          <n-radio :value="1">启用</n-radio>
          <n-radio :value="2">禁用</n-radio>
        </n-radio-group>
      </n-form-item>
    </n-form>
    <template #footer>
      <n-space justify="end">
        <n-button @click="handleCancel">取消</n-button>
        <n-button type="primary" strong secondary :loading="loading" @click="handleSubmit">
          保存
        </n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script lang="ts" setup>
  import { ref, reactive } from 'vue';
  import { useMessage, type FormInst, type FormRules } from 'naive-ui';
  import { addAdmin } from '@/api/system/admin';
  import { getRoleList } from '@/api/system/role';
  import { getDeptTree } from '@/api/system/dept';

  const message = useMessage();
  const showModal = ref(false);
  const loading = ref(false);
  const formRef = ref<FormInst | null>(null);

  const roleOptions = ref<any[]>([]);
  const deptTreeOptions = ref<any[]>([]);

  const formParams = reactive({
    nickname: '',
    account: '',
    roleId: null as number | null,
    deptId: null as number | null,
    password: '',
    status: 1,
  });

  const rules: FormRules = {
    nickname: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
    account: [{ required: true, message: '请输入登录账号', trigger: 'blur' }],
    roleId: [{ required: true, type: 'number', message: '请选择角色', trigger: 'change' }],
    password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  };

  // 获取角色列表
  const fetchRoleList = async () => {
    try {
      const response = await getRoleList({ limit: 100 });
      if (response.code === 0 && response.data?.data) {
        roleOptions.value = response.data.data
          .filter((item: any) => item.id !== 1) // 过滤掉超级管理员角色
          .map((item: any) => ({
            label: item.name,
            value: item.id,
          }));
      }
    } catch (error) {
      console.error('获取角色列表失败:', error);
    }
  };

  // 获取部门树（用于 NTreeSelect）
  // 后端 /dept/tree 返回的已是树（带 children），这里只做字段名映射，不重组结构
  const mapDeptTree = (nodes: any[]): any[] => {
    return nodes.map((node: any) => {
      const mapped: any = {
        key: node.id,
        label: node.name,
        value: node.id,
      };
      if (node.children && node.children.length > 0) {
        mapped.children = mapDeptTree(node.children);
      }
      return mapped;
    });
  };

  const fetchDeptTree = async () => {
    try {
      const response = await getDeptTree();
      if (response.code === 0 && response.data) {
        deptTreeOptions.value = mapDeptTree(response.data);
      }
    } catch (error) {
      console.error('获取部门树失败:', error);
    }
  };

  // 重置表单
  const resetForm = () => {
    formParams.nickname = '';
    formParams.account = '';
    formParams.roleId = null;
    formParams.deptId = null;
    formParams.password = '';
    formParams.status = 1;
  };

  async function openModal() {
    resetForm();
    await Promise.all([fetchRoleList(), fetchDeptTree()]);
    showModal.value = true;
  }

  async function handleSubmit() {
    try {
      await formRef.value?.validate();
    } catch {
      return false;
    }

    loading.value = true;
    try {
      const submitData: any = { ...formParams };
      const response = await addAdmin(submitData);
      if (response.code == 0) {
        message.success('员工添加成功');
        showModal.value = false;
        emit('success');
      } else {
        message.error(response.message);
      }
    } catch (error: any) {
      message.error(error?.data?.message || '添加失败');
    } finally {
      loading.value = false;
    }
    return false;
  }

  function handleCancel() {
    showModal.value = false;
  }

  const emit = defineEmits(['success']);

  defineExpose({ openModal });
</script>
