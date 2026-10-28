import request from './request'

/** 获取验证码 */
export function getCaptcha() {
  return request.get('/common/captcha/get', { feedback: 'inline' })
}

/** 用户登录 */
export function userLogin(data: { username: string; password: string; captchaKey: string; captchaCode: string }) {
  return request.post('/common/auth/login', data, { feedback: 'inline' })
}

/** 管理员登录 */
export function adminLogin(data: { username: string; password: string; captchaKey: string; captchaCode: string }) {
  return request.post('/common/auth/adminLogin', data, { feedback: 'inline' })
}

/** 用户注册 */
export function userRegister(data: { username: string; password: string; email?: string; phone?: string }) {
  return request.post('/fore/user/register', data, { feedback: 'inline' })
}
