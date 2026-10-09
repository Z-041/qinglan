import type { EChartsOption } from 'echarts'

import type { ReportReason } from '@shared/types/review-common'
import type { DailyCount, LatencyStats, ModeratorRank, ProposalStats, ReasonCount } from '@/types/admin'

/**
 * 图表配色。ECharts 画在 canvas 上，**无法解析 CSS 变量**，因此必须在运行时把
 * `styles/main.css` 的语义色读成实际色值再喂给图表——这样「全站唯一色值来源」的口径不变，
 * 也不会在图表里硬编码十六进制。
 */
export interface ChartPalette {
  primary: string
  primaryDeep: string
  primarySoft: string
  danger: string
  warning: string
  ink: string
  muted: string
  line: string
}

/** 读取语义色变量。取不到时返回空串，由下方 `color()` 退化为 ECharts 默认色，而不是渲染成黑色 */
export function readChartPalette(root: Element = document.documentElement): ChartPalette {
  const style = getComputedStyle(root)
  const read = (name: string): string => style.getPropertyValue(name).trim()
  return {
    primary: read('--ql-primary'),
    primaryDeep: read('--ql-primary-deep'),
    primarySoft: read('--ql-primary-soft'),
    danger: read('--ql-danger'),
    warning: read('--ql-warning'),
    ink: read('--ql-ink'),
    muted: read('--ql-muted'),
    line: read('--ql-line'),
  }
}

/** 空串 → undefined，让 ECharts 用默认色而不是把空串当黑色渲染 */
function color(value: string): string | undefined {
  return value || undefined
}

/** 趋势图的时间范围 */
export type TrendRange = '7d' | '30d'

/** 取对应范围的趋势点（后端已保证升序、无数据补 0） */
export function trendPoints(
  trends: { last7Days: DailyCount[]; last30Days: DailyCount[] },
  range: TrendRange,
): DailyCount[] {
  return range === '7d' ? trends.last7Days : trends.last30Days
}

/** 近 7 / 30 日处置趋势（折线） */
export function handledTrendOption(points: DailyCount[], palette: ChartPalette): EChartsOption {
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 8, right: 16, top: 24, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: points.map((point) => point.date),
      axisLine: { lineStyle: { color: color(palette.line) } },
      axisLabel: { color: color(palette.muted) },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: color(palette.line) } },
      axisLabel: { color: color(palette.muted) },
    },
    series: [
      {
        name: '处置数',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: points.map((point) => point.count),
        itemStyle: { color: color(palette.primary) },
        lineStyle: { color: color(palette.primary) },
        areaStyle: { color: color(palette.primarySoft) },
      },
    ],
  }
}

/** 举报按理由分布（柱状） */
export function reportReasonOption(
  items: ReasonCount[],
  palette: ChartPalette,
  labelOf: (reason: ReportReason) => string,
): EChartsOption {
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 8, right: 16, top: 24, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      // 八个理由标签较长，斜排并强制全显，窄窗口下靠 containLabel 不溢出
      axisLabel: { color: color(palette.muted), interval: 0, rotate: 30 },
      axisLine: { lineStyle: { color: color(palette.line) } },
      data: items.map((item) => labelOf(item.reason)),
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: color(palette.line) } },
      axisLabel: { color: color(palette.muted) },
    },
    series: [
      {
        name: '举报数',
        type: 'bar',
        barMaxWidth: 28,
        data: items.map((item) => item.count),
        itemStyle: { color: color(palette.primary), borderRadius: [4, 4, 0, 0] },
      },
    ],
  }
}

/** 处置时效 P50 / P90（柱状，单位秒） */
export function latencyOption(latency: LatencyStats, palette: ChartPalette): EChartsOption {
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 8, right: 16, top: 24, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      data: ['P50（中位）', 'P90'],
      axisLine: { lineStyle: { color: color(palette.line) } },
      axisLabel: { color: color(palette.muted) },
    },
    yAxis: {
      type: 'value',
      name: '秒',
      splitLine: { lineStyle: { color: color(palette.line) } },
      axisLabel: { color: color(palette.muted) },
    },
    series: [
      {
        name: '耗时',
        type: 'bar',
        barMaxWidth: 48,
        data: [latency.p50Seconds, latency.p90Seconds],
        itemStyle: { color: color(palette.primary), borderRadius: [4, 4, 0, 0] },
      },
    ],
  }
}

/** 提案表决概况（准予 / 不予 / 僵局） */
export function proposalOption(proposals: ProposalStats, palette: ChartPalette): EChartsOption {
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 8, right: 16, top: 24, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      data: ['准予', '不予', '僵局待裁决'],
      axisLine: { lineStyle: { color: color(palette.line) } },
      axisLabel: { color: color(palette.muted) },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: color(palette.line) } },
      axisLabel: { color: color(palette.muted) },
    },
    series: [
      {
        name: '提案数',
        type: 'bar',
        barMaxWidth: 48,
        data: [
          { value: proposals.approved, itemStyle: { color: color(palette.primary) } },
          { value: proposals.rejected, itemStyle: { color: color(palette.danger) } },
          { value: proposals.escalated, itemStyle: { color: color(palette.warning) } },
        ],
        itemStyle: { borderRadius: [4, 4, 0, 0] },
      },
    ],
  }
}

/** 审核员处置量排行（横向柱状；只有 operatorId 与计数） */
export function moderatorRankingOption(
  ranking: ModeratorRank[],
  palette: ChartPalette,
): EChartsOption {
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 8, right: 24, top: 16, bottom: 8, containLabel: true },
    xAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: color(palette.line) } },
      axisLabel: { color: color(palette.muted) },
    },
    yAxis: {
      type: 'category',
      // inverse：榜首先排在最上方
      inverse: true,
      data: ranking.map((item) => `#${item.operatorId}`),
      axisLine: { lineStyle: { color: color(palette.line) } },
      axisLabel: { color: color(palette.muted) },
    },
    series: [
      {
        name: '处置量',
        type: 'bar',
        barMaxWidth: 20,
        data: ranking.map((item) => item.count),
        itemStyle: { color: color(palette.primaryDeep), borderRadius: [0, 4, 4, 0] },
      },
    ],
  }
}
