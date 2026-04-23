<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useWatchlist } from '@/features/watchlist/composables/useWatchlist'
import PanelCard from '@/shared/components/layout/PanelCard.vue'
import SectionHeader from '@/shared/components/layout/SectionHeader.vue'
import PriceChangeChip from '@/shared/components/display/PriceChangeChip.vue'
import ConceptChipGroup from '@/shared/components/display/ConceptChipGroup.vue'
import EmptyState from '@/shared/components/feedback/EmptyState.vue'

const router = useRouter()
const store = useWatchlist()

const newGroupName = ref('')
const showCreateGroupDialog = ref(false)
const showRenameGroupDialog = ref(false)
const renameGroupName = ref('')
const showSwitchGroupMenu = ref(false)
const switchMenuStockId = ref<number | null>(null)
const switchMenuX = ref(0)
const switchMenuY = ref(0)

const switchGroupOptions = computed(() => {
  if (store.activeWatchlistGroupId.value == null) {
    return store.watchlistGroups.value
  }

  return store.watchlistGroups.value.filter((group) => group.id !== store.activeWatchlistGroupId.value)
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

function chooseGroup(groupId: number) {
  void store.setActiveWatchlistGroup(groupId)
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
  showSwitchGroupMenu.value = true
}

function moveToGroup(groupId: number) {
  const stockId = switchMenuStockId.value

  if (stockId == null) {
    return
  }

  closeSwitchGroupMenu()
  void store.moveStockToGroup(stockId, groupId)
}

watch(() => store.activeWatchlistGroupId.value, closeSwitchGroupMenu)
watch(() => store.watchlist.value.length, closeSwitchGroupMenu)
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
            :class="{ 'group-tab--active': group.id === store.activeWatchlistGroupId.value }" type="button"
            @click="chooseGroup(group.id)">
            <span>{{ group.name }}</span>
            <span class="group-tab-count">{{ group.stockCount }}</span>
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

    <div v-else class="watchlist-list">
      <article v-for="stock in store.watchlist.value" :key="stock.id" class="watchlist-item"
        @contextmenu="openSwitchGroupMenu($event, stock.id)">
        <div class="watchlist-main flex item-center">
          <div class="watchlist-item-head pointer" @click="openDetail(stock.id)">
            <div class="watchlist-item-name">{{ stock.name }}</div>
            <div class="flex item-center">
              <div class="watchlist-item-market">{{ stock.market }}</div>
              <div class="watchlist-item-symbol">{{ stock.symbol }}</div>
            </div>
          </div>
          <div class="watchlist-item-stats">
            <strong class="stat-value">{{ store.formatPrice(stock.latestPrice) }}</strong>
          </div>
          <PriceChangeChip :tone="store.changeClass(stock.changePercent)"
            :value="store.formatPercent(stock.changePercent)" />
          <div>
            <span v-if="stock.boardType" class="meta-chip">{{ stock.boardType }}</span>
          </div>
          <div>
            <span>{{ stock.industry || '未分类行业' }}</span>
          </div>

          <ConceptChipGroup :concepts="stock.concepts" :max="10" />
        </div>

        <div class="item-actions">
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

    <template v-if="showSwitchGroupMenu">
      <div class="watchlist-context-menu-mask" @click="closeSwitchGroupMenu" @contextmenu.prevent="closeSwitchGroupMenu" />
      <div class="watchlist-context-menu" :style="{ left: `${switchMenuX}px`, top: `${switchMenuY}px` }" @click.stop
        @contextmenu.prevent>
        <div class="watchlist-context-menu__title">切换分组</div>
        <button v-for="group in switchGroupOptions" :key="group.id" class="watchlist-context-menu__item" type="button"
          @click="moveToGroup(group.id)">
          {{ group.name }}
        </button>
        <div v-if="switchGroupOptions.length === 0" class="watchlist-context-menu__empty">暂无可切换分组</div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.group-tabs {
  display: flex;
  align-items: center;
  gap: 0;
  margin-top: 12px;
  border-bottom: 1px solid #d1d5db;
  overflow-x: auto;
  white-space: nowrap;
}

.group-tab {
  position: relative;
  height: 46px;
  padding: 0 20px;
  border: 0;
  background: transparent;
  color: #111827;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: 0 0 auto;
  overflow: hidden;
}

.group-tab--active {
  color: #2563eb;
}

.group-tab--active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  background: #2563eb;
}

.group-tab-count {
  font-size: 0.8rem;
  color: #6b7280;
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
}

.watchlist-context-menu__item:hover {
  background: var(--bg-soft);
  color: var(--primary-600);
}

.watchlist-context-menu__empty {
  padding: 8px;
  font-size: var(--font-size-xs);
  color: var(--text-muted);
}
</style>
