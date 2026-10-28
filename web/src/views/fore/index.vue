<script setup lang="ts">
import { computed, ref, onMounted } from 'vue';
import { ArrowRight } from '@element-plus/icons-vue';
import { getIndexData } from '@/api/index';
import { useLoad } from '@/composables/useLoad';
import { price } from '@/utils/display';
import ProductCard from '@/components/ProductCard.vue';
import ProductImage from '@/components/ProductImage.vue';
import PageState from '@/components/PageState.vue';
import sceneImage from '@/assets/home-desk-scene.webp';
const data = ref<any>({}), sceneFailed = ref(false);
const { loading, error, run } = useLoad();
const available = (p: any) => p.status !== 0 && Number(p.totalStock ?? p.stock) > 0;
const categories = computed<any[]>(() => data.value.categories || []);
const candidates = computed<any[]>(() => {
    const seen = new Set<number>();
    return [...(data.value.hotProducts || []), ...(data.value.newProducts || [])].filter(p => {
        if (!available(p) || seen.has(p.id)) return false;
        seen.add(p.id); return true;
    });
});
const featuredProducts = computed(() => {
    const picked: any[] = [], seen = new Set<number>();
    // 先覆盖不同分类，再按接口顺序补齐；不编造精选标识或销量。
    for (const p of candidates.value) {
        if (!seen.has(p.categoryId)) { picked.push(p); seen.add(p.categoryId); }
        if (picked.length === 4) break;
    }
    return [...picked, ...candidates.value.filter(p => !picked.some(item => item.id === p.id))].slice(0, 4);
});
const relatedProducts = computed(() => {
    const others = candidates.value.filter(p => !featuredProducts.value.some(featured => featured.id === p.id));
    return [...others, ...featuredProducts.value].slice(0, 3);
});
const newProducts = computed<any[]>(() => (data.value.newProducts || []).filter(available).slice(0, 4));
const categoryName = (id: number) => categories.value.find(c => c.id === id)?.name;
function load() { return run(() => getIndexData(), (r: any) => { data.value = r.data || {}; }); }
onMounted(load);
</script>

<template>
  <div class="home-page">
    <PageState :loading="loading" :error="error" @retry="load">
      <section class="daily-scene" aria-label="桌面日常">
        <div class="section-heading"><h1>桌面日常</h1></div>
        <div class="scene-layout" :class="{ 'scene-only': !relatedProducts.length }">
          <div class="scene-photo">
            <img v-if="!sceneFailed" :src="sceneImage" alt="明亮桌面上的蓝白键盘、电脑、保温杯和日常文具" width="1672" height="941" fetchpriority="high" @error="sceneFailed=true" />
            <span v-else class="scene-fallback">桌面日常</span>
          </div>
          <aside v-if="relatedProducts.length" class="scene-products">
            <h2>相关好物</h2>
            <router-link v-for="p in relatedProducts" :key="p.id" :to="`/product/${p.id}`">
              <ProductImage :src="p.mainImage" :alt="p.name" />
              <span class="scene-product-copy"><strong>{{p.name}}</strong><span class="small-price">¥ {{price(p.price)}}<small v-if="Number(p.maxPrice)>Number(p.price)">起</small></span></span>
              <el-icon><ArrowRight /></el-icon>
            </router-link>
          </aside>
        </div>
      </section>
      <section class="home-section curated-section">
        <div class="section-heading"><h2>精选好物</h2><router-link class="text-link" to="/product/list">查看全部<el-icon><ArrowRight /></el-icon></router-link></div>
        <PageState :empty="!featuredProducts.length" title="商品正在准备中" description="稍后再来看看。">
          <div class="product-grid"><ProductCard v-for="p in featuredProducts" :key="p.id" :product="p" :category-name="categoryName(p.categoryId)" /></div>
        </PageState>
      </section>
      <section class="home-section new-arrivals">
        <div class="section-heading"><h2>新近上架</h2><router-link to="/product/list" class="text-link">浏览商品<el-icon><ArrowRight /></el-icon></router-link></div>
        <PageState :empty="!newProducts.length" title="暂时没有新品" description="可以先看看其他商品。">
          <div class="arrival-list">
            <router-link v-for="p in newProducts" :key="p.id" :to="`/product/${p.id}`">
              <ProductImage :src="p.mainImage" :alt="p.name" />
              <span><strong>{{p.name}}</strong><span class="small-price">¥ {{price(p.price)}}<small v-if="Number(p.maxPrice)>Number(p.price)">起</small></span></span>
            </router-link>
          </div>
        </PageState>
      </section>
    </PageState>
  </div>
</template>

<style scoped>
/* 独立首页探索：白色基底、日常场景、轻量商品索引，保持现有蓝色交互。 */
.daily-scene { margin-bottom: 38px; }
.daily-scene .section-heading { margin-bottom: 18px; }
.daily-scene h1 { font-size: 18px; font-weight: 600; }
.scene-layout { display: grid; grid-template-columns: minmax(0, 1fr) 286px; gap: 28px; align-items: stretch; }
.scene-photo { height: 400px; overflow: hidden; border-radius: 8px; background: #f2f3f4; }
.scene-photo img { display: block; width: 100%; height: 100%; object-fit: cover; object-position: center top; }
.scene-products { display: flex; flex-direction: column; min-width: 0; padding: 10px 0 0; }
.scene-products h2 { font-size: 14px; font-weight: 500; color: #6b7483; margin-bottom: 12px; }
.scene-products > a { display: grid; grid-template-columns: 84px minmax(0, 1fr) 16px; gap: 15px; align-items: center; padding: 15px 0; border-bottom: 1px solid #eceef2; }
.scene-products > a:last-child { border-bottom: 0; padding-bottom: 0; }
.scene-products > a .product-image { width: 84px; height: 84px; display: block; border-radius: 5px; object-fit: scale-down; background: #f5f6f7; }
.scene-product-copy { min-width: 0; }
.scene-products strong { display: block; font-size: 13px; font-weight: 500; line-height: 1.7; margin-bottom: 12px; }
.small-price { display: block; font-size: 14px; font-variant-numeric: tabular-nums; color: #26303e; }
.small-price small { font-size: 11px; font-weight: 400; color: #7e8794; margin-left: 4px; }
.scene-products > a > .el-icon { width: 15px; height: 15px; color: #8b94a1; transition: transform .18s; }
.scene-products > a:hover strong { color: var(--blue); }
.scene-products > a:hover > .el-icon { transform: translateX(3px); color: var(--blue); }
.home-section h2 { font-size: 18px; font-weight: 600; }
.curated-section { margin-bottom: 42px; }
.curated-section .section-heading { margin-bottom: 20px; }
.curated-section .card-copy { padding: 0 1px; }
.curated-section .card-meta { font-size: 12px; }
.curated-section .product-name { margin-top: 13px; }
.new-arrivals { border-top: 1px solid #e7eaf0; padding-top: 28px; }
.new-arrivals .section-heading { margin-bottom: 23px; }
.arrival-list { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 24px; }
.arrival-list > a { display: grid; grid-template-columns: 92px minmax(0, 1fr); align-items: center; gap: 15px; }
.arrival-list .product-image { width: 92px; height: 92px; object-fit: scale-down; border-radius: 5px; background: #f5f6f7; }
.arrival-list strong { display: block; font-size: 13px; font-weight: 500; margin-bottom: 13px; line-height: 1.7; }
.arrival-list a:hover strong { color: var(--blue); }
@media (max-width: 1320px) {
  .scene-layout { grid-template-columns: minmax(0, 1fr) 264px; gap: 24px; }
  .scene-photo { height: 370px; }
  .scene-products > a { grid-template-columns: 74px minmax(0, 1fr) 14px; padding: 13px 0; gap: 12px; }
  .scene-products > a .product-image { width: 74px; height: 74px; }
  .scene-products strong { font-size: 12px; }
  .arrival-list { gap: 20px; }
  .arrival-list > a { grid-template-columns: 76px minmax(0, 1fr); gap: 12px; }
  .arrival-list .product-image { width: 76px; height: 76px; }
}
@media (prefers-reduced-motion: reduce) {
  .scene-products > a > .el-icon, .category-rail > a { transition: none; }
  .scene-products > a:hover > .el-icon { transform: none; }
}


.scene-only { grid-template-columns: minmax(0,1fr); }
.scene-fallback { display: grid; place-items: center; height: 100%; color: #768293; }
.home-section { margin-bottom: 42px; }
.home-section:last-child { margin-bottom: 0; }
.home-page .section-heading { border: 0; padding: 0; }
.home-page .text-link { display: inline-flex; align-items: center; gap: 7px; }
.home-page .page-state { min-height: 160px; }
.category-rail .el-icon { font-size: 18px; color: #727e8f; }

</style>
