<script setup lang="ts">
import CustomerOrders from '@/components/CustomerOrders.vue';
</script>

<template>
  <nav class="store-crumb" aria-label="当前位置"><router-link to="/">首页</router-link><span>/</span><strong>我的订单</strong></nav>
  <div class="page-heading">
    <h1>我的订单</h1>
    <router-link to="/user/profile" class="text-link">个人中心 →</router-link>
  </div>
  <CustomerOrders />
</template>
