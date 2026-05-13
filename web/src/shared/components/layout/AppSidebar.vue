<script setup lang="ts">
interface Item {
  key: string
  label: string
  to: string
}

defineProps<{
  items: Item[]
  activeKey: string
}>()

const emit = defineEmits<{
  (e: 'navigate', to: string): void
}>()
</script>

<template>
  <aside class="app-sidebar">
    <div class="sidebar-brand">
      <span class="brand-mark">研</span>
      <span class="brand-name">Inker</span>
    </div>

    <nav class="sidebar-nav">
      <button
        v-for="item in items"
        :key="item.key"
        class="nav-item"
        :class="{ 'nav-item--active': item.key === activeKey }"
        type="button"
        @click="emit('navigate', item.to)"
      >
        {{ item.label }}
      </button>
    </nav>
  </aside>
</template>
