import type { Router } from 'vue-router'
import { domainForPath, readSession, safeRedirect } from '@/utils/session'
import { useUserStore } from '@/store/user'
import { useAdminAccessStore } from '@/store/adminAccess'
export function setupPermissionGuard(router: Router) {
  router.beforeEach(async (to) => {
    useUserStore().switchDomain(to.path)
    if (to.meta.requiresAuth && !readSession(domainForPath(to.path))) {
      return { path: to.path.startsWith('/admin') ? '/admin/login' : '/login', query: { redirect: to.fullPath } }
    }
    if (to.meta.requiresAuth && domainForPath(to.path) === 'ADMIN' && to.name !== 'adminAccess') {
      const access = useAdminAccessStore()
      try { await access.load() }
      catch {
        if (!readSession('ADMIN')) return { path: '/admin/login', query: { redirect: to.fullPath } }
        return { path: '/admin/access', query: { reason: 'unavailable' } }
      }
      if (to.name === 'adminHome') {
        const requested = safeRedirect(to.query.redirect, 'ADMIN')
        return access.canPath(requested) ? requested : access.home
      }
      if (!access.canPath(to.path)) return { path: '/admin/access', query: { reason: 'forbidden' } }
    }
    return true
  })
}
