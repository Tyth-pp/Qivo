import test from 'node:test'
import assert from 'node:assert/strict'
import { roleChangeDiff, readRoleSnapshot } from './roleWorkspace.ts'

test('授权历史使用保存时的名称，并排除分组和未变化项', () => {
  const before = JSON.stringify({ name: '旧角色', permissions: [{ id: 1, code: 'product', name: '商品管理', leaf: false }, { id: 2, code: 'product:list', name: '原查看名称', leaf: true }] })
  const after = JSON.stringify({ name: '新角色', permissions: [{ id: 1, code: 'product', name: '商品管理', leaf: false }, { id: 3, code: 'product:update', name: '原编辑名称', leaf: true }] })
  const diff = roleChangeDiff(before, after)
  assert.deepEqual(diff.added.map(x => x.name), ['原编辑名称'])
  assert.deepEqual(diff.removed.map(x => x.name), ['原查看名称'])
  assert.equal(diff.before?.name, '旧角色')
})
test('历史损坏不能伪装为空授权，新增与删除允许一侧没有快照', () => {
  assert.equal(readRoleSnapshot('{bad'), undefined)
  assert.equal(roleChangeDiff('{bad', null).readable, false)
  assert.equal(roleChangeDiff(null, JSON.stringify({ permissions: [] })).readable, true)
})
