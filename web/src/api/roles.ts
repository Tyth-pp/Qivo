import request from './request'
import type { AdminCapabilities } from '@/utils/adminAccess'

export interface Role { id: number; name: string; code: string; description?: string; version: number; memberCount: number; updateTime?: string }
export interface Permission { id: number; name: string; code: string; parentId: number; type?: number }
export interface RolePreview { version: number; memberCount: number; added: Permission[]; removed: Permission[]; capabilities: AdminCapabilities; warnings: string[] }
export interface RoleWorkspace { role: Role; builtIn: boolean; catalog: Permission[]; permissionIds: number[]; preview: RolePreview }
export interface RoleMember { id: number; username: string; realName?: string; avatar?: string; status: number; lastLoginTime?: string }
export interface RoleChange { id: string; roleId: number; roleName: string; action: string; actorId: number; actorName: string; beforeSnapshot?: string; afterSnapshot?: string; note?: string; createTime: string }
export interface RoleWrite { id?: number; name: string; code: string; description: string; version?: number }
export interface Page<T> { records: T[]; total: number }
interface Response<T> { data: T }
const inline = { feedback: 'inline' as const }
export const listRoles = () => request.get('/admin/role/list', inline) as unknown as Promise<Response<Role[]>>
export const getRoleWorkspace = (id: number) => request.get(`/admin/role/${id}/workspace`, inline) as unknown as Promise<Response<RoleWorkspace>>
export const previewRole = (roleId: number, permissionIds: number[], version: number) => request.post('/admin/role/preview', { roleId, permissionIds, version }, inline) as unknown as Promise<Response<RolePreview>>
export const saveRolePermissions = (roleId: number, permissionIds: number[], version: number) => request.post('/admin/role/permission', { roleId, permissionIds, version }, inline) as unknown as Promise<Response<Role>>
export const createRole = (data: RoleWrite) => request.post('/admin/role/add', data, inline) as unknown as Promise<Response<Role>>
export const editRole = (data: RoleWrite) => request.put('/admin/role/update', data, inline) as unknown as Promise<Response<Role>>
export const copyRole = (id: number, sourceVersion: number, role: RoleWrite) => request.post(`/admin/role/${id}/copy`, { sourceVersion, role }, inline) as unknown as Promise<Response<Role>>
export const removeRole = (id: number, version: number) => request.delete(`/admin/role/${id}`, { ...inline, params: { version } }) as unknown as Promise<Response<void>>
export const getRoleMembers = (id: number, params: Record<string, unknown>) => request.get(`/admin/role/${id}/members`, { ...inline, params }) as unknown as Promise<Response<Page<RoleMember>>>
export const getRoleHistory = (id: number | undefined, params: Record<string, unknown>) => request.get(id === undefined ? '/admin/role/history' : `/admin/role/${id}/history`, { ...inline, params }) as unknown as Promise<Response<Page<RoleChange>>>
