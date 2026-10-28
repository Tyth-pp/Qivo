/// <reference types="node" />
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readSession, saveSession, clearSession, clearSessionIfCurrent, domainForPath, safeRedirect } from './session.ts'
function storage(): Storage {
  const values = new Map<string, string>()
  return { getItem: key => values.get(key) ?? null, setItem: (key, value) => { values.set(key, value) },
    removeItem: key => { values.delete(key) }, clear: () => values.clear(), key: i => [...values.keys()][i] ?? null,
    get length() { return values.size } }
}
test('consumer and admin sessions coexist; logout only removes its domain', () => {
  const local = storage()
  local.setItem('unrelated', 'keep')
  saveSession('USER', { token: 'user', username: 'buyer' }, local)
  saveSession('ADMIN', { token: 'admin', username: 'operator' }, local)
  clearSession('ADMIN', local)
  assert.equal(readSession('USER', local)?.token, 'user')
  assert.equal(readSession('ADMIN', local), null)
  assert.equal(local.getItem('unrelated'), 'keep')
})
test('legacy tokens and corrupt sessions cannot grant access', () => {
  const local = storage()
  local.setItem('token', 'legacy')
  assert.equal(readSession('USER', local), null)
  local.setItem('qivo.session.v2.USER', '{invalid')
  assert.equal(readSession('USER', local), null)
  local.setItem('qivo.session.v2.USER', '{"token":123,"username":"x"}')
  assert.equal(readSession('USER', local), null)
  assert.equal(domainForPath('/admin/order/list'), 'ADMIN')
  assert.equal(domainForPath('/order/list'), 'USER')
})
test('login redirects stay inside the intended identity domain', () => {
  assert.equal(safeRedirect('/order/list?status=0', 'USER'), '/order/list?status=0')
  assert.equal(safeRedirect('https://evil.invalid', 'USER'), '/')
  assert.equal(safeRedirect('//evil.invalid', 'ADMIN'), '/admin/dashboard')
  assert.equal(safeRedirect('/admin/product/list', 'USER'), '/')
  assert.equal(safeRedirect('/login', 'USER'), '/')
  assert.equal(safeRedirect('/register', 'USER'), '/')
  assert.equal(safeRedirect('/admin/../order/list', 'ADMIN'), '/admin/dashboard')
  assert.equal(safeRedirect('/admin%2f..%2forder/list', 'ADMIN'), '/admin/dashboard')
  assert.equal(safeRedirect('/%61dmin/product/list', 'USER'), '/')
  assert.equal(safeRedirect('/%2fexternal.invalid', 'USER'), '/')
  assert.equal(safeRedirect('/admin/login/', 'ADMIN'), '/admin/dashboard')
  assert.equal(safeRedirect('/bad%ZZ', 'USER'), '/')
  assert.equal(domainForPath('/administrator'), 'USER')
})

test('separate tabs keep separate identities; stale failures cannot remove a new session', () => {
  const tabA = storage(), tabB = storage()
  saveSession('USER', { token: 'old', username: 'buyer' }, tabA)
  assert.equal(readSession('USER', tabB), null)
  saveSession('USER', { token: 'new', username: 'other' }, tabA)
  assert.equal(clearSessionIfCurrent('USER', 'old', tabA), false)
  assert.equal(clearSessionIfCurrent('USER', undefined, tabA), false)
  assert.equal(readSession('USER', tabA)?.username, 'other')
  assert.equal(clearSessionIfCurrent('USER', 'new', tabA), true)
})
