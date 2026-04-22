<script setup lang="ts">
import type { Stock } from '@/api/stock'
import type { WatchlistItem } from '@/composables/useDashboard'

defineProps<{
  watchlist: WatchlistItem[]
  watchlistCount: number
  formatPrice: (value: number | null) => string
  formatPercent: (value: number | null) => string
  formatDate: (value: string | undefined) => string
  changeClass: (value: number | null) => string
}>()

const emit = defineEmits<{
  (e: 'open-detail', stock: Stock): void
  (e: 'remove', stockId: number): void
}>()
</script>

<template>
  <div class="page-stack watchlist-view">
    <section class="panel section-panel watchlist-intro">
      <div class="watchlist-header">
        <div>
          <p class="eyebrow">My Watchlist</p>
          <h3 class="section-title">自选追踪</h3>
          <p class="app-desc watchlist-subtitle">集中查看你正在跟踪的股票，并随时打开详情或移除。</p>
        </div>
        <span class="watchlist-badge watchlist-badge--accent">已关注 {{ watchlistCount }} 只</span>
      </div>
    </section>

    <section class="watchlist-panel panel">
      <div v-if="watchlist.length === 0" class="watchlist-empty">
        还没有加入自选，先去选股列表里挑几个值得持续跟踪的标的吧。
      </div>

      <div v-else class="watchlist-list">
        <article v-for="stock in watchlist" :key="stock.id" class="watchlist-item">
          <button class="watchlist-main" type="button" @click="emit('open-detail', stock)">
            <div class="watchlist-item-head">
              <div class="watchlist-item-symbol">{{ stock.symbol }}</div>
              <div class="watchlist-item-title">
                <div class="watchlist-item-name">{{ stock.name }}</div>
                <div class="watchlist-item-market">
                  {{ stock.market }}
                  <span v-if="stock.exchangeCode">· {{ stock.exchangeCode }}</span>
                </div>
              </div>
            </div>

            <div class="watchlist-item-body">
              <div class="watchlist-item-info">
                <span class="watchlist-meta-chip watchlist-meta-chip--industry">
                  {{ stock.industry || '未分类行业' }}
                </span>
                <span v-if="stock.boardType" class="watchlist-meta-chip watchlist-meta-chip--board">
                  {{ stock.boardType }}
                </span>
                <span class="watchlist-meta-chip">{{ formatDate(stock.addedAt) }}</span>
              </div>

              <div class="watchlist-concepts">
                <template v-if="stock.concepts.length > 0">
                  <span v-for="concept in stock.concepts.slice(0, 3)" :key="concept" class="watchlist-concept-chip">
                    {{ concept }}
                  </span>
                  <span v-if="stock.concepts.length > 3" class="watchlist-concept-chip watchlist-concept-chip--more">
                    +{{ stock.concepts.length - 3 }}
                  </span>
                </template>
                <span v-else class="watchlist-concept-empty">暂无概念</span>
              </div>

              <div class="watchlist-item-stats">
                <span class="watchlist-stat-label">最新价</span>
                <div class="watchlist-item-price">{{ formatPrice(stock.latestPrice) }}</div>
                <div class="watchlist-item-change" :class="changeClass(stock.changePercent)">
                  {{ formatPercent(stock.changePercent) }}
                </div>
              </div>
            </div>
          </button>

          <button class="btn mini watchlist-remove-btn" type="button" @click="emit('remove', stock.id)">移除</button>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
.watchlist-intro {
  border-color: #d8e2f3;
  background:
    radial-gradient(circle at 0% 0%, rgba(37, 99, 235, 0.08), transparent 36%),
    linear-gradient(165deg, #ffffff 0%, #f8fbff 100%);
}

.watchlist-subtitle {
  margin-top: 10px;
}

.watchlist-badge--accent {
  box-shadow: inset 0 0 0 1px rgba(29, 78, 216, 0.16);
}

.watchlist-panel {
  border-color: #dfe8f6;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
}

.watchlist-empty {
  border: 1px dashed #c4d3ec;
  border-radius: 14px;
  padding: 28px 24px;
  text-align: center;
  background: rgba(255, 255, 255, 0.72);
}

.watchlist-list {
  display: grid;
  gap: 12px;
}

.watchlist-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  border: 1px solid #dbe5f3;
  border-radius: 16px;
  padding: 14px;
  background: linear-gradient(160deg, #ffffff 0%, #f9fbff 100%);
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.watchlist-item:hover {
  transform: translateY(-1px);
  border-color: #b8c9e8;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
}

.watchlist-main {
  width: 100%;
  border: 0;
  background: transparent;
  display: grid;
  grid-template-columns: minmax(220px, 0.9fr) minmax(0, 1.8fr);
  gap: 14px;
  align-items: center;
  padding: 0;
  text-align: left;
  cursor: pointer;
}

.watchlist-item-head {
  display: grid;
  gap: 8px;
}

.watchlist-item-symbol {
  width: fit-content;
  min-width: 84px;
  height: 30px;
  padding: 0 10px;
  border: 1px solid #c8d8f2;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #1d4ed8;
  background: #eff6ff;
  font-size: 0.86rem;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.watchlist-item-title {
  display: grid;
  gap: 2px;
}

.watchlist-item-name {
  margin: 0;
  font-size: 1.06rem;
  color: #0f172a;
  font-weight: 700;
}

.watchlist-item-market {
  color: #64748b;
  font-size: 0.82rem;
  letter-spacing: 0.01em;
}

.watchlist-item-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px 16px;
  align-items: center;
}

.watchlist-item-info {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.watchlist-meta-chip {
  border: 1px solid transparent;
  background: #eff4fb;
  color: #475569;
}

.watchlist-meta-chip--industry {
  background: #ebf3ff;
  border-color: #d7e6ff;
}

.watchlist-meta-chip--board {
  background: #f5f3ff;
  border-color: #e7ddff;
  color: #5b21b6;
}

.watchlist-concepts {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.watchlist-concept-chip {
  border: 1px solid #d7e0f5;
  background: #f2f5fb;
  color: #334155;
}

.watchlist-concept-chip--more {
  border-color: #ced8ea;
  background: #e2e8f0;
  color: #1e293b;
}

.watchlist-item-stats {
  min-width: 108px;
  border: 1px solid #dce7f5;
  border-radius: 12px;
  padding: 8px 10px;
  background: #f8fbff;
  justify-items: end;
}

.watchlist-stat-label {
  color: #64748b;
  font-size: 0.75rem;
  letter-spacing: 0.02em;
}

.watchlist-item-price {
  color: #0f172a;
  font-size: 1.25rem;
  font-weight: 800;
  line-height: 1.15;
}

.watchlist-item-change {
  width: fit-content;
  min-height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 0.92rem;
  font-weight: 700;
}

.watchlist-item-change.up {
  background: #fef1f2;
}

.watchlist-item-change.down {
  background: #ecfdf5;
}

.watchlist-item-change.neutral {
  background: #f1f5f9;
}

.watchlist-remove-btn {
  border-color: #d5deeb;
  background: #f8fafc;
  color: #334155;
}

.watchlist-remove-btn:hover {
  border-color: #fecaca;
  background: #fff1f2;
  color: #b91c1c;
}

@media (max-width: 1200px) {
  .watchlist-main {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .watchlist-item-body {
    grid-template-columns: 1fr;
  }

  .watchlist-item-stats {
    justify-items: start;
  }
}

@media (max-width: 900px) {
  .watchlist-item {
    grid-template-columns: 1fr;
  }

  .watchlist-remove-btn {
    width: 100%;
    height: 36px;
  }
}
</style>
