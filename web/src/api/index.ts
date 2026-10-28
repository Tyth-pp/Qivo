import request from './request'

/** 首页数据 */
export function getIndexData() {
  return request.get('/fore/index/data')
}
