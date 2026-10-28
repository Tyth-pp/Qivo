import request from './request';
export const getAdminProfile = () => request.get('/admin/profile');
export const updateAdminProfile = (data: { realName: string; avatar: string }) => request.put('/admin/profile', data);
export const changeOwnPassword = (admin: boolean, oldPassword: string, newPassword: string) =>
  request.put(admin ? '/admin/password' : '/fore/user/password', { oldPassword, newPassword }, { feedback: 'inline' });
export function uploadAvatar(file: File) {
  const data = new FormData(); data.append('file', file);
  return request.post('/common/upload/avatar', data, { feedback: 'inline' });
}
