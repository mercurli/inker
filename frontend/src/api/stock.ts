import http from '@/shared/api/http'

export interface Stock {
  id: number
  symbol: string
  name: string
  latestPrice: number | null
  changePercent: number | null
  totalMarketValue: number | null
  market: string
  exchangeCode?: string
  industry?: string
  concepts: string[]
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
  sortBy?: 'code' | 'name' | 'exchangeCode' | 'market' | 'industry' | 'listDate' | 'id' | 'latestPrice' | 'changePercent' | 'totalMarketValue' | 'boardType'
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

export const stockApi = {
  getStocks: (params?: StockQueryParams) => http.get<PageResponse<Stock>>('/stocks', { params }),
  getStock: (id: number) => http.get<Stock>(`/stocks/${id}`),
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
  getWatchlistGroupStocks: (groupId: number) => http.get<WatchlistStock[]>(`/watchlist/groups/${groupId}/stocks`),
  ensureStockInDefaultGroup: (stockId: number) => http.post(`/watchlist/stocks/${stockId}/default`, null),
  addStockToGroup: (groupId: number, stockId: number) => http.post(`/watchlist/groups/${groupId}/stocks/${stockId}`, null),
  removeStockFromGroup: (groupId: number, stockId: number) => http.delete(`/watchlist/groups/${groupId}/stocks/${stockId}`),
  unwatchStock: (stockId: number) => http.delete(`/watchlist/stocks/${stockId}`),
  getWatchedStockIds: () => http.get<number[]>('/watchlist/stocks/ids')
}
