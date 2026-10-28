export type AdminCapability = 'dashboard' | 'product:list' | 'category:list' | 'order:list' | 'user:list' | 'role:list' | 'product:add' | 'product:update' | 'order:ship' | 'order:price' | 'user:disable' | 'role:write' | 'admin:list' | 'admin:write' | 'order:refund'
export type AdminCapabilities = Partial<Record<AdminCapability, boolean>>
export const adminPages: { path: string; name: string; capability: AdminCapability; icon: string; group?: string }[] = [
  { path: '/admin/dashboard', name: '工作台', capability: 'dashboard', icon: 'dashboard' },
  { path: '/admin/statistics', name: '数据统计', capability: 'dashboard', icon: 'statistics', group: '经营概况' },
  { path: '/admin/product/list', name: '商品管理', capability: 'product:list', icon: 'product', group: '商品维护' },
  { path: '/admin/product/category', name: '分类管理', capability: 'category:list', icon: 'category', group: '商品维护' },
  { path: '/admin/order/list', name: '订单管理', capability: 'order:list', icon: 'order', group: '交易与售后' },
  { path: '/admin/after-sale/list', name: '售后处理', capability: 'order:refund', icon: 'afterSale', group: '交易与售后' },
  { path: '/admin/user/list', name: '用户管理', capability: 'user:list', icon: 'user', group: '账号与权限' },
  { path: '/admin/role/list', name: '角色权限', capability: 'role:list', icon: 'role', group: '账号与权限' },
  { path: '/admin/admin/list', name: '管理员账号', capability: 'admin:list', icon: 'admin', group: '账号与权限' },
]
export function canVisitAdmin(path: string, capabilities: AdminCapabilities): boolean {
  const pathname = path.split(/[?#]/)[0]?.replace(/\/+$/, '')
  const page = adminPages.find(item => item.path === pathname)
  return !!page && capabilities[page.capability] === true
}
export function adminHome(capabilities: AdminCapabilities): string {
  return adminPages.find(page => capabilities[page.capability] === true)?.path || '/admin/access'
}
