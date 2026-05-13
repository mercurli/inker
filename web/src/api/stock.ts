import http from '@/shared/api/http'

export interface Stock {
  id: number
  symbol: string
  name: string
  logo?: string | null
  latestPrice: number | null
  changePercent: number | null
  fiveDayChangePercent: number | null
  volume: number | null
  amount: number | null
  turnoverRate: number | null
  totalMarketValue: number | null
  circulatingMarketValue: number | null
  dynamicPeRatio: number | null
  market: string
  exchangeCode?: string
  industry?: string
  concepts: string[]
  primaryConcept?: string | null
  boardType?: string
  listDate?: string
}

export interface StockDailyCandle {
  tradeDate: string
  openPrice: number
  closePrice: number
  highPrice: number
  lowPrice: number
  volume: number
  amount: number
  changePercent: number
}

export interface StockDailyKLine {
  symbol: string
  name: string
  candles: StockDailyCandle[]
}

export interface WatchlistGroup {
  id: number
  name: string
  default: boolean
  sortOrder: number
  stockCount: number
  averageChangePercent: number | null
  industryCounts: Record<string, number>
  primaryConceptCounts: Record<string, number>
}

export interface WatchlistStock extends Stock {
  addedAt: string
}

export interface StockQueryParams {
  keyword?: string
  exchangeCode?: 'SSE' | 'SZSE'
  boardType?: string
  industry?: string
  concept?: string
  page?: number
  size?: number
  sortBy?: 'code' | 'name' | 'exchangeCode' | 'market' | 'industry' | 'listDate' | 'id' | 'latestPrice' | 'changePercent' | 'fiveDayChangePercent' | 'amount' | 'turnoverRate' | 'totalMarketValue' | 'dynamicPeRatio' | 'boardType'
  sortDirection?: 'ASC' | 'DESC'
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export interface ImportResult {
  fetched: number
  imported: number
  skippedSt: number
  skippedBeijingExchange: number
}

export interface QuoteSyncResult {
  source: string
  fetched: number
  matched: number
  updated: number
  skippedMissing: number
}

export interface QuoteSyncProgress {
  stage: string
  percent: number
  message: string
  tradeDate: string | null
  fetched: number
  matched: number
  updated: number
  skippedMissing: number
  result?: QuoteSyncResult
}

export interface CreateWatchlistGroupRequest {
  name: string
}

export interface UpdateWatchlistGroupRequest {
  name?: string
  sortOrder?: number
}

export interface ReorderWatchlistGroupStocksRequest {
  stockIds: number[]
}

export interface ReorderWatchlistGroupsRequest {
  groupIds: number[]
}

export interface UpdateStockConceptsRequest {
  concepts: string[]
}

export function resolveApiAssetUrl(path?: string | null) {
  if (!path) {
    return null
  }

  if (/^https?:\/\//i.test(path)) {
    return path
  }

  const baseUrl = http.defaults.baseURL ?? window.location.origin
  return new URL(path, baseUrl).toString()
}

export const stockApi = {
  getStocks: (params?: StockQueryParams) => http.get<PageResponse<Stock>>('/stocks', { params }),
  getStock: (id: number) => http.get<Stock>(`/stocks/${id}`),
  updateStockConcepts: (id: number, payload: UpdateStockConceptsRequest) => http.patch<Stock>(`/stocks/${id}/concepts`, payload),
  getDailyKLine: (id: number, limit = 60) => http.get<StockDailyKLine>(`/stocks/${id}/daily-k-line`, { params: { limit } }),
  getIndustries: () => http.get<string[]>('/stocks/industries'),
  getConcepts: () => http.get<string[]>('/stocks/concepts'),
  importStocks: () => http.post<ImportResult>('/stocks/import', null, { timeout: 120000 }),
  syncQuotes: () => http.post<QuoteSyncResult>('/stocks/quotes/sync', null, { timeout: 120000 }),
  quoteSyncStreamUrl: () => `${http.defaults.baseURL ?? ''}/stocks/quotes/sync/stream`,
  health: () => http.get('/health'),

  getWatchlistGroups: () => http.get<WatchlistGroup[]>('/watchlist/groups'),
  createWatchlistGroup: (payload: CreateWatchlistGroupRequest) => http.post<WatchlistGroup>('/watchlist/groups', payload),
  updateWatchlistGroup: (groupId: number, payload: UpdateWatchlistGroupRequest) => http.patch<WatchlistGroup>(`/watchlist/groups/${groupId}`, payload),
  deleteWatchlistGroup: (groupId: number) => http.delete(`/watchlist/groups/${groupId}`),
  reorderWatchlistGroups: (payload: ReorderWatchlistGroupsRequest) => http.put('/watchlist/groups/order', payload),
  getWatchlistGroupStocks: (groupId: number) => http.get<WatchlistStock[]>(`/watchlist/groups/${groupId}/stocks`),
  reorderWatchlistGroupStocks: (groupId: number, payload: ReorderWatchlistGroupStocksRequest) => http.put(`/watchlist/groups/${groupId}/stocks/order`, payload),
  ensureStockInDefaultGroup: (stockId: number) => http.post(`/watchlist/stocks/${stockId}/default`, null),
  addStockToGroup: (groupId: number, stockId: number) => http.post(`/watchlist/groups/${groupId}/stocks/${stockId}`, null),
  removeStockFromGroup: (groupId: number, stockId: number) => http.delete(`/watchlist/groups/${groupId}/stocks/${stockId}`),
  unwatchStock: (stockId: number) => http.delete(`/watchlist/stocks/${stockId}`),
  getStockGroupIds: (stockId: number) => http.get<number[]>(`/watchlist/stocks/${stockId}/groups/ids`),
  getWatchedStockIds: () => http.get<number[]>('/watchlist/stocks/ids')
}
