import { computed, ref } from 'vue'
import { marketApi, type DistributionBucket, type MarketSummary, type SizedDistributionBucket } from '@/api/market'
import {
  stockApi,
  type QuoteSyncProgress,
  type QuoteSyncResult,
  type Stock,
  type StockDailyKLine,
  type StockQueryParams,
  type WatchlistGroup,
  type WatchlistStock
} from '@/api/stock'
import { formatAmount, formatDate, formatMarketValue, formatPeRatio, formatPercent, formatPrice, formatTurnoverRate, formatVolume, toneByPercent } from '@/shared/lib/formatters'
import type { TrendTone } from '@/shared/types/common'

export interface WatchlistItem extends WatchlistStock {}

const LEGACY_WATCHLIST_STORAGE_KEY = 'inker.watchlist'

const stocks = ref<Stock[]>([])
const loading = ref(true)
const error = ref('')
const importMessage = ref('')
const importing = ref(false)
const quoteSyncing = ref(false)
const quoteSyncProgress = ref<QuoteSyncProgress | null>(null)
const quoteSyncMessage = ref('')
const quoteSyncResult = ref<QuoteSyncResult | null>(null)

const keyword = ref('')
const exchangeCode = ref<'SSE' | 'SZSE' | ''>('')
const boardType = ref('')
const industry = ref('')
const concept = ref('')
const industries = ref<string[]>([])
const industriesLoaded = ref(false)
const industriesFetchFailed = ref(false)
const concepts = ref<string[]>([])
const conceptsLoaded = ref(false)
const conceptsFetchFailed = ref(false)

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
const watchlistGroups = ref<WatchlistGroup[]>([])
const activeWatchlistGroupId = ref<number | null>(null)
const watchlistLoading = ref(false)
const watchedStockIdsRaw = ref<number[]>([])

const marketSummary = ref<MarketSummary>({
  total: 0,
  rising: 0,
  falling: 0,
  flat: 0,
  lastSyncedAt: null,
  distribution: [],
  topFiveDayRisingIndustries: [],
  topFiveDayRisingConcepts: [],
  strongest: null
})

let debounceTimer: ReturnType<typeof setTimeout> | null = null
let initPromise: Promise<void> | null = null
let quoteSyncSource: EventSource | null = null

const watchlistCount = computed(() => watchedStockIdsRaw.value.length)
const watchlistIds = computed(() => new Set(watchedStockIdsRaw.value))
const totalPagesDisplay = computed(() => Math.max(totalPages.value, 1))
const activeWatchlistGroup = computed(() =>
  watchlistGroups.value.find((group) => group.id === activeWatchlistGroupId.value) ?? null
)

const suggestedIndustries = computed(() => {
  const industries = new Set<string>()

  stocks.value.forEach((stock) => {
    if (stock.industry) {
      industries.add(stock.industry)
    }
  })

  return Array.from(industries).sort((left, right) => left.localeCompare(right))
})

const industryOptions = computed(() => {
  if (industriesFetchFailed.value) {
    return suggestedIndustries.value
  }

  if (industries.value.length > 0 || industriesLoaded.value) {
    return industries.value
  }

  return suggestedIndustries.value
})

const suggestedConcepts = computed(() => {
  const conceptSet = new Set<string>()

  stocks.value.forEach((stock) => {
    stock.concepts.forEach((item) => {
      const trimmed = item.trim()

      if (trimmed) {
        conceptSet.add(trimmed)
      }
    })
  })

  return Array.from(conceptSet).sort((left, right) => left.localeCompare(right))
})

const conceptOptions = computed(() => {
  if (conceptsFetchFailed.value) {
    return suggestedConcepts.value
  }

  if (concepts.value.length > 0 || conceptsLoaded.value) {
    return concepts.value
  }

  return suggestedConcepts.value
})

const strongestStockLabel = computed(() => {
  const strongest = marketSummary.value.strongest

  if (!strongest) {
    return '暂无领涨股票'
  }

  return `${strongest.name} ${formatPercent(strongest.changePercent)}`
})

function withRelativeWidth(distribution: DistributionBucket[]): SizedDistributionBucket[] {
  const maxCount = Math.max(...distribution.map((item) => item.count), 0)

  return distribution.map((item) => ({
    ...item,
    width: maxCount === 0 ? '0%' : `${(item.count / maxCount) * 100}%`
  }))
}

const priceChangeDistribution = computed(() => {
  return withRelativeWidth(marketSummary.value.distribution ?? [])
})

const topFiveDayRisingIndustries = computed(() => {
  return withRelativeWidth(marketSummary.value.topFiveDayRisingIndustries ?? [])
})

const topFiveDayRisingConcepts = computed(() => {
  return withRelativeWidth(marketSummary.value.topFiveDayRisingConcepts ?? [])
})

function normalizeStock(stock: Stock): Stock {
  const concepts = Array.isArray(stock.concepts)
    ? Array.from(new Set(stock.concepts.map((item) => item.trim()).filter(Boolean)))
    : []

  return {
    ...stock,
    concepts,
    logo: stock.logo || null,
    primaryConcept: stock.primaryConcept ?? concepts[0] ?? null
  }
}

function normalizeWatchlistStock(stock: WatchlistStock): WatchlistItem {
  return {
    ...normalizeStock(stock),
    addedAt: stock.addedAt
  }
}

async function fetchWatchlistGroups() {
  const response = await stockApi.getWatchlistGroups()
  watchlistGroups.value = response.data

  if (watchlistGroups.value.length === 0) {
    activeWatchlistGroupId.value = null
    return
  }

  if (
    activeWatchlistGroupId.value == null ||
    !watchlistGroups.value.some((group) => group.id === activeWatchlistGroupId.value)
  ) {
    activeWatchlistGroupId.value = watchlistGroups.value[0]?.id ?? null
  }
}

async function fetchWatchedStockIds() {
  const response = await stockApi.getWatchedStockIds()
  watchedStockIdsRaw.value = Array.from(new Set(response.data)).sort((left, right) => left - right)
}

async function fetchActiveWatchlistStocks() {
  if (activeWatchlistGroupId.value == null) {
    watchlist.value = []
    return
  }

  const response = await stockApi.getWatchlistGroupStocks(activeWatchlistGroupId.value)
  watchlist.value = response.data.map(normalizeWatchlistStock)
}

async function refreshWatchlistState() {
  await Promise.all([fetchWatchlistGroups(), fetchWatchedStockIds()])
  await fetchActiveWatchlistStocks()
}

async function migrateLegacyWatchlistIfNeeded() {
  try {
    const rawValue = localStorage.getItem(LEGACY_WATCHLIST_STORAGE_KEY)

    if (!rawValue) {
      return
    }

    const parsedValue: unknown = JSON.parse(rawValue)

    if (!Array.isArray(parsedValue)) {
      localStorage.removeItem(LEGACY_WATCHLIST_STORAGE_KEY)
      return
    }

    if (watchedStockIdsRaw.value.length > 0) {
      localStorage.removeItem(LEGACY_WATCHLIST_STORAGE_KEY)
      return
    }

    const stockIds = parsedValue
      .filter(
        (item): item is Partial<WatchlistItem> & { id: number } =>
          typeof item === 'object' && item !== null && typeof item.id === 'number'
      )
      .map((item) => item.id)

    if (stockIds.length === 0) {
      localStorage.removeItem(LEGACY_WATCHLIST_STORAGE_KEY)
      return
    }

    await Promise.allSettled(stockIds.map((stockId) => stockApi.ensureStockInDefaultGroup(stockId)))
    localStorage.removeItem(LEGACY_WATCHLIST_STORAGE_KEY)
  } catch {
    localStorage.removeItem(LEGACY_WATCHLIST_STORAGE_KEY)
  }
}

function syncWatchlist(items: Stock[]) {
  if (watchlist.value.length === 0 || items.length === 0) {
    return
  }

  const stockMap = new Map(items.map((stock) => [stock.id, stock]))

  watchlist.value = watchlist.value.map((item) => {
    const latestStock = stockMap.get(item.id)

    if (!latestStock) {
      return item
    }

    return {
      ...normalizeStock(latestStock),
      addedAt: item.addedAt
    }
  })
}

function mergeConceptOptions(nextConcepts: string[]) {
  const conceptSet = new Set(concepts.value)

  nextConcepts.forEach((item) => {
    const trimmed = item.trim()

    if (trimmed) {
      conceptSet.add(trimmed)
    }
  })

  concepts.value = Array.from(conceptSet).sort((left, right) => left.localeCompare(right))
}

function syncStockState(stock: Stock) {
  const normalizedStock = normalizeStock(stock)

  stocks.value = stocks.value.map((item) => (item.id === normalizedStock.id ? normalizedStock : item))

  if (selectedStockId.value === normalizedStock.id || selectedStock.value?.id === normalizedStock.id) {
    selectedStockId.value = normalizedStock.id
    selectedStock.value = normalizedStock
  }

  syncWatchlist([normalizedStock])
  mergeConceptOptions(normalizedStock.concepts)
}

function isInWatchlist(stockId: number) {
  return watchlistIds.value.has(stockId)
}

async function updateStockConcepts(stockId: number, nextConcepts: string[]) {
  try {
    const response = await stockApi.updateStockConcepts(stockId, { concepts: nextConcepts })
    const normalizedStock = normalizeStock(response.data)

    syncStockState(normalizedStock)
    await fetchConcepts()
    return normalizedStock
  } catch {
    throw new Error('Failed to update stock concepts')
  }
}

async function toggleWatchlist(stock: Stock) {
  try {
    if (isInWatchlist(stock.id)) {
      await stockApi.unwatchStock(stock.id)
    } else {
      await stockApi.ensureStockInDefaultGroup(stock.id)
    }

    await refreshWatchlistState()
  } catch {
    error.value = '更新自选状态失败，请稍后重试。'
  }
}

async function removeFromWatchlist(stockId: number) {
  try {
    await stockApi.unwatchStock(stockId)
    await refreshWatchlistState()
  } catch {
    error.value = '移除自选失败，请稍后重试。'
  }
}

async function removeFromActiveGroup(stockId: number) {
  if (activeWatchlistGroupId.value == null) {
    return
  }

  try {
    await stockApi.removeStockFromGroup(activeWatchlistGroupId.value, stockId)
    await refreshWatchlistState()
  } catch {
    error.value = '从分组移除失败，请稍后重试。'
  }
}

async function addToGroup(stockId: number, groupId: number) {
  try {
    await stockApi.addStockToGroup(groupId, stockId)
    await refreshWatchlistState()
  } catch {
    error.value = '添加到分组失败，请稍后重试。'
  }
}

async function removeFromGroup(stockId: number, groupId: number) {
  try {
    await stockApi.removeStockFromGroup(groupId, stockId)
    await refreshWatchlistState()
  } catch {
    error.value = '从分组移除失败，请稍后重试。'
  }
}

async function getStockGroupIds(stockId: number) {
  try {
    const response = await stockApi.getStockGroupIds(stockId)
    return response.data
  } catch {
    error.value = '加载股票分组失败，请稍后重试。'
    return []
  }
}

async function moveStockToGroup(stockId: number, targetGroupId: number) {
  const sourceGroupId = activeWatchlistGroupId.value

  if (sourceGroupId == null || sourceGroupId === targetGroupId) {
    return
  }

  try {
    // Add first, then remove from current group to avoid accidental unwatch on failures.
    await stockApi.addStockToGroup(targetGroupId, stockId)
    await stockApi.removeStockFromGroup(sourceGroupId, stockId)
    await refreshWatchlistState()
  } catch {
    error.value = '切换分组失败，请稍后重试。'
  }
}

async function createWatchlistGroup(name: string) {
  const groupName = name.trim()

  if (!groupName) {
    return
  }

  try {
    const response = await stockApi.createWatchlistGroup({ name: groupName })
    await fetchWatchlistGroups()
    activeWatchlistGroupId.value = response.data.id
    await fetchActiveWatchlistStocks()
  } catch {
    error.value = '创建分组失败，请稍后重试。'
  }
}

async function renameWatchlistGroup(groupId: number, name: string) {
  const groupName = name.trim()

  if (!groupName) {
    return
  }

  try {
    await stockApi.updateWatchlistGroup(groupId, { name: groupName })
    await fetchWatchlistGroups()
  } catch {
    error.value = '重命名分组失败，请稍后重试。'
  }
}

async function deleteWatchlistGroup(groupId: number) {
  try {
    await stockApi.deleteWatchlistGroup(groupId)
    await refreshWatchlistState()
  } catch {
    error.value = '删除分组失败，请稍后重试。'
  }
}

async function reorderWatchlistGroups(groupIds: number[]) {
  const previousGroups = watchlistGroups.value
  const defaultGroups = previousGroups.filter((group) => group.default)
  const groupById = new Map(previousGroups.map((group) => [group.id, group]))
  const nextGroups = [
    ...defaultGroups.map((group) => ({ ...group, sortOrder: 0 })),
    ...groupIds
      .map((groupId, index) => {
        const group = groupById.get(groupId)
        return group ? { ...group, sortOrder: index + 1 } : null
      })
      .filter((group): group is WatchlistGroup => group != null)
  ]

  if (nextGroups.length !== previousGroups.length) {
    return
  }

  watchlistGroups.value = nextGroups

  try {
    await stockApi.reorderWatchlistGroups({ groupIds })
    await fetchWatchlistGroups()
  } catch {
    watchlistGroups.value = previousGroups
    error.value = '保存分组排序失败，请稍后重试。'
  }
}

async function reorderWatchlistGroupStocks(groupId: number, stockIds: number[]) {
  const previousWatchlist = watchlist.value
  const itemById = new Map(watchlist.value.map((item) => [item.id, item]))
  const nextWatchlist = stockIds
    .map((stockId) => itemById.get(stockId))
    .filter((item): item is WatchlistItem => item != null)

  if (nextWatchlist.length !== watchlist.value.length) {
    return
  }

  watchlist.value = nextWatchlist

  try {
    await stockApi.reorderWatchlistGroupStocks(groupId, { stockIds })
    await fetchWatchlistGroups()
  } catch {
    watchlist.value = previousWatchlist
    error.value = '保存排序失败，请稍后重试。'
  }
}

async function setActiveWatchlistGroup(groupId: number) {
  activeWatchlistGroupId.value = groupId

  try {
    watchlistLoading.value = true
    await fetchActiveWatchlistStocks()
  } catch {
    error.value = '加载分组失败，请稍后重试。'
  } finally {
    watchlistLoading.value = false
  }
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
      topFiveDayRisingIndustries: [],
      topFiveDayRisingConcepts: [],
      strongest: null
    }
  }
}

async function fetchIndustries() {
  try {
    const response = await stockApi.getIndustries()
    const uniqueIndustries = new Set<string>()

    response.data.forEach((item) => {
      const trimmed = item.trim()

      if (trimmed) {
        uniqueIndustries.add(trimmed)
      }
    })

    industries.value = Array.from(uniqueIndustries).sort((left, right) => left.localeCompare(right))
    industriesFetchFailed.value = false
  } catch {
    industriesFetchFailed.value = true
  } finally {
    industriesLoaded.value = true
  }
}

async function fetchConcepts() {
  try {
    const response = await stockApi.getConcepts()
    const uniqueConcepts = new Set<string>()

    response.data.forEach((item) => {
      const trimmed = item.trim()

      if (trimmed) {
        uniqueConcepts.add(trimmed)
      }
    })

    concepts.value = Array.from(uniqueConcepts).sort((left, right) => left.localeCompare(right))
    conceptsFetchFailed.value = false
  } catch {
    conceptsFetchFailed.value = true
  } finally {
    conceptsLoaded.value = true
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
      concept: concept.value || undefined,
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

    await Promise.all([fetchStocks(), fetchMarketSummary(), refreshWatchlistState(), fetchIndustries(), fetchConcepts()])
  } catch {
    error.value = '同步 A 股数据失败，请稍后重试。'
  } finally {
    importing.value = false
  }
}

function parseQuoteSyncProgress(event: MessageEvent<string>): QuoteSyncProgress | null {
  try {
    return JSON.parse(event.data) as QuoteSyncProgress
  } catch {
    return null
  }
}

function closeQuoteSyncSource() {
  if (quoteSyncSource) {
    quoteSyncSource.close()
    quoteSyncSource = null
  }
}

async function refreshAfterQuoteSync() {
  await Promise.all([fetchStocks(), fetchMarketSummary(), refreshWatchlistState()])
}

function handleQuoteSyncProgress(event: MessageEvent<string>) {
  const progress = parseQuoteSyncProgress(event)

  if (!progress) {
    return
  }

  quoteSyncProgress.value = progress
  quoteSyncMessage.value = progress.message

  if (progress.stage === 'completed') {
    quoteSyncResult.value = progress.result ?? {
      source: 'tushare',
      fetched: progress.fetched,
      matched: progress.matched,
      updated: progress.updated,
      skippedMissing: progress.skippedMissing,
      fiveDayBaselineTradeDate: progress.fiveDayBaselineTradeDate,
      fiveDayBaselineCount: progress.fiveDayBaselineCount
    }
    quoteSyncMessage.value = `行情同步完成：抓取 ${progress.fetched} 条，匹配 ${progress.matched} 条，更新 ${progress.updated} 条。`
    quoteSyncing.value = false
    closeQuoteSyncSource()
    void refreshAfterQuoteSync()
  }

  if (progress.stage === 'failed') {
    error.value = progress.message || '同步行情失败，请稍后重试。'
    quoteSyncing.value = false
    closeQuoteSyncSource()
  }
}

function syncQuotesWithProgress() {
  if (quoteSyncing.value) {
    return
  }

  closeQuoteSyncSource()
  quoteSyncing.value = true
  error.value = ''
  quoteSyncMessage.value = '准备同步行情...'
  quoteSyncResult.value = null
  quoteSyncProgress.value = {
    stage: 'starting',
    percent: 0,
    message: '准备同步行情...',
    tradeDate: null,
    fetched: 0,
    matched: 0,
    updated: 0,
    skippedMissing: 0,
    fiveDayBaselineTradeDate: null,
    fiveDayBaselineCount: 0
  }

  const source = new EventSource(stockApi.quoteSyncStreamUrl())
  quoteSyncSource = source

  ;['starting', 'resolving_trade_date', 'fetching_quotes', 'fetching_history', 'matching', 'saving', 'completed', 'failed'].forEach((eventName) => {
    source.addEventListener(eventName, (event) => handleQuoteSyncProgress(event as MessageEvent<string>))
  })

  source.onerror = () => {
    if (!quoteSyncing.value) {
      return
    }

    error.value = '同步行情连接中断，请稍后重试。'
    quoteSyncing.value = false
    closeQuoteSyncSource()
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

function clearMessages() {
  importMessage.value = ''
  quoteSyncMessage.value = ''
  error.value = ''
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
  if (!initPromise) {
    initPromise = (async () => {
      watchlistLoading.value = true
      await Promise.all([fetchStocks(), fetchMarketSummary(), refreshWatchlistState(), fetchIndustries(), fetchConcepts()])
      await migrateLegacyWatchlistIfNeeded()
      await refreshWatchlistState()
      watchlistLoading.value = false
    })()
  }

  await initPromise
}

export function useDashboardStore() {
  return {
    stocks,
    loading,
    error,
    importMessage,
    importing,
    quoteSyncing,
    quoteSyncProgress,
    quoteSyncMessage,
    quoteSyncResult,
    keyword,
    exchangeCode,
    boardType,
    industry,
    concept,
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
    watchlistGroups,
    activeWatchlistGroup,
    activeWatchlistGroupId,
    watchlistLoading,
    watchlistCount,
    marketSummary,
    strongestStockLabel,
    suggestedIndustries,
    industryOptions,
    suggestedConcepts,
    conceptOptions,
    priceChangeDistribution,
    topFiveDayRisingIndustries,
    topFiveDayRisingConcepts,
    importStocks,
    syncQuotesWithProgress,
    onSearch,
    debouncedOnSearch,
    onSort,
    sortIndicator,
    goToPage,
    loadDetailById,
    updateStockConcepts,
    toggleWatchlist,
    removeFromWatchlist,
    removeFromActiveGroup,
    addToGroup,
    removeFromGroup,
    getStockGroupIds,
    moveStockToGroup,
    createWatchlistGroup,
    renameWatchlistGroup,
    deleteWatchlistGroup,
    reorderWatchlistGroups,
    reorderWatchlistGroupStocks,
    setActiveWatchlistGroup,
    isInWatchlist,
    formatPrice,
    formatPercent,
    formatAmount,
    formatVolume,
    formatMarketValue,
    formatTurnoverRate,
    formatPeRatio,
    formatDate,
    changeClass: toneByPercent as (value: number | null) => TrendTone,
    boardClass,
    clearMessages,
    fetchMarketSummary,
    fetchIndustries,
    fetchConcepts,
    refreshWatchlistState,
    init
  }
}
