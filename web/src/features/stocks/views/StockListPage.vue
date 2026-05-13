<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStocksList } from '@/features/stocks/composables/useStocksList'
import PanelCard from '@/shared/components/layout/PanelCard.vue'
import SectionHeader from '@/shared/components/layout/SectionHeader.vue'
import PaginationBar from '@/shared/components/interaction/PaginationBar.vue'
import SearchableSelect from '@/shared/components/interaction/SearchableSelect.vue'
import PriceChangeChip from '@/shared/components/display/PriceChangeChip.vue'
import BoardTag from '@/shared/components/display/BoardTag.vue'
import ConceptChipGroup from '@/shared/components/display/ConceptChipGroup.vue'
import WatchlistToggleButton from '@/shared/components/interaction/WatchlistToggleButton.vue'
import LoadingState from '@/shared/components/feedback/LoadingState.vue'
import EmptyState from '@/shared/components/feedback/EmptyState.vue'
import { resolveApiAssetUrl, type Stock, type StockQueryParams } from '@/api/stock'

const router = useRouter()
const route = useRoute()
const store = useStocksList()
const selectedGroupStock = ref<Stock | null>(null)
const showGroupDialog = ref(false)
const selectedGroupId = ref<number | null>(null)
const groupSaving = ref(false)
const LAST_SELECTED_GROUP_STORAGE_KEY = 'inker.stocks.lastSelectedWatchlistGroupId'

function conceptFromQuery(value: unknown) {
  if (Array.isArray(value)) {
    return String(value[0] ?? '').trim()
  }

  return typeof value === 'string' ? value.trim() : ''
}

watch(
  () => route.query.concept,
  (value) => {
    const nextConcept = conceptFromQuery(value)

    if (store.concept.value === nextConcept) {
      return
    }

    store.concept.value = nextConcept
    store.onSearch()
  },
  { immediate: true }
)

function openDetail(stock: Stock) {
  void router.push(`/stocks/${stock.id}`)
}

function onSort(field: StockQueryParams['sortBy']) {
  void store.onSort(field)
}

function onConceptChange(value: string) {
  const nextConcept = value.trim()
  const nextQuery = { ...route.query }

  if (nextConcept) {
    nextQuery.concept = nextConcept
  } else {
    delete nextQuery.concept
  }

  if (conceptFromQuery(route.query.concept) === nextConcept) {
    store.onSearch()
    return
  }

  void router.replace({ name: 'stocks', query: nextQuery })
}

function primaryOrderedConcepts(stock: Stock) {
  const concepts = stock.concepts.filter((concept) => concept.trim())

  if (!stock.primaryConcept) {
    return concepts
  }

  return [
    stock.primaryConcept,
    ...concepts.filter((concept) => concept !== stock.primaryConcept)
  ]
}

function logoUrl(stock: Stock) {
  return resolveApiAssetUrl(stock.logo)
}

function logoFallback(stock: Stock) {
  return stock.name.trim().slice(0, 1) || stock.symbol.slice(0, 1) || '-'
}

function readLastSelectedGroupId() {
  try {
    const rawGroupId = localStorage.getItem(LAST_SELECTED_GROUP_STORAGE_KEY)
    const groupId = rawGroupId == null ? Number.NaN : Number.parseInt(rawGroupId, 10)

    if (!Number.isFinite(groupId)) {
      return null
    }

    return store.watchlistGroups.value.some((group) => group.id === groupId) ? groupId : null
  } catch {
    return null
  }
}

function rememberLastSelectedGroupId(groupId: number) {
  try {
    localStorage.setItem(LAST_SELECTED_GROUP_STORAGE_KEY, String(groupId))
  } catch {
    // Ignore storage failures; the add-to-group action should still succeed.
  }
}

function openGroupDialog(stock: Stock) {
  selectedGroupStock.value = stock
  selectedGroupId.value = readLastSelectedGroupId()
  showGroupDialog.value = true
}

function closeGroupDialog() {
  if (groupSaving.value) {
    return
  }

  showGroupDialog.value = false
  selectedGroupStock.value = null
  selectedGroupId.value = null
}

async function confirmAddToGroup() {
  const stock = selectedGroupStock.value
  const groupId = selectedGroupId.value

  if (!stock || groupId == null || groupSaving.value) {
    return
  }

  groupSaving.value = true

  try {
    await store.addToGroup(stock.id, groupId)
    rememberLastSelectedGroupId(groupId)
    showGroupDialog.value = false
    selectedGroupStock.value = null
    selectedGroupId.value = null
  } finally {
    groupSaving.value = false
  }
}
</script>

<template>
  <div class="page-stack">
    <div class="filter filter--stocks-sticky">
      <div class="filter-fields">
        <div class="filter-group filter-group--wide">
          <label class="field-label">关键词</label>
          <input :value="store.keyword.value" class="field-control" placeholder="搜索代码 / 名称"
            @input="store.keyword.value = ($event.target as HTMLInputElement).value"
            @input.capture="store.debouncedOnSearch" />
        </div>

        <div class="filter-group">
          <label class="field-label">交易所</label>
          <select :value="store.exchangeCode.value" class="field-control"
            @change="store.exchangeCode.value = ($event.target as HTMLSelectElement).value as '' | 'SSE' | 'SZSE'"
            @change.capture="store.debouncedOnSearch">
            <option value="">全部交易所</option>
            <option value="SSE">上交所</option>
            <option value="SZSE">深交所</option>
          </select>
        </div>

        <div class="filter-group">
          <label class="field-label">板块</label>
          <select :value="store.boardType.value" class="field-control"
            @change="store.boardType.value = ($event.target as HTMLSelectElement).value"
            @change.capture="store.debouncedOnSearch">
            <option value="">全部板块</option>
            <option value="主板">主板</option>
            <option value="创业板">创业板</option>
            <option value="科创板">科创板</option>
          </select>
        </div>

        <div class="filter-group filter-group--wide">
          <label class="field-label">行业</label>
          <SearchableSelect
            v-model="store.industry.value"
            :options="store.industryOptions.value"
            placeholder="输入行业关键词"
            :allow-custom="true"
            @change="store.debouncedOnSearch"
          />
        </div>

        <div class="filter-group filter-group--wide">
          <label class="field-label">概念</label>
          <SearchableSelect
            v-model="store.concept.value"
            :options="store.conceptOptions.value"
            placeholder="选择概念"
            :allow-custom="false"
            @change="onConceptChange"
          />
        </div>
      </div>
      <button class="btn btn-primary" type="button" @click="store.onSearch">开始筛选</button>
    </div>
    <PanelCard as="main" class="table-panel">
      <div class="table-toolbar">
        <SectionHeader eyebrow="Market List" title="股票列表" subtitle="点击表头切换排序，点击代码名称打开个股详情。">
          <template #extra>
            <span class="stock-count">{{ store.totalElements.value }} 只</span>
          </template>
        </SectionHeader>
      </div>

      <div class="table-wrapper" :class="{ 'table-wrapper--loading': store.loading.value }">
        <table class="stock-table">
          <colgroup>
            <col class="col-index" />
            <col class="col-stock" />
            <col class="col-price" />
            <col class="col-change" />
            <col class="col-change" />
            <col class="col-amount" />
            <col class="col-turnover" />
            <col class="col-market-value" />
            <col class="col-pe" />
            <col class="col-industry" />
            <col class="col-concepts" />
            <col class="col-board" />
            <col class="col-action" />
          </colgroup>
          <thead>
            <tr>
              <th class="th-center col-cell-index">序号</th>
              <th class="th-sortable col-cell-stock" @click="onSort('code')">名称 / 代码{{ store.sortIndicator('code') }}</th>
              <th class="th-sortable col-cell-price" @click="onSort('latestPrice')">最新价{{ store.sortIndicator('latestPrice')
              }}</th>
              <th class="th-sortable col-cell-change" @click="onSort('changePercent')">涨跌幅{{
                store.sortIndicator('changePercent') }}</th>
              <th class="th-sortable col-cell-change col-cell-five-day" @click="onSort('fiveDayChangePercent')">5日涨跌幅{{
                store.sortIndicator('fiveDayChangePercent') }}</th>
              <th class="th-sortable col-cell-amount" @click="onSort('amount')">成交额{{ store.sortIndicator('amount') }}</th>
              <th class="th-sortable col-cell-turnover" @click="onSort('turnoverRate')">换手{{ store.sortIndicator('turnoverRate') }}</th>
              <th class="th-sortable col-cell-market-value" @click="onSort('totalMarketValue')">总市值{{
                store.sortIndicator('totalMarketValue') }}</th>
              <th class="th-sortable col-cell-pe" @click="onSort('dynamicPeRatio')">市盈率{{ store.sortIndicator('dynamicPeRatio') }}</th>
              <th class="th-sortable col-cell-industry" @click="onSort('industry')">行业{{ store.sortIndicator('industry') }}</th>
              <th class="th-center col-cell-concepts">概念</th>
              <th class="th-sortable th-center col-cell-board" @click="onSort('boardType')">板块{{ store.sortIndicator('boardType') }}</th>
              <th class="th-center col-cell-action">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="store.loading.value && store.stocks.value.length === 0">
              <td colspan="13" class="td-center">
                <LoadingState text="加载中..." />
              </td>
            </tr>
            <tr v-else-if="store.stocks.value.length === 0">
              <td colspan="13" class="td-center">
                <EmptyState message="暂无数据，请先同步 A 股数据。" />
              </td>
            </tr>
            <tr v-for="(stock, index) in store.stocks.value" :key="stock.id"
              :class="{ 'stock-row--active': store.selectedStockId.value === stock.id }">
              <td class="td-center col-cell-index">{{ store.page.value * store.size.value + index + 1 }}</td>
              <td class="td-stock col-cell-stock">
                <button class="stock-link stock-link--stacked" type="button" @click="openDetail(stock)">
                  <span class="stock-link__main">
                    <img v-if="logoUrl(stock)" class="stock-logo" :src="logoUrl(stock) ?? ''" :alt="`${stock.name} logo`" loading="lazy" />
                    <span v-else class="stock-logo stock-logo--fallback">{{ logoFallback(stock) }}</span>
                    <span class="stock-link__text">
                      <span class="stock-link__name">{{ stock.name }}</span>
                      <span class="stock-link__symbol">{{ stock.symbol }}</span>
                    </span>
                  </span>
                </button>
              </td>
              <td class="col-cell-price">{{ store.formatPrice(stock.latestPrice) }}</td>
              <td class="col-cell-change">
                <PriceChangeChip :tone="store.changeClass(stock.changePercent)"
                  :value="store.formatPercent(stock.changePercent)" />
              </td>
              <td class="col-cell-change col-cell-five-day">
                <PriceChangeChip :tone="store.changeClass(stock.fiveDayChangePercent)"
                  :value="store.formatPercent(stock.fiveDayChangePercent)" />
              </td>
              <td class="col-cell-amount">{{ store.formatAmount(stock.amount) }}</td>
              <td class="col-cell-turnover">{{ store.formatTurnoverRate(stock.turnoverRate) }}</td>
              <td class="col-cell-market-value">{{ store.formatMarketValue(stock.totalMarketValue) }}</td>
              <td class="col-cell-pe">{{ store.formatPeRatio(stock.dynamicPeRatio) }}</td>
              <td class="col-cell-industry">{{ stock.industry || '--' }}</td>
              <td class="td-center col-cell-concepts">
                <ConceptChipGroup :concepts="primaryOrderedConcepts(stock)" :primary-concept="stock.primaryConcept" :max="1" />
              </td>
              <td class="td-center col-cell-board">
                <BoardTag :label="stock.boardType || '--'" :variant="store.boardClass(stock.boardType)" />
              </td>
              <td class="td-center col-cell-action">
                <div class="stock-action-buttons">
                  <WatchlistToggleButton :active="store.isInWatchlist(stock.id)" @toggle="store.toggleWatchlist(stock)" />
                  <button class="btn btn-icon stock-group-edit-btn" type="button" aria-label="编辑分组" title="编辑分组"
                    @click="openGroupDialog(stock)">
                    <svg viewBox="0 0 24 24" aria-hidden="true">
                      <path d="M9 6h11" />
                      <path d="M9 12h11" />
                      <path d="M9 18h7" />
                      <path d="M4 6h1" />
                      <path d="M4 12h1" />
                      <path d="M4 18h1" />
                      <path d="M19 16v6" />
                      <path d="M16 19h6" />
                    </svg>
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>

        <div v-if="store.loading.value && store.stocks.value.length > 0" class="table-loading-overlay" aria-live="polite">
          <span class="table-loading-spinner" aria-hidden="true"></span>
          <span>列表加载中...</span>
        </div>
      </div>
    </PanelCard>

    <PaginationBar
      v-if="store.totalPages.value > 1"
      :page="store.page.value"
      :total-pages="store.totalPages.value"
      @change="store.goToPage"
    />

    <div v-if="showGroupDialog" class="dialog-backdrop" @click.self="closeGroupDialog">
      <div class="dialog-card stock-group-dialog">
        <div class="stock-group-dialog__header">
          <div>
            <h3 class="dialog-title">编辑分组</h3>
            <p v-if="selectedGroupStock" class="stock-group-dialog__stock">
              {{ selectedGroupStock.name }} {{ selectedGroupStock.symbol }}
            </p>
          </div>
          <button class="btn btn-icon" type="button" aria-label="关闭" title="关闭" :disabled="groupSaving"
            @click="closeGroupDialog">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M18 6L6 18" />
              <path d="M6 6L18 18" />
            </svg>
          </button>
        </div>

        <div v-if="store.watchlistGroups.value.length > 0" class="stock-group-list">
          <button v-for="group in store.watchlistGroups.value" :key="group.id" class="stock-group-option"
            :class="{ 'stock-group-option--selected': selectedGroupId === group.id }" type="button"
            :disabled="groupSaving" @click="selectedGroupId = group.id">
            <span class="stock-group-option__name">{{ group.name }}</span>
            <span class="stock-group-option__count">{{ group.stockCount }} 只</span>
          </button>
        </div>
        <p v-else class="stock-group-empty">暂无自选分组，请先到自选页创建分组。</p>

        <div class="dialog-actions">
          <button class="btn" type="button" :disabled="groupSaving" @click="closeGroupDialog">取消</button>
          <button class="btn btn-primary" type="button"
            :disabled="selectedGroupId == null || store.watchlistGroups.value.length === 0 || groupSaving"
            @click="confirmAddToGroup">
            {{ groupSaving ? '添加中...' : '确认' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.table-wrapper {
  overflow-x: hidden;
}

.stock-table th,
.stock-table td {
  overflow: hidden;
  padding: var(--space-2);
  text-overflow: ellipsis;
  vertical-align: middle;
}

.stock-table th {
  letter-spacing: 0;
}

.col-index {
  width: 48px;
}

.col-stock {
  width: 142px;
}

.col-price {
  width: 76px;
}

.col-change {
  width: 86px;
}

.col-amount {
  width: 98px;
}

.col-turnover {
  width: 64px;
}

.col-market-value {
  width: 94px;
}

.col-pe {
  width: 76px;
}

.col-industry {
  width: 90px;
}

.col-concepts {
  width: 126px;
}

.col-board {
  width: 72px;
}

.col-action {
  width: 86px;
}

.td-stock {
  min-width: 0;
}

.stock-link,
.stock-link__main,
.stock-link__text,
.stock-link__name,
.stock-link__symbol {
  max-width: 100%;
}

.stock-link {
  width: 100%;
  min-width: 0;
  text-align: left;
}

.stock-link__name,
.stock-link__symbol {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.col-cell-concepts :deep(.concept-chip-group) {
  min-width: 0;
  flex-wrap: nowrap;
  overflow: hidden;
}

.col-cell-concepts :deep(.concept-chip) {
  min-width: 0;
  max-width: 76px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.col-cell-concepts :deep(.concept-chip--more) {
  flex: 0 0 auto;
  max-width: none;
}

.stock-table :deep(.price-chip) {
  max-width: 100%;
  min-width: 0;
  justify-content: center;
  padding: 0 var(--space-1);
}

.col-cell-board :deep(.board-tag),
.stock-action-buttons,
.stock-action-buttons :deep(.watchlist-toggle),
.stock-group-edit-btn {
  flex-shrink: 0;
}

.col-cell-board :deep(.board-tag) {
  min-width: 56px;
  min-height: 22px;
  padding: 0 var(--space-1);
  font-size: var(--font-size-xs);
}

.stock-action-buttons {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-1);
}

.stock-group-edit-btn {
  color: var(--text-secondary);
}

.stock-group-edit-btn:hover {
  color: var(--primary-600);
}

.stock-group-dialog {
  width: min(460px, calc(100vw - 32px));
}

.stock-group-dialog__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.stock-group-dialog__stock {
  margin: var(--space-1) 0 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.stock-group-list {
  display: grid;
  gap: var(--space-2);
  max-height: min(360px, calc(100vh - 260px));
  overflow-y: auto;
}

.stock-group-option {
  min-height: 44px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
  color: var(--text-primary);
  padding: var(--space-2) var(--space-3);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  cursor: pointer;
  transition: var(--transition-fast);
}

.stock-group-option:hover {
  border-color: var(--primary-500);
  background: var(--bg-soft);
}

.stock-group-option--selected {
  border-color: var(--primary-500);
  background: #f4f9ff;
  color: var(--primary-600);
}

.stock-group-option__name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: var(--font-weight-semibold);
}

.stock-group-option__count {
  flex: 0 0 auto;
  color: var(--text-muted);
  font-size: var(--font-size-xs);
}

.stock-group-empty {
  margin: 0;
  padding: var(--space-4);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-sm);
  background: var(--bg-soft);
  color: var(--text-secondary);
  text-align: center;
  font-size: var(--font-size-sm);
}

@media (max-width: 1199px) {
  .col-concepts,
  .col-pe,
  .col-board,
  .col-cell-concepts,
  .col-cell-pe,
  .col-cell-board {
    display: none;
  }

  .col-stock {
    width: 18%;
  }

  .col-index {
    width: 5%;
  }

  .col-price {
    width: 8%;
  }

  .col-change {
    width: 9%;
  }

  .col-amount,
  .col-market-value {
    width: 10%;
  }

  .col-turnover {
    width: 7%;
  }

  .col-industry {
    width: 11%;
  }

  .col-action {
    width: 13%;
  }
}

@media (max-width: 768px) {
  .col-amount,
  .col-turnover,
  .col-market-value,
  .col-industry,
  .col-cell-amount,
  .col-cell-turnover,
  .col-cell-market-value,
  .col-cell-industry {
    display: none;
  }

  .table-panel {
    padding: var(--space-3);
  }

  .stock-table th,
  .stock-table td {
    padding: var(--space-2) var(--space-1);
    font-size: var(--font-size-xs);
  }

  .col-index {
    width: 7%;
  }

  .col-stock {
    width: 26%;
  }

  .col-price {
    width: 12%;
  }

  .col-change {
    width: 16%;
  }

  .col-action {
    width: 23%;
  }

  .stock-logo,
  .stock-logo--fallback {
    width: 28px;
    height: 28px;
  }

  .stock-action-buttons {
    gap: 2px;
  }

  .stock-group-edit-btn,
  .stock-action-buttons :deep(.watchlist-toggle) {
    width: 30px;
    min-width: 30px;
    height: 30px;
  }

  .stock-group-edit-btn svg {
    width: 14px;
    height: 14px;
  }
}
</style>
