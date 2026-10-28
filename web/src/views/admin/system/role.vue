<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { TreeInstance } from 'element-plus'
import { Search, Plus, CopyDocument, Lock, Clock, Check, Warning, ArrowRight } from '@element-plus/icons-vue'
import { useAdminAccessStore } from '@/store/adminAccess'
import { adminPages } from '@/utils/adminAccess'
import type { AdminCapability } from '@/utils/adminAccess'
import { normalizePermissionSelection, permissionLeafKeys } from '@/utils/permissionSelection'
import { permissionHelp } from '@/utils/roleWorkspace'
import { listRoles, getRoleWorkspace, previewRole, saveRolePermissions, createRole, editRole, copyRole, removeRole } from '@/api/roles'
import type { Permission, Role, RolePreview, RoleWorkspace, RoleWrite } from '@/api/roles'
import { useLoad } from '@/composables/useLoad'
import PageState from '@/components/PageState.vue'
import RoleMembers from './RoleMembers.vue'
import RoleHistory from './RoleHistory.vue'

interface PermissionTree extends Permission { label: string; children: PermissionTree[] }
const access = useAdminAccessStore()
const rows = ref<Role[]>([]), selectedId = ref<number>(), workspace = ref<RoleWorkspace>(), roleSearch = ref('')
const listState = useLoad(), workState = useLoad(), tab = ref('permissions'), tree = ref<TreeInstance>()
const checked = ref<number[]>([]), original = ref<number[]>([]), preview = ref<RolePreview>()
const previewBusy = ref(false), previewError = ref(''), conflict = ref(''), saving = ref(false), saveError = ref('')
const filter = ref(''), selectedOnly = ref(false), expanded = ref(true), allHistory = ref(false)
const confirmVisible = ref(false), formVisible = ref(false), formMode = ref<'create'|'edit'|'copy'>('create'), formError = ref('')
const form = reactive<RoleWrite>({ name:'', code:'', description:'' }), formOriginal = ref(''), copySource = ref<{id:number;version:number;name:string}>()
let previewTimer: ReturnType<typeof setTimeout> | undefined, previewSequence = 0
const selected = computed(() => workspace.value?.role)
const moduleReady = computed(() => !!workspace.value && Number.isInteger(workspace.value.role.version) && !workState.loading.value && !workState.error.value)
const builtIn = computed(() => workspace.value?.builtIn === true)
const writable = computed(() => moduleReady.value && access.can('role:write') && !builtIn.value)
const catalog = computed(() => workspace.value?.catalog || [])
const visibleRoles = computed(() => rows.value.filter(x => `${x.name} ${x.code} ${x.description || ''}`.toLowerCase().includes(roleSearch.value.trim().toLowerCase())))
const leaves = computed(() => permissionLeafKeys(catalog.value, checked.value))
const originalLeaves = computed(() => permissionLeafKeys(catalog.value, original.value))
const dirty = computed(() => !!selected.value && JSON.stringify([...checked.value].sort((a,b)=>a-b)) !== JSON.stringify([...original.value].sort((a,b)=>a-b)))
const added = computed(() => leaves.value.filter(id => !originalLeaves.value.includes(id)).length)
const removed = computed(() => originalLeaves.value.filter(id => !leaves.value.includes(id)).length)
const enabledPages = computed(() => adminPages.filter(p => preview.value?.capabilities[p.capability]))
const actionLabels: {key:AdminCapability;name:string}[] = [
  {key:'product:add',name:'新增商品'}, {key:'product:update',name:'编辑商品、规格与库存'},
  {key:'order:ship',name:'订单发货'}, {key:'order:price',name:'修改订单金额'},
  {key:'order:refund',name:'审核售后申请'}, {key:'user:disable',name:'用户启停与积分调整'},
  {key:'role:write',name:'修改角色与授权'}, {key:'admin:write',name:'管理管理员账号'},
]
const enabledActions = computed(() => actionLabels.filter(p => preview.value?.capabilities[p.key]))
const disabledActions = computed(() => actionLabels.filter(p => !preview.value?.capabilities[p.key]))
const permissionTree = computed(() => {
  const map = new Map<number, PermissionTree>(catalog.value.map(p => [p.id,{...p,label:p.name,children:[]}]))
  const roots: PermissionTree[] = []
  catalog.value.forEach(p => { const node=map.get(p.id)!; const parent=map.get(p.parentId); if(parent)parent.children.push(node); else roots.push(node) })
  return roots
})
const errorText = (e: unknown, fallback: string) => (e as {userMessage?:string})?.userMessage || fallback
const isConflict = (e: unknown) => (e as {response?:{status?:number}})?.response?.status === 409 && errorText(e,'').includes('角色已被其他管理员修改')
const time = (value?: string) => value?.replace('T',' ').slice(0,16) || '暂无更新记录'

async function loadRoles(preferred?: number) {
  await listState.run(listRoles, r => {
    rows.value=r.data
    const id = preferred || selectedId.value || r.data[0]?.id
    if (id && rows.value.some(x=>x.id===id)) { selectedId.value=id; void loadWorkspace(id) }
    else if(r.data[0]) { selectedId.value=r.data[0].id; void loadWorkspace(r.data[0].id) }
    else { selectedId.value=undefined; workspace.value=undefined }
  })
}
async function loadWorkspace(id: number) {
  clearTimeout(previewTimer); previewSequence++; previewBusy.value=false; previewError.value=''; conflict.value=''; saveError.value=''
  await workState.run(() => getRoleWorkspace(id), async r => {
    workspace.value=r.data
    original.value=normalizePermissionSelection(r.data.catalog, r.data.builtIn ? r.data.catalog.map(x=>x.id) : r.data.permissionIds)
    checked.value=[...original.value]; preview.value=r.data.preview
    filter.value=''; selectedOnly.value=false; expanded.value=true
    await nextTick(); tree.value?.setCheckedKeys(permissionLeafKeys(catalog.value, checked.value))
  })
}
async function leave() {
  if(saving.value) return false
  if(formVisible.value && JSON.stringify(form)!==formOriginal.value) {
    try { await ElMessageBox.confirm('角色资料尚未保存，确定放弃并离开吗？','放弃修改',{confirmButtonText:'放弃修改',cancelButtonText:'继续编辑',type:'warning'}); formVisible.value=false }
    catch { return false }
  }
  if(!dirty.value) return true
  try { await ElMessageBox.confirm('权限修改尚未保存，确定放弃并离开吗？','放弃修改',{confirmButtonText:'放弃修改',cancelButtonText:'继续编辑',type:'warning'}); return true }
  catch { return false }
}
onBeforeRouteLeave(leave)
async function selectRole(role: Role) {
  if(role.id===selectedId.value || !await leave()) return
  selectedId.value=role.id; tab.value='permissions'; await loadWorkspace(role.id)
}
async function reload() {
  if(!selectedId.value || !await leave()) return
  await loadRoles(selectedId.value)
}
async function discard() {
  if(!selected.value || !await leave()) return
  await loadWorkspace(selected.value.id)
}
function checkPermissions(_: unknown, info:{checkedKeys:number[]}) { checked.value=normalizePermissionSelection(catalog.value,info.checkedKeys) }
function filterNode(query: string, node: PermissionTree) {
  const matches = `${node.name} ${permissionHelp[node.code] || ''}`.toLowerCase().includes(query.toLowerCase())
  return matches && (!selectedOnly.value || checked.value.includes(node.id))
}
function applyFilter() { tree.value?.filter(filter.value.trim()) }
watch([filter,selectedOnly],applyFilter)
watch(checked, () => {
  applyFilter(); clearTimeout(previewTimer); previewSequence++
  if(!dirty.value) { preview.value=workspace.value?.preview; previewBusy.value=false; previewError.value=''; return }
  previewBusy.value=true
  previewTimer=setTimeout(()=>void updatePreview(),280)
})
async function updatePreview() {
  if(!selected.value || builtIn.value) return false
  const sequence=++previewSequence, id=selected.value.id, version=selected.value.version, ids=[...checked.value]
  previewBusy.value=true; previewError.value=''
  try {
    const r=await previewRole(id,ids,version)
    if(sequence!==previewSequence) return false
    preview.value=r.data; return true
  } catch(e) {
    if(sequence===previewSequence) {
      previewError.value=errorText(e,'暂时无法核对权限，请重试')
      if(isConflict(e)) conflict.value=previewError.value
    }
    return false
  } finally { if(sequence===previewSequence)previewBusy.value=false }
}
function toggleExpanded() {
  expanded.value=!expanded.value
  catalog.value.forEach(p => { const node=tree.value?.getNode(p.id); if(node)node.expanded=expanded.value })
}
async function reviewSave() {
  if(!writable.value || !dirty.value || saving.value || conflict.value) return
  clearTimeout(previewTimer)
  if(await updatePreview())confirmVisible.value=true
}
async function commitPermissions() {
  if(!selected.value || saving.value || !writable.value) return
  const id=selected.value.id
  saving.value=true; saveError.value=''
  try {
    await saveRolePermissions(id,[...checked.value],selected.value.version)
    confirmVisible.value=false
    // 写入成功后立即重置脏标记；摘要刷新失败不再把成功写入显示为失败。
    original.value=[...checked.value]
    ElMessage.success({message:'权限已保存',grouping:true})
    await loadRoles(id)
    void access.load().catch(()=>{})
  } catch(e) {
    saveError.value=errorText(e,'保存未完成，当前选择已保留，请核对后重试')
    if(isConflict(e)) { conflict.value=saveError.value; confirmVisible.value=false }
  } finally { saving.value=false }
}
async function openForm(mode:'create'|'edit'|'copy') {
  if(!moduleReady.value || !access.can('role:write') || saving.value || !await leave())return
  if(dirty.value && selected.value)await loadWorkspace(selected.value.id)
  formMode.value=mode; formError.value=''
  Object.assign(form,{id:undefined,name:'',code:'',description:'',version:undefined})
  if(mode==='edit' && selected.value)Object.assign(form,selected.value)
  if(mode==='copy' && selected.value) {
    copySource.value={id:selected.value.id,version:selected.value.version,name:selected.value.name}
    form.name=`${selected.value.name}副本`.slice(0,50); form.description=selected.value.description || ''
  }
  formOriginal.value=JSON.stringify(form); formVisible.value=true
}
async function closeForm(done?:()=>void) {
  if(saving.value)return
  if(JSON.stringify(form)!==formOriginal.value) {
    try { await ElMessageBox.confirm('角色资料尚未保存，确定关闭吗？','放弃修改',{confirmButtonText:'放弃修改',cancelButtonText:'继续编辑'}) }
    catch { return }
  }
  formVisible.value=false; done?.()
}
async function submitForm() {
  if(saving.value || !access.can('role:write'))return
  formError.value=''
  if(!form.name.trim()) { formError.value='请填写角色名称'; return }
  if(!/^[a-zA-Z][a-zA-Z0-9_]{2,49}$/.test(form.code)) { formError.value='角色编码使用3至50位字母、数字或下划线，并以字母开头'; return }
  saving.value=true
  try {
    const payload={...form,name:form.name.trim(),description:form.description.trim()}
    const r=formMode.value==='edit'?await editRole(payload):formMode.value==='copy'&&copySource.value?await copyRole(copySource.value.id,copySource.value.version,payload):await createRole(payload)
    formVisible.value=false; original.value=[...checked.value]; tab.value='permissions'
    ElMessage.success(formMode.value==='copy'?'角色已复制，尚未分配给账号':'角色已保存')
    await loadRoles(r.data.id)
  } catch(e) { formError.value=errorText(e,'保存未完成，填写内容已保留'); if(isConflict(e)&&selected.value)conflict.value='角色信息可能已变化，请关闭弹窗并重新加载后核对。' }
  finally { saving.value=false }
}
async function deleteSelected() {
  if(!selected.value || !writable.value || saving.value || !await leave())return
  if(selected.value.memberCount) { tab.value='members'; return }
  try { await ElMessageBox.confirm(`确定删除“${selected.value.name}”吗？角色的变更记录会保留。`,'删除角色',{type:'warning',confirmButtonText:'删除角色',cancelButtonText:'取消'}) }
  catch { return }
  saving.value=true
  try {
    await removeRole(selected.value.id,selected.value.version)
    selectedId.value=undefined; workspace.value=undefined; original.value=[]; checked.value=[]
    ElMessage.success('角色已删除，变更记录已保留'); await loadRoles()
  } catch(e) { saveError.value=errorText(e,'删除未完成，请重试'); if(isConflict(e))conflict.value=saveError.value }
  finally { saving.value=false }
}
function beforeUnload(event:BeforeUnloadEvent) { if(dirty.value||saving.value||(formVisible.value&&JSON.stringify(form)!==formOriginal.value)){event.preventDefault();event.returnValue=''} }
onMounted(()=>{void loadRoles(); window.addEventListener('beforeunload',beforeUnload)})
onBeforeUnmount(()=>{clearTimeout(previewTimer);previewSequence++;window.removeEventListener('beforeunload',beforeUnload)})
</script>

<template>
  <div class="role-page">
    <div class="page-heading role-page-heading"><h1>角色权限</h1><div class="heading-actions"><el-button :icon="Clock" @click="allHistory=true">全部变更记录</el-button><el-button v-if="access.can('role:write')" type="primary" :icon="Plus" :disabled="saving||!moduleReady" @click="openForm('create')">新建角色</el-button></div></div>
    <PageState :loading="listState.loading.value" :error="listState.error.value" :empty="!rows.length" title="还没有角色" description="新建角色后，再为管理员分配合适的功能权限。" @retry="loadRoles()">
      <div class="role-workspace">
        <aside class="panel role-directory">
          <div class="directory-heading"><h2>角色</h2><span>{{rows.length}}</span></div>
          <el-input v-model="roleSearch" :prefix-icon="Search" placeholder="搜索角色" aria-label="搜索角色" clearable />
          <div class="role-items" aria-label="角色列表">
            <button v-for="row in visibleRoles" :key="row.id" type="button" :class="['role-item',{active:selectedId===row.id}]" :aria-current="selectedId===row.id?'true':undefined" :disabled="saving" @click="selectRole(row)">
              <span class="role-item-title"><strong>{{row.name}}</strong><el-icon v-if="row.code==='super_admin'" aria-label="系统内置角色"><Lock /></el-icon></span>
              <span class="role-item-description">{{row.description||'暂无角色说明'}}</span>
              <span class="role-item-meta">{{row.memberCount ?? 0}} 个关联账号<span v-if="row.code==='super_admin'">系统内置</span></span>
            </button>
            <p v-if="!visibleRoles.length" class="directory-empty">没有匹配的角色</p>
          </div>
          <div class="directory-foot"><el-icon><Lock /></el-icon><span>{{access.can('role:write')?'仅超级管理员可修改授权':'当前账号可查看角色配置'}}</span></div>
        </aside>
        <section class="panel role-detail">
          <PageState :loading="workState.loading.value" :error="workState.error.value" @retry="reload">
            <template v-if="selected">
              <header class="role-detail-header">
                <div class="role-detail-name"><h2>{{selected.name}}</h2><span class="role-kind">{{builtIn?'系统内置':'自定义角色'}}</span><span v-if="!access.can('role:write')" class="role-kind">只读</span></div>
                <p>{{selected.description||'暂无角色说明'}}</p>
                <div class="role-detail-meta"><code>{{selected.code}}</code><button type="button" @click="tab='members'">{{selected.memberCount}} 个关联账号 <el-icon><ArrowRight /></el-icon></button><span>更新于 {{time(selected.updateTime)}}</span></div>
                <div class="role-detail-actions" v-if="writable"><el-button :icon="CopyDocument" :disabled="saving" @click="openForm('copy')">复制角色</el-button><el-button :disabled="saving" @click="openForm('edit')">编辑资料</el-button><el-tooltip :content="selected.memberCount?'角色仍有关联账号，请先调整账号角色':'删除该角色'"><span><el-button text type="danger" :disabled="saving||selected.memberCount>0" @click="deleteSelected">删除</el-button></span></el-tooltip></div>
              </header>
              <div v-if="conflict" class="role-conflict" role="alert"><el-icon><Warning /></el-icon><span>{{conflict}} 当前选择已保留。</span><el-button link type="primary" :disabled="saving" @click="reload">重新加载</el-button></div>
              <el-tabs v-model="tab" class="role-tabs">
                <el-tab-pane label="功能权限" name="permissions">
                  <div v-if="builtIn" class="builtin-notice"><el-icon><Lock /></el-icon><div><strong>系统内置角色，拥有全部管理权限</strong><p>权限由系统维护，不能通过勾选收回。账号分配仍遵循超级管理员保护规则。</p></div></div>
                  <div class="permission-layout">
                    <div class="permission-editor">
                      <div class="permission-toolbar"><el-input v-model="filter" :prefix-icon="Search" placeholder="搜索功能权限" aria-label="搜索功能权限" clearable /><el-button text @click="toggleExpanded">{{expanded?'收起全部':'展开全部'}}</el-button></div>
                      <div class="permission-filter-row"><el-checkbox v-model="selectedOnly">只看已授权</el-checkbox><span>已选 {{leaves.length}} 项</span></div>
                      <el-tree ref="tree" :key="selected.id" :data="permissionTree" show-checkbox node-key="id" default-expand-all :default-checked-keys="leaves" :filter-node-method="filterNode" :props="{disabled:()=>saving||!writable}" empty-text="没有匹配的功能权限" @check="checkPermissions">
                        <template #default="{data}"><div :class="['permission-node',{'permission-group':data.children.length}]"><strong>{{data.name}}</strong><small v-if="!data.children.length&&permissionHelp[data.code]">{{permissionHelp[data.code]}}</small></div></template>
                      </el-tree>
                      <p v-if="!builtIn" class="permission-hint">分组支持全选与半选，取消分组会取消其下全部权限。修改后统一保存。</p>
                    </div>
                    <aside class="ability-preview" aria-label="实际能力预览">
                      <div class="preview-heading"><el-icon><Check /></el-icon><h3>实际能力预览</h3></div>
                      <p class="preview-caption">{{dirty?'按当前选择核对，尚未保存':'按服务端当前授权规则展示'}}</p>
                      <div v-if="previewBusy" role="status" class="preview-loading"><el-skeleton :rows="5" animated /><span>正在核对权限…</span></div>
                      <div v-else-if="previewError" role="status" class="preview-failure"><p>{{previewError}}</p><el-button size="small" @click="updatePreview">重新核对</el-button></div>
                      <template v-else-if="preview">
                        <h4>可进入的页面 <span>{{enabledPages.length}}</span></h4><ul v-if="enabledPages.length" class="ability-pages"><li v-for="p in enabledPages" :key="p.path">{{p.name}}</li></ul><p v-else class="ability-empty">暂无业务页面权限</p>
                        <h4>允许的操作 <span>{{enabledActions.length}}</span></h4><ul v-if="enabledActions.length" class="ability-actions"><li v-for="p in enabledActions" :key="p.key"><el-icon><Check /></el-icon>{{p.name}}</li></ul><p v-else class="ability-empty">暂无业务写操作权限</p>
                        <details v-if="disabledActions.length" class="disabled-abilities"><summary>查看不可用操作（{{disabledActions.length}}）</summary><ul><li v-for="p in disabledActions" :key="p.key">{{p.name}}</li></ul></details>
                        <div v-for="warning in preview.warnings" :key="warning" class="ability-warning"><el-icon><Warning /></el-icon><p>{{warning}}</p></div>
                      </template>
                    </aside>
                  </div>
                  <footer class="permission-footer" v-if="writable">
                    <div role="status"><strong v-if="dirty">新增 {{added}} 项，移除 {{removed}} 项</strong><span v-else class="saved-status"><el-icon><Check /></el-icon>权限已同步</span><small>{{dirty?'修改将影响该角色下的所有关联账号':'服务端会在关联账号后续请求时校验当前权限'}}</small></div>
                    <div><el-button :disabled="!dirty||saving" @click="discard">放弃修改</el-button><el-button type="primary" :loading="saving" :disabled="!dirty||previewBusy||!!previewError||!!conflict" @click="reviewSave">保存权限</el-button></div>
                  </footer>
                </el-tab-pane>
                <el-tab-pane label="关联账号" name="members" lazy><RoleMembers v-if="tab==='members'" :role-id="selected.id" /></el-tab-pane>
                <el-tab-pane label="变更记录" name="history" lazy><RoleHistory v-if="tab==='history'" :role-id="selected.id" /></el-tab-pane>
              </el-tabs>
              <p v-if="saveError&&!confirmVisible&&!conflict" class="role-inline-error" role="alert">{{saveError}}</p>
            </template>
          </PageState>
        </section>
      </div>
    </PageState>
  </div>
  <el-dialog v-model="confirmVisible" title="确认权限变更" width="600px" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" class="role-confirm-dialog">
    <template v-if="selected&&preview"><div class="confirm-role"><strong>{{selected.name}}</strong><span>当前影响 {{preview.memberCount}} 个关联账号</span></div><div class="confirm-diff"><section><h3>新增 {{preview.added.length}} 项</h3><ul v-if="preview.added.length"><li v-for="p in preview.added" :key="p.id">{{p.name}}</li></ul><p v-else>无新增权限</p></section><section><h3>移除 {{preview.removed.length}} 项</h3><ul v-if="preview.removed.length"><li v-for="p in preview.removed" :key="p.id">{{p.name}}</li></ul><p v-else>无移除权限</p></section></div><p class="confirm-note">保存后，关联账号后续请求将按新权限校验；已打开的页面会在重新进入或权限刷新后更新入口。</p><p v-for="warning in preview.warnings" :key="warning" class="confirm-warning">{{warning}}</p></template>
    <p v-if="saveError" role="alert" class="role-inline-error">{{saveError}}</p>
    <template #footer><el-button :disabled="saving" @click="confirmVisible=false">继续编辑</el-button><el-button type="primary" :loading="saving" @click="commitPermissions">确认保存</el-button></template>
  </el-dialog>
  <el-dialog v-model="formVisible" :title="formMode==='create'?'新建角色':formMode==='copy'?'复制角色':'编辑角色资料'" width="500px" :before-close="closeForm" :close-on-click-modal="false" :close-on-press-escape="!saving">
    <p v-if="formMode==='copy'" class="form-description">复制“{{copySource?.name}}”已保存的权限配置，生成独立角色。新角色不会自动分配给任何账号。</p>
    <el-form label-position="top" @submit.prevent="submitForm"><el-form-item label="角色名称"><el-input v-model="form.name" maxlength="50" show-word-limit placeholder="例如：商品维护员" :disabled="saving" /></el-form-item><el-form-item label="角色编码"><el-input v-model="form.code" maxlength="50" placeholder="例如：product_editor" :disabled="saving||formMode==='edit'" /><small class="form-help">{{formMode==='edit'?'编码创建后保持固定，用于识别角色':'3至50位字母、数字或下划线，以字母开头'}}</small></el-form-item><el-form-item label="角色说明"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="255" show-word-limit placeholder="说明这个角色负责的工作" :disabled="saving" /></el-form-item><p v-if="formError" class="role-inline-error" role="alert">{{formError}}</p></el-form>
    <template #footer><el-button :disabled="saving" @click="closeForm()">取消</el-button><el-button type="primary" :loading="saving" @click="submitForm">{{formMode==='copy'?'创建副本':'保存角色'}}</el-button></template>
  </el-dialog>
  <el-drawer v-model="allHistory" title="全部角色变更记录" size="800px" class="role-history-drawer"><p class="all-history-note">包括已删除角色的历史记录。记录中的名称与权限均保留修改当时的内容。</p><RoleHistory v-if="allHistory" /></el-drawer>
</template>

<style scoped>
.role-page-heading h1 { font-size:20px; }
.heading-actions { display:flex; gap:8px; }.heading-actions .el-button+.el-button { margin-left:0; }
.role-workspace { display:grid; grid-template-columns:224px minmax(0,1fr); gap:20px; align-items:start; }
.admin-shell .role-directory { padding:16px 12px 0; position:sticky; top:0; }
.directory-heading { display:flex; align-items:center; gap:8px; padding:0 4px 16px; }.directory-heading h2 { margin:0; font-size:14px; }.directory-heading>span { color:#697487; font-size:12px; }
.role-directory>.el-input { margin-bottom:12px; }.role-items { max-height:calc(100dvh - 350px); overflow-y:auto; min-height:120px; }
.role-item { display:block; width:100%; border:0; padding:16px 12px; text-align:left; border-radius:5px; margin-bottom:4px; background:transparent; color:var(--ink); cursor:pointer; }
.role-item:hover { background:#f5f7fb; }.role-item.active { background:var(--admin-soft); }.role-item:focus-visible { outline:2px solid var(--admin-accent); outline-offset:-2px; }
.role-item-title { display:flex; align-items:center; justify-content:space-between; gap:8px; }.role-item-title strong { font-size:14px; font-weight:600; overflow-wrap:anywhere; }.role-item-title .el-icon { color:#697487; font-size:14px; flex:none; }
.role-item-description { display:block; font-size:12px; line-height:1.7; color:#687385; margin-top:6px; overflow-wrap:anywhere; }
.role-item-meta { display:flex; align-items:center; gap:12px; font-size:11px; color:#687385; margin-top:10px; }.role-item.active .role-item-title { color:var(--admin-accent); }
.directory-foot { display:flex; gap:8px; align-items:flex-start; border-top:1px solid var(--line); padding:16px 4px; margin-top:8px; color:#687385; font-size:11px; line-height:1.6; }.directory-foot .el-icon { margin-top:2px; flex:none; }.directory-empty { padding:24px 8px; color:#687385; font-size:13px; }
.admin-shell .role-detail { margin:0 !important; padding:24px 24px 0; min-width:0; }.role-detail-header { position:relative; padding-bottom:24px; }.role-detail-name { display:flex; gap:10px; align-items:center; flex-wrap:wrap; }.role-detail-name h2 { margin:0; font-size:18px; line-height:1.5; overflow-wrap:anywhere; }.role-kind { border:1px solid var(--line); border-radius:4px; padding:2px 6px; font-size:11px; color:#687385; background:#fafbfc; }
.role-detail-header>p { color:#687385; font-size:13px; line-height:1.7; margin:8px 0 16px; }.role-detail-meta { display:flex; align-items:center; gap:16px; flex-wrap:wrap; font-size:12px; color:#687385; }.role-detail-meta code { font-family:Consolas,monospace; font-size:11px; color:#687385; }.role-detail-meta button { display:flex; align-items:center; gap:4px; color:var(--admin-accent); border:0; background:none; padding:0; cursor:pointer; font-size:12px; }.role-detail-actions { display:flex; gap:8px; margin-top:20px; }.role-detail-actions .el-button+.el-button { margin-left:0; }
.role-tabs :deep(.el-tabs__header) { margin-bottom:24px; }.role-tabs :deep(.el-tabs__item) { font-size:14px; padding:0 20px; height:44px; }.role-tabs :deep(.el-tabs__nav-wrap:after) { height:1px; }.role-tabs :deep(.el-tabs__content) { overflow:visible; }
.permission-layout { display:grid; grid-template-columns:minmax(280px,1fr) 244px; gap:28px; align-items:start; padding-bottom:24px; }.permission-editor { min-width:0; }.permission-toolbar { display:flex; gap:4px; align-items:center; }.permission-toolbar .el-input { min-width:120px; }.permission-toolbar .el-button { flex:none; padding:8px; font-size:12px; }.permission-filter-row { display:flex; align-items:center; justify-content:space-between; font-size:12px; color:#687385; margin:10px 0 12px; }.permission-filter-row :deep(.el-checkbox__label) { font-size:12px; }
.permission-editor :deep(.el-tree-node__content) { height:auto; min-height:42px; align-items:flex-start; padding-top:9px; padding-bottom:9px; }.permission-editor :deep(.el-tree-node__expand-icon) { margin-top:1px; }.permission-editor :deep(.el-checkbox) { margin-top:1px; height:20px; }.permission-editor :deep(.el-tree-node__label) { white-space:normal; min-width:0; flex:1; }.permission-node { display:flex; flex-direction:column; gap:4px; line-height:1.5; padding-right:8px; }.permission-node strong { font-weight:400; font-size:13px; }.permission-group strong { font-weight:600; font-size:14px; }.permission-node small { font-size:11px; color:#687385; max-width:300px; line-height:1.6; }.permission-hint { margin:20px 0 0; font-size:11px; color:#687385; line-height:1.8; }
.ability-preview { background:#f7f8fa; border:1px solid #edf0f4; border-radius:6px; padding:20px 16px; min-width:0; }.preview-heading { display:flex; align-items:center; gap:8px; }.preview-heading>.el-icon { color:var(--admin-accent); }.preview-heading h3 { margin:0; font-size:14px; font-weight:600; }.preview-caption { color:#687385; line-height:1.7; font-size:11px; margin:8px 0 24px; }.ability-preview h4 { margin:20px 0 12px; font-size:12px; font-weight:600; color:#515d70; }.ability-preview h4 span { margin-left:4px; font-weight:400; }.ability-preview ul { padding:0; list-style:none; margin:0; }.ability-pages { display:flex; flex-wrap:wrap; gap:6px; }.ability-pages li { background:white; border:1px solid #e7e9ee; border-radius:4px; padding:4px 7px; font-size:11px; }.ability-actions li { display:flex; gap:8px; align-items:flex-start; font-size:12px; margin:10px 0; line-height:1.6; }.ability-actions .el-icon { color:var(--admin-accent); margin-top:3px; flex:none; }.ability-empty { color:#687385; font-size:12px; line-height:1.8; }.disabled-abilities { margin-top:20px; border-top:1px solid var(--line); padding-top:16px; font-size:11px; color:#687385; }.disabled-abilities summary { cursor:pointer; }.disabled-abilities li { margin-top:10px; }.ability-warning { display:flex; align-items:flex-start; gap:7px; padding-top:16px; font-size:11px; line-height:1.8; color:#805527; }.ability-warning .el-icon { flex:none; margin-top:4px; }.ability-warning p { margin:0; }.preview-loading span,.preview-failure { color:#687385; font-size:12px; line-height:1.8; }.preview-loading span { display:block; margin-top:16px; }
.builtin-notice { display:flex; gap:12px; padding:14px 16px; border:1px solid #e1e7f5; background:#f6f8ff; border-radius:5px; margin-bottom:24px; }.builtin-notice>.el-icon { color:var(--admin-accent); margin-top:2px; }.builtin-notice strong { font-size:13px; font-weight:500; }.builtin-notice p { margin:5px 0 0; font-size:12px; color:#687385; line-height:1.7; }
.permission-footer { position:sticky; bottom:-20px; z-index:3; display:flex; align-items:center; justify-content:space-between; gap:16px; background:#fff; padding:16px 24px; margin:0 -24px; border-top:1px solid var(--line); border-radius:0 0 8px 8px; }.permission-footer strong { font-size:13px; font-weight:500; }.permission-footer small { display:block; font-size:11px; line-height:1.7; color:#687385; margin-top:4px; }.permission-footer>div:last-child { display:flex; flex:none; gap:8px; }.permission-footer .el-button+.el-button { margin-left:0; }.saved-status { display:flex; align-items:center; gap:6px; font-size:12px; color:#687385; }
.role-conflict { display:flex; align-items:flex-start; gap:8px; padding:12px; margin-bottom:16px; border:1px solid #f0d9ad; background:#fffaf1; border-radius:5px; font-size:12px; color:#805527; line-height:1.8; }.role-conflict>.el-icon { flex:none; margin-top:4px; }.role-conflict>span { flex:1; }.role-conflict .el-button { flex:none; }.role-inline-error { font-size:12px; line-height:1.8; color:#b74637; margin:12px 0; }.form-description,.form-help { color:#687385; font-size:12px; line-height:1.8; }.form-description { margin:0 0 20px; }.form-help { margin-top:5px; display:block; }
.confirm-role { display:flex; align-items:center; justify-content:space-between; gap:16px; padding-bottom:20px; border-bottom:1px solid var(--line); }.confirm-role strong { font-size:16px; font-weight:600; }.confirm-role span { font-size:12px; color:#687385; }.confirm-diff { display:grid; grid-template-columns:1fr 1fr; gap:24px; margin-top:20px; }.confirm-diff h3 { font-size:14px; margin:0 0 12px; }.confirm-diff ul { padding-left:18px; line-height:2; margin:0; }.confirm-diff p { color:#687385; font-size:13px; }.confirm-note { background:#f5f6f8; border-radius:5px; padding:12px; line-height:1.8; font-size:12px; color:#596272; margin-top:24px; }.confirm-warning { font-size:12px; color:#805527; line-height:1.8; }.all-history-note { font-size:12px; color:#687385; line-height:1.8; margin:0 0 20px; }
@media(max-width:1360px) { .role-workspace { grid-template-columns:200px minmax(0,1fr); gap:16px; }.permission-layout { grid-template-columns:minmax(250px,1fr) 216px; gap:20px; }.role-detail-meta { gap:12px; }.permission-footer small { max-width:235px; }.ability-preview { padding:16px 12px; } }
</style>
