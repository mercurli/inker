<script setup lang="ts">
defineProps<{
  concepts: string[]
  max?: number
}>()
</script>

<template>
  <div v-if="concepts.length > 0" class="concept-chip-group">
    <span v-for="concept in concepts.slice(0, max ?? 3)" :key="concept" class="concept-chip">
      {{ concept }}
    </span>
    <span v-if="concepts.length > (max ?? 3)" class="concept-chip concept-chip--more">+{{ concepts.length - (max ?? 3) }}</span>
  </div>
  <span v-else>--</span>
</template>
