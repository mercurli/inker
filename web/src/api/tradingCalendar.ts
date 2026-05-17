import http from '@/shared/api/http'

export interface TradingCalendarDay {
  id: number
  tradeDate: string
  open: boolean
  holiday: boolean
  remark: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface CreateTradingCalendarDayRequest {
  tradeDate: string
  open: boolean
  holiday?: boolean
  remark?: string | null
}

export interface UpdateTradingCalendarDayRequest {
  open?: boolean
  holiday?: boolean
  remark?: string | null
}

export interface TradingCalendarQueryParams {
  startDate?: string
  endDate?: string
  open?: boolean
  sortDirection?: 'ASC' | 'DESC'
}

export interface LatestOpenTradingDate {
  tradeDate: string | null
}

export const tradingCalendarApi = {
  getDays: (params?: TradingCalendarQueryParams) => http.get<TradingCalendarDay[]>('/trading-calendar', { params }),
  getLatestOpen: () => http.get<LatestOpenTradingDate>('/trading-calendar/latest-open'),
  createDay: (payload: CreateTradingCalendarDayRequest) => http.post<TradingCalendarDay>('/trading-calendar', payload),
  updateDay: (id: number, payload: UpdateTradingCalendarDayRequest) => http.patch<TradingCalendarDay>(`/trading-calendar/${id}`, payload),
  deleteDay: (id: number) => http.delete(`/trading-calendar/${id}`)
}
