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
  { key: 'watchlist', label: '自选', to: '/watchlist' },
  { key: 'trading-calendar', label: '日历', to: '/trading-calendar' }
]

const activeKey = computed(() => String(route.meta.navKey ?? 'market'))
const pageTitle = computed(() => String(route.meta.title ?? '研墨'))
const pageDescription = computed(() => String(route.meta.description ?? ''))
const isStandaloneLayout = computed(() => route.meta.layout === 'standalone')

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
  <main v-if="isStandaloneLayout" class="standalone-page-shell">
    <RouterView />
  </main>

  <div v-else class="app-shell">
    <div class="dashboard-frame">
      <AppSidebar :items="navItems" :active-key="activeKey" @navigate="navigate" />

      <section class="app-main">
        <PageHeader
          :title="pageTitle"
          :subtitle="pageDescription"
          :total="store.marketSummary.value.total"
          :watchlist-count="store.watchlistCount.value"
          :importing="store.importing.value"
          :quote-syncing="store.quoteSyncing.value"
          @sync="store.importStocks"
          @sync-quotes="store.syncQuotesWithProgress"
        />

        <div v-if="store.importMessage.value" class="banner banner--success">{{ store.importMessage.value }}</div>
        <div v-if="store.quoteSyncProgress.value || store.quoteSyncMessage.value" class="quote-sync-panel">
          <div class="quote-sync-head">
            <span class="quote-sync-title">{{ store.quoteSyncMessage.value || store.quoteSyncProgress.value?.message }}</span>
            <strong class="quote-sync-percent">{{ store.quoteSyncProgress.value?.percent ?? 0 }}%</strong>
          </div>
          <div class="quote-sync-track" role="progressbar" :aria-valuenow="store.quoteSyncProgress.value?.percent ?? 0" aria-valuemin="0" aria-valuemax="100">
            <div class="quote-sync-fill" :style="{ width: `${store.quoteSyncProgress.value?.percent ?? 0}%` }"></div>
          </div>
          <div class="quote-sync-meta">
            <span>交易日 {{ store.quoteSyncProgress.value?.tradeDate ?? '确认中' }}</span>
            <span>抓取 {{ store.quoteSyncProgress.value?.fetched ?? 0 }}</span>
            <span>匹配 {{ store.quoteSyncProgress.value?.matched ?? 0 }}</span>
            <span>更新 {{ store.quoteSyncProgress.value?.updated ?? 0 }}</span>
            <span>未匹配 {{ store.quoteSyncProgress.value?.skippedMissing ?? 0 }}</span>
          </div>
        </div>
        <ErrorAlert v-if="store.error.value" :message="store.error.value" />

        <RouterView />
      </section>
    </div>
  </div>
</template>
