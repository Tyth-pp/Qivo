import request from './request'
import { readSession } from '@/utils/session'
import { prepareSubmission, readPending, clearPending } from '@/utils/submission'
import type { PendingSubmission } from '@/utils/submission'
import { ElMessage } from 'element-plus'
const owner = () => String(readSession('USER')?.userId || readSession('USER')?.username || '')
export const pendingOrder = () => readPending(owner(), sessionStorage)
async function sendPending(p: PendingSubmission) {
  const user = owner()
  try {
    const response = await request.post(p.path, p.payload, { headers: { 'Idempotency-Key': p.key } })
    clearPending(user, p.key, sessionStorage)
    return response
  } catch (error: any) {
    if ([400, 404, 409].includes(error.response?.status) && error.response?.data?.code !== undefined) clearPending(user, p.key, sessionStorage)
    throw error
  }
}
export async function retryPendingOrder() {
  const p = pendingOrder()
  if (!p) throw new Error('没有待确认的下单请求')
  return sendPending(p)
}
function submit(path: PendingSubmission['path'], payload: Record<string, unknown>) {
  try { return sendPending(prepareSubmission(owner(), path, payload, sessionStorage)) }
  catch (error: any) { ElMessage.warning(error.message); return Promise.reject(error) }
}

export function createOrder(addressId: number, cartItemIds: number[], expectedTotal: string, remark?: string) {
  return submit('/fore/order/create', { addressId, cartItemIds, expectedTotal, remark })
}

export function createBuyNowOrder(skuId: number, quantity: number, addressId: number, expectedTotal: string, remark?: string) {
  return submit('/fore/order/buy-now', { skuId, quantity, addressId, expectedTotal, remark })
}

export function payOrder(orderId: number) {
  return request.post('/fore/order/pay', { orderId })
}

export function cancelOrder(orderId: number) {
  return request.put(`/fore/order/cancel/${orderId}`)
}

export function confirmOrder(orderId: number) {
  return request.put(`/fore/order/confirm/${orderId}`)
}

export function getOrderList(params: Record<string, any>) {
  return request.get('/fore/order/list', { params })
}

export function getOrderDetail(orderId: number) {
  return request.get(`/fore/order/detail/${orderId}`)
}

export function requestRefund(orderId: number) {
  return request.post('/fore/order/refund', { orderId })
}
