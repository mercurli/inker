<script setup lang="ts">
import { useMarketSummary } from '@/features/market/composables/useMarketSummary'
import PanelCard from '@/shared/components/layout/PanelCard.vue'
import SectionHeader from '@/shared/components/layout/SectionHeader.vue'
import MetricCard from '@/shared/components/display/MetricCard.vue'

const { marketSummary, watchlistCount, priceChangeDistribution } = useMarketSummary()
</script>

<template>
  <div class="page-stack">
    <div class="w50">
      <section class="market-strip">
        <MetricCard label="上涨家数" :value="marketSummary.rising" note="偏强市场里更容易观察到趋势延续。" tone="up" />
        <MetricCard label="下跌家数" :value="marketSummary.falling" note="下行阶段更适合优先做风险控制。" tone="down" />
        <MetricCard label="平盘家数" :value="marketSummary.flat" note="中性分布时更要细看行业结构。" tone="neutral" />
      </section>
      <PanelCard>
        <SectionHeader eyebrow="Distribution" title="涨跌幅分布" />

        <div class="distribution-chart">
          <div v-for="item in priceChangeDistribution" :key="item.label" class="distribution-bar"
            :class="`distribution-bar--${item.tone}`">
            <div class="distribution-bar-track">
              <div class="distribution-bar-value" :style="{ bottom: `calc(${item.width} + 6px)` }">{{ item.count }}
              </div>
              <div class="distribution-bar-fill" :style="{ height: item.width }"></div>
            </div>
            <div class="distribution-bar-label">{{ item.label }}</div>
          </div>
        </div>
      </PanelCard>
    </div>

  </div>
</template>
