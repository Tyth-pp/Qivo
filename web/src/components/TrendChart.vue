<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue';
import * as echarts from 'echarts';
const props = defineProps<{
    rows: any[];
    metric: 'amount' | 'count';
}>();
const element = ref<HTMLElement>();
let chart: echarts.ECharts | undefined, observer: ResizeObserver | undefined;
function draw() {
    if (!chart)
        return;
    chart.setOption({
        animation: false, tooltip: { trigger: 'axis', valueFormatter: (v: any) => props.metric === 'amount' ? `¥${Number(v).toFixed(2)}` : `${v} 笔` }, grid: { left: 65, right: 20, top: 22, bottom: 32 }, xAxis: {
            type: 'category', boundaryGap: false, data: props.rows.map(x => x.date || `${x.month}月`), axisLine: { lineStyle: { color: '#e7e9ee' } }, axisLabel: { color: '#8993a3', fontSize: 11 }
        }, yAxis: {
            type: 'value', minInterval: props.metric === 'count' ? 1 : undefined, name: props.metric === 'amount' ? '元' : '笔', nameTextStyle: { color: '#8993a3', fontSize: 11 }, splitLine: { lineStyle: { color: '#eceef2', type: 'dashed' } }, axisLabel: { color: '#8993a3', fontSize: 11 }
        }, series: [
            {
                type: 'line', smooth: false, data: props.rows.map(x => x[props.metric] == null ? null : Number(x[props.metric])), symbolSize: 5, lineStyle: { width: 2, color: '#245ae8' }, itemStyle: { color: '#245ae8' }, areaStyle: { color: '#edf2ff', opacity: .7 }
            }
        ]
    }, true);
}
onMounted(() => {
    if (element.value) {
        chart = echarts.init(element.value);
        observer = new ResizeObserver(() => chart?.resize());
        observer.observe(element.value);
        draw();
    }
});
watch(() => [props.rows, props.metric], async () => {
    await nextTick();
    draw();
}, { deep: true });
onBeforeUnmount(() => {
    observer?.disconnect();
    chart?.dispose();
});
</script>

<template>
  <div
    ref="element"
    style="height:285px;width:100%"
    role="img"
    :aria-label="metric==='amount'?'已付款订单金额趋势':'已付款订单数量趋势'"
   />
</template>
