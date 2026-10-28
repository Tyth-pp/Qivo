<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import { onBeforeRouteUpdate } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getAdminProfile, updateAdminProfile } from '@/api/account';
import { useUserStore } from '@/store/user';
import { useLoad } from '@/composables/useLoad';
import AvatarEditor from './AvatarEditor.vue';
import PasswordChangeForm from './PasswordChangeForm.vue';
import PageState from './PageState.vue';
const props = defineProps<{ modelValue: boolean }>();
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>();
const session = useUserStore(), tab = ref('profile'), saving = ref(false), uploading = ref(false), passwordBusy = ref(false);
const form = reactive({ username: '', realName: '', avatar: '' }), original = ref('');
const { loading, error, run } = useLoad();
function load() {
  const token = session.token;
  return run(() => getAdminProfile(), (r: any) => {
    if (!session.isAdmin || session.token !== token) return;
    Object.assign(form, { username: r.data.username, realName: r.data.realName || '', avatar: r.data.avatar || '' });
    original.value = JSON.stringify(form);
    session.updateIdentity({ realName: form.realName, avatar: form.avatar });
  });
}
watch(() => props.modelValue, value => { if (value) { tab.value = 'profile'; load(); } });
async function canClose() {
  if (saving.value || uploading.value || passwordBusy.value) return false;
  if (original.value && JSON.stringify(form) !== original.value) {
    try { await ElMessageBox.confirm('个人资料尚未保存，确定放弃修改吗？', '放弃修改'); }
    catch { return false; }
  }
  return true;
}
async function changeTab(next: string, current: string) {
  if (saving.value || uploading.value || passwordBusy.value) return false;
  if (current === 'profile' && !await canClose()) return false;
  if (current === 'profile' && original.value) Object.assign(form, JSON.parse(original.value));
  return true;
}
async function close(done: () => void) { if (await canClose()) done(); }
onBeforeRouteUpdate(async () => { if (!props.modelValue) return true; if (!await canClose()) return false; emit('update:modelValue', false); return true; });
async function save() {
  if (saving.value || uploading.value) return;
  saving.value = true;
  try {
    await updateAdminProfile({ realName: form.realName, avatar: form.avatar });
    original.value = JSON.stringify(form); session.updateIdentity({ realName: form.realName, avatar: form.avatar });
    ElMessage.success('个人资料已保存');
  } catch { /* 失败保留编辑内容 */ }
  finally { saving.value = false; }
}
</script>
<template>
  <el-drawer :model-value="modelValue" title="账号设置" size="500px" destroy-on-close :before-close="close" :close-on-click-modal="!saving && !uploading && !passwordBusy" :close-on-press-escape="!saving && !uploading && !passwordBusy" :show-close="!saving && !uploading && !passwordBusy" @update:model-value="emit('update:modelValue',$event)">
    <div class="admin-account-settings">
      <p class="account-description">管理自己的资料与登录密码</p>
      <el-tabs v-model="tab" :before-leave="changeTab">
        <el-tab-pane label="个人资料" name="profile">
          <PageState :loading="loading" :error="error" @retry="load">
            <el-form label-position="top" @submit.prevent="save">
              <AvatarEditor v-model="form.avatar" :name="form.realName || form.username" :disabled="saving" @busy="uploading=$event" />
              <el-form-item label="账号"><el-input :model-value="form.username" disabled /></el-form-item>
              <el-form-item label="显示名称"><el-input v-model="form.realName" aria-label="显示名称" maxlength="50" :disabled="saving" /></el-form-item>
              <p class="account-help">角色与权限由账号管理员维护。</p>
              <el-button type="primary" native-type="submit" :loading="saving" :disabled="uploading">保存资料</el-button>
            </el-form>
          </PageState>
        </el-tab-pane>
        <el-tab-pane label="修改密码" name="password" lazy><PasswordChangeForm v-if="tab==='password'" admin @busy="passwordBusy=$event" @saved="emit('update:modelValue',false)" /></el-tab-pane>
      </el-tabs>
    </div>
  </el-drawer>
</template>
<style scoped>
.admin-account-settings { padding: 0 10px 24px; }
.account-description { font-size: 13px; color: #7c8777; margin: 0 0 24px; }
.account-help { font-size: 12px; color: #7b8575; margin: 0 0 24px; }
.admin-account-settings :deep(.el-tab-pane) { padding-top: 22px; }
</style>
