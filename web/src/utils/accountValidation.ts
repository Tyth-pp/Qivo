export const usernameHint = '3–50 位字母、数字或下划线，用于登录';
export function usernameError(value: string): string {
  if (!value) return '请先设置用户名';
  if (/[\u3400-\u9fff]/u.test(value)) return '用户名暂不支持中文，请使用字母、数字或下划线';
  return /^[a-zA-Z0-9_]{3,50}$/.test(value) ? '' : '用户名需为3–50位，只能包含字母、数字或下划线';
}
export function passwordError(value: string, label = '密码'): string {
  if (!value) return `请填写${label}`;
  if (value.length < 8) return `${label}至少需要8位`;
  if (new TextEncoder().encode(value).length > 72) return `${label}过长，请减少字符；包含中文或特殊字符时可适当缩短`;
  return '';
}
export interface RegistrationForm { username: string; password: string; password2: string; email: string; phone: string }
export function registrationErrors(form: RegistrationForm): Record<keyof RegistrationForm, string> {
  return {
    username: usernameError(form.username), password: passwordError(form.password),
    password2: !form.password2 ? '请再次输入密码' : form.password2 !== form.password ? '两次输入的密码不一致，请重新确认' : '',
    email: form.email && (form.email.length > 100 || !/^[^\s@]+@[^\s@]+$/.test(form.email)) ? '邮箱格式不正确，例如 name@example.com' : '',
    phone: form.phone.length > 20 ? '联系电话不能超过20个字符' : ''
  };
}
