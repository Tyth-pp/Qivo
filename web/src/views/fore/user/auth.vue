<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ArrowLeft, RefreshRight } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { getCaptcha, userLogin, userRegister } from '@/api/auth';
import { useUserStore } from '@/store/user';
import { registrationErrors, usernameHint } from '@/utils/accountValidation';
import type { RegistrationForm } from '@/utils/accountValidation';
import { feedbackMessage } from '@/utils/feedback';
import { safeRedirect } from '@/utils/session';
import type { LoginSession } from '@/utils/session';
import loginArtwork from '@/assets/auth/login-room.webp';
import registerArtwork from '@/assets/auth/register-room.webp';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const isRegister = computed(() => route.name === 'register');
const loginLoading = ref(false);
const registerLoading = ref(false);
const busy = computed(() => loginLoading.value || registerLoading.value);
const loginForm = ref({ username: '', password: '', captchaKey: '', captchaCode: '' });
const loginStep = ref<'credentials' | 'captcha'>('credentials');
const captchaInput = ref<HTMLInputElement | null>(null);
const registerForm = ref({ username: '', password: '', password2: '', email: '', phone: '' });
const showLoginPassword = ref(false);
const showRegisterPassword = ref(false);
const showConfirmPassword = ref(false);
const contactExpanded = ref(false);
const loginError = ref('');
const registerError = ref('');
const registerFieldErrors = ref<Record<string,string>>({});
function checkField(field: keyof RegistrationForm) {
  registerFieldErrors.value[field] = registrationErrors(registerForm.value)[field];
}
function editField(field: keyof RegistrationForm) {
  registerError.value = '';
  if (registerFieldErrors.value[field]) checkField(field);
  if (field === 'password' && registerFieldErrors.value.password2) checkField('password2');
}
async function focusRegisterField(field: string) {
  clearTimeout(focusTimer);
  if (field === 'email' || field === 'phone') contactExpanded.value = true;
  await nextTick();
  const suffix = ({username:'name',password:'password',password2:'confirm',email:'email',phone:'phone'} as Record<string,string>)[field];
  document.getElementById('customer-register-'+suffix)?.focus({preventScroll:true});
}
const captchaImage = ref('');
const captchaLoading = ref(false);
const captchaError = ref(false);
const loginUsername = ref<HTMLInputElement | null>(null);
const registerUsername = ref<HTMLInputElement | null>(null);
let captchaRequest = 0;
let focusTimer: ReturnType<typeof setTimeout> | undefined;
let mounted = false;

async function fetchCaptcha() {
  if (isRegister.value || loginStep.value !== 'captcha' || captchaLoading.value) return;
  const requestId = ++captchaRequest;
  captchaLoading.value = true;
  captchaError.value = false;
  loginForm.value.captchaKey = '';
  loginForm.value.captchaCode = '';
  try {
    const response = await getCaptcha() as unknown as { captchaKey: string; captchaImage: string };
    if (!mounted || requestId !== captchaRequest) return;
    if (!response.captchaKey || !response.captchaImage) throw new Error('Captcha unavailable');
    loginForm.value.captchaKey = response.captchaKey;
    captchaImage.value = response.captchaImage;
  } catch {
    if (!mounted || requestId !== captchaRequest) return;
    captchaImage.value = '';
    captchaError.value = true;
  } finally {
    if (mounted && requestId === captchaRequest) captchaLoading.value = false;
  }
}

function resetCaptcha() {
  captchaRequest++;
  captchaLoading.value = false;
  captchaError.value = false;
  captchaImage.value = '';
  loginForm.value.captchaKey = '';
  loginForm.value.captchaCode = '';
}

async function backToCredentials() {
  if (busy.value) return;
  resetCaptcha();
  loginStep.value = 'credentials';
  loginError.value = '';
  await nextTick();
  loginUsername.value?.focus({ preventScroll: true });
}

function switchMode(register: boolean) {
  if (busy.value || register === isRegister.value) return;
  void router.push({ name: register ? 'register' : 'login', query: route.query });
}

function focusUsername() {
  clearTimeout(focusTimer);
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  focusTimer = setTimeout(() => {
    if (!mounted) return;
    (isRegister.value ? registerUsername.value : loginUsername.value)?.focus({ preventScroll: true });
  }, reduceMotion ? 0 : 520);
}

watch(isRegister, async () => {
  loginStep.value = 'credentials';
  resetCaptcha();
  loginError.value = '';
  registerError.value = '';
  registerFieldErrors.value = {};
  showLoginPassword.value = false;
  showRegisterPassword.value = false;
  showConfirmPassword.value = false;
  await nextTick();
  focusUsername();
});

async function handleLogin() {
  if (busy.value || isRegister.value) return;
  loginError.value = '';
  if (!loginForm.value.username || !loginForm.value.password) {
    loginError.value = '请填写用户名和密码';
    return;
  }
  if (loginStep.value === 'credentials') {
    clearTimeout(focusTimer);
    showLoginPassword.value = false;
    loginStep.value = 'captcha';
    void fetchCaptcha();
    await nextTick();
    captchaInput.value?.focus({ preventScroll: true });
    return;
  }
  if (!loginForm.value.captchaKey) {
    loginError.value = captchaLoading.value ? '验证码正在加载，请稍候' : '请先重新获取验证码';
    return;
  }
  if (!loginForm.value.captchaCode) {
    loginError.value = '请填写验证码';
    return;
  }
  loginLoading.value = true;
  const destination = safeRedirect(route.query.redirect, 'USER');
  try {
    const response = await userLogin({ ...loginForm.value }) as unknown as { data: LoginSession };
    if (!mounted || isRegister.value || loginStep.value !== 'captcha') return;
    userStore.setLogin(response.data, false);
    ElMessage.success('登录成功');
    await router.push(destination);
  } catch (error) {
    if (!mounted || isRegister.value || loginStep.value !== 'captcha') return;
    loginError.value = feedbackMessage(error, '登录未成功，请核对账号信息后重试');
    await fetchCaptcha();
  } finally {
    loginLoading.value = false;
  }
}

async function handleRegister() {
  if (busy.value) return;
  registerError.value = '';
  const form = registerForm.value;
  registerFieldErrors.value = registrationErrors(form);
  const first = Object.keys(registerFieldErrors.value).find(key => registerFieldErrors.value[key]);
  if (first) { await focusRegisterField(first); return; }
  registerLoading.value = true;
  try {
    await userRegister({ username: form.username, password: form.password, email: form.email || undefined, phone: form.phone || undefined });
    if (!mounted || !isRegister.value) return;
    loginForm.value.username = form.username;
    registerForm.value.password = '';
    registerForm.value.password2 = '';
    ElMessage.success('注册成功，请登录');
    await router.push({ name: 'login', query: route.query });
  } catch (error) {
    if (mounted && isRegister.value) {
      const message = feedbackMessage(error, '注册暂未完成，请稍后重试');
      if (message.includes('用户名')) {
        registerFieldErrors.value.username = message;
        await focusRegisterField('username');
      } else registerError.value = message + '，填写的内容已保留。';
    }
  } finally {
    registerLoading.value = false;
  }
}

onMounted(() => {
  mounted = true;
});
onBeforeUnmount(() => {
  mounted = false;
  captchaRequest++;
  clearTimeout(focusTimer);
});
</script>

<template>
  <main class="customer-auth">
    <header class="customer-auth__header">
      <router-link to="/" class="customer-auth__back"><el-icon><ArrowLeft /></el-icon>返回商城</router-link>
    </header>

    <div class="customer-auth__stage">
      <section
        class="customer-auth__card"
        :class="{ 'is-register': isRegister, 'is-expanded': isRegister && contactExpanded }"
        aria-label="账号登录与注册"
      >
        <div class="customer-auth__panel customer-auth__panel--login" :inert="isRegister" :aria-hidden="isRegister">
          <form class="customer-auth__form" aria-labelledby="customer-login-title" novalidate @submit.prevent="handleLogin" @input="loginError = ''">
            <fieldset :disabled="busy || isRegister">
              <button v-if="loginStep === 'captcha'" class="customer-auth__previous" type="button" :disabled="busy" @click="backToCredentials"><el-icon><ArrowLeft /></el-icon>返回账号填写</button>
              <h1 id="customer-login-title">{{ loginStep === 'credentials' ? '登录账号' : '完成图片验证' }}</h1>
              <p class="customer-auth__intro">{{ loginStep === 'credentials' ? '输入账号密码后，需完成图片验证。' : '输入下图中的字符，验证后即可登录。' }}</p>
              <template v-if="loginStep === 'credentials'">
              <label class="customer-auth__field" for="customer-login-name"><span>用户名</span><input id="customer-login-name" ref="loginUsername" v-model="loginForm.username" name="username" autocomplete="username" placeholder="请输入用户名" maxlength="50" required /></label>
              <label class="customer-auth__field" for="customer-login-password">
                <span>密码</span>
                <span class="customer-auth__input-wrap"><input id="customer-login-password" aria-label="密码" v-model="loginForm.password" name="password" :type="showLoginPassword ? 'text' : 'password'" autocomplete="current-password" placeholder="请输入密码" required /><button type="button" class="customer-auth__eye" :aria-label="showLoginPassword ? '隐藏登录密码' : '显示登录密码'" :aria-pressed="showLoginPassword" @click.prevent="showLoginPassword = !showLoginPassword">{{ showLoginPassword ? '隐藏' : '显示' }}</button></span>
              </label>
              </template>
              <template v-else>
              <label class="customer-auth__field" for="customer-login-captcha">
                <span>图形验证码</span>
                <span class="customer-auth__captcha-row"><input id="customer-login-captcha" ref="captchaInput" aria-label="图形验证码" v-model="loginForm.captchaCode" name="captchaCode" autocomplete="off" autocapitalize="off" spellcheck="false" maxlength="10" placeholder="请输入验证码" :aria-describedby="captchaError ? 'customer-captcha-error' : undefined" required /><button class="customer-auth__captcha" type="button" :disabled="captchaLoading || busy" :aria-label="captchaError ? '重新获取验证码' : '刷新验证码'" :aria-busy="captchaLoading" @click.prevent="fetchCaptcha"><span v-if="captchaLoading" class="customer-auth__captcha-text">加载中…</span><img v-else-if="captchaImage" :src="captchaImage" alt="图形验证码" /><span v-else class="customer-auth__captcha-text">重新获取</span><el-icon :class="{ 'is-spinning': captchaLoading }"><RefreshRight /></el-icon></button></span>
              </label>
              <p v-if="captchaError" id="customer-captcha-error" class="customer-auth__captcha-error" role="status">验证码未加载成功，点击右侧重试</p>
              <p class="customer-auth__captcha-hint">看不清图片？点击图片更换</p>
              </template>
              <p class="customer-auth__error" role="status" aria-live="polite">{{ loginError }}</p>
              <button class="customer-auth__primary" type="submit" :disabled="busy || (loginStep === 'captcha' && (captchaLoading || !loginForm.captchaKey))" :aria-busy="loginLoading">{{ loginLoading ? '登录中…' : loginStep === 'credentials' ? '继续验证' : '登录' }}</button>
              <p v-if="loginStep === 'credentials'" class="customer-auth__switch-line">还没有账号？<button type="button" :disabled="busy" @click="switchMode(true)">创建账号</button></p>
            </fieldset>
          </form>
        </div>

        <div class="customer-auth__panel customer-auth__panel--register" :inert="!isRegister" :aria-hidden="!isRegister">
          <form class="customer-auth__form" aria-labelledby="customer-register-title" novalidate @submit.prevent="handleRegister">
            <fieldset :disabled="busy || !isRegister">
              <h1 id="customer-register-title">创建账号</h1>
              <p class="customer-auth__intro">用户名用于登录，联系方式可稍后补充。</p>
              <label class="customer-auth__field" for="customer-register-name"><span>用户名</span><input id="customer-register-name" :aria-invalid="!!registerFieldErrors.username" aria-describedby="register-username-feedback" @blur="checkField('username')" @input="editField('username')" ref="registerUsername" v-model="registerForm.username" name="username" autocomplete="username" placeholder="设置用户名" maxlength="50" required /><small v-if="registerFieldErrors.username || usernameHint" id="register-username-feedback" class="customer-auth__field-feedback" :class="{ 'is-error': registerFieldErrors.username }" :role="registerFieldErrors.username ? 'alert' : undefined">{{registerFieldErrors.username || usernameHint}}</small></label>
              <label class="customer-auth__field" for="customer-register-password"><span>密码</span><span class="customer-auth__input-wrap"><input id="customer-register-password" :aria-invalid="!!registerFieldErrors.password" aria-describedby="register-password-feedback" @blur="checkField('password')" @input="editField('password')" aria-label="密码" v-model="registerForm.password" name="password" :type="showRegisterPassword ? 'text' : 'password'" autocomplete="new-password" placeholder="设置密码" required /><button type="button" class="customer-auth__eye" :aria-label="showRegisterPassword ? '隐藏注册密码' : '显示注册密码'" :aria-pressed="showRegisterPassword" @click.prevent="showRegisterPassword = !showRegisterPassword">{{ showRegisterPassword ? '隐藏' : '显示' }}</button></span><small v-if="registerFieldErrors.password || '至少 8 位，建议使用字母、数字或符号'" id="register-password-feedback" class="customer-auth__field-feedback" :class="{ 'is-error': registerFieldErrors.password }" :role="registerFieldErrors.password ? 'alert' : undefined">{{registerFieldErrors.password || '至少 8 位，建议使用字母、数字或符号'}}</small></label>
              <label class="customer-auth__field" for="customer-register-confirm"><span>确认密码</span><span class="customer-auth__input-wrap"><input id="customer-register-confirm" :aria-invalid="!!registerFieldErrors.password2" :aria-describedby="registerFieldErrors.password2 ? 'register-password2-feedback' : undefined" @blur="checkField('password2')" @input="editField('password2')" aria-label="确认密码" v-model="registerForm.password2" name="password2" :type="showConfirmPassword ? 'text' : 'password'" autocomplete="new-password" placeholder="再次输入密码" required /><button type="button" class="customer-auth__eye" :aria-label="showConfirmPassword ? '隐藏确认密码' : '显示确认密码'" :aria-pressed="showConfirmPassword" @click.prevent="showConfirmPassword = !showConfirmPassword">{{ showConfirmPassword ? '隐藏' : '显示' }}</button></span><small v-if="registerFieldErrors.password2 || ''" id="register-password2-feedback" class="customer-auth__field-feedback" :class="{ 'is-error': registerFieldErrors.password2 }" :role="registerFieldErrors.password2 ? 'alert' : undefined">{{registerFieldErrors.password2 || ''}}</small></label>
              <button class="customer-auth__contact-toggle" type="button" :aria-expanded="contactExpanded" aria-controls="customer-contact-fields" @click="contactExpanded = !contactExpanded">联系方式 <span>选填</span><span class="customer-auth__chevron" :class="{ 'is-open': contactExpanded }" aria-hidden="true">{{ contactExpanded ? '−' : '＋' }}</span></button>
              <div v-if="contactExpanded" id="customer-contact-fields" class="customer-auth__contacts">
                <label class="customer-auth__field" for="customer-register-email"><span>邮箱</span><input id="customer-register-email" :aria-invalid="!!registerFieldErrors.email" :aria-describedby="registerFieldErrors.email ? 'register-email-feedback' : undefined" @blur="checkField('email')" @input="editField('email')" v-model="registerForm.email" name="email" type="email" autocomplete="email" placeholder="输入邮箱（选填）" /><small v-if="registerFieldErrors.email || ''" id="register-email-feedback" class="customer-auth__field-feedback" :class="{ 'is-error': registerFieldErrors.email }" :role="registerFieldErrors.email ? 'alert' : undefined">{{registerFieldErrors.email || ''}}</small></label>
                <label class="customer-auth__field" for="customer-register-phone"><span>手机号码</span><input id="customer-register-phone" :aria-invalid="!!registerFieldErrors.phone" :aria-describedby="registerFieldErrors.phone ? 'register-phone-feedback' : undefined" @blur="checkField('phone')" @input="editField('phone')" v-model="registerForm.phone" name="phone" type="tel" autocomplete="tel" placeholder="输入联系电话（选填）" maxlength="20" /><small v-if="registerFieldErrors.phone || ''" id="register-phone-feedback" class="customer-auth__field-feedback" :class="{ 'is-error': registerFieldErrors.phone }" :role="registerFieldErrors.phone ? 'alert' : undefined">{{registerFieldErrors.phone || ''}}</small></label>
              </div>
              <p class="customer-auth__error" role="status" aria-live="polite">{{ registerError }}</p>
              <button class="customer-auth__primary" type="submit" :disabled="busy" :aria-busy="registerLoading">{{ registerLoading ? '创建中…' : '创建账号' }}</button>
              <p class="customer-auth__switch-line">已有账号？<button type="button" :disabled="busy" @click="switchMode(false)">登录</button></p>
            </fieldset>
          </form>
        </div>

        <aside class="customer-auth__picture" aria-label="同一房间的桌面场景">
          <img class="customer-auth__art customer-auth__art--login" :src="loginArtwork" :aria-hidden="isRegister" alt="窗边浅木桌面上的石墨灰台灯、合上的钴蓝笔记本与暖白杯子" />
          <img class="customer-auth__art customer-auth__art--register" :src="registerArtwork" :aria-hidden="!isRegister" alt="同一张桌子另一侧，打开的钴蓝笔记本、笔与装有耳机盒的收纳托盘" />
        </aside>
      </section>
    </div>

  </main>
</template>

<style scoped src="./auth.css"></style>
