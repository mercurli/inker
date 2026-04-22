<script setup lang="ts">
import { computed } from "vue";
import type { MarketSummary } from "@/api/market";

interface DistributionItem {
  label: string;
  count: number;
  width: string;
  tone: "up" | "down" | "neutral";
}

const props = defineProps<{
  marketSummary: MarketSummary;
  watchlistCount: number;
  strongestStockLabel: string;
  priceChangeDistribution: DistributionItem[];
}>();

const trendUpWeight = computed(() => {
  const total = props.marketSummary.rising + props.marketSummary.falling;
  if (total <= 0) {
    return 1;
  }
  return props.marketSummary.rising;
});

const trendDownWeight = computed(() => {
  const total = props.marketSummary.rising + props.marketSummary.falling;
  if (total <= 0) {
    return 1;
  }
  return props.marketSummary.falling;
});
</script>

<template>
  <div>
    
    <div class="page-stack">

      <section class="market-strip">
        <article class="summary-card panel">
          <span class="summary-label">上涨家数</span>
          <strong class="summary-value up">{{ marketSummary.rising }}</strong>
          <span class="summary-note"
            >偏强市场里，更容易观察到行业轮动和趋势延续。</span
          >
        </article>
        <article class="summary-card panel">
          <span class="summary-label">下跌家数</span>
          <strong class="summary-value down">{{
            marketSummary.falling
          }}</strong>
          <span class="summary-note"
            >下行阶段更适合优先做风险控制和质量筛选。</span
          >
        </article>
        <article class="summary-card panel">
          <span class="summary-label">平盘家数</span>
          <strong class="summary-value neutral">{{
            marketSummary.flat
          }}</strong>
          <span class="summary-note"
            >中性分布时，可以结合行业和板块进一步细看结构变化。</span
          >
        </article>
        <article class="summary-card panel summary-card--accent">
          <span class="summary-label">自选追踪</span>
          <strong class="summary-stock">{{ watchlistCount }} 只</strong>
          <span class="summary-note"
            >你持续跟踪的股票数量会同步展示在这里。</span
          >
        </article>
      </section>

      <section class="panel section-panel market-distribution-panel">
        <div class="distribution-header">
          <div>
            <p class="eyebrow">Distribution</p>
            <h3 class="section-title">涨跌幅分布</h3>
          </div>
        </div>

        <div class="distribution-chart">
          <div
            v-for="item in priceChangeDistribution"
            :key="item.label"
            class="distribution-bar"
            :class="`distribution-bar--${item.tone}`"
          >
            <div class="distribution-bar-track">
              <div
                class="distribution-bar-value"
                :style="{ bottom: `calc(${item.width} + 6px)` }"
              >
                {{ item.count }}
              </div>
              <div
                class="distribution-bar-fill"
                :style="{ height: item.width }"
              ></div>
            </div>
            <div class="distribution-bar-label">{{ item.label }}</div>
          </div>
        </div>

        <div class="distribution-balance">
          <div class="distribution-balance-track">
            <div
              class="distribution-balance-segment distribution-balance-segment--up"
              :style="{ flexGrow: trendUpWeight }"
            ></div>
            <div class="distribution-balance-gap" aria-hidden="true">
              <span></span>
              <span></span>
            </div>
            <div
              class="distribution-balance-segment distribution-balance-segment--down"
              :style="{ flexGrow: trendDownWeight }"
            ></div>
          </div>
          <div class="distribution-balance-meta">
            <span class="distribution-balance-text distribution-balance-text--up">
              上涨 {{ marketSummary.rising }}只
            </span>
            <span class="distribution-balance-text distribution-balance-text--down">
              {{ marketSummary.falling }}只 下跌
            </span>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>
