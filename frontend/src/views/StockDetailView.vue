<script setup lang="ts">
import { computed, ref } from 'vue'
import type { Stock, StockDailyKLine } from '@/api/stock'

type RangeKey = '1M' | '3M' | '6M'

const props = defineProps<{
  selectedStock: Stock | null
  detailLoading: boolean
  detailError: string
  dailyKLine: StockDailyKLine | null
  kLineLoading: boolean
  kLineError: string
  isInWatchlist: (stockId: number) => boolean
  formatPrice: (value: number | null) => string
  formatPercent: (value: number | null) => string
  formatDate: (value: string | undefined) => string
  changeClass: (value: number | null) => string
}>()

const emit = defineEmits<{
  (e: 'toggle-watchlist', stock: Stock): void
  (e: 'back'): void
}>()

const activeRange = ref<RangeKey>('1M')
const rangeKeys: RangeKey[] = ['1M', '3M', '6M']
const hoveredDate = ref<string | null>(null)

const rangeConfig: Record<RangeKey, number> = {
  '1M': 22,
  '3M': 66,
  '6M': 132
}

const visibleCandles = computed(() => {
  const candles = props.dailyKLine?.candles ?? []
  return candles.slice(-rangeConfig[activeRange.value])
})

const chartGeometry = computed(() => {
  const candles = visibleCandles.value
  if (candles.length === 0) {
    return null
  }

  const width = Math.max(720, candles.length * 22)
  const height = 320
  const padding = { top: 16, right: 18, bottom: 28, left: 56 }
  const guideCount = 5
  const innerWidth = width - padding.left - padding.right
  const innerHeight = height - padding.top - padding.bottom
  const minPrice = Math.min(...candles.map((item) => item.lowPrice))
  const maxPrice = Math.max(...candles.map((item) => item.highPrice))
  const priceRange = Math.max(maxPrice - minPrice, 0.01)
  const step = innerWidth / candles.length
  const bodyWidth = Math.max(8, Math.min(14, step * 0.58))

  const y = (price: number) => padding.top + ((maxPrice - price) / priceRange) * innerHeight
  const clamp = (value: number, min: number, max: number) => Math.min(Math.max(value, min), max)

  const yTicks = Array.from({ length: guideCount }, (_, index) => {
    const ratio = index / (guideCount - 1)
    const value = maxPrice - ratio * priceRange
    const yPos = padding.top + ratio * innerHeight
    return {
      key: `tick-${index}`,
      yPos,
      label: value.toFixed(2)
    }
  })

  const firstCandle = candles[0]
  if (!firstCandle) {
    return null
  }

  let highestIndex = 0
  let lowestIndex = 0
  let highestCandle = firstCandle
  let lowestCandle = firstCandle
  candles.forEach((item, index) => {
    if (item.highPrice > highestCandle.highPrice) {
      highestIndex = index
      highestCandle = item
    }
    if (item.lowPrice < lowestCandle.lowPrice) {
      lowestIndex = index
      lowestCandle = item
    }
  })
  const highestX = padding.left + highestIndex * step + step / 2
  const lowestX = padding.left + lowestIndex * step + step / 2
  const highestY = y(highestCandle.highPrice)
  const lowestY = y(lowestCandle.lowPrice)
  const highestAnchor = highestX > width - 130 ? 'end' : 'start'
  const lowestAnchor = lowestX > width - 130 ? 'end' : 'start'

  return {
    width,
    height,
    padding,
    step,
    minPrice,
    maxPrice,
    yTicks,
    highestPoint: {
      x: highestX,
      y: highestY,
      labelX: clamp(highestAnchor === 'end' ? highestX - 8 : highestX + 8, padding.left + 26, width - padding.right - 26),
      labelY: clamp(highestY - 6, padding.top + 12, height - padding.bottom - 12),
      label: `H ${highestCandle.highPrice.toFixed(2)}`,
      anchor: highestAnchor
    },
    lowestPoint: {
      x: lowestX,
      y: lowestY,
      labelX: clamp(lowestAnchor === 'end' ? lowestX - 8 : lowestX + 8, padding.left + 26, width - padding.right - 26),
      labelY: clamp(lowestY + 14, padding.top + 12, height - padding.bottom - 8),
      label: `L ${lowestCandle.lowPrice.toFixed(2)}`,
      anchor: lowestAnchor
    },
    items: candles.map((item, index) => {
      const x = padding.left + index * step + step / 2
      const openY = y(item.openPrice)
      const closeY = y(item.closePrice)
      const highY = y(item.highPrice)
      const lowY = y(item.lowPrice)

      return {
        ...item,
        x,
        wickTop: highY,
        wickBottom: lowY,
        bodyY: Math.min(openY, closeY),
        bodyHeight: Math.max(Math.abs(openY - closeY), 2),
        bodyWidth,
        tone: item.closePrice > item.openPrice ? 'up' : item.closePrice < item.openPrice ? 'down' : 'neutral'
      }
    })
  }
})

const latestCandle = computed(() => {
  const candles = visibleCandles.value
  return candles.length > 0 ? candles[candles.length - 1] : null
})

const hoveredCandle = computed(() => {
  if (!hoveredDate.value || !chartGeometry.value) {
    return null
  }
  return chartGeometry.value.items.find((item) => item.tradeDate === hoveredDate.value) ?? null
})
</script>

<template>
  <div class="page-stack stock-detail-page">
    <section class="panel section-panel">
      <div class="detail-page-topbar">
        <button class="btn btn-page detail-back-btn" type="button" @click="emit('back')">返回列表</button>
        <div v-if="selectedStock" class="detail-page-actions">
          <button
            class="btn btn-watchlist"
            :class="{ 'btn-watchlist--active': isInWatchlist(selectedStock.id) }"
            type="button"
            @click="emit('toggle-watchlist', selectedStock)"
          >
            {{ isInWatchlist(selectedStock.id) ? '移除自选' : '加入自选' }}
          </button>
        </div>
      </div>

      <div v-if="detailLoading" class="detail-loading">当前价</div>
      <div v-else-if="detailError" class="detail-error">{{ detailError }}</div>
      <template v-else-if="selectedStock">
        <div class="detail-page-hero">
          <div>
            <p class="detail-caption">Stock Detail</p>
            <h2 class="detail-page-title">{{ selectedStock.name }}</h2>
            <p class="detail-symbol">{{ selectedStock.symbol }}</p>
          </div>
          <div class="detail-hero-metrics">
            <article class="detail-item">
              <span class="detail-item-label">价格</span>
              <strong class="detail-item-value detail-price">{{ formatPrice(selectedStock.latestPrice) }}</strong>
            </article>
            <article class="detail-item">
              <span class="detail-item-label">涨幅</span>
              <strong class="detail-item-value detail-change" :class="changeClass(selectedStock.changePercent)">
                {{ formatPercent(selectedStock.changePercent) }}
              </strong>
            </article>
          </div>
        </div>

        <div class="detail-grid detail-grid--page">
          <article class="detail-item">
            <span class="detail-item-label">股票代码</span>
            <strong class="detail-item-value">{{ selectedStock.symbol }}</strong>
          </article>
          <article class="detail-item">
            <span class="detail-item-label">市场</span>
            <strong class="detail-item-value">{{ selectedStock.market || '--' }}</strong>
          </article>
          <article class="detail-item">
            <span class="detail-item-label">所属板块</span>
            <strong class="detail-item-value">{{ selectedStock.boardType || '--' }}</strong>
          </article>
          <article class="detail-item">
            <span class="detail-item-label">所属行业</span>
            <strong class="detail-item-value">{{ selectedStock.industry || '--' }}</strong>
          </article>
          <article class="detail-item">
            <span class="detail-item-label">上市日期</span>
            <strong class="detail-item-value">{{ formatDate(selectedStock.listDate) }}</strong>
          </article>
          <article class="detail-item detail-item--full">
            <span class="detail-item-label">所属概念</span>
            <strong class="detail-item-value">
              {{ selectedStock.concepts.length > 0 ? selectedStock.concepts.join(' / ') : '--' }}
            </strong>
          </article>
        </div>
      </template>
    </section>

    <section class="panel section-panel">
      <div class="kline-toolbar">
        <div>
          <p class="detail-caption">Daily K</p>
          <h3 class="section-title">日K走势</h3>
          <p class="app-desc"></p>
        </div>
        <div class="range-switcher">
          <button
            v-for="key in rangeKeys"
            :key="key"
            class="btn btn-page range-btn"
            :class="{ 'range-btn--active': activeRange === key }"
            type="button"
            @click="activeRange = key"
          >
            {{ key }}
          </button>
        </div>
      </div>

      <div v-if="kLineLoading" class="detail-loading">閺冾檻閸旂姾娴囨稉?..</div>
      <div v-else-if="kLineError" class="detail-error">{{ kLineError }}</div>
      <div v-else-if="!chartGeometry" class="detail-loading">閺嗗倹妫ら弮顧戦弫鐗堝祦</div>
      <template v-else>
        <div class="kline-card">
          <div class="kline-summary-strip">
            <article class="detail-item">
              <span class="detail-item-label">当前价</span>
              <strong class="detail-item-value">{{ latestCandle ? latestCandle.closePrice.toFixed(2) : '--' }}</strong>
            </article>
            <article class="detail-item">
              <span class="detail-item-label">当日涨幅</span>
              <strong class="detail-item-value" :class="changeClass(latestCandle?.changePercent ?? null)">
                {{ formatPercent(latestCandle?.changePercent ?? null) }}
              </strong>
            </article>
            <article class="detail-item">
              <span class="detail-item-label">最低价</span>
              <strong class="detail-item-value">{{ chartGeometry.minPrice.toFixed(2) }}</strong>
            </article>
            <article class="detail-item">
              <span class="detail-item-label">最高价</span>
              <strong class="detail-item-value">{{ chartGeometry.maxPrice.toFixed(2) }}</strong>
            </article>
          </div>

          <div class="kline-svg-shell">
            <svg
              class="kline-svg"
              :viewBox="`0 0 ${chartGeometry.width} ${chartGeometry.height}`"
              :aria-label="`${selectedStock?.name ?? ''} 日K图`"
              role="img"
              @mouseleave="hoveredDate = null"
            >
              <line
                v-for="tick in chartGeometry.yTicks"
                :key="tick.key"
                :x1="chartGeometry.padding.left"
                :x2="chartGeometry.width - chartGeometry.padding.right"
                :y1="tick.yPos"
                :y2="tick.yPos"
                class="kline-guide"
              />
              <text
                v-for="tick in chartGeometry.yTicks"
                :key="`${tick.key}-label`"
                :x="chartGeometry.padding.left - 6"
                :y="tick.yPos"
                class="kline-y-label"
                text-anchor="end"
                dominant-baseline="middle"
              >
                {{ tick.label }}
              </text>
              <g v-for="item in chartGeometry.items" :key="item.tradeDate">
                <line
                  :x1="item.x"
                  :x2="item.x"
                  :y1="item.wickTop"
                  :y2="item.wickBottom"
                  :class="`kline-wick-svg kline-wick-svg--${item.tone}`"
                />
                <rect
                  :x="item.x - item.bodyWidth / 2"
                  :y="item.bodyY"
                  :width="item.bodyWidth"
                  :height="item.bodyHeight"
                  rx="2"
                  :class="`kline-body-svg kline-body-svg--${item.tone}`"
                />
                <rect
                  :x="item.x - chartGeometry.step / 2"
                  :y="chartGeometry.padding.top"
                  :width="chartGeometry.step"
                  :height="chartGeometry.height - chartGeometry.padding.top - chartGeometry.padding.bottom"
                  class="kline-hit-area"
                  @mouseenter="hoveredDate = item.tradeDate"
                  @mousemove="hoveredDate = item.tradeDate"
                />
              </g>
              <line
                v-if="hoveredCandle"
                :x1="hoveredCandle.x"
                :x2="hoveredCandle.x"
                :y1="chartGeometry.padding.top"
                :y2="chartGeometry.height - chartGeometry.padding.bottom"
                class="kline-hover-guide"
              />
              <text
                v-if="hoveredCandle"
                :x="chartGeometry.padding.left + 8"
                :y="chartGeometry.padding.top + 12"
                class="kline-hover-label"
              >
                {{ formatDate(hoveredCandle.tradeDate) }}
              </text>
              <circle :cx="chartGeometry.highestPoint.x" :cy="chartGeometry.highestPoint.y" r="2.5" class="kline-extreme-dot" />
              <text
                :x="chartGeometry.highestPoint.labelX"
                :y="chartGeometry.highestPoint.labelY"
                :text-anchor="chartGeometry.highestPoint.anchor"
                class="kline-extreme-label"
              >
                {{ chartGeometry.highestPoint.label }}
              </text>
              <circle :cx="chartGeometry.lowestPoint.x" :cy="chartGeometry.lowestPoint.y" r="2.5" class="kline-extreme-dot" />
              <text
                :x="chartGeometry.lowestPoint.labelX"
                :y="chartGeometry.lowestPoint.labelY"
                :text-anchor="chartGeometry.lowestPoint.anchor"
                class="kline-extreme-label"
              >
                {{ chartGeometry.lowestPoint.label }}
              </text>
            </svg>
          </div>

          <div class="kline-axis">
            <span>{{ visibleCandles[0]?.tradeDate }}</span>
            <span>{{ visibleCandles[visibleCandles.length - 1]?.tradeDate }}</span>
          </div>
        </div>
      </template>
    </section>
  </div>
</template>

