import request from './request'

export function getProductList(params: Record<string, any>) {
  return request.get('/fore/product/list', { params })
}

export function getProductDetail(id: number) {
  return request.get(`/fore/product/detail/${id}`)
}

export function collectProduct(productId: number) {
  return request.post('/fore/product/collect', { productId })
}

export function cancelCollect(productId: number) {
  return request.delete(`/fore/product/collect/${productId}`)
}

export function getCollectList() {
  return request.get('/fore/product/collect')
}
