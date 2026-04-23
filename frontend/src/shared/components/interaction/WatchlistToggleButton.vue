<script setup lang="ts">
defineProps<{
  active: boolean
}>()

const emit = defineEmits<{
  (e: 'toggle'): void
}>()
</script>

<template>
  <button
    class="btn watchlist-toggle"
    :class="{ 'watchlist-toggle--active': active }"
    type="button"
    :aria-label="active ? '移除自选' : '加入自选'"
    @click="emit('toggle')"
  >
    <span v-if="active" aria-hidden="true">&#10084;</span>
    <span v-else aria-hidden="true">&#9825;</span>
  </button>
</template>
