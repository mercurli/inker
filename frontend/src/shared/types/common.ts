export type TrendTone = 'up' | 'down' | 'neutral'

export interface PaginationState {
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface PaginationRequest {
  page?: number
  size?: number
}
