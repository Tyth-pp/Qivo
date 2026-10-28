import axios from 'axios'
import type { InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { clearSessionIfCurrent, domainForPath, readSession } from '@/utils/session'
import type { Domain } from '@/utils/session'
import router from '@/router'
import { readableMessage } from '@/utils/feedback'
declare module 'axios' { interface AxiosRequestConfig { feedback?: 'inline' } }
const request = axios.create({ baseURL: '/api', timeout: 10000 })
function requestDomain(url = ''): Domain {
  if (url.startsWith('/admin/')) return 'ADMIN'
  if (url.startsWith('/fore/')) return 'USER'
  return domainForPath(window.location.pathname)
}
type SessionRequest = InternalAxiosRequestConfig & { sessionDomain?: Domain; sessionToken?: string }
const publicRequest = (url = '', method = 'get') => {
  if (['/common/auth/login', '/common/auth/adminLogin', '/common/captcha/get', '/fore/user/register'].includes(url)) return true
  return method.toLowerCase() === 'get' && (/^\/fore\/index\//.test(url) || url === '/fore/product/list' || /^\/fore\/(product\/detail|comment\/list)\/[^/]+$/.test(url))
}
request.interceptors.request.use(config => {
  const captured = config as SessionRequest
  captured.sessionDomain = requestDomain(config.url)
  if (!publicRequest(config.url, config.method)) {
    const session = readSession(captured.sessionDomain)
    captured.sessionToken = session?.token
    if (session) config.headers.Authorization = `Bearer ${session.token}`
  }
  return config
})
function handleUnauthorized(config?: SessionRequest) {
  if (!config || publicRequest(config.url, config.method)) return
  const domain = config.sessionDomain || requestDomain(config.url)
  if (!clearSessionIfCurrent(domain, config.sessionToken)) return
  const login = domain === 'ADMIN' ? '/admin/login' : '/login'
  const current = router.currentRoute.value
  if (domain === domainForPath(current.path) && current.meta.requiresAuth) {
    void router.replace({ path: login, query: { redirect: current.fullPath } })
  }
}
function passwordMismatch(config: SessionRequest | undefined, code: unknown) {
  return code === 1002 && ['/fore/user/password', '/admin/password'].includes(config?.url || '')
}
function refreshAdminPermissions(config?: SessionRequest) {
  if (config?.sessionDomain === 'ADMIN' && config.url !== '/admin/access' && config.sessionToken === readSession('ADMIN')?.token) {
    window.dispatchEvent(new Event('qivo-admin-permissions-stale'))
  }
}
request.interceptors.response.use(response => {
  const data = response.data
  if (data.code === undefined || data.code === 200) return data
  if (data.code === 401) handleUnauthorized(response.config as SessionRequest)
  if (data.code === 403) refreshAdminPermissions(response.config as SessionRequest)
  const message = readableMessage(data.msg)
  if (response.config.feedback !== 'inline') ElMessage.error(message)
  return Promise.reject(Object.assign(new Error(message), { userMessage: message, response }))
}, error => {
  const status = error.response?.status
  if (status === 401 && !passwordMismatch(error.config, error.response?.data?.code)) handleUnauthorized(error.config as SessionRequest | undefined)
  if (status === 403) refreshAdminPermissions(error.config as SessionRequest | undefined)
  const fallback = error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : error.response ? '服务暂时繁忙，请稍后重试' : '网络连接不稳定，请检查网络后重试'
  error.userMessage = readableMessage(error.response?.data?.msg, fallback)
  if (error.config?.feedback !== 'inline') ElMessage.error(error.userMessage)
  return Promise.reject(error)
})
export default request
