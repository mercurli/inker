import { useDashboardStore } from '@/shared/state/dashboardStore'

export function useWatchlist() {
  const store = useDashboardStore()

  return {
    watchlist: store.watchlist,
    watchlistGroups: store.watchlistGroups,
    activeWatchlistGroup: store.activeWatchlistGroup,
    activeWatchlistGroupId: store.activeWatchlistGroupId,
    watchlistLoading: store.watchlistLoading,
    watchlistCount: store.watchlistCount,
    removeFromWatchlist: store.removeFromWatchlist,
    removeFromActiveGroup: store.removeFromActiveGroup,
    addToGroup: store.addToGroup,
    moveStockToGroup: store.moveStockToGroup,
    createWatchlistGroup: store.createWatchlistGroup,
    renameWatchlistGroup: store.renameWatchlistGroup,
    deleteWatchlistGroup: store.deleteWatchlistGroup,
    setActiveWatchlistGroup: store.setActiveWatchlistGroup,
    formatPrice: store.formatPrice,
    formatPercent: store.formatPercent,
    formatDate: store.formatDate,
    changeClass: store.changeClass
  }
}
