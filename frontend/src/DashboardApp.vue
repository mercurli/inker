<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import {
  dashboardHash,
  normalizeDashboardPage,
  useDashboard,
  type DashboardPage,
} from "@/composables/useDashboard";
import MarketOverviewView from "@/views/MarketOverviewView.vue";
import StockDetailView from "@/views/StockDetailView.vue";
import StockListView from "@/views/StockListView.vue";
import WatchlistView from "@/views/WatchlistView.vue";

type AppPage = DashboardPage | "detail";

function parseRoute(hash: string): { page: AppPage; detailId: number | null } {
  const normalizedHash = hash.replace(/^#/, "");

  if (normalizedHash.startsWith("stock/")) {
    const detailId = Number(normalizedHash.slice("stock/".length));
    const isValid = Number.isInteger(detailId) && detailId > 0;

    return {
      page: isValid ? "detail" : "stocks",
      detailId: isValid ? detailId : null,
    };
  }

  return {
    page: normalizeDashboardPage(hash),
    detailId: null,
  };
}

const initialRoute = parseRoute(window.location.hash);
const currentPage = ref<AppPage>(initialRoute.page);
const dashboard = useDashboard();

const pageMeta = computed(() => {
  if (currentPage.value === "detail") {
    return {
      title: dashboard.selectedStock.value?.name ?? "个股详情",
      description: "聚焦查看单只股票的基础信息与最近一段时间的日K走势。",
    };
  }

  if (currentPage.value === "stocks") {
    return {
      title: "选股列表",
      description: "筛选、排序，点击代码名称查看详情，把研究流程拆成独立页面后会更聚焦。",
    };
  }

  if (currentPage.value === "watchlist") {
    return {
      title: "自选追踪",
      description: "专门查看你已收藏的股票，减少在主列表和自选之间来回切换。",
    };
  }

  return {
    title: "市场总览",
    description: "先看市场，再看个股，再看自选，三个页面各自承担单一任务。",
  };
});

async function handleHashChange() {
  const route = parseRoute(window.location.hash);
  currentPage.value = route.page;

  if (route.page === "detail" && route.detailId) {
    await dashboard.loadDetailById(route.detailId);
    return;
  }

  dashboard.closeDetail();
}

function navigate(page: DashboardPage) {
  const nextHash = dashboardHash(page);

  if (window.location.hash === nextHash) {
    currentPage.value = page;
    dashboard.closeDetail();
    return;
  }

  window.location.hash = nextHash;
}

function openDetailPage(stock: { id: number }) {
  const nextHash = `#stock/${stock.id}`;

  if (window.location.hash === nextHash) {
    currentPage.value = "detail";
    void dashboard.loadDetailById(stock.id);
    return;
  }

  window.location.hash = nextHash;
}

onMounted(() => {
  window.addEventListener("hashchange", handleHashChange);
  void dashboard.init().then(handleHashChange);
});

onUnmounted(() => {
  window.removeEventListener("hashchange", handleHashChange);
});
</script>

<template>
  <div class="app-shell">
    <div class="dashboard-frame">
      <aside class="dashboard-sidebar panel">
        <div class="sidebar-brand">
          <span class="brand-mark">研</span>
        </div>

        <nav class="sidebar-nav">
          <button
            class="nav-item"
            :class="{ 'nav-item--active': currentPage === 'market' }"
            type="button"
            @click="navigate('market')"
            title="市场总览"
          >
            <span class="nav-icon">市场</span>
          </button>
          <button
            class="nav-item"
            :class="{ 'nav-item--active': currentPage === 'stocks' || currentPage === 'detail' }"
            type="button"
            @click="navigate('stocks')"
            title="选股列表"
          >
            <span class="nav-icon">选股</span>
          </button>
          <button
            class="nav-item"
            :class="{ 'nav-item--active': currentPage === 'watchlist' }"
            type="button"
            @click="navigate('watchlist')"
            title="自选追踪"
          >
            <span class="nav-icon">自选</span>
          </button>
        </nav>
      </aside>

      <section class="app">
        <!-- <header class="topbar panel">
          <div class="plan-chip">
            <span class="plan-icon">◈</span>
            <span class="plan-name">Personal Plan</span>
          </div>
        </header> -->

        <div class="content-shell panel">
          <div class="content-head">
            <div>
              <h1 class="content-title">{{ pageMeta.title }}</h1>
              <p class="content-subtitle">{{ pageMeta.description }}</p>
            </div>
            <div class="header-actions">
              <div class="header-metric">
                <span class="metric-label">覆盖标的</span>
                <strong class="metric-value">{{ dashboard.marketSummary.value.total }}</strong>
              </div>
              <div class="header-metric">
                <span class="metric-label">我的自选</span>
                <strong class="metric-value">{{ dashboard.watchlistCount.value }}</strong>
              </div>
              <button class="btn btn-sync" :disabled="dashboard.importing.value" @click="dashboard.importStocks">
                {{ dashboard.importing.value ? '同步中...' : '同步A股数据' }}
              </button>
            </div>
          </div>

          <div v-if="dashboard.importMessage.value" class="import-banner panel">
            {{ dashboard.importMessage.value }}
          </div>
          <div v-if="dashboard.error.value" class="error-bar panel">
            {{ dashboard.error.value }}
          </div>

          <MarketOverviewView
            v-if="currentPage === 'market'"
            :market-summary="dashboard.marketSummary.value"
            :watchlist-count="dashboard.watchlistCount.value"
            :strongest-stock-label="dashboard.strongestStockLabel.value"
            :price-change-distribution="dashboard.priceChangeDistribution.value"
          />

          <StockListView
            v-else-if="currentPage === 'stocks'"
            :stocks="dashboard.stocks.value"
            :loading="dashboard.loading.value"
            :keyword="dashboard.keyword.value"
            :exchange-code="dashboard.exchangeCode.value"
            :board-type="dashboard.boardType.value"
            :industry="dashboard.industry.value"
            :suggested-industries="dashboard.suggestedIndustries.value"
            :total-elements="dashboard.totalElements.value"
            :page="dashboard.page.value"
            :total-pages="dashboard.totalPages.value"
            :total-pages-display="dashboard.totalPagesDisplay.value"
            :size="dashboard.size.value"
            :watchlist-count="dashboard.watchlistCount.value"
            :selected-stock-id="dashboard.selectedStockId.value"
            :is-in-watchlist="dashboard.isInWatchlist"
            :sort-indicator="dashboard.sortIndicator"
            :format-price="dashboard.formatPrice"
            :format-percent="dashboard.formatPercent"
            :change-class="dashboard.changeClass"
            :board-class="dashboard.boardClass"
            @update:keyword="dashboard.keyword.value = $event"
            @update:exchange-code="dashboard.exchangeCode.value = $event"
            @update:board-type="dashboard.boardType.value = $event"
            @update:industry="dashboard.industry.value = $event"
            @search="dashboard.onSearch"
            @debounced-search="dashboard.debouncedOnSearch"
            @sort="dashboard.onSort"
            @open-detail="openDetailPage"
            @toggle-watchlist="dashboard.toggleWatchlist"
            @page-change="dashboard.goToPage"
          />

          <WatchlistView
            v-else-if="currentPage === 'watchlist'"
            :watchlist="dashboard.watchlist.value"
            :watchlist-count="dashboard.watchlistCount.value"
            :format-price="dashboard.formatPrice"
            :format-percent="dashboard.formatPercent"
            :format-date="dashboard.formatDate"
            :change-class="dashboard.changeClass"
            @open-detail="openDetailPage"
            @remove="dashboard.removeFromWatchlist"
          />

          <StockDetailView
            v-else
            :selected-stock="dashboard.selectedStock.value"
            :detail-loading="dashboard.detailLoading.value"
            :detail-error="dashboard.detailError.value"
            :daily-k-line="dashboard.dailyKLine.value"
            :k-line-loading="dashboard.kLineLoading.value"
            :k-line-error="dashboard.kLineError.value"
            :is-in-watchlist="dashboard.isInWatchlist"
            :format-price="dashboard.formatPrice"
            :format-percent="dashboard.formatPercent"
            :format-date="dashboard.formatDate"
            :change-class="dashboard.changeClass"
            @toggle-watchlist="dashboard.toggleWatchlist"
            @back="navigate('stocks')"
          />
        </div>
      </section>
    </div>
  </div>
</template>

<style>
.dashboard-frame {
  display: grid;
  grid-template-columns: 100px 1fr;
  /* grid-template-rows: 80px 1fr; */
  grid-template-areas:
    "sidebar header"
    "sidebar main";
  height: 100vh;
  overflow: hidden;
}

.panel {
  background: #ffffff;
}

.dashboard-sidebar {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24px;
  padding: 18px 12px;
  height: 100vh;
  border-right: 1px solid #e4e7ec;
}

.sidebar-brand {
  width: 100%;
  display: flex;
  justify-content: center;
  padding-bottom: 14px;
}

.brand-mark {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #0f172a;
  color: #ffffff;
  font-size: 1rem;
  font-weight: 700;
}

.sidebar-nav {
  display: grid;
  gap: 10px;
}

.nav-item {
  width: 54px;
  height: 54px;
  border: 1px solid transparent;
  border-radius: 14px;
  background: #ffffff;
  cursor: pointer;
}

.nav-item--active {
  background: linear-gradient(180deg, #1da1f2 0%, #1c64f2 100%);
}

.nav-icon {
  color: #6b7280;
  font-weight: 700;
  font-size: 0.86rem;
  letter-spacing: 0.02em;
}

.nav-item--active .nav-icon {
  color: #ffffff;
}

.app {
  min-width: 0;
  min-height: 0;
  display: grid;
  overflow-y: auto;
}

.topbar {
  height: 80px;
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr) 260px;
  align-items: center;
  gap: 16px;
  padding: 14px 20px;
  border-bottom: 1px solid #e4e7ec;
}

.plan-chip {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-weight: 600;
}

.plan-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, #6d5efc, #5a67f2);
  color: #fff;
}

.search-wrap {
  width: 100%;
}

.top-search {
  width: 100%;
  height: 44px;
  border: 1px solid #eef0f3;
  border-radius: 12px;
  padding: 0 14px;
  background: #f8fafc;
}

.account-chip {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
}

.avatar {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: #dbeafe;
  color: #1d4ed8;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
}

.account-name {
  margin: 0;
  font-size: 0.92rem;
  font-weight: 700;
}

.account-role {
  margin: 2px 0 0;
  font-size: 0.78rem;
  color: #6b7280;
}

.content-shell {
  padding: 20px;
}

.content-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.content-title,
.section-title,
.table-title,
.detail-title {
  margin: 0;
  color: #0f172a;
}

.content-subtitle,
.app-desc,
.summary-note,
.table-note,
.watchlist-empty,
.page-info,
.td-industry,
.watchlist-item-industry {
  color: #64748b;
}

.content-subtitle {
  margin: 8px 0 0;
}

.eyebrow,
.filter-label,
.summary-label,
.metric-label,
.detail-item-label,
.detail-caption,
.detail-label {
  margin: 0;
  color: #2563eb;
  font-size: 0.72rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  font-weight: 700;
}

.page-stack {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  width: 100%;
  gap: 16px;
  margin-top: 16px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.header-metric,
.summary-card,
.detail-item,
.filter-bar,
.pagination-bar,
.section-panel,
.watchlist-panel,
.table-panel {
  border: 1px solid #e7ebf1;
  border-radius: 16px;
  background: #ffffff;
}

.header-metric {
  padding: 8px 12px;
}

.metric-value {
  display: block;
  margin-top: 4px;
}

.btn {
  height: 38px;
  padding: 0 14px;
  border: 1px solid #d7deea;
  border-radius: 10px;
  background: #ffffff;
  cursor: pointer;
}

.btn-sync,
.btn-search,
.btn-detail,
.btn-watchlist--active,
.range-btn--active {
  border-color: #1c64f2;
  background: linear-gradient(180deg, #2e90fa 0%, #1c64f2 100%);
  color: #ffffff;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.import-banner,
.error-bar {
  margin-top: 12px;
  padding: 10px 12px;
}

.import-banner {
  border: 1px solid #bbf7d0;
  background: #f0fdf4;
  color: #166534;
}

.error-bar {
  border: 1px solid #fecaca;
  background: #fef2f2;
  color: #991b1b;
}

.section-panel,
.pagination-bar,
.watchlist-panel,
.detail-panel {
  padding: 16px;
}

.filter-bar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: 10px;
  padding: 12px;
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 8px;
}

.filter-group {
  display: grid;
  gap: 4px;
}

.filter-group--wide {
  grid-column: span 2;
}

.filter-input,
.filter-select,
.page-size-select {
  width: 100%;
  height: 34px;
  border: 1px solid #dce2ea;
  border-radius: 8px;
  padding: 0 10px;
}

.filter-actions,
.content-header,
.watchlist-header,
.detail-page-topbar,
.detail-page-hero,
.kline-toolbar,
.kline-axis,
.kline-summary-strip,
.detail-hero-metrics,
.page-size-group,
.page-nav,
.row-actions,
.detail-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.filter-actions {
  flex-wrap: nowrap;
  white-space: nowrap;
}

.stock-count,
.watchlist-badge {
  display: inline-flex;
  align-items: center;
  height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  background: #eff6ff;
  color: #1d4ed8;
  font-weight: 700;
}

.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  flex-wrap: wrap;
}

.pagination-nav {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.page-circle {
  width: 32px;
  height: 32px;
  border: 1px solid #e5e7eb;
  border-radius: 999px;
  background: #f8fafc;
  color: #6b7280;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.page-circle:hover:not(:disabled) {
  border-color: #d1d5db;
  background: #f3f4f6;
}

.page-number--active {
  border-color: #111827;
  color: #111827;
  background: #ffffff;
}

.page-arrow {
  font-size: 1.15rem;
  line-height: 1;
}

.page-ellipsis {
  color: #6b7280;
  font-weight: 600;
  padding: 0 2px;
}

.page-circle:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.table-panel {
  overflow: hidden;
}

.table-toolbar {
  padding: 14px 16px;
  border-bottom: 1px solid #eef2f7;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.table-wrapper {
  overflow-x: auto;
}

.stock-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

.col-code {
  width: 120px;
}

.col-index {
  width: 76px;
}

.col-name {
  width: 100px;
}

.col-industry {
  width: 170px;
}

.col-concepts {
  width: 220px;
}

.col-board {
  width: 110px;
}

.col-price,
.col-change {
  width: 110px;
}

.col-action {
  width: 96px;
}

.stock-table th,
.stock-table td {
  padding: 12px;
  border-bottom: 1px solid #edf1f5;
  white-space: nowrap;
}

.stock-table th {
  font-size: 0.76rem;
  color: #64748b;
  text-transform: uppercase;
}

.th-sortable {
  cursor: pointer;
  user-select: none;
}

.sort-head {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.sort-head--right {
  justify-content: flex-end;
  width: 100%;
}

.sort-indicator {
  display: inline-block;
  width: 1.2em;
  text-align: center;
}

.th-right,
.td-right {
  text-align: right;
}

.th-center,
.td-center {
  text-align: center;
}

.stock-row--active td {
  background: #f8fbff;
}

.td-code,
.detail-symbol,
.watchlist-item-symbol {
  font-family: "Consolas", "SFMono-Regular", monospace;
}

.concept-chip-group {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 4px;
}

.concept-chip {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  background: #eef2ff;
  color: #3730a3;
  font-size: 0.74rem;
  line-height: 1;
}

.concept-chip--more {
  background: #e2e8f0;
  color: #334155;
}

.td-code {
  color: #1d4ed8;
  font-weight: 700;
}

.stock-link {
  border: 0;
  background: transparent;
  padding: 0;
  font: inherit;
  cursor: pointer;
}

.stock-link:hover {
  text-decoration: underline;
}

.stock-link:focus-visible {
  outline: 2px solid #93c5fd;
  outline-offset: 2px;
}

.stock-link--code {
  color: #1d4ed8;
  font-weight: 700;
}

.stock-link--name {
  color: #0f172a;
  font-weight: 600;
}

.btn-watchlist-icon {
  width: 38px;
  padding: 0;
  font-size: 1rem;
  line-height: 1;
}

.btn-watchlist-icon:not(.btn-watchlist--active) {
  border-color: #fecdd3;
  background: #fff1f2;
  color: #f43f5e;
}

.row-actions {
  justify-content: center;
}

.change-chip,
.board-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 68px;
  border-radius: 999px;
  padding: 3px 8px;
  font-weight: 700;
}

.up {
  color: #dc2626;
}

.down {
  color: #16a34a;
}

.neutral {
  color: #64748b;
}

.change-chip.up {
  background: #fef2f2;
}

.change-chip.down {
  background: #f0fdf4;
}

.change-chip.neutral {
  background: #f8fafc;
}

.board-main {
  background: #eff6ff;
  color: #1d4ed8;
}

.board-gem {
  background: #fff7ed;
  color: #ea580c;
}

.board-star {
  background: #f5f3ff;
  color: #7c3aed;
}

.market-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.summary-card {
  padding: 14px;
  display: grid;
  gap: 6px;
}

.summary-value,
.summary-stock,
.detail-price,
.detail-change {
  font-size: 1.3rem;
  font-weight: 800;
}

.summary-card--accent {
  background: #f8fbff;
}

.distribution-chart {
  display: flex;
  align-items: stretch;
  gap: 10px;
}

.distribution-bar {
  display: grid;
  gap: 8px;
  padding: 8px 4px;
  flex: 1 1 0;
  min-width: 0;
}

.distribution-bar-track {
  min-height: 122px;
  position: relative;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  border-radius: 8px;
}

.distribution-bar-fill {
  width: 70%;
  min-height: 8px;
  border-radius: 8px 8px 4px 4px;
  background: linear-gradient(180deg, #9ca3af 0%, #6b7280 100%);
}

.distribution-bar-value,
.distribution-bar-label {
  text-align: center;
}

.distribution-bar-label {
  white-space: nowrap;
}

.distribution-bar-value {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  white-space: nowrap;
  font-weight: 600;
  line-height: 1;
}

.distribution-bar--up .distribution-bar-fill {
  background: linear-gradient(180deg, #ff2d55 0%, #dc143c 100%);
}

.distribution-bar--up .distribution-bar-value {
  color: #b21136;
}

.distribution-bar--down .distribution-bar-fill {
  background: linear-gradient(180deg, #10a48d 0%, #0a7f6f 100%);
}

.distribution-bar--down .distribution-bar-value {
  color: #0f6f62;
}

.distribution-bar--neutral .distribution-bar-fill {
  background: linear-gradient(180deg, #9ca3af 0%, #6b7280 100%);
}

.distribution-bar--neutral .distribution-bar-value {
  color: #4b5563;
}

.distribution-balance {
  margin-top: 14px;
  display: grid;
  gap: 8px;
}

.distribution-balance-track {
  height: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.distribution-balance-segment {
  flex-basis: 0;
  height: 100%;
}

.distribution-balance-segment--up {
  background: linear-gradient(90deg, #ef233c 0%, #d90429 100%);
  border-radius: 4px 0 0 4px;
}

.distribution-balance-segment--down {
  background: linear-gradient(90deg, #0f8f7f 0%, #0b6f63 100%);
  border-radius: 0 4px 4px 0;
}

.distribution-balance-gap {
  width: 30px;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.distribution-balance-gap span {
  display: block;
  width: 12px;
  height: 100%;
  background: #8b94a3;
  border-radius: 2px;
  transform: skewX(-28deg);
}

.distribution-balance-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 0.96rem;
  font-weight: 600;
}

.distribution-balance-text--up {
  color: #d90429;
}

.distribution-balance-text--down {
  color: #0b6f63;
}

.market-distribution-panel {
  width: 50%;
}

.watchlist-view .watchlist-list {
  display: grid;
  gap: 10px;
}

.watchlist-view .watchlist-item {
  border: 1px solid #e8edf3;
  border-radius: 12px;
  padding: 10px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.watchlist-view .watchlist-main {
  flex: 1;
  border: 0;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  cursor: pointer;
}

.watchlist-view .watchlist-item-head,
.watchlist-view .watchlist-item-info,
.watchlist-view .watchlist-item-stats,
.kline-card {
  display: grid;
  gap: 6px;
}

.watchlist-view .watchlist-meta-chip {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  background: #f1f5f9;
  font-size: 0.78rem;
}

.watchlist-view .watchlist-meta-chip--industry {
  background: #eff6ff;
  color: #1e40af;
}

.watchlist-view .watchlist-concepts {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.watchlist-view .watchlist-concept-chip {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  background: #eef2ff;
  color: #3730a3;
  font-size: 0.78rem;
}

.watchlist-view .watchlist-concept-chip--more {
  background: #e2e8f0;
  color: #334155;
}

.watchlist-view .watchlist-concept-empty {
  color: #94a3b8;
  font-size: 0.78rem;
}

.detail-page-title {
  margin: 6px 0;
}

.detail-grid,
.detail-grid--page,
.detail-hero,
.kline-summary-strip {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.detail-grid--page,
.kline-summary-strip {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.detail-item {
  padding: 10px;
}

.detail-item--full {
  grid-column: 1 / -1;
}

.kline-svg-shell {
  overflow-x: auto;
  border: 1px solid #e7edf5;
  border-radius: 14px;
  padding: 10px;
  background: #fcfdff;
}

.kline-svg {
  width: 100%;
  min-width: 720px;
  height: 300px;
}

.kline-guide {
  stroke: #dbe3ef;
}

.kline-y-label {
  fill: #64748b;
  font-size: 11px;
}

.kline-hit-area {
  fill: transparent;
}

.kline-hover-guide {
  stroke: #94a3b8;
  stroke-dasharray: 4 3;
}

.kline-hover-label,
.kline-extreme-label {
  fill: #334155;
  font-size: 11px;
}

.kline-extreme-dot {
  fill: #1e293b;
}

.kline-wick-svg--up,
.kline-body-svg--up {
  stroke: #dc2626;
  fill: #dc2626;
}

.kline-wick-svg--down,
.kline-body-svg--down {
  stroke: #16a34a;
  fill: #16a34a;
}

.kline-wick-svg--neutral,
.kline-body-svg--neutral {
  stroke: #64748b;
  fill: #64748b;
}

.btn.mini {
  height: 30px;
  padding: 0 10px;
  font-size: 0.76rem;
}

@media (max-width: 1200px) {
  .topbar {
    grid-template-columns: 1fr;
  }

  .account-chip {
    justify-content: flex-start;
  }

  .filter-bar {
    grid-template-columns: 1fr;
    align-items: stretch;
  }

  .market-strip,
  .filter-grid,
  .detail-grid,
  .detail-grid--page,
  .kline-summary-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .filter-group--wide {
    grid-column: span 1;
  }
}

@media (max-width: 900px) {
  .app-shell {
    padding: 12px;
  }

  .dashboard-frame {
    grid-template-columns: 1fr;
    grid-template-rows: auto 1fr;
  }

  .dashboard-sidebar {
    height: auto;
    flex-direction: row;
    justify-content: space-between;
  }

  .sidebar-brand {
    border-bottom: 0;
    width: auto;
    padding-bottom: 0;
  }

  .sidebar-nav {
    display: flex;
  }

  .content-head,
  .header-actions,
  .content-header,
  .watchlist-header,
  .detail-page-topbar,
  .detail-page-hero,
  .kline-toolbar,
  .filter-actions,
  .page-nav,
  .watchlist-view .watchlist-main,
  .watchlist-view .watchlist-item {
    flex-direction: column;
    align-items: stretch;
  }

  .pagination-bar {
    align-items: stretch;
  }

  .pagination-nav {
    justify-content: center;
  }

  .market-strip,
  .filter-grid,
  .detail-grid,
  .detail-grid--page,
  .kline-summary-strip {
    grid-template-columns: 1fr;
  }

  .market-distribution-panel {
    width: 100%;
  }
}
</style>
