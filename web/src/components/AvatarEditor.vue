<script setup lang="ts">
import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import { feedbackMessage } from '@/utils/feedback';
import { uploadAvatar } from '@/api/account';
import AccountAvatar from './AccountAvatar.vue';
defineProps<{ modelValue?: string; name?: string; disabled?: boolean }>();
const emit = defineEmits<{ 'update:modelValue': [value: string]; busy: [value: boolean] }>();
const input = ref<HTMLInputElement>(), uploading = ref(false);
async function select(event: Event) {
  const target = event.target as HTMLInputElement, file = target.files?.[0]; target.value = '';
  if (!file || uploading.value) return;
  if (!['image/jpeg','image/png','image/webp'].includes(file.type) || file.size > 2*1024*1024) {
    ElMessage.warning('请选择不超过 2MB 的 JPG、PNG 或 WebP 图片'); return;
  }
  uploading.value = true; emit('busy', true);
  try {
    const result: any = await uploadAvatar(file);
    emit('update:modelValue', result.data);
  } catch (error: any) {
    ElMessage.error(error.response?.data?.code === 5002
      ? '头像需为2MB以内的 JPG、PNG 或 WebP 图片，边长不超过4096像素'
      : feedbackMessage(error, '头像上传未成功，请稍后重试') + '，原头像已保留');
  }
  finally { uploading.value = false; emit('busy', false); }
}
</script>
<template>
  <div class="avatar-editor">
    <AccountAvatar :src="modelValue" :name="name" :size="72" />
    <div><div class="avatar-actions">
      <el-button :loading="uploading" :disabled="disabled" @click="input?.click()">更换头像</el-button>
      <el-button v-if="modelValue" link :disabled="disabled || uploading" @click="emit('update:modelValue','')">恢复默认</el-button>
    </div><p>JPG、PNG 或 WebP，最大 2MB</p><small>保存资料后生效</small></div>
    <input ref="input" class="file-input" type="file" accept="image/jpeg,image/png,image/webp" aria-label="选择头像图片" @change="select" />
  </div>
</template>
<style scoped>
.avatar-editor { display: flex; gap: 20px; align-items: center; margin: 6px 0 28px; }
.avatar-actions { display: flex; gap: 14px; align-items: center; }
.avatar-editor p { color: #737f90; font-size: 12px; margin: 9px 0 3px; }
.avatar-editor small { color: #85909f; font-size: 12px; }
.file-input { display: none; }
</style>
