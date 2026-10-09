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
const props = defineProps<{
  option: EChartsOption
  /** 容器高度；图表必须有确定高度，否则初次渲染高度为 0 */
  height?: string
}>()

echarts.use([LineChart, BarChart, GridComponent, TooltipComponent, CanvasRenderer])

const el = ref<HTMLDivElement | null>(null)
let chart: ReturnType<typeof echarts.init> | null = null
let observer: ResizeObserver | null = null

function render(): void {
  chart?.setOption(props.option, true)
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

watch(() => props.option, render, { deep: true })

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
  window.removeEventListener('resize', resize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div ref="el" class="w-full" :style="{ height: props.height ?? '280px' }" />
</template>
