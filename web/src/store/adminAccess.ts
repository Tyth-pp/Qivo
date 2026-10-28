import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import request from '@/api/request'
import { readSession } from '@/utils/session'
import { adminHome, canVisitAdmin } from '@/utils/adminAccess'
import type { AdminCapabilities, AdminCapability } from '@/utils/adminAccess'

export const useAdminAccessStore = defineStore('admin-access', () => {
  const capabilities = ref<AdminCapabilities>({})
  const owner = ref(''), loading = ref(false), error = ref('')
  let sequence = 0
  let pending: { token: string; promise: Promise<void> } | undefined
  function can(capability: AdminCapability) {
    return owner.value === readSession('ADMIN')?.token && capabilities.value[capability] === true
  }
  const currentCapabilities = computed(() => owner.value === readSession('ADMIN')?.token ? capabilities.value : {})
  const home = computed(() => adminHome(currentCapabilities.value))
  function canPath(path: string) { return canVisitAdmin(path, currentCapabilities.value) }
  function reset() { sequence++; owner.value = ''; capabilities.value = {}; error.value = ''; loading.value = false; pending = undefined }
  async function load() {
    const token = readSession('ADMIN')?.token
    if (!token) { reset(); throw new Error('请先登录管理员账号') }
    if (pending?.token === token) return pending.promise
    const current = ++sequence
    loading.value = true
    error.value = ''
    const promise = (async () => {
      try {
        const response = await request.get('/admin/access') as unknown as { data: { adminId: number; capabilities: AdminCapabilities } }
        if (current !== sequence || readSession('ADMIN')?.token !== token) throw new Error('登录身份已变化')
        if (!response.data || typeof response.data.capabilities !== 'object' || !response.data.capabilities || Array.isArray(response.data.capabilities)) throw new Error('权限响应不可用')
        const adminId = readSession('ADMIN')?.adminId
        if (adminId !== undefined && response.data.adminId !== adminId) throw new Error('权限身份不匹配')
        capabilities.value = response.data.capabilities
        owner.value = token
      } catch (failure) {
        if (current === sequence) {
          capabilities.value = {}; owner.value = ''
          error.value = '暂时无法获取当前权限，请稍后重试或联系管理员。'
        }
        throw failure
      } finally {
        if (current === sequence) { loading.value = false; pending = undefined }
      }
    })()
    pending = { token, promise }
    return promise
  }
  window.addEventListener('qivo-session-changed', () => { if (owner.value !== readSession('ADMIN')?.token) reset() })
  window.addEventListener('qivo-admin-permissions-stale', () => { if (readSession('ADMIN')) void load().catch(() => {}) })
  return { can, canPath, home, load, loading, error, reset, currentCapabilities }
})
