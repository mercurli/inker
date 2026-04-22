<script setup lang="ts">
import { computed } from 'vue'
import type { Stock, StockQueryParams } from '@/api/stock'

const props = defineProps<{
  stocks: Stock[]
  loading: boolean
  keyword: string
  exchangeCode: '' | 'SSE' | 'SZSE'
  boardType: string
  industry: string
  suggestedIndustries: string[]
  totalElements: number
  page: number
  totalPages: number
  totalPagesDisplay: number
  size: number
  watchlistCount: number
  selectedStockId: number | null
  isInWatchlist: (stockId: number) => boolean
  sortIndicator: (field: StockQueryParams['sortBy']) => string
  formatPrice: (value: number | null) => string
  formatPercent: (value: number | null) => string
  changeClass: (value: number | null) => string
  boardClass: (value: string | undefined) => string
}>()

const emit = defineEmits<{
  (e: 'update:keyword', value: string): void
  (e: 'update:exchangeCode', value: '' | 'SSE' | 'SZSE'): void
  (e: 'update:boardType', value: string): void
  (e: 'update:industry', value: string): void
  (e: 'search'): void
  (e: 'debounced-search'): void
  (e: 'sort', field: StockQueryParams['sortBy']): void
  (e: 'open-detail', stock: Stock): void
  (e: 'toggle-watchlist', stock: Stock): void
  (e: 'page-change', page: number): void
}>()

const pageCount = computed(() => Math.max(props.totalPages, 1))

const pageItems = computed(() => {
  const current = Math.min(Math.max(props.page + 1, 1), pageCount.value)
  const pages = new Set<number>()

  if (pageCount.value <= 7) {
    for (let i = 1; i <= pageCount.value; i += 1) {
      pages.add(i)
    }
  } else {
    pages.add(1)
    pages.add(pageCount.value)

    if (current <= 4) {
      for (let i = 2; i <= 5; i += 1) {
        pages.add(i)
      }
    } else if (current >= pageCount.value - 3) {
      for (let i = pageCount.value - 4; i <= pageCount.value - 1; i += 1) {
        pages.add(i)
      }
    } else {
      pages.add(current - 1)
      pages.add(current)
      pages.add(current + 1)
    }
  }

  const sortedPages = [...pages].filter((value) => value >= 1 && value <= pageCount.value).sort((a, b) => a - b)
  const items: Array<number | 'ellipsis'> = []

  sortedPages.forEach((value, index) => {
    const prev = sortedPages[index - 1]
    if (prev !== undefined && value - prev > 1) {
      items.push('ellipsis')
    }
    items.push(value)
  })

  return items
})
</script>

<template>
  <div class="page-stack">
    <section class="panel section-panel">
      <div class="content-header">
        <div>
          <p class="eyebrow">Stock Screener</p>
          <h3 class="section-title">选股列表</h3>
          <p class="app-desc">筛选、排序，点击代码名称查看详情，并把值得跟踪的标的加入自选池。</p>
        </div>
        <span class="watchlist-badge">自选 {{ watchlistCount }} 只</span>
      </div>

      <section class="filter-bar">
        <div class="filter-grid">
          <div class="filter-group filter-group--wide">
            <label class="filter-label">关键词</label>
            <input
              :value="keyword"
              class="filter-input"
              placeholder="搜索代码 / 名称"
              @input="emit('update:keyword', ($event.target as HTMLInputElement).value)"
              @input.capture="emit('debounced-search')"
            />
          </div>

          <div class="filter-group">
            <label class="filter-label">交易所</label>
            <select
              :value="exchangeCode"
              class="filter-select"
              @change="emit('update:exchangeCode', ($event.target as HTMLSelectElement).value as '' | 'SSE' | 'SZSE')"
              @change.capture="emit('debounced-search')"
            >
              <option value="">全部交易所</option>
              <option value="SSE">上交所</option>
              <option value="SZSE">深交所</option>
            </select>
          </div>

          <div class="filter-group">
            <label class="filter-label">板块</label>
            <select
              :value="boardType"
              class="filter-select"
              @change="emit('update:boardType', ($event.target as HTMLSelectElement).value)"
              @change.capture="emit('debounced-search')"
            >
              <option value="">全部板块</option>
              <option value="主板">主板</option>
              <option value="创业板">创业板</option>
              <option value="科创板">科创板</option>
            </select>
          </div>

          <div class="filter-group filter-group--wide">
            <label class="filter-label">行业</label>
            <input
              :value="industry"
              class="filter-input"
              placeholder="输入行业关键词"
              list="industry-list"
              @input="emit('update:industry', ($event.target as HTMLInputElement).value)"
              @input.capture="emit('debounced-search')"
            />
            <datalist id="industry-list">
              <option v-for="item in suggestedIndustries" :key="item" :value="item" />
            </datalist>
          </div>
        </div>

        <div class="filter-actions">
          <span class="stock-count">共 {{ totalElements }} 只股票</span>
          <button class="btn btn-search" type="button" @click="emit('search')">开始筛选</button>
        </div>
      </section>
    </section>

    <main class="table-panel">
      <div class="table-toolbar">
        <div>
          <p class="eyebrow">Market List</p>
          <h4 class="table-title">股票列表</h4>
        </div>
        <span class="table-note">点击表头切换排序，点击代码名称会打开右侧个股面板。</span>
      </div>

      <div class="table-wrapper">
        <table class="stock-table">
          <colgroup>
            <col class="col-index" />
            <col class="col-code" />
            <col class="col-name" />
            <col class="col-price" />
            <col class="col-change" />
            <col class="col-industry" />
            <col class="col-concepts" />
            <col class="col-board" />
            <col class="col-action" />
          </colgroup>
          <thead>
            <tr>
              <th class="th-center">序号</th>
              <th class="th-sortable" @click="emit('sort', 'code')">
                <span class="sort-head">
                  <span>代码</span>
                  <span class="sort-indicator">{{ sortIndicator('code') || ' ' }}</span>
                </span>
              </th>
              <th class="th-sortable" @click="emit('sort', 'name')" >
                <span class="sort-head">
                  <span>名称</span>
                  <span class="sort-indicator">{{ sortIndicator('name') || ' ' }}</span>
                </span>
              </th>
              <th class="th-sortable th-right" @click="emit('sort', 'latestPrice')">
                <span class="sort-head sort-head--right">
                  <span>最新价</span>
                  <span class="sort-indicator">{{ sortIndicator('latestPrice') || ' ' }}</span>
                </span>
              </th>
              <th class="th-sortable th-right" @click="emit('sort', 'changePercent')">
                <span class="sort-head sort-head--right">
                  <span>涨跌幅</span>
                  <span class="sort-indicator">{{ sortIndicator('changePercent') || ' ' }}</span>
                </span>
              </th>
              <th class="th-sortable" @click="emit('sort', 'industry')">
                <span class="sort-head">
                  <span>行业</span>
                  <span class="sort-indicator">{{ sortIndicator('industry') || ' ' }}</span>
                </span>
              </th>
              <th class="th-center">概念</th>
              <th class="th-sortable" @click="emit('sort', 'boardType')">
                <span class="sort-head">
                  <span>板块</span>
                  <span class="sort-indicator">{{ sortIndicator('boardType') || ' ' }}</span>
                </span>
              </th>
              <th class="th-center">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="9" class="td-center">加载中...</td>
            </tr>
            <tr v-else-if="stocks.length === 0">
              <td colspan="9" class="td-center">暂无数据，请先同步 A 股数据。</td>
            </tr>
            <tr
              v-for="(stock, index) in stocks"
              :key="stock.id"
              class="stock-row"
              :class="{ 'stock-row--active': selectedStockId === stock.id }"
            >
              <td class="td-center">{{ page * size + index + 1 }}</td>
              <td class="td-code">
                <button class="stock-link stock-link--code" type="button" @click="emit('open-detail', stock)">
                  {{ stock.symbol }}
                </button>
              </td>
              <td class="td-name">
                <button class="stock-link stock-link--name" type="button" @click="emit('open-detail', stock)">
                  {{ stock.name }}
                </button>
              </td>
              <td class="td-right">{{ formatPrice(stock.latestPrice) }}</td>
              <td class="td-right">
                <span class="change-chip" :class="changeClass(stock.changePercent)">
                  {{ formatPercent(stock.changePercent) }}
                </span>
              </td>
              <td class="td-industry">{{ stock.industry || '--' }}</td>
              <td class="td-center td-concepts">
                <div v-if="stock.concepts.length > 0" class="concept-chip-group">
                  <span v-for="concept in stock.concepts.slice(0, 3)" :key="concept" class="concept-chip">
                    {{ concept }}
                  </span>
                  <span v-if="stock.concepts.length > 3" class="concept-chip concept-chip--more">
                    +{{ stock.concepts.length - 3 }}
                  </span>
                </div>
                <span v-else>--</span>
              </td>
              <td>
                <span class="board-tag" :class="boardClass(stock.boardType)">
                  {{ stock.boardType || '--' }}
                </span>
              </td>
              <td class="td-center">
                <div class="row-actions">
                  <button
                    class="btn btn-watchlist btn-watchlist-icon"
                    :class="{ 'btn-watchlist--active': isInWatchlist(stock.id) }"
                    type="button"
                    :aria-label="isInWatchlist(stock.id) ? '移除自选' : '加入自选'"
                    :title="isInWatchlist(stock.id) ? '移除自选' : '加入自选'"
                    @click="emit('toggle-watchlist', stock)"
                  >
                    <span v-if="isInWatchlist(stock.id)" aria-hidden="true">&#10084;</span>
                    <span v-else aria-hidden="true">&#9825;</span>
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </main>

    <footer class="pagination-bar panel">
      <div class="pagination-nav">
        <button
          class="page-circle page-arrow"
          :disabled="page === 0 || totalPages <= 1"
          type="button"
          aria-label="上一页"
          @click="emit('page-change', page - 1)"
        >
          ‹
        </button>

        <template v-for="(item, index) in pageItems" :key="`${item}-${index}`">
          <span v-if="item === 'ellipsis'" class="page-ellipsis">...</span>
          <button
            v-else
            class="page-circle page-number"
            :class="{ 'page-number--active': page + 1 === item }"
            type="button"
            @click="emit('page-change', item - 1)"
          >
            {{ item }}
          </button>
        </template>

        <button
          class="page-circle page-arrow"
          :disabled="page + 1 >= totalPages || totalPages <= 1"
          type="button"
          aria-label="下一页"
          @click="emit('page-change', page + 1)"
        >
          ›
        </button>
      </div>
    </footer>
  </div>
</template>
