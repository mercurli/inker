<script setup lang="ts">
defineProps<{
  label: string
  variant: string
}>()
</script>

<template>
  <span class="board-tag" :class="variant">{{ label }}</span>
</template>
