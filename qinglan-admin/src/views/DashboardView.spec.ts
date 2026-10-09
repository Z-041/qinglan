import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchAdminStats } from '@/api/admin'
import DashboardView from '@/views/DashboardView.vue'
import type { AdminStats } from '@/types/admin'

vi.mock('@/api/admin', () => ({ fetchAdminStats: vi.fn() }))

const mockedFetchAdminStats = vi.mocked(fetchAdminStats)

function emptyStats(): AdminStats {
  return {
    content: { totalPosts: 0, pendingPosts: 0, takenDownPosts: 0, todayNewPosts: 0 },
    moderation: { pendingReports: 0, pendingAppeals: 0, todayHandled: 0, last7DaysHandled: [] },
    users: { totalUsers: 0, bannedUsers: 0, todayNewUsers: 0 },
    trends: { last7Days: [], last30Days: [] },
    reportReasons: [],
    dispositionLatency: { p50Seconds: 0, p90Seconds: 0, sampleSize: 0 },
    proposals: { approved: 0, rejected: 0, escalated: 0, approvalRate: 0 },
    moderatorRanking: [],
  }
}

function fullStats(): AdminStats {
  return {
    content: { totalPosts: 10, pendingPosts: 2, takenDownPosts: 1, todayNewPosts: 3 },
    moderation: {
      pendingReports: 4,
      pendingAppeals: 1,
      todayHandled: 5,
      last7DaysHandled: [{ date: '2026-10-01', count: 5 }],
    },
    users: { totalUsers: 100, bannedUsers: 6, todayNewUsers: 7 },
    trends: {
      last7Days: [{ date: '2026-10-01', count: 5 }],
      last30Days: [{ date: '2026-09-02', count: 3 }],
    },
    reportReasons: [{ reason: 'ABUSE', count: 2 }],
    dispositionLatency: { p50Seconds: 60, p90Seconds: 600, sampleSize: 12 },
    proposals: { approved: 3, rejected: 1, escalated: 1, approvalRate: 0.75 },
    moderatorRanking: [{ operatorId: 7, count: 9 }],
  }
}

/** EChart 内部依赖 canvas，jsdom 不可用；这里换成一个只回显 option 的桩，避免真实初始化 */
const EChartStub = {
  props: ['option', 'height'],
  template: '<div class="echart-stub" />',
}

function mountView() {
  return mount(DashboardView, { global: { stubs: { EChart: EChartStub } } })
}

describe('DashboardView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('加载失败时显示失败态与重试，而不是「暂无数据」', async () => {
    mockedFetchAdminStats.mockRejectedValue(new Error('网络异常'))

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('看板数据没能加载出来')
    expect(wrapper.findAll('button').some((button) => button.text() === '重试')).toBe(true)
    // 关键：网络故障不得被说成「真的没有数据」
    expect(wrapper.text()).not.toContain('暂无数据')
  })

  it('加载成功但确实无数据时才显示「暂无数据」', async () => {
    mockedFetchAdminStats.mockResolvedValue(emptyStats())

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('暂无数据')
    expect(wrapper.text()).not.toContain('看板数据没能加载出来')
    // 无数据时不渲染图表占位
    expect(wrapper.findAll('.echart-stub')).toHaveLength(0)
  })

  it('有数据时渲染五个图表，并展示提案通过率', async () => {
    mockedFetchAdminStats.mockResolvedValue(fullStats())

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.findAll('.echart-stub')).toHaveLength(5)
    expect(wrapper.text()).toContain('处置趋势')
    expect(wrapper.text()).toContain('举报按理由分布')
    expect(wrapper.text()).toContain('处置时效')
    expect(wrapper.text()).toContain('提案表决概况')
    expect(wrapper.text()).toContain('审核员处置量排行')
    expect(wrapper.text()).toContain('75%')
  })
})
