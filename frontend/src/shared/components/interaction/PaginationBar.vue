<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  page: number
  totalPages: number
}>()

const emit = defineEmits<{
  (e: 'change', page: number): void
}>()

const pageItems = computed(() => {
  const count = Math.max(props.totalPages, 1)
  const current = Math.min(Math.max(props.page + 1, 1), count)
  const pages = new Set<number>()

  if (count <= 7) {
    for (let i = 1; i <= count; i += 1) {
      pages.add(i)
    }
  } else {
    pages.add(1)
    pages.add(count)

    if (current <= 4) {
      for (let i = 2; i <= 5; i += 1) {
        pages.add(i)
      }
    } else if (current >= count - 3) {
      for (let i = count - 4; i <= count - 1; i += 1) {
        pages.add(i)
      }
    } else {
      pages.add(current - 1)
      pages.add(current)
      pages.add(current + 1)
    }
  }

  const sorted = [...pages].filter((value) => value >= 1 && value <= count).sort((a, b) => a - b)
  const items: Array<number | 'ellipsis'> = []

  sorted.forEach((value, index) => {
    const prev = sorted[index - 1]

    if (prev !== undefined && value - prev > 1) {
      items.push('ellipsis')
    }

    items.push(value)
  })

  return items
})
</script>

<template>
  <footer class="pagination-bar panel-card">
    <button class="page-circle" :disabled="page === 0 || totalPages <= 1" type="button" @click="emit('change', page - 1)">‹</button>

    <template v-for="(item, index) in pageItems" :key="`${item}-${index}`">
      <span v-if="item === 'ellipsis'" class="page-ellipsis">...</span>
      <button
        v-else
        class="page-circle"
        :class="{ 'page-circle--active': page + 1 === item }"
        type="button"
        @click="emit('change', item - 1)"
      >
        {{ item }}
      </button>
    </template>

    <button class="page-circle" :disabled="page + 1 >= totalPages || totalPages <= 1" type="button" @click="emit('change', page + 1)">›</button>
  </footer>
</template>
