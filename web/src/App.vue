<script setup lang="ts">
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import StorefrontLayout from '@/components/StorefrontLayout.vue';
const route = useRoute();
const storefront = computed(() => !route.path.startsWith('/admin') && !['/login', '/register'].includes(route.path));
</script>

<template>
  <el-config-provider :locale="zhCn">
    <StorefrontLayout v-if="storefront" :compact="route.path === '/order/confirm'">
      <router-view />
    </StorefrontLayout>
    <router-view v-else />
  </el-config-provider>
</template>
