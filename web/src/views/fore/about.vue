<script setup lang="ts">
import { ref } from 'vue';
import { ShoppingBag, Tickets, RefreshLeft, ArrowRight } from '@element-plus/icons-vue';
import coverImage from '@/assets/about-living-scene.webp';
const coverFailed = ref(false);
</script>

<template>
  <div class="about-page">
    <nav class="store-crumb" aria-label="当前位置"><router-link to="/">首页</router-link><span>›</span><strong>关于栖物</strong></nav>
    <section class="about-opening">
      <h1>关于栖物</h1>
      <p>Qivo 汇集数码办公与居家日用商品，围绕工作桌面和日常使用，<br />提供直接、清楚的商品选择。</p>
    </section>
    <figure class="about-photo">
      <img v-if="!coverFailed" :src="coverImage" alt="自然光下的陶瓷杯、玻璃杯与木质收纳托盘" width="1672" height="941" @error="coverFailed=true" />
      <span v-else class="about-photo-fallback">日常杯具与收纳</span>
    </figure>
    <section class="about-guide" aria-labelledby="guide-heading">
      <h2 id="guide-heading">购物须知</h2>
      <div class="guide-row">
        <h3><el-icon><ShoppingBag /></el-icon>挑选与下单</h3>
        <div><p>商品支持按分类浏览。购买前，请确认所选规格、数量和库存。</p><p>加入购物车后，请在提交订单前再次确认商品、数量和金额。</p></div>
      </div>
      <div class="guide-row">
        <h3><el-icon><Tickets /></el-icon>订单与收货</h3>
        <div><p>提交订单前，请核对收货人、联系电话和详细地址，避免因信息有误影响收货。</p><p>订单提交后，可在“我的订单”查看商品明细、订单状态及已填写的物流单号。</p></div>
      </div>
      <div class="guide-row">
        <h3><el-icon><RefreshLeft /></el-icon>售后申请</h3>
        <div><p>如需申请售后，请进入“我的订单”，查看对应订单是否支持申请。</p><p>提交申请时，请如实填写原因。申请进度、审核结果与处理记录均可在订单内查看。</p></div>
      </div>
    </section>
    <div class="about-end"><router-link to="/product/list">浏览商品<el-icon><ArrowRight /></el-icon></router-link></div>
  </div>
</template>

<style scoped>
.about-page { max-width: 1080px; margin: 0 auto; }
.about-page .store-crumb { margin: 0 0 34px; }
.about-opening { margin-bottom: 30px; }
.about-opening h1 { font-size: 20px; font-weight: 600; margin-bottom: 16px; }
.about-opening p { font-size: 14px; line-height: 1.95; color: #697381; }
.about-photo { height: 248px; border-radius: 7px; overflow: hidden; background: #f3f5f7; margin: 0 0 36px; }
.about-photo img { display: block; width: 100%; height: 100%; object-fit: cover; object-position: center 50%; }
.about-guide > h2 { font-size: 16px; font-weight: 600; margin-bottom: 7px; }
.guide-row { display: grid; grid-template-columns: 220px minmax(0,1fr); gap: 36px; padding: 25px 0; border-bottom: 1px solid #e7eaf0; }
.guide-row:last-child { border-bottom: 0; padding-bottom: 20px; }
.guide-row h3 { display: flex; align-items: center; gap: 12px; font-size: 14px; font-weight: 550; line-height: 1.9; }
.guide-row h3 .el-icon { width: 17px; height: 17px; color: #7a879b; }
.guide-row p { font-size: 13px; line-height: 1.9; color: #677180; max-width: 690px; }
.guide-row p + p { margin-top: 5px; }
.about-end { padding: 13px 0 0 256px; }
.about-end a { display: inline-flex; gap: 15px; align-items: center; font-size: 13px; color: #164dff; padding: 6px 0; }
.about-end .el-icon { width: 16px; height: 16px; transition: transform .18s; }
.about-end a:hover .el-icon { transform: translateX(4px); }
@media (prefers-reduced-motion: reduce) { .about-end .el-icon { transition: none; } .about-end a:hover .el-icon { transform: none; } }

.about-photo-fallback { display: grid; place-items: center; height: 100%; color: #788292; font-size: 14px; }
.about-page .store-crumb { font-size: 12px; color: #7d8796; }

</style>
