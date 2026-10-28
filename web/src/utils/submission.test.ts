import { test } from 'node:test'
import assert from 'node:assert/strict'
import { prepareSubmission, clearPending, readPending } from './submission.ts'
test('下单重试与刷新复用键，未确认时不覆盖原请求，账号隔离', () => {
  const values = new Map<string, string>()
  const storage = { getItem: (k: string) => values.get(k) || null, setItem: (k: string, v: string) => values.set(k, v), removeItem: (k: string) => values.delete(k) } as unknown as Storage
  const a = prepareSubmission('1', '/fore/order/create', { cartItemIds: [1] }, storage)
  assert.equal(prepareSubmission('1', '/fore/order/create', { cartItemIds: [1] }, storage).key, a.key)
  assert.equal(readPending('1', storage)?.key, a.key)
  assert.throws(() => prepareSubmission('1', '/fore/order/create', { cartItemIds: [2] }, storage))
  assert.notEqual(prepareSubmission('2', '/fore/order/create', { cartItemIds: [1] }, storage).key, a.key)
  clearPending('1', 'wrong', storage); assert.ok(readPending('1', storage))
  clearPending('1', a.key, storage); assert.equal(readPending('1', storage), null)
})
