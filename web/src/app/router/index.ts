import { createRouter, createWebHistory } from 'vue-router'
import MarketOverviewPage from '@/features/market/views/MarketOverviewPage.vue'
import StockListPage from '@/features/stocks/views/StockListPage.vue'
import WatchlistPage from '@/features/watchlist/views/WatchlistPage.vue'
import StockDetailPage from '@/features/stock-detail/views/StockDetailPage.vue'
import TradingCalendarPage from '@/features/trading-calendar/views/TradingCalendarPage.vue'

export const routes = [
  {
    path: '/',
    redirect: '/market'
  },
  {
    path: '/market',
    name: 'market',
    component: MarketOverviewPage,
    meta: {
      navKey: 'market',
      title: '市场总览',
      description: '先看市场。'
    }
  },
  {
    path: '/stocks',
    name: 'stocks',
    component: StockListPage,
    meta: {
      navKey: 'stocks',
      title: '选股列表',
      description: '筛选、排序，点击代码名称查看详情。'
    }
  },
  {
    path: '/stocks/:id(\\d+)',
    name: 'stock-detail',
    component: StockDetailPage,
    meta: {
      navKey: 'stocks',
      layout: 'standalone',
      title: '个股详情',
      description: '聚焦查看单只股票的基础信息与最近一段时间的日K走势。'
    }
  },
  {
    path: '/watchlist',
    name: 'watchlist',
    component: WatchlistPage,
    meta: {
      navKey: 'watchlist',
      title: '自选追踪',
      description: '专门查看你已收藏的股票。'
    }
  },
  {
    path: '/trading-calendar',
    name: 'trading-calendar',
    component: TradingCalendarPage,
    meta: {
      navKey: 'trading-calendar',
      title: '交易日历',
      description: '人工维护 A 股开市与休市日期。'
    }
  }
]

export const router = createRouter({
  history: createWebHistory(),
  routes
})
