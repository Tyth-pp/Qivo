<script setup lang="ts">
import { orderStatus } from '@/utils/display';
defineProps<{
    order: {
        payStatus?: number;
        shipStatus?: number;
    };
}>();
</script>

<template>
  <span
    class="status-tag"
    :class="{ amber: order.payStatus === 0, gray: [2, 3].includes(order.payStatus ?? -1) || order.shipStatus === 2 }"
  >{{ orderStatus(order) }}</span>
</template>
