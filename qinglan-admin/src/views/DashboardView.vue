<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import EChart from '@/components/admin/EChart.vue'
import PageHeader from '@/components/admin/PageHeader.vue'
import BaseCard from '@shared/components/base/BaseCard.vue'
import StatePanel from '@shared/components/StatePanel.vue'
import { useUserLabels } from '@shared/composables/useUserLabels'
import { useDashboard } from '@/composables/useDashboard'
import {
  handledTrendOption,
  latencyOption,
  moderatorRankingOption,
  proposalOption,
  readChartPalette,
  reportReasonOption,
  trendPoints,
  type TrendRange,
} from '@/composables/dashboardCharts'

interface MetricItem {
  label: string
  value: number | undefined
}

interface MetricSection {
  title: string
  items: MetricItem[]
}

const { stats, loading, failed, load } = useDashboard()
const { reportReasonLabel } = useUserLabels()

/** 语义色在运行时读成实际色值（canvas 不认 CSS 变量），全站唯一色值来源仍是 main.css */
const palette = readChartPalette()

const sections = computed<MetricSection[]>(() => {
  const data = stats.value
  return [
    {
      title: '内容',
      items: [
        { label: '帖子总数', value: data?.content.totalPosts },
        { label: '待审核', value: data?.content.pendingPosts },
        { label: '已下架', value: data?.content.takenDownPosts },
        { label: '今日新增', value: data?.content.todayNewPosts },
      ],
    },
    {
      title: '治理',
      items: [
        { label: '待处理举报', value: data?.moderation.pendingReports },
        { label: '待复审申诉', value: data?.moderation.pendingAppeals },
        { label: '今日处置', value: data?.moderation.todayHandled },
      ],
    },
    {
      title: '用户',
      items: [
        { label: '用户总数', value: data?.users.totalUsers },
        { label: '封禁用户', value: data?.users.bannedUsers },
        { label: '今日新增', value: data?.users.todayNewUsers },
      ],
    },
  ]
})

/** 趋势图范围切换；默认近 7 日 */
const trendRange = ref<TrendRange>('7d')

const trendData = computed(() => (stats.value ? trendPoints(stats.value.trends, trendRange.value) : []))
const trendOption = computed(() => handledTrendOption(trendData.value, palette))

const reasonItems = computed(() => stats.value?.reportReasons ?? [])
const reasonChartOption = computed(() => reportReasonOption(reasonItems.value, palette, reportReasonLabel))

const latency = computed(() => stats.value?.dispositionLatency ?? { p50Seconds: 0, p90Seconds: 0, sampleSize: 0 })
const latencyChartOption = computed(() => latencyOption(latency.value, palette))

const proposals = computed(
  () => stats.value?.proposals ?? { approved: 0, rejected: 0, escalated: 0, approvalRate: 0 },
)
const proposalChartOption = computed(() => proposalOption(proposals.value, palette))
const approvalRateText = computed(() => `${Math.round(proposals.value.approvalRate * 100)}%`)
const proposalTotal = computed(
  () => proposals.value.approved + proposals.value.rejected + proposals.value.escalated,
)

const ranking = computed(() => stats.value?.moderatorRanking ?? [])
const rankingChartOption = computed(() => moderatorRankingOption(ranking.value, palette))

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="flex flex-col gap-5">
    <!-- 标准刷新按钮收敛在 PageHeader（在途自动禁用，避免连点） -->
    <PageHeader title="数据看板" :refreshing="loading" @refresh="load" />

    <!-- 失败与「真的没有数据」必须分成两屏；文案是状态说明，请求错误信息仍只由
         http 拦截器的 toast 给出（AGENTS.md 第七章）。本块在 flex gap 容器内，去重顶边距 -->
    <StatePanel
      :state="loading && !stats ? 'loading' : failed && !stats ? 'failed' : 'ready'"
      top-space-class="mt-0"
      failed-title="看板数据没能加载出来"
      @retry="load()"
    >
      <div class="grid grid-cols-3 gap-5">
        <BaseCard v-for="section in sections" :key="section.title">
          <h2 class="text-sm font-medium text-muted">{{ section.title }}</h2>
          <dl class="mt-4 flex flex-col gap-3">
            <div v-for="metric in section.items" :key="metric.label" class="flex items-baseline justify-between">
              <dt class="text-sm text-muted">{{ metric.label }}</dt>
              <dd class="text-xl font-semibold tabular-nums text-ink">{{ metric.value ?? '—' }}</dd>
            </div>
          </dl>
        </BaseCard>
      </div>

      <div class="grid grid-cols-2 gap-5">
        <BaseCard>
          <div class="flex items-center justify-between">
            <h2 class="text-sm font-medium text-muted">处置趋势</h2>
            <el-radio-group v-model="trendRange" size="small">
              <el-radio-button value="7d">近 7 日</el-radio-button>
              <el-radio-button value="30d">近 30 日</el-radio-button>
            </el-radio-group>
          </div>
          <!-- 图表容器固定高度（EChart 默认 280px），避免布局跳动 -->
          <EChart v-if="trendData.length" class="mt-3" :option="trendOption" aria-label="处置趋势图" />
          <p v-else class="mt-3 text-sm text-muted">暂无数据</p>
        </BaseCard>

        <BaseCard>
          <h2 class="text-sm font-medium text-muted">举报按理由分布</h2>
          <EChart v-if="reasonItems.length" class="mt-3" :option="reasonChartOption" aria-label="举报按理由分布图" />
          <p v-else class="mt-3 text-sm text-muted">暂无数据</p>
        </BaseCard>

        <BaseCard>
          <h2 class="text-sm font-medium text-muted">处置时效（近 30 天）</h2>
          <EChart
            v-if="latency.sampleSize > 0"
            class="mt-3"
            :option="latencyChartOption"
            aria-label="处置时效图（近 30 天）"
          />
          <p v-else class="mt-3 text-sm text-muted">暂无数据</p>
        </BaseCard>

        <BaseCard>
          <div class="flex items-center justify-between">
            <h2 class="text-sm font-medium text-muted">提案表决概况</h2>
            <span class="text-sm text-muted">通过率 <span class="font-medium text-ink">{{ approvalRateText }}</span></span>
          </div>
          <EChart
            v-if="proposalTotal > 0"
            class="mt-3"
            :option="proposalChartOption"
            aria-label="提案通过率图"
          />
          <p v-else class="mt-3 text-sm text-muted">暂无数据</p>
        </BaseCard>

        <BaseCard class="col-span-2">
          <h2 class="text-sm font-medium text-muted">审核员处置量排行</h2>
          <EChart
            v-if="ranking.length"
            class="mt-3"
            :option="rankingChartOption"
            height="320px"
            aria-label="审核员处置量排行图"
          />
          <p v-else class="mt-3 text-sm text-muted">暂无数据</p>
        </BaseCard>
      </div>
    </StatePanel>
  </div>
</template>
