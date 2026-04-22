<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { marketApi, type MarketSummary } from './api/market'
import { stockApi, type Stock, type StockQueryParams } from './api/stock'

interface WatchlistItem extends Stock {
  addedAt: string
}

type DashboardTab = 'market' | 'stock' | 'watchlist'

const WATCHLIST_STORAGE_KEY = 'inker.watchlist'

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
const watchlist = ref<WatchlistItem[]>([])
const activeTab = ref<DashboardTab>('market')
const marketSummary = ref<MarketSummary>({
  total: 0,
  rising: 0,
  falling: 0,
  flat: 0,
  strongest: null
})

// Debounce for search inputs
const debounceTimeout = ref<NodeJS.Timeout | null>(null)
const debouncedOnSearch = (): void => {
  if (debounceTimeout.value) {
    clearTimeout(debounceTimeout.value)
  }
  debounceTimeout.value = setTimeout(() => {
    page.value = 0
    void fetchStocks()
  }, 300)
}

const totalPagesDisplay = computed(() => Math.max(totalPages.value, 1))
const watchlistCount = computed(() => watchlist.value.length)
const watchlistIds = computed(() => new Set(watchlist.value.map((stock) => stock.id)))





const priceChangeDistribution = computed(() => {
  const ranges = [
    { min: -Infinity, max: -9, label: '-9%以下' },
    { min: -9, max: -6, label: '-9%~-6%' },
    { min: -6, max: -3, label: '-6%~-3%' },
    { min: -3, max: 0, label: '-3%~0%' },
    { min: 0, max: 3, label: '0%~3%' },
    { min: 3, max: 6, label: '3%~6%' },
    { min: 6, max: 9, label: '6%~9%' },
    { min: 9, max: Infinity, label: '9%以上' }
  ]

  const distribution = ranges.map(range => {
    const count = stocks.value.reduce((acc, stock) => {
      const change = stock.changePercent ?? 0
      if (change >= range.min && change < range.max) {
        return acc + 1
      }
      return acc
    }, 0)
    return { label: range.label, count }
  })

  const maxCount = Math.max(...distribution.map(item => item.count), 0)

  return distribution.map(item => ({
    ...item,
    width: maxCount === 0 ? '0%' : `${(item.count / maxCount) * 100}%`
  }))
})





const saveWatchlist = () => {
  localStorage.setItem(WATCHLIST_STORAGE_KEY, JSON.stringify(watchlist.value))
}

const loadWatchlist = () => {
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

const syncWatchlist = (items: Stock[]) => {
  if (watchlist.value.length === 0 || items.length === 0) return

  const stockMap = new Map(items.map((stock) => [stock.id, stock]))
  let changed = false

  const nextWatchlist = watchlist.value.map((item) => {
    const latestStock = stockMap.get(item.id)
    if (!latestStock) return item

    const nextItem = toWatchlistItem(latestStock, item.addedAt)
    if (
      nextItem.symbol !== item.symbol ||
      nextItem.name !== item.name ||
      nextItem.latestPrice !== item.latestPrice ||
      nextItem.changePercent !== item.changePercent ||
      nextItem.market !== item.market ||
      nextItem.exchangeCode !== item.exchangeCode ||
      nextItem.industry !== item.industry ||
      nextItem.boardType !== item.boardType ||
      nextItem.listDate !== item.listDate
    ) {
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

const isInWatchlist = (stockId: number) => watchlistIds.value.has(stockId)

const addToWatchlist = (stock: Stock) => {
  if (isInWatchlist(stock.id)) return

  watchlist.value = [toWatchlistItem(stock), ...watchlist.value]
  saveWatchlist()
}

const removeFromWatchlist = (stockId: number) => {
  watchlist.value = watchlist.value.filter((stock) => stock.id !== stockId)
  saveWatchlist()
}

const toggleWatchlist = (stock: Stock) => {
  if (isInWatchlist(stock.id)) {
    removeFromWatchlist(stock.id)
    return
  }

  addToWatchlist(stock)
}

const fetchMarketSummary = async () => {
  try {
    const response = await marketApi.getSummary()
    marketSummary.value = response.data
  } catch {
    marketSummary.value = {
      total: totalElements.value,
      rising: 0,
      falling: 0,
      flat: 0,
      strongest: null
    }
  }
}

const fetchStocks = async () => {
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

    stocks.value = response.data.content
    totalElements.value = response.data.totalElements
    totalPages.value = response.data.totalPages
    syncWatchlist(response.data.content)

    if (selectedStockId.value != null) {
      const existing = response.data.content.find((stock) => stock.id === selectedStockId.value)
      if (existing) {
        selectedStock.value = { ...selectedStock.value, ...existing }
      }
    }
  } catch {
    error.value = '加载股票数据失败，请确认后端服务是否启动。'
  } finally {
    loading.value = false
  }
}

const importStocks = async () => {
  try {
    importing.value = true
    error.value = ''
    importMessage.value = ''

    const response = await stockApi.importStocks()
    const result = response.data
    importMessage.value = `同步完成：抓取 ${result.fetched} 条，入库 ${result.imported} 条，过滤ST ${result.skippedSt} 条，过滤北交所 ${result.skippedBeijingExchange} 条。`
    page.value = 0
    await Promise.all([fetchStocks(), fetchMarketSummary()])
  } catch {
    error.value = '同步A股数据失败'
  } finally {
    importing.value = false
  }
}



const onSort = async (field: StockQueryParams['sortBy']) => {
  if (sortBy.value === field) {
    sortDirection.value = sortDirection.value === 'ASC' ? 'DESC' : 'ASC'
  } else {
    sortBy.value = field
    sortDirection.value = 'ASC'
  }

  page.value = 0
  await fetchStocks()
}

const sortIndicator = (field: string) => {
  if (sortBy.value !== field) return ''
  return sortDirection.value === 'ASC' ? ' ↑' : ' ↓'
}

const goToPage = async (nextPage: number) => {
  if (nextPage < 0 || nextPage >= totalPages.value) return
  page.value = nextPage
  await fetchStocks()
}

const onSizeChange = async () => {
  page.value = 0
  await fetchStocks()
}

const openDetail = async (stock: Stock) => {
  selectedStockId.value = stock.id
  selectedStock.value = stock
  detailLoading.value = true
  detailError.value = ''

  try {
    const response = await stockApi.getStock(stock.id)
    selectedStock.value = response.data
    syncWatchlist([response.data])
  } catch {
    detailError.value = '加载个股详情失败，请稍后重试。'
  } finally {
    detailLoading.value = false
  }
}

const closeDetail = () => {
  selectedStockId.value = null
  selectedStock.value = null
  detailLoading.value = false
  detailError.value = ''
}



const formatPrice = (price: number | null) => {
  if (price == null) return '--'
  return price.toFixed(2)
}

const formatPercent = (pct: number | null) => {
  if (pct == null) return '--'
  const sign = pct > 0 ? '+' : ''
  return `${sign}${pct.toFixed(2)}%`
}

const formatDate = (value: string | undefined) => {
   if (!value) return '--'
   return value
 }

const suggestedIndustries = computed(() => {
    // Extract unique industries from all stocks and return sorted list
    const industries = new Set<string>()
    stocks.value.forEach(stock => {
      if (stock.industry) {
        industries.add(stock.industry)
      }
    })
    return Array.from(industries).sort()
  })

const filteredStocks = computed(() => {
    return stocks.value.filter(stock => {
      // Filter by keyword (search in symbol or name)
      if (keyword.value) {
        const keywordLower = keyword.value.toLowerCase()
        if (
          !stock.symbol.toLowerCase().includes(keywordLower) &&
          !stock.name.toLowerCase().includes(keywordLower)
        ) {
          return false
        }
      }
      
      // Filter by exchangeCode
      if (exchangeCode.value && stock.exchangeCode !== exchangeCode.value) {
        return false
      }
      
      // Filter by boardType
      if (boardType.value && stock.boardType !== boardType.value) {
        return false
      }
      
      // Filter by industry
      if (industry.value && stock.industry?.toLowerCase() !== industry.value.toLowerCase()) {
        return false
      }
      
      return true
    })
  })

const changeClass = (pct: number | null) => {
  if (pct == null || pct === 0) return 'neutral'
  return pct > 0 ? 'up' : 'down'
}

const boardClass = (board: string | undefined) => {
  if (!board) return ''
  if (board === '科创板') return 'board-star'
  if (board === '创业板') return 'board-gem'
  return 'board-main'
}

loadWatchlist()

onMounted(() => {
  void Promise.all([fetchStocks(), fetchMarketSummary()])
})
</script>

<template>
  <div class="app-shell">
    <div class="dashboard-frame">
      <aside class="dashboard-sidebar panel">
        <div class="sidebar-brand">
          <span class="brand-mark">研</span>
          <div>
            <p class="eyebrow">Inker OS</p>
            <h1 class="sidebar-title">研墨终端</h1>
          </div>
        </div>

         <nav class="sidebar-nav">
           <button class="nav-item nav-item--active" type="button" @click="activeTab = 'market'">
             <span class="nav-icon">▣</span>
             <span>市场总览</span>
           </button>
           <button class="nav-item" type="button" @click="activeTab = 'stock'">
             <span class="nav-icon">◎</span>
             <span>选股列表</span>
           </button>
           <button class="nav-item" type="button" @click="activeTab = 'watchlist'">
             <span class="nav-icon">◌</span>
             <span>自选追踪</span>
           </button>
         </nav>

        <section class="sidebar-card">
          <p class="eyebrow">Workspace</p>
          <h2 class="sidebar-card-title">现代金融看板</h2>
          <p class="sidebar-card-desc">以浅色卡片、柔和阴影和橙色强调重构你的股票分析工作台。</p>
        </section>
      </aside>

      <div class="app-grid" :class="{ 'app-grid--detail': selectedStockId !== null }">
        <section class="app">
          <header class="app-header panel">
            <div class="header-topbar">
              <div class="header-intro">
                <p class="eyebrow">Quant Screening Console</p>
                <h2 class="app-title">A股基本面筛选控制台</h2>
                <p class="app-desc">围绕市场概览、股票明细与自选追踪构建的轻量分析台。</p>
              </div>

              <div class="header-actions">
                <div class="header-metric">
                  <span class="metric-label">覆盖标的</span>
                  <strong class="metric-value">{{ marketSummary.total }}</strong>
                </div>
                <div class="header-metric">
                  <span class="metric-label">我的自选</span>
                  <strong class="metric-value">{{ watchlistCount }}</strong>
                </div>
                <button class="btn btn-sync" :disabled="importing" @click="importStocks">
                  {{ importing ? '同步中...' : '同步A股数据' }}
                </button>
              </div>
            </div>

             <div class="hero-grid">
               <section class="hero-copy">
                 <span class="hero-badge">智能筛选面板</span>
                 <h3 class="hero-title">将主列表、自选池与详情洞察整合到同一工作流中。</h3>
                 <p class="hero-text">
                   通过统一的卡片式布局、柔和层级和数据优先的排版，让日常股票扫描更清晰。
                 </p>
               </section>
             </div>
          </header>

            <section v-if="activeTab === 'market'" class="market-strip" style="margin-top: 32px;">
              <article class="summary-card panel">
                <span class="summary-label">上涨家数</span>
                <strong class="summary-value up">{{ marketSummary.rising }}</strong>
                <span class="summary-note">市场偏强时优先观察盈利与现金流双升公司</span>
              </article>
              <article class="summary-card panel">
                <span class="summary-label">下跌家数</span>
                <strong class="summary-value down">{{ marketSummary.falling }}</strong>
                <span class="summary-note">下行时更适合做质量筛选与风险回避</span>
              </article>
              <article class="summary-card panel">
                <span class="summary-label">平盘家数</span>
                <strong class="summary-value neutral">{{ marketSummary.flat }}</strong>
                <span class="summary-note">中性分布可结合行业过滤寻找结构性机会</span>
              </article>
              <article class="summary-card panel summary-card--accent">
                <span class="summary-label">自选池跟踪</span>
                <strong class="summary-stock">{{ watchlistCount }} 只股票</strong>
                <span class="summary-note">聚焦你持续观察的高价值标的</span>
              </article>
            </section>
            <section v-if="activeTab === 'market'" class="price-change-distribution tab-section">
              <div class="distribution-header">
                <h3 class="section-title">涨跌幅分布</h3>
              </div>
              <div class="distribution-chart">
                <div v-for="item in priceChangeDistribution" :key="item.label" class="distribution-bar">
                  <div class="distribution-bar-label">{{ item.label }}</div>
                  <div class="distribution-bar-fill" :style="{ width: item.width }"></div>
                  <div class="distribution-bar-value">{{ item.count }}</div>
                </div>
              </div>
            </section>

          <div v-if="importMessage" class="import-banner panel">{{ importMessage }}</div>
          <div v-if="error" class="error-bar panel">{{ error }}</div>

          <section class="content-tabs panel">
            <div class="content-header">
              <div>
                <p class="eyebrow">Data Workspace</p>
                <h3 class="section-title">股票池与自选池</h3>
              </div>

              <div class="tab-header">
                <button
                  class="tab-button"
                  :class="{ 'tab-button--active': activeTab === 'market' }"
                  @click="activeTab = 'market'"
                >
                  主列表
                </button>
                <button
                  class="tab-button"
                  :class="{ 'tab-button--active': activeTab === 'watchlist' }"
                  @click="activeTab = 'watchlist'"
                >
                  自选列表
                  <span class="tab-count">{{ watchlistCount }}</span>
                </button>
              </div>
            </div>

            <template v-if="activeTab === 'market'">
              <section class="filter-bar tab-section">
                <div class="filter-grid">
                  <div class="filter-group filter-group--wide">
                    <label class="filter-label">关键词</label>
          <input
            v-model="keyword"
            class="filter-input"
            placeholder="搜索代码 / 名称"
            @input="debouncedOnSearch"
          />
                  </div>

                   <div class="filter-group">
                     <label class="filter-label">交易所</label>
                     <select v-model="exchangeCode" class="filter-select" @change="debouncedOnSearch">
                       <option value="">全部交易所</option>
                       <option value="SSE">上交所</option>
                       <option value="SZSE">深交所</option>
                     </select>
                   </div>

                   <div class="filter-group">
                     <label class="filter-label">板块</label>
                     <select v-model="boardType" class="filter-select" @change="debouncedOnSearch">
                       <option value="">全部板块</option>
                       <option value="主板">主板</option>
                       <option value="创业板">创业板</option>
                       <option value="科创板">科创板</option>
                     </select>
                   </div>

                    <div class="filter-group filter-group--wide">
                      <label class="filter-label">行业</label>
                      <input
                        v-model="industry"
                        class="filter-input"
                        placeholder="输入行业关键词"
                        list="industry-list"
                        @input="debouncedOnSearch"
                      />
                      <datalist id="industry-list">
                        <option v-for="industry in suggestedIndustries" :key="industry" :value="industry" />
                      </datalist>
                    </div>
                </div>

                <div class="filter-actions">
                  <span class="stock-count">共 {{ totalElements }} 只股票</span>
                  <button class="btn btn-search" @click="onSearch">开始筛选</button>
                </div>
              </section>

              <main class="table-panel tab-section">
                <div class="table-toolbar">
                  <div>
                    <p class="eyebrow">Market List</p>
                    <h4 class="table-title">可排序股票明细</h4>
                  </div>
                  <span class="table-note">点击表头可切换排序，查看详情可打开右侧股票画像。</span>
                </div>

                <div class="table-wrapper">
                  <table class="stock-table">
                    <colgroup>
                      <col class="col-code" />
                      <col class="col-name" />
                      <col class="col-industry" />
                      <col class="col-board" />
                      <col class="col-price" />
                      <col class="col-change" />
                      <col class="col-action" />
                    </colgroup>
                    <thead>
                      <tr>
                        <th class="th-sortable" @click="onSort('code')">代码{{ sortIndicator('code') }}</th>
                        <th class="th-sortable" @click="onSort('name')">名称{{ sortIndicator('name') }}</th>
                        <th class="th-sortable" @click="onSort('industry')">行业{{ sortIndicator('industry') }}</th>
                        <th class="th-sortable" @click="onSort('boardType')">板块{{ sortIndicator('boardType') }}</th>
                        <th class="th-sortable th-right" @click="onSort('latestPrice')">
                          最新价{{ sortIndicator('latestPrice') }}
                        </th>
                        <th class="th-sortable th-right" @click="onSort('changePercent')">
                          涨跌幅{{ sortIndicator('changePercent') }}
                        </th>
                        <th >操作</th>
                      </tr>
                    </thead>
                     <tbody>
                       <tr v-if="loading">
                         <td colspan="6" class="td-center">加载中...</td>
                       </tr>
                       <tr v-else-if="filteredStocks.length === 0">
                         <td colspan="6" class="td-center">暂无数据，请先同步A股数据</td>
                       </tr>
                       <tr
                         v-for="stock in filteredStocks"
                         :key="stock.id"
                         class="stock-row"
                       >
                        <td class="td-code">{{ stock.symbol }}</td>
                        <td class="td-name">{{ stock.name }}</td>
                        <td class="td-industry">{{ stock.industry || '--' }}</td>
                        <td>
                          <span class="board-tag" :class="boardClass(stock.boardType)">
                            {{ stock.boardType || '--' }}
                          </span>
                        </td>
                        <td class="td-right">{{ formatPrice(stock.latestPrice) }}</td>
                        <td class="td-right">
                          <span class="change-chip" :class="changeClass(stock.changePercent)">
                            {{ formatPercent(stock.changePercent) }}
                          </span>
                        </td>
                         <td class="td-center">
                           <div class="row-actions">
                             <button class="btn btn-detail" @click="openDetail(stock)">查看详情</button>
                           </div>
                         </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </main>

              <footer class="pagination-bar tab-section">
                <div class="page-size-group">
                   <span>每页</span>
                   <select v-model.number="size" class="page-size-select" @change="onSizeChange">
                     <option :value="20">20</option>
                     <option :value="50">50</option>
                     <option :value="100">100</option>
                   </select>
                   <span>条</span>
                 </div>

                <div class="page-nav">
                  <button class="btn btn-page" :disabled="page === 0" @click="goToPage(0)">首页</button>
                  <button class="btn btn-page" :disabled="page === 0" @click="goToPage(page - 1)">上一页</button>
                  <span class="page-info">第 {{ page + 1 }} / {{ totalPagesDisplay }} 页</span>
                  <button class="btn btn-page" :disabled="page + 1 >= totalPages" @click="goToPage(page + 1)">
                    下一页
                  </button>
                  <button class="btn btn-page" :disabled="page + 1 >= totalPages" @click="goToPage(totalPages - 1)">
                    末页
                  </button>
                </div>
              </footer>
             </template>
             <section v-else class="watchlist-panel tab-section">
               <div class="watchlist-header">
                 <div>
                   <p class="eyebrow">My Watchlist</p>
                   <h2 class="watchlist-title">关注中的股票</h2>
                 </div>
                 <span class="watchlist-badge">已关注 {{ watchlistCount }} 只</span>
               </div>

               <div v-if="watchlist.length === 0" class="watchlist-empty">
                 还没有加入自选，切回选股列表点击“加入自选”即可收藏股票。
               </div>

               <div v-else class="watchlist-list">
                 <div v-for="stock in watchlist" :key="stock.id" class="watchlist-item">
                   <div class="watchlist-item-info">
                     <div class="watchlist-item-symbol">{{ stock.symbol }}</div>
                     <div class="watchlist-item-name">{{ stock.name }}</div>
                     <div class="watchlist-item-industry">{{ stock.industry || '--' }}</div>
                   </div>
                   <div class="watchlist-item-stats">
                     <div class="watchlist-item-price">{{ formatPrice(stock.latestPrice) }}</div>
                     <div class="watchlist-item-change" :class="changeClass(stock.changePercent)">
                       {{ formatPercent(stock.changePercent) }}
                     </div>
                   </div>
                 </div>
               </div>
             </section>
           </section>
        </section>

        <aside v-if="selectedStock" class="detail-panel panel">
          <div class="detail-header">
            <div>
              <p class="detail-caption">Stock Detail</p>
              <h2 class="detail-title">{{ selectedStock.name }}</h2>
              <p class="detail-symbol">{{ selectedStock.symbol }}</p>
            </div>
            <div class="detail-actions">
              <button
                class="btn btn-watchlist"
                :class="{ 'btn-watchlist--active': isInWatchlist(selectedStock.id) }"
                @click="toggleWatchlist(selectedStock)"
              >
                {{ isInWatchlist(selectedStock.id) ? '移除自选' : '加入自选' }}
              </button>
              <button class="btn btn-close" @click="closeDetail">关闭</button>
            </div>
          </div>

          <div v-if="detailLoading" class="detail-loading">详情加载中...</div>
          <div v-else-if="detailError" class="detail-error">{{ detailError }}</div>
          <template v-else>
            <div class="detail-hero">
              <div>
                <span class="detail-label">最新价</span>
                <strong class="detail-price">{{ formatPrice(selectedStock.latestPrice) }}</strong>
              </div>
              <div>
                <span class="detail-label">涨跌幅</span>
                <strong class="detail-change" :class="changeClass(selectedStock.changePercent)">
                  {{ formatPercent(selectedStock.changePercent) }}
                </strong>
              </div>
            </div>

            <div class="detail-grid">
              <article class="detail-item">
                <span class="detail-item-label">股票代码</span>
                <strong class="detail-item-value">{{ selectedStock.symbol }}</strong>
              </article>
              <article class="detail-item">
                <span class="detail-item-label">交易市场</span>
                <strong class="detail-item-value">{{ selectedStock.market || '--' }}</strong>
              </article>
              <article class="detail-item">
                <span class="detail-item-label">交易所</span>
                <strong class="detail-item-value">{{ selectedStock.exchangeCode || '--' }}</strong>
              </article>
              <article class="detail-item">
                <span class="detail-item-label">所属板块</span>
                <strong class="detail-item-value">{{ selectedStock.boardType || '--' }}</strong>
              </article>
              <article class="detail-item detail-item--full">
                <span class="detail-item-label">行业</span>
                <strong class="detail-item-value">{{ selectedStock.industry || '--' }}</strong>
              </article>
              <article class="detail-item detail-item--full">
                <span class="detail-item-label">上市日期</span>
                <strong class="detail-item-value">{{ formatDate(selectedStock.listDate) }}</strong>
              </article>
            </div>
          </template>
        </aside>
      </div>
    </div>
  </div>
</template>

<style scoped>
.app-shell {
  height: 100vh;
  padding: 24px;
}

.dashboard-frame {
  display: grid;
  
  grid-template-columns: 250px minmax(0, 1fr);
  max-width: 1780px;
  margin: 0 auto;
  align-items: start;
}

.dashboard-sidebar,
.panel {
  border: 1px solid var(--border-soft);
  border-radius: 28px;
  background: var(--bg-surface);
  box-shadow: var(--shadow-card);
  backdrop-filter: blur(18px);
}

.dashboard-sidebar {
  position: sticky;
  top: 24px;
  padding: 24px;
  
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 14px;
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: 18px;
  background: linear-gradient(135deg, var(--accent-primary), var(--accent-secondary));
  color: #fff;
  font-size: 1.25rem;
  font-weight: 800;
  box-shadow: 0 16px 30px rgba(255, 122, 89, 0.28);
}

.sidebar-title,
.section-title,
.watchlist-title,
.table-title,
.detail-title,
.sidebar-card-title {
  margin: 0;
  color: var(--text-primary);
}

.sidebar-nav {
  display: grid;
  gap: 10px;
  margin-top: 28px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 14px 16px;
  border: 1px solid transparent;
  border-radius: 18px;
  background: transparent;
  color: var(--text-secondary);
  font-weight: 600;
  cursor: pointer;
  transition: 0.22s ease;
}

.nav-item:hover,
.nav-item--active {
  border-color: rgba(255, 122, 89, 0.18);
  background: rgba(255, 122, 89, 0.08);
  color: var(--accent-deep);
}

.nav-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.72);
}

.sidebar-card {
  margin-top: 28px;
  padding: 18px;
  border-radius: 22px;
  background: linear-gradient(180deg, rgba(255, 244, 239, 0.95) 0%, rgba(255, 255, 255, 0.94) 100%);
  border: 1px solid rgba(255, 122, 89, 0.18);
}

.sidebar-card-desc,
.app-desc,
.hero-text,
.summary-note,
.table-note {
  margin: 0;
  color: var(--text-secondary);
}

.sidebar-card-desc {
  margin-top: 10px;
}

.app-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 24px;
}

.app-grid--detail {
  grid-template-columns: minmax(0, 1fr) 360px;
  align-items: start;
}

.app {
  min-width: 0;
}

.app-header {
  padding: 30px;
  overflow: hidden;
}

.header-topbar,
.content-header,
.detail-header,
.watchlist-header {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: flex-start;
}

.eyebrow,
.detail-caption,
.filter-label,
.summary-label,
.detail-label,
.detail-item-label,
.metric-label {
  margin: 0;
  color: var(--accent-primary);
  font-size: 0.74rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  font-weight: 700;
}

.header-intro {
  display: grid;
  gap: 10px;
}

.app-title {
  margin: 0;
  color: var(--text-primary);
  font-size: 2.2rem;
  line-height: 1.1;
}

.header-actions {
  display: flex;
  align-items: stretch;
  justify-content: flex-end;
  gap: 14px;
  flex-wrap: wrap;
}

.header-metric,
.summary-card,
.detail-item,
.watchlist-card,
.filter-bar,
.pagination-bar {
  border: 1px solid var(--border-soft);
  background: var(--bg-surface-strong);
  box-shadow: var(--shadow-soft);
}

.header-metric {
  min-width: 128px;
  padding: 14px 18px;
  border-radius: 18px;
}

.metric-value,
.summary-value,
.summary-stock,
.detail-price,
.detail-change,
.watchlist-price,
.hero-stock {
  display: block;
  color: var(--text-primary);
  font-weight: 800;
}

.metric-value {
  margin-top: 8px;
  font-size: 1.45rem;
}

.hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(280px, 0.9fr);
  gap: 20px;
  margin-top: 28px;
}

.hero-copy,
.hero-highlight {
  padding: 26px;
  border-radius: 24px;
}

.hero-copy {
  background: linear-gradient(135deg, rgba(255, 122, 89, 0.92) 0%, rgba(255, 155, 123, 0.9) 100%);
  color: #fff;
  box-shadow: 0 24px 50px rgba(255, 122, 89, 0.2);
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.18);
  font-size: 0.88rem;
  font-weight: 700;
}

.hero-title {
  margin: 18px 0 12px;
  font-size: 2rem;
  line-height: 1.22;
}

.hero-text {
  color: rgba(255, 255, 255, 0.86);
  max-width: 620px;
}

.hero-highlight {
  display: grid;
  align-content: space-between;
  background: linear-gradient(180deg, rgba(255, 244, 239, 0.92) 0%, rgba(255, 255, 255, 0.96) 100%);
  border: 1px solid rgba(255, 122, 89, 0.16);
}

.hero-stock {
  margin-top: 10px;
  font-size: 1.6rem;
}

.hero-symbol,
.summary-sub,
.watchlist-symbol,
.watchlist-price,
.detail-symbol,
.td-code,
.th-right,
.td-right {
  font-family: 'Consolas', 'SFMono-Regular', monospace;
}

.hero-symbol {
  color: var(--text-muted);
  font-size: 0.95rem;
  margin-top: 6px;
}

.hero-change {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: fit-content;
  min-width: 96px;
  margin-top: 18px;
  padding: 10px 14px;
  border-radius: 999px;
  font-weight: 800;
  background: rgba(255, 255, 255, 0.88);
}

.market-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
}

.summary-card {
  display: grid;
  gap: 10px;
  padding: 22px;
  border-radius: 24px;
}

.summary-card--accent {
  background: linear-gradient(180deg, rgba(255, 244, 239, 0.96) 0%, rgba(255, 255, 255, 1) 100%);
  border-color: rgba(255, 122, 89, 0.2);
}

.summary-value,
.summary-stock {
  font-size: 1.6rem;
}

.import-banner,
.error-bar {
  padding: 16px 18px;
  border-radius: 20px;
}

.import-banner {
  border: 1px solid rgba(22, 163, 74, 0.18);
  background: rgba(240, 253, 244, 0.94);
  color: #166534;
}

.error-bar {
  border: 1px solid rgba(220, 38, 38, 0.14);
  background: rgba(254, 242, 242, 0.96);
  color: #991b1b;
}

.content-tabs {
  padding: 24px;
}

.tab-header {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.tab-button {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  height: 46px;
  padding: 0 18px;
  border: 1px solid rgba(203, 213, 225, 0.92);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.8);
  color: var(--text-secondary);
  font-weight: 700;
  cursor: pointer;
  transition: 0.22s ease;
}

.tab-button:hover {
  border-color: rgba(255, 122, 89, 0.28);
  color: var(--accent-deep);
}

.tab-button--active {
  border-color: rgba(255, 122, 89, 0.2);
  background: linear-gradient(135deg, rgba(255, 122, 89, 0.94) 0%, rgba(255, 155, 123, 0.9) 100%);
  color: #fff7ed;
  box-shadow: 0 16px 28px rgba(255, 122, 89, 0.2);
}

.tab-count,
.watchlist-badge,
.stock-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  font-weight: 700;
}

.tab-count {
  min-width: 24px;
  height: 24px;
  padding: 0 8px;
  background: rgba(255, 255, 255, 0.18);
}

.tab-section {
  margin-top: 22px;
}

.filter-bar {
  display: grid;
  gap: 18px;
  padding: 22px;
  border-radius: 24px;
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.filter-group {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.filter-group--wide {
  grid-column: span 2;
}

.filter-input,
.filter-select,
.page-size-select,
.btn {
  height: 46px;
  border-radius: 16px;
  border: 1px solid rgba(203, 213, 225, 0.9);
  background: rgba(255, 255, 255, 0.96);
  color: var(--text-primary);
}

.filter-input,
.filter-select,
.page-size-select {
  width: 100%;
  padding: 0 14px;
  font-size: 0.94rem;
}

.filter-input:focus,
.filter-select:focus,
.page-size-select:focus {
  outline: none;
  border-color: rgba(255, 122, 89, 0.54);
  box-shadow: 0 0 0 4px rgba(255, 122, 89, 0.12);
}

.filter-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 16px;
  cursor: pointer;
  font-size: 0.94rem;
  font-weight: 700;
  transition: 0.22s ease;
}

.btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 12px 20px rgba(148, 163, 184, 0.18);
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-sync,
.btn-search,
.btn-detail,
.btn-watchlist--active {
  border-color: rgba(255, 122, 89, 0.2);
  background: linear-gradient(135deg, var(--accent-primary) 0%, var(--accent-secondary) 100%);
  color: #fff;
}

.btn-watchlist,
.btn-close,
.btn-page {
  background: rgba(255, 255, 255, 0.96);
}

.btn-watchlist {
  min-width: 102px;
  color: #9a5b2e;
  border-color: rgba(245, 184, 75, 0.34);
  background: rgba(255, 247, 237, 0.96);
}

.stock-count {
  min-height: 42px;
  padding: 0 16px;
  background: rgba(255, 244, 239, 0.96);
  color: var(--accent-deep);
}

.table-panel {
  overflow: hidden;
  border: 1px solid var(--border-soft);
  border-radius: 28px;
  background: var(--bg-surface-strong);
}

.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 22px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.92);
}

.table-title {
  margin: 6px 0 0;
  font-size: 1.18rem;
}

.table-note,
.page-size-group,
.page-nav,
.page-info,
.td-industry,
.watchlist-empty,
.watchlist-badge,
.detail-loading,
.detail-error {
  color: var(--text-secondary);
}

.table-wrapper {
  overflow-x: auto;
}

.stock-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  table-layout: fixed;
}

.col-code {
  width: 120px;
}

.col-name {
  width: 180px;
}

.col-industry {
  width: 220px;
}

.col-board {
  width: 120px;
}

.col-price,
.col-change {
  width: 120px;
}

.col-action {
  width: 220px;
}

.stock-table thead {
  background: #f8fafc;
}

.stock-table th,
.stock-table td {
  padding: 17px 18px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.96);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.stock-table th {
  color: #6b7280;
  font-size: 0.82rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.th-sortable {
  cursor: pointer;
}

.th-sortable:hover {
  color: var(--accent-deep);
}

.th-right,
.td-right {
  text-align: right;
}

.th-center,
.td-center {
  text-align: center;
}

.stock-table td {
  color: var(--text-primary);
  background: rgba(255, 255, 255, 0.96);
}

.stock-row {
  transition: 0.22s ease;
}

.stock-row:hover td,
.stock-row--active td {
  background: rgba(255, 244, 239, 0.82);
}

.stock-row--active td:first-child {
  box-shadow: inset 4px 0 0 var(--accent-primary);
}

.row-actions {
  display: flex;
  justify-content: center;
  gap: 10px;
  flex-wrap: wrap;
}

.td-code {
  color: var(--accent-deep);
  font-weight: 800;
}

.td-name,
.watchlist-name {
  color: var(--text-primary);
  font-weight: 700;
}

.change-chip,
.board-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 78px;
  padding: 6px 10px;
  border-radius: 999px;
  font-weight: 800;
}

.up {
  color: var(--danger);
}

.down {
  color: var(--success);
}

.neutral {
  color: var(--neutral);
}

.change-chip.up,
.hero-change.up {
  background: rgba(254, 226, 226, 0.92);
}

.change-chip.down,
.hero-change.down {
  background: rgba(220, 252, 231, 0.92);
}

.change-chip.neutral,
.hero-change.neutral {
  background: rgba(241, 245, 249, 0.96);
}

.board-main {
  background: rgba(219, 234, 254, 0.9);
  color: #2563eb;
}

.board-gem {
  background: rgba(255, 237, 213, 0.95);
  color: #ea580c;
}

.board-star {
  background: rgba(243, 232, 255, 0.96);
  color: #9333ea;
}

.pagination-bar,
.watchlist-panel,
.detail-panel {
  padding: 22px;
  border-radius: 24px;
}

.page-size-group,
.page-nav,
.detail-actions,
.watchlist-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.page-nav {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.page-info {
  min-width: 120px;
  text-align: center;
}

.watchlist-header {
  margin-bottom: 18px;
}

.watchlist-badge {
  min-height: 38px;
  padding: 0 16px;
  background: rgba(255, 244, 239, 0.96);
  color: var(--accent-deep);
}

.watchlist-empty {
  padding: 24px;
  border: 1px dashed rgba(203, 213, 225, 0.92);
  border-radius: 20px;
  text-align: center;
}

.watchlist-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 16px;
}

.watchlist-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px;
  border-radius: 22px;
}

.watchlist-main {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.watchlist-main:hover .watchlist-name {
  color: var(--accent-deep);
}

.watchlist-symbol,
.detail-symbol {
  margin: 0;
  color: var(--accent-primary);
}

.watchlist-name {
  margin: 8px 0 0;
  font-size: 1rem;
}

.watchlist-meta {
  flex-direction: column;
  align-items: flex-end;
}

.watchlist-price {
  margin: 0;
  font-size: 1.1rem;
}

.detail-panel {
  position: sticky;
  top: 24px;
}

.detail-title {
  margin: 6px 0 4px;
  font-size: 1.55rem;
}

.detail-loading,
.detail-error {
  margin-top: 18px;
  padding: 16px;
  border-radius: 16px;
  background: rgba(248, 250, 252, 0.98);
}

.detail-error {
  border: 1px solid rgba(220, 38, 38, 0.14);
}

.detail-hero {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 20px;
  padding: 18px;
  border-radius: 22px;
  background: linear-gradient(180deg, rgba(255, 244, 239, 0.96) 0%, rgba(255, 255, 255, 1) 100%);
  border: 1px solid rgba(255, 122, 89, 0.16);
}

.detail-price,
.detail-change {
  margin-top: 8px;
  font-size: 1.6rem;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 18px;
}

.detail-item {
  padding: 16px;
  border-radius: 18px;
}

.detail-item--full {
  grid-column: 1 / -1;
}

.detail-item-value {
  display: block;
  margin-top: 8px;
  color: var(--text-primary);
  font-size: 1rem;
  font-weight: 700;
}

@media (max-width: 1480px) {
  .dashboard-frame {
    grid-template-columns: minmax(0, 1fr);
  }

  .dashboard-sidebar {
    position: static;
  }
}

@media (max-width: 1280px) {
  .app-grid--detail,
  .hero-grid,
  .market-strip {
    grid-template-columns: minmax(0, 1fr);
  }

  .detail-panel {
    position: static;
  }

  .filter-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .filter-group--wide {
    grid-column: span 1;
  }
}

.price-change-distribution {
   margin-top: 24px;
}

.distribution-header {
   margin-bottom: 16px;
}

.distribution-chart {
   display: grid;
   gap: 12px;
}

.distribution-bar {
   display: flex;
   flex-direction: column;
   gap: 4px;
   align-items: stretch;
}

.distribution-bar-label {
   font-size: 0.88rem;
   color: var(--text-secondary);
   white-space: nowrap;
}

.distribution-bar-fill {
   height: 24px;
   background: linear-gradient(90deg, var(--accent-primary), var(--accent-secondary));
   border-radius: 4px;
   position: relative;
   overflow: hidden;
}

.distribution-bar-value {
   font-size: 0.88rem;
   font-weight: 600;
   color: var(--text-primary);
   min-width: 40px;
   text-align: right;
}

.watchlist-list {
   display: flex;
   flex-direction: column;
   gap: 12px;
   margin-top: 20px;
}

.watchlist-item {
   display: flex;
   justify-content: space-between;
   align-items: center;
   padding: 16px;
   background: rgba(255, 255, 255, 0.96);
   border-radius: 16px;
   border: 1px solid rgba(203, 213, 225, 0.9);
}

.watchlist-item-info {
   display: flex;
   flex-direction: column;
   gap: 4px;
   flex: 1;
}

.watchlist-item-symbol {
   font-family: 'Consolas', 'SFMono-Regular', monospace;
   font-weight: 600;
   color: var(--accent-primary);
   font-size: 0.94rem;
}

.watchlist-item-name {
   color: var(--text-primary);
   font-weight: 500;
   font-size: 0.88rem;
}

.watchlist-item-industry {
   color: var(--text-secondary);
   font-size: 0.82rem;
   font-style: italic;
}

.watchlist-item-stats {
   display: flex;
   flex-direction: column;
   align-items: flex-end;
   gap: 4px;
}

.watchlist-item-price {
   font-weight: 700;
   color: var(--text-primary);
   font-size: 0.94rem;
}

.watchlist-item-change {
   font-weight: 600;
   font-size: 0.88rem;
   min-width: 60px;
   text-align: right;
}

@media (max-width: 960px) {
   .app-shell {
     padding: 14px;
   }

  .dashboard-sidebar,
  .app-header,
  .content-tabs,
  .pagination-bar,
  .filter-bar,
  .detail-panel,
  .watchlist-panel {
    padding: 18px;
  }

  .header-topbar,
  .content-header,
  .watchlist-header,
  .detail-header,
  .filter-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .header-actions,
  .detail-actions,
  .page-nav {
    justify-content: stretch;
  }

  .tab-button,
  .btn,
  .watchlist-badge,
  .stock-count {
    width: 100%;
  }

  .filter-grid,
  .detail-grid,
  .detail-hero {
    grid-template-columns: 1fr;
  }

  .page-size-group,
  .page-nav,
  .row-actions {
    width: 100%;
    justify-content: space-between;
    flex-wrap: wrap;
  }
}
</style>
