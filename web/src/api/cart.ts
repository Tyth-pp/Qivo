import request from './request'

export function getCartList() {
  return request.get('/fore/cart/list')
}

export function addToCart(skuId: number, quantity = 1) {
  return request.post('/fore/cart/add', { skuId, quantity })
}

export function updateCartQuantity(cartId: number, quantity: number) {
  return request.put('/fore/cart/update', { cartId, quantity })
}

export function checkCartItem(cartId: number, checked: number) {
  return request.put('/fore/cart/check', { cartId, checked })
}

export function deleteCartItem(cartId: number) {
  return request.delete(`/fore/cart/delete/${cartId}`)
}

export function getCheckedItems() {
  return request.get('/fore/cart/checked')
}
