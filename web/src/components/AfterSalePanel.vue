<script setup lang="ts">
import {ref,computed,watch} from 'vue'
import {ElMessage} from 'element-plus'
import {afterSaleHistory,applyAfterSale,afterSaleLabels,activeAfterSale} from '@/api/afterSale'
import {readSession} from '@/utils/session'
import {useLoad} from '@/composables/useLoad'
import {price} from '@/utils/display'
import PageState from './PageState.vue'
const props=defineProps<{orderId:number;eligible:boolean;amount:number|string}>()
const emit=defineEmits<{changed:[];busy:[value:boolean]}>()
const rows=ref<any[]>([]),reason=ref(''),busy=ref(false),pending=ref(false),key=ref(''),state=useLoad()
const storageKey=()=> 'qivo.after-sale.'+(readSession('USER')?.userId||'anonymous')+'.'+props.orderId
const active=computed(()=>rows.value.some(r=>activeAfterSale(r.status)))
function load(){return state.run(()=>afterSaleHistory(props.orderId),(r:any)=>{
 rows.value=r.data||[]
 if(rows.value.some(row=>row.requestKey===key.value)){sessionStorage.removeItem(storageKey());pending.value=false;reason.value=''}
})}
watch(()=>props.orderId,()=>{
 rows.value=[];reason.value='';pending.value=false;key.value=crypto.randomUUID()
 try{const saved=JSON.parse(sessionStorage.getItem(storageKey())||'null');if(saved){key.value=saved.key;reason.value=saved.reason;pending.value=true}}catch{}
 load()
},{immediate:true})
async function submit(){
 if(busy.value)return
 if(!reason.value.trim()){ElMessage.warning('请填写退款原因');return}
 busy.value=true;emit('busy',true);pending.value=true
 sessionStorage.setItem(storageKey(),JSON.stringify({key:key.value,reason:reason.value}))
 try{
  await applyAfterSale(props.orderId,reason.value,key.value);sessionStorage.removeItem(storageKey());pending.value=false;reason.value=''
  key.value=crypto.randomUUID();ElMessage.success('售后申请已提交');await load();emit('changed')
 }catch{ElMessage.warning('请查看申请记录确认结果；重试会沿用原申请标识')}
 finally{busy.value=false;emit('busy',false)}
}
</script>
<template>
 <section class="drawer-section">
  <h3>售后记录</h3><p class="soft-note">仅支持已付款、未发货的整单模拟退款；不产生真实资金退回。</p>
  <PageState :loading="state.loading.value" :error="state.error.value" @retry="load">
   <p v-if="!rows.length" class="soft-note">暂无售后申请</p>
   <article v-for="row in rows" :key="row.id" class="after-sale-record"><div><el-tag>{{afterSaleLabels[row.status]||row.status}}</el-tag> <strong>¥{{price(row.amount)}}</strong></div><p>{{row.reason}}</p><small>{{row.createTime}}</small><p v-if="row.reviewNote">审核说明：{{row.reviewNote}}</p><p v-if="row.status==='REFUNDED'" class="soft-note">模拟退款已完成，未发生真实资金退回。</p></article>
   <el-form v-if="eligible&&!active&&!rows.some(row=>row.status==='REFUNDED')" label-position="top" @submit.prevent="submit">
    <el-form-item label="整单退款原因"><el-input v-model="reason" type="textarea" maxlength="500" show-word-limit :disabled="busy||pending"/></el-form-item>
    <el-button type="primary" :loading="busy" @click="submit">{{pending?'重试原申请':'申请整单模拟退款'}}</el-button>
   </el-form>
   <p v-if="active" class="soft-note">申请正在处理，订单暂停发货；审核结果会显示在这里。</p>
   <el-button v-if="pending||active" link :disabled="busy" @click="load">刷新申请结果</el-button>
  </PageState>
 </section>
</template>
<style scoped>.after-sale-record{padding:14px 0;border-bottom:1px solid var(--line);margin-bottom:15px}.after-sale-record small{color:var(--muted)}.after-sale-record p{overflow-wrap:anywhere}</style>
