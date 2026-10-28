<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Hide, RefreshRight, View } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { feedbackMessage } from '@/utils/feedback';
import { adminLogin, getCaptcha } from '@/api/auth';
import { useUserStore } from '@/store/user';
import type { LoginSession } from '@/utils/session';
import logo from '@/assets/qivo-wordmark.svg';
import artwork from '@/assets/auth/admin-workspace-clean.svg';

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const busy = ref(false);
const showPassword = ref(false);
const captchaLoading = ref(false);
const captchaError = ref(false);
const captchaImage = ref('');
const loginError = ref('');
const fieldErrors = ref({ username: '', password: '', captchaCode: '' });
type LoginField = keyof typeof fieldErrors.value;
async function focusField(field: LoginField) {
  await nextTick();
  const id = { username: 'admin-account', password: 'admin-password', captchaCode: 'admin-captcha' }[field];
  document.getElementById(id)?.focus({ preventScroll: true });
}
function clearFeedback(event: Event) {
  loginError.value = '';
  const target = event.target;
  if (target instanceof HTMLInputElement && target.name in fieldErrors.value) {
    fieldErrors.value[target.name as LoginField] = '';
  }
}
const form = ref({ username: '', password: '', captchaKey: '', captchaCode: '' });
let active = true;
let captchaRequest = 0;

async function fetchCaptcha(preserveFeedback = false) {
  if (captchaLoading.value) return;
  if (!preserveFeedback) fieldErrors.value.captchaCode = '';
  const requestId = ++captchaRequest;
  captchaLoading.value = true;
  captchaError.value = false;
  captchaImage.value = '';
  form.value.captchaKey = '';
  form.value.captchaCode = '';
  try {
    const response = await getCaptcha() as unknown as { captchaKey: string; captchaImage: string };
    if (!active || requestId !== captchaRequest) return;
    if (!response.captchaKey || !response.captchaImage) throw new Error('Captcha unavailable');
    form.value.captchaKey = response.captchaKey;
    captchaImage.value = response.captchaImage;
  } catch {
    if (active && requestId === captchaRequest) captchaError.value = true;
  } finally {
    if (active && requestId === captchaRequest) captchaLoading.value = false;
  }
}

async function handleLogin() {
  if (busy.value) return;
  loginError.value = '';
  fieldErrors.value = {
    username: form.value.username ? '' : '请填写管理员账号',
    password: form.value.password ? '' : '请填写密码',
    captchaCode: !form.value.captchaKey
      ? (captchaLoading.value ? '验证码正在加载，请稍候' : '请重新获取验证码')
      : form.value.captchaCode ? '' : '请输入图片中的验证码'
  };
  const first = (Object.keys(fieldErrors.value) as LoginField[]).find(field => fieldErrors.value[field]);
  if (first) { await focusField(first); return; }
  busy.value = true;
  try {
    const response = await adminLogin({ ...form.value }) as unknown as { data: LoginSession };
    if (!active) return;
    userStore.setLogin(response.data, true);
    ElMessage.success('登录成功');
    await router.replace({ path: '/admin', query: route.query });
  } catch (error) {
    if (!active) return;
    const message = feedbackMessage(error, '登录未成功，请核对账号信息后重试');
    if (message.includes('验证码')) fieldErrors.value.captchaCode = message;
    else if (message.includes('账号') && /禁用|停用|无法使用/.test(message)) fieldErrors.value.username = message;
    else loginError.value = message;
    await fetchCaptcha(true);

  } finally {
    busy.value = false;
    if (active && fieldErrors.value.username) await focusField('username');
    else if (active && fieldErrors.value.captchaCode) await focusField('captchaCode');
  }
}

onMounted(() => { void fetchCaptcha(); });
onBeforeUnmount(() => { active = false; captchaRequest++; });
</script>

<template>
  <main class="admin-login">
    <section class="card" aria-label="管理员登录">
      <aside class="scene" aria-hidden="true"><img :src="artwork" alt="" /></aside>
      <div class="form-area">
        <div class="form-content">
          <div class="brand"><img :src="logo" alt="Qivo" /><span>管理控制台</span></div>
          <h1 id="admin-login-title">管理员登录</h1>
          <p class="intro" id="admin-login-intro">使用已分配的账号登录</p>
          <form aria-labelledby="admin-login-title" aria-describedby="admin-login-intro" novalidate @submit.prevent="handleLogin" @input="clearFeedback">
            <fieldset :disabled="busy">
              <div class="field">
                <label for="admin-account">管理员账号</label>
                <input id="admin-account" v-model="form.username" name="username" autocomplete="username" placeholder="输入管理员账号" maxlength="50" :aria-invalid="!!fieldErrors.username" :aria-describedby="fieldErrors.username ? 'admin-account-error' : undefined" required />
                <p v-if="fieldErrors.username" id="admin-account-error" class="field-error" role="alert">{{ fieldErrors.username }}</p>
              </div>
              <div class="field">
                <label for="admin-password">密码</label>
                <div class="password"><input id="admin-password" v-model="form.password" name="password" :type="showPassword ? 'text' : 'password'" autocomplete="current-password" placeholder="输入密码" :aria-invalid="!!fieldErrors.password" :aria-describedby="fieldErrors.password ? 'admin-password-error' : undefined" required /><button class="eye" type="button" :aria-label="showPassword ? '隐藏密码' : '显示密码'" :aria-pressed="showPassword" @click="showPassword = !showPassword"><el-icon><Hide v-if="showPassword" /><View v-else /></el-icon></button></div>
                <p v-if="fieldErrors.password" id="admin-password-error" class="field-error" role="alert">{{ fieldErrors.password }}</p>
              </div>
              <div class="field captcha-field">
                <label for="admin-captcha">图片验证码</label>
                <div class="captcha-row">
                  <input id="admin-captcha" v-model="form.captchaCode" name="captchaCode" autocomplete="off" autocapitalize="off" :spellcheck="false" placeholder="输入验证码" maxlength="10" :aria-invalid="!!fieldErrors.captchaCode" :aria-describedby="fieldErrors.captchaCode || captchaError ? 'admin-captcha-error' : 'admin-captcha-hint'" required />
                  <button class="captcha" type="button" :disabled="captchaLoading || busy" :aria-label="captchaError ? '重新获取验证码图片' : '点击图片更换验证码'" :aria-busy="captchaLoading" @click="fetchCaptcha()">
                    <span v-if="captchaLoading">加载中…</span><img v-else-if="captchaImage" :src="captchaImage" alt="图片验证码" /><span v-else>点击重试</span>
                  </button>
                  <button class="captcha-refresh" type="button" :disabled="captchaLoading || busy" :aria-label="captchaError ? '重新获取验证码' : '刷新验证码'" title="更换验证码" @click="fetchCaptcha()"><el-icon :class="{ 'is-spinning': captchaLoading }"><RefreshRight /></el-icon></button>
                </div>
                <p v-if="fieldErrors.captchaCode || captchaError" id="admin-captcha-error" class="field-error" role="alert">{{ fieldErrors.captchaCode || '验证码加载失败，点击右侧重试' }}</p>
                <p v-else id="admin-captcha-hint" class="captcha-hint">看不清？点击图片或右侧按钮更换验证码</p>
              </div>
              <p v-if="loginError" id="admin-login-message" class="message" role="alert">{{ loginError }}</p>
              <button class="submit" type="submit" :disabled="busy || captchaLoading || !form.captchaKey" :aria-busy="busy">{{ busy ? '登录中…' : '登录管理台' }}</button>
            </fieldset>
          </form>
        </div>
      </div>
    </section>
  </main>
</template>

<style scoped src="./login.css"></style>
