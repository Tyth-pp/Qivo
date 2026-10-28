export interface SpecValue { id: string; name: string }
export interface SpecDefinition { id: string; name: string; values: SpecValue[] }
export interface Selection { specId: string; valueId: string }
/** 商品 ID 与 SKU ID 不能互换；多规格商品必须进入详情选择。 */
export function quickCartSkuId(product: { specMode?: string; defaultSkuId?: number | null }): number | null {
  const id = product.defaultSkuId
  return product.specMode === 'SINGLE' && typeof id === 'number' && Number.isSafeInteger(id) && id > 0 ? id : null
}
export function specKey(values: Selection[]) {
  return values.length ? [...values].sort((a, b) => a.specId.localeCompare(b.specId)).map(v => `${v.specId}=${v.valueId}`).join('|') : 'DEFAULT'
}
export function combinations(defs: SpecDefinition[]): Selection[][] {
  let rows: Selection[][] = [[]]
  for (const def of defs) {
    if (!def.values.length || rows.length * def.values.length > 100) throw new Error('规格组合不能为空，且最多生成 100 种规格组合')
    rows = rows.flatMap(row => def.values.map(v => [...row, { specId: def.id, valueId: v.id }]))
  }
  return rows
}
export function specText(defs: SpecDefinition[], values: Selection[]) {
  return values.map(v => {
    const d = defs.find(d => d.id === v.specId)
    return `${d?.name ?? ''}：${d?.values.find(x => x.id === v.valueId)?.name ?? ''}`
  }).join(' / ') || '默认规格'
}
export function parseSelections(value: string | Selection[]): Selection[] {
  return typeof value === 'string' ? JSON.parse(value) : value
}
export function snapshotText(value: string | null) {
  if (!value) return '历史规格未记录'
  try { return JSON.parse(value).text || '默认规格' } catch { return '规格数据异常' }
}
