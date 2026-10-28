<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { validImageAddress } from '@/utils/image'
const props = defineProps<{ modelValue: string | null | undefined; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: string]; uploading: [value: boolean] }>()
const input = ref<HTMLInputElement>()
const busy = ref(false)
async function upload(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  busy.value = true
  emit('uploading', true)
  try {
    const data = new FormData()
    data.append('file', file)
    const result: any = await request.post('/common/upload/file', data)
    if (typeof result.data !== 'string' || !validImageAddress(result.data)) { ElMessage.error('图片上传失败，请重试'); return }
    emit('update:modelValue', result.data)
    ElMessage.success('图片已上传，请保存对应商品或规格')
  } catch { /* 请求拦截器显示错误；保留旧图，允许重新上传。 */ }
  finally { busy.value = false; emit('uploading', false); target.value = '' }
}
</script>
<template>
  <div class="image-input">
    <el-image v-if="modelValue && validImageAddress(modelValue)" :src="modelValue" fit="contain" :preview-src-list="[modelValue]" preview-teleported class="preview"><template #error><span>图片不可访问</span></template></el-image>
    <div class="fields">
      <el-input :model-value="modelValue || ''" :disabled="disabled || busy" maxlength="255" placeholder="上传图片，或填写 /upload/...、https://..." @update:model-value="emit('update:modelValue', $event)" />
      <input ref="input" type="file" accept="image/*" hidden @change="upload" />
      <el-button :loading="busy" :disabled="disabled" @click="input?.click()">{{ modelValue ? '更换图片' : '上传图片' }}</el-button>
      <el-button v-if="modelValue" :disabled="disabled || busy" @click="emit('update:modelValue', '')">清除</el-button>
      <small v-if="modelValue && !validImageAddress(modelValue)" class="error">请上传图片或填写访问地址，不能填写电脑磁盘路径。</small>
    </div>
  </div>
</template>
<style scoped>
.image-input { display:flex; gap:12px; width:100%; align-items:flex-start }
.preview { width:80px; height:80px; flex-shrink:0; border:1px solid #ddd; border-radius:4px }
.fields { flex:1; min-width:0 } .fields>.el-input { margin-bottom:8px }
.error { display:block; color:#d33; line-height:1.5 }
</style>
