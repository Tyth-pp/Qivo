<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { passwordError } from '@/utils/accountValidation';
import { feedbackMessage } from '@/utils/feedback';
import { changeOwnPassword } from '@/api/account';
import { clearSessionIfCurrent, readSession } from '@/utils/session';
const props = defineProps<{ admin?: boolean }>();
const emit = defineEmits<{ busy: [value: boolean]; saved: [] }>();
const errors = reactive({ old: '', next: '', confirm: '' }), formError = ref('');
const router = useRouter(), saving = ref(false), form = reactive({ old: '', next: '', confirm: '' });
async function submit() {
  if (saving.value) return;
  formError.value = '';
  errors.old = form.old ? '' : '请输入当前密码';
  errors.next = passwordError(form.next, '新密码');
  if (!errors.next && form.old === form.next) errors.next = '新密码不能与当前密码相同';
  errors.confirm = !form.confirm ? '请再次输入新密码' : form.next !== form.confirm ? '两次输入的新密码不一致，请重新确认' : '';
  if (Object.values(errors).some(Boolean)) return;
  const domain = props.admin ? 'ADMIN' : 'USER', token = readSession(domain)?.token;
  saving.value = true; emit('busy', true);
  try {
    await changeOwnPassword(!!props.admin, form.old, form.next);
    form.old = ''; form.next = ''; form.confirm = '';
    // 请求已完成，先释放路由离开保护，再清理对应身份并跳转。
    saving.value = false; emit('busy', false);
    emit('saved');
    if (clearSessionIfCurrent(domain, token)) await router.replace(props.admin ? '/admin/login' : '/login');
    ElMessage.success('密码已修改，请重新登录');
  } catch (error: any) {
    if (error.response?.data?.code === 1002) errors.old = '当前密码不正确，请重新输入';
    else formError.value = feedbackMessage(error, '密码暂未修改，请稍后重试');
  }
  finally { saving.value = false; emit('busy', false); }
}
</script>
<template>
  <el-form class="password-change" label-position="top" @submit.prevent="submit">
    <p class="password-note">修改后需要重新登录，此账号原有登录会话将失效。</p>
    <el-form-item label="当前密码" :error="errors.old"><el-input v-model="form.old" @input="errors.old='';formError=''" aria-label="当前密码" type="password" autocomplete="current-password" show-password maxlength="72" :disabled="saving" /></el-form-item>
    <el-form-item label="新密码" :error="errors.next"><el-input v-model="form.next" @input="errors.next='';formError=''" aria-label="新密码" type="password" autocomplete="new-password" show-password maxlength="72" :disabled="saving" placeholder="至少 8 位" /></el-form-item>
    <el-form-item label="确认新密码" :error="errors.confirm"><el-input v-model="form.confirm" @input="errors.confirm='';formError=''" aria-label="确认新密码" type="password" autocomplete="new-password" show-password maxlength="72" :disabled="saving" /></el-form-item>
    <p v-if="formError" class="password-feedback" role="alert">{{formError}}，已保留填写内容。</p>
    <el-button type="primary" native-type="submit" :loading="saving">保存并重新登录</el-button>
  </el-form>
</template>
<style scoped>
.password-note { color: #738091; font-size: 12px; line-height: 1.8; margin: 0 0 24px; }
.password-change { max-width: 460px; }
.password-feedback { font-size: 12px; line-height: 1.7; color: #b94943; margin-bottom: 16px; }
</style>
