<script setup lang="ts">
import { useAdminAccessStore } from "@/store/adminAccess";
const access = useAdminAccessStore();
import { ref, onMounted } from 'vue';
import { Box, Tickets, Menu, Wallet, CircleCheck, ArrowRight, RefreshLeft } from '@element-plus/icons-vue';
import { getWorkbench, getOrderList, getRevenue } from '@/api/admin';
import PageState from '@/components/PageState.vue';
import OrderStatus from '@/components/OrderStatus.vue';
import TrendChart from '@/components/TrendChart.vue';
import { useLoad } from '@/composables/useLoad';
import { price } from '@/utils/display';
const data = ref<any>({}), orders = ref<any[]>([]), trend = ref<any[]>([]), { loading, error, run } = useLoad();
const ordersLoad = useLoad();
function loadOrders() {
    if (!access.can("order:list")) { orders.value = []; ordersLoad.error.value = "forbidden"; return Promise.resolve(); }
    return ordersLoad.run(() => getOrderList({ page: 1, pageSize: 4, status: 1 }), (response: any) => {
        orders.value = response.data?.records || [];
    });
}
async function load() {
    await Promise.all([run(() => Promise.all([getWorkbench(), getRevenue('week')]), ([a, c]: any[]) => {
        data.value = a.data || {};
        trend.value = c.data?.list || [];
    }), loadOrders()]);
}
onMounted(load);
</script>

<template>
  <div class="admin-dashboard">
    <div class="page-heading"><div><h1>工作台</h1><p class="admin-heading-description">订单、商品与日常经营，一目了然。</p></div><span class="admin-heading-note">今日经营概况</span></div>
    <PageState :loading="loading" :error="error" @retry="load">
      <section class="admin-metrics" aria-label="经营指标">
        <article class="panel"><div class="metric-label">今日订单金额<el-icon><Wallet /></el-icon></div><strong><small>¥</small>{{price(data.todayRevenue)}}</strong><p>今日创建且已付款的订单</p></article>
        <article class="panel"><div class="metric-label">今日订单<el-icon><Tickets /></el-icon></div><strong>{{data.totalOrders??0}}<em>笔</em></strong><p>按订单创建日期统计</p></article>
        <article class="panel"><div class="metric-label">今日已付款<el-icon><CircleCheck /></el-icon></div><strong>{{data.paidOrders??0}}<em>笔</em></strong><p>另有 {{data.unpaidOrders??0}} 笔订单待付款</p></article>
        <article class="panel"><div class="metric-label">在售商品<el-icon><Box /></el-icon></div><strong>{{data.onShelf??0}}<em>件</em></strong><p>已下架 {{data.offShelf??0}} 件</p></article>
      </section>
      <div class="admin-dashboard-grid">
        <section class="panel dashboard-trend"><div class="section-heading"><div><h2>近期订单金额</h2><p class="chart-caption">按创建日期统计已付款订单 · 元</p></div><router-link v-if="access.can('dashboard')" to="/admin/statistics" class="text-link">查看统计<el-icon><ArrowRight /></el-icon></router-link></div><PageState :empty="!trend.length" title="暂无统计数据"><TrendChart :rows="trend" metric="amount" /></PageState></section>
        <aside class="panel dashboard-operations"><h2>常用操作</h2>
          <router-link v-if="access.can('product:list')" to="/admin/product/list" class="quick-link"><el-icon><Box /></el-icon><div>管理商品<small>维护商品与可售规格</small></div><el-icon class="quick-arrow"><ArrowRight /></el-icon></router-link>
          <router-link v-if="access.can('order:list') && ordersLoad.error.value !== 'forbidden'" to="/admin/order/list" class="quick-link"><el-icon><Tickets /></el-icon><div>处理订单<small>查看订单与发货信息</small></div><el-icon class="quick-arrow"><ArrowRight /></el-icon></router-link>
          <router-link v-if="access.can('order:refund')" to="/admin/after-sale/list" class="quick-link"><el-icon><RefreshLeft /></el-icon><div>售后处理<small>查看申请与处理记录</small></div><el-icon class="quick-arrow"><ArrowRight /></el-icon></router-link>
          <router-link v-if="access.can('category:list') && !access.can('order:refund')" to="/admin/product/category" class="quick-link"><el-icon><Menu /></el-icon><div>整理分类<small>维护商品分类</small></div><el-icon class="quick-arrow"><ArrowRight /></el-icon></router-link>
          <div class="dashboard-inventory"><span>商品概况</span><div><strong>{{data.onShelf??0}} <small>在售</small></strong><strong>{{data.offShelf??0}} <small>已下架</small></strong></div><div class="inventory-bar" v-if="Number(data.onShelf)+Number(data.offShelf)>0"><i :style="{width:100*Number(data.onShelf)/(Number(data.onShelf)+Number(data.offShelf))+'%'}"></i></div></div>
        </aside>
      </div>
      <section v-if="access.can('order:list')" class="panel list-panel dashboard-orders">
        <div class="section-heading"><div><h2>最近已付款订单</h2><p class="chart-caption">包含不同发货状态的已付款订单</p></div><router-link v-if="ordersLoad.error.value !== 'forbidden'" to="/admin/order/list" class="text-link">全部订单<el-icon><ArrowRight /></el-icon></router-link></div>
        <PageState :loading="ordersLoad.loading.value" :error="ordersLoad.error.value" @retry="loadOrders"><el-table :data="orders" empty-text="暂无已付款订单">
          <el-table-column prop="orderNo" label="订单 / 下单时间" min-width="240"><template #default="{row}"><span class="mono">{{row.orderNo}}</span><small class="order-time">{{row.createTime}}</small></template></el-table-column>
          <el-table-column label="收件人" min-width="110"><template #default="{row}">{{row.receiverName||'未记录'}}</template></el-table-column>
          <el-table-column label="订单金额" width="140"><template #default="{row}"><span class="numeric">¥{{price(row.totalAmount)}}</span></template></el-table-column>
          <el-table-column label="状态" width="130"><template #default="{row}"><OrderStatus :order="row" /></template></el-table-column>
        </el-table></PageState>
      </section>
    </PageState>
  </div>
</template>

