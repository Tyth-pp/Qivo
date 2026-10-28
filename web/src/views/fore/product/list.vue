<script setup lang="ts">
import { ref, watch, onMounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Sort } from '@element-plus/icons-vue';
import { getProductList } from '@/api/product';
import { getIndexData } from '@/api/index';
import { useLoad } from '@/composables/useLoad';
import PageState from '@/components/PageState.vue';
import ProductCard from '@/components/ProductCard.vue';
import CatalogCategoryFilters from '@/components/CatalogCategoryFilters.vue';
const route = useRoute(), router = useRouter();
const products = ref<any[]>([]), categories = ref<any[]>([]), total = ref(0);
const page = ref(1), sort = ref(''), counts = ref<Record<number, number>>({});
const allCount = ref<number>(), categoryError = ref(false);
const { loading, error, run } = useLoad();
const shownFrom = computed(() => total.value ? (page.value - 1) * 12 + 1 : 0);
function load() {
    return run(() => getProductList({ page: page.value, pageSize: 12,
        keyword: route.query.keyword || undefined, categoryId: route.query.categoryId || undefined,
        sort: sort.value || undefined
    }), (r: any) => {
        products.value = r.data?.records || [];
        total.value = r.data?.total || 0;
    });
}
function changeCategory(id: number | null) {
    router.push({ path: route.path, query: { ...route.query, categoryId: id || undefined, page: undefined } });
}
function changeSort() {
    router.push({ path: route.path, query: { ...route.query, sort: sort.value || undefined, page: undefined } });
}
function changePage(value: number) {
    router.push({ path: route.path, query: { ...route.query, page: value > 1 ? value : undefined } });
    window.scrollTo({ top: 0, behavior: 'instant' });
}
function reset() { router.push('/product/list'); }
async function loadCategories() {
    categoryError.value = false;
    try {
        const r: any = await getIndexData();
        categories.value = r.data?.categories || [];
        const roots = categories.value.filter(c => !c.parentId && c.status !== 0);
        // 仅查询总数，不抓取全量商品，也不以当前页推算分类数量。
        await Promise.allSettled([null, ...roots.map(c => c.id)].map(async id => {
            const result: any = await getProductList({ page: 1, pageSize: 1, categoryId: id || undefined });
            if (id === null) allCount.value = result.data.total;
            else counts.value[id] = result.data.total;
        }));
    } catch { categoryError.value = true; }
}
watch(() => route.fullPath, () => {
    const requested = Number(route.query.page || 1);
    page.value = Number.isSafeInteger(requested) && requested > 0 ? requested : 1;
    sort.value = ['price_asc', 'price_desc', 'sales'].includes(String(route.query.sort)) ? String(route.query.sort) : '';
    load();
}, { immediate: true });
onMounted(loadCategories);
</script>

<template>
  <div class="catalog-page">
    <nav class="store-crumb" aria-label="当前位置"><router-link to="/">首页</router-link><span>›</span><strong>全部商品</strong></nav>
    <div class="catalog-page-heading">
      <div class="inline-actions"><h1>{{route.query.keyword ? `搜索：${route.query.keyword}` : route.query.section==='new'?'新近上架':'全部商品'}}</h1><span class="catalog-result-count">{{total}} 件商品</span></div>
      <div class="catalog-sort"><el-icon><Sort /></el-icon>
        <el-select v-model="sort" :empty-values="[null, undefined]" aria-label="商品排序" @change="changeSort">
          <el-option label="默认排序" value="" /><el-option label="价格从低到高" value="price_asc" />
          <el-option label="价格从高到低" value="price_desc" /><el-option label="销量优先" value="sales" />
        </el-select>
      </div>
    </div>
    <div class="catalog-page-filters">
      <CatalogCategoryFilters :categories="categories" :model-value="Number(route.query.categoryId) || null"
        :counts="counts" :total="allCount" @change="changeCategory" />
      <button v-if="categoryError" class="text-link category-retry" type="button" @click="loadCategories">分类暂未加载，点击重试</button>
    </div>
    <PageState :loading="loading" :error="error" :empty="!products.length" title="没有找到相关商品" description="换个关键词或分类试试看。" @retry="load">
      <template #action><el-button @click="reset">查看全部商品</el-button></template>
      <div class="product-grid">
        <ProductCard v-for="p in products" :key="p.id" :product="p" :category-name="categories.find(c=>c.id===p.categoryId)?.name" />
      </div>
      <div class="catalog-page-pagination">
        <span>已显示 {{shownFrom}}–{{Math.min(page*12,total)}} 件，共 {{total}} 件</span>
        <el-pagination v-if="total>12" :current-page="page" :page-size="12" :total="total" layout="prev,pager,next" @current-change="changePage" />
      </div>
    </PageState>
  </div>
</template>
