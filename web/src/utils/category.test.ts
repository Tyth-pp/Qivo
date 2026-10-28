import test from 'node:test'
import assert from 'node:assert/strict'
import { categoryTree } from './category.ts'
test('平铺分类正确还原父子关系，不丢失孤立节点、不修改接口数据', () => {
  const rows = [{ id: 2, parentId: 1, name: '子分类' }, { id: 1, parentId: 0, name: '主分类' }, { id: 3, parentId: 99, name: '孤立分类' }]
  const tree = categoryTree(rows)
  assert.equal(tree.length, 2)
  assert.equal(tree[0]!.children![0]!.name, '子分类')
  assert.equal(tree[1]!.name, '孤立分类')
  assert.equal('children' in rows[0]!, false)
  assert.equal(categoryTree([{ id: 1, parentId: 1, name: '异常数据' }]).length, 1)
})
