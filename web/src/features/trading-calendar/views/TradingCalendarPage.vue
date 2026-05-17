<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { tradingCalendarApi, type TradingCalendarDay } from '@/api/tradingCalendar'
import PanelCard from '@/shared/components/layout/PanelCard.vue'
import SectionHeader from '@/shared/components/layout/SectionHeader.vue'
import LoadingState from '@/shared/components/feedback/LoadingState.vue'
import { formatDate } from '@/shared/lib/formatters'

interface CalendarCell {
  date: Date
  dateKey: string
  dayNumber: number
  inMonth: boolean
  isToday: boolean
  defaultOpen: boolean
  open: boolean
  manualDay: TradingCalendarDay | null
  saving: boolean
}

const today = new Date()
const visibleMonth = ref(new Date(today.getFullYear(), today.getMonth(), 1))
const loading = ref(false)
const error = ref('')
const message = ref('')
const manualDays = ref<TradingCalendarDay[]>([])
const latestOpenDate = ref<string | null>(null)
const savingDateKey = ref<string | null>(null)
const contextMenu = ref<{
  visible: boolean
  x: number
  y: number
  cell: CalendarCell | null
}>({
  visible: false,
  x: 0,
  y: 0,
  cell: null
})

const weekdayLabels = ['一', '二', '三', '四', '五', '六', '日']

const visibleMonthLabel = computed(() => {
  const year = visibleMonth.value.getFullYear()
  const month = String(visibleMonth.value.getMonth() + 1).padStart(2, '0')
  return `${year} 年 ${month} 月`
})

const monthStart = computed(() => new Date(visibleMonth.value.getFullYear(), visibleMonth.value.getMonth(), 1))
const gridStart = computed(() => addDays(monthStart.value, -mondayBasedWeekday(monthStart.value)))
const gridEnd = computed(() => addDays(gridStart.value, 41))

const manualDayByDate = computed(() => new Map(manualDays.value.map((day) => [day.tradeDate, day])))

const calendarCells = computed<CalendarCell[]>(() => {
  return Array.from({ length: 42 }, (_, index) => {
    const date = addDays(gridStart.value, index)
    const dateKey = toDateInputValue(date)
    const manualDay = manualDayByDate.value.get(dateKey) ?? null
    const defaultOpen = isDefaultOpen(date)
    return {
      date,
      dateKey,
      dayNumber: date.getDate(),
      inMonth: date.getMonth() === visibleMonth.value.getMonth(),
      isToday: dateKey === toDateInputValue(today),
      defaultOpen,
      open: manualDay?.open ?? defaultOpen,
      manualDay,
      saving: savingDateKey.value === dateKey
    }
  })
})

function toDateInputValue(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function addDays(date: Date, days: number) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days)
}

function mondayBasedWeekday(date: Date) {
  return (date.getDay() + 6) % 7
}

function isDefaultOpen(date: Date) {
  const day = date.getDay()
  return day !== 0 && day !== 6
}

function statusLabel(open: boolean) {
  return open ? '开市' : '休市'
}

function defaultLabel(cell: CalendarCell) {
  return cell.defaultOpen ? '默认工作日' : '默认周末'
}

function resolveRemark(cell: CalendarCell) {
  if (cell.manualDay?.holiday) {
    return cell.manualDay.remark || '节假日'
  }
  if (cell.manualDay?.remark) {
    return cell.manualDay.remark
  }
  return cell.manualDay ? '人工指定' : defaultLabel(cell)
}

async function fetchLatestOpenDate() {
  try {
    const response = await tradingCalendarApi.getLatestOpen()
    latestOpenDate.value = response.data.tradeDate
  } catch {
    latestOpenDate.value = null
  }
}

async function fetchDays() {
  loading.value = true
  error.value = ''

  try {
    const response = await tradingCalendarApi.getDays({
      startDate: toDateInputValue(gridStart.value),
      endDate: toDateInputValue(gridEnd.value),
      sortDirection: 'ASC'
    })
    manualDays.value = response.data
    await fetchLatestOpenDate()
  } catch {
    error.value = '加载交易日日历失败，请确认后端服务是否已启动。'
  } finally {
    loading.value = false
  }
}

function previousMonth() {
  visibleMonth.value = new Date(visibleMonth.value.getFullYear(), visibleMonth.value.getMonth() - 1, 1)
  void fetchDays()
}

function nextMonth() {
  visibleMonth.value = new Date(visibleMonth.value.getFullYear(), visibleMonth.value.getMonth() + 1, 1)
  void fetchDays()
}

function goToCurrentMonth() {
  visibleMonth.value = new Date(today.getFullYear(), today.getMonth(), 1)
  void fetchDays()
}

function openContextMenu(event: MouseEvent, cell: CalendarCell) {
  if (savingDateKey.value != null) {
    return
  }

  contextMenu.value = {
    visible: true,
    x: event.clientX,
    y: event.clientY,
    cell
  }
}

function closeContextMenu() {
  contextMenu.value.visible = false
  contextMenu.value.cell = null
}

async function setContextMenuDayOpen(open: boolean) {
  const cell = contextMenu.value.cell
  closeContextMenu()

  if (!cell) {
    return
  }

  await saveDayState(cell, open)
}

async function markContextMenuHoliday() {
  const cell = contextMenu.value.cell
  closeContextMenu()

  if (!cell) {
    return
  }

  const holidayName = window.prompt('请输入节假日名称', cell.manualDay?.remark || '')
  if (holidayName == null) {
    return
  }

  const normalizedHolidayName = holidayName.trim() || '节假日'
  await saveDayState(cell, false, true, normalizedHolidayName)
}

async function unmarkContextMenuHoliday() {
  const cell = contextMenu.value.cell
  closeContextMenu()

  if (!cell || !cell.manualDay || savingDateKey.value != null) {
    return
  }

  savingDateKey.value = cell.dateKey
  error.value = ''
  message.value = ''

  try {
    await tradingCalendarApi.updateDay(cell.manualDay.id, {
      holiday: false,
      remark: cell.manualDay.remark === '节假日' ? '' : cell.manualDay.remark
    })
    message.value = `${formatDate(cell.dateKey)} 已取消节假日标记。`
    await fetchDays()
  } catch {
    error.value = '取消节假日标记失败，请稍后重试。'
  } finally {
    savingDateKey.value = null
  }
}

async function resetContextMenuDay() {
  const cell = contextMenu.value.cell
  closeContextMenu()

  if (!cell || !cell.manualDay || savingDateKey.value != null) {
    return
  }

  savingDateKey.value = cell.dateKey
  error.value = ''
  message.value = ''

  try {
    await tradingCalendarApi.deleteDay(cell.manualDay.id)
    message.value = `${formatDate(cell.dateKey)} 已恢复默认规则。`
    await fetchDays()
  } catch {
    error.value = '恢复默认规则失败，请稍后重试。'
  } finally {
    savingDateKey.value = null
  }
}

async function saveDayState(cell: CalendarCell, nextOpen: boolean, holiday = false, remark?: string) {
  if (savingDateKey.value != null) {
    return
  }

  savingDateKey.value = cell.dateKey
  error.value = ''
  message.value = ''

  const nextHoliday = holiday && !nextOpen
  const nextRemark = remark ?? (nextHoliday ? '节假日' : nextOpen ? '人工开市' : '人工休市')
  const nextMatchesDefault = nextOpen === cell.defaultOpen && !nextHoliday

  try {
    if (cell.manualDay && nextMatchesDefault) {
      await tradingCalendarApi.deleteDay(cell.manualDay.id)
      message.value = `${formatDate(cell.dateKey)} 已恢复默认规则。`
    } else if (cell.manualDay) {
      await tradingCalendarApi.updateDay(cell.manualDay.id, {
        open: nextOpen,
        holiday: nextHoliday,
        remark: nextRemark
      })
      message.value = `${formatDate(cell.dateKey)} 已设为${nextHoliday ? '节假日' : statusLabel(nextOpen)}。`
    } else {
      await tradingCalendarApi.createDay({
        tradeDate: cell.dateKey,
        open: nextOpen,
        holiday: nextHoliday,
        remark: nextRemark
      })
      message.value = `${formatDate(cell.dateKey)} 已设为${nextHoliday ? '节假日' : statusLabel(nextOpen)}。`
    }
    await fetchDays()
  } catch {
    error.value = '保存交易日状态失败，请稍后重试。'
  } finally {
    savingDateKey.value = null
  }
}

onMounted(() => {
  window.addEventListener('click', closeContextMenu)
  window.addEventListener('scroll', closeContextMenu, true)
  void fetchDays()
})

onBeforeUnmount(() => {
  window.removeEventListener('click', closeContextMenu)
  window.removeEventListener('scroll', closeContextMenu, true)
})
</script>

<template>
  <div class="page-stack trading-calendar-page">
    <div v-if="message" class="banner banner--success">{{ message }}</div>
    <div v-if="error" class="state-block state-block--error">{{ error }}</div>

    <PanelCard as="main" class="calendar-panel">
      <div class="calendar-header">
        <SectionHeader eyebrow="Trading Calendar" title="交易日历" subtitle="工作日默认开市，周末默认休市；右键日期切换人工指定。" />
        <div class="calendar-actions">
          <button class="btn" type="button" @click="previousMonth">上月</button>
          <button class="btn" type="button" @click="goToCurrentMonth">本月</button>
          <button class="btn" type="button" @click="nextMonth">下月</button>
        </div>
      </div>

      <div class="calendar-month-title">{{ visibleMonthLabel }}</div>

      <LoadingState v-if="loading" text="日历加载中..." />
      <div v-else class="month-calendar" @contextmenu.prevent>
        <div v-for="label in weekdayLabels" :key="label" class="weekday-cell">{{ label }}</div>
        <button
          v-for="cell in calendarCells"
          :key="cell.dateKey"
          class="calendar-day"
          :class="{
            'calendar-day--muted': !cell.inMonth,
            'calendar-day--today': cell.isToday,
            'calendar-day--closed': !cell.open,
            'calendar-day--manual': cell.manualDay,
            'calendar-day--holiday': cell.manualDay?.holiday,
            'calendar-day--saving': cell.saving
          }"
          type="button"
          :disabled="savingDateKey != null"
          :title="`${formatDate(cell.dateKey)}，右键打开菜单`"
          @contextmenu.prevent="openContextMenu($event, cell)"
        >
          <span class="calendar-day-number">{{ cell.dayNumber }}</span>
          <span class="calendar-day-status">{{ cell.saving ? '保存中' : cell.manualDay?.holiday ? '节假日' : statusLabel(cell.open) }}</span>
          <span class="calendar-day-source">{{ resolveRemark(cell) }}</span>
        </button>
      </div>
    </PanelCard>

    <div
      v-if="contextMenu.visible && contextMenu.cell"
      class="calendar-context-menu"
      :style="{ left: `${contextMenu.x}px`, top: `${contextMenu.y}px` }"
      @click.stop
      @contextmenu.prevent
    >
      <button type="button" class="calendar-context-item" :disabled="contextMenu.cell.open" @click="setContextMenuDayOpen(true)">
        设为开市
      </button>
      <button type="button" class="calendar-context-item" :disabled="!contextMenu.cell.open" @click="setContextMenuDayOpen(false)">
        设为休市
      </button>
      <button type="button" class="calendar-context-item" :disabled="!!contextMenu.cell.manualDay?.holiday" @click="markContextMenuHoliday">
        标记节假日
      </button>
      <button type="button" class="calendar-context-item" :disabled="!contextMenu.cell.manualDay?.holiday" @click="unmarkContextMenuHoliday">
        取消节假日
      </button>
      <button
        type="button"
        class="calendar-context-item"
        :disabled="!contextMenu.cell.manualDay"
        @click="resetContextMenuDay"
      >
        恢复默认
      </button>
    </div>
  </div>
</template>

<style scoped>
.trading-calendar-page {
  gap: var(--space-4);
}

.calendar-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
}

.calendar-actions {
  display: inline-flex;
  gap: var(--space-2);
  flex-wrap: wrap;
  justify-content: flex-end;
}

.calendar-month-title {
  margin-top: var(--space-4);
  color: var(--text-primary);
  font-size: var(--font-size-lg);
  font-weight: 700;
}

.month-calendar {
  margin-top: var(--space-3);
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--border-soft);
  gap: 1px;
}

.weekday-cell {
  min-height: 36px;
  display: grid;
  place-items: center;
  background: var(--bg-soft);
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.calendar-day {
  appearance: none;
  border: 0;
  min-width: 0;
  min-height: 112px;
  padding: var(--space-2);
  background: var(--bg-surface);
  color: var(--text-primary);
  display: grid;
  grid-template-rows: auto auto 1fr;
  gap: var(--space-1);
  text-align: left;
  cursor: context-menu;
}

.calendar-day:hover {
  background: var(--bg-soft);
}

.calendar-day:disabled {
  cursor: wait;
  opacity: 0.72;
}

.calendar-day--muted {
  color: var(--text-muted);
  background: #fbfcfd;
}

.calendar-day--closed {
  background: #fff7f7;
}

.calendar-day--holiday {
  background: #fff7ed;
}

.calendar-day--manual {
  box-shadow: inset 3px 0 0 var(--primary-500);
}

.calendar-day--holiday.calendar-day--manual {
  box-shadow: inset 3px 0 0 #ea580c;
}

.calendar-day--today {
  outline: 2px solid var(--primary-500);
  outline-offset: -2px;
}

.calendar-day--saving {
  background: var(--bg-soft);
}

.calendar-day-number {
  font-size: var(--font-size-md);
  font-weight: 700;
}

.calendar-day-status {
  width: fit-content;
  border-radius: var(--radius-pill);
  padding: 2px 8px;
  background: rgba(22, 163, 74, 0.10);
  color: var(--success-500);
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.calendar-day--closed .calendar-day-status {
  background: rgba(239, 68, 68, 0.10);
  color: var(--danger-500);
}

.calendar-day--holiday .calendar-day-status {
  background: rgba(234, 88, 12, 0.12);
  color: #ea580c;
}

.calendar-day-source {
  color: var(--text-muted);
  font-size: var(--font-size-xs);
  overflow-wrap: anywhere;
}

.calendar-context-menu {
  position: fixed;
  z-index: 1200;
  min-width: 132px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
  box-shadow: var(--shadow-hover);
  padding: var(--space-1);
  display: grid;
  gap: 2px;
}

.calendar-context-item {
  min-height: 34px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-primary);
  padding: 0 var(--space-3);
  text-align: left;
  cursor: pointer;
}

.calendar-context-item:hover:not(:disabled) {
  background: var(--bg-soft);
  color: var(--primary-600);
}

.calendar-context-item:disabled {
  color: var(--text-muted);
  cursor: not-allowed;
}

@media (max-width: 900px) {
  .calendar-header {
    display: grid;
  }

  .calendar-actions {
    justify-content: start;
  }

  .calendar-day {
    min-height: 88px;
    padding: var(--space-1);
  }
}

@media (max-width: 640px) {
  .month-calendar {
    overflow-x: auto;
    grid-template-columns: repeat(7, minmax(96px, 1fr));
  }
}
</style>
