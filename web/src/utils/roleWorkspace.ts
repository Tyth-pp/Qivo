export interface Snapshot {
  name?: string; code?: string; description?: string
  permissions: { id: number; name: string; code: string; leaf?: boolean }[]
}
export function readRoleSnapshot(value?: string | null): Snapshot | undefined {
  if (!value) return undefined
  try {
    const parsed = JSON.parse(value)
    if (!parsed || typeof parsed !== 'object' || !Array.isArray(parsed.permissions)) return undefined
    return { name: typeof parsed.name === 'string' ? parsed.name : '', code: typeof parsed.code === 'string' ? parsed.code : '', description: typeof parsed.description === 'string' ? parsed.description : '', permissions: parsed.permissions.filter((p: any) => p && typeof p.name === 'string' && typeof p.code === 'string') }
  } catch { return undefined }
}
export function roleChangeDiff(before?: string | null, after?: string | null) {
  const a = readRoleSnapshot(before), b = readRoleSnapshot(after)
  const old = a?.permissions.filter(p => p.leaf !== false) || [], next = b?.permissions.filter(p => p.leaf !== false) || []
  return { before: a, after: b, added: next.filter(p => !old.some(x => x.code === p.code)), removed: old.filter(p => !next.some(x => x.code === p.code)), readable: (!before || !!a) && (!after || !!b) }
}
export const permissionHelp: Record<string, string> = {
  dashboard: '包含工作台与数据统计',
  'product:update': '包含商品编辑、上下架、规格、库存调整及分类维护',
  'product:delete': '当前未开放商品删除入口',
  'category:list': '查看商品分类，分类维护沿用商品编辑权限',
  'user:disable': '包含用户启停和积分调整',
  'order:refund': '包含售后列表、申请详情与审核',
  'role:list': '查看角色、关联账号与变更记录，不包含授权写操作',
  'admin:list': '查看管理员账号，不包含账号写操作',
}
export const roleActionLabels: Record<string, string> = { CREATE: '新建角色', COPY: '复制角色', UPDATE: '修改资料', PERMISSIONS: '调整权限', DELETE: '删除角色' }
