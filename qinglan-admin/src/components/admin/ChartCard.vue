<script setup lang="ts">
import BaseCard from '@shared/components/base/BaseCard.vue'

/**
 * 看板图表卡：BaseCard + 标题（右侧可挂附加控件）+ 图表 + 统一的「暂无数据」空态。
 *
 * 看板上五个图表卡此前逐字重复（标题样式 + `EChart v-if` + `p v-else` 空态），抽到一处。
 * option 的构造仍留在页面（`dashboardCharts`），本组件只承载外壳与「有无数据」的呈现分支——
 * 空态与失败态的分工依旧：网络故障由更外层的 StatePanel 挡掉，不会走到这里被说成「没有数据」。
 */
defineProps<{
  title: string
  /** 有无数据；为 false 时只渲染空态，默认插槽（图表）不渲染 */
  hasData: boolean
}>()
</script>

<template>
  <BaseCard>
    <div class="flex items-center justify-between">
      <h2 class="text-sm font-medium text-muted">{{ title }}</h2>
      <slot name="action" />
    </div>
    <slot v-if="hasData" />
    <p v-else class="mt-3 text-sm text-muted">暂无数据</p>
  </BaseCard>
</template>
