<template>
  <section class="filter-bar panel-card">
    <div class="filter-grid">
      <slot name="fields" />
    </div>
    <div class="filter-actions">
      <slot name="actions" />
    </div>
  </section>
</template>
