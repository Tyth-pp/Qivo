<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue';
import { useRoute, useRouter, onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Tickets, Star, Location, User, SwitchButton } from '@element-plus/icons-vue';
import { getProfile, updateProfile, getAddressList, addAddress, updateAddress, deleteAddress } from '@/api/user';
import { getCollectList, cancelCollect, getProductDetail } from '@/api/product';
import { useUserStore } from '@/store/user';
import { useLoad } from '@/composables/useLoad';
import CustomerOrders from '@/components/CustomerOrders.vue';
import PageState from '@/components/PageState.vue';
import ProductCard from '@/components/ProductCard.vue';
import AccountAvatar from '@/components/AccountAvatar.vue';
import AvatarEditor from '@/components/AvatarEditor.vue';
import PasswordChangeForm from '@/components/PasswordChangeForm.vue';
const route = useRoute(), router = useRouter(), session = useUserStore(), user = reactive<any>({}), addresses = ref<any[]>([]), favorites = ref<any[]>([]), busy = ref(false);
const avatarUploading = ref(false), passwordVisible = ref(false), passwordBusy = ref(false);
const sections = [
    { id: 'orders', name: '我的订单', icon: Tickets }, { id: 'collection', name: '我的收藏', icon: Star }, { id: 'address', name: '收货地址', icon: Location }, { id: 'info', name: '账号资料', icon: User }
];
const tab = computed(() => sections.some(s => s.id === route.query.tab) ? String(route.query.tab) : 'orders');
const { loading, error, run } = useLoad(), addrVisible = ref(false), addrForm = reactive<any>({}), original = ref('');
async function load() {
    const token = session.token;
    return run(async () => {
        if (tab.value === 'address')
            return getAddressList();
        if (tab.value === 'collection') {
            const ids: any = await getCollectList();
            return {
                data: await Promise.all((ids.data || []).map(async (id: number) => {
                    try {
                        const d: any = await getProductDetail(id);
                        return d.data.product;
                    }
                    catch (e: any) {
                        if (e.response?.status !== 404)
                            throw e;
                        return { id, name: '商品暂不可用', totalStock: 0 };
                    }
                }))
            };
        }
        ;
        return getProfile();
    }, (r: any) => {
        if (session.isAdmin || session.token !== token) return;
        if (tab.value === 'address')
            addresses.value = r.data || [];
        else if (tab.value === 'collection')
            favorites.value = r.data || [];
        else {
            Object.assign(user, r.data || {});
            session.updateIdentity({avatar: user.avatar || ''});
            original.value = JSON.stringify({ email: user.email, phone: user.phone, avatar: user.avatar || '' });
        }
    });
}
watch(tab, load, { immediate: true });
async function saveProfile() {
    if (busy.value || avatarUploading.value) return;
    busy.value = true;
    try {
        await updateProfile({ email: user.email, phone: user.phone, avatar: user.avatar || '' });
        session.updateIdentity({avatar: user.avatar || ''});
        original.value = JSON.stringify({ email: user.email, phone: user.phone, avatar: user.avatar || '' });
        ElMessage.success('资料已保存');
    }
    catch {
    }
    finally {
        busy.value = false;
    }
}
function openPassword() {
    if (original.value !== JSON.stringify({ email: user.email, phone: user.phone, avatar: user.avatar || '' })) {
        ElMessage.info('请先保存资料，再修改密码'); return;
    }
    passwordVisible.value = true;
}
async function dirtyConfirm() {
    if (tab.value !== 'info' || !original.value || original.value === JSON.stringify({ email: user.email, phone: user.phone, avatar: user.avatar || '' }))
        return true;
    try {
        await ElMessageBox.confirm('资料尚未保存，确定离开吗？', '放弃修改');
        return true;
    }
    catch {
        return false;
    }
}
function changeTab(id: string) {
    router.push({ path: route.path, query: { tab: id } });
}
const addressOriginal = ref('');
async function canLeave() {
    if (busy.value || avatarUploading.value || passwordBusy.value)
        return false;
    if (addrVisible.value && addressOriginal.value !== JSON.stringify(addrForm)) {
        try {
            await ElMessageBox.confirm('地址尚未保存，确定离开吗？', '放弃修改');
        }
        catch {
            return false;
        }
    }
    return dirtyConfirm();
}
async function closeAddress(done: () => void) {
    if (await canLeave())
        done();
}
onBeforeRouteLeave(canLeave);
onBeforeRouteUpdate((to, from) => to.query.tab === from.query.tab || canLeave());
function openAddress(a?: any) {
    Object.assign(addrForm, { id: null, receiverName: '', receiverPhone: '', province: '', city: '', district: '', detail: '', isDefault: 0 }, a || {});
    addressOriginal.value = JSON.stringify(addrForm);
    addrVisible.value = true;
}
async function saveAddress() {
    if (busy.value)
        return;
    if (!addrForm.receiverName.trim() || !/^1\d{10}$/.test(addrForm.receiverPhone) || !['province', 'city', 'district', 'detail'].every(k => addrForm[k]?.trim())) {
        ElMessage.warning('请填写完整地址和有效手机号');
        return;
    }
    busy.value = true;
    try {
        if (addrForm.id)
            await updateAddress(addrForm.id, addrForm);
        else
            await addAddress(addrForm);
        addrVisible.value = false;
        ElMessage.success('地址已保存');
        await load();
    }
    catch {
    }
    finally {
        busy.value = false;
    }
}
async function removeAddress(id: number) {
    try {
        await ElMessageBox.confirm('删除后不可恢复，确定删除该地址吗？', '删除地址', { type: 'warning' });
        await deleteAddress(id);
        await load();
    }
    catch {
    }
}
async function uncollect(id: number) {
    if (busy.value)
        return;
    busy.value = true;
    try {
        await cancelCollect(id);
        favorites.value = favorites.value.filter(p => p.id !== id);
        ElMessage.success('已取消收藏');
    }
    catch {
    }
    finally {
        busy.value = false;
    }
}
async function logout() {
    if (!await canLeave())
        return;
    try {
        if (!await session.logout()) return;
        original.value = '';
        addrVisible.value = false;
        router.push('/login');
    }
    catch {
        ElMessage.error('退出失败，请重试');
    }
}
</script>

<template>
  <nav class="store-crumb" aria-label="当前位置"><router-link to="/">首页</router-link><span>/</span><strong>个人中心</strong></nav>
  <div class="profile-layout">
    <aside class="panel profile-side">
      <div class="profile-identity">
        <AccountAvatar :src="session.avatar" :name="session.username" />
        <div>
          <strong>{{session.username}}</strong>
          <small>当前账号</small>
        </div>
      </div>
      <button v-for="s in sections" :key="s.id" :class="{active:tab===s.id}" @click="changeTab(s.id)">
        <el-icon>
          <component :is="s.icon" />
        </el-icon>
        {{s.name}}
      </button>
      <button class="logout" @click="logout">
        <el-icon>
          <SwitchButton />
        </el-icon>
        退出登录
      </button>
    </aside>
    <section class="panel profile-content">
      <CustomerOrders v-if="tab==='orders'" compact />
      <template v-else>
        <div class="section-heading">
          <h1>{{sections.find(s=>s.id===tab)?.name}}</h1>
          <el-button v-if="tab==='address'" type="primary" @click="openAddress()">添加地址</el-button>
        </div>
        <PageState
          :loading="loading"
          :error="error"
          :empty="tab==='collection'?!favorites.length:tab==='address'?!addresses.length:false"
          :title="tab==='collection'?'收藏架还空着':'还没有收货地址'"
          @retry="load"
        >
          <template #action>
            <el-button v-if="tab==='address'" @click="openAddress()">添加地址</el-button>
            <router-link v-else to="/product/list" class="text-link">去发现好物 →</router-link>
          </template>
          <div v-if="tab==='collection'" class="product-grid favorites-grid">
            <ProductCard v-for="p in favorites" :key="p.id" :product="p">
              <el-button link :disabled="busy" style="margin-top:12px" @click="uncollect(p.id)">取消收藏</el-button>
            </ProductCard>
          </div>
          <div v-else-if="tab==='address'">
            <article v-for="a in addresses" :key="a.id" class="profile-address" :class="{ 'is-default': a.isDefault }">
              <div>
                <strong>{{a.receiverName}}</strong>
                <span>{{a.receiverPhone}}</span>
                <span class="status-tag" v-if="a.isDefault">默认</span>
                <p>{{a.province}}{{a.city}}{{a.district}}{{a.detail}}</p>
              </div>
              <div class="inline-actions">
                <el-button link @click="openAddress(a)">编辑</el-button>
                <el-button link class="danger-link" @click="removeAddress(a.id)">删除</el-button>
              </div>
            </article>
          </div>
          <el-form v-else label-position="top" class="profile-form" @submit.prevent="saveProfile">
            <AvatarEditor v-model="user.avatar" :name="session.username" :disabled="busy" @busy="avatarUploading=$event" />
            <el-form-item label="用户名">
              <el-input :model-value="user.username" disabled />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="user.email" type="email" />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="user.phone" maxlength="11" />
            </el-form-item>
            <el-button type="primary" :loading="busy" :disabled="avatarUploading" native-type="submit">保存资料</el-button>
            <section class="profile-security"><h2>账号安全</h2><p>定期更新密码，保护账号与订单信息。</p><el-button :disabled="avatarUploading || busy" @click="openPassword">修改密码</el-button></section>
          </el-form>
        </PageState>
      </template>
    </section>
  </div>
  <el-dialog v-model="passwordVisible" class="consumer-dialog" title="修改密码" width="460px" destroy-on-close :close-on-click-modal="!passwordBusy" :close-on-press-escape="!passwordBusy" :show-close="!passwordBusy">
    <PasswordChangeForm @busy="passwordBusy=$event" @saved="passwordVisible=false;original=''" />
  </el-dialog>
  <el-dialog class="consumer-dialog"
    v-model="addrVisible"
    :title="addrForm.id?'编辑收货地址':'添加收货地址'"
    width="540px"
    :close-on-click-modal="false"
    :before-close="closeAddress"
    :show-close="!busy"
    :close-on-press-escape="!busy"
  >
    <el-form label-position="top" class="form-grid">
      <el-form-item label="收件人">
        <el-input v-model="addrForm.receiverName" maxlength="30" />
      </el-form-item>
      <el-form-item label="手机号">
        <el-input v-model="addrForm.receiverPhone" maxlength="11" />
      </el-form-item>
      <el-form-item label="省份">
        <el-input v-model="addrForm.province" />
      </el-form-item>
      <el-form-item label="城市">
        <el-input v-model="addrForm.city" />
      </el-form-item>
      <el-form-item label="区 / 县">
        <el-input v-model="addrForm.district" />
      </el-form-item>
      <el-form-item label="默认地址">
        <el-switch v-model="addrForm.isDefault" :active-value="1" :inactive-value="0" />
      </el-form-item>
      <el-form-item label="详细地址" class="full">
        <el-input v-model="addrForm.detail" type="textarea" :rows="2" maxlength="200" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="busy" @click="closeAddress(()=>addrVisible=false)">取消</el-button>
      <el-button type="primary" :loading="busy" @click="saveAddress">保存地址</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.profile-layout {
  display: grid;
  grid-template-columns: 215px minmax(0,1fr);
  gap: 27px;
  align-items: start;
}

.profile-side {
  padding: 21px 13px;
}

.profile-identity {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 0 10px 22px;
  border-bottom: 1px solid var(--line);
  margin-bottom: 15px;
}

.profile-identity strong {
  font-size: 13px;
  font-weight: 500;
}

.profile-identity small {
  display: block;
  font-size: 11px;
  margin-top: 4px;
}

.profile-side button {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 12px 15px;
  width: 100%;
  border: 0;
  border-radius: 5px;
  background: none;
  text-align: left;
  font-size: 13px;
  color: #687382;
  margin-bottom: 5px;
}

.profile-side button.active {
  background: #edf2ff;
  color: #164dff;
}

.profile-side .logout {
  border-top: 1px solid var(--line);
  border-radius: 0;
  margin-top: 15px;
  padding-top: 20px;
  color: #a96b55;
}

.profile-content {
  margin-top: 0 !important;
  min-width: 0;
}

.favorites-grid {
  grid-template-columns: repeat(3,minmax(0,1fr));
  gap: 25px 20px;
}

.favorites-grid :deep(.product-card-bottom) {
  align-items: flex-start;
  flex-direction: column;
  gap: 10px;
}

.profile-address {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  border-bottom: 1px solid var(--line);
  padding: 22px 0;
}

.profile-address:last-child {
  border: 0;
}

.profile-address strong {
  font-weight: 500;
  font-size: 13px;
}

.profile-address span {
  margin-left: 15px;
  font-size: 12px;
}

.profile-address p {
  font-size: 12px;
  color: var(--muted);
  margin-top: 10px;
}

.profile-form {
  width: 380px;
  margin-top: 28px;
}
.profile-content :deep(.page-state) { min-height: 270px; padding: 26px; }
.profile-content :deep(.section-heading h1), .profile-content :deep(.section-heading h2) { font-size: 18px; }
.profile-content { min-height: 0; }
</style>

<style scoped>
.profile-security { margin-top: 34px; padding-top: 25px; border-top: 1px solid var(--line); }
.profile-security h2 { font-size: 16px; margin-bottom: 10px; }
.profile-security p { color: #798493; font-size: 12px; margin: 0 0 17px; }
.profile-content :deep(.page-state) { min-height: 270px; padding: 26px; }
.profile-content :deep(.section-heading h1), .profile-content :deep(.section-heading h2) { font-size: 18px; }
.profile-content { min-height: 0; }
</style>
