import request from './request'

export function getProductComments(productId: number, params: Record<string, any> = {}) {
  return request.get(`/fore/comment/list/${productId}`, { params })
}

export function addComment(data: Record<string, any>) {
  return request.post('/fore/comment/add', data)
}

export function appendComment(commentId: number, appendContent: string) {
  return request.post('/fore/comment/append', { commentId, appendContent })
}
