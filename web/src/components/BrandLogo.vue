<script setup lang="ts">
import navigationLogo from '@/assets/qivo-navigation-wordmark.svg';
import logo from '@/assets/qivo-wordmark.svg';
defineProps<{
    compact?: boolean;
    wordmark?: boolean;
    to?: string;
}>();
</script>

<template>
  <router-link :to="to || '/'" class="brand-logo" :class="{ compact }" :aria-label="to?.startsWith('/admin') ? 'Qivo 管理工作台' : 'Qivo 首页'">
    <img v-if="wordmark" class="qivo-wordmark" :src="navigationLogo" alt="Qivo" />
    <img v-else :src="logo" alt="Qivo" />
  </router-link>
</template>

<style scoped>
.qivo-wordmark { display: block; }
.brand-logo {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
}

.brand-logo img {
  width: 106px;
  height: 45px;
  object-fit: contain;
}

.brand-logo img.qivo-wordmark { width: 90px; height: 36px; }

.brand-logo.compact img {
  width: 94px;
  height: 40px;
}
</style>
