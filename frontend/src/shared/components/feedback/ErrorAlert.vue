<script setup lang="ts">
defineProps<{
  message: string
}>()
</script>

<template>
  <div class="state-block state-block--error">{{ message }}</div>
</template>
