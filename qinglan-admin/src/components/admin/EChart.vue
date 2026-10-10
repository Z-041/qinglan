<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import * as echarts from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsOption } from 'echarts'

/**
 * ECharts 图表容器（阶段 C）。
 *
 * 按需注册（core + 折线 / 柱状 + 网格 / 提示 + Canvas 渲染），不带地图、坐标系之外的模块，
 * 尽量压住包体积；配色由外层经 option 注入（读 CSS 变量，见 dashboardCharts）。
 *
 * 容器有**固定高度**（props.height，默认 280px），避免图表未渲染时布局跳动；
 * 宽度跟随父容器，并在容器尺寸变化时 resize——管理端仅 PC，但窗口可缩小。
 */
const props = withDefaults(
  defineProps<{
    option: EChartsOption
    /** 容器高度；图表必须有确定高度，否则初次渲染高度为 0 */
    height?: string
    /** 图表的可访问名：屏幕阅读器据此得知图表含义（canvas 本身无文字） */
    ariaLabel?: string
  }>(),
  { ariaLabel: '数据图表' },
)

echarts.use([LineChart, BarChart, GridComponent, TooltipComponent, CanvasRenderer])

/**
 * 用户开启系统级「减少动态效果」时关闭 ECharts 的入场动画——CSS 媒体查询管不到 canvas 动画，
 * 必须在 setOption 前关掉。jsdom 等无 matchMedia 的环境按「不减少」处理。
 */
const prefersReducedMotion: boolean =
  typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    ? window.matchMedia('(prefers-reduced-motion: reduce)').matches
    : false

const el = ref<HTMLDivElement | null>(null)
let chart: ReturnType<typeof echarts.init> | null = null
let observer: ResizeObserver | null = null

function render(): void {
  // notMerge=true（第二参）会整体替换 option，故把 animation 开关合并在传入对象上
  chart?.setOption({ ...props.option, animation: !prefersReducedMotion }, true)
}

function resize(): void {
  chart?.resize()
}

onMounted(() => {
  if (!el.value) {
    return
  }
  // jsdom 等无 canvas 环境 init 可能抛错：捕获后降级为「不渲染」，不影响页面其余部分与测试
  try {
    chart = echarts.init(el.value)
  } catch {
    chart = null
  }
  render()
  if (typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(resize)
    observer.observe(el.value)
  } else {
    window.addEventListener('resize', resize)
  }
})

// option 来自视图的 computed，每次重算都是新引用，浅监听即可触发；
// deep 会递归遍历整棵 EChartsOption（含 series/data），纯属浪费，故不加 deep
watch(() => props.option, render)

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
  window.removeEventListener('resize', resize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div
    ref="el"
    role="img"
    :aria-label="props.ariaLabel"
    class="w-full"
    :style="{ height: props.height ?? '280px' }"
  />
</template>
