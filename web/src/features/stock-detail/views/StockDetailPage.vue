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
import SearchableSelect from '@/shared/components/interaction/SearchableSelect.vue'
import { resolveApiAssetUrl } from '@/api/stock'

type RangeKey = '3M' | '6M'

const route = useRoute()
const router = useRouter()
const store = useStockDetail()

const activeRange = ref<RangeKey>('3M')
const rangeKeys: RangeKey[] = ['3M', '6M']
const hoveredDate = ref<string | null>(null)
const showConceptDialog = ref(false)
const primaryConceptInput = ref('')
const newConceptInput = ref('')
const conceptSaving = ref(false)
const conceptDialogError = ref('')

const rangeConfig: Record<RangeKey, number> = {
  '3M': 66,
  '6M': 132
}

const rangeLabelMap: Record<RangeKey, string> = {
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
  const height = 360
  const padding = { top: 16, right: 18, bottom: 76, left: 56 }
  const guideCount = 5
  const innerWidth = width - padding.left - padding.right
  const innerHeight = height - padding.top - padding.bottom
  const plotBottom = height - padding.bottom
  const xAxisLabelY = height - 24
  const lowLabelMaxY = xAxisLabelY - 18
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

  const items = candles.map((item, index) => {
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
      upperWickBottom: Math.min(openY, closeY),
      lowerWickTop: Math.max(openY, closeY),
      bodyY: Math.min(openY, closeY),
      bodyHeight: Math.max(Math.abs(openY - closeY), 2),
      bodyWidth,
      tone: item.closePrice > item.openPrice ? 'up' : item.closePrice < item.openPrice ? 'down' : 'neutral'
    }
  })

  const maxXTicks = Math.max(2, Math.floor(innerWidth / 120) + 1)
  const xTickCount = Math.min(candles.length, maxXTicks)
  const xTickIndexes = Array.from({ length: xTickCount }, (_, index) => {
    if (xTickCount === 1) {
      return 0
    }

    return Math.round((index * (candles.length - 1)) / (xTickCount - 1))
  })
  const xTicks = Array.from(new Set(xTickIndexes))
    .map((index) => {
      const candle = candles[index]
      const item = items[index]

      if (!candle || !item) {
        return null
      }

      return {
        key: `date-${candle.tradeDate}`,
        x: item.x,
        y: xAxisLabelY,
        label: candle.tradeDate
      }
    })
    .filter((tick): tick is { key: string; x: number; y: number; label: string } => tick !== null)

  const ma5Points = items
    .map((item, index) => {
      if (index < 4) {
        return null
      }

      const averageClose =
        candles.slice(index - 4, index + 1).reduce((sum, candle) => sum + candle.closePrice, 0) / 5

      return {
        key: `ma5-${item.tradeDate}`,
        x: item.x,
        y: y(averageClose),
        value: averageClose
      }
    })
    .filter((point): point is { key: string; x: number; y: number; value: number } => point !== null)
  const ma5Polyline = ma5Points.map((point) => `${point.x},${point.y}`).join(' ')
  const firstItem = items[0]!
  const highestItem = items.reduce((result, item) => (item.highPrice > result.highPrice ? item : result), firstItem)
  const lowestItem = items.reduce((result, item) => (item.lowPrice < result.lowPrice ? item : result), firstItem)
  const makeExtremeLabel = (
    item: (typeof items)[number],
    kind: 'high' | 'low',
    price: number,
    pointY: number
  ) => {
    const direction = item.x + 72 <= width - padding.right ? 'right' : 'left'
    const sign = direction === 'right' ? 1 : -1
    const labelY =
      kind === 'high'
        ? clamp(pointY - 18, 8, plotBottom - 8)
        : clamp(pointY + 22, plotBottom + 10, lowLabelMaxY)

    return {
      key: `${kind}-${item.tradeDate}`,
      pointX: item.x,
      pointY,
      lineStartX: item.x + sign * 30,
      lineEndX: item.x,
      lineEndY: labelY,
      textX: item.x + sign * 34,
      textY: labelY,
      textAnchor: direction === 'right' ? 'start' : 'end',
      label: price.toFixed(2)
    }
  }
  const extremeLabels = [
    makeExtremeLabel(highestItem, 'high', highestItem.highPrice, highestItem.wickTop),
    makeExtremeLabel(lowestItem, 'low', lowestItem.lowPrice, lowestItem.wickBottom)
  ]

  return {
    width,
    height,
    padding,
    step,
    minPrice,
    maxPrice,
    yTicks,
    xTicks,
    ma5Points,
    ma5Polyline,
    extremeLabels,
    items
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

const currentConcepts = computed(() => store.selectedStock.value?.concepts ?? [])
const primaryConcept = computed(() => store.selectedStock.value?.primaryConcept ?? currentConcepts.value[0] ?? null)
const selectedLogoUrl = computed(() => resolveApiAssetUrl(store.selectedStock.value?.logo))
const selectedLogoFallback = computed(() => {
  const stock = store.selectedStock.value
  return stock?.name.trim().slice(0, 1) || stock?.symbol.slice(0, 1) || '-'
})

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

function openConceptDialog() {
  primaryConceptInput.value = primaryConcept.value ?? ''
  newConceptInput.value = ''
  conceptDialogError.value = ''
  showConceptDialog.value = true
}

function closeConceptDialog() {
  if (conceptSaving.value) {
    return
  }

  showConceptDialog.value = false
  conceptDialogError.value = ''
}

async function savePrimaryConcept() {
  const stock = store.selectedStock.value
  const nextPrimaryConcept = primaryConceptInput.value.trim()

  if (!stock || !nextPrimaryConcept) {
    conceptDialogError.value = '请输入主概念。'
    return
  }

  const nextConcepts = [
    nextPrimaryConcept,
    ...currentConcepts.value.filter((concept) => concept !== nextPrimaryConcept)
  ]

  await saveConcepts(nextConcepts)
}

async function saveNewConcept() {
  const stock = store.selectedStock.value
  const nextConcept = newConceptInput.value.trim()

  if (!stock || !nextConcept) {
    conceptDialogError.value = '请输入要新增的概念。'
    return
  }

  if (currentConcepts.value.includes(nextConcept)) {
    conceptDialogError.value = '该概念已存在。'
    return
  }

  await saveConcepts([...currentConcepts.value, nextConcept])
}

async function deleteConcept(targetConcept: string) {
  const stock = store.selectedStock.value
  const conceptName = targetConcept.trim()

  if (!stock || !conceptName) {
    conceptDialogError.value = '请选择要删除的概念。'
    return
  }

  await saveConcepts(currentConcepts.value.filter((concept) => concept !== conceptName))
}

function confirmDeleteConcept(concept: string) {
  const confirmed = window.confirm(`确定删除概念“${concept}”吗？`)

  if (!confirmed) {
    return
  }

  void deleteConcept(concept)
}

async function saveConcepts(nextConcepts: string[]) {
  const stock = store.selectedStock.value

  if (!stock) {
    return
  }

  try {
    conceptSaving.value = true
    conceptDialogError.value = ''
    await store.updateStockConcepts(stock.id, nextConcepts)
    showConceptDialog.value = false
  } catch {
    conceptDialogError.value = '保存失败，请稍后重试。'
  } finally {
    conceptSaving.value = false
  }
}
</script>

<template>
  <div class="page-stack stock-detail-page">
    <PanelCard>
      <div v-if="store.selectedStock.value" class="detail-page-topbar detail-page-topbar--actions">
        <button
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
          <div class="detail-title-wrap">
            <img v-if="selectedLogoUrl" class="detail-stock-logo" :src="selectedLogoUrl" :alt="`${store.selectedStock.value.name} logo`" />
            <span v-else class="detail-stock-logo detail-stock-logo--fallback">{{ selectedLogoFallback }}</span>
            <div>
              <p class="eyebrow">Stock Detail</p>
              <h2 class="detail-page-title">{{ store.selectedStock.value.name }}</h2>
              <p class="detail-symbol">{{ store.selectedStock.value.symbol }}</p>
            </div>
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
          <article class="detail-item">
            <span class="detail-item-label">5日涨跌幅</span>
            <PriceChangeChip
              :tone="store.changeClass(store.selectedStock.value.fiveDayChangePercent)"
              :value="store.formatPercent(store.selectedStock.value.fiveDayChangePercent)"
            />
          </article>
          <article class="detail-item"><span class="detail-item-label">成交量</span><strong>{{ store.formatVolume(store.selectedStock.value.volume) }}</strong></article>
          <article class="detail-item"><span class="detail-item-label">成交额</span><strong>{{ store.formatAmount(store.selectedStock.value.amount) }}</strong></article>
          <article class="detail-item"><span class="detail-item-label">换手率</span><strong>{{ store.formatTurnoverRate(store.selectedStock.value.turnoverRate) }}</strong></article>
          <article class="detail-item"><span class="detail-item-label">总市值</span><strong>{{ store.formatMarketValue(store.selectedStock.value.totalMarketValue) }}</strong></article>
          <article class="detail-item"><span class="detail-item-label">流通市值</span><strong>{{ store.formatMarketValue(store.selectedStock.value.circulatingMarketValue) }}</strong></article>
          <article class="detail-item"><span class="detail-item-label">动态市盈率</span><strong>{{ store.formatPeRatio(store.selectedStock.value.dynamicPeRatio) }}</strong></article>
          <article class="detail-item detail-item--full">
            <span class="detail-item-label">所属概念</span>
            <ConceptChipGroup
              :concepts="currentConcepts"
              :primary-concept="primaryConcept"
              :max="currentConcepts.length"
              clickable
              @select="openConceptFilter"
            >
              <template #append>
                <button class="concept-chip concept-chip--button concept-chip--icon" type="button" aria-label="编辑概念" title="编辑概念" @click="openConceptDialog">
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M12 20H21" />
                    <path d="M16.5 3.5A2.12 2.12 0 0 1 19.5 6.5L7 19L3 20L4 16L16.5 3.5Z" />
                  </svg>
                </button>
              </template>
            </ConceptChipGroup>
          </article>
        </div>
      </template>
    </PanelCard>

    <div v-if="showConceptDialog" class="dialog-backdrop" @click.self="closeConceptDialog">
      <div class="dialog-card concept-dialog">
        <div class="concept-dialog__header">
          <h3 class="dialog-title">编辑概念</h3>
          <button class="btn btn-icon" type="button" aria-label="关闭" title="关闭" :disabled="conceptSaving" @click="closeConceptDialog">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M18 6L6 18" />
              <path d="M6 6L18 18" />
            </svg>
          </button>
        </div>

        <div class="concept-dialog__section concept-dialog__section--stacked">
          <span class="field-label">当前概念</span>
          <div class="concept-delete-list">
            <span v-for="concept in currentConcepts" :key="concept" class="concept-chip concept-chip--deletable" :class="{ 'concept-chip--primary': concept === primaryConcept }">
              {{ concept }}
              <button
                class="concept-chip__icon-button"
                type="button"
                :aria-label="`删除概念 ${concept}`"
                :title="`删除概念 ${concept}`"
                :disabled="conceptSaving"
                @click="confirmDeleteConcept(concept)"
              >
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M18 6L6 18" />
                  <path d="M6 6L18 18" />
                </svg>
              </button>
            </span>
            <span v-if="currentConcepts.length === 0" class="concept-delete-empty">暂无概念</span>
          </div>
        </div>

        <div class="concept-dialog__section">
          <label class="field-label" for="primary-concept-input">主概念</label>
          <SearchableSelect
            input-id="primary-concept-input"
            v-model="primaryConceptInput"
            :options="currentConcepts"
            placeholder="选择主概念"
            :allow-custom="false"
            :disabled="conceptSaving"
            show-all-options-on-open
            @keyup.enter="savePrimaryConcept"
          />
          <button class="btn btn-primary" type="button" :disabled="conceptSaving" @click="savePrimaryConcept">
            保存
          </button>
        </div>

        <div class="concept-dialog__section">
          <label class="field-label" for="new-concept-input">新增概念</label>
          <input
            id="new-concept-input"
            v-model="newConceptInput"
            class="field-control"
            :disabled="conceptSaving"
            placeholder="输入概念名称"
            @keyup.enter="saveNewConcept"
          />
          <button class="btn btn-primary" type="button" :disabled="conceptSaving" @click="saveNewConcept">
            新增
          </button>
        </div>

        <p v-if="conceptDialogError" class="dialog-error">{{ conceptDialogError }}</p>
      </div>
    </div>

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
              <defs>
                <marker
                  id="kline-extreme-arrow"
                  markerHeight="6"
                  markerWidth="6"
                  orient="auto"
                  refX="5"
                  refY="3"
                  viewBox="0 0 6 6"
                >
                  <path d="M0,0 L6,3 L0,6 Z" class="kline-extreme-label__arrow" />
                </marker>
              </defs>
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
              <text
                v-for="tick in chartGeometry.xTicks"
                :key="tick.key"
                :x="tick.x"
                :y="tick.y"
                class="kline-x-label"
                text-anchor="middle"
                dominant-baseline="middle"
              >
                {{ tick.label }}
              </text>

              <g v-for="item in chartGeometry.items" :key="item.tradeDate">
                <line
                  v-if="item.tone !== 'up'"
                  :x1="item.x"
                  :x2="item.x"
                  :y1="item.wickTop"
                  :y2="item.wickBottom"
                  :class="`kline-wick-svg--${item.tone}`"
                />
                <template v-else>
                  <line
                    :x1="item.x"
                    :x2="item.x"
                    :y1="item.wickTop"
                    :y2="item.upperWickBottom"
                    class="kline-wick-svg--up"
                  />
                  <line
                    :x1="item.x"
                    :x2="item.x"
                    :y1="item.lowerWickTop"
                    :y2="item.wickBottom"
                    class="kline-wick-svg--up"
                  />
                </template>
                <rect
                  :x="item.x - item.bodyWidth / 2"
                  :y="item.bodyY"
                  :width="item.bodyWidth"
                  :height="item.bodyHeight"
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
              <g
                v-for="label in chartGeometry.extremeLabels"
                :key="label.key"
                class="kline-extreme-label"
              >
                <line
                  :x1="label.lineStartX"
                  :x2="label.lineEndX"
                  :y1="label.lineEndY"
                  :y2="label.lineEndY"
                  class="kline-extreme-label__line"
                />
                <text
                  :x="label.textX"
                  :y="label.textY"
                  :text-anchor="label.textAnchor"
                  dominant-baseline="middle"
                  class="kline-extreme-label__text"
                >
                  {{ label.label }}
                </text>
              </g>
              <polyline
                v-if="chartGeometry.ma5Points.length > 1"
                :points="chartGeometry.ma5Polyline"
                class="kline-ma5-line"
              />

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
        </div>
      </template>
    </PanelCard>
  </div>
</template>
