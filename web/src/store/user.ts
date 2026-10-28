import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { clearSessionIfCurrent, domainForPath, readSession, saveSession } from '@/utils/session'
import type { Domain, LoginSession } from '@/utils/session'
export const useUserStore = defineStore('user', () => {
  const domain = ref<Domain>(domainForPath(window.location.pathname))
  const session = ref<LoginSession | null>(readSession(domain.value))
  function refresh() { session.value = readSession(domain.value) }
  window.addEventListener('qivo-session-changed', refresh)
  function switchDomain(path: string) { domain.value = domainForPath(path); refresh() }
  const token = computed(() => session.value?.token || '')
  const userId = computed(() => session.value?.userId || session.value?.adminId || 0)
  const username = computed(() => session.value?.username || '')
  const avatar = computed(() => session.value?.avatar || '')
  function updateIdentity(data: { avatar?: string; realName?: string }) {
    if (session.value) { saveSession(domain.value, { ...session.value, ...data }); refresh() }
  }
  const realName = computed(() => session.value?.realName || '')
  const isAdmin = computed(() => domain.value === 'ADMIN')
  function setLogin(data: LoginSession, admin = false) {
    domain.value = admin ? 'ADMIN' : 'USER'
    saveSession(domain.value, data)
    refresh()
  }
  async function logout() {
    const current = domain.value
    const value = readSession(current)
    if (value) {
      const response = await fetch('/api/common/auth/logout', {
        method: 'POST', headers: { Authorization: `Bearer ${value.token}` }
      })
      if (!response.ok && response.status !== 401) throw new Error('退出失败，请重试')
    }
    return value ? clearSessionIfCurrent(current, value.token) : true
  }
  return { token, userId, username, realName, avatar, updateIdentity, isAdmin, setLogin, logout, switchDomain }
})
