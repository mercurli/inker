<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useWatchlist } from '@/features/watchlist/composables/useWatchlist'
import PanelCard from '@/shared/components/layout/PanelCard.vue'
import SectionHeader from '@/shared/components/layout/SectionHeader.vue'
import PriceChangeChip from '@/shared/components/display/PriceChangeChip.vue'
import ConceptChipGroup from '@/shared/components/display/ConceptChipGroup.vue'
import BoardTag from '@/shared/components/display/BoardTag.vue'
import EmptyState from '@/shared/components/feedback/EmptyState.vue'
import { resolveApiAssetUrl, type Stock } from '@/api/stock'

type WatchlistSortDirection = 'asc' | 'desc'
type WatchlistSortField = 'latestPrice' | 'changePercent' | 'industry'

const router = useRouter()
const store = useWatchlist()

const watchlistSortDefaultDirections: Record<WatchlistSortField, WatchlistSortDirection> = {
  latestPrice: 'desc',
  changePercent: 'desc',
  industry: 'asc'
}

const newGroupName = ref('')
const showCreateGroupDialog = ref(false)
const showRenameGroupDialog = ref(false)
const renameGroupName = ref('')
const showSwitchGroupMenu = ref(false)
const switchMenuStockId = ref<number | null>(null)
const switchMenuStockGroupIds = ref<number[]>([])
const switchMenuLoading = ref(false)
const switchMenuOperatingGroupId = ref<number | null>(null)
const switchMenuX = ref(0)
const switchMenuY = ref(0)
const draggedGroupId = ref<number | null>(null)
const dragOverGroupId = ref<number | null>(null)
const draggedStockId = ref<number | null>(null)
const dragOverStockId = ref<number | null>(null)
const watchlistListRef = ref<HTMLElement | null>(null)
const watchlistSortField = ref<WatchlistSortField | null>(null)
const watchlistSortDirection = ref<WatchlistSortDirection>('desc')
let stockAutoScrollFrame: number | null = null
let stockAutoScrollDirection = 0
let stockAutoScrollSpeed = 0

const switchGroupOptions = computed(() => store.watchlistGroups.value)
const isWatchlistSorted = computed(() => watchlistSortField.value != null)

const activeIndustryCountEntries = computed(() => Object.entries(store.activeWatchlistGroup.value?.industryCounts ?? {}))
const activePrimaryConceptCountEntries = computed(() =>
  Object.entries(store.activeWatchlistGroup.value?.primaryConceptCounts ?? {})
)
const displayedWatchlist = computed(() => {
  const field = watchlistSortField.value

  if (!field) {
    return store.watchlist.value
  }

  const direction = watchlistSortDirection.value

  return store.watchlist.value
    .map((stock, index) => ({ stock, index }))
    .sort((left, right) => {
      const result = compareWatchlistStocks(left.stock, right.stock, field, direction)
      return result === 0 ? left.index - right.index : result
    })
    .map((item) => item.stock)
})

watch(
  () => store.activeWatchlistGroup.value?.name,
  (name) => {
    renameGroupName.value = name ?? ''
  },
  { immediate: true }
)

function openDetail(stockId: number) {
  void router.push(`/stocks/${stockId}`)
}

function logoUrl(stock: Stock) {
  return resolveApiAssetUrl(stock.logo)
}

function logoFallback(stock: Stock) {
  return stock.name.trim().slice(0, 1) || stock.symbol.slice(0, 1) || '-'
}

function compareNullableNumber(left: number | null, right: number | null, direction: WatchlistSortDirection) {
  if (left == null && right == null) {
    return 0
  }

  if (left == null) {
    return 1
  }

  if (right == null) {
    return -1
  }

  const result = left - right
  return direction === 'asc' ? result : -result
}

function compareText(left: string, right: string, direction: WatchlistSortDirection) {
  const result = left.localeCompare(right, 'zh-CN')
  return direction === 'asc' ? result : -result
}

function compareWatchlistStocks(
  left: (typeof store.watchlist.value)[number],
  right: (typeof store.watchlist.value)[number],
  field: WatchlistSortField,
  direction: WatchlistSortDirection
) {
  if (field === 'latestPrice') {
    return compareNullableNumber(left.latestPrice, right.latestPrice, direction)
  }

  if (field === 'changePercent') {
    return compareNullableNumber(left.changePercent, right.changePercent, direction)
  }

  return compareText(left.industry?.trim() || '未分类行业', right.industry?.trim() || '未分类行业', direction)
}

function toggleWatchlistSort(field: WatchlistSortField) {
  const defaultDirection = watchlistSortDefaultDirections[field]

  if (watchlistSortField.value !== field) {
    watchlistSortField.value = field
    watchlistSortDirection.value = defaultDirection
    resetStockDragState()
    return
  }

  if (watchlistSortDirection.value === defaultDirection) {
    watchlistSortDirection.value = defaultDirection === 'asc' ? 'desc' : 'asc'
    resetStockDragState()
    return
  }

  watchlistSortField.value = null
  watchlistSortDirection.value = defaultDirection
  resetStockDragState()
}

function watchlistSortIndicator(field: WatchlistSortField) {
  if (watchlistSortField.value !== field) {
    return ''
  }

  return watchlistSortDirection.value === 'asc' ? ' ↑' : ' ↓'
}

function chooseGroup(groupId: number) {
  void store.setActiveWatchlistGroup(groupId)
}

function isDefaultGroup(group: { default: boolean }) {
  return group.default
}

function createGroup() {
  const name = newGroupName.value.trim()

  if (!name) {
    return
  }

  void store.createWatchlistGroup(name)
  newGroupName.value = ''
  showCreateGroupDialog.value = false
}

function openCreateGroupDialog() {
  newGroupName.value = ''
  showCreateGroupDialog.value = true
}

function closeCreateGroupDialog() {
  showCreateGroupDialog.value = false
}

function openRenameGroupDialog() {
  renameGroupName.value = store.activeWatchlistGroup.value?.name ?? ''
  showRenameGroupDialog.value = true
}

function closeRenameGroupDialog() {
  showRenameGroupDialog.value = false
}

function renameCurrentGroup() {
  const groupId = store.activeWatchlistGroupId.value
  const name = renameGroupName.value.trim()

  if (groupId == null || !name) {
    return
  }

  void store.renameWatchlistGroup(groupId, name)
  showRenameGroupDialog.value = false
}

function deleteCurrentGroup() {
  const group = store.activeWatchlistGroup.value

  if (!group) {
    return
  }

  const confirmed = window.confirm(`确定删除分组“${group.name}”吗？按规则会取消该分组内股票的关注。`)

  if (!confirmed) {
    return
  }

  void store.deleteWatchlistGroup(group.id)
}

function closeSwitchGroupMenu() {
  showSwitchGroupMenu.value = false
  switchMenuStockId.value = null
  switchMenuStockGroupIds.value = []
  switchMenuLoading.value = false
  switchMenuOperatingGroupId.value = null
}

function openSwitchGroupMenu(event: MouseEvent, stockId: number) {
  event.preventDefault()

  const menuWidth = 196
  const menuHeight = 54 + Math.max(switchGroupOptions.value.length, 1) * 36
  const viewportWidth = window.innerWidth
  const viewportHeight = window.innerHeight
  const safePadding = 8

  switchMenuX.value = Math.max(safePadding, Math.min(event.clientX, viewportWidth - menuWidth - safePadding))
  switchMenuY.value = Math.max(safePadding, Math.min(event.clientY, viewportHeight - menuHeight - safePadding))
  switchMenuStockId.value = stockId
  switchMenuStockGroupIds.value = []
  showSwitchGroupMenu.value = true
  void loadSwitchMenuStockGroups(stockId)
}

async function loadSwitchMenuStockGroups(stockId: number) {
  switchMenuLoading.value = true
  const groupIds = await store.getStockGroupIds(stockId)

  if (showSwitchGroupMenu.value && switchMenuStockId.value === stockId) {
    switchMenuStockGroupIds.value = groupIds
  }

  switchMenuLoading.value = false
}

function isSwitchGroupSelected(groupId: number) {
  return switchMenuStockGroupIds.value.includes(groupId)
}

function isSwitchGroupDisabled(groupId: number) {
  return switchMenuLoading.value ||
    switchMenuOperatingGroupId.value != null ||
    (isSwitchGroupSelected(groupId) && switchMenuStockGroupIds.value.length <= 1)
}

async function toggleSwitchGroup(groupId: number) {
  const stockId = switchMenuStockId.value

  if (stockId == null || isSwitchGroupDisabled(groupId)) {
    return
  }

  const selected = isSwitchGroupSelected(groupId)
  switchMenuOperatingGroupId.value = groupId

  if (selected) {
    await store.removeFromGroup(stockId, groupId)
  } else {
    await store.addToGroup(stockId, groupId)
  }

  if (showSwitchGroupMenu.value && switchMenuStockId.value === stockId) {
    switchMenuStockGroupIds.value = await store.getStockGroupIds(stockId)
  }

  switchMenuOperatingGroupId.value = null
}

function resetGroupDragState() {
  draggedGroupId.value = null
  dragOverGroupId.value = null
}

function stopStockAutoScroll() {
  stockAutoScrollDirection = 0
  stockAutoScrollSpeed = 0

  if (stockAutoScrollFrame != null) {
    window.cancelAnimationFrame(stockAutoScrollFrame)
    stockAutoScrollFrame = null
  }
}

function tickStockAutoScroll() {
  const scrollContainer = watchlistListRef.value

  if (!scrollContainer || stockAutoScrollDirection === 0 || stockAutoScrollSpeed <= 0) {
    stopStockAutoScroll()
    return
  }

  scrollContainer.scrollLeft += stockAutoScrollDirection * stockAutoScrollSpeed
  stockAutoScrollFrame = window.requestAnimationFrame(tickStockAutoScroll)
}

function updateStockAutoScroll(event: DragEvent) {
  const scrollContainer = watchlistListRef.value

  if (!scrollContainer) {
    stopStockAutoScroll()
    return
  }

  const edgeSize = 48
  const maxSpeed = 18
  const bounds = scrollContainer.getBoundingClientRect()
  const distanceToLeft = event.clientX - bounds.left
  const distanceToRight = bounds.right - event.clientX
  let nextDirection = 0
  let nextSpeed = 0

  if (distanceToLeft >= 0 && distanceToLeft < edgeSize) {
    nextDirection = -1
    nextSpeed = Math.max(4, ((edgeSize - distanceToLeft) / edgeSize) * maxSpeed)
  } else if (distanceToRight >= 0 && distanceToRight < edgeSize) {
    nextDirection = 1
    nextSpeed = Math.max(4, ((edgeSize - distanceToRight) / edgeSize) * maxSpeed)
  }

  const maxScrollLeft = scrollContainer.scrollWidth - scrollContainer.clientWidth

  if (
    nextDirection === 0 ||
    (nextDirection < 0 && scrollContainer.scrollLeft <= 0) ||
    (nextDirection > 0 && scrollContainer.scrollLeft >= maxScrollLeft)
  ) {
    stopStockAutoScroll()
    return
  }

  stockAutoScrollDirection = nextDirection
  stockAutoScrollSpeed = nextSpeed

  if (stockAutoScrollFrame == null) {
    stockAutoScrollFrame = window.requestAnimationFrame(tickStockAutoScroll)
  }
}

function resetStockDragState() {
  draggedStockId.value = null
  dragOverStockId.value = null
  stopStockAutoScroll()
}

function onGroupDragStart(event: DragEvent, group: { id: number; default: boolean }) {
  if (isDefaultGroup(group)) {
    event.preventDefault()
    return
  }

  draggedGroupId.value = group.id
  dragOverGroupId.value = group.id
  event.dataTransfer?.setData('text/plain', String(group.id))

  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
  }
}

function onGroupDragOver(event: DragEvent, group: { id: number; default: boolean }) {
  if (draggedGroupId.value == null || draggedGroupId.value === group.id || isDefaultGroup(group)) {
    return
  }

  event.preventDefault()
  dragOverGroupId.value = group.id

  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
}

function onGroupDrop(event: DragEvent, targetGroup: { id: number; default: boolean }) {
  event.preventDefault()

  const sourceGroupId = draggedGroupId.value
  resetGroupDragState()

  if (sourceGroupId == null || sourceGroupId === targetGroup.id || isDefaultGroup(targetGroup)) {
    return
  }

  const currentGroupIds = store.watchlistGroups.value
    .filter((group) => !isDefaultGroup(group))
    .map((group) => group.id)
  const sourceIndex = currentGroupIds.indexOf(sourceGroupId)
  const targetIndex = currentGroupIds.indexOf(targetGroup.id)

  if (sourceIndex < 0 || targetIndex < 0) {
    return
  }

  const nextGroupIds = [...currentGroupIds]
  const [movedGroupId] = nextGroupIds.splice(sourceIndex, 1)

  if (movedGroupId == null) {
    return
  }

  nextGroupIds.splice(targetIndex, 0, movedGroupId)

  void store.reorderWatchlistGroups(nextGroupIds)
}

function onStockDragStart(event: DragEvent, stockId: number) {
  if (isWatchlistSorted.value) {
    event.preventDefault()
    return
  }

  draggedStockId.value = stockId
  dragOverStockId.value = stockId
  event.dataTransfer?.setData('text/plain', String(stockId))

  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
  }
}

function onStockDragOver(event: DragEvent, stockId: number) {
  if (isWatchlistSorted.value || draggedStockId.value == null) {
    return
  }

  event.preventDefault()
  updateStockAutoScroll(event)

  if (draggedStockId.value !== stockId) {
    dragOverStockId.value = stockId
  }

  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
}

function onStockDrop(event: DragEvent, targetStockId: number) {
  event.preventDefault()

  if (isWatchlistSorted.value) {
    resetStockDragState()
    return
  }

  const sourceStockId = draggedStockId.value
  const groupId = store.activeWatchlistGroupId.value

  resetStockDragState()

  if (sourceStockId == null || sourceStockId === targetStockId || groupId == null) {
    return
  }

  const currentStockIds = store.watchlist.value.map((stock) => stock.id)
  const sourceIndex = currentStockIds.indexOf(sourceStockId)
  const targetIndex = currentStockIds.indexOf(targetStockId)

  if (sourceIndex < 0 || targetIndex < 0) {
    return
  }

  const nextStockIds = [...currentStockIds]
  const [movedStockId] = nextStockIds.splice(sourceIndex, 1)

  if (movedStockId == null) {
    return
  }

  nextStockIds.splice(targetIndex, 0, movedStockId)

  void store.reorderWatchlistGroupStocks(groupId, nextStockIds)
}

function pinStockToTop(stockId: number) {
  if (isWatchlistSorted.value) {
    return
  }

  const groupId = store.activeWatchlistGroupId.value
  const currentStockIds = store.watchlist.value.map((stock) => stock.id)
  const currentIndex = currentStockIds.indexOf(stockId)

  if (groupId == null || currentIndex <= 0) {
    return
  }

  const nextStockIds = [
    stockId,
    ...currentStockIds.filter((currentStockId) => currentStockId !== stockId)
  ]

  void store.reorderWatchlistGroupStocks(groupId, nextStockIds)
}

watch(() => store.activeWatchlistGroupId.value, closeSwitchGroupMenu)
watch(() => store.watchlist.value.length, closeSwitchGroupMenu)
watch(() => store.activeWatchlistGroupId.value, resetStockDragState)
watch(() => store.watchlist.value.length, resetStockDragState)
watch(() => store.watchlistGroups.value.length, resetGroupDragState)
onUnmounted(stopStockAutoScroll)
</script>

<template>
  <div class="page-stack">
    <PanelCard>
      <SectionHeader eyebrow="My Watchlist" title="自选分组" subtitle="通过分组 Tab 管理你的关注标的。">
        <template #extra>
          <span class="watchlist-badge">已关注 {{ store.watchlistCount.value }} 只</span>
        </template>
      </SectionHeader>

      <div class="flex between">
        <div class="group-tabs">
          <button v-for="group in store.watchlistGroups.value" :key="group.id" class="group-tab"
            :class="{
              'group-tab--active': group.id === store.activeWatchlistGroupId.value,
              'group-tab--fixed': isDefaultGroup(group),
              'group-tab--dragging': draggedGroupId === group.id,
              'group-tab--drag-over': dragOverGroupId === group.id && draggedGroupId !== group.id
            }"
            type="button"
            :draggable="!isDefaultGroup(group)"
            @click="chooseGroup(group.id)"
            @dragstart="onGroupDragStart($event, group)"
            @dragover="onGroupDragOver($event, group)"
            @drop="onGroupDrop($event, group)"
            @dragend="resetGroupDragState">
            <span class="group-tab-main">
              <span class="group-tab-name">{{ group.name }}</span>
              <span class="group-tab-count">{{ group.stockCount }}</span>
            </span>
            <span class="group-tab-average" :class="`group-tab-average--${store.changeClass(group.averageChangePercent)}`">
              {{ store.formatPercent(group.averageChangePercent) }}
            </span>
          </button>
        </div>
        <div class="group-actions">
          <button class="btn btn-primary" type="button" @click="openCreateGroupDialog">新增分组</button>
          <button class="btn" type="button" @click="openRenameGroupDialog">重命名</button>
          <button class="btn btn-danger" type="button" @click="deleteCurrentGroup">删除分组</button>
        </div>
      </div>
    </PanelCard>

    <div v-if="showCreateGroupDialog" class="dialog-backdrop" @click.self="closeCreateGroupDialog">
      <div class="dialog-card">
        <h3 class="dialog-title">新增分组</h3>
        <input v-model="newGroupName" class="field-control" placeholder="请输入分组名称" @keydown.enter="createGroup" />
        <div class="dialog-actions">
          <button class="btn" type="button" @click="closeCreateGroupDialog">取消</button>
          <button class="btn btn-primary" type="button" @click="createGroup">确认新增</button>
        </div>
      </div>
    </div>

    <div v-if="showRenameGroupDialog" class="dialog-backdrop" @click.self="closeRenameGroupDialog">
      <div class="dialog-card">
        <h3 class="dialog-title">重命名分组</h3>
        <input v-model="renameGroupName" class="field-control" placeholder="请输入新的分组名称"
          @keydown.enter="renameCurrentGroup" />
        <div class="dialog-actions">
          <button class="btn" type="button" @click="closeRenameGroupDialog">取消</button>
          <button class="btn btn-primary" type="button" @click="renameCurrentGroup">确认重命名</button>
        </div>
      </div>
    </div>

    <EmptyState v-if="store.watchlistLoading.value" message="分组加载中..." />

    <EmptyState v-else-if="store.watchlistGroups.value.length === 0" message="还没有分组，请先创建一个分组。" />

    <EmptyState v-else-if="store.watchlist.value.length === 0" message="当前分组没有股票，去选股列表添加，或把其他分组股票加入到这里。" />

    <div v-else ref="watchlistListRef" class="watchlist-list">
      <div class="watchlist-summary">
        <div class="watchlist-summary-row">
          <span class="watchlist-summary-label">行业</span>
          <span
            v-for="[industry, count] in activeIndustryCountEntries"
            :key="industry"
            class="group-summary-chip"
            :title="`${industry} ${count}`"
          >
            {{ industry }} {{ count }}
          </span>
          <span v-if="activeIndustryCountEntries.length === 0" class="group-summary-empty">暂无行业</span>
        </div>
        <div class="watchlist-summary-row">
          <span class="watchlist-summary-label">主概念</span>
          <span
            v-for="[concept, count] in activePrimaryConceptCountEntries"
            :key="concept"
            class="group-summary-chip group-summary-chip--concept"
            :title="`${concept} ${count}`"
          >
            {{ concept }} {{ count }}
          </span>
          <span v-if="activePrimaryConceptCountEntries.length === 0" class="group-summary-empty">暂无主概念</span>
        </div>
      </div>
      <div class="watchlist-table">
        <div class="watchlist-row watchlist-table-header" role="row">
          <div>股票</div>
          <button class="watchlist-header-sort watchlist-cell--right" type="button" @click="toggleWatchlistSort('latestPrice')">
            最新价{{ watchlistSortIndicator('latestPrice') }}
          </button>
          <button class="watchlist-header-sort" type="button" @click="toggleWatchlistSort('changePercent')">
            涨跌幅{{ watchlistSortIndicator('changePercent') }}
          </button>
          <button class="watchlist-header-sort" type="button" @click="toggleWatchlistSort('industry')">
            行业{{ watchlistSortIndicator('industry') }}
          </button>
          <div>概念</div>
          <div class="watchlist-cell--center">操作</div>
        </div>
      <article v-for="(stock, stockIndex) in displayedWatchlist" :key="stock.id" class="watchlist-item"
        :draggable="!isWatchlistSorted"
        :class="{
          'watchlist-item--dragging': draggedStockId === stock.id,
          'watchlist-item--drag-over': dragOverStockId === stock.id && draggedStockId !== stock.id,
          'watchlist-item--sort-active': isWatchlistSorted
        }"
        @dragstart="onStockDragStart($event, stock.id)"
        @dragover="onStockDragOver($event, stock.id)"
        @drop="onStockDrop($event, stock.id)"
        @dragend="resetStockDragState"
        @contextmenu="openSwitchGroupMenu($event, stock.id)">
          <button class="watchlist-stock-cell pointer" type="button" @click="openDetail(stock.id)">
            <div class="watchlist-stock-title">
              <img v-if="logoUrl(stock)" class="stock-logo" :src="logoUrl(stock) ?? ''" :alt="`${stock.name} logo`" loading="lazy" />
              <span v-else class="stock-logo stock-logo--fallback">{{ logoFallback(stock) }}</span>
              <div class="watchlist-stock-copy">
                <div class="watchlist-item-name">{{ stock.name }}</div>
                <div class="watchlist-stock-meta">
                  <BoardTag :label="stock.boardType || '--'" :variant="store.boardClass(stock.boardType)" />
                  <span class="watchlist-item-symbol">{{ stock.symbol }}</span>
                </div>
              </div>
            </div>
          </button>
          <div class="watchlist-item-stats">
            <strong class="stat-value">{{ store.formatPrice(stock.latestPrice) }}</strong>
          </div>
          <PriceChangeChip :tone="store.changeClass(stock.changePercent)"
            :value="store.formatPercent(stock.changePercent)" />
          <div class="watchlist-cell watchlist-cell--text" :title="stock.industry || '未分类行业'">
            <span>{{ stock.industry || '未分类行业' }}</span>
          </div>

          <div class="watchlist-cell watchlist-cell--concepts" :title="stock.primaryConcept || stock.concepts[0] || '暂无概念'">
            <ConceptChipGroup :concepts="stock.concepts" :primary-concept="stock.primaryConcept" :max="1" />
          </div>

        <div class="item-actions">
          <button class="btn btn-icon watchlist-pin-btn" type="button" aria-label="置顶排序" title="置顶排序"
            :disabled="stockIndex === 0 || isWatchlistSorted"
            @click="pinStockToTop(stock.id)">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M12 19V5" />
              <path d="M6 11l6-6 6 6" />
              <path d="M5 21h14" />
            </svg>
          </button>
          <button class="btn btn-icon watchlist-remove-btn" type="button" aria-label="取消关注" title="取消关注"
            @click="store.removeFromWatchlist(stock.id)">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M18 6L6 18" />
              <path d="M6 6L18 18" />
            </svg>
          </button>
        </div>
      </article>
      </div>
    </div>

    <template v-if="showSwitchGroupMenu">
      <div class="watchlist-context-menu-mask" @click="closeSwitchGroupMenu" @contextmenu.prevent="closeSwitchGroupMenu" />
      <div class="watchlist-context-menu" :style="{ left: `${switchMenuX}px`, top: `${switchMenuY}px` }" @click.stop
        @contextmenu.prevent>
        <div class="watchlist-context-menu__title">管理分组</div>
        <button v-for="group in switchGroupOptions" :key="group.id" class="watchlist-context-menu__item" type="button"
          :class="{ 'watchlist-context-menu__item--selected': isSwitchGroupSelected(group.id) }"
          :disabled="isSwitchGroupDisabled(group.id)"
          @click="toggleSwitchGroup(group.id)">
          <span class="watchlist-context-menu__check" aria-hidden="true">
            <svg v-if="isSwitchGroupSelected(group.id)" viewBox="0 0 24 24">
              <path d="M20 6L9 17l-5-5" />
            </svg>
          </span>
          <span class="watchlist-context-menu__label">{{ group.name }}</span>
        </button>
        <div v-if="switchGroupOptions.length === 0" class="watchlist-context-menu__empty">暂无可切换分组</div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.group-tabs {
  display: flex;
  align-items: stretch;
  gap: var(--space-2);
  margin-top: 12px;
  overflow-x: auto;
  padding-bottom: var(--space-1);
}

.group-tab {
  position: relative;
  min-width: 180px;
  max-width: 240px;
  min-height: 64px;
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
  color: var(--text-primary);
  display: inline-flex;
  flex-direction: column;
  align-items: stretch;
  justify-content: space-between;
  gap: var(--space-2);
  flex: 0 0 auto;
  cursor: pointer;
}

.group-tab:not(.group-tab--fixed) {
  cursor: grab;
}

.group-tab--active {
  border-color: var(--primary-500);
  color: var(--primary-600);
  background: #f4f9ff;
}

.group-tab--fixed {
  cursor: pointer;
}

.group-tab--dragging {
  opacity: 0.54;
  cursor: grabbing;
  user-select: none;
}

.group-tab--drag-over {
  border-color: var(--primary-500);
  box-shadow: 0 4px 12px rgba(29, 155, 240, 0.16);
}

.group-tab-main {
  display: flex;
  align-items: center;
  min-width: 0;
  justify-content: space-between;
  gap: var(--space-2);
}

.group-tab-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: var(--font-weight-semibold);
}

.group-tab-count {
  min-width: 22px;
  height: 22px;
  padding: 0 var(--space-2);
  border-radius: var(--radius-pill);
  background: var(--bg-soft);
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.group-tab-average {
  min-width: 0;
  max-width: 100%;
  align-self: flex-start;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  line-height: 1.4;
}

.group-tab-average--up {
  color: var(--up-500);
}

.group-tab-average--down {
  color: var(--down-500);
}

.group-tab-average--neutral {
  color: var(--text-muted);
}

.watchlist-summary {
  display: grid;
  gap: var(--space-1);
  padding: 0 var(--space-1);
}

.watchlist-summary-row {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  flex-wrap: wrap;
  min-height: 30px;
}

.watchlist-summary-label {
  min-width: 44px;
  color: var(--text-muted);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
}

.group-summary-chip,
.group-summary-empty {
  max-width: min(240px, 100%);
  min-height: 22px;
  display: inline-flex;
  align-items: center;
  border-radius: var(--radius-pill);
  padding: 0 var(--space-2);
  font-size: var(--font-size-xs);
  white-space: nowrap;
}

.group-summary-chip {
  border: 1px solid #dbe7ff;
  background: #eef4ff;
  color: #3730a3;
  overflow: hidden;
  text-overflow: ellipsis;
}

.group-summary-chip--concept {
  border-color: #d8f3dc;
  background: #f0fdf4;
  color: #166534;
}

.group-summary-empty {
  color: var(--text-muted);
}

.watchlist-list {
  overflow-x: auto;
}

.watchlist-table {
  min-width: 820px;
  display: grid;
  gap: var(--space-2);
}

.watchlist-row,
.watchlist-item {
  display: grid;
  grid-template-columns: 180px 110px 96px 120px minmax(0, 1fr) 88px;
  align-items: center;
  column-gap: var(--space-4);
}

.watchlist-table-header {
  padding: 0 var(--space-3);
  color: var(--text-muted);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  text-transform: uppercase;
}

.watchlist-header-sort {
  min-width: 0;
  border: 0;
  background: transparent;
  color: inherit;
  padding: 0;
  font: inherit;
  text-align: left;
  text-transform: inherit;
  cursor: pointer;
}

.watchlist-header-sort:hover {
  color: var(--primary-600);
}

.watchlist-header-sort.watchlist-cell--right {
  text-align: right;
}

.watchlist-item {
  min-width: 820px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  padding: var(--space-3);
  background: linear-gradient(160deg, var(--bg-surface) 0%, #fafcff 100%);
  transition: var(--transition-fast);
  cursor: grab;
}

.watchlist-item--sort-active {
  cursor: default;
}

.watchlist-item:hover {
  border-color: var(--border-strong);
  box-shadow: var(--shadow-hover);
}

.watchlist-item--dragging {
  opacity: 0.54;
  cursor: grabbing;
  user-select: none;
}

.watchlist-item--drag-over {
  border-color: var(--primary-500);
  box-shadow: 0 4px 12px rgba(29, 155, 240, 0.16);
}

.watchlist-stock-cell {
  min-width: 0;
  border: 0;
  background: transparent;
  color: inherit;
  padding: 0;
  display: grid;
  gap: 6px;
  text-align: left;
}

.watchlist-stock-cell :deep(.stock-logo) {
  width: 34px;
  height: 34px;
}

.watchlist-stock-meta {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  min-width: 0;
}

.watchlist-stock-meta :deep(.board-tag) {
  flex: 0 0 auto;
  min-width: auto;
  min-height: 18px;
  border-radius: 0;
  padding: 0 4px;
  font-size: 11px;
  line-height: 18px;
}

.watchlist-cell,
.watchlist-item-stats,
.item-actions {
  min-width: 0;
}

.watchlist-cell--right,
.watchlist-item-stats {
  text-align: right;
  justify-items: end;
}

.watchlist-cell--center,
.item-actions {
  justify-content: center;
}

.watchlist-cell--text,
.watchlist-cell--concepts {
  overflow: hidden;
}

.watchlist-cell--text span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.watchlist-cell--concepts :deep(.concept-chip-group) {
  justify-content: flex-start;
  flex-wrap: nowrap;
  overflow: hidden;
  min-width: 0;
}

.watchlist-cell--concepts :deep(.concept-chip) {
  max-width: min(100%, 240px);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.watchlist-muted {
  color: var(--text-muted);
}

.watchlist-pin-btn {
  color: var(--primary-600);
}

.watchlist-remove-btn {
  border: 0;
  color: var(--text-secondary);
}

.watchlist-context-menu-mask {
  position: fixed;
  inset: 0;
  z-index: 1090;
}

.watchlist-context-menu {
  position: fixed;
  z-index: 1100;
  min-width: 196px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
  box-shadow: var(--shadow-hover);
  padding: 8px;
  display: grid;
  gap: 4px;
}

.watchlist-context-menu__title {
  padding: 6px 8px;
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  color: var(--text-muted);
}

.watchlist-context-menu__item {
  height: 34px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-primary);
  text-align: left;
  padding: 0 8px;
  cursor: pointer;
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  align-items: center;
  gap: 6px;
}

.watchlist-context-menu__item:hover:not(:disabled) {
  background: var(--bg-soft);
  color: var(--primary-600);
}

.watchlist-context-menu__item--selected {
  color: var(--primary-600);
}

.watchlist-context-menu__item:disabled {
  cursor: default;
  color: var(--text-muted);
}

.watchlist-context-menu__check {
  width: 18px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.watchlist-context-menu__check svg {
  width: 15px;
  height: 15px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.watchlist-context-menu__label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.watchlist-context-menu__empty {
  padding: 8px;
  font-size: var(--font-size-xs);
  color: var(--text-muted);
}
</style>
