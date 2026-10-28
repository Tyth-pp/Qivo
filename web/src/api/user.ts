import request from './request'

export function getProfile() {
  return request.get('/fore/user/profile')
}

export function updateProfile(data: Record<string, any>) {
  return request.put('/fore/user/profile', data)
}

export function getAddressList() {
  return request.get('/fore/user/address')
}

export function addAddress(data: Record<string, any>) {
  return request.post('/fore/user/address', data)
}

export function updateAddress(id: number, data: Record<string, any>) {
  return request.put(`/fore/user/address/${id}`, data)
}

export function deleteAddress(id: number) {
  return request.delete(`/fore/user/address/${id}`)
}
