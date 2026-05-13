<script setup lang="ts">
import type { TrendTone } from '@/shared/types/common'

defineProps<{
  label: string
  value: string | number
  note?: string
  tone?: TrendTone
}>()
</script>

<template>
  <article class="metric-card" :class="tone ? `metric-card--${tone}` : ''">
    <span class="metric-card-label">{{ label }}</span>
    <strong class="metric-card-value">{{ value }}</strong>
    <span v-if="note" class="metric-card-note">{{ note }}</span>
  </article>
</template>
