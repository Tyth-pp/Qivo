import test from 'node:test'
import assert from 'node:assert/strict'
import { price, orderStatus } from './display.ts'

test('金额展示区分零、缺失与非法值，并保留两位小数', () => {
  assert.equal(price(0), '0.00')
  assert.equal(price('142.5'), '142.50')
  assert.equal(price(undefined), '—')
  assert.equal(price('invalid'), '—')
})
test('订单终止状态优先于发货状态，付款和履约不混淆', () => {
  assert.equal(orderStatus({ payStatus: 3, shipStatus: 1 }), '已取消')
  assert.equal(orderStatus({ payStatus: 2, shipStatus: 2 }), '已退款')
  assert.equal(orderStatus({ payStatus: 0, shipStatus: 0 }), '待付款')
  assert.equal(orderStatus({ payStatus: 1, shipStatus: 0 }), '待发货')
  assert.equal(orderStatus({ payStatus: 1, shipStatus: 1 }), '已发货')
  assert.equal(orderStatus({ payStatus: 1, shipStatus: 2 }), '已完成')
  assert.equal(orderStatus({}), '状态未知')
})
