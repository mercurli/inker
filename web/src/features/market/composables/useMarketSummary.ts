import { useDashboardStore } from '@/shared/state/dashboardStore'

export function useMarketSummary() {
  const store = useDashboardStore()

  return {
    marketSummary: store.marketSummary,
    watchlistCount: store.watchlistCount,
    strongestStockLabel: store.strongestStockLabel,
    priceChangeDistribution: store.priceChangeDistribution,
    fetchMarketSummary: store.fetchMarketSummary
  }
}
