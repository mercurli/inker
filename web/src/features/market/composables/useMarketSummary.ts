import { useDashboardStore } from '@/shared/state/dashboardStore'

export function useMarketSummary() {
  const store = useDashboardStore()

  return {
    marketSummary: store.marketSummary,
    watchlistCount: store.watchlistCount,
    strongestStockLabel: store.strongestStockLabel,
    priceChangeDistribution: store.priceChangeDistribution,
    topFiveDayRisingIndustries: store.topFiveDayRisingIndustries,
    topFiveDayRisingConcepts: store.topFiveDayRisingConcepts,
    fetchMarketSummary: store.fetchMarketSummary
  }
}
