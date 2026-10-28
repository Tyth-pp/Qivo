<script setup lang="ts">
import ProductImage from '@/components/ProductImage.vue';
import PageState from '@/components/PageState.vue';
import { useLoad } from '@/composables/useLoad';
const { loading, error, run } = useLoad();
const busy = ref(false);
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getCartList, updateCartQuantity, checkCartItem, deleteCartItem } from '@/api/cart';
import { useCartStore } from '@/store/cart';
const router = useRouter();
const cartStore = useCartStore();
const items = ref<any[]>([]);
const checkedAll = ref(false);
function load() {
    return run(() => getCartList(), (res: any) => {
        items.value = res.data || [];
        checkedAll.value = items.value.length > 0 && items.value.every(i => i.checked === 1);
        cartStore.setCount(items.value.reduce((n, i) => n + i.quantity, 0));
    });
}
async function mutate(action: () => Promise<unknown>) {
    if (busy.value)
        return;
    busy.value = true;
    try {
        await action();
    }
    catch { /* 保留界面并允许重试 */
    }
    finally {
        busy.value = false;
    }
}
const checkedItems = computed(() => items.value.filter(i => i.checked));
const toCents = (value: number) => Math.round((Number(value) || 0) * 100);
const formatPrice = (value: number) => (toCents(value) / 100).toFixed(2);
const itemSubtotal = (item: any) => toCents(item.productPrice) * item.quantity;
const formatSubtotal = (item: any) => (itemSubtotal(item) / 100).toFixed(2);
const totalPrice = computed(() => (checkedItems.value.reduce((s, i) => s + itemSubtotal(i), 0) / 100).toFixed(2));
async function toggleAll() {
    const nextChecked = checkedAll.value ? 1 : 0;
    await Promise.all(items.value.map(i => checkCartItem(i.id, nextChecked)));
    load();
}
async function changeCheck(row: any, value: boolean) {
    await checkCartItem(row.id, value ? 1 : 0);
    load();
}
async function changeQty(row: any, delta: number) {
    const qty = row.quantity + delta;
    if (qty < 1 || qty > 999)
        return;
    await updateCartQuantity(row.id, qty);
    load();
}
async function removeItem(id: number) {
    await ElMessageBox.confirm('确定删除该商品吗？', '提示', { type: 'warning' });
    await deleteCartItem(id);
    ElMessage.success('已删除');
    load();
}
function goCheckout() {
    if (!checkedItems.value.length) {
        ElMessage.warning('请先勾选商品');
        return;
    }
    if (checkedItems.value.some(i => i.unavailableReason)) {
        ElMessage.warning('选中商品存在不可结算项，请调整数量或取消勾选');
        return;
    }
    router.push('/order/confirm');
}
onMounted(() => load());
</script>

<template>
  <nav class="store-crumb" aria-label="当前位置"><router-link to="/">首页</router-link><span>/</span><strong>购物车</strong></nav>
  <div class="page-heading">
    <div class="inline-actions"><h1>购物车</h1><span class="catalog-count">{{items.length}} 件商品</span></div>
    <router-link to="/product/list" class="text-link">继续选购 →</router-link>
  </div>
  <PageState
    :loading="loading"
    :error="error"
    :empty="!items.length"
    title="购物车还是空的"
    description="把喜欢的好物加入购物车，再一起结算。"
    @retry="load"
  >
    <template #action>
      <el-button type="primary" @click="router.push('/product/list')">去选购</el-button>
    </template>
    <section class="panel cart-panel">
      <div class="cart-columns"><el-checkbox v-model="checkedAll" :disabled="busy" aria-label="全选商品" @change="mutate(toggleAll)" /><span>商品信息</span><span>单价</span><span>数量</span><span>小计</span><span>操作</span></div>
      <article v-for="row in items" :key="row.id" class="cart-row">
        <el-checkbox
          :model-value="row.checked===1"
          :disabled="busy"
          :aria-label="`选择${row.productName}`"
          @change="(v:any)=>mutate(()=>changeCheck(row,v))"
         />
        <div class="cart-product"><ProductImage :src="row.productImage" :alt="row.productName" />
        <div>
          <router-link :to="`/product/${row.productId}`">
            <h3>{{row.productName}}</h3>
          </router-link>
          <small>{{row.specText}}</small>
          <p class="invalid" v-if="row.unavailableReason">{{row.unavailableReason}}</p>
        </div></div>
        <span>¥{{formatPrice(row.productPrice)}}</span>
        <div class="quantity-control">
          <el-button size="small" :disabled="busy||row.quantity<=1" @click="mutate(()=>changeQty(row,-1))">−</el-button>
          <span>{{row.quantity}}</span>
          <el-button size="small" :disabled="busy||row.quantity>=999" @click="mutate(()=>changeQty(row,1))">＋</el-button>
        </div>
        <strong>¥{{formatSubtotal(row)}}</strong>
        <el-button link class="danger-link" :disabled="busy" @click="mutate(()=>removeItem(row.id))">移除</el-button>
      </article>
    </section>
    <section class="panel cart-total">
      <span class="muted">已选 {{checkedItems.reduce((n,i)=>n+i.quantity,0)}} 件商品</span>
      <span class="muted">商品合计 <strong class="price">¥{{totalPrice}}</strong></span>
      <el-button type="primary" :disabled="busy||!checkedItems.length" @click="goCheckout">去结算</el-button>
    </section>
  </PageState>
</template>

<style scoped>
.cart-panel {
  padding: 0 24px;
}

.cart-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 0;
  border-bottom: 1px solid var(--line);
}

.cart-row {
  display: grid;
  grid-template-columns: 25px 75px minmax(0,1fr) 90px 135px 100px 45px;
  gap: 19px;
  align-items: center;
  padding: 24px 0;
  border-bottom: 1px solid var(--line);
  font-size: 13px;
}

.cart-row:last-child {
  border: 0;
}

.cart-row .product-image {
  width: 75px;
}

.cart-row small {
  display: block;
  margin-top: 7px;
}

.quantity-control {
  display: flex;
  align-items: center;
  gap: 12px;
}

.quantity-control .el-button {
  padding: 5px 8px;
}

.quantity-control .el-button+.el-button {
  margin: 0;
}

.invalid {
  font-size: 11px;
  color: #b0794a;
  margin-top: 7px;
}

.cart-total {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 24px;
  margin-top: 22px;
  position: sticky;
  bottom: 16px;
  box-shadow: 0 5px 25px #30472e08;
}
</style>
