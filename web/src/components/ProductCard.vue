<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Plus, ArrowRight, Star, StarFilled } from '@element-plus/icons-vue';
import ProductImage from './ProductImage.vue';
import { price } from '@/utils/display';
import { quickCartSkuId } from '@/utils/sku';
import { addToCart } from '@/api/cart';
import { useUserStore } from '@/store/user';
import { useCartStore } from '@/store/cart';
import { useCollectionStore } from '@/store/collection';
const props = defineProps<{ product: any; categoryName?: string }>();
const collections = useCollectionStore();
const saved = computed(() => collections.ids.includes(Number(props.product.id)));
const specSummary = computed(() => {
    if (props.product.specMode !== 'MULTI') return '标准款';
    try {
        const raw = props.product.specDefinitions || props.product.specDefinition || [];
        const definitions = typeof raw === 'string' ? JSON.parse(raw) : raw;
        if (definitions.length === 1 && definitions[0].values?.length)
            return definitions[0].values.length + ' 种' + definitions[0].name;
    } catch { /* 异常规格只显示入口，不猜测数量 */ }
    return '多规格可选';
});
async function toggleCollection() {
    if (!user.token || user.isAdmin) {
        router.push({ path: '/login', query: { redirect: route.fullPath } });
        return;
    }
    try {
        const result = await collections.toggle(Number(props.product.id));
        if (result !== undefined) ElMessage.success(result ? '已收藏' : '已取消收藏');
    } catch { /* 请求层提示，保留原收藏状态 */ }
}
const router = useRouter(), route = useRoute(), user = useUserStore(), cart = useCartStore(), busy = ref(false);
async function add(p: any) {
    const sku = quickCartSkuId(p);
    if (sku === null) {
        router.push(`/product/${p.id}`);
        return;
    }
    if (!user.token) {
        router.push({ path: '/login', query: { redirect: route.fullPath } });
        return;
    }
    if (busy.value)
        return;
    busy.value = true;
    try {
        await addToCart(sku, 1);
        cart.addCount(1);
        ElMessage.success('已加入购物车');
    }
    catch { /* 请求层提示错误 */
    }
    finally {
        busy.value = false;
    }
}
</script>

<template>
  <article class="product-card">
    <div class="product-card-media">
      <router-link :to="`/product/${product.id}`" :aria-label="`查看${product.name}`">
        <ProductImage :src="product.mainImage" :alt="product.name" />
      </router-link>
      <button type="button" class="product-save" :class="{ saved }" :aria-pressed="saved"
        :aria-label="saved ? '取消收藏' : '收藏商品'" :disabled="collections.pending.includes(Number(product.id))" @click="toggleCollection">
        <el-icon><StarFilled v-if="saved" /><Star v-else /></el-icon>
      </button>
    </div>
    <router-link :to="`/product/${product.id}`">
      <h3>{{ product.name }}</h3>
    </router-link>
    <p class="product-card-meta"><span v-if="categoryName">{{categoryName}}<span class="meta-separator">·</span></span>{{specSummary}}</p>
    <div class="product-card-bottom">
      <strong class="price">
        <span class="currency">¥</span>{{ price(product.price) }}
        <small v-if="Number(product.maxPrice) > Number(product.price)">起</small>
      </strong>
      <el-button
        :loading="busy"
        :disabled="product.status === 0 || Number(product.totalStock ?? product.stock) === 0"
        @click="add(product)"
      >
        <el-icon v-if="product.status !== 0 && Number(product.totalStock ?? product.stock) > 0">
          <ArrowRight v-if="quickCartSkuId(product) === null" />
          <Plus v-else />
        </el-icon>
        {{ product.status === 0 ? '已下架' : Number(product.totalStock ?? product.stock) === 0 ? '暂时缺货' : quickCartSkuId(product) === null ? '选择规格' : '加入购物车' }}
      </el-button>
    </div>
    <slot />
  </article>
</template>
