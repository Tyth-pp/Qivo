import test from 'node:test'
import assert from 'node:assert/strict'
import { combinations, specKey, specText, parseSelections, snapshotText, quickCartSkuId } from './sku.ts'

test('快捷加购使用默认 SKU 标识，多规格和缺失规格不能猜测标识', () => {
 assert.equal(quickCartSkuId({ specMode: 'SINGLE', defaultSkuId: 91 }), 91)
 assert.equal(quickCartSkuId({ specMode: 'MULTI', defaultSkuId: 91 }), null)
 assert.equal(quickCartSkuId({ specMode: 'SINGLE', defaultSkuId: null }), null)
 assert.equal(quickCartSkuId({ specMode: 'SINGLE', defaultSkuId: 0 }), null)
})
test('组合顺序稳定且展示名称不会参与唯一标识', () => {
 const defs = [{ id: 'color', name: '颜色', values: [{ id: 'black', name: '黑色' }, { id: 'white', name: '白色' }] }, { id: 'size', name: '尺码', values: [{ id: 'small', name: '小号' }] }]
 const rows = combinations(defs)
 assert.equal(rows.length, 2)
 assert.equal(specKey(rows[0]!), specKey([...rows[0]!].reverse()))
 assert.equal(specText(defs, rows[0]!), '颜色：黑色 / 尺码：小号')
 assert.deepEqual(parseSelections(JSON.stringify(rows[0])), rows[0])
 assert.equal(specKey([]), 'DEFAULT')
})
test('组合规模受限并明确处理历史快照', () => {
 assert.throws(() => combinations([{ id: 'size', name: '尺码', values: [] }]))
 const values = Array.from({ length: 11 }, (_, i) => ({ id: `v${i}`, name: String(i) }))
 assert.throws(() => combinations([{ id: 'x', name: '横', values }, { id: 'y', name: '纵', values }]))
 assert.equal(snapshotText(null), '历史规格未记录')
 assert.equal(snapshotText('{"text":"颜色：黑色"}'), '颜色：黑色')
})
