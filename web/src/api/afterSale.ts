import request from './request'
export const afterSaleLabels: Record<string,string> = {REQUESTED:'待审核',REFUNDING:'退款处理中',REFUND_FAILED:'退款失败待重试',REJECTED:'已拒绝',REFUNDED:'已模拟退款'}
export const activeAfterSale = (s:string) => ['REQUESTED','REFUNDING','REFUND_FAILED'].includes(s)
export function applyAfterSale(orderId:number,reason:string,key:string){return request.post('/fore/after-sale',{orderId,reason},{headers:{'Idempotency-Key':key}})}
export function afterSaleHistory(orderId:number){return request.get('/fore/after-sale/order/'+orderId)}
export function adminAfterSales(params:Record<string,any>){return request.get('/admin/after-sale/list',{params})}
export function refundAttempts(id:string){return request.get('/admin/after-sale/'+id+'/attempts')}
export function reviewAfterSale(id:string,approve:boolean,note:string){return request.post('/admin/after-sale/'+id+'/review',{approve,note})}
