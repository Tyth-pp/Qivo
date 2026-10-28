<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAdminAccessStore } from '@/store/adminAccess';
const access = useAdminAccessStore(), route = useRoute(), router = useRouter();
const failed = computed(() => !!access.error || route.query.reason === 'unavailable');
async function retry() {
  try { await access.load(); if (access.home !== '/admin/access') await router.replace(access.home); }
  catch { /* 保留错误状态，允许主动重试。 */ }
}
</script>
<template>
  <section class="panel page-state" role="status">
    <h1 style="font-size:18px">{{ failed ? '权限信息暂不可用' : '当前页面不可访问' }}</h1>
    <p>{{ access.error || (access.home === '/admin/access' ? '当前账号还没有可访问的管理功能，请联系管理员分配权限。' : '当前账号没有这个页面的访问权限，可返回已授权的页面。') }}</p>
    <div class="inline-actions">
      <el-button :loading="access.loading" @click="retry">重新获取权限</el-button>
      <el-button v-if="access.home !== '/admin/access'" type="primary" @click="router.replace(access.home)">返回可访问页面</el-button>
    </div>
  </section>
</template>
