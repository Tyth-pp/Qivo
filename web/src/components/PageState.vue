<script setup lang="ts">
import { Box, Lock, RefreshRight } from '@element-plus/icons-vue';
defineProps<{
    loading?: boolean;
    error?: boolean | string;
    empty?: boolean;
    title?: string;
    description?: string;
}>();
defineEmits<{
    retry: [
    ];
}>();
</script>

<template>
  <div v-if="loading" class="page-loading" role="status" aria-label="正在加载">
    <el-skeleton :rows="5" animated />
  </div>
  <div v-else-if="error" class="page-state" role="status">
    <el-icon>
      <Lock v-if="error === 'forbidden'" /><RefreshRight v-else />
    </el-icon>
    <h3>{{ error === 'forbidden' ? '没有访问权限' : '暂时没能加载成功' }}</h3>
    <p>{{ error === 'forbidden' ? '当前账号不能访问这项功能，请联系管理员调整权限。' : typeof error === 'string' ? error : '你的操作已保留，请稍后重试。' }}</p>
    <el-button @click="$emit('retry')">{{ error === 'forbidden' ? '重新检查权限' : '重新加载' }}</el-button>
  </div>
  <div v-else-if="empty" class="page-state">
    <el-icon>
      <Box />
    </el-icon>
    <h3>{{ title || '这里暂时没有内容' }}</h3>
    <p v-if="description">{{ description }}</p>
    <slot name="action" />
  </div>
  <slot v-else />
</template>
