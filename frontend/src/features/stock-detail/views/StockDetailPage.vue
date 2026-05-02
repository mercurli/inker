<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStockDetail } from '@/features/stock-detail/composables/useStockDetail'
import PanelCard from '@/shared/components/layout/PanelCard.vue'
import SectionHeader from '@/shared/components/layout/SectionHeader.vue'
import LoadingState from '@/shared/components/feedback/LoadingState.vue'
import ErrorAlert from '@/shared/components/feedback/ErrorAlert.vue'
import PriceChangeChip from '@/shared/components/display/PriceChangeChip.vue'
import ConceptChipGroup from '@/shared/components/display/ConceptChipGroup.vue'

type RangeKey = '1M' | '3M' | '6M'

const route = useRoute()
const router = useRouter()
const store = useStockDetail()

const activeRange = ref<RangeKey>('1M')
const rangeKeys: RangeKey[] = ['1M', '3M', '6M']
const hoveredDate = ref<string | null>(null)

const rangeConfig: Record<RangeKey, number> = {
  '1M': 22,
  '3M': 66,
  '6M': 132
}

const rangeLabelMap: Record<RangeKey, string> = {
  '1M': '近1月',
  '3M': '近3月',
  '6M': '近6月'
}

watch(
  () => route.params.id,
  (value) => {
    const stockId = Number(value)

    if (Number.isInteger(stockId) && stockId > 0) {
      void store.loadDetailById(stockId)
    }
  },
  { immediate: true }
)

const visibleCandles = computed(() => {
  const candles = store.dailyKLine.value?.candles ?? []
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

  return {
    width,
    height,
    padding,
    step,
    minPrice,
    maxPrice,
    yTicks,
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

const activeCandle = computed(() => hoveredCandle.value ?? latestCandle.value)

const hoverDateLabel = computed(() => {
  const candle = hoveredCandle.value
  const geometry = chartGeometry.value

  if (!candle || !geometry) {
    return null
  }

  const labelWidth = 76
  const labelHeight = 24
  const labelX = Math.min(
    Math.max(candle.x - labelWidth / 2, geometry.padding.left),
    geometry.width - geometry.padding.right - labelWidth
  )
  const labelY = geometry.padding.top + 8

  return {
    x: labelX,
    y: labelY,
    width: labelWidth,
    height: labelHeight,
    textX: labelX + labelWidth / 2,
    textY: labelY + labelHeight / 2,
    text: candle.tradeDate
  }
})

function openConceptFilter(concept: string) {
  void router.push({ name: 'stocks', query: { concept } })
}
</script>

<template>
  <div class="page-stack stock-detail-page">
    <PanelCard>
      <div class="detail-page-topbar">
        <button class="btn btn-icon" type="button" aria-label="返回列表" title="返回列表" @click="router.push('/stocks')">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M15 6L9 12L15 18" />
          </svg>
        </button>
        <button
          v-if="store.selectedStock.value"
          class="btn"
          :class="{ 'btn-primary': store.isInWatchlist(store.selectedStock.value.id) }"
          type="button"
          @click="store.toggleWatchlist(store.selectedStock.value)"
        >
          {{ store.isInWatchlist(store.selectedStock.value.id) ? '移除自选' : '加入自选' }}
        </button>
      </div>

      <LoadingState v-if="store.detailLoading.value" text="详情加载中..." />
      <ErrorAlert v-else-if="store.detailError.value" :message="store.detailError.value" />

      <template v-else-if="store.selectedStock.value">
        <div class="detail-page-hero">
          <div>
            <p class="eyebrow">Stock Detail</p>
            <h2 class="detail-page-title">{{ store.selectedStock.value.name }}</h2>
            <p class="detail-symbol">{{ store.selectedStock.value.symbol }}</p>
          </div>
          <div class="detail-hero-metrics">
            <article class="detail-item">
              <span class="detail-item-label">价格</span>
              <strong class="detail-item-value">{{ store.formatPrice(store.selectedStock.value.latestPrice) }}</strong>
            </article>
            <article class="detail-item">
              <span class="detail-item-label">涨幅</span>
              <PriceChangeChip
                :tone="store.changeClass(store.selectedStock.value.changePercent)"
                :value="store.formatPercent(store.selectedStock.value.changePercent)"
              />
            </article>
          </div>
        </div>

        <div class="detail-grid detail-grid--page">
          <article class="detail-item"><span class="detail-item-label">市场</span><strong>{{ store.selectedStock.value.market || '--' }}</strong></article>
          <article class="detail-item"><span class="detail-item-label">所属板块</span><strong>{{ store.selectedStock.value.boardType || '--' }}</strong></article>
          <article class="detail-item"><span class="detail-item-label">所属行业</span><strong>{{ store.selectedStock.value.industry || '--' }}</strong></article>
          <article class="detail-item"><span class="detail-item-label">上市日期</span><strong>{{ store.formatDate(store.selectedStock.value.listDate) }}</strong></article>
          <article class="detail-item detail-item--full">
            <span class="detail-item-label">所属概念</span>
            <ConceptChipGroup
              :concepts="store.selectedStock.value.concepts"
              :max="store.selectedStock.value.concepts.length"
              clickable
              @select="openConceptFilter"
            />
          </article>
        </div>
      </template>
    </PanelCard>

    <PanelCard>
      <div class="kline-toolbar">
        <SectionHeader eyebrow="Daily K" title="日K走势" />
        <div class="range-switcher">
          <button
            v-for="key in rangeKeys"
            :key="key"
            class="btn"
            :class="{ 'btn-primary': activeRange === key }"
            type="button"
            @click="activeRange = key"
          >
            {{ rangeLabelMap[key] }}
          </button>
        </div>
      </div>

      <LoadingState v-if="store.kLineLoading.value" text="K线加载中..." />
      <ErrorAlert v-else-if="store.kLineError.value" :message="store.kLineError.value" />
      <LoadingState v-else-if="!chartGeometry" text="暂无K线数据" />

      <template v-else>
        <div class="kline-card">
          <div class="kline-summary-strip">
            <article class="detail-item"><span class="detail-item-label">当前价</span><strong>{{ activeCandle ? activeCandle.closePrice.toFixed(2) : '--' }}</strong></article>
            <article class="detail-item"><span class="detail-item-label">当日涨幅</span><PriceChangeChip :tone="store.changeClass(activeCandle?.changePercent ?? null)" :value="store.formatPercent(activeCandle?.changePercent ?? null)" /></article>
            <article class="detail-item"><span class="detail-item-label">开盘价</span><strong>{{ activeCandle ? activeCandle.openPrice.toFixed(2) : '--' }}</strong></article>
            <article class="detail-item"><span class="detail-item-label">最高价</span><strong>{{ activeCandle ? activeCandle.highPrice.toFixed(2) : '--' }}</strong></article>
            <article class="detail-item"><span class="detail-item-label">最低价</span><strong>{{ activeCandle ? activeCandle.lowPrice.toFixed(2) : '--' }}</strong></article>
          </div>

          <div class="kline-svg-shell">
            <svg
              class="kline-svg"
              :viewBox="`0 0 ${chartGeometry.width} ${chartGeometry.height}`"
              :aria-label="`${store.selectedStock.value?.name ?? ''} 日K图`"
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
                  :class="`kline-wick-svg--${item.tone}`"
                />
                <rect
                  :x="item.x - item.bodyWidth / 2"
                  :y="item.bodyY"
                  :width="item.bodyWidth"
                  :height="item.bodyHeight"
                  rx="2"
                  :class="`kline-body-svg--${item.tone}`"
                />
                <rect
                  :x="item.x - chartGeometry.step / 2"
                  :y="chartGeometry.padding.top"
                  :width="chartGeometry.step"
                  :height="chartGeometry.height - chartGeometry.padding.top - chartGeometry.padding.bottom"
                  class="kline-hit-area"
                  @mouseenter="hoveredDate = item.tradeDate"
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
              <g v-if="hoverDateLabel" class="kline-hover-label">
                <rect
                  :x="hoverDateLabel.x"
                  :y="hoverDateLabel.y"
                  :width="hoverDateLabel.width"
                  :height="hoverDateLabel.height"
                  rx="8"
                />
                <text
                  :x="hoverDateLabel.textX"
                  :y="hoverDateLabel.textY"
                  text-anchor="middle"
                  dominant-baseline="middle"
                >
                  {{ hoverDateLabel.text }}
                </text>
              </g>
            </svg>
          </div>

          <div class="kline-axis">
            <span>{{ visibleCandles[0]?.tradeDate }}</span>
            <span>{{ visibleCandles[visibleCandles.length - 1]?.tradeDate }}</span>
          </div>
        </div>
      </template>
    </PanelCard>
  </div>
</template>
