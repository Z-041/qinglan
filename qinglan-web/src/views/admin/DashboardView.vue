<script setup lang="ts">
import { computed, onMounted } from 'vue'

import BaseButton from '@/components/base/BaseButton.vue'
import BaseCard from '@/components/base/BaseCard.vue'
import { useDashboard } from '@/composables/useDashboard'

interface MetricItem {
  label: string
  value: number | undefined
}

interface MetricSection {
  title: string
  items: MetricItem[]
}

const { stats, loading, failed, load } = useDashboard()

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

const last7Days = computed(() => stats.value?.moderation.last7DaysHandled ?? [])

/** 条形基准取 7 日最大值；全 0 时退化为 1，避免除零 */
const maxDaily = computed(() => Math.max(1, ...last7Days.value.map((item) => item.count)))

function barWidth(count: number): string {
  return `${Math.round((count / maxDaily.value) * 100)}%`
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="flex flex-col gap-5">
    <div class="flex items-center justify-between">
      <h1 class="text-lg font-semibold text-ink">数据看板</h1>
      <!-- 用 BaseButton 而非裸 button：与其余管理端页面一致，且 loading 期间自动禁用，
           避免连点打出多个请求 -->
      <BaseButton variant="ghost" size="sm" :loading="loading" @click="load">刷新</BaseButton>
    </div>

    <p v-if="loading && !stats" class="text-sm text-muted">加载中…</p>

    <!-- 失败与「真的没有数据」必须分成两屏：否则一次网络故障会被说成「暂无数据」。
         文案是状态说明，具体错误信息仍只由 http 拦截器的 toast 给出（AGENTS.md 第七章）。 -->
    <div
      v-else-if="failed && !stats"
      class="rounded-xl border border-dashed border-line-strong px-5 py-10 text-center"
    >
      <p class="text-sm font-medium text-ink">看板数据没能加载出来</p>
      <BaseButton class="mt-4" variant="ghost" size="sm" @click="load()">重试</BaseButton>
    </div>

    <template v-else>
      <div class="grid grid-cols-3 gap-5">
        <BaseCard v-for="section in sections" :key="section.title">
          <h2 class="text-sm font-medium text-muted">{{ section.title }}</h2>
          <dl class="mt-4 flex flex-col gap-3">
            <div v-for="metric in section.items" :key="metric.label" class="flex items-baseline justify-between">
              <dt class="text-sm text-muted">{{ metric.label }}</dt>
              <dd class="text-xl font-semibold text-ink">{{ metric.value ?? '—' }}</dd>
            </div>
          </dl>
        </BaseCard>
      </div>

      <BaseCard>
        <h2 class="text-sm font-medium text-muted">近 7 日处置数</h2>
        <ul v-if="last7Days.length" class="mt-4 flex flex-col gap-2.5">
          <li v-for="day in last7Days" :key="day.date" class="flex items-center gap-3">
            <span class="w-24 text-xs text-muted">{{ day.date }}</span>
            <span class="h-2 flex-1 overflow-hidden rounded-full bg-primary-tint">
              <span class="block h-full rounded-full bg-primary" :style="{ width: barWidth(day.count) }" />
            </span>
            <span class="w-8 text-right text-xs text-ink">{{ day.count }}</span>
          </li>
        </ul>
        <p v-else class="mt-3 text-sm text-muted">暂无数据</p>
      </BaseCard>
    </template>
  </div>
</template>
