export type Domain = 'USER' | 'ADMIN'
export interface LoginSession {
  token: string
  userId?: number
  adminId?: number
  username: string
  avatar?: string
  realName?: string
}
export function domainForPath(path: string): Domain {
  return path === '/admin' || path.startsWith('/admin/') ? 'ADMIN' : 'USER'
}
export function safeRedirect(value: unknown, domain: Domain): string {
  const fallback = domain === 'ADMIN' ? '/admin/dashboard' : '/'
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || /[\\\s\u0000-\u001f]/.test(value)) return fallback
  try {
    const url = new URL(value, 'https://qivo.invalid')
    const decoded = decodeURIComponent(url.pathname)
    if (url.origin !== 'https://qivo.invalid' || decoded.startsWith('//') || /[\\\u0000-\u001f]/.test(decoded)) return fallback
    const normalized = new URL(decoded, 'https://qivo.invalid')
    const path = normalized.pathname.replace(/\/+$/, '') || '/'
    if (domainForPath(path) !== domain || ['/login', '/register', '/admin/login'].includes(path)) return fallback
    return normalized.pathname + url.search + url.hash
  } catch { return fallback }
}
const key = (domain: Domain) => `qivo.session.v2.${domain}`
// 不迁移旧的浏览器共享会话，避免新标签页重新继承旧账号。
export function initializeTabSession() {
  for (const domain of ['USER', 'ADMIN'] as const) localStorage.removeItem(key(domain))
  // window.open 可能复制 opener 的 sessionStorage；新窗口首次导航时清除副本。
  const navigation = performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming | undefined
  if (window.opener && navigation?.type === 'navigate') {
    for (const domain of ['USER', 'ADMIN'] as const) sessionStorage.removeItem(key(domain))
    window.opener = null
  }
}
export function readSession(domain: Domain, storage: Storage = sessionStorage): LoginSession | null {
  try {
    const value = JSON.parse(storage.getItem(key(domain)) || 'null')
    return value && typeof value.token === 'string' && value.token.trim() && typeof value.username === 'string' && value.username.trim() ? value : null
  } catch { return null }
}
export function saveSession(domain: Domain, value: LoginSession, storage: Storage = sessionStorage) {
  storage.setItem(key(domain), JSON.stringify(value))
  notify()
}
export function clearSession(domain: Domain, storage: Storage = sessionStorage) {
  storage.removeItem(key(domain))
  notify()
}
export function clearSessionIfCurrent(domain: Domain, token: string | undefined, storage: Storage = sessionStorage): boolean {
  if (!token || readSession(domain, storage)?.token !== token) return false
  clearSession(domain, storage)
  return true
}
function notify() {
  if (typeof window !== 'undefined') window.dispatchEvent(new Event('qivo-session-changed'))
}
