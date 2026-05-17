import type { TrendTone } from '@/shared/types/common'
import http from '@/shared/api/http'

const THS_HOT_LIST_URL =
  'https://dq.10jqka.com.cn/fuyao/hot_list_data/out/hot_list/v1/stock'

export interface DistributionBucket {
  label: string
  count: number
  tone: TrendTone
}

export interface SizedDistributionBucket extends DistributionBucket {
  width: string
}

export interface MarketSummary {
  total: number
  rising: number
  falling: number
  flat: number
  lastSyncedAt: string | null
  distribution: DistributionBucket[]
  topFiveDayRisingIndustries: DistributionBucket[]
  topFiveDayRisingConcepts: DistributionBucket[]
  strongest: {
    id: number
    symbol: string
    name: string
    changePercent: number | null
  } | null
}

export interface HotListTag {
  concept_tag?: string[] | string | null
  popularity_tag?: string | null
}

export interface HotListRawItem {
  order?: number | string | null
  code?: string | null
  name?: string | null
  rate?: number | string | null
  rise_and_fall?: number | string | null
  hot_rank_chg?: number | string | null
  tag?: HotListTag | null
}

export interface HotListResponse {
  status_code: number
  status_msg?: string
  data?: {
    stock_list?: HotListRawItem[]
  }
}

export const marketApi = {
  getSummary: () => http.get<MarketSummary>('/stocks/summary'),
  getHotList: () =>
    http.get<HotListResponse>(THS_HOT_LIST_URL, {
      params: {
        stock_type: 'a',
        type: 'hour',
        list_type: 'normal'
      }
    })
}
