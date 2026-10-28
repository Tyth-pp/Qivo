<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Grid, TrendCharts, Box, Menu, Tickets, User, Lock, SwitchButton, RefreshLeft, UserFilled, Setting } from '@element-plus/icons-vue';
import BrandLogo from '@/components/BrandLogo.vue';
import { useUserStore } from '@/store/user';
import { useAdminAccessStore } from '@/store/adminAccess';
import { adminPages } from '@/utils/adminAccess';
import { readSession } from '@/utils/session';
import PageState from '@/components/PageState.vue';
import {getAdminProfile} from '@/api/account';
import AccountAvatar from '@/components/AccountAvatar.vue';
import AdminAccountSettings from '@/components/AdminAccountSettings.vue';
const route = useRoute(), router = useRouter(), user = useUserStore(), content = ref<HTMLElement>();
const access = useAdminAccessStore();
const icons: Record<string, any> = { dashboard: Grid, statistics: TrendCharts, product: Box, category: Menu, order: Tickets, user: User, role: Lock, admin:UserFilled, afterSale:RefreshLeft };
const settingsVisible = ref(false);
const profileToken = user.token;
getAdminProfile().then((r:any)=>{if(user.isAdmin && user.token===profileToken)user.updateIdentity({realName:r.data.realName||'',avatar:r.data.avatar||''});}).catch(()=>{});
const menu = computed(() => adminPages.filter(page => access.can(page.capability)));
const current = computed(() => adminPages.find(m => m.path === route.path)?.name || '管理台');
function refreshAccess() {
    if (document.visibilityState !== 'visible' || !readSession('ADMIN')) return;
    void access.load().catch(() => {});
}
onMounted(() => {
    document.body.classList.add('qivo-admin-theme');
    if (route.name === 'adminAccess' && !access.error) refreshAccess();
    window.addEventListener('focus', refreshAccess);
    document.addEventListener('visibilitychange', refreshAccess);
});
onBeforeUnmount(() => {
    document.body.classList.remove('qivo-admin-theme');
    window.removeEventListener('focus', refreshAccess);
    document.removeEventListener('visibilitychange', refreshAccess);
});
watch(() => route.path, async () => {
    await nextTick();
    content.value?.scrollTo(0, 0);
});
async function logout() {
    try {
        if (await user.logout()) router.push('/admin/login');
    }
    catch {
        ElMessage.error('退出失败，请重试');
    }
}
</script>

<template>
  <div class="admin-shell">
    <aside class="admin-side">
      <div class="admin-brand">
        <BrandLogo compact wordmark :to="access.home" />
        <span class="admin-brand-caption">商城管理台</span>
      </div>
      <nav class="admin-nav">
        <template v-for="(m,index) in menu" :key="m.path">
          <span v-if="m.group && m.group !== menu[index-1]?.group" class="admin-nav-label">{{m.group}}</span>
          <router-link :to="m.path" :class="{active:route.path===m.path}">
            <el-icon>
              <component :is="icons[m.icon]" />
            </el-icon>
            {{m.name}}
          </router-link>
        </template>
      </nav>
      <div class="admin-account">
        <button class="admin-settings-trigger" type="button" aria-label="账号设置" @click="settingsVisible=true">
          <AccountAvatar :src="user.avatar" :name="user.realName||user.username" :size="34" />
          <span><strong>{{user.realName||user.username}}</strong><small>账号设置</small></span>
        </button>
        <el-button link style="margin-left:auto" aria-label="退出登录" @click="logout">
          <el-icon>
            <SwitchButton />
          </el-icon>
        </el-button>
      </div>
    </aside>
    <div class="admin-main">
      <header class="admin-top">
        <div class="admin-crumb">
          管理台
          <span>/</span>
          <b>{{current}}</b>
        </div>
        <div class="admin-top-tools"><span>{{new Date().toLocaleDateString('zh-CN',{year:'numeric',month:'long',day:'numeric'})}}</span><i></i><button type="button" class="admin-top-identity" aria-label="账号设置" @click="settingsVisible=true"><AccountAvatar :src="user.avatar" :name="user.realName||user.username" :size="28" /><strong>{{user.realName||user.username}}</strong></button></div>
      </header>
      <main class="admin-content" ref="content">
        <div class="admin-page">
          <router-view v-if="route.name === 'adminAccess' || access.canPath(route.path)" />
          <PageState v-else :error="access.error || 'forbidden'" :loading="access.loading" @retry="refreshAccess" />
        </div>
        <footer class="admin-footer">Qivo 管理台</footer>
      </main>
    </div>
  </div>
  <AdminAccountSettings v-model="settingsVisible" />
</template>
<style scoped>
.admin-settings-trigger { display: flex; gap: 10px; align-items: center; text-align: left; min-width: 0; padding: 0; background: none; border: 0; cursor: pointer; color: inherit; }
.admin-settings-trigger > span { min-width: 0; }
.admin-settings-trigger strong { display: block; max-width: 92px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; font-weight: 500; }
.admin-settings-trigger small { display: block; font-size: 11px; color: var(--muted); margin-top: 3px; }
.admin-settings-trigger:hover small { color: var(--green); }
.admin-settings-trigger:focus-visible { outline: 2px solid var(--green); outline-offset: 5px; }
</style>
