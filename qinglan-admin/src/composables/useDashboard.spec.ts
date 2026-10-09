import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchAdminStats } from '@/api/admin'
import { useDashboard } from '@/composables/useDashboard'
import type { AdminStats } from '@/types/admin'

vi.mock('@/api/admin', () => ({ fetchAdminStats: vi.fn() }))

const mockedFetchAdminStats = vi.mocked(fetchAdminStats)

function stats(): AdminStats {
  return {
    content: { totalPosts: 10, pendingPosts: 2, takenDownPosts: 1, todayNewPosts: 3 },
    moderation: {
      pendingReports: 4,
      pendingAppeals: 1,
      todayHandled: 5,
      last7DaysHandled: Array.from({ length: 7 }, (_, index) => ({
        date: `2026-10-0${index + 1}`,
        count: index,
      })),
    },
    users: { totalUsers: 100, bannedUsers: 6, todayNewUsers: 7 },
    trends: {
      last7Days: Array.from({ length: 7 }, (_, index) => ({ date: `2026-10-0${index + 1}`, count: index })),
      last30Days: Array.from({ length: 30 }, (_, index) => ({ date: `2026-09-${index + 1}`, count: index })),
    },
    reportReasons: [{ reason: 'ABUSE', count: 2 }],
    dispositionLatency: { p50Seconds: 60, p90Seconds: 600, sampleSize: 12 },
    proposals: { approved: 3, rejected: 1, escalated: 1, approvalRate: 0.75 },
    moderatorRanking: [{ operatorId: 7, count: 9 }],
  }
}

describe('useDashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('加载后写入统计并结束 loading', async () => {
    mockedFetchAdminStats.mockResolvedValue(stats())

    const dashboard = useDashboard()
    await dashboard.load()

    expect(mockedFetchAdminStats).toHaveBeenCalledTimes(1)
    expect(dashboard.stats.value?.content.totalPosts).toBe(10)
    expect(dashboard.stats.value?.moderation.last7DaysHandled).toHaveLength(7)
    expect(dashboard.loading.value).toBe(false)
  })

  it('加载失败时 stats 保持 null、置 failed，且不向外抛错（错误已由拦截器提示）', async () => {
    mockedFetchAdminStats.mockRejectedValue(new Error('网络异常'))

    const dashboard = useDashboard()
    await expect(dashboard.load()).resolves.toBeUndefined()

    expect(dashboard.stats.value).toBeNull()
    expect(dashboard.loading.value).toBe(false)
    // failed 供页面区分「加载失败」与「暂无数据」
    expect(dashboard.failed.value).toBe(true)
  })

  it('已有数据后刷新失败不置 failed，保留旧数据（不用错误屏盖掉内容）', async () => {
    mockedFetchAdminStats.mockResolvedValueOnce(stats()).mockRejectedValueOnce(new Error('网络异常'))

    const dashboard = useDashboard()
    await dashboard.load()
    await dashboard.load()

    expect(dashboard.failed.value).toBe(false)
    expect(dashboard.stats.value?.content.totalPosts).toBe(10)
  })

  it('并发加载时丢弃先发的过期响应，旧结果不得覆盖新结果', async () => {
    const fresh = stats()
    const stale = stats()
    stale.content.totalPosts = 1
    let resolveFirst!: (value: AdminStats) => void
    mockedFetchAdminStats
      .mockImplementationOnce(() => new Promise((resolve) => {
        resolveFirst = resolve
      }))
      .mockResolvedValueOnce(fresh)

    const dashboard = useDashboard()
    const first = dashboard.load()
    const second = dashboard.load()
    await second

    // 先发的那次此时才返回：序号已过期，必须被丢弃
    resolveFirst(stale)
    await first

    expect(dashboard.stats.value?.content.totalPosts).toBe(10)
    expect(dashboard.loading.value).toBe(false)
  })
})
