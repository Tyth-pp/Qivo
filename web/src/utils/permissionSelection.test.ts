/// <reference types="node" />
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { normalizePermissionSelection, permissionLeafKeys } from './permissionSelection.ts'

const nodes = [{ id: 1, parentId: 0 }, { id: 2, parentId: 1 }, { id: 3, parentId: 1 }, { id: 4, parentId: 0 }]

test('legacy missing group does not hide retained child grants', () => {
  assert.deepEqual(normalizePermissionSelection(nodes, [2, 3, 4]), [1, 2, 3, 4])
  assert.deepEqual(normalizePermissionSelection(nodes, [4]), [4])
})
test('partial group round trip never expands into ungranted siblings', () => {
  const partial = normalizePermissionSelection(nodes, [2])
  assert.deepEqual(partial, [1, 2])
  assert.deepEqual(permissionLeafKeys(nodes, partial), [2])
  assert.deepEqual(normalizePermissionSelection(nodes, permissionLeafKeys(nodes, partial)), partial)
})
test('group-only historical grant and unknown ids do not grant descendants', () => {
  assert.deepEqual(normalizePermissionSelection(nodes, [1, 999]), [])
})
