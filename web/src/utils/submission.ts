export interface PendingSubmission { key: string; path: '/fore/order/create' | '/fore/order/buy-now'; payload: Record<string, unknown> }
const slot = (owner: string) => `qivo.pending-order.v1.${owner}`
export function readPending(owner: string, storage: Storage): PendingSubmission | null {
  try { const p = JSON.parse(storage.getItem(slot(owner)) || 'null'); return p && typeof p.key === 'string' && ['/fore/order/create', '/fore/order/buy-now'].includes(p.path) && p.payload && typeof p.payload === 'object' ? p : null }
  catch { return null }
}
export function prepareSubmission(owner: string, path: PendingSubmission['path'], payload: Record<string, unknown>, storage: Storage): PendingSubmission {
  const pending = readPending(owner, storage)
  if (pending) {
    if (pending.path !== path || JSON.stringify(pending.payload) !== JSON.stringify(payload)) throw new Error('上一次下单结果尚未确认，请先重试确认上次请求')
    return pending
  }
  const result = { key: crypto.randomUUID(), path, payload }
  storage.setItem(slot(owner), JSON.stringify(result))
  return result
}
export function clearPending(owner: string, key: string, storage: Storage) {
  if (readPending(owner, storage)?.key === key) storage.removeItem(slot(owner))
}
