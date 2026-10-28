<script setup lang="ts">
import {ref,reactive,onMounted} from 'vue'
import {useRouter} from 'vue-router'
import {ElMessage,ElMessageBox} from 'element-plus'
import {useAdminAccessStore} from '@/store/adminAccess'
import {useLoad} from '@/composables/useLoad'
import PageState from '@/components/PageState.vue'
import {price} from '@/utils/display'
import {adminAfterSales,refundAttempts,reviewAfterSale,afterSaleLabels} from '@/api/afterSale'
const access=useAdminAccessStore(),router=useRouter(),state=useLoad(),attemptState=useLoad(),rows=ref<any[]>([]),total=ref(0),selected=ref<any>(),attempts=ref<any[]>([])
const filter=reactive({page:1,pageSize:10,status:'',orderNo:''})
const note=ref(''),saving=ref(false),visible=ref(false)
function load(){return state.run(async()=>{const r:any=await adminAfterSales(filter);return r.data},r=>{rows.value=r?.records||[];total.value=r?.total||0})}
function loadAttempts(){if(!selected.value)return;return attemptState.run(()=>refundAttempts(selected.value.id),(r:any)=>{attempts.value=r.data||[]})}
function open(row:any){selected.value=row;note.value='';attempts.value=[];visible.value=true;loadAttempts()}
function close(done?:()=>void){if(saving.value)return;visible.value=false;done?.()}
async function review(approve:boolean){
 if(saving.value||!access.can('order:refund'))return
 if(!approve&&!note.value.trim()){ElMessage.warning('请填写拒绝原因');return}
 saving.value=true
 try{
  await ElMessageBox.confirm(approve?'确认对订单 '+selected.value.orderNo+' 整单模拟退款 ¥'+price(selected.value.amount)+'？不会发生真实资金退回。':'确认拒绝这次申请？原因会展示给买家。','确认售后处理')
  const r:any=await reviewAfterSale(selected.value.id,approve,note.value);selected.value=r.data
  if(r.data.status==='REFUND_FAILED')ElMessage.warning('模拟退款未完成，原订单与库存保持不变，可重试原申请')
  else ElMessage.success(r.data.status==='REFUNDED'?'模拟退款成功，未发生真实资金退回':'申请已拒绝')
  note.value='';await Promise.all([load(),loadAttempts()])
 }catch{}finally{saving.value=false}
}
onMounted(load)
</script>
<template>
 <div class="page-heading"><h1>售后处理</h1><el-button @click="load">刷新列表</el-button></div>
 <section class="panel list-panel">
  <form class="filter-row" @submit.prevent="filter.page=1;load()">
   <el-select v-model="filter.status" placeholder="全部申请" clearable @change="filter.page=1;load()"><el-option v-for="(label,key) in afterSaleLabels" :key="key" :label="label" :value="key"/></el-select>
   <el-input v-model="filter.orderNo" placeholder="搜索订单编号" aria-label="订单编号" clearable/>
   <el-button native-type="submit">查询</el-button><el-button link @click="filter.status='';filter.orderNo='';filter.page=1;load()">重置</el-button>
  </form>
  <p class="soft-note" style="margin:0 24px 18px">仅处理已付款、未发货订单的整单模拟退款，不产生真实资金退回。申请处理中暂停发货。</p>
  <PageState :loading="state.loading.value" :error="state.error.value" @retry="load">
   <el-table :data="rows" empty-text="没有符合条件的售后申请">
    <el-table-column prop="id" label="申请编号" min-width="195" show-overflow-tooltip/><el-table-column prop="orderNo" label="订单编号" min-width="200"/>
    <el-table-column prop="reason" label="申请原因" min-width="220" show-overflow-tooltip/>
    <el-table-column label="金额" width="120"><template #default="{row}">¥{{price(row.amount)}}</template></el-table-column>
    <el-table-column label="状态" min-width="150"><template #default="{row}"><el-tag :type="row.status==='REFUNDED'?'success':row.status==='REFUND_FAILED'?'danger':'info'">{{afterSaleLabels[row.status]||row.status}}</el-tag></template></el-table-column>
    <el-table-column prop="createTime" label="申请时间" min-width="170"/><el-table-column label="操作" width="100" fixed="right"><template #default="{row}"><el-button link @click="open(row)">查看处理</el-button></template></el-table-column>
   </el-table>
   <div class="pager"><el-pagination v-model:current-page="filter.page" :page-size="filter.pageSize" :total="total" layout="total,prev,pager,next" @current-change="load"/></div>
  </PageState>
 </section>
 <el-drawer v-model="visible" title="售后申请" size="560px" :before-close="close" :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving">
  <template v-if="selected">
   <dl class="info-list"><dt>申请编号</dt><dd>{{selected.id}}</dd><dt>订单编号</dt><dd>{{selected.orderNo}} <el-button v-if="access.can('order:list')" link @click="router.push({path:'/admin/order/list',query:{open:selected.orderId}})">查看订单明细</el-button></dd><dt>当前状态</dt><dd>{{afterSaleLabels[selected.status]}}</dd><dt>申请原因</dt><dd>{{selected.reason}}</dd><dt>整单退款金额</dt><dd>¥{{price(selected.amount)}}</dd><dt>审核说明</dt><dd>{{selected.reviewNote||'暂无'}}</dd><dt>审核管理员ID</dt><dd>{{selected.reviewerId||'尚未审核'}}</dd></dl>
   <section class="drawer-section"><h3>退款处理记录</h3><PageState :loading="attemptState.loading.value" :error="attemptState.error.value" @retry="loadAttempts"><p v-if="!attempts.length" class="soft-note">尚无退款尝试</p><article v-for="a in attempts" :key="a.id" class="refund-attempt"><strong>第 {{a.attemptNo}} 次 · {{a.status==='SUCCEEDED'?'模拟成功':'处理失败'}}</strong><p>{{a.message}}</p><small>{{a.createTime}} · 管理员 {{a.operatorId}}</small></article></PageState></section>
   <template v-if="['REQUESTED','REFUND_FAILED'].includes(selected.status)"><el-form label-position="top"><el-form-item label="处理说明（拒绝时必填）"><el-input v-model="note" type="textarea" maxlength="500" show-word-limit :disabled="saving"/></el-form-item></el-form><p class="soft-note">批准后将模拟退款并更新库存与销量，请先核对申请信息。</p></template>
  </template>
  <template #footer><el-button :disabled="saving" @click="close()">关闭</el-button><template v-if="access.can('order:refund')&&selected&&['REQUESTED','REFUND_FAILED'].includes(selected.status)"><el-button v-if="selected.status==='REQUESTED'" :disabled="saving" @click="review(false)">拒绝申请</el-button><el-button type="primary" :loading="saving" @click="review(true)">{{selected.status==='REFUND_FAILED'?'重试模拟退款':'批准并模拟退款'}}</el-button></template></template>
 </el-drawer>
</template>
<style scoped>.refund-attempt{padding:14px 0;border-bottom:1px solid var(--line)}.refund-attempt p{margin:8px 0}.refund-attempt small{color:var(--muted)}</style>
