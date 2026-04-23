<script setup lang="ts">
import type { TrendTone } from '@/shared/types/common'

defineProps<{
  value: string
  tone: TrendTone
}>()
</script>

<template>
  <span class="price-chip" :class="`price-chip--${tone}`">{{ value }}</span>
</template>
