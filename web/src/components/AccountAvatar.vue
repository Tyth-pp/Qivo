<script setup lang="ts">
import { ref, watch } from 'vue';
const props = defineProps<{ src?: string; name?: string; size?: number }>();
const failed = ref(false);
watch(() => props.src, () => { failed.value = false; });
</script>
<template>
  <span class="account-avatar" :style="{ width: (size || 44)+'px', height: (size || 44)+'px' }">
    <img v-if="src && !failed" :src="src" :alt="`${name || '账号'}的头像`" @error="failed=true" />
    <span v-else aria-label="默认头像">{{(name || '用').slice(0,1)}}</span>
  </span>
</template>
<style scoped>
.account-avatar { display: inline-flex; align-items: center; justify-content: center; overflow: hidden; border-radius: 50%; background: #eef1f5; color: #465368; font-weight: 550; font-size: 18px; flex: none; }
.account-avatar img { width: 100%; height: 100%; object-fit: cover; }
</style>
