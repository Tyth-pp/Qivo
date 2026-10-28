<script setup lang="ts">
import { computed, ref, watch, onMounted, onBeforeUnmount } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Search, ShoppingBag, User, Tickets, ArrowDown, ArrowRight, Star, Location, Setting, SwitchButton } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import AccountAvatar from './AccountAvatar.vue';
import ProductImage from './ProductImage.vue';
import { price } from '@/utils/display';
import BrandLogo from './BrandLogo.vue';
import { getProductList } from '@/api/product';
import { getIndexData } from '@/api/index';
import { useUserStore } from '@/store/user';
import { useCartStore } from '@/store/cart';
import { getCartList } from '@/api/cart';
import { useCollectionStore } from '@/store/collection';
defineProps<{
    compact?: boolean;
}>();
const route = useRoute(), router = useRouter(), user = useUserStore(), cart = useCartStore(), keyword = ref('');
const collections = useCollectionStore();
watch(() => [user.token, user.isAdmin, route.path], () => {
    collections.refresh().catch(() => { /* 请求层提示；收藏失败不阻断匿名商品浏览 */ });
}, { immediate: true });
const categories = ref<any[]>([]), products = ref<any[]>([]);
const openCategory = ref<number | null>(null), accountOpen = ref(false), loggingOut = ref(false);
const categoryTriggers = new Map<number, HTMLButtonElement>();
const accountTrigger = ref<HTMLButtonElement>();
const customer = computed(() => !!user.token && !user.isAdmin);
const menuCategory = computed(() => categories.value.find(c => c.id === openCategory.value));
const menuChildren = computed(() => categories.value.filter(c => c.parentId === openCategory.value && c.status !== 0));
const categoryProducts = ref<Record<number, any[]>>({});
const menuProducts = computed(() => (categoryProducts.value[openCategory.value ?? -1] ?? products.value).filter(p => p.status !== 0 && Number(p.totalStock ?? p.stock) > 0 && (p.categoryId === openCategory.value || menuChildren.value.some(c => c.id === p.categoryId))).slice(0, 3));
function closeMenus(restore = false) {
    if (restore) { if (openCategory.value !== null) categoryTriggers.get(openCategory.value)?.focus(); else if (accountOpen.value) accountTrigger.value?.focus(); }
    openCategory.value = null; accountOpen.value = false;
}
function toggleCategory(id: number) {
    accountOpen.value = false; openCategory.value = openCategory.value === id ? null : id;
    if (openCategory.value !== null && !categoryProducts.value[id]) {
        getProductList({ page: 1, pageSize: 3, categoryId: id }).then((r: any) => {
            categoryProducts.value[id] = r.data?.records || [];
        }).catch(() => { /* 请求层反馈；仍可从子分类或整个分类入口继续浏览 */ });
    }
}
function escapeMenu(event: KeyboardEvent) { if (event.key === 'Escape') closeMenus(true); }
function focusOutside(event: FocusEvent) { if (!(event.target as HTMLElement)?.closest('.store-header')) closeMenus(); }
onMounted(() => { document.addEventListener('keydown', escapeMenu); document.addEventListener('focusin', focusOutside); });
onBeforeUnmount(() => { document.removeEventListener('keydown', escapeMenu); document.removeEventListener('focusin', focusOutside); });
watch(() => route.fullPath, () => closeMenus());
async function logout() {
    if (loggingOut.value) return;
    loggingOut.value = true;
    try { if (await user.logout()) { closeMenus(); await router.push('/'); ElMessage.success('已退出登录'); } }
    catch { ElMessage.error('退出未成功，请重试'); }
    finally { loggingOut.value = false; }
}

const mainCategories = computed(() => categories.value.filter(c => !c.parentId && c.status !== 0).slice(0, 2));
const activeCategoryId = computed(() => {
    const category = categories.value.find(c => String(c.id) === String(route.query.categoryId));
    return category ? category.parentId || category.id : null;
});
getIndexData().then((r: any) => {
    categories.value = r.data?.categories || [];
    const seen = new Set<number>();
    products.value = [...(r.data?.hotProducts || []), ...(r.data?.newProducts || [])].filter(p => !seen.has(p.id) && !!seen.add(p.id));
}).catch(() => { /* 分类导航失败不阻断商品入口 */ });
watch(() => route.query.keyword, v => {
    keyword.value = String(v || '');
}, { immediate: true });
watch(() => user.token, async (token) => {
    if (!token || user.isAdmin) {
        cart.setCount(0);
        return;
    }
    try {
        const r: any = await getCartList();
        if (token === user.token && !user.isAdmin)
            cart.setCount((r.data || []).reduce((n: number, i: any) => n + i.quantity, 0));
    }
    catch { /* 数量失败不阻断导航 */
    }
}, { immediate: true });
function search() {
    router.push({ path: '/product/list', query: keyword.value.trim() ? { keyword: keyword.value.trim() } : {} });
}
</script>

<template>
  <div class="storefront" :class="{ 'catalog-store': route.path.startsWith('/product'), 'home-store': route.path === '/' }">
    <header class="store-header">
      <div class="store-header-inner">
        <BrandLogo wordmark />
        <nav v-if="!compact" class="store-nav">
          <router-link to="/" :class="{ active: route.path === '/' }">首页</router-link>
          <router-link to="/product/list" :class="{ active: route.path.startsWith('/product') && !route.query.categoryId }">全部商品</router-link>
          <button v-for="c in mainCategories" :key="c.id" :ref="el => { if (el) categoryTriggers.set(c.id, el as HTMLButtonElement); }" class="category-trigger" :class="{active:openCategory===c.id || activeCategoryId===c.id}" :aria-expanded="openCategory===c.id" aria-controls="store-category-panel" @click="toggleCategory(c.id)">{{c.name}}<el-icon><ArrowDown /></el-icon></button>
          <router-link to="/about" :class="{ active: route.path === '/about' }">关于栖物</router-link>
        </nav>
        <span v-else class="muted checkout-step">
          购物车
          <span>／</span>
          <b>确认订单</b>
          <span>／</span>
          完成
        </span>
        <div class="store-tools" v-if="!compact">
          <form class="header-search" @submit.prevent="search">
            <el-icon>
              <Search />
            </el-icon>
            <input v-model="keyword" placeholder="搜索喜欢的好物…" aria-label="搜索商品" />
          </form>
          <router-link to="/cart" class="nav-icon labeled-tool" aria-label="购物车">
            <el-badge :value="cart.count" :hidden="!cart.count" :max="99">
              <el-icon>
                <ShoppingBag />
              </el-icon>
            </el-badge><span>购物车</span>
          </router-link>
          <router-link to="/order/list" class="nav-icon">
            <el-icon>
              <Tickets />
            </el-icon>
            我的订单
          </router-link>
          <button v-if="customer" ref="accountTrigger" class="account-menu-trigger" :aria-expanded="accountOpen" aria-controls="store-account-panel" @click="openCategory=null;accountOpen=!accountOpen">
            <AccountAvatar :src="user.avatar" :name="user.username" :size="30" /><span class="nav-username">{{user.username}}</span><el-icon><ArrowDown /></el-icon>
          </button>
          <router-link v-else :to="{path:'/login',query:{redirect:route.fullPath}}" class="guest-entry">登录 / 注册</router-link>
        </div>
        <router-link v-else to="/cart" class="text-link" style="margin-left:auto">返回购物车</router-link>
      </div>
      <div v-if="openCategory!==null && !compact" id="store-category-panel" class="store-category-panel" role="region" :aria-label="menuCategory?.name+'分类导航'">
        <div class="category-panel-links"><h2>{{menuCategory?.name}}</h2><router-link v-for="c in menuChildren" :key="c.id" :to="{path:'/product/list',query:{categoryId:c.id}}" @click="closeMenus()">{{c.name}}<el-icon><ArrowRight /></el-icon></router-link><router-link class="all-category-link" :to="{path:'/product/list',query:{categoryId:openCategory}}" @click="closeMenus()">查看整个分类<el-icon><ArrowRight /></el-icon></router-link></div>
        <div v-if="menuProducts.length" class="category-panel-products"><router-link v-for="p in menuProducts" :key="p.id" :to="`/product/${p.id}`" @click="closeMenus()"><ProductImage :src="p.mainImage" :alt="p.name" /><strong>{{p.name}}</strong><span>¥ {{price(p.price)}}<small v-if="Number(p.maxPrice)>Number(p.price)"> 起</small></span></router-link></div>
      </div>
      <div v-if="accountOpen && customer && !compact" id="store-account-panel" class="store-account-panel" role="region" aria-label="账号菜单">
        <div class="account-popover-identity"><AccountAvatar :src="user.avatar" :name="user.username" /><div><strong>{{user.username}}</strong><small>我的账号</small></div></div>
        <router-link to="/user/profile" @click="closeMenus()"><el-icon><User /></el-icon>个人中心<el-icon class="trailing"><ArrowRight /></el-icon></router-link>
        <router-link to="/user/profile?tab=collection" @click="closeMenus()"><el-icon><Star /></el-icon>我的收藏</router-link>
        <router-link to="/user/profile?tab=address" @click="closeMenus()"><el-icon><Location /></el-icon>收货地址</router-link>
        <router-link to="/user/profile?tab=info" @click="closeMenus()"><el-icon><Setting /></el-icon>账号设置</router-link>
        <button :disabled="loggingOut" class="account-signout" @click="logout"><el-icon><SwitchButton /></el-icon>{{loggingOut?'正在退出…':'退出登录'}}</button>
      </div>
    </header>
    <div v-if="openCategory!==null || accountOpen" class="store-navigation-dismiss" aria-hidden="true" @click="closeMenus()"></div>
    <main class="store-main">
      <slot />
    </main>
    <footer class="store-footer">
      <div class="footer-top">
        <span class="footer-copyright">© {{ new Date().getFullYear() }} Qivo</span>

      </div>
    </footer>
  </div>
</template>
