<script setup lang="ts">
import { watch } from 'vue'
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
import type { Stock, StockQueryParams } from '@/api/stock'

const router = useRouter()
const route = useRoute()
const store = useStocksList()

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
              <th class="th-sortable" @click="onSort('code')">代码{{ store.sortIndicator('code') }}</th>
              <th class="th-sortable" @click="onSort('name')">名称{{ store.sortIndicator('name') }}</th>
              <th class="th-sortable th-right" @click="onSort('latestPrice')">最新价{{ store.sortIndicator('latestPrice')
              }}</th>
              <th class="th-sortable th-right" @click="onSort('changePercent')">涨跌幅{{
                store.sortIndicator('changePercent') }}</th>
              <th class="th-sortable" @click="onSort('industry')">行业{{ store.sortIndicator('industry') }}</th>
              <th class="th-center">概念</th>
              <th class="th-sortable" @click="onSort('boardType')">板块{{ store.sortIndicator('boardType') }}</th>
              <th class="th-center">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="store.loading.value">
              <td colspan="9" class="td-center">
                <LoadingState text="加载中..." />
              </td>
            </tr>
            <tr v-else-if="store.stocks.value.length === 0">
              <td colspan="9" class="td-center">
                <EmptyState message="暂无数据，请先同步 A 股数据。" />
              </td>
            </tr>
            <tr v-for="(stock, index) in store.stocks.value" :key="stock.id"
              :class="{ 'stock-row--active': store.selectedStockId.value === stock.id }">
              <td class="td-center">{{ store.page.value * store.size.value + index + 1 }}</td>
              <td class="td-code">
                <button class="stock-link" type="button" @click="openDetail(stock)">{{ stock.symbol }}</button>
              </td>
              <td>
                <button class="stock-link stock-link--name" type="button" @click="openDetail(stock)">{{ stock.name
                }}</button>
              </td>
              <td class="td-right">{{ store.formatPrice(stock.latestPrice) }}</td>
              <td class="td-right">
                <PriceChangeChip :tone="store.changeClass(stock.changePercent)"
                  :value="store.formatPercent(stock.changePercent)" />
              </td>
              <td>{{ stock.industry || '--' }}</td>
              <td class="td-center">
                <ConceptChipGroup :concepts="stock.concepts" :max="3" />
              </td>
              <td>
                <BoardTag :label="stock.boardType || '--'" :variant="store.boardClass(stock.boardType)" />
              </td>
              <td class="td-center">
                <WatchlistToggleButton :active="store.isInWatchlist(stock.id)" @toggle="store.toggleWatchlist(stock)" />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </PanelCard>

    <PaginationBar
      v-if="store.totalPages.value > 1"
      :page="store.page.value"
      :total-pages="store.totalPages.value"
      @change="store.goToPage"
    />
  </div>
</template>
