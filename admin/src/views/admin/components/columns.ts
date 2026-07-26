import { h } from 'vue';
import { NTag } from 'naive-ui';

export const columns = [
  {
    title: 'ID',
    key: 'id',
  },
  {
    title: '登录账号',
    key: 'account',
  },
  {
    title: '姓名',
    key: 'nickname',
  },
  {
    title: '角色',
    key: 'roleName',
  },
  {
    title: '部门',
    key: 'deptName',
  },
  {
    title: '状态',
    key: 'status',
    render(row) {
      return h(
        NTag,
        {
          type: row.status === 1 ? 'success' : 'error',
        },
        {
          default: () => (row.status === 1 ? '启用' : '禁用'),
        }
      );
    },
  },
  {
    title: '创建时间',
    key: 'createTime',
  },
];
