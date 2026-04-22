import axios from 'axios'

const api = axios.create({
  baseURL: 'http://localhost:8081/api/v1',
  timeout: 30000
})

export interface Stock {
  id: number
  symbol: string
  name: string
  latestPrice: number | null
  changePercent: number | null
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

export interface StockQueryParams {
  keyword?: string
  exchangeCode?: 'SSE' | 'SZSE'
  boardType?: string
  industry?: string
  page?: number
  size?: number
  sortBy?: 'code' | 'name' | 'exchangeCode' | 'market' | 'industry' | 'listDate' | 'id' | 'latestPrice' | 'changePercent' | 'boardType'
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

export const stockApi = {
  getStocks: (params?: StockQueryParams) => api.get<PageResponse<Stock>>('/stocks', { params }),
  getStock: (id: number) => api.get<Stock>(`/stocks/${id}`),
  getDailyKLine: (id: number, limit = 60) => api.get<StockDailyKLine>(`/stocks/${id}/daily-k-line`, { params: { limit } }),
  getIndustries: () => api.get<string[]>('/stocks/industries'),
  importStocks: () => api.post<ImportResult>('/stocks/import', null, { timeout: 120000 }),
  health: () => api.get('/health')
}

export default api
