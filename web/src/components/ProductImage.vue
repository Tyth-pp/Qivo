<script setup lang="ts">
import { ref, watch } from 'vue';
import { Picture, FullScreen } from '@element-plus/icons-vue';
const props = defineProps<{
    src?: string | null;
    alt?: string;
    zoom?: boolean;
}>();
const failed = ref(false), visible = ref(false);
watch(() => props.src, () => {
    failed.value = false;
});
</script>

<template>
  <div class="product-image">
    <button
      v-if="src && !failed && zoom"
      class="image-zoom"
      :aria-label="`放大查看${alt || '商品图片'}`"
      @click="visible = true"
    >
      <img :src="src" :alt="alt || '商品图片'" @error="failed = true" />
      <span class="image-zoom-cue" aria-hidden="true"><el-icon><FullScreen /></el-icon></span>
    </button>
    <img v-else-if="src && !failed" :src="src" :alt="alt || '商品图片'" loading="lazy" @error="failed = true" />
    <div v-else class="image-placeholder" role="img" :aria-label="alt || '暂无图片'">
      <el-icon>
        <Picture />
      </el-icon>
      <small>{{ failed ? '图片暂不可用' : '暂无图片' }}</small>
    </div>
    <el-image-viewer v-if="visible && src && !failed" :url-list="[src]" @close="visible = false" />
  </div>
</template>

<style scoped>
.product-image {
  background: #f0f3ed;
  aspect-ratio: 1;
  overflow: hidden;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.product-image img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: scale-down;
}

.image-zoom-cue { position: absolute; right: 14px; bottom: 14px; display: grid; place-items: center; width: 30px; height: 30px; border-radius: 4px; background: #fff; color: #697381; }
.image-zoom {
  position: relative;
  width: 100%;
  height: 100%;
  padding: 0;
  border: 0;
  cursor: zoom-in;
  background: none;
}

.image-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #a1ada0;
  font-size: 26px;
  height: 100%;
  width: 100%;
}

.image-placeholder small {
  font-size: 11px;
}
</style>
