<script setup lang="ts">
import { computed } from 'vue';
import type { CategoryNode } from '@/utils/category';

const props = defineProps<{ categories: CategoryNode[]; modelValue: number | null; counts?: Record<number, number>; total?: number }>();
const emit = defineEmits<{ change: [id: number | null] }>();
const visible = computed(() => props.categories.filter(c => c.status !== 0));
const roots = computed(() => visible.value.filter(c => !c.parentId));
const selected = computed(() => visible.value.find(c => c.id === props.modelValue));
const rootId = computed(() => selected.value ? selected.value.parentId || selected.value.id : null);
const children = computed(() => visible.value.filter(c =>
  c.parentId && roots.value.some(root => root.id === c.parentId) && (!rootId.value || c.parentId === rootId.value))
  .sort((a, b) => roots.value.findIndex(root => root.id === a.parentId) - roots.value.findIndex(root => root.id === b.parentId)
    || Number(a.sortOrder || 0) - Number(b.sortOrder || 0)));
</script>

<template>
  <div class="catalog-category-filters">
    <div class="category-roots" role="group" aria-label="商品大类">
      <button type="button" :class="{ active: modelValue === null }" :aria-pressed="modelValue === null" @click="emit('change', null)">全部<span v-if="total !== undefined" class="category-number">{{total}}</span></button>
      <button v-for="category in roots" :key="category.id" type="button"
        :class="{ active: rootId === category.id }" :aria-pressed="rootId === category.id"
        @click="emit('change', category.id)">{{ category.name }}<span v-if="counts?.[category.id] !== undefined" class="category-number">{{counts[category.id]}}</span></button>
    </div>
    <div v-if="children.length" class="category-children" role="group" aria-label="商品子类">
      <span>分类</span>
      <button v-for="category in children" :key="category.id" type="button"
        :class="{ active: modelValue === category.id }" :aria-pressed="modelValue === category.id"
        @click="emit('change', category.id)">{{ category.name }}</button>
    </div>
  </div>
</template>

<style scoped>
.catalog-category-filters { display: grid; gap: 12px; }
.category-roots, .category-children { display: flex; flex-wrap: wrap; align-items: center; gap: 7px; }
button { border: 1px solid transparent; border-radius: 5px; background: #f1f5fa; color: #566782; padding: 7px 13px; font: inherit; font-size: 11px; line-height: 1.3; cursor: pointer; }
button:hover { border-color: #abc0f3; color: #164dff; }
button:focus-visible { outline: 2px solid #164dff; outline-offset: 2px; }
.category-roots button.active { background: #0f172a; color: #fff; }
.category-children { gap: 5px; }
.category-children span { font-size: 11px; color: #8b98ad; margin-right: 6px; }
.category-children button { background: transparent; padding: 5px 10px; color: #69778d; }
.category-children button.active { background: #edf3ff; color: #164dff; }
</style>
