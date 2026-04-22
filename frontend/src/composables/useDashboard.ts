import { computed, ref } from 'vue'
import { marketApi, type DistributionBucket, type MarketSummary } from '@/api/market'
import { stockApi, type Stock, type StockDailyKLine, type StockQueryParams } from '@/api/stock'

export interface WatchlistItem extends Stock {
  addedAt: string
}

export type DashboardPage = 'market' | 'stocks' | 'watchlist'

const WATCHLIST_STORAGE_KEY = 'inker.watchlist'
const DEFAULT_PAGE: DashboardPage = 'market'

export function normalizeDashboardPage(hash: string): DashboardPage {
  const normalizedHash = hash.replace(/^#/, '')

  if (normalizedHash === 'market' || normalizedHash === 'stocks' || normalizedHash === 'watchlist') {
    return normalizedHash
  }

  return DEFAULT_PAGE
}

export function dashboardHash(page: DashboardPage): string {
  return `#${page}`
}

export function useDashboard() {
  const stocks = ref<Stock[]>([])
  const loading = ref(true)
  const error = ref('')
  const importMessage = ref('')
  const importing = ref(false)

  const keyword = ref('')
  const exchangeCode = ref<'SSE' | 'SZSE' | ''>('')
  const boardType = ref('')
  const industry = ref('')

  const page = ref(0)
  const size = ref(20)
  const totalElements = ref(0)
  const totalPages = ref(0)
  const sortBy = ref<StockQueryParams['sortBy']>('code')
  const sortDirection = ref<'ASC' | 'DESC'>('ASC')

  const selectedStockId = ref<number | null>(null)
  const selectedStock = ref<Stock | null>(null)
  const detailLoading = ref(false)
  const detailError = ref('')
  const dailyKLine = ref<StockDailyKLine | null>(null)
  const kLineLoading = ref(false)
  const kLineError = ref('')

  const watchlist = ref<WatchlistItem[]>([])
  const marketSummary = ref<MarketSummary>({
    total: 0,
    rising: 0,
    falling: 0,
    flat: 0,
    lastSyncedAt: null,
    distribution: [],
    strongest: null
  })

  let debounceTimer: ReturnType<typeof setTimeout> | null = null

  const watchlistCount = computed(() => watchlist.value.length)
  const watchlistIds = computed(() => new Set(watchlist.value.map((stock) => stock.id)))
  const totalPagesDisplay = computed(() => Math.max(totalPages.value, 1))

  const suggestedIndustries = computed(() => {
    const industries = new Set<string>()

    stocks.value.forEach((stock) => {
      if (stock.industry) {
        industries.add(stock.industry)
      }
    })

    return Array.from(industries).sort((left, right) => left.localeCompare(right))
  })

  const strongestStockLabel = computed(() => {
    const strongest = marketSummary.value.strongest

    if (!strongest) {
      return '暂无领涨股票'
    }

    return `${strongest.name} ${formatPercent(strongest.changePercent)}`
  })

  const priceChangeDistribution = computed(() => {
    const distribution: DistributionBucket[] = marketSummary.value.distribution ?? []

    const maxCount = Math.max(...distribution.map((item) => item.count), 0)

    return distribution.map((item) => ({
      ...item,
      width: maxCount === 0 ? '0%' : `${(item.count / maxCount) * 100}%`
    }))
  })

  function normalizeStock(stock: Stock): Stock {
    return {
      ...stock,
      concepts: Array.isArray(stock.concepts) ? stock.concepts : []
    }
  }

  function toWatchlistItem(stock: Stock, addedAt = new Date().toISOString()): WatchlistItem {
    return {
      ...normalizeStock(stock),
      addedAt
    }
  }

  function saveWatchlist() {
    localStorage.setItem(WATCHLIST_STORAGE_KEY, JSON.stringify(watchlist.value))
  }

  function loadWatchlist() {
    try {
      const rawValue = localStorage.getItem(WATCHLIST_STORAGE_KEY)

      if (!rawValue) {
        watchlist.value = []
        return
      }

      const parsedValue: unknown = JSON.parse(rawValue)

      if (!Array.isArray(parsedValue)) {
        watchlist.value = []
        return
      }

      watchlist.value = parsedValue
        .filter(
          (item): item is Partial<WatchlistItem> & { id: number } =>
            typeof item === 'object' && item !== null && typeof item.id === 'number'
        )
        .map((item) =>
          toWatchlistItem(item as Stock, typeof item.addedAt === 'string' ? item.addedAt : new Date().toISOString())
        )
        .sort((left, right) => right.addedAt.localeCompare(left.addedAt))
    } catch {
      watchlist.value = []
    }
  }

  function syncWatchlist(items: Stock[]) {
    if (watchlist.value.length === 0 || items.length === 0) {
      return
    }

    const stockMap = new Map(items.map((stock) => [stock.id, stock]))
    let changed = false

    const nextWatchlist = watchlist.value.map((item) => {
      const latestStock = stockMap.get(item.id)

      if (!latestStock) {
        return item
      }

      const nextItem = toWatchlistItem(latestStock, item.addedAt)

      if (JSON.stringify(nextItem) !== JSON.stringify(item)) {
        changed = true
        return nextItem
      }

      return item
    })

    if (changed) {
      watchlist.value = nextWatchlist
      saveWatchlist()
    }
  }

  async function refreshWatchlistFromServer() {
    if (watchlist.value.length === 0) {
      return
    }

    const responses = await Promise.all(
      watchlist.value.map(async (item) => {
        try {
          const response = await stockApi.getStock(item.id)
          return normalizeStock(response.data)
        } catch {
          return null
        }
      })
    )

    const latestMap = new Map(
      responses
        .filter((item): item is Stock => item != null)
        .map((item) => [item.id, item])
    )

    let changed = false
    const nextWatchlist = watchlist.value.map((item) => {
      const latest = latestMap.get(item.id)

      if (!latest) {
        return item
      }

      const nextItem = toWatchlistItem(latest, item.addedAt)
      if (JSON.stringify(nextItem) !== JSON.stringify(item)) {
        changed = true
        return nextItem
      }

      return item
    })

    if (changed) {
      watchlist.value = nextWatchlist
      saveWatchlist()
    }
  }

  function isInWatchlist(stockId: number) {
    return watchlistIds.value.has(stockId)
  }

  function addToWatchlist(stock: Stock) {
    if (isInWatchlist(stock.id)) {
      return
    }

    watchlist.value = [toWatchlistItem(stock), ...watchlist.value]
    saveWatchlist()
  }

  function removeFromWatchlist(stockId: number) {
    watchlist.value = watchlist.value.filter((stock) => stock.id !== stockId)
    saveWatchlist()
  }

  function toggleWatchlist(stock: Stock) {
    if (isInWatchlist(stock.id)) {
      removeFromWatchlist(stock.id)
      return
    }

    addToWatchlist(stock)
  }

  async function fetchMarketSummary() {
    try {
      const response = await marketApi.getSummary()
      marketSummary.value = response.data
    } catch {
      marketSummary.value = {
        total: totalElements.value,
        rising: 0,
        falling: 0,
        flat: 0,
        lastSyncedAt: null,
        distribution: [],
        strongest: null
      }
    }
  }

  async function fetchStocks() {
    try {
      loading.value = true
      error.value = ''

      const response = await stockApi.getStocks({
        keyword: keyword.value || undefined,
        exchangeCode: exchangeCode.value || undefined,
        boardType: boardType.value || undefined,
        industry: industry.value || undefined,
        page: page.value,
        size: size.value,
        sortBy: sortBy.value,
        sortDirection: sortDirection.value
      })

      stocks.value = response.data.content.map(normalizeStock)
      totalElements.value = response.data.totalElements
      totalPages.value = response.data.totalPages
      syncWatchlist(stocks.value)

      if (selectedStockId.value != null) {
        const existing = stocks.value.find((stock) => stock.id === selectedStockId.value)

        if (existing) {
          selectedStock.value = {
            ...selectedStock.value,
            ...existing
          }
        }
      }
    } catch {
      error.value = '加载股票数据失败，请确认后端服务是否已启动。'
    } finally {
      loading.value = false
    }
  }

  async function importStocks() {
    try {
      importing.value = true
      error.value = ''
      importMessage.value = ''

      const response = await stockApi.importStocks()
      const result = response.data

      importMessage.value = `同步完成：抓取 ${result.fetched} 条，入库 ${result.imported} 条，过滤 ST ${result.skippedSt} 条，过滤北交所 ${result.skippedBeijingExchange} 条。`
      page.value = 0

      await Promise.all([fetchStocks(), fetchMarketSummary()])
      await refreshWatchlistFromServer()
    } catch {
      error.value = '同步 A 股数据失败，请稍后重试。'
    } finally {
      importing.value = false
    }
  }

  function onSearch() {
    page.value = 0
    void fetchStocks()
  }

  function debouncedOnSearch() {
    if (debounceTimer) {
      clearTimeout(debounceTimer)
    }

    debounceTimer = setTimeout(() => {
      page.value = 0
      void fetchStocks()
    }, 300)
  }

  async function onSort(field: StockQueryParams['sortBy']) {
    if (sortBy.value === field) {
      sortDirection.value = sortDirection.value === 'ASC' ? 'DESC' : 'ASC'
    } else {
      sortBy.value = field
      sortDirection.value = 'ASC'
    }

    page.value = 0
    await fetchStocks()
  }

  function sortIndicator(field: StockQueryParams['sortBy']) {
    if (sortBy.value !== field) {
      return ''
    }

    return sortDirection.value === 'ASC' ? ' ↑' : ' ↓'
  }

  async function goToPage(nextPage: number) {
    if (nextPage < 0 || nextPage >= totalPages.value) {
      return
    }

    page.value = nextPage
    await fetchStocks()
  }

  async function onSizeChange() {
    page.value = 0
    await fetchStocks()
  }

  async function loadDetailById(stockId: number, stockPreview?: Stock | null) {
    selectedStockId.value = stockId
    selectedStock.value = stockPreview ?? selectedStock.value
    dailyKLine.value = null
    detailLoading.value = true
    detailError.value = ''
    kLineLoading.value = true
    kLineError.value = ''

    try {
      const [stockResponse, kLineResponse] = await Promise.all([
        stockApi.getStock(stockId),
        stockApi.getDailyKLine(stockId, 120)
      ])
      const normalizedStock = normalizeStock(stockResponse.data)
      selectedStock.value = normalizedStock
      dailyKLine.value = kLineResponse.data
      syncWatchlist([normalizedStock])
    } catch {
      detailError.value = '加载个股详情失败，请稍后重试。'
      kLineError.value = '加载日K失败，请稍后重试。'
    } finally {
      detailLoading.value = false
      kLineLoading.value = false
    }
  }

  async function openDetail(stock: Stock) {
    await loadDetailById(stock.id, stock)
  }

  function closeDetail() {
    selectedStockId.value = null
    selectedStock.value = null
    detailLoading.value = false
    detailError.value = ''
    dailyKLine.value = null
    kLineLoading.value = false
    kLineError.value = ''
  }

  function formatPrice(price: number | null) {
    if (price == null) {
      return '--'
    }

    return price.toFixed(2)
  }

  function formatPercent(pct: number | null) {
    if (pct == null) {
      return '--'
    }

    const sign = pct > 0 ? '+' : ''
    return `${sign}${pct.toFixed(2)}%`
  }

  function formatDate(value: string | undefined) {
    if (!value) {
      return '--'
    }

    const date = new Date(value)

    if (Number.isNaN(date.getTime())) {
      return value
    }

    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    const hours = String(date.getHours()).padStart(2, '0')
    const minutes = String(date.getMinutes()).padStart(2, '0')

    return `${year}-${month}-${day} ${hours}:${minutes}`
  }

  function changeClass(pct: number | null) {
    if (pct == null || pct === 0) {
      return 'neutral'
    }

    return pct > 0 ? 'up' : 'down'
  }

  function boardClass(board: string | undefined) {
    if (!board) {
      return ''
    }

    if (board === '科创板') {
      return 'board-star'
    }

    if (board === '创业板') {
      return 'board-gem'
    }

    return 'board-main'
  }

  async function init() {
    loadWatchlist()
    await Promise.all([fetchStocks(), fetchMarketSummary()])
    await refreshWatchlistFromServer()
  }

  return {
    stocks,
    loading,
    error,
    importMessage,
    importing,
    keyword,
    exchangeCode,
    boardType,
    industry,
    page,
    size,
    totalElements,
    totalPages,
    totalPagesDisplay,
    selectedStockId,
    selectedStock,
    detailLoading,
    detailError,
    dailyKLine,
    kLineLoading,
    kLineError,
    watchlist,
    watchlistCount,
    marketSummary,
    strongestStockLabel,
    suggestedIndustries,
    priceChangeDistribution,
    importStocks,
    onSearch,
    debouncedOnSearch,
    onSort,
    sortIndicator,
    goToPage,
    onSizeChange,
    loadDetailById,
    openDetail,
    closeDetail,
    toggleWatchlist,
    removeFromWatchlist,
    isInWatchlist,
    formatPrice,
    formatPercent,
    formatDate,
    changeClass,
    boardClass,
    init
  }
}
