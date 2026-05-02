import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { marketApi, type HotListRawItem } from '@/api/market'
import { stockApi } from '@/api/stock'

const HOT_LIST_LIMIT = 20

export interface HotListItem {
  rank: number
  code: string
  name: string
  rate: number | null
  changePercent: number | null
  hotRankChange: number | null
  conceptTags: string[]
  popularityTag: string | null
}

export interface HotListDigestItem {
  label: string
  count: number
}

export interface HotListHeatStats {
  up: number
  down: number
  flat: number
}

function normalizeTagValue(value: unknown) {
  if (typeof value !== 'string') {
    return ''
  }

  return value.trim()
}

function normalizeConceptTags(value: unknown) {
  if (Array.isArray(value)) {
    return value
      .map((tag) => normalizeTagValue(tag))
      .filter((tag) => tag.length > 0)
  }

  if (typeof value === 'string') {
    return value
      .split(/[,\s/、，|]+/)
      .map((tag) => tag.trim())
      .filter((tag) => tag.length > 0)
  }

  return []
}

function toNumber(value: number | string | null | undefined) {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null
  }

  if (typeof value === 'string') {
    const parsed = Number(value.trim())
    return Number.isFinite(parsed) ? parsed : null
  }

  return null
}

function normalizeHotListItem(item: HotListRawItem, index: number): HotListItem | null {
  const code = typeof item.code === 'string' ? item.code.trim() : ''
  const name = typeof item.name === 'string' ? item.name.trim() : ''

  if (!code || !name) {
    return null
  }

  const parsedRank = toNumber(item.order)
  const rank = parsedRank != null && parsedRank > 0 ? parsedRank : index + 1

  return {
    rank,
    code,
    name,
    rate: toNumber(item.rate),
    changePercent: toNumber(item.rise_and_fall),
    hotRankChange: toNumber(item.hot_rank_chg),
    conceptTags: normalizeConceptTags(item.tag?.concept_tag),
    popularityTag: normalizeTagValue(item.tag?.popularity_tag) || null
  }
}

export function useMarketHotList() {
  const router = useRouter()
  const loading = ref(true)
  const error = ref('')
  const list = ref<HotListItem[]>([])
  const notFoundMessage = ref('')
  const resolvingCode = ref('')
  const conceptDigest = computed<HotListDigestItem[]>(() => {
    const conceptCounter = new Map<string, number>()

    list.value.forEach((item) => {
      item.conceptTags.forEach((tag) => {
        conceptCounter.set(tag, (conceptCounter.get(tag) ?? 0) + 1)
      })
    })

    return Array.from(conceptCounter.entries())
      .sort((left, right) => {
        if (right[1] !== left[1]) {
          return right[1] - left[1]
        }

        return left[0].localeCompare(right[0])
      })
      .slice(0, 6)
      .map(([label, count]) => ({ label, count }))
  })
  const popularityDigest = computed<HotListDigestItem[]>(() => {
    const popularityCounter = new Map<string, number>()

    list.value.forEach((item) => {
      if (!item.popularityTag) {
        return
      }

      popularityCounter.set(item.popularityTag, (popularityCounter.get(item.popularityTag) ?? 0) + 1)
    })

    return Array.from(popularityCounter.entries())
      .sort((left, right) => {
        if (right[1] !== left[1]) {
          return right[1] - left[1]
        }

        return left[0].localeCompare(right[0])
      })
      .slice(0, 4)
      .map(([label, count]) => ({ label, count }))
  })
  const heatStats = computed<HotListHeatStats>(() => {
    return list.value.reduce<HotListHeatStats>(
      (result, item) => {
        const change = item.hotRankChange

        if (change == null || change === 0) {
          result.flat += 1
          return result
        }

        if (change > 0) {
          result.up += 1
          return result
        }

        result.down += 1
        return result
      },
      {
        up: 0,
        down: 0,
        flat: 0
      }
    )
  })

  async function fetchHotList() {
    loading.value = true
    error.value = ''

    try {
      const response = await marketApi.getHotList()
      const rawList = response.data.data?.stock_list ?? []

      list.value = rawList
        .map((item, index) => normalizeHotListItem(item, index))
        .filter((item): item is HotListItem => item != null)
        .slice(0, HOT_LIST_LIMIT)
    } catch {
      list.value = []
      error.value = '加载今日热榜失败，请稍后重试。'
    } finally {
      loading.value = false
    }
  }

  async function openStockDetailByCode(code: string) {
    const normalizedCode = code.trim()

    if (!normalizedCode) {
      return
    }

    notFoundMessage.value = ''
    resolvingCode.value = normalizedCode

    try {
      const response = await stockApi.getStocks({
        keyword: normalizedCode,
        page: 0,
        size: 20,
        sortBy: 'code',
        sortDirection: 'ASC'
      })

      const exactMatchedStock = response.data.content.find((stock) => stock.symbol === normalizedCode)

      if (exactMatchedStock) {
        await router.push(`/stocks/${exactMatchedStock.id}`)
        return
      }

      notFoundMessage.value = `${normalizedCode} 暂未收录，无法跳转详情。`
    } catch {
      notFoundMessage.value = '跳转详情失败，请稍后重试。'
    } finally {
      resolvingCode.value = ''
    }
  }

  onMounted(() => {
    void fetchHotList()
  })

  return {
    loading,
    error,
    list,
    conceptDigest,
    popularityDigest,
    heatStats,
    notFoundMessage,
    resolvingCode,
    fetchHotList,
    openStockDetailByCode
  }
}
