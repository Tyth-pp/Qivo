<script setup lang="ts">
import {ref,reactive,onMounted} from 'vue'
import {ElMessage,ElMessageBox} from 'element-plus'
import {useAdminAccessStore} from '@/store/adminAccess'
import {useUserStore} from '@/store/user'
import {useLoad} from '@/composables/useLoad'
import PageState from '@/components/PageState.vue'
import {getRoleList} from '@/api/admin'
import {adminAccounts,createAdminAccount,editAdminAccount,changeAdminStatus,resetAdminPassword} from '@/api/adminAccounts'
const access=useAdminAccessStore(),user=useUserStore(),rows=ref<any[]>([]),roles=ref<any[]>([]),total=ref(0)
const filter=reactive({page:1,pageSize:10,keyword:'',status:undefined as number|undefined}),state=useLoad()
const visible=ref(false),saving=ref(false),mode=ref(''),target=ref<any>(),form=reactive({username:'',realName:'',password:'',confirm:'',roleId:undefined as number|undefined})
let original=''
function load(){return state.run(async()=>{const data:any=await adminAccounts(filter);return data.data},data=>{rows.value=data?.records||[];total.value=data?.total||0})}
async function open(row:any,type:string){
 if(!access.can('admin:write'))return
 target.value=row;mode.value=type;Object.assign(form,{username:row?.username||'',realName:row?.realName||'',password:'',confirm:'',roleId:undefined})
 original=JSON.stringify(form);visible.value=true
 if(type!=='password'){try{const r:any=await getRoleList();roles.value=r.data||[]}catch{roles.value=[]}}
}
async function close(done?:()=>void){
 if(saving.value)return
 if(JSON.stringify(form)!==original){try{await ElMessageBox.confirm('尚有未保存的账号修改，确定放弃吗？','放弃修改')}catch{return}}
 visible.value=false;done?.()
}
async function save(){
 if(saving.value||!access.can('admin:write'))return
 if(mode.value!=='edit'){
  if(!/^[a-zA-Z0-9_]{3,50}$/.test(form.username)&&mode.value==='create'){ElMessage.warning('账号使用3–50位字母、数字或下划线');return}
  if(form.password.length<8||new TextEncoder().encode(form.password).length>72||form.password!==form.confirm){ElMessage.warning('请设置至少8位、UTF-8不超过72字节的密码，两次输入需一致');return}
 }
 if(mode.value==='create'&&!form.roleId){ElMessage.warning('请选择角色');return}
 saving.value=true
 try{
  if(mode.value==='create')await createAdminAccount({username:form.username,realName:form.realName,password:form.password,roleId:form.roleId})
  else if(mode.value==='password')await resetAdminPassword(target.value.id,form.password)
  else await editAdminAccount(target.value.id,{realName:form.realName,roleId:form.roleId})
  visible.value=false;form.password='';form.confirm='';ElMessage.success('账号已保存');await load()
 }catch{}finally{saving.value=false}
}
async function toggle(row:any){
 if(saving.value||!access.can('admin:write')||row.id===user.userId)return
 try{
  await ElMessageBox.confirm(row.status===1?'停用后该账号现有登录将失效，确认继续？':'确认启用该账号？','账号状态')
  saving.value=true;await changeAdminStatus(row.id,row.status===1?0:1);ElMessage.success('状态已更新');await load()
 }catch{}finally{saving.value=false}
}
onMounted(load)
</script>
<template>
 <div class="page-heading"><h1>管理员账号</h1><el-button v-if="access.can('admin:write')" type="primary" @click="open(null,'create')">新增管理员</el-button></div>
 <section class="panel list-panel">
  <form class="filter-row" @submit.prevent="filter.page=1;load()">
   <el-input v-model="filter.keyword" placeholder="账号或姓名" aria-label="账号或姓名" clearable/>
   <el-select v-model="filter.status" placeholder="全部状态" clearable @change="filter.page=1;load()"><el-option label="已启用" :value="1"/><el-option label="已停用" :value="0"/></el-select>
   <el-button native-type="submit">查询</el-button><el-button link @click="Object.assign(filter,{page:1,keyword:'',status:undefined});load()">重置</el-button>
  </form>
  <p class="soft-note" style="margin:0 24px 18px">管理员账号与商城消费者独立。停用或重设密码后，旧登录不能继续使用。</p>
  <PageState :loading="state.loading.value" :error="state.error.value" @retry="load">
   <el-table :data="rows" empty-text="没有符合条件的管理员">
    <el-table-column prop="username" label="账号" min-width="150"/><el-table-column label="姓名" min-width="120"><template #default="{row}">{{row.realName||'未填写'}} <el-tag v-if="row.id===user.userId" size="small">当前账号</el-tag></template></el-table-column>
    <el-table-column label="角色" min-width="180"><template #default="{row}"><el-tag v-for="role in row.roles" :key="role.id" style="margin:3px">{{role.name}}</el-tag><span v-if="!row.roles?.length" class="muted">未分配角色</span></template></el-table-column>
    <el-table-column label="状态" width="100"><template #default="{row}"><el-tag :type="row.status===1?'success':'info'">{{row.status===1?'已启用':'已停用'}}</el-tag></template></el-table-column>
    <el-table-column prop="createTime" label="创建时间" min-width="170"/>
    <el-table-column v-if="access.can('admin:write')" label="操作" width="220" fixed="right"><template #default="{row}"><div class="row-actions">
     <el-button link @click="open(row,'edit')">编辑</el-button><el-button v-if="row.id!==user.userId" link :disabled="saving" @click="toggle(row)">{{row.status===1?'停用':'启用'}}</el-button>
     <el-button v-if="row.id!==user.userId" link @click="open(row,'password')">重设密码</el-button>
    </div></template></el-table-column>
   </el-table>
   <div class="pager"><el-pagination v-model:current-page="filter.page" v-model:page-size="filter.pageSize" :total="total" layout="total,prev,pager,next" @current-change="load"/></div>
  </PageState>
 </section>
 <el-drawer v-model="visible" :title="mode==='create'?'新增管理员':mode==='password'?'重设管理员密码':'编辑管理员'" size="460px" :before-close="close" :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving">
  <el-form label-position="top" :disabled="saving">
   <el-form-item label="账号"><el-input v-model="form.username" :disabled="mode!=='create'" maxlength="50"/></el-form-item>
   <template v-if="mode!=='password'">
    <el-form-item label="姓名"><el-input v-model="form.realName" maxlength="50"/></el-form-item>
    <el-form-item :label="mode==='create'?'角色':'调整角色（不选则保留）'"><el-select v-model="form.roleId" :disabled="target?.id===user.userId" clearable placeholder="请选择角色"><el-option v-for="role in roles" :key="role.id" :value="role.id" :label="role.name"/></el-select></el-form-item>
    <p v-if="mode==='edit'" class="soft-note">选择新角色将替换原角色；不能修改自己的角色。</p>
   </template>
   <template v-if="mode!=='edit'"><el-form-item label="密码"><el-input v-model="form.password" type="password" show-password autocomplete="new-password"/></el-form-item><el-form-item label="确认密码"><el-input v-model="form.confirm" type="password" show-password autocomplete="new-password"/></el-form-item></template>
  </el-form>
  <template #footer><el-button :disabled="saving" @click="close()">取消</el-button><el-button type="primary" :loading="saving" :disabled="!access.can('admin:write')" @click="save">保存账号</el-button></template>
 </el-drawer>
</template>
