<script setup lang="ts">
defineProps<{
  loading?: boolean
  text?: string
}>()
</script>

<template>
  <div class="state-block">{{ loading ? (text ?? '加载中...') : (text ?? '--') }}</div>
</template>
