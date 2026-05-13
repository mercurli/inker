import { useDashboardStore } from '@/shared/state/dashboardStore'

export function useStockDetail() {
  const store = useDashboardStore()

  return {
    selectedStock: store.selectedStock,
    detailLoading: store.detailLoading,
    detailError: store.detailError,
    dailyKLine: store.dailyKLine,
    kLineLoading: store.kLineLoading,
    kLineError: store.kLineError,
    isInWatchlist: store.isInWatchlist,
    toggleWatchlist: store.toggleWatchlist,
    formatPrice: store.formatPrice,
    formatPercent: store.formatPercent,
    formatDate: store.formatDate,
    changeClass: store.changeClass,
    loadDetailById: store.loadDetailById,
    updateStockConcepts: store.updateStockConcepts
  }
}
