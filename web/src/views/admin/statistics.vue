<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue';
import { InfoFilled } from '@element-plus/icons-vue';
import { getRevenue, getOrderStats, getProductTop10 } from '@/api/admin';
import PageState from '@/components/PageState.vue';
import TrendChart from '@/components/TrendChart.vue';
import { useLoad } from '@/composables/useLoad';
const stats = ref<any>({}), rows = ref<any[]>([]), top = ref<any[]>([]), period = ref('day'), { loading, error, run } = useLoad();
const metric = ref<'amount' | 'count'>('amount');
const hasCount = computed(() => rows.value.some(row => row.count !== undefined && row.count !== null));
watch(hasCount, available => { if (!available) metric.value = 'amount'; });
function load() {
    return run(() => Promise.all([getOrderStats(), getRevenue(period.value), getProductTop10()]), ([a, b, c]: any[]) => {
        stats.value = a.data || {};
        rows.value = b.data?.list || [];
        top.value = c.data?.list || [];
    });
}
onMounted(load);
</script>

<template>
  <div class="admin-statistics">
    <div class="page-heading"><div><h1>数据统计</h1><p class="admin-heading-description">分清累计数据与周期趋势，查看经营变化。</p></div><span class="admin-heading-note">统计口径以订单创建时间为准</span></div>
    <PageState :loading="loading" :error="error" @retry="load">
      <section class="panel statistics-totals"><div><p>累计订单</p><strong>{{stats.totalOrders??0}}<small>笔</small></strong></div><div><p>累计已付款订单</p><strong>{{stats.validOrders??0}}<small>笔</small></strong></div><span><el-icon><InfoFilled /></el-icon>累计指标不随趋势时段变化</span></section>
      <section class="panel statistics-trend"><div class="section-heading"><div><h2>经营趋势</h2><p class="chart-caption">按订单创建日期统计 · {{metric==='amount'?'元':'笔'}}</p></div><el-select v-model="period" style="width:155px" aria-label="统计时段" @change="load"><el-option label="近期按日" value="day" /><el-option label="最近一周" value="week" /><el-option label="今年按月" value="month" /></el-select></div>
        <div class="trend-switch" role="group" aria-label="趋势指标"><button :class="{active:metric==='amount'}" :aria-pressed="metric==='amount'" @click="metric='amount'">已付款订单金额</button><button v-if="hasCount" :class="{active:metric==='count'}" :aria-pressed="metric==='count'" @click="metric='count'">已付款订单数量</button></div>
        <PageState :empty="!rows.length" title="暂无统计数据"><TrendChart :rows="rows" :metric="metric" /></PageState>
      </section>
      <section class="panel statistics-ranking"><div class="section-heading"><h2>在售商品销量排行</h2><small>累计销量前 10 名，不随趋势时段变化</small></div><el-table :data="top" empty-text="暂无在售商品"><el-table-column type="index" label="排名" width="90" /><el-table-column prop="name" label="商品" min-width="220" /><el-table-column prop="sales" label="累计销量" width="180" /></el-table></section>
    </PageState>
  </div>
</template>
