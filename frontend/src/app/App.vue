<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import AppSidebar from '@/shared/components/layout/AppSidebar.vue'
import PageHeader from '@/shared/components/layout/PageHeader.vue'
import ErrorAlert from '@/shared/components/feedback/ErrorAlert.vue'
import { useDashboardStore } from '@/shared/state/dashboardStore'

const route = useRoute()
const router = useRouter()
const store = useDashboardStore()

const navItems = [
  { key: 'market', label: '市场', to: '/market' },
  { key: 'stocks', label: '选股', to: '/stocks' },
  { key: 'watchlist', label: '自选', to: '/watchlist' }
]

const activeKey = computed(() => String(route.meta.navKey ?? 'market'))
const pageTitle = computed(() => String(route.meta.title ?? '研墨'))
const pageDescription = computed(() => String(route.meta.description ?? ''))

onMounted(() => {
  void store.init()
})

function navigate(to: string) {
  if (route.path === to) {
    return
  }

  void router.push(to)
}
</script>

<template>
  <div class="app-shell">
    <div class="dashboard-frame">
      <AppSidebar :items="navItems" :active-key="activeKey" @navigate="navigate" />

      <section class="app-main">
        <PageHeader
          :title="pageTitle"
          :subtitle="pageDescription"
          :total="store.marketSummary.value.total"
          :watchlist-count="store.watchlistCount.value"
          :importing="store.importing.value"
          @sync="store.importStocks"
        />

        <div v-if="store.importMessage.value" class="banner banner--success">{{ store.importMessage.value }}</div>
        <ErrorAlert v-if="store.error.value" :message="store.error.value" />

        <RouterView />
      </section>
    </div>
  </div>
</template>
