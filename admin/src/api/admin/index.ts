import { Alova } from '@/utils/http/alova/index';

// 获取商户超管信息
export function getMerchantAdmin(merchant_id: number) {
  return Alova.Get<any>('.merchant/getMerchantAdmin', { params: { merchant_id } });
}

// 修改管理员密码
export function updateAdminPassword(params: any) {
  return Alova.Post<any>('.merchant/updatePassword', params);
}
