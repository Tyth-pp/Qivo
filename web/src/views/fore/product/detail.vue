<script setup lang="ts">
import ProductImage from '@/components/ProductImage.vue';
import PageState from '@/components/PageState.vue';
import { useLoad } from '@/composables/useLoad';
import { price } from '@/utils/display';
import { parseSelections } from '@/utils/sku';
const { loading, error, run } = useLoad();
const commentError = ref(false), commentLoading = ref(false);
import { ref, computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { getProductDetail } from '@/api/product';
import { ShoppingBag, Star, StarFilled, Check, InfoFilled, ChatDotRound } from '@element-plus/icons-vue';
import { useCollectionStore } from '@/store/collection';
import { getIndexData } from '@/api/index';
import { addToCart } from '@/api/cart';
import { getProductComments } from '@/api/comment';
import { useCartStore } from '@/store/cart';
import { useUserStore } from '@/store/user';
const route = useRoute();
const router = useRouter();
const cartStore = useCartStore();
const userStore = useUserStore();
const skus = ref<any[]>([]);
const definitions = ref<any[]>([]);
const selection = ref<Record<string, string>>({});
const selectedSku = computed(() => skus.value.find(s => {
    const choices = parseSelections(s.specValues);
    return choices.length === definitions.value.length && choices.every((c: any) => selection.value[c.specId] === c.valueId);
}));
const busy = ref(false);
const canBuy = computed(() => !!selectedSku.value && selectedSku.value.status !== 'DISABLED' && product.value.status !== 0 && selectedSku.value.stock >= quantity.value && Number.isInteger(quantity.value) && quantity.value >= 1 && quantity.value <= 999);
watch(selectedSku, () => {
    quantity.value = 1;
});
const product = ref<any>({});
const collections = useCollectionStore();
const collected = computed(() => collections.ids.includes(Number(product.value.id)));
const categoryLabel = ref('');
function optionAvailable(specId: string, valueId: string) {
    return skus.value.some(s => s.status !== 'DISABLED' && parseSelections(s.specValues).some(c => c.specId === specId && c.valueId === valueId));
}
function selectedValue(d: any) { return d.values.find((v: any) => v.id === selection.value[d.id])?.name; }
let commentSequence = 0;
const quantity = ref(1);
const comments = ref<any[]>([]);
const commentTotal = ref(0);
async function loadComments() {
    const current = ++commentSequence;
    const id = Number(route.params.id);
    commentLoading.value = true;
    commentError.value = false;
    try {
        const c: any = await getProductComments(id, { page: 1, pageSize: 10 });
        if (current !== commentSequence || id !== Number(route.params.id)) return;
        comments.value = c.data?.records || [];
        commentTotal.value = c.data?.total || 0;
    }
    catch {
        if (current === commentSequence && id === Number(route.params.id)) commentError.value = true;
    }
    finally {
        if (current === commentSequence) commentLoading.value = false;
    }
}
function load() {
    selection.value = {};
    commentSequence++;
    comments.value = [];
    commentTotal.value = 0;
    categoryLabel.value = '';
    return run(() => getProductDetail(Number(route.params.id)), (res: any) => {
        product.value = res.data?.product || {};
        skus.value = res.data?.skus || [];
        definitions.value = res.data?.specDefinitions || [];
        const categoryId = product.value.categoryId;
        getIndexData().then((r: any) => {
            if (product.value.categoryId === categoryId) categoryLabel.value = r.data?.categories?.find((c: any) => c.id === categoryId)?.name || '';
        }).catch(() => { /* 分类名称不阻断购买 */ });
        loadComments();
    });
}
watch(() => route.params.id, load, { immediate: true });
async function handleCollect() {
    if (!userStore.token) {
        router.push({ path: '/login', query: { redirect: route.fullPath } });
        return;
    }
    try {
        const result = await collections.toggle(Number(product.value.id));
        if (result !== undefined) ElMessage.success(result ? '已收藏' : '已取消收藏');
    } catch { /* 请求层提示；失败保留收藏状态 */ }
}
async function handleAddCart() {
    if (!userStore.token) {
        router.push({ path: '/login', query: { redirect: route.fullPath } });
        return;
    }
    if (!canBuy.value || busy.value) {
        ElMessage.warning('请选择有货的规格和有效数量');
        return;
    }
    busy.value = true;
    try {
        await addToCart(selectedSku.value.id, quantity.value);
        cartStore.addCount(quantity.value);
        ElMessage.success('已加入购物车');
    }
    finally {
        busy.value = false;
    }
}
function handleBuyNow() {
    if (!userStore.token) {
        router.push({ path: '/login', query: { redirect: route.fullPath } });
        return;
    }
    if (!canBuy.value) {
        ElMessage.warning('请选择有货的规格');
        return;
    }
    router.push({ path: '/order/confirm', query: { mode: 'buyNow', productId: product.value.id, skuId: selectedSku.value.id, quantity: quantity.value } });
}
</script>

<template>
  <div class="product-detail-page">
  <div class="detail-breadcrumb">
    <nav class="store-crumb" aria-label="当前位置"><router-link to="/">首页</router-link><span>/</span><router-link to="/product/list">全部商品</router-link><span>/</span><strong>{{product.name||'商品详情'}}</strong></nav>
  </div>
  <PageState :loading="loading" :error="error" @retry="load">
    <div class="detail-layout">
      <ProductImage class="detail-image" :src="selectedSku?.image || product.mainImage" :alt="product.name" zoom />
      <section class="detail-info">
        <span v-if="categoryLabel" class="detail-category">{{categoryLabel}}</span>
        <h1>{{product.name}}</h1>
        <p class="detail-description">{{product.description}}</p>
        <div class="detail-price">
          <strong class="price"><span class="currency">¥</span>{{price(selectedSku?.price ?? product.price)}}</strong>
          <del v-if="selectedSku?.originalPrice">¥{{price(selectedSku.originalPrice)}}</del>
        </div>
        <div v-for="d in definitions" :key="d.id" class="spec-choice">
          <h3>{{d.name}}<span v-if="selectedValue(d)" class="selected-spec">已选 {{selectedValue(d)}}</span></h3>
          <el-radio-group v-model="selection[d.id]">
            <el-radio-button v-for="v in d.values" :key="v.id" :value="v.id" :disabled="!optionAvailable(d.id,v.id)">{{v.name}}<el-icon v-if="selection[d.id]===v.id" class="spec-check"><Check /></el-icon></el-radio-button>
          </el-radio-group>
        </div>
        <p v-if="definitions.length && !selectedSku" class="soft-note">请选择完整规格，部分组合可能未提供。</p>
        <p v-if="selectedSku?.stock===0" class="soft-note">该规格暂时缺货，请选择其他规格。</p>
        <div class="quantity-row">
          <span>数量</span>
          <el-input-number v-model="quantity" :min="1" :max="Math.max(1,Math.min(selectedSku?.stock||1,999))" />
          <span class="muted">{{selectedSku ? '现货' : '可售库存'}} {{selectedSku?.stock ?? product.totalStock ?? 0}} 件</span>
        </div>
        <div class="inline-actions detail-actions">
          <el-button :loading="busy" :disabled="!canBuy" @click="handleAddCart"><el-icon><ShoppingBag /></el-icon>加入购物车</el-button>
          <el-button type="primary" :disabled="!canBuy" @click="handleBuyNow">立即购买</el-button>
          <el-button class="detail-collect" :aria-label="collected ? '取消收藏' : '收藏商品'" :aria-pressed="collected" :disabled="collections.pending.includes(Number(product.id))" @click="handleCollect"><el-icon><StarFilled v-if="collected" /><Star v-else /></el-icon></el-button>
        </div>
        <p class="muted detail-footnote"><el-icon><InfoFilled /></el-icon>下单时将再次核对商品价格与库存。</p>
      </section>
    </div>
    <section class="panel product-reviews" style="margin-top:35px">
      <div class="section-heading">
        <h2>商品评价</h2>
        <span class="muted">{{commentTotal}} 条</span>
      </div>
      <PageState
        :loading="commentLoading"
        :error="commentError"
        title="还没有评价"
        @retry="loadComments"
      >
        <p v-if="!comments.length" class="review-empty"><el-icon><ChatDotRound /></el-icon>还没有评价，期待你的使用分享。</p>
        <article class="review-item" v-for="c in comments" :key="c.id">
          <div>
            <b>{{c.username||'匿名用户'}}</b>
            <small>{{c.createTime}}</small>
          </div>
          <section>
            <el-rate :model-value="c.rating" disabled />
            <p>{{c.content||'用户未填写评价内容'}}</p>
            <p class="soft-note" v-if="c.appendContent">追评：{{c.appendContent}}</p>
          </section>
        </article>
      </PageState>
    </section>
  </PageState>
  </div>
</template>

<style scoped>
.detail-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 65px;
}

.detail-image {
  width: 100%;
  max-height: 540px;
}

.detail-info {
  padding: 15px 10px;
}

.detail-info h1 {
  font-size: 22px;
  line-height: 1.6;
  margin: 10px 0 15px;
}

.detail-description {
  color: var(--muted);
  font-size: 13px;
  line-height: 1.9;
}

.detail-price {
  margin: 24px 0 12px;
  display: flex;
  align-items: baseline;
  gap: 15px;
}

.detail-price .price {
  font-size: 28px;
}

.detail-price del {
  color: #a4ad9b;
  font-size: 13px;
}

.detail-stock {
  font-size: 12px;
  color: var(--muted);
  border-bottom: 1px solid var(--line);
  padding-bottom: 22px;
  margin-bottom: 24px;
}

.detail-stock span {
  margin-left: 35px;
}

.spec-choice {
  margin: 20px 0;
}

.spec-choice h3 {
  font-size: 12px;
  color: var(--muted);
  margin-bottom: 10px;
}

.quantity-row {
  display: flex;
  gap: 20px;
  align-items: center;
  margin: 23px 0;
}

.review-item {
  display: grid;
  grid-template-columns: 185px 1fr;
  gap: 20px;
  border-top: 1px solid var(--line);
  padding: 20px 0;
}

.review-item b {
  font-weight: 500;
  font-size: 13px;
}

.review-item small {
  display: block;
  margin-top: 7px;
  font-size: 11px;
}

.review-item p {
  font-size: 13px;
  margin-top: 10px;
}
</style>
