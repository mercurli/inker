<script setup lang="ts">
defineProps<{
  title: string
  subtitle: string
  total: number
  watchlistCount: number
  importing: boolean
}>()

const emit = defineEmits<{
  (e: 'sync'): void
}>()
</script>

<template>
  <header class="page-header">
    <div class="page-header-main">
      <h1 class="page-title">{{ title }}</h1>
      <p class="page-subtitle">{{ subtitle }}</p>
    </div>

    <div class="header-actions">
      <div class="header-metric">
        <span class="metric-label">覆盖标的</span>
        <strong class="metric-value">{{ total }}</strong>
      </div>
      <div class="header-metric">
        <span class="metric-label">我的自选</span>
        <strong class="metric-value">{{ watchlistCount }}</strong>
      </div>
      <button class="btn btn-primary" type="button" :disabled="importing" @click="emit('sync')">
        {{ importing ? '同步中...' : '同步A股数据' }}
      </button>
    </div>
  </header>
</template>
