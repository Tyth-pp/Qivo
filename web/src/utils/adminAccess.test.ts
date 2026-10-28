/// <reference types="node" />
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { adminHome, canVisitAdmin } from './adminAccess.ts'

test('administrator home follows effective page permissions, not role names', () => {
  assert.equal(adminHome({ 'product:list': true }), '/admin/product/list')
  assert.equal(adminHome({ 'order:list': true }), '/admin/order/list')
  assert.equal(adminHome({ dashboard: true, 'product:list': true }), '/admin/dashboard')
  assert.equal(adminHome({ 'product:update': true }), '/admin/access')
  assert.equal(adminHome({}), '/admin/access')
})
test('administrator routes require the exact page capability', () => {
  assert.equal(canVisitAdmin('/admin/product/list?keyword=杯', { 'product:list': true }), true)
  assert.equal(canVisitAdmin('/admin/user/list', { 'product:list': true }), false)
  assert.equal(canVisitAdmin('/admin/product/category', { 'product:list': true }), false)
  assert.equal(canVisitAdmin('/admin/unknown', { dashboard: true }), false)
})

test('account management and after sale have independent read capabilities and landing pages', () => {
  assert.equal(adminHome({'admin:list':true}), '/admin/admin/list')
  assert.equal(adminHome({'order:refund':true}), '/admin/after-sale/list')
  assert.equal(canVisitAdmin('/admin/after-sale/list', {'order:list':true}), false)
  assert.equal(canVisitAdmin('/admin/admin/list', {'role:list':true}), false)
  assert.equal(canVisitAdmin('/admin/admin/list', {'admin:write':true}), false)
})
