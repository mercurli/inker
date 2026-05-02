<script setup lang="ts">
const props = defineProps<{
  concepts: string[]
  max?: number
  clickable?: boolean
}>()

const emit = defineEmits<{
  (e: 'select', concept: string): void
}>()

function visibleConcepts() {
  return props.concepts.slice(0, props.max ?? 3)
}
</script>

<template>
  <div v-if="concepts.length > 0" class="concept-chip-group">
    <template v-if="clickable">
      <button
        v-for="concept in visibleConcepts()"
        :key="concept"
        class="concept-chip concept-chip--button"
        type="button"
        @click="emit('select', concept)"
      >
        {{ concept }}
      </button>
    </template>
    <template v-else>
      <span v-for="concept in visibleConcepts()" :key="concept" class="concept-chip">
        {{ concept }}
      </span>
    </template>
    <span v-if="concepts.length > (max ?? 3)" class="concept-chip concept-chip--more">+{{ concepts.length - (max ?? 3) }}</span>
  </div>
  <span v-else>--</span>
</template>
