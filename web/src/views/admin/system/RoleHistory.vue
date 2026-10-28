<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { getRoleHistory } from '@/api/roles'
import type { RoleChange } from '@/api/roles'
import { roleActionLabels, roleChangeDiff } from '@/utils/roleWorkspace'
import { useLoad } from '@/composables/useLoad'
import PageState from '@/components/PageState.vue'

const props = defineProps<{ roleId?: number }>()
const rows = ref<RoleChange[]>([]), page = ref(1), total = ref(0), operator = ref(''), dates = ref<string[]>([])
const { loading, error, run } = useLoad()
const selected = ref<RoleChange>(), visible = ref(false)
const diff = computed(() => roleChangeDiff(selected.value?.beforeSnapshot, selected.value?.afterSnapshot))
const time = (value?: string) => value?.replace('T', ' ').slice(0, 19) || '暂无记录'
function load() {
  return run(() => getRoleHistory(props.roleId, { page: page.value, pageSize: 10, operator: operator.value.trim() || undefined, from: dates.value?.[0], to: dates.value?.[1] }), r => { rows.value = r.data.records; total.value = r.data.total })
}
function query() { page.value = 1; void load() }
function reset() { operator.value = ''; dates.value = []; query() }
watch(() => props.roleId, () => { page.value = 1; operator.value = ''; dates.value = []; void load() }, { immediate: true })
</script>

<template>
  <div class="role-history">
    <div class="history-filters">
      <el-input v-model="operator" aria-label="搜索操作人" placeholder="搜索操作人" :prefix-icon="Search" clearable @keyup.enter="query" />
      <el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" range-separator="至" aria-label="变更日期" />
      <el-button @click="query">查询</el-button><el-button text @click="reset">重置</el-button>
    </div>
    <PageState :loading="loading" :error="error" :empty="!rows.length" title="暂无变更记录" description="新发生的角色资料与授权修改会记录在这里。" @retry="load">
      <el-table :data="rows" class="history-table">
        <el-table-column label="变更" min-width="160">
          <template #default="{row}"><strong>{{roleActionLabels[row.action] || row.action}}</strong><small v-if="roleId === undefined">{{row.roleName}}</small></template>
        </el-table-column>
        <el-table-column prop="actorName" label="操作人" min-width="110" />
        <el-table-column label="时间" min-width="165"><template #default="{row}">{{time(row.createTime)}}</template></el-table-column>
        <el-table-column label="详情" width="100" align="right"><template #default="{row}"><el-button link type="primary" @click="selected=row;visible=true">查看变更</el-button></template></el-table-column>
      </el-table>
      <div class="role-pagination"><span>共 {{total}} 条记录</span><el-pagination v-model:current-page="page" :total="total" :page-size="10" layout="prev,pager,next" @current-change="load" /></div>
    </PageState>
  </div>
  <el-dialog v-model="visible" title="变更详情" width="640px" append-to-body class="role-change-dialog">
    <template v-if="selected">
      <dl class="change-meta"><div><dt>角色</dt><dd>{{selected.roleName}}</dd></div><div><dt>操作人</dt><dd>{{selected.actorName}}</dd></div><div><dt>操作</dt><dd>{{roleActionLabels[selected.action] || selected.action}}</dd></div><div><dt>时间</dt><dd>{{time(selected.createTime)}}</dd></div></dl>
      <p class="change-note">{{selected.note}}</p>
      <el-alert v-if="!diff.readable" title="这条记录的快照暂时无法解析，请联系管理员核查。" type="warning" :closable="false" />
      <template v-else>
        <div v-if="selected.action==='UPDATE'" class="metadata-diff">
          <div><small>修改前</small><strong>{{diff.before?.name}}</strong><p>{{diff.before?.description || '暂无说明'}}</p></div>
          <div><small>修改后</small><strong>{{diff.after?.name}}</strong><p>{{diff.after?.description || '暂无说明'}}</p></div>
        </div>
        <div class="permission-diff">
          <section><h3>新增权限 <span>{{diff.added.length}}</span></h3><ul v-if="diff.added.length"><li v-for="p in diff.added" :key="p.code">{{p.name}}</li></ul><p v-else>无新增权限</p></section>
          <section><h3>移除权限 <span>{{diff.removed.length}}</span></h3><ul v-if="diff.removed.length"><li v-for="p in diff.removed" :key="p.code">{{p.name}}</li></ul><p v-else>无移除权限</p></section>
        </div>
      </template>
    </template>
    <template #footer><el-button @click="visible=false">关闭</el-button></template>
  </el-dialog>
</template>

<style scoped>
.history-filters { display:flex; gap:8px; align-items:center; margin-bottom:20px; flex-wrap:wrap; }
.history-filters .el-input { width:170px; }
.history-filters :deep(.el-date-editor) { width:260px; flex-grow:0; }
.history-table strong { font-weight:500; }
.history-table small { display:block; color:#687385; margin-top:4px; font-size:12px; }
.role-pagination { display:flex; justify-content:space-between; align-items:center; padding-top:20px; color:#687385; font-size:12px; }
.change-meta { display:grid; grid-template-columns:1fr 1fr; gap:16px; margin:0 0 20px; }
.change-meta div { display:flex; gap:16px; }
.change-meta dt { color:#687385; white-space:nowrap; }
.change-meta dd { margin:0; overflow-wrap:anywhere; }
.change-note { padding:12px; background:#f5f6f8; border-radius:5px; color:#596272; }
.permission-diff,.metadata-diff { display:grid; grid-template-columns:1fr 1fr; gap:20px; margin-top:20px; }
.permission-diff h3 { font-size:14px; margin:0 0 12px; font-weight:600; }
.permission-diff h3 span { margin-left:5px; color:#687385; font-weight:400; }
.permission-diff ul { padding-left:18px; margin:0; line-height:2; }
.permission-diff p,.metadata-diff p { color:#687385; line-height:1.7; margin:8px 0; }
.metadata-diff small { display:block; margin-bottom:8px; color:#687385; }
</style>
