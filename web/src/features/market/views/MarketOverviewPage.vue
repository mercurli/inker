<script setup lang="ts">
import { useMarketSummary } from '@/features/market/composables/useMarketSummary'
import { useMarketHotList } from '@/features/market/composables/useMarketHotList'
import PanelCard from '@/shared/components/layout/PanelCard.vue'
import SectionHeader from '@/shared/components/layout/SectionHeader.vue'
import MetricCard from '@/shared/components/display/MetricCard.vue'
import PriceChangeChip from '@/shared/components/display/PriceChangeChip.vue'
import LoadingState from '@/shared/components/feedback/LoadingState.vue'
import EmptyState from '@/shared/components/feedback/EmptyState.vue'
import { formatPercent, toneByPercent } from '@/shared/lib/formatters'
import type { TrendTone } from '@/shared/types/common'

const { marketSummary, priceChangeDistribution } = useMarketSummary()
const {
  loading: hotListLoading,
  error: hotListError,
  list: hotList,
  conceptDigest,
  popularityDigest,
  notFoundMessage: hotListNotFoundMessage,
  resolvingCode,
  openStockDetailByCode
} = useMarketHotList()

function priceChangeTone(value: number | null): TrendTone {
  return toneByPercent(value)
}

function formatRateWan(value: number | null) {
  if (value == null) {
    return '--万'
  }

  const wanValue = value / 10000

  if (wanValue >= 1000) {
    return `${wanValue.toFixed(0)}万`
  }

  if (wanValue >= 100) {
    return `${wanValue.toFixed(1)}万`
  }

  return `${wanValue.toFixed(2)}万`
}
</script>

<template>
  <div class="page-stack">
    <div class="market-overview-layout">
      <div class="market-overview-main">
        <section class="market-strip">
          <MetricCard label="上涨家数" :value="marketSummary.rising" note="偏强市场里更容易观察到趋势延续。" tone="up" />
          <MetricCard label="下跌家数" :value="marketSummary.falling" note="下行阶段更适合优先做风险控制。" tone="down" />
          <MetricCard label="平盘家数" :value="marketSummary.flat" note="中性分布时更要细看行业结构。" tone="neutral" />
        </section>
        <PanelCard>
          <SectionHeader eyebrow="Distribution" title="涨跌幅分布" />

          <div class="distribution-chart">
            <div
              v-for="item in priceChangeDistribution"
              :key="item.label"
              class="distribution-bar"
              :class="`distribution-bar--${item.tone}`"
            >
              <div class="distribution-bar-track">
                <div class="distribution-bar-value" :style="{ bottom: `calc(${item.width} + 6px)` }">
                  {{ item.count }}
                </div>
                <div class="distribution-bar-fill" :style="{ height: item.width }"></div>
              </div>
              <div class="distribution-bar-label">{{ item.label }}</div>
            </div>
          </div>
        </PanelCard>
      </div>

      <PanelCard class="hotlist-panel">
        <SectionHeader eyebrow="Hot List" title="今日热榜" >
          <template #extra>
            <span class="hotlist-badge">TOP {{ hotList.length }}</span>
          </template>
        </SectionHeader>

        <LoadingState v-if="hotListLoading" text="热榜加载中..." />
        <div v-else-if="hotListError" class="state-block state-block--error">{{ hotListError }}</div>
        <EmptyState v-else-if="hotList.length === 0" message="暂无热榜数据。" />
        <div v-else class="hotlist-content">
          <p v-if="hotListNotFoundMessage" class="hotlist-inline-error">{{ hotListNotFoundMessage }}</p>
          <div class="hotlist-digest">
            <div class="hotlist-digest-block">
              <p class="hotlist-digest-title">概念聚焦</p>
              <div class="hotlist-chip-group">
                <span v-if="conceptDigest.length === 0" class="hotlist-chip hotlist-chip--muted">暂无概念标签</span>
                <span v-for="item in conceptDigest" :key="item.label" class="hotlist-chip">
                  {{ item.label }} · {{ item.count }}
                </span>
              </div>
            </div>
            <div class="hotlist-digest-block">
              <p class="hotlist-digest-title">热度标签</p>
              <div class="hotlist-chip-group">
                <span v-if="popularityDigest.length === 0" class="hotlist-chip hotlist-chip--muted">暂无热度标签</span>
                <span v-for="item in popularityDigest" :key="item.label" class="hotlist-chip hotlist-chip--primary">
                  {{ item.label }} · {{ item.count }}
                </span>
              </div>
            </div>
          </div>

          <ul class="hotlist-list">
            <li v-for="item in hotList" :key="`${item.code}-${item.rank}`" class="hotlist-item">
              <button
                class="hotlist-item-button"
                :class="{ 'hotlist-item-button--priority': item.isHighPriority }"
                type="button"
                :disabled="resolvingCode === item.code"
                @click="openStockDetailByCode(item.code)"
              >
                <span class="hotlist-rank" :class="{ 'hotlist-rank--top': item.rank <= 3 }">{{ item.rank }}</span>

                <div class="hotlist-main">
                  <div class="hotlist-title-line">
                    <span class="hotlist-name">{{ item.name }}</span>
                    <span class="hotlist-code">{{ item.code }}</span>
                  </div>
                  <div v-if="item.popularityTag || item.conceptTags.length > 0" class="hotlist-item-tags">
                    <span v-if="item.popularityTag" class="hotlist-chip hotlist-chip--primary">
                      {{ item.popularityTag }}
                    </span>
                    <span v-for="tag in item.conceptTags.slice(0, 2)" :key="`${item.code}-${tag}`" class="hotlist-chip">
                      {{ tag }}
                    </span>
                  </div>
                </div>

                <PriceChangeChip :tone="priceChangeTone(item.changePercent)" :value="formatPercent(item.changePercent)" />

                <div class="hotlist-badge-group">
                  <span class="hotlist-rate-badge" :class="{ 'hotlist-rate-badge--priority': item.isHighPriority }">
                    热度 {{ formatRateWan(item.rate) }}
                    <svg
                      v-if="item.isHighPriority"
                      class="hotlist-priority-icon"
                      viewBox="0 0 24 24"
                      aria-label="重点热度"
                    >
                      <path
                        d="M12.8 2.6c.4 2.8-.4 4.7-2.3 6.2-1.6 1.3-2.9 2.6-2.9 4.9 0 2.5 2 4.4 4.4 4.4s4.4-1.9 4.4-4.4c0-1.6-.7-2.9-1.8-4.1 2.6 1.3 4.4 3.8 4.4 7 0 3.9-3.1 6.8-7 6.8s-7-2.9-7-6.8c0-3.6 2.1-5.7 4.1-7.5 1.7-1.6 3.4-3.1 3.7-6.5Z"
                      />
                    </svg>
                  </span>
                </div>
              </button>
            </li>
          </ul>
        </div>
      </PanelCard>
    </div>
  </div>
</template>

<style scoped>
.market-overview-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 36%);
  gap: var(--space-4);
  align-items: start;
}

.market-overview-main {
  min-width: 0;
}

.hotlist-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: var(--space-3);
  min-height: 0;
  max-height: calc(100vh - 180px);
  overflow: hidden;
}

.hotlist-content {
  display: grid;
  gap: var(--space-2);
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding-right: var(--space-1);
}

.hotlist-digest {
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-md);
  background: var(--bg-soft);
  padding: var(--space-2);
  display: grid;
  gap: var(--space-2);
}

.hotlist-digest-block {
  display: grid;
  gap: var(--space-1);
}

.hotlist-digest-title {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hotlist-chip-group {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
}

.hotlist-chip {
  min-height: 20px;
  display: inline-flex;
  align-items: center;
  border-radius: var(--radius-pill);
  padding: 0 6px;
  border: 1px solid var(--border-default);
  background: var(--bg-surface);
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
}

.hotlist-chip--primary {
  border-color: var(--primary-500);
  color: var(--primary-600);
  background: var(--primary-50);
}

.hotlist-chip--muted {
  border-style: dashed;
  color: var(--text-muted);
}

.hotlist-digest-stats {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.hotlist-stat {
  min-height: 24px;
  border-radius: var(--radius-pill);
  display: inline-flex;
  align-items: center;
  padding: 0 var(--space-2);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  background: var(--bg-surface);
  border: 1px solid var(--border-default);
}

.hotlist-stat--up {
  color: var(--success-500);
}

.hotlist-stat--down {
  color: var(--danger-500);
}

.hotlist-stat--flat {
  color: var(--text-secondary);
}

.hotlist-inline-error {
  margin: 0;
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  background: var(--bg-soft);
  color: var(--danger-500);
  font-size: var(--font-size-sm);
}

.hotlist-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: var(--space-2);
}

.hotlist-item-button {
  width: 100%;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
  color: inherit;
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) auto auto;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  text-align: left;
  cursor: pointer;
  transition: var(--transition-fast);
}

.hotlist-item-button:hover:not(:disabled) {
  border-color: var(--border-strong);
  background: var(--bg-soft);
}

.hotlist-item-button--priority {
  background: var(--bg-soft);
}

.hotlist-item-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.hotlist-rank {
  color: var(--text-secondary);
  font-weight: var(--font-weight-semibold);
}

.hotlist-rank--top {
  color: var(--danger-500);
}

.hotlist-main {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.hotlist-title-line {
  min-width: 0;
  display: flex;
  align-items: baseline;
  gap: var(--space-1);
}

.hotlist-item-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
  margin-top: 2px;
}

.hotlist-name {
  min-width: 0;
  color: var(--text-primary);
  font-weight: var(--font-weight-semibold);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hotlist-code {
  flex: 0 0 auto;
  color: var(--text-muted);
  font-size: var(--font-size-xs);
  font-family: "Consolas", "SFMono-Regular", monospace;
}

.hotlist-badge-group {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-1);
  min-width: 0;
}

.hotlist-priority-icon {
  width: 14px;
  height: 14px;
  fill: currentColor;
  flex: 0 0 auto;
}

.hotlist-rate-badge {
  min-width: 48px;
  min-height: 24px;
  border-radius: var(--radius-pill);
  padding: 0 var(--space-2);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-1);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  background: var(--bg-soft);
  color: var(--text-secondary);
}

.hotlist-rate-badge--priority {
  color: var(--danger-500);
}

@media (max-width: 1199px) {
  .market-overview-layout {
    grid-template-columns: 1fr;
  }

  .hotlist-panel {
    max-height: calc(100vh - 160px);
  }
}

@media (max-width: 768px) {
  .hotlist-item-button {
    grid-template-columns: 24px minmax(0, 1fr);
    row-gap: var(--space-2);
  }

  .hotlist-item-button :deep(.price-chip),
  .hotlist-badge-group {
    justify-self: start;
  }
}
</style>
