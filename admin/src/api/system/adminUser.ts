import { Alova } from '@/utils/http/alova/index';

/** 管理员账号（AdminUserVo） */
export interface AdminUser {
  id: number;
  account: string;
  nickname: string;
  avatar?: string;
  status: number; // 1正常 2禁用
  lastLoginIp?: string;
  lastLoginTime?: string;
  createTime?: string;
}

/** 新增/编辑入参（AdminUserValidate）：id 编辑时必填；password 编辑时留空=不改 */
export interface AdminUserPayload {
  id?: number;
  account: string;
  nickname: string;
  password?: string;
  status?: number;
  avatar?: string;
}

// 账号列表（分页 + 模糊搜索）
export function getAdminUserList(params: { page: number; limit: number; nickname?: string; account?: string }) {
  return Alova.Get<any>('/admin/index', { params });
}

// 新增账号
export function addAdminUser(data: AdminUserPayload) {
  return Alova.Post<any>('/admin/add', data);
}

// 编辑账号（改昵称/状态/密码）
export function editAdminUser(data: AdminUserPayload) {
  return Alova.Post<any>('/admin/edit', data);
}

// 删除账号（超管 id=1 后端会拒绝）
export function delAdminUser(id: number) {
  return Alova.Post<any>('/admin/del', { id });
}
