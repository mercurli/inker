import api from './stock'

export interface DistributionBucket {
  label: string
  count: number
  tone: 'up' | 'down' | 'neutral'
}

export interface MarketSummary {
  total: number
  rising: number
  falling: number
  flat: number
  lastSyncedAt: string | null
  distribution: DistributionBucket[]
  strongest: {
    id: number
    symbol: string
    name: string
    changePercent: number | null
  } | null
}

export const marketApi = {
  getSummary: () => api.get<MarketSummary>('/stocks/summary')
}
