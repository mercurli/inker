import type { Router } from 'vue-router'

export function resolveStockDetailHref(router: Router, stockId: number) {
  return router.resolve({ name: 'stock-detail', params: { id: String(stockId) } }).href
}

export function openStockDetailPage(router: Router, stockId: number) {
  window.open(resolveStockDetailHref(router, stockId), '_blank', 'noopener,noreferrer')
}
