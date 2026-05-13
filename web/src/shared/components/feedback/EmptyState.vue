<script setup lang="ts">
defineProps<{
  message: string
}>()
</script>

<template>
  <div class="state-block state-block--empty">{{ message }}</div>
</template>
