<script setup lang="ts">
import ProductImage from '@/components/ProductImage.vue';
import PageState from '@/components/PageState.vue';
import { useLoad } from '@/composables/useLoad';
const { loading, error, run } = useLoad();
import { ref, computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { getCheckedItems } from '@/api/cart';
import { createBuyNowOrder, createOrder, pendingOrder, retryPendingOrder } from '@/api/order';
import { getProductDetail } from '@/api/product';
import { getAddressList } from '@/api/user';
import { parseSelections, specText } from '@/utils/sku';
const route = useRoute();
const router = useRouter();
const items = ref<any[]>([]);
const addresses = ref<any[]>([]);
const selectedAddr = ref<number>(0);
const remark = ref('');
const submitting = ref(false);
const stale = ref(false);
const pending = ref(pendingOrder());
async function recoverOrder() {
    if (submitting.value)
        return;
    submitting.value = true;
    try {
        const res: any = await retryPendingOrder();
        ElMessage.success(`订单已确认：${res.data.orderNo}`);
        router.push('/order/list');
    }
    catch {
        pending.value = pendingOrder();
    }
    finally {
        submitting.value = false;
    }
}
const toCents = (value: number) => Math.round((Number(value) || 0) * 100);
const formatPrice = (value: number) => (toCents(value) / 100).toFixed(2);
const itemSubtotal = (item: any) => toCents(item.productPrice) * item.quantity;
const formatSubtotal = (item: any) => (itemSubtotal(item) / 100).toFixed(2);
const totalPrice = computed(() => (items.value.reduce((s, i) => s + itemSubtotal(i), 0) / 100).toFixed(2));
async function readCheckout() {
    const mode = route.query.mode;
    const productId = Number(route.query.productId);
    const quantity = Number(route.query.quantity || 1);
    const skuId = Number(route.query.skuId);
    const addrRes: any = await getAddressList();
    const nextAddresses = addrRes.data || [];
    let nextItems: any[] = [];
    if (mode === 'buyNow') {
        const res: any = await getProductDetail(productId);
        const product = res.data?.product || {};
        const sku = (res.data?.skus || []).find((s: any) => s.id === skuId);
        if (!sku || !Number.isInteger(quantity) || quantity < 1 || quantity > Math.min(999, sku.stock)) {
            ElMessage.warning('规格已失效或库存不足，请重新选择');
        }
        else {
            nextItems = [
                {
                    id: product.id, productId: product.id, productName: product.name,
                    skuId: sku.id, specText: specText(res.data?.specDefinitions || [], parseSelections(sku.specValues)),
                    productImage: sku.image || product.mainImage, productPrice: sku.price, quantity
                }
            ];
        }
    }
    else {
        const res: any = await getCheckedItems();
        nextItems = res.data || [];
    }
    return { addresses: nextAddresses, items: nextItems };
}
function load() {
    return run(readCheckout, snapshot => {
        addresses.value = snapshot.addresses;
        items.value = snapshot.items;
        selectedAddr.value = snapshot.addresses.find((a: any) => a.isDefault)?.id || snapshot.addresses[0]?.id || 0;
    });
}
watch(() => route.fullPath, () => {
    stale.value = false;
    load();
}, { immediate: true });
async function handleSubmit() {
    if (!selectedAddr.value) {
        ElMessage.warning('请选择收货地址');
        return;
    }
    if (!items.value.length) {
        ElMessage.warning('请选择商品');
        return;
    }
    if (submitting.value)
        return;
    if (items.value.some(i => i.unavailableReason)) {
        ElMessage.warning('商品状态或库存已变化，请返回购物车调整');
        return;
    }
    submitting.value = true;
    try {
        const res: any = route.query.mode === 'buyNow'
            ? await createBuyNowOrder(Number(route.query.skuId), Number(route.query.quantity || 1), selectedAddr.value, totalPrice.value, remark.value || undefined)
            : await createOrder(selectedAddr.value, items.value.map(i => i.id), totalPrice.value, remark.value || undefined);
        ElMessage.success(`下单成功，订单号：${res.data.orderNo}`);
        router.push('/order/list');
    }
    catch (error: any) {
        if (error.response?.status === 409) {
            stale.value = true;
            ElMessage.warning('商品价格或库存已变化，请刷新结算信息后重新确认');
        }
    }
    finally {
        pending.value = pendingOrder();
        submitting.value = false;
    }
}
</script>

<template>
  <nav class="store-crumb" aria-label="当前位置"><router-link to="/cart">购物车</router-link><span>/</span><strong>确认订单</strong></nav>
  <div class="page-heading">
    <h1>确认订单</h1>
  </div>
  <el-alert v-if="pending" title="上一次下单结果尚未确认，请先确认结果后继续。" type="warning" :closable="false">
    <el-button :loading="submitting" @click="recoverOrder">重试确认上次下单</el-button>
  </el-alert>
  <el-alert v-if="stale" type="warning" :closable="false" title="商品价格或库存已变化，请刷新结算信息后重新确认。">
    <el-button @click="router.go(0)">刷新结算信息</el-button>
  </el-alert>
  <PageState :loading="loading" :error="error" @retry="load">
    <div class="checkout-layout">
      <div>
        <section class="panel">
          <div class="section-heading">
            <h2>收货地址</h2>
            <router-link to="/user/profile?tab=address" class="text-link">管理地址 →</router-link>
          </div>
          <el-radio-group v-model="selectedAddr" class="address-choices">
            <label v-for="a in addresses" :key="a.id" class="address-choice" :class="{selected:selectedAddr===a.id}">
              <el-radio :value="a.id">
                <strong>{{a.receiverName}}</strong>
                <span>{{a.receiverPhone}}</span>
                <span v-if="a.isDefault" class="status-tag" style="margin-left:12px">默认</span>
              </el-radio>
              <p>{{a.province}}{{a.city}}{{a.district}}{{a.detail}}</p>
            </label>
          </el-radio-group>
          <PageState :empty="!addresses.length" title="还没有收货地址">
            <template #action>
              <el-button @click="router.push('/user/profile?tab=address')">添加收货地址</el-button>
            </template>
          </PageState>
        </section>
        <section class="panel">
          <h2>商品清单</h2>
          <div v-for="i in items" :key="i.id" class="checkout-item">
            <ProductImage :src="i.productImage" :alt="i.productName" />
            <div>
              <h3>{{i.productName}}</h3>
              <small>{{i.specText}} × {{i.quantity}}</small>
            </div>
            <strong>¥{{formatSubtotal(i)}}</strong>
          </div>
          <p v-if="!items.length" class="soft-note">没有可结算商品，请返回购物车或重新选择规格。</p>
        </section>
        <section class="panel">
          <h2 style="margin-bottom:15px">订单备注</h2>
          <el-input v-model="remark" type="textarea" :rows="2" maxlength="200" placeholder="选填，例如包装说明" />
        </section>
      </div>
      <aside class="panel checkout-summary">
        <h2>订单摘要</h2>
        <div>
          <span>商品合计</span>
          <strong>¥{{totalPrice}}</strong>
        </div>
        <div class="summary-total">
          <span>应付金额</span>
          <strong class="price">¥{{totalPrice}}</strong>
        </div>
        <el-button
          type="primary"
          size="large"
          style="width:100%"
          :disabled="loading||stale||!!pending||!items.length||!selectedAddr"
          :loading="submitting"
          @click="handleSubmit"
        >提交订单</el-button>
        <p>提交前将再次核对价格与库存。当前付款为模拟操作。</p>
      </aside>
    </div>
  </PageState>
</template>

<style scoped>
.checkout-layout {
  display: grid;
  grid-template-columns: minmax(0,1fr) 310px;
  gap: 25px;
  align-items: start;
}

.address-choices {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 12px;
  width: 100%;
}

.address-choice {
  padding: 12px 17px;
  border: 1px solid var(--line);
  border-radius: 6px;
  cursor: pointer;
}

.address-choice.selected {
  border-color: #81a771;
  background: #f6f9f1;
}

.address-choice strong {
  font-weight: 500;
}

.address-choice p {
  font-size: 12px;
  color: var(--muted);
  padding-left: 24px;
}

.checkout-item {
  display: flex;
  gap: 16px;
  align-items: center;
  padding: 20px 0;
  border-bottom: 1px solid var(--line);
}

.checkout-item:last-child {
  border: 0;
}

.checkout-item .product-image {
  width: 64px;
}

.checkout-item>div {
  flex: 1;
}

.checkout-item .product-image {
  flex: none;
}

.checkout-item small {
  display: block;
  margin-top: 6px;
}

.checkout-item strong {
  font-size: 14px;
  font-weight: 500;
}

.checkout-summary {
  position: sticky;
  top: 112px;
}

.checkout-summary>div {
  display: flex;
  justify-content: space-between;
  margin: 25px 0;
  font-size: 13px;
}

.checkout-summary .summary-total {
  border-top: 1px solid var(--line);
  padding-top: 22px;
}

.checkout-summary p {
  margin-top: 17px;
  font-size: 11px;
  color: var(--muted);
  line-height: 1.9;
}
</style>
