<script setup lang="ts">
import { ref, watch } from 'vue'
import { Search, TopRight } from '@element-plus/icons-vue'
import { getRoleMembers } from '@/api/roles'
import type { RoleMember } from '@/api/roles'
import { useLoad } from '@/composables/useLoad'
import { useAdminAccessStore } from '@/store/adminAccess'
import PageState from '@/components/PageState.vue'
import AccountAvatar from '@/components/AccountAvatar.vue'
const props = defineProps<{ roleId: number }>()
const access = useAdminAccessStore(), rows = ref<RoleMember[]>([]), keyword = ref(''), status = ref<number>(), page = ref(1), total = ref(0)
const { loading, error, run } = useLoad()
function load() { return run(() => getRoleMembers(props.roleId, { page:page.value, pageSize:10, keyword:keyword.value.trim()||undefined, status:status.value }), r => { rows.value=r.data.records; total.value=r.data.total }) }
function query() { page.value=1; void load() }
function reset() { keyword.value=''; status.value=undefined; query() }
watch(() => props.roleId, () => { keyword.value=''; status.value=undefined; page.value=1; void load() }, { immediate:true })
</script>
<template>
  <div class="role-members">
    <div class="member-filters"><el-input v-model="keyword" aria-label="搜索关联账号" placeholder="搜索账号或姓名" :prefix-icon="Search" clearable @keyup.enter="query" /><el-select v-model="status" placeholder="全部状态" clearable aria-label="账号状态" @change="query"><el-option label="已启用" :value="1" /><el-option label="已停用" :value="0" /></el-select><el-button @click="query">查询</el-button><el-button text @click="reset">重置</el-button></div>
    <p class="member-note">调整角色权限会影响这里的所有账号。账号的角色分配在管理员账号页维护。<router-link v-if="access.can('admin:list')" to="/admin/admin/list">管理账号 <el-icon><TopRight /></el-icon></router-link></p>
    <PageState :loading="loading" :error="error" :empty="!rows.length" title="没有匹配的关联账号" description="该角色尚未分配给账号，或当前筛选没有结果。" @retry="load">
      <el-table :data="rows"><el-table-column label="管理员账号" min-width="210"><template #default="{row}"><div class="member-identity"><AccountAvatar :src="row.avatar" :name="row.realName||row.username" :size="34" /><span><strong>{{row.realName||row.username}}</strong><small>{{row.username}}</small></span></div></template></el-table-column><el-table-column label="状态" width="100"><template #default="{row}"><el-tag :type="row.status===1?'success':'info'" size="small" effect="plain">{{row.status===1?'已启用':'已停用'}}</el-tag></template></el-table-column><el-table-column label="最近登录" min-width="170"><template #default="{row}">{{row.lastLoginTime?.replace('T',' ').slice(0,16)||'尚未登录'}}</template></el-table-column></el-table>
      <div class="member-pagination"><span>共 {{total}} 个账号</span><el-pagination v-model:current-page="page" :total="total" :page-size="10" layout="prev,pager,next" @current-change="load" /></div>
    </PageState>
  </div>
</template>
<style scoped>
.member-filters { display:flex; gap:8px; flex-wrap:wrap; }
.member-filters .el-input { width:210px; }.member-filters .el-select { width:130px; }
.member-note { font-size:12px; color:#687385; line-height:1.8; margin:16px 0 20px; }.member-note a { color:var(--admin-accent); margin-left:8px; }
.member-identity { display:flex; align-items:center; gap:12px; }.member-identity strong { display:block; font-weight:500; }.member-identity small { display:block; font-size:12px; color:#687385; margin-top:4px; }
.member-pagination { display:flex; align-items:center; justify-content:space-between; padding-top:20px; color:#687385; font-size:12px; }
</style>
