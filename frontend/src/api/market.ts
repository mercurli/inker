import type { TrendTone } from '@/shared/types/common'
import http from '@/shared/api/http'

export interface DistributionBucket {
  label: string
  count: number
  tone: TrendTone
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
  getSummary: () => http.get<MarketSummary>('/stocks/summary')
}
