<script setup lang="ts">
import { useAdminAccessStore } from "@/store/adminAccess";
const access = useAdminAccessStore();
import { ref, reactive, onMounted } from 'vue';
import {useRoute} from 'vue-router';
const route=useRoute();
import { ElMessage } from 'element-plus';
import { getOrderList, getOrderDetail, updateOrderPrice, shipOrder } from '@/api/admin';
import type { AdminOrder, AdminOrderDetail } from '@/api/admin';
import { useLoad } from '@/composables/useLoad';
import { price, copyText, paymentTabs } from '@/utils/display';
import PageState from '@/components/PageState.vue';
import OrderStatus from '@/components/OrderStatus.vue';
import ProductImage from '@/components/ProductImage.vue';
import { snapshotText } from '@/utils/sku';
import { validImageAddress } from '@/utils/image';
const rows = ref<AdminOrder[]>([]), total = ref(0), tab = ref(''), dates = ref<string[]>([]), search = reactive({ page: 1, pageSize: 10, orderNo: '' }), { loading, error, run } = useLoad();
function load() {
    return run(() => getOrderList({
        ...search, status: tab.value === '' ? undefined : Number(tab.value), startTime: dates.value?.[0] ? `${dates.value[0]} 00:00:00` : undefined, endTime: dates.value?.[1] ? `${dates.value[1]} 23:59:59` : undefined
    }), (r: any) => {
        rows.value = r.data?.records || [];
        total.value = r.data?.total || 0;
    });
}
function reset() {
    Object.assign(search, { page: 1, orderNo: '' });
    dates.value = [];
    tab.value = '';
    load();
}
const detailVisible = ref(false), detail = ref<AdminOrderDetail>(), detailLoad = useLoad(), selectedId = ref(0);
function showDetail(row: AdminOrder) {
    selectedId.value = row.id;
    detailVisible.value = true;
    loadDetail();
}
function loadDetail() {
    detail.value = undefined;
    return detailLoad.run(() => getOrderDetail(selectedId.value), (r: any) => {
        detail.value = r.data;
    });
}
const modal = ref(''), target = ref<AdminOrder>(), trackingNo = ref(''), amount = ref<number>(0), saving = ref(false);
function open(row: AdminOrder, type: string) {
    if (!access.can(type === "ship" ? "order:ship" : "order:price")) return;
    target.value = row;
    trackingNo.value = '';
    amount.value = Number(row.totalAmount);
    modal.value = type;
}
async function save() {
    if (!access.can(modal.value === "ship" ? "order:ship" : "order:price")) return;
    if (!target.value || saving.value)
        return;
    if (modal.value === 'ship' && !trackingNo.value.trim()) {
        ElMessage.warning('请填写物流单号');
        return;
    }
    if (modal.value === 'price' && (!Number.isFinite(amount.value) || amount.value <= 0 || amount.value > 99999999.99)) {
        ElMessage.warning('请填写有效金额');
        return;
    }
    saving.value = true;
    try {
        if (modal.value === 'ship')
            await shipOrder(target.value.id, trackingNo.value.trim());
        else
            await updateOrderPrice(target.value.id, amount.value);
        modal.value = '';
        ElMessage.success('操作成功');
        await load();
        if (detailVisible.value)
            await loadDetail();
    }
    catch {
    }
    finally {
        saving.value = false;
    }
}
function canPrice(row: AdminOrder) {
    const t = new Date(String(row.createTime).replace(' ', 'T')).getTime();
    return row.payStatus === 0 && Number.isFinite(t) && Date.now() < t + 600000;
}
onMounted(()=>{load();const id=Number(route.query.open);if(Number.isSafeInteger(id)&&id>0){selectedId.value=id;detailVisible.value=true;loadDetail()}});
</script>

<template>
  <div class="page-heading">
    <h1>订单管理</h1>
    <el-button @click="load">刷新列表</el-button>
  </div>
  <section class="panel list-panel">
    <el-tabs v-model="tab" @tab-change="search.page=1;load()">
      <el-tab-pane v-for="t in paymentTabs" :key="t.value" :label="t.label" :name="t.value" />
    </el-tabs>
    <form class="filter-row" @submit.prevent="search.page=1;load()">
      <el-input v-model="search.orderNo" placeholder="搜索订单编号" aria-label="订单编号" clearable />
      <el-date-picker
        v-model="dates"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        aria-label="下单日期范围"
       />
      <el-button native-type="submit">查询</el-button>
      <el-button link @click="reset">重置</el-button>
    </form>
    <PageState :loading="loading" :error="error" @retry="load">
      <el-table :data="rows" empty-text="没有符合条件的订单">
        <el-table-column label="订单 / 下单时间" min-width="235">
          <template #default="{row}">
            <span class="copy-line mono">
              {{row.orderNo}}
              <button class="copy-btn" aria-label="复制订单编号" @click="copyText(row.orderNo)">⧉</button>
            </span>
            <small style="display:block;font-size:11px">{{row.createTime}}</small>
          </template>
        </el-table-column>
        <el-table-column label="收件人" min-width="150">
          <template #default="{row}">
            <span>{{row.receiverName||'未记录'}}</span>
            <small style="display:block">{{row.receiverPhone||'—'}}</small>
          </template>
        </el-table-column>
        <el-table-column label="订单金额" width="135">
          <template #default="{row}">
            <strong class="mono">¥{{price(row.totalAmount)}}</strong>
          </template>
        </el-table-column>
        <el-table-column label="付款状态" width="100">
          <template #default="{row}">{{({0:'待付款',1:'已付款',2:'已退款',3:'已取消'} as Record<number,string>)[row.payStatus]||'未知'}}</template>
        </el-table-column>
        <el-table-column label="履约状态" width="100">
          <template #default="{row}">
            <OrderStatus :order="row" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="165" fixed="right">
          <template #default="{row}">
            <div class="row-actions">
              <el-button link @click="showDetail(row)">详情</el-button>
              <el-button link type="primary" v-if="access.can('order:ship')&&row.payStatus===1&&row.shipStatus===0" @click="open(row,'ship')">发货</el-button>
              <el-button link v-if="access.can('order:price')&&canPrice(row)" @click="open(row,'price')">改价</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="search.page"
          v-model:page-size="search.pageSize"
          :total="total"
          :page-sizes="[10,20,50]"
          layout="total,sizes,prev,pager,next"
          @change="load"
         />
      </div>
    </PageState>
  </section>
  <el-drawer v-model="detailVisible" title="订单详情" size="660px">
    <PageState :loading="detailLoad.loading.value" :error="detailLoad.error.value" @retry="loadDetail">
      <template v-if="detail">
        <OrderStatus :order="detail" />
        <section class="drawer-section order-items">
          <h3>商品明细 <small>下单时的商品与规格</small></h3>
          <div v-if="detail.items === undefined" class="soft-note" role="status">商品明细暂不可用，请稍后重试。</div>
          <div v-else-if="!detail.items.length" class="soft-note" role="status">该订单暂无商品明细记录。</div>
          <article v-for="item in detail.items || []" :key="item.id" class="order-item">
            <ProductImage :src="item.productImg && validImageAddress(item.productImg) ? item.productImg : ''" :alt="item.productName || '下单商品'" />
            <div class="order-item-info">
              <strong>{{item.productName || '历史商品名称未记录'}}</strong>
              <small>{{item.skuSnapshotSource === 'CURRENT' ? snapshotText(item.skuSpecSnapshot || null) : '历史规格未记录'}}</small>
              <small v-if="item.skuCode">规格编号 {{item.skuCode}}</small>
              <span>¥{{price(item.unitPrice)}} <span class="muted">× {{item.quantity}}</span></span>
            </div>
            <strong class="mono item-subtotal">¥{{price(item.totalPrice)}}</strong>
          </article>
        </section>
        <section class="drawer-section">
          <h3>收货信息</h3>
          <dl class="info-list">
            <dt>收件人</dt>
            <dd>{{detail.receiverName||'未记录'}} <span>{{detail.receiverPhone||'—'}}</span></dd>
            <dt>收货地址</dt>
            <dd>{{detail.addressSnapshotSource==='CURRENT'?(detail.receiverAddressSnapshot||'收货快照未记录'):'历史订单未记录收货快照'}}</dd>
            <dt>买家备注</dt>
            <dd>{{detail.remark||'无'}}</dd>
          </dl>
        </section>
        <section class="drawer-section">
          <h3>订单信息</h3>
          <dl class="info-list">
            <dt>订单编号</dt>
            <dd class="copy-line mono">
              {{detail.orderNo}}
              <button class="copy-btn" @click="copyText(detail.orderNo)">⧉</button>
            </dd>
            <dt>下单时间</dt>
            <dd>{{detail.createTime}}</dd>
            <dt>付款时间</dt>
            <dd>{{detail.payTime||'—'}}</dd>
            <dt>发货时间</dt>
            <dd>{{detail.shipTime||'—'}}</dd>
            <dt>物流单号</dt>
            <dd>{{detail.trackingNo||'未填写'}}</dd>
            <dt>订单应付金额</dt>
            <dd class="price">¥{{price(detail.totalAmount)}}</dd>
          </dl>
          <p class="soft-note">商品小计来自下单快照；订单应付金额可能经过管理员调整，以订单金额为准。</p>
        </section>
      </template>
    </PageState>
    <template #footer>
      <el-button @click="detailVisible=false">关闭</el-button>
      <el-button v-if="!detailLoad.loading.value&&!detailLoad.error.value&&access.can('order:ship')&&detail?.payStatus===1&&detail?.shipStatus===0" type="primary" @click="open(detail,'ship')">填写发货信息</el-button>
    </template>
  </el-drawer>
  <el-dialog
    :model-value="!!modal"
    :title="modal==='ship'?'填写发货信息':'修改订单金额'"
    width="440px"
    :close-on-click-modal="false"
    :close-on-press-escape="!saving"
    :show-close="!saving"
    @close="modal=''"
  >
    <small class="mono">{{target?.orderNo}}</small>
    <el-form label-position="top" style="margin-top:20px">
      <el-form-item v-if="modal==='ship'" label="物流单号">
        <el-input v-model="trackingNo" maxlength="100" placeholder="请核对后填写" />
      </el-form-item>
      <el-form-item v-else label="订单金额 / 元">
        <el-input-number v-model="amount" :min="0.01" :max="99999999.99" :precision="2" />
      </el-form-item>
    </el-form>
    <p class="soft-note">{{modal==='ship'?'确认后买家可查看物流单号。':'仅能修改未付款且未超时的订单，保存时会再次校验状态。'}}</p>
    <template #footer>
      <el-button :disabled="saving" @click="modal=''">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!access.can(modal==='ship'?'order:ship':'order:price')" @click="save">{{modal==='ship'?'确认发货':'确认改价'}}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.order-items { margin-top: 24px; }
.order-items h3 small { margin-left: 10px; font-size: 12px; font-weight: 400; color: var(--muted); }
.order-item { display: grid; grid-template-columns: 64px minmax(0, 1fr) auto; gap: 14px; padding: 16px 0; border-bottom: 1px solid var(--line); align-items: start; }
.order-item:last-child { border-bottom: 0; }
.order-item :deep(.product-image) { width: 64px; }
.order-item-info { display: flex; flex-direction: column; gap: 5px; min-width: 0; overflow-wrap: anywhere; }
.order-item-info strong { font-size: 14px; font-weight: 500; }
.order-item-info small { color: var(--muted); font-size: 12px; }
.item-subtotal { white-space: nowrap; font-size: 14px; font-weight: 500; }
</style>
