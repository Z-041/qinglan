import { describe, expect, it } from 'vitest'
import type { EChartsOption } from 'echarts'

import {
  handledTrendOption,
  latencyOption,
  moderatorRankingOption,
  proposalOption,
  readChartPalette,
  reportReasonOption,
  trendPoints,
  type ChartPalette,
} from '@/composables/dashboardCharts'
import type { ReportReason } from '@shared/types/review-common'

const palette: ChartPalette = {
  primary: 'primary',
  primaryDeep: 'primary-deep',
  primarySoft: 'primary-soft',
  danger: 'danger',
  warning: 'warning',
  ink: 'ink',
  muted: 'muted',
  line: 'line',
}

interface SeriesShape {
  data: unknown[]
  itemStyle?: { color?: string }
  lineStyle?: { color?: string }
}

function seriesOf(option: EChartsOption): SeriesShape[] {
  return option.series as unknown as SeriesShape[]
}

function axisData(option: EChartsOption, axis: 'xAxis' | 'yAxis'): unknown[] {
  return (option[axis] as unknown as { data?: unknown[] }).data ?? []
}

function labelOf(reason: ReportReason): string {
  return `理由-${reason}`
}

describe('trendPoints', () => {
  const trends = {
    last7Days: [{ date: '2026-10-01', count: 1 }],
    last30Days: [{ date: '2026-09-10', count: 2 }],
  }

  it('按范围取对应趋势点', () => {
    expect(trendPoints(trends, '7d')).toBe(trends.last7Days)
    expect(trendPoints(trends, '30d')).toBe(trends.last30Days)
  })
})

describe('图表数据映射', () => {
  it('处置趋势：x 轴为日期、series 为计数、线色取语义主色', () => {
    const option = handledTrendOption(
      [
        { date: '2026-10-01', count: 3 },
        { date: '2026-10-02', count: 5 },
      ],
      palette,
    )

    expect(axisData(option, 'xAxis')).toEqual(['2026-10-01', '2026-10-02'])
    expect(seriesOf(option)[0].data).toEqual([3, 5])
    expect(seriesOf(option)[0].lineStyle?.color).toBe('primary')
  })

  it('举报理由分布：标签经文案函数映射，柱值取计数', () => {
    const option = reportReasonOption(
      [
        { reason: 'ABUSE', count: 4 },
        { reason: 'PRIVACY', count: 1 },
      ],
      palette,
      labelOf,
    )

    expect(axisData(option, 'xAxis')).toEqual(['理由-ABUSE', '理由-PRIVACY'])
    expect(seriesOf(option)[0].data).toEqual([4, 1])
  })

  it('处置时效：P50 / P90 依次取两个分位值', () => {
    const option = latencyOption({ p50Seconds: 60, p90Seconds: 600, sampleSize: 12 }, palette)

    expect(axisData(option, 'xAxis')).toEqual(['P50（中位）', 'P90'])
    expect(seriesOf(option)[0].data).toEqual([60, 600])
  })

  it('提案概况：准予 / 不予 / 僵局 三根柱取对应计数', () => {
    const option = proposalOption({ approved: 3, rejected: 1, escalated: 2, approvalRate: 0.75 }, palette)
    const data = seriesOf(option)[0].data as { value: number }[]

    expect(data.map((item) => item.value)).toEqual([3, 1, 2])
  })

  it('审核员排行：分类为 operatorId、值为处置量、榜首先排', () => {
    const option = moderatorRankingOption(
      [
        { operatorId: 7, count: 9 },
        { operatorId: 9, count: 4 },
      ],
      palette,
    )

    expect(axisData(option, 'yAxis')).toEqual(['#7', '#9'])
    expect(seriesOf(option)[0].data).toEqual([9, 4])
    expect((option.yAxis as unknown as { inverse?: boolean }).inverse).toBe(true)
  })
})

describe('readChartPalette', () => {
  it('返回全部语义色键（取不到时为空串，由 builder 退化为默认色）', () => {
    const result = readChartPalette(document.documentElement)

    expect(Object.keys(result).sort()).toEqual(
      ['danger', 'ink', 'line', 'muted', 'primary', 'primaryDeep', 'primarySoft', 'warning'].sort(),
    )
  })
})
